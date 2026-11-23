"""Verdicts of the release bundle check on bytecode counts and manifest differences."""
import contextlib
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import check_release_bundle as crb  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" android:versionCode="{code}"
    android:versionName="4.x" package="com.kimjisub.launchpad">
    <uses-feature android:name="android.hardware.usb.host" android:required="false"/>
    <uses-feature android:glEsVersion="0x00020000" android:required="true"/>
    {features}
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:maxSdkVersion="29" android:name="android.permission.READ_EXTERNAL_STORAGE"/>
    {permissions}
    {queries}
    <application>
        <meta-data android:name="google_analytics_adid_collection_enabled" android:value="{adid}"/>
        <activity android:name=".activity.MainActivity"/>
        <receiver android:exported="true" android:name="com.google.firebase.iid.FirebaseInstanceIdReceiver"/>
        {receivers}
    </application>
</manifest>
'''


def facts(code=122, features='', permissions='', queries='', receivers='', adid='false'):
    return crb.manifest_facts(MANIFEST.format(code=code, features=features, permissions=permissions, queries=queries,
                                              receivers=receivers, adid=adid))


def dex(strings=1, calls=0):
    return dict(const_strings={k: strings for k in crb.DIAGNOSTIC_KEYS}, calls={c: calls for c in crb.FORBIDDEN_CALLS})


class DexTest(unittest.TestCase):
    def test_counts_const_string_loads_and_forbidden_calls(self):
        lines = [
            '43eb1c: 1a02 8d56   |04be: const-string v2, "exit_last_screen" // string@568d',
            '43eb5a: 1a01 8b56   |04dd: const-string/jumbo v11, "exit_foreground_screen" // string@568b',
            '43eb5b: 1a01 8b56   |04de: const-string v1, "exit_foreground_screen_extra" // string@568c',
            '43eb5c: 1a01 8b56   |04df: sget-object v1, Lexit_process_ms;',
            '2afd6e: 7010 e808   |0005: invoke-direct {v0}, Landroid/media/MediaPlayer;.<init>:()V // method@08e8',
            '2afd70: 7010 e808   |0007: invoke-virtual {v0}, Landroid/media/MediaPlayer;.start:()V // method@08e9',
        ]
        counts = crb.count_dex(lines)
        self.assertEqual(counts['const_strings'], {'exit_foreground_screen': 1, 'exit_foreground_pack_open': 0,
                                                   'exit_foreground_ms': 0, 'exit_process_ms': 0,
                                                   'exit_last_screen': 1})
        self.assertEqual(counts['calls'], {'Landroid/media/MediaPlayer;.<init>': 1})

    def test_a_removed_key_and_a_forbidden_call_fail(self):
        self.assertEqual(crb.dex_problems(dex()), [])
        candidate = dex(calls=2)
        candidate['const_strings']['exit_process_ms'] = 0
        problems = crb.dex_problems(candidate)
        self.assertEqual(len(problems), 2)
        self.assertIn('exit_process_ms', problems[0])
        self.assertIn('MediaPlayer', problems[1])

    def test_every_key_is_a_string_literal_the_app_writes(self):
        source = (ROOT / 'app/src/main/java/com/kimjisub/launchpad/diagnostics/SystemExitReporter.kt').read_text()
        for key in crb.DIAGNOSTIC_KEYS:
            self.assertIn(f'"{key}"', source)


class ManifestTest(unittest.TestCase):
    def test_same_manifest_passes(self):
        self.assertEqual(crb.manifest_problems(facts(), facts(123), set()), ([], []))

    def test_the_4_3_0_additions_are_listed_not_failed(self):
        candidate = facts(123, features='<uses-feature android:name="android.software.midi" android:required="false"/>',
                          queries='<queries><intent><action android:name="android.media.midi.MidiDeviceService"/>'
                                  '</intent></queries>',
                          receivers='<receiver android:exported="false" android:name="a.UsbMidiPermissionReceiver"/>')
        problems, changes = crb.manifest_problems(facts(), candidate, set())
        self.assertEqual(problems, [])
        self.assertEqual(changes, [
            'uses-feature added (not required): android.software.midi',
            'queries added: intent[ action[name=android.media.midi.MidiDeviceService]]',
            'receivers added: receiver[exported=false name=a.UsbMidiPermissionReceiver]'])

    def test_a_permission_change_fails_unless_it_is_expected(self):
        camera = '<uses-permission android:name="android.permission.CAMERA"/>'
        problems, _ = crb.manifest_problems(facts(), facts(123, permissions=camera), set())
        self.assertEqual(len(problems), 1)
        self.assertIn('permission added: android.permission.CAMERA', problems[0])
        self.assertEqual(crb.manifest_problems(facts(), facts(123, permissions=camera), {'+android.permission.CAMERA'}),
                         ([], []))
        removed, _ = crb.manifest_problems(facts(permissions=camera), facts(123), set())
        self.assertIn('permission removed: android.permission.CAMERA', removed[0])
        missing, _ = crb.manifest_problems(facts(), facts(123), {'+android.permission.CAMERA'})
        self.assertIn('is not in the candidate', missing[0])

    def test_a_changed_permission_limit_is_listed(self):
        previous = facts()
        candidate = crb.manifest_facts(MANIFEST.format(code=123, features='', permissions='', queries='', receivers='',
                                                       adid='false').replace('maxSdkVersion="29"', 'maxSdkVersion="32"'))
        problems, changes = crb.manifest_problems(previous, candidate, set())
        self.assertEqual(problems, [])
        self.assertIn('maxSdkVersion=32', changes[0])

    def test_a_newly_required_feature_fails(self):
        for previous_feature in ('', '<uses-feature android:name="android.hardware.camera" android:required="false"/>'):
            with self.subTest(previous=previous_feature):
                candidate = facts(123, features='<uses-feature android:name="android.hardware.camera"/>')
                problems, _ = crb.manifest_problems(facts(features=previous_feature), candidate, set())
                self.assertEqual(len(problems), 1)
                self.assertIn('android.hardware.camera is now required', problems[0])

    def test_advertising_id_collection_and_permission_fail(self):
        candidate = facts(123, adid='true', permissions='<uses-permission android:name="com.google.android.gms.permission.AD_ID"/>')
        problems, _ = crb.manifest_problems(facts(), candidate, {'+com.google.android.gms.permission.AD_ID'})
        self.assertEqual(len(problems), 2)
        self.assertIn('google_analytics_adid_collection_enabled', problems[0])
        self.assertIn('advertising permission com.google.android.gms.permission.AD_ID', problems[1])


class MainTest(unittest.TestCase):
    def build(self, code, strings=1, calls=0, **manifest):
        return dict(source=f'{code}.aab', apk_sha256='0', version_code=code, version_name=f'4.{code}',
                    manifest=facts(code, **manifest), dex=dex(strings, calls))

    def run_main(self, builds=None, error=None, extra=()):
        def read_build(source, work):
            if error:
                raise error
            work.mkdir(parents=True)
            (work / 'AndroidManifest.xml').write_text(f'<manifest>{source.name}</manifest>\n')
            return builds[source.name]
        with tempfile.TemporaryDirectory() as tmp:
            for name in ('old.aab', 'new.aab'):
                (Path(tmp) / name).write_bytes(b'x')
            out = Path(tmp) / 'out'
            with mock.patch.object(crb, 'read_build', side_effect=read_build), contextlib.redirect_stdout(io.StringIO()), \
                    contextlib.redirect_stderr(io.StringIO()):
                code = crb.main(['--previous', f'{tmp}/old.aab', '--candidate', f'{tmp}/new.aab', '--out', str(out),
                                 *extra])
            files = {p.name: p.read_text() for p in out.iterdir()}
        return code, files

    def test_a_clean_candidate_exits_0_with_report_and_diff(self):
        code, files = self.run_main({'old.aab': self.build(122, strings=0, calls=1), 'new.aab': self.build(123)})
        self.assertEqual(code, 0)
        self.assertIn('"passed": true', files['report.json'])
        self.assertIn('+<manifest>new.aab</manifest>', files['manifest.diff'])
        self.assertTrue(files['summary.txt'].rstrip().endswith('PASS'))

    def test_a_failing_candidate_exits_1(self):
        code, files = self.run_main({'old.aab': self.build(122), 'new.aab': self.build(122, calls=1)})
        self.assertEqual(code, 1)
        self.assertIn('is not newer', files['summary.txt'])
        self.assertIn('MediaPlayer', files['summary.txt'])

    def test_a_tool_that_cannot_run_exits_2(self):
        code, files = self.run_main(error=crb.SetupError('bundletool is needed to turn a bundle into an APK.'))
        self.assertEqual(code, 2)
        self.assertIn('bundletool', files['setup-error.txt'])
        self.assertNotIn('report.json', files)

    def test_expected_permission_needs_a_sign(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            crb.main(['--previous', 'a', '--candidate', 'b', '--out', 'c', '--expect-permission', 'CAMERA'])


if __name__ == '__main__':
    unittest.main()
