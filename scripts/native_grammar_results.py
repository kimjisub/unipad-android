#!/usr/bin/env python3
"""Run the reviewed native pack checks and preserve their requirement-level evidence.

Does not change the shared conformance reporter. Audio decoding and the real native
voice path are recorded separately from optional external emulator output checks.
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
import sys
import xml.etree.ElementTree as ET

PREFIX = 'com.kimjisub.launchpad.'
ROOT = PREFIX + 'unipack.PackRootNamesDeviceTest'
ARCHIVE = PREFIX + 'UniPackLegacyArchiveDeviceTest'
DECODE = PREFIX + 'audio.AudioFormatDecodeDeviceTest'
PAD = PREFIX + 'audio.AudioFormatPadDeviceTest'
OUTPUT = PREFIX + 'audio.EmulatorOutput'
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
for item, decode, pad, output in [
    ('A-01', 'decodesPcmWav', 'wavReachesItsPad', 'wavIsHeardAtTheEmulatorOutput'),
    ('A-02', 'decodesMp3', 'mp3ReachesItsPad', 'mp3IsHeardAtTheEmulatorOutput'),
    ('A-03', 'decodesOggVorbis', 'oggReachesItsPad', 'oggIsHeardAtTheEmulatorOutput'),
    ('A-04', 'decodesAacInM4a', 'm4aReachesItsPad', 'm4aIsHeardAtTheEmulatorOutput'),
]:
    REQUIRED[item] = [DECODE + '#' + decode, PAD + '#' + pad, OUTPUT + '#' + output]
REQUIRED['A-05'] = [DECODE + '#decodesMp3NamedWavByItsContent',
                    DECODE + '#decodesTaggedMp3NamedWavByItsContent',
                    PAD + '#bareMp3NamedWavReachesItsPad', PAD + '#taggedMp3NamedWavReachesItsPad',
                    OUTPUT + '#bareMp3NamedWavIsHeardAtTheEmulatorOutput',
                    OUTPUT + '#taggedMp3NamedWavIsHeardAtTheEmulatorOutput']


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


def read_audio_results(directory, revision, serial):
    """Read only this runner's external output round, retaining its original verdicts."""
    results = read_results(directory)
    results = {name: records for name, records in results.items() if name.startswith(OUTPUT + '#')}
    try:
        metadata = json.loads((directory / 'metadata.json').read_text())
        report = json.loads((directory / 'results.json').read_text())
        commands = json.loads((directory / 'commands.json').read_text())
        verified = (metadata['source_commit'] == revision and metadata['serial'] == serial and
                    metadata['source_diff'] == '' and metadata['status'] == '' and
                    all(metadata[key] for key in ['os', 'emulator_version', 'apk_sha256', 'test_apk_sha256']) and
                    report['exit_code'] == 0)
        for command_type in ['instrument', 'record']:
            traces = [trace for trace in commands if command_type in trace.get('command', [])]
            verified = verified and len(traces) == 1 and traces[0].get('exit') == 0
        for filename in ['stream.wav', 'stream.packets.json', 'recordings.json', 'commands.json',
                         'instrumentation.txt', 'stream-capture.txt']:
            verified = verified and (directory / filename).is_file() and (directory / filename).stat().st_size > 0
        for name, records in results.items():
            case_name = name.split('#', 1)[1]
            originals = [case for case in report['results'] if case.get('name') == case_name]
            waveform = directory / (case_name + '.wav')
            if (len(originals) != 1 or originals[0].get('status') != 'passed' or
                    not waveform.is_file() or waveform.stat().st_size == 0):
                for record in records:
                    if record['status'] == 'passed':
                        record['status'] = 'unverified'
    except (OSError, ValueError, KeyError, TypeError):
        verified = False
    return results, bool(verified)


def item_results(results, environment_verified, output_verified=False):
    items = {}
    for item, names in REQUIRED.items():
        status = verdict(names, results)
        reasons = []
        if not environment_verified:
            reasons.append('Execution environment or clean source revision was not verified')
        if item.startswith('A-') and not output_verified:
            reasons.append('External output round, raw waveform, environment or clean source revision was not verified')
        if reasons and status == 'passed':
            status = 'unverified'
        items[item] = {'status': status, 'requiredTests': names, 'reasons': reasons,
                       'tests': {name: results.get(name, []) for name in names}}
    return items


