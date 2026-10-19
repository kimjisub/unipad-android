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
