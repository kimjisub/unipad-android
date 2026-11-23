#!/usr/bin/env python3
"""Static checks of a release candidate against the last published build.

Both builds may be bundles (.aab), universal .apks sets or APKs. Each is turned into
one universal APK, then:

1. bytecode (`dexdump -d` of every classes*.dex): each system-exit diagnostic key is
   still a `const-string` after R8, and no forbidden call (`MediaPlayer` construction)
   is left in the candidate;
2. merged manifest (`apkanalyzer manifest print`): permissions are the same as in the
   published build unless a change is named with --expect-permission, no newly
   required `uses-feature` narrows the devices that can install the app, advertising
   ID collection stays off and no advertising permission is present; other changes
   (features, queries, receivers) are listed, not judged.

Exit status: 0 every check passed, 1 a check failed, 2 the check could not run.
"""
from __future__ import annotations

import argparse
from collections import Counter
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET

from check_debug_telemetry_manifest import AD_ID_COLLECTION, AD_PERMISSIONS, ANDROID, metadata
from upgrade_check import SetupError, build_tool, universal_apk

# Crash report keys written by SystemExitReporter; R8 must keep them as literal strings.
DIAGNOSTIC_KEYS = ('exit_foreground_screen', 'exit_foreground_pack_open', 'exit_foreground_ms', 'exit_process_ms',
                   'exit_last_screen')
# Calls the candidate must not contain, with the reason. MediaPlayer crashed in vendor
# players (ColorOS) and every sound now goes through the app's own decoder.
FORBIDDEN_CALLS = {'Landroid/media/MediaPlayer;.<init>': 'constructs the system MediaPlayer'}
PERMISSION_TAGS = ('uses-permission', 'uses-permission-sdk-23')


# --- bytecode -----------------------------------------------------------------------

def count_dex(lines, keys=DIAGNOSTIC_KEYS, calls=tuple(FORBIDDEN_CALLS)) -> dict:
    """Counts `const-string` loads of each key and references to each call in `dexdump -d` lines."""
    strings = re.compile(r'\bconst-string(?:/jumbo)? v\d+, "(' + '|'.join(map(re.escape, keys)) + r')"')
    found_strings: Counter = Counter()
    found_calls: Counter = Counter()
    for line in lines:
        if 'const-string' in line and (match := strings.search(line)):
            found_strings[match[1]] += 1
        for call in calls:
            if call in line:
                found_calls[call] += 1
    return dict(const_strings={key: found_strings[key] for key in keys}, calls={call: found_calls[call] for call in calls})


def dex_counts(apk: Path, work: Path) -> dict:
    dexdump = build_tool('dexdump')
    totals = dict(const_strings=Counter(), calls=Counter())
    with zipfile.ZipFile(apk) as archive:
        names = sorted(n for n in archive.namelist() if re.fullmatch(r'classes\d*\.dex', n))
        if not names:
            raise SetupError(f'{apk} has no classes*.dex')
        for name in names:
            archive.extract(name, work)
            with subprocess.Popen([dexdump, '-d', str(work / name)], stdout=subprocess.PIPE,
                                  stderr=subprocess.DEVNULL, text=True, errors='replace') as process:
                counts = count_dex(process.stdout)
            if process.returncode:
                raise SetupError(f'dexdump failed on {name} of {apk} ({process.returncode})')
            for kind in totals:
                totals[kind].update(counts[kind])
            (work / name).unlink()
    return dict(dex_files=names, const_strings={k: totals['const_strings'][k] for k in DIAGNOSTIC_KEYS},
                calls={c: totals['calls'][c] for c in FORBIDDEN_CALLS})


def dex_problems(candidate: dict) -> list[str]:
    problems = [f'diagnostic key "{key}" is not a const-string in the candidate (R8 removed or renamed it)'
                for key, count in candidate['const_strings'].items() if count == 0]
    problems += [f'{call} is referenced {count} time(s): {FORBIDDEN_CALLS[call]}'
                 for call, count in candidate['calls'].items() if count]
    return problems


