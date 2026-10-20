#!/usr/bin/env python3
"""Run the reviewed native pack checks and preserve their requirement-level evidence.

Does not change the shared conformance reporter. Audio decoding and the real native
voice path are recorded separately from an output waveform, which is not measured.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import xml.etree.ElementTree as ET

PREFIX = 'com.kimjisub.launchpad.'
ROOT = PREFIX + 'unipack.PackRootNamesDeviceTest'
ARCHIVE = PREFIX + 'UniPackLegacyArchiveDeviceTest'
DECODE = PREFIX + 'audio.AudioFormatDecodeDeviceTest'
PAD = PREFIX + 'audio.AudioFormatPadDeviceTest'
REQUIRED = {
    'F-01': [ROOT + '#' + name for name in [
        'uppercaseRootNamesLoadOnCaseSensitiveStorage', 'infoWithAnExtensionIsNotInfo',
        'pluralKeySoundsIsNotKeySound']],
    'F-05': [PREFIX + 'manager.FileManagerTest#' + name for name in [
        'removeDoubleFolder_opensThePackFolderBesideAReadme',
        'removeDoubleFolder_opensAPackWrappedTwiceBesideAReadme',
        'removeDoubleFolder_opensThePackFolderBesideFilesAFolderWithoutInfoAndMacosx',
        'removeDoubleFolder_keepsPackWrappedInTwoFoldersOfTheSameNameBesideMacosx',
        'removeDoubleFolder_ignoresHiddenFilesWhenDetecting',
    ]] + [ARCHIVE + '#aKoreanWindowsArchiveWithAReadmeBesideThePackImports'],
    'F-06': [PREFIX + 'manager.PackArchiveTest#' + name for name in [
        'utf8NamesWithTheUtf8Flag_areReadAsUtf8',
        'utf8NamesWithoutTheUtf8Flag_areReadAsUtf8',
        'cp949NamesWithoutTheUtf8Flag_areReadAsCp949',
    ]] + [ARCHIVE + '#theDeviceReadsCp949', ARCHIVE + '#aKoreanWindowsArchiveWithAReadmeBesideThePackImports'],
}
for item, decode, pad in [
    ('A-01', 'decodesPcmWav', 'wavReachesItsPad'),
    ('A-02', 'decodesMp3', 'mp3ReachesItsPad'),
    ('A-03', 'decodesOggVorbis', 'oggReachesItsPad'),
    ('A-04', 'decodesAacInM4a', 'm4aReachesItsPad'),
]:
    REQUIRED[item] = [DECODE + '#' + decode, PAD + '#' + pad]
REQUIRED['A-05'] = [DECODE + '#decodesMp3NamedWavByItsContent',
                    DECODE + '#decodesTaggedMp3NamedWavByItsContent',
                    PAD + '#bareMp3NamedWavReachesItsPad', PAD + '#taggedMp3NamedWavReachesItsPad']


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_results(directory):
    results = {}
    for path in sorted(directory.rglob('*.xml')):
        tree = ET.parse(path)
        for case in tree.iter('testcase'):
            name = case.get('classname', '') + '#' + case.get('name', '')
            status = 'passed'
            if case.find('failure') is not None or case.find('error') is not None:
                status = 'failed'
            elif case.find('skipped') is not None or case.get('status') in ('notrun', 'skipped'):
                status = 'unverified'
            results.setdefault(name, []).append({
                'status': status, 'seconds': case.get('time'),
                'raw': str(path.relative_to(directory)), 'sha256': sha256(path),
            })
    return results


def verdict(names, results):
    entries = [results.get(name, []) for name in names]
    if any(record['status'] == 'failed' for records in entries for record in records):
        return 'failed'
    if any(len(records) != 1 or records[0]['status'] != 'passed' for records in entries):
        return 'unverified'
    return 'passed'


def item_results(results, environment_verified):
    items = {}
    for item, names in REQUIRED.items():
        status = verdict(names, results)
        reasons = []
        if not environment_verified:
            reasons.append('Execution environment or clean source revision was not verified')
        if item.startswith('A-'):
            reasons.append('Output waveform is not captured; decoder PCM and native voice acceptance alone do not verify rendered output')
        if reasons and status == 'passed':
            status = 'unverified'
        items[item] = {'status': status, 'requiredTests': names, 'reasons': reasons,
                       'tests': {name: results.get(name, []) for name in names}}
    return items


def capture(command):
    return subprocess.check_output(command, text=True).strip()


def run(command, output):
    started = datetime.now(timezone.utc).isoformat()
    with output.open('w') as log:
        completed = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=False)
    return {'command': command, 'exitCode': completed.returncode, 'startedAt': started,
            'finishedAt': datetime.now(timezone.utc).isoformat(), 'log': output.name}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path, help='New immutable result directory')
    parser.add_argument('--grammar-version', required=True)
    parser.add_argument('--grammar-revision', required=True)
    args = parser.parse_args()
    unit = Path('app/build/test-results/testDebugUnitTest')
    connected = Path('app/build/outputs/androidTest-results/connected')
    diagnostics = Path('.ci-results')
    output = args.output.resolve()
    for source in (unit, connected, diagnostics):
        resolved_source = source.resolve()
        if output.is_relative_to(resolved_source) or resolved_source.is_relative_to(output):
            parser.error(f'Output must not overlap result source {source} (including symlinks)')
    serial = os.environ.get('ANDROID_SERIAL')
    if not serial:
        parser.error('ANDROID_SERIAL must identify the leased device')
    if args.output.exists():
        parser.error('Output already exists; preserve previous rounds in a different child directory')
    args.output.mkdir(parents=True)
    revision = capture(['git', 'rev-parse', 'HEAD'])
    dirty = capture(['git', 'status', '--porcelain', '--untracked-files=all'])
    environment = {'host': platform.platform(), 'python': platform.python_version(), 'serial': serial}
    for prop in ['ro.build.fingerprint', 'ro.build.version.sdk', 'ro.product.cpu.abi']:
        environment[prop] = capture(['adb', '-s', serial, 'shell', 'getprop', prop])
    commands = []
    if unit.exists():
        shutil.rmtree(unit)
    commands.append(run(['./gradlew', ':app:testDebugUnitTest', '--rerun', '--tests', PREFIX + 'manager.FileManagerTest',
                         '--tests', PREFIX + 'manager.PackArchiveTest', '--no-daemon', '--console=plain'],
                        args.output / 'unit.log'))
    if unit.exists():
        shutil.copytree(unit, args.output / 'raw/unit')
    commands.append(run(['bash', 'scripts/ci-connected-tests.sh', ROOT, ARCHIVE, DECODE, PAD],
                        args.output / 'device.log'))
    if connected.exists():
        shutil.copytree(connected, args.output / 'raw/device')
    if diagnostics.exists():
        shutil.copytree(diagnostics, args.output / 'diagnostics')
    raw = args.output / 'raw'
    results = read_results(raw)
    environment_verified = (not dirty and all(environment.values()) and
                            capture(['git', 'rev-parse', 'HEAD']) == revision and
                            capture(['git', 'status', '--porcelain', '--untracked-files=all']) == dirty)
    items = item_results(results, environment_verified)
    if any(command['exitCode'] != 0 for command in commands):
        for item in items.values():
            if item['status'] == 'passed':
                item['status'] = 'unverified'
            item['reasons'].append('A test command failed; see its exit code and original output')
    logcat = args.output / 'diagnostics/logcat.txt'
    probes = [line for line in logcat.read_text(errors='replace').splitlines()
              if 'case-sensitive probe passed at ' in line] if logcat.exists() else []
    if len(probes) < 3 and items['F-01']['status'] == 'passed':
        items['F-01']['status'] = 'unverified'
        items['F-01']['reasons'].append('Original case-sensitivity probe log is missing')
    fixtures = Path('app/src/androidTest/assets/audio-formats')
    report = {
        'schemaVersion': 1, 'platform': 'android', 'repositoryCommit': revision,
        'sourceDirty': bool(dirty), 'environment': environment,
        'caseSensitivityProbeLog': probes,
        'grammar': {'version': args.grammar_version, 'revision': args.grammar_revision},
        'commonCorpus': {'sha256': None, 'reason': 'Standalone native fixtures; no shared corpus consumed'},
        'fixtureSha256': {path.name: sha256(path) for path in sorted(fixtures.iterdir()) if path.is_file()},
        'commands': commands, 'items': items,
        'rawFiles': {str(path.relative_to(args.output)): sha256(path)
                     for path in sorted(args.output.rglob('*')) if path.is_file()},
    }
    (args.output / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({item: value['status'] for item, value in items.items()}, indent=2))
    return 1 if any(command['exitCode'] != 0 for command in commands) else 0


if __name__ == '__main__':
    raise SystemExit(main())