def same_directory(first, second):
    return first.exists() and second.exists() and os.path.samefile(first, second)


def overlaps(source, output):
    """Whether output is source, lies inside it, or contains it.

    Besides the resolved paths, existing ancestors are compared by on-disk identity, which
    also catches case aliases on case-insensitive disks.
    """
    resolved = source.resolve()
    if output.is_relative_to(resolved) or resolved.is_relative_to(output):
        return True
    source = source.absolute()
    return (any(same_directory(ancestor, source) for ancestor in (output, *output.parents)) or
            any(same_directory(ancestor, output) for ancestor in (source, *source.parents)))


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
    parser.add_argument('--with-audio-output', action='store_true',
                        help='Also run the external output suite on the audio-enabled leased emulator')
    parser.add_argument('--devices-tool', type=Path, help='Managed devices.py used for external recording')
    args = parser.parse_args()
    if args.with_audio_output and (not args.devices_tool or not args.devices_tool.is_file()):
        parser.error('--with-audio-output requires an existing --devices-tool')
    output = args.output.resolve()
    if output.exists():
        parser.error('Output already exists; preserve previous rounds in a different child directory')
    unit = Path('app/build/test-results/testDebugUnitTest')
    connected = Path('app/build/outputs/androidTest-results/connected')
    diagnostics = Path('.ci-results')
    sources = (unit, connected, diagnostics)
    for source in sources:
        if overlaps(source, output):
            parser.error(f'Output must not overlap result source {source} (including symlinks)')
    serial = os.environ.get('ANDROID_SERIAL')
    if not serial:
        parser.error('ANDROID_SERIAL must identify the leased device')
    existing_parent = output.parent
    while not existing_parent.exists():
        existing_parent = existing_parent.parent
    # Use the checked path for creation and all writes so '..' creates no extra directories.
    args.output = output
    args.output.mkdir(parents=True)
    # Creating the output can also create a missing source, or its parents, via a case alias.
    for source in sources:
        if overlaps(source, output):
            created = output
            while created != existing_parent:
                try:
                    created.rmdir()
                except OSError:
                    break
                created = created.parent
            parser.error(f'Output must not overlap result source {source} (including symlinks)')
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
    results = read_results(args.output / 'raw')
    # Output names in unit/device XML cannot stand in for an external recording.
    results = {name: records for name, records in results.items() if not name.startswith(OUTPUT + '#')}
    output_verified = False
    if args.with_audio_output:
        commands.append(run(['./gradlew', ':app:assembleDebug', ':app:assembleDebugAndroidTest',
                             '--no-daemon', '--console=plain'], args.output / 'audio-build.log'))
        if commands[-1]['exitCode'] == 0:
            audio_directory = args.output / 'raw/audio-output'
            commands.append(run([sys.executable, 'scripts/run_audio_output.py', '--serial', serial,
                                 '--devices-tool', str(args.devices_tool.resolve()),
                                 '--apk', 'app/build/outputs/apk/debug/app-debug.apk',
                                 '--test-apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
                                 '--output', str(audio_directory)], args.output / 'audio-output.log'))
            audio_results, output_verified = read_audio_results(audio_directory, revision, serial)
            for records in audio_results.values():
                for record in records:
                    record['raw'] = 'audio-output/' + record['raw']
            results.update(audio_results)
    final_revision = capture(['git', 'rev-parse', 'HEAD'])
    final_dirty = capture(['git', 'status', '--porcelain', '--untracked-files=all'])
    environment_verified = (not dirty and all(environment.values()) and
                            final_revision == revision and final_dirty == dirty)
    items = item_results(results, environment_verified, output_verified)
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
        'sourceBefore': {'commit': revision, 'status': dirty},
        'sourceAfter': {'commit': final_revision, 'status': final_dirty},
        'audioOutput': {'requested': args.with_audio_output, 'verified': output_verified},
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
