"""Verify the manifest check accepts only collection-off debug and untouched release manifests."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

CHECKER = Path(__file__).resolve().parents[1] / 'check_debug_telemetry_manifest.py'
spec = importlib.util.spec_from_file_location('check_debug_telemetry_manifest', CHECKER)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


def manifest(permission='', values=None):
    entries = ''.join(f'<meta-data android:name="{name}" android:value="{value}" />'
                      for name, value in (values or {}).items())
    return ('<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            f'{permission}<application>{entries}</application></manifest>')


INTERNET = '<uses-permission android:name="android.permission.INTERNET" />'


class DebugTelemetryManifestTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.debug = Path(temp.name) / 'debug.xml'
        self.release = Path(temp.name) / 'release.xml'
        self.debug.write_text(manifest(INTERNET, checker.DEBUG_COLLECTION_OFF))
        self.release.write_text(manifest(INTERNET))

    def test_accepts_collection_off_debug_and_untouched_release(self):
        self.assertEqual(checker.problems(self.debug, self.release), [])

    def test_rejects_debug_missing_or_reversing_each_value(self):
        for name, value in checker.DEBUG_COLLECTION_OFF.items():
            for replacement in (None, 'false' if value == 'true' else 'true'):
                with self.subTest(name=name, replacement=replacement):
                    values = dict(checker.DEBUG_COLLECTION_OFF)
                    if replacement is None:
                        del values[name]
                    else:
                        values[name] = replacement
                    self.debug.write_text(manifest(INTERNET, values))
                    self.assertEqual(len(checker.problems(self.debug, self.release)), 1)

    def test_rejects_unreplaced_server_check_placeholder(self):
        values = dict(checker.DEBUG_COLLECTION_OFF, firebase_analytics_collection_deactivated='${x}')
        self.debug.write_text(manifest(INTERNET, values))
        self.assertIn('firebase_analytics_collection_deactivated', checker.problems(self.debug, self.release)[0])

    def test_accepts_either_release_internet_declaration(self):
        for tag in ('uses-permission', 'uses-permission-sdk-23'):
            with self.subTest(tag=tag):
                self.release.write_text(manifest(f'<{tag} android:name="android.permission.INTERNET" />'))
                self.assertEqual(checker.problems(self.debug, self.release), [])

    def test_rejects_release_without_internet(self):
        self.release.write_text(manifest())
        self.assertEqual(checker.problems(self.debug, self.release),
                         ['release: android.permission.INTERNET is missing'])

    def test_rejects_any_debug_value_in_release(self):
        for name, value in checker.DEBUG_COLLECTION_OFF.items():
            with self.subTest(name=name):
                self.release.write_text(manifest(INTERNET, {name: value}))
                self.assertEqual(checker.problems(self.debug, self.release),
                                 [f'release: debug-only {name} is present'])


if __name__ == '__main__':
    unittest.main()