# --- manifest -----------------------------------------------------------------------

def attr(element: ET.Element, name: str) -> str | None:
    return element.get(ANDROID + name)


def describe(element: ET.Element) -> str:
    """One element as `tag name k=v ...`, attributes sorted, children inlined; stable across builds."""
    attrs = ' '.join(f'{k.removeprefix(ANDROID)}={v}' for k, v in sorted(element.attrib.items()))
    inner = ' '.join(describe(child) for child in element)
    return f'{element.tag}[{attrs}{" " + inner if inner else ""}]'


def manifest_facts(xml_text: str) -> dict:
    root = ET.fromstring(xml_text)
    application = root.find('application')
    components = list(application) if application is not None else []
    return dict(
        permissions=sorted(attr(e, 'name') for e in root if e.tag in PERMISSION_TAGS),
        permission_entries=sorted(describe(e) for e in root if e.tag in PERMISSION_TAGS),
        features={attr(e, 'name') or f'glEsVersion={attr(e, "glEsVersion")}': attr(e, 'required') != 'false'
                  for e in root if e.tag == 'uses-feature'},
        queries=sorted(describe(child) for q in root.findall('queries') for child in q),
        receivers=sorted(describe(e) for e in components if e.tag == 'receiver'),
        metadata=metadata(root),
    )


def manifest_problems(previous: dict, candidate: dict, expected: set[str]) -> tuple[list[str], list[str]]:
    """(failures, other changes). `expected` names permission changes as +NAME or -NAME."""
    problems, changes = [], []
    added = set(candidate['permissions']) - set(previous['permissions'])
    removed = set(previous['permissions']) - set(candidate['permissions'])
    found = {f'+{p}' for p in added} | {f'-{p}' for p in removed}
    problems += [f'permission {"added" if c[0] == "+" else "removed"}: {c[1:]} (name it with --expect-permission {c} '
                 'once the store declarations match)' for c in sorted(found - expected)]
    problems += [f'expected permission change {c} is not in the candidate' for c in sorted(expected - found)]
    entries = sorted(set(candidate['permission_entries']) ^ set(previous['permission_entries']))
    if entries and not found:
        changes.append(f'permission attributes changed: {entries}')
    if candidate['metadata'].get(AD_ID_COLLECTION) != 'false':
        problems.append(f'{AD_ID_COLLECTION} is {candidate["metadata"].get(AD_ID_COLLECTION)!r}, must be false')
    problems += [f'advertising permission {p} is present' for p in sorted(AD_PERMISSIONS & set(candidate['permissions']))]
    for name, required in sorted(candidate['features'].items()):
        before = previous['features'].get(name)
        if required and not before:
            problems.append(f'uses-feature {name} is now required: devices without it can no longer install the app')
        elif before is None:
            changes.append(f'uses-feature added (not required): {name}')
        elif before and not required:
            changes.append(f'uses-feature no longer required: {name}')
    changes += [f'uses-feature removed: {name}' for name in sorted(set(previous['features']) - set(candidate['features']))]
    for kind in ('queries', 'receivers'):
        changes += [f'{kind} added: {e}' for e in sorted(set(candidate[kind]) - set(previous[kind]))]
        changes += [f'{kind} removed: {e}' for e in sorted(set(previous[kind]) - set(candidate[kind]))]
    return problems, changes


def apkanalyzer() -> str:
    """The SDK's own apkanalyzer: one installed elsewhere (Homebrew) cannot find the SDK's build tools."""
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:
        raise SetupError('Set ANDROID_HOME to the Android SDK.')
    tool = Path(sdk) / 'cmdline-tools/latest/bin/apkanalyzer'
    if not tool.exists():
        raise SetupError(f'{tool} is missing: install the SDK command-line tools (cmdline-tools;latest)')
    return str(tool)


def manifest_text(apk: Path) -> str:
    result = subprocess.run([apkanalyzer(), 'manifest', 'print', str(apk)], capture_output=True, text=True, timeout=300)
    if result.returncode:
        raise SetupError(f'apkanalyzer could not read the manifest of {apk}: {result.stderr.strip()}')
    return result.stdout


