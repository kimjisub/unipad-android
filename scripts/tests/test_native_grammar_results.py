import contextlib
import json
import io
from unittest.mock import patch
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('native_results', Path(__file__).parents[1] / 'native_grammar_results.py')
MISSING_SOURCE_OUTPUTS = [
    ('.ci-results', '.CI-RESULTS/round/nested'),
    ('app/build/test-results/testDebugUnitTest', 'app/build/test-results/TESTdEBUGuNITtEST/round/nested'),
    ('app/build/outputs/androidTest-results/connected', 'app/build/outputs/androidTest-results/CONNECTED/round/nested'),
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
                with self.subTest(source=source, absolute=absolute), \
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

    def test_missing_source_different_case_is_admitted_on_case_sensitive_filesystem(self):
        for source, spelling in MISSING_SOURCE_OUTPUTS:
            with self.subTest(source=source), tempfile.TemporaryDirectory() as directory, \
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
                self.assertTrue(output.is_dir())
                self.assertFalse(Path(source).exists())

    def test_rejected_missing_source_alias_preserves_new_directories_with_content(self):
        for source, spelling in MISSING_SOURCE_OUTPUTS:
            for populated in ['output', 'parent']:
                with self.subTest(source=source, populated=populated), \
                        tempfile.TemporaryDirectory() as directory, contextlib.chdir(directory):
                    if not self.case_aliases_share_directory(directory):
                        self.skipTest('Temporary filesystem distinguishes case; missing-source aliases are distinct')
                    module = self.load()
                    self.assertFalse(Path(source).exists())
                    output = Path(spelling)
                    sentinel = (output if populated == 'output' else output.parent) / 'keep.txt'
                    mkdir = Path.mkdir

                    def create_with_content(path, *args, **kwargs):
                        mkdir(path, *args, **kwargs)
                        if path == output:
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
                    self.assertEqual(populated == 'output', output.exists())

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
        module = self.load()
        with tempfile.TemporaryDirectory() as directory, contextlib.chdir(directory):
            output = Path('existing')
            output.mkdir()
            sentinel = output / 'original.txt'
            sentinel.write_text('keep previous round')
            arguments = ['native_grammar_results.py', '--output', str(output),
                         '--grammar-version', 'test', '--grammar-revision', 'test-revision']
            errors = io.StringIO()
            with patch('sys.argv', arguments), patch.dict('os.environ', {}, clear=True), \
                    contextlib.redirect_stderr(errors):
                with self.assertRaises(SystemExit) as raised:
                    module.main()
            self.assertEqual(2, raised.exception.code)
            self.assertIn('Output already exists', errors.getvalue())
            self.assertEqual('keep previous round', sentinel.read_text())

    def test_cached_unit_results_cannot_be_attributed_to_the_current_commit(self):
        module = self.load()
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as evidence, \
                contextlib.chdir(directory):
            output = Path(evidence) / 'round'
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
            report = json.loads((output / 'results.json').read_text())
            self.assertEqual('current-commit', report['repositoryCommit'])
            raw = (output / 'raw/unit/TEST-Unit.xml').read_text()
            self.assertIn('current-commit', raw, 'A previous Gradle result must not be labelled as a fresh run')
