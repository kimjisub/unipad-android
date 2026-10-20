import contextlib
import json
from unittest.mock import patch
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('native_results', Path(__file__).parents[1] / 'native_grammar_results.py')


class NativeGrammarResultsTest(unittest.TestCase):
    def load(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        return module

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

    def test_cached_unit_results_cannot_be_attributed_to_the_current_commit(self):
        module = self.load()
        with tempfile.TemporaryDirectory() as directory, contextlib.chdir(directory):
            root = Path(directory)
            output = root / 'round'
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