# --- run ------------------------------------------------------------------------------

def read_build(source: Path, work: Path) -> dict:
    apk = universal_apk(source.resolve(), work)
    badging = re.search(r'android:versionCode="(\d+)"[\s\S]*?android:versionName="([^"]*)"', manifest := manifest_text(apk))
    (work / 'AndroidManifest.xml').write_text(manifest)
    return dict(source=str(source), apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                version_code=int(badging[1]) if badging else None, version_name=badging[2] if badging else None,
                manifest=manifest_facts(manifest), dex=dex_counts(apk, work))


def check(previous: dict, candidate: dict, expected: set[str]) -> dict:
    problems = dex_problems(candidate['dex'])
    manifest_failures, changes = manifest_problems(previous['manifest'], candidate['manifest'], expected)
    problems += manifest_failures
    if candidate['version_code'] is not None and previous['version_code'] is not None \
            and candidate['version_code'] <= previous['version_code']:
        problems.append(f'candidate version {candidate["version_code"]} is not newer than {previous["version_code"]}')
    return dict(passed=not problems, problems=problems, manifest_changes=changes)


def summary_lines(name: str, build: dict) -> list[str]:
    lines = [f'{name}: {build["version_name"]} ({build["version_code"]}) {build["source"]}']
    lines += [f'  call {call}: {count}' for call, count in build['dex']['calls'].items()]
    lines += [f'  const-string "{key}": {count}' for key, count in build['dex']['const_strings'].items()]
    lines.append(f'  permissions ({len(build["manifest"]["permissions"])}): {", ".join(build["manifest"]["permissions"])}')
    return lines


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--candidate', type=Path, required=True, help='the release candidate: .aab, universal .apks or .apk')
    parser.add_argument('--previous', type=Path, required=True, help='the last published build, same formats')
    parser.add_argument('--out', type=Path, required=True, help='new folder for report.json, manifests and the diff')
    parser.add_argument('--expect-permission', action='append', default=[], metavar='+NAME|-NAME',
                        help='a permission the candidate adds (+) or removes (-) on purpose; repeat for each')
    args = parser.parse_args(argv)
    if bad := [p for p in args.expect_permission if p[:1] not in '+-' or len(p) < 2]:
        parser.error(f'--expect-permission takes +NAME or -NAME, got {bad}')
    if args.out.exists() and any(args.out.iterdir()):
        parser.error(f'{args.out} is not empty; give each run its own folder')
    args.out.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory() as tmp:
            builds = {}
            for name, source in (('previous', args.previous), ('candidate', args.candidate)):
                if not source.is_file():
                    raise SetupError(f'{source} does not exist')
                builds[name] = read_build(source, Path(tmp) / name)
                shutil.copy(Path(tmp) / name / 'AndroidManifest.xml', args.out / f'manifest-{name}.xml')
    except (SetupError, subprocess.SubprocessError, OSError, zipfile.BadZipFile) as error:
        print(f'bundle check could not run: {error}', file=sys.stderr)
        (args.out / 'setup-error.txt').write_text(f'{error}\n')
        return 2
    verdict = check(builds['previous'], builds['candidate'], set(args.expect_permission))
    (args.out / 'manifest.diff').write_text(''.join(difflib.unified_diff(
        (args.out / 'manifest-previous.xml').read_text().splitlines(True),
        (args.out / 'manifest-candidate.xml').read_text().splitlines(True), 'previous', 'candidate')))
    summary = summary_lines('previous', builds['previous']) + summary_lines('candidate', builds['candidate'])
    summary += [f'manifest change: {c}' for c in verdict['manifest_changes']]
    summary += [f'FAIL {p}' for p in verdict['problems']] or ['PASS']
    (args.out / 'summary.txt').write_text('\n'.join(summary) + '\n')
    (args.out / 'report.json').write_text(json.dumps(dict(builds=builds, **verdict), indent=1, ensure_ascii=False) + '\n')
    print('\n'.join(summary))
    return 0 if verdict['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
