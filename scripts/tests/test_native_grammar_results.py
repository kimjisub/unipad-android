import contextlib
import json
import io
from unittest.mock import patch
import importlib.util
from pathlib import Path
import tempfile
import unittest

OUTPUT = 'com.kimjisub.launchpad.audio.EmulatorOutput#'
OUTPUT_TESTS = {
    'A-01': ['wavIsHeardAtTheEmulatorOutput'],
    'A-02': ['mp3IsHeardAtTheEmulatorOutput'],
    'A-03': ['oggIsHeardAtTheEmulatorOutput'],
    'A-04': ['m4aIsHeardAtTheEmulatorOutput'],
    'A-05': ['bareMp3NamedWavIsHeardAtTheEmulatorOutput',
             'taggedMp3NamedWavIsHeardAtTheEmulatorOutput'],
}

SPEC = importlib.util.spec_from_file_location('native_results', Path(__file__).parents[1] / 'native_grammar_results.py')
MISSING_SOURCE_OUTPUTS = [
    (source, variant)
    for source, spelling in [
        ('.ci-results', '.CI-RESULTS/round/nested'),
        ('app/build/test-results/testDebugUnitTest', 'app/build/test-results/TESTdEBUGuNITtEST/round/nested'),
        ('app/build/outputs/androidTest-results/connected', 'app/build/outputs/androidTest-results/CONNECTED/round/nested'),
    ]
    for variant in [spelling, spelling.replace('/round/', '/unused/../round/'),
                    'unrelated/../' + spelling]
]
# Outputs that would contain a missing source once a case alias creates the source's parents.
MISSING_SOURCE_CONTAINERS = [
    (source, variant)
    for source, spelling in [
        ('app/build/test-results/testDebugUnitTest', 'APP/BUILD/test-results'),
        ('app/build/outputs/androidTest-results/connected', 'app/BUILD/outputs/androidTest-results'),
        ('app/build/test-results/testDebugUnitTest', 'APP/BUILD'),
    ]
    for variant in [spelling, 'unrelated/../' + spelling]
]


