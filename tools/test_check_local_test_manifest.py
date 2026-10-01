"""Regression checks for INTERNET declarations in local-test manifests."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    'manifest_check', Path(__file__).with_name('check-local-test-manifest.py'))
manifest_check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manifest_check)


class InternetPermissionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.debug = Path(self.temp.name) / 'debug.xml'
        self.release = Path(self.temp.name) / 'release.xml'
        self.write_debug()
        self.write_release('uses-permission')

    def write_debug(self, permission='', source=False):
        metadata = ''.join(
            f'<meta-data android:name="{name}" android:value="{value}" />'
            for name, value in manifest_check.EXPECTED.items())
        package = '' if source else 'package="com.kimjisub.launchpad.dev"'
        self.debug.write_text(
            f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            f'xmlns:tools="http://schemas.android.com/tools" {package}>'
            f'{permission}<application>{metadata}</application></manifest>')

    def write_release(self, tag=None):
        permission = (f'<{tag} android:name="android.permission.INTERNET" />'
                      if tag else '')
        self.release.write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            f'{permission}<application /></manifest>')

    def test_accepts_network_blocked_test_manifest(self):
        manifest_check.check(self.debug, self.release)

    def test_rejects_both_test_permission_forms(self):
        for tag in ('uses-permission', 'uses-permission-sdk-23'):
            with self.subTest(tag=tag):
                self.write_debug(f'<{tag} android:name="android.permission.INTERNET" />')
                with self.assertRaisesRegex(AssertionError, 'can open network sockets'):
                    manifest_check.check(self.debug, self.release)

    def test_recognizes_both_public_permission_forms(self):
        for tag in ('uses-permission', 'uses-permission-sdk-23'):
            with self.subTest(tag=tag):
                self.write_release(tag)
                manifest_check.check(self.debug, self.release)

    def test_rejects_public_manifest_without_internet(self):
        self.write_release()
        with self.assertRaisesRegex(AssertionError, 'public network permission changed'):
            manifest_check.check(self.debug, self.release)

    def test_source_removal_cannot_hide_versioned_permission(self):
        removal = ('<uses-permission android:name="android.permission.INTERNET" '
                   'tools:node="remove" />')
        self.write_debug(removal, source=True)
        manifest_check.check(self.debug, self.release, source=True)
        self.write_debug(
            removal + '<uses-permission-sdk-23 android:name="android.permission.INTERNET" />',
            source=True)
        with self.assertRaisesRegex(AssertionError, 'missing INTERNET removal'):
            manifest_check.check(self.debug, self.release, source=True)


if __name__ == '__main__':
    unittest.main()