class NativeGrammarResultsTest(unittest.TestCase):
    def load(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        return module

    def case_aliases_share_directory(self, root):
        with tempfile.TemporaryDirectory(dir=root) as probe:
            lower = Path(probe) / 'case-probe'
            upper = Path(probe) / 'CASE-PROBE'
            lower.mkdir()
            return upper.exists() and upper.samefile(lower)

    def test_missing_source_case_alias_is_rejected_and_new_directories_are_removed(self):
        for source, spelling in MISSING_SOURCE_OUTPUTS:
            for absolute in [False, True]:
                with self.subTest(source=source, spelling=spelling, absolute=absolute), \
                        tempfile.TemporaryDirectory() as directory, contextlib.chdir(directory):
                    if not self.case_aliases_share_directory(directory):
                        self.skipTest('Temporary filesystem distinguishes case; missing-source aliases are distinct')
                    module = self.load()
                    source_path = Path(source)
                    source_path.parent.mkdir(parents=True, exist_ok=True)
                    sentinel = source_path.parent / 'original.txt'
                    if absolute:
                        sentinel.write_text('keep existing evidence')
                        (source_path.parent / 'empty-existing').mkdir()
                    before = set(Path('.').rglob('*'))
                    self.assertFalse(source_path.exists())
                    output = Path(spelling).absolute() if absolute else Path(spelling)
                    arguments = ['native_grammar_results.py', '--output', str(output),
                                 '--grammar-version', 'test', '--grammar-revision', 'test-revision']
                    errors = io.StringIO()
                    with patch('sys.argv', arguments), \
                            patch.dict('os.environ', {'ANDROID_SERIAL': 'leased-device'}), \
                            patch.object(module, 'capture', side_effect=AssertionError('External work started')), \
                            patch.object(module, 'run', side_effect=AssertionError('Test command started')), \
                            patch.object(module.shutil, 'rmtree', side_effect=AssertionError('Source removal started')), \
                            patch.object(module.shutil, 'copytree', side_effect=AssertionError('Source copy started')), \
                            contextlib.redirect_stderr(errors):
                        with self.assertRaises(SystemExit) as raised:
                            module.main()
                    self.assertEqual(2, raised.exception.code)
                    self.assertIn('overlap', errors.getvalue())
                    self.assertFalse(output.exists())
                    self.assertFalse(source_path.exists())
                    self.assertTrue(Path(directory).is_dir())
                    self.assertEqual(before, set(Path('.').rglob('*')))
                    if absolute:
                        self.assertEqual('keep existing evidence', sentinel.read_text())

    def test_output_containing_a_missing_source_case_alias_is_rejected(self):
        for source, spelling in MISSING_SOURCE_CONTAINERS:
            for absolute in [False, True]:
                for existing_app in [False, True]:
                    with self.subTest(source=source, spelling=spelling, absolute=absolute, existing=existing_app), \
                            tempfile.TemporaryDirectory() as directory, contextlib.chdir(directory):
                        if not self.case_aliases_share_directory(directory):
                            self.skipTest('Temporary filesystem distinguishes case; missing-source aliases are distinct')
                        module = self.load()
                        if existing_app:
                            Path('app').mkdir()
                            Path('app/original.txt').write_text('keep existing evidence')
                        before = set(Path('.').rglob('*'))
                        self.assertFalse(Path(source).exists())
                        output = Path(spelling).absolute() if absolute else Path(spelling)
                        arguments = ['native_grammar_results.py', '--output', str(output),
                                     '--grammar-version', 'test', '--grammar-revision', 'test-revision']
                        errors = io.StringIO()
                        with patch('sys.argv', arguments), \
                                patch.dict('os.environ', {'ANDROID_SERIAL': 'leased-device'}), \
                                patch.object(module, 'capture', side_effect=AssertionError('External work started')), \
                                patch.object(module, 'run', side_effect=AssertionError('Test command started')), \
                                contextlib.redirect_stderr(errors):
                            with self.assertRaises(SystemExit) as raised:
                                module.main()
                        self.assertEqual(2, raised.exception.code)
                        self.assertIn('overlap', errors.getvalue())
                        self.assertFalse(output.exists())
                        self.assertEqual(before, set(Path('.').rglob('*')))
                        if existing_app:
                            self.assertEqual('keep existing evidence', Path('app/original.txt').read_text())

    def test_missing_source_different_case_is_admitted_on_case_sensitive_filesystem(self):
        for source, spelling in MISSING_SOURCE_OUTPUTS + MISSING_SOURCE_CONTAINERS:
            with self.subTest(source=source, spelling=spelling), tempfile.TemporaryDirectory() as directory, \
                    contextlib.chdir(directory):
                if self.case_aliases_share_directory(directory):
                    self.skipTest('Temporary filesystem aliases case; distinct-case paths require a case-sensitive disk')
                module = self.load()
                self.assertFalse(Path(source).exists())
                output = Path(spelling)
                arguments = ['native_grammar_results.py', '--output', str(output),
                             '--grammar-version', 'test', '--grammar-revision', 'test-revision']
                with patch('sys.argv', arguments), \
                        patch.dict('os.environ', {'ANDROID_SERIAL': 'leased-device'}), \
                        patch.object(module, 'capture', side_effect=AssertionError('Admitted output')), \
                        patch.object(module, 'run', side_effect=AssertionError('Test command started')):
                    with self.assertRaisesRegex(AssertionError, 'Admitted output'):
                        module.main()
                self.assertTrue(output.resolve().is_dir())
                self.assertFalse(Path('unrelated').exists())
                self.assertFalse((output.resolve().parents[1] / 'unused').exists())
                self.assertFalse(Path(source).exists())

    def test_rejected_missing_source_alias_preserves_new_directories_with_content(self):
        for source, spelling in MISSING_SOURCE_OUTPUTS:
            for populated in ['output', 'parent']:
                with self.subTest(source=source, spelling=spelling, populated=populated), \
                        tempfile.TemporaryDirectory() as directory, contextlib.chdir(directory):
                    if not self.case_aliases_share_directory(directory):
                        self.skipTest('Temporary filesystem distinguishes case; missing-source aliases are distinct')
                    module = self.load()
                    self.assertFalse(Path(source).exists())
                    output = Path(spelling)
                    sentinel = (output if populated == 'output' else output.parent).resolve() / 'keep.txt'
                    mkdir = Path.mkdir

                    def create_with_content(path, *args, **kwargs):
                        mkdir(path, *args, **kwargs)
                        if path.resolve() == output.resolve():
                            sentinel.write_text('keep new content')

                    arguments = ['native_grammar_results.py', '--output', str(output),
                                 '--grammar-version', 'test', '--grammar-revision', 'test-revision']
                    errors = io.StringIO()
                    with patch('sys.argv', arguments), \
                            patch.dict('os.environ', {'ANDROID_SERIAL': 'leased-device'}), \
                            patch.object(Path, 'mkdir', create_with_content), \
                            patch.object(module, 'capture', side_effect=AssertionError('External work started')), \
                            contextlib.redirect_stderr(errors):
                        with self.assertRaises(SystemExit) as raised:
                            module.main()
                    self.assertEqual(2, raised.exception.code)
                    self.assertIn('overlap', errors.getvalue())
                    self.assertEqual('keep new content', sentinel.read_text())
                    self.assertEqual(populated == 'output', output.resolve().exists())

    def test_missing_skipped_failed_and_duplicate_never_pass(self):
        module = self.load()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'test.xml').write_text('''<testsuite><testcase classname="Example" name="ok"/>
            <testcase classname="Example" name="skip"><skipped/></testcase>
            <testcase classname="Example" name="bad"><failure>wrong value</failure></testcase>
            <testcase classname="Example" name="duplicate"/><testcase classname="Example" name="duplicate"/>
            </testsuite>''')
            results = module.read_results(root)
            self.assertEqual(module.verdict(['Example#ok'], results), 'passed')
            for name in ['missing', 'skip', 'duplicate']:
                self.assertEqual(module.verdict(['Example#' + name], results), 'unverified')
            self.assertEqual(module.verdict(['Example#ok', 'Example#bad'], results), 'failed')
            self.assertEqual(module.verdict(['Example#ok', 'Example#missing'], results), 'unverified')

    def test_audio_is_unverified_without_output_waveform(self):
        module = self.load()
        results = {name: [{'status': 'passed'}] for names in module.REQUIRED.values() for name in names}
        self.assertEqual(module.item_results(results, environment_verified=True)['A-01']['status'], 'unverified')
        self.assertEqual(module.item_results(results, environment_verified=True)['F-01']['status'], 'passed')
        self.assertEqual(module.item_results(results, environment_verified=False)['F-01']['status'], 'unverified')

    def test_required_output_names_are_fixed_independently_of_results(self):
        module = self.load()
        for item, names in OUTPUT_TESTS.items():
            self.assertEqual([OUTPUT + name for name in names],
                             [name for name in module.REQUIRED[item] if name.startswith(OUTPUT)])

    def make_output_round(self, root, missing=None, duplicate=None, status='passed'):
        names = [name for tests in OUTPUT_TESTS.values() for name in tests if name != missing]
        if duplicate:
            names.append(duplicate)
        cases = ''.join(f'<testcase classname="{OUTPUT[:-1]}" name="{name}">' +
                        ('<failure/>' if status == 'failed' else
                         '<error/>' if status == 'environment_unconfirmed' else '') +
                        '</testcase>' for name in names)
        (root / 'TEST-EmulatorOutput.xml').write_text('<testsuite>' + cases + '</testsuite>')
        (root / 'results.json').write_text(json.dumps({'exit_code': 0 if status == 'passed' else 3,
            'results': [{'name': name, 'status': status} for name in names]}))
        (root / 'metadata.json').write_text(json.dumps({
            'source_commit': 'current-commit', 'source_diff': '', 'status': '',
            'serial': 'leased-device', 'os': 'device-properties', 'emulator_version': 'version',
            'apk_sha256': 'a' * 64, 'test_apk_sha256': 'b' * 64}))
        for filename in ['stream.wav', 'stream.packets.json', 'recordings.json', 'commands.json',
                         'instrumentation.txt', 'stream-capture.txt'] + [name + '.wav' for name in names]:
            (root / filename).write_text('original evidence')
        (root / 'commands.json').write_text(json.dumps([
            {'command': ['adb', '-s', 'leased-device', 'shell', 'am', 'instrument'], 'exit': 0},
            {'command': ['python3', 'devices.py', 'record', 'leased-device', 'stream.wav', '120'], 'exit': 0},
        ]))

    def output_items(self, module, root, environment=True):
        results = {name: [{'status': 'passed'}] for names in module.REQUIRED.values()
                   for name in names if not name.startswith(OUTPUT)}
        audio, verified = module.read_audio_results(root, 'current-commit', 'leased-device')
        results.update(audio)
        return module.item_results(results, environment, verified)

    def test_complete_output_can_verify_all_five_audio_items(self):
        module = self.load()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_output_round(root)
            items = self.output_items(module, root)
            self.assertEqual(['passed'] * 5, [items[item]['status'] for item in OUTPUT_TESTS])
            self.assertEqual('unverified', self.output_items(module, root, False)['A-01']['status'])

    def test_each_mp3_named_wav_output_is_required(self):
        module = self.load()
        for name in OUTPUT_TESTS['A-05']:
            with self.subTest(missing=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.make_output_round(root, missing=name)
                items = self.output_items(module, root)
                self.assertEqual('unverified', items['A-05']['status'])
                self.assertEqual('passed', items['A-01']['status'])

    def test_failed_unconfirmed_and_duplicate_output_never_pass(self):
        module = self.load()
        for status, duplicate in [('failed', None), ('environment_unconfirmed', None),
                                  ('passed', OUTPUT_TESTS['A-01'][0])]:
            with self.subTest(status=status, duplicate=duplicate), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.make_output_round(root, status=status, duplicate=duplicate)
                self.assertNotEqual('passed', self.output_items(module, root)['A-01']['status'])

    def test_output_from_another_revision_dirty_source_or_device_never_passes(self):
        module = self.load()
        for key, value in [('source_commit', 'other-commit'), ('source_diff', 'tracked edit'),
                           ('status', '?? untracked-file'), ('serial', 'other-device'), ('os', '')]:
            with self.subTest(key=key), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.make_output_round(root)
                metadata = json.loads((root / 'metadata.json').read_text())
                metadata[key] = value
                (root / 'metadata.json').write_text(json.dumps(metadata))
                self.assertEqual('unverified', self.output_items(module, root)['A-01']['status'])

    def test_missing_raw_waveform_clock_or_metadata_never_passes(self):
        module = self.load()
        for name in ['metadata.json', 'recordings.json', 'stream.packets.json', 'stream.wav',
                     'commands.json', 'results.json', 'wavIsHeardAtTheEmulatorOutput.wav']:
            with self.subTest(missing=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.make_output_round(root)
                (root / name).unlink()
                self.assertEqual('unverified', self.output_items(module, root)['A-01']['status'])

    def test_missing_or_failed_screen_and_recorder_exit_never_passes(self):
        module = self.load()
        for index in [0, 1]:
            for exit_code in [None, 7]:
                with self.subTest(command=index, exit=exit_code), tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    self.make_output_round(root)
                    commands = json.loads((root / 'commands.json').read_text())
                    if exit_code is None:
                        del commands[index]['exit']
                    else:
                        commands[index]['exit'] = exit_code
                    (root / 'commands.json').write_text(json.dumps(commands))
                    self.assertEqual('unverified', self.output_items(module, root)['A-01']['status'])

    def test_selected_output_run_is_preserved_separately_and_hashed(self):
        for selected, audio_exit in [(False, 0), (True, 0), (True, 3)]:
            with self.subTest(selected=selected, exit=audio_exit), tempfile.TemporaryDirectory() as directory, \
                    tempfile.TemporaryDirectory() as evidence, contextlib.chdir(directory):
                module = self.load()
                Path('app/src/androidTest/assets/audio-formats').mkdir(parents=True)
                Path('devices.py').write_text('managed device tool')

                def capture(command):
                    if command == ['git', 'rev-parse', 'HEAD']:
                        return 'current-commit'
                    if command[:2] == ['git', 'status']:
                        return ''
                    return 'device-property'

                def run(command, log):
                    if 'scripts/run_audio_output.py' in command:
                        self.assertIn('--devices-tool', command)
                        self.assertEqual('leased-device', command[command.index('--serial') + 1])
                        root = Path(command[command.index('--output') + 1])
                        root.mkdir(parents=True)
                        self.make_output_round(root, status='passed' if audio_exit == 0 else 'environment_unconfirmed')
                        code = audio_exit
                    elif command[0] == 'bash':
                        root = Path('app/build/outputs/androidTest-results/connected')
                        root.mkdir(parents=True)
                        cases = ''.join(f'<testcase classname="{name.split("#")[0]}" name="{name.split("#")[1]}"/>'
                                        for tests in module.REQUIRED.values() for name in tests)
                        # Even forged output names in connected XML cannot replace host recordings.
                        (root / 'TEST-Device.xml').write_text('<testsuite>' + cases + '</testsuite>')
                        Path('.ci-results').mkdir()
                        Path('.ci-results/logcat.txt').write_text('case-sensitive probe passed at fixture\n' * 3)
                        code = 0
                    else:
                        self.assertEqual('./gradlew', command[0])
                        code = 0
                    log.write_text('original command output\n')
                    return {'command': command, 'exitCode': code, 'log': log.name}

                arguments = ['native_grammar_results.py', '--output', str(Path(evidence) / 'round'),
                             '--grammar-version', 'G3', '--grammar-revision', 'grammar-sha']
                if selected:
                    arguments += ['--with-audio-output', '--devices-tool', 'devices.py']
                with patch('sys.argv', arguments), patch.dict('os.environ', {'ANDROID_SERIAL': 'leased-device'}), \
                        patch.object(module, 'capture', side_effect=capture), patch.object(module, 'run', side_effect=run):
                    self.assertEqual(0 if audio_exit == 0 else 1, module.main())
                root = Path(evidence) / 'round'
                report = json.loads((root / 'results.json').read_text())
                self.assertEqual(selected, report['audioOutput']['requested'])
                self.assertEqual('failed' if audio_exit else 'passed' if selected else 'unverified',
                                 report['items']['A-01']['status'])
                if audio_exit:
                    self.assertNotEqual('passed', report['items']['A-01']['status'])
                self.assertEqual({'commit': 'current-commit', 'status': ''}, report['sourceBefore'])
                self.assertEqual(report['sourceBefore'], report['sourceAfter'])
                if selected:
                    self.assertEqual('original evidence', (root / 'raw/audio-output/stream.wav').read_text())
                    for name in ['stream.wav', 'stream.packets.json', 'recordings.json', 'metadata.json',
                                 'commands.json', 'TEST-EmulatorOutput.xml', 'results.json']:
                        relative = 'raw/audio-output/' + name
                        self.assertEqual(module.sha256(root / relative), report['rawFiles'][relative])
                    self.assertEqual(1, len(report['items']['A-01']['tests'][OUTPUT + OUTPUT_TESTS['A-01'][0]]))
                    self.assertEqual('audio-output/TEST-EmulatorOutput.xml',
                        report['items']['A-01']['tests'][OUTPUT + OUTPUT_TESTS['A-01'][0]][0]['raw'])

    def test_overlapping_output_is_rejected_before_any_work(self):
        sources = ['.ci-results', 'app/build/test-results/testDebugUnitTest',
                   'app/build/outputs/androidTest-results/connected']
        for source in sources:
            for spelling in ['relative', 'absolute', 'parent-components', 'symlink']:
                with self.subTest(source=source, spelling=spelling), \
                        tempfile.TemporaryDirectory() as directory, contextlib.chdir(directory):
                    module = self.load()
                    source_path = Path(source)
                    source_path.mkdir(parents=True)
                    sentinel = source_path / 'original.txt'
                    sentinel.write_text('keep original evidence')
                    output = source_path / 'round'
                    if spelling == 'absolute':
                        output = output.resolve()
                    elif spelling == 'parent-components':
                        output = source_path / 'unused/../round'
                    elif spelling == 'symlink':
                        Path('alias').symlink_to(source_path.resolve(), target_is_directory=True)
                        output = Path('alias/round')
                    arguments = ['native_grammar_results.py', '--output', str(output),
                                 '--grammar-version', 'test', '--grammar-revision', 'test-revision']
                    errors = io.StringIO()
                    with patch('sys.argv', arguments), \
                            patch.dict('os.environ', {'ANDROID_SERIAL': 'leased-device'}), \
                            patch.object(module, 'capture', side_effect=AssertionError('External work started')), \
                            contextlib.redirect_stderr(errors):
                        with self.assertRaises(SystemExit) as raised:
                            module.main()
                    self.assertEqual(2, raised.exception.code)
                    self.assertIn('overlap', errors.getvalue())
                    self.assertFalse(output.exists())
                    self.assertEqual('keep original evidence', sentinel.read_text())

    def test_output_cannot_contain_a_symlinked_result_source(self):
        for source in ['.ci-results', 'app/build/test-results/testDebugUnitTest',
                       'app/build/outputs/androidTest-results/connected']:
            with self.subTest(source=source), tempfile.TemporaryDirectory() as directory, \
                    contextlib.chdir(directory):
                module = self.load()
                output = Path(directory) / 'round'
                source_path = Path(source)
                source_path.parent.mkdir(parents=True, exist_ok=True)
                source_path.symlink_to(output / 'source', target_is_directory=True)
                arguments = ['native_grammar_results.py', '--output', str(output),
                             '--grammar-version', 'test', '--grammar-revision', 'test-revision']
                errors = io.StringIO()
                with patch('sys.argv', arguments), \
                        patch.dict('os.environ', {'ANDROID_SERIAL': 'leased-device'}), \
                        patch.object(module, 'capture', side_effect=AssertionError('External work started')), \
                        contextlib.redirect_stderr(errors):
                    with self.assertRaises(SystemExit) as raised:
                        module.main()
                self.assertEqual(2, raised.exception.code)
                self.assertIn('overlap', errors.getvalue())
                self.assertFalse(output.exists())

    def test_case_alias_output_uses_actual_filesystem_identity(self):
        sources = ['.ci-results', 'app/build/test-results/testDebugUnitTest',
                   'app/build/outputs/androidTest-results/connected']
        for source in sources:
            for absolute in [False, True]:
                for existing_output in [False, True]:
                    with self.subTest(source=source, absolute=absolute, existing=existing_output), \
                            tempfile.TemporaryDirectory() as directory, contextlib.chdir(directory):
                        module = self.load()
                        source_path = Path(source)
                        source_path.mkdir(parents=True)
                        sentinel = source_path / 'original.txt'
                        sentinel.write_text('keep original evidence')
                        alias = source_path.with_name(source_path.name.swapcase())
                        same_directory = alias.exists() and alias.samefile(source_path)
                        output = alias / 'round' / 'nested'
                        if existing_output:
                            output.mkdir(parents=True)
                        if absolute:
                            output = Path(directory) / output
                        arguments = ['native_grammar_results.py', '--output', str(output),
                                     '--grammar-version', 'test', '--grammar-revision', 'test-revision']
                        errors = io.StringIO()
                        with patch('sys.argv', arguments), \
                                patch.dict('os.environ', {'ANDROID_SERIAL': 'leased-device'}), \
                                patch.object(module, 'capture', side_effect=AssertionError('Admitted output')), \
                                contextlib.redirect_stderr(errors):
                            if existing_output or same_directory:
                                with self.assertRaises(SystemExit) as raised:
                                    module.main()
                                self.assertEqual(2, raised.exception.code)
                                self.assertEqual(existing_output, output.exists())
                            else:
                                # A distinct directory on a case-sensitive disk must be admitted.
                                with self.assertRaisesRegex(AssertionError, 'Admitted output'):
                                    module.main()
                                self.assertTrue(output.is_dir())
                        self.assertEqual('keep original evidence', sentinel.read_text())

    def test_existing_output_is_rejected_before_device_or_source_checks(self):
        for spelling in ['existing', 'unused/../existing']:
            module = self.load()
            with self.subTest(spelling=spelling), tempfile.TemporaryDirectory() as directory, \
                    contextlib.chdir(directory):
                Path('existing').mkdir()
                sentinel = Path('existing/original.txt')
                sentinel.write_text('keep previous round')
                arguments = ['native_grammar_results.py', '--output', spelling,
                             '--grammar-version', 'test', '--grammar-revision', 'test-revision']
                errors = io.StringIO()
                with patch('sys.argv', arguments), patch.dict('os.environ', {}, clear=True), \
                        contextlib.redirect_stderr(errors):
                    with self.assertRaises(SystemExit) as raised:
                        module.main()
                self.assertEqual(2, raised.exception.code)
                self.assertIn('Output already exists', errors.getvalue())
                self.assertEqual('keep previous round', sentinel.read_text())
                self.assertFalse(Path('unused').exists())

    def test_cached_unit_results_cannot_be_attributed_to_the_current_commit(self):
        for spelling in ['round', 'unused/../round']:
            module = self.load()
            with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as evidence, \
                    contextlib.chdir(directory):
                output = Path(evidence) / spelling
                Path('app/src/androidTest/assets/audio-formats').mkdir(parents=True)

                def capture(command):
                    if command[:3] == ['git', 'rev-parse', 'HEAD']:
                        return 'current-commit'
                    if command[:2] == ['git', 'status']:
                        return ''
                    return 'device-property'

                def run(command, log):
                    if command[0] == './gradlew':
                        result = Path('app/build/test-results/testDebugUnitTest')
                        result.mkdir(parents=True)
                        forced = any(flag in command for flag in ('--rerun', '--rerun-tasks', '--no-build-cache'))
                        origin = 'current-commit' if forced else 'older-commit'
                        (result / 'TEST-Unit.xml').write_text(
                            '<testsuite><testcase classname="Unit" name="check"/>'
                            f'<system-out>{origin}</system-out></testsuite>')
                    else:
                        result = Path('app/build/outputs/androidTest-results/connected')
                        result.mkdir(parents=True)
                        (result / 'TEST-Device.xml').write_text(
                            '<testsuite><testcase classname="Device" name="probe"/></testsuite>')
                        Path('.ci-results').mkdir()
                        Path('.ci-results/logcat.txt').write_text('case-sensitive probe passed at fixture\n' * 3)
                    log.write_text('simulated Gradle execution\n')
                    return {'command': command, 'exitCode': 0, 'log': log.name}

                arguments = ['native_grammar_results.py', '--output', str(output),
                             '--grammar-version', 'test', '--grammar-revision', 'test-revision']
                with patch('sys.argv', arguments), patch.dict('os.environ', {'ANDROID_SERIAL': 'leased-device'}), \
                        patch.object(module, 'capture', side_effect=capture), patch.object(module, 'run', side_effect=run), \
                        patch.object(module, 'REQUIRED', {'F-01': ['Device#probe'], 'F-05': ['Unit#check']}):
                    self.assertEqual(0, module.main())
                self.assertFalse((Path(evidence) / 'unused').exists())
                output = output.resolve()
                report = json.loads((output / 'results.json').read_text())
                self.assertEqual('current-commit', report['repositoryCommit'])
                raw = (output / 'raw/unit/TEST-Unit.xml').read_text()
                self.assertIn('current-commit', raw, 'A previous Gradle result must not be labelled as a fresh run')
