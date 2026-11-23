"""Exit codes, verdicts and cleanup of the Firebase-off check, with the device and screen simulated."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import firebase_off_check as fo  # noqa: E402
from release_store_check import CheckFailed, Node, ScreenTimeout  # noqa: E402

PACKAGE = 'com.kimjisub.launchpad'
CRASH = (f'11-24 10:00:05.000  1234  1234 E AndroidRuntime: Process: {PACKAGE}, PID: 1234\n'
         'java.lang.IllegalStateException: Default FirebaseApp is not initialized in this process\n')


class FakeAdb:
    """Answers the adb commands the check sends; `fail_on` makes one command fail."""

    def __init__(self, apk_sha, *, crash_disabled='', firebase_while_disabled=False, firebase_lines_enabled=True,
                 root=True, fail_on=(), alive=True, stuck_on=None):
        self.apk_sha, self.crash_disabled = apk_sha, crash_disabled
        self.firebase_while_disabled, self.firebase_lines_enabled = firebase_while_disabled, firebase_lines_enabled
        self.is_root, self.fail_on, self.alive = root, fail_on, alive
        self.disabled = False
        # Activities above the main screen, top last; `stuck_on` keeps one there when Back is pressed.
        self.activities, self.stuck_on = [], stuck_on
        self.commands = []

    def run(self, *args, timeout=60, check=True):
        command = ' '.join(args)
        self.commands.append(command)
        if any(part in command for part in self.fail_on):
            if check:
                raise CheckFailed(f'adb {command} failed: simulated')
            return b''
        if command.startswith('logcat -d -b crash'):
            return (self.crash_disabled if self.disabled else '').encode()
        if command.startswith('logcat -d'):
            on = not self.disabled
            log = ''
            if (on and self.firebase_lines_enabled) or (not on and self.firebase_while_disabled):
                log = (f'I FirebaseCrashlytics: {fo.CRASHLYTICS_STARTED} 20.1.0 for {PACKAGE}\n'
                       f'I {fo.FIREBASE_STARTED}\n')
            return log.encode()
        return self.shell_output(command).encode()

    def shell(self, *args, timeout=60, check=True):
        return self.run('shell', *args, timeout=timeout, check=check).decode().strip()

    def shell_output(self, command):
        command = command.removeprefix('shell ')
        if command == 'id -u':
            return '0' if self.is_root else '2000'
        if command.startswith('getprop'):
            return {'ro.build.version.sdk': '34', 'ro.build.version.release': '14'}.get(command.split()[-1], 'x')
        if command.startswith('pm path'):
            return 'package:/data/app/base.apk'
        if command.startswith('sha256sum'):
            return f'{self.apk_sha}  /data/app/base.apk'
        if command.startswith('dumpsys package'):
            disabled = f'      disabledComponents:\n        {fo.PROVIDER}\n' if self.disabled else ''
            return (f'    versionCode=123 minSdk=24 targetSdk=37\n    versionName=4.3.0\n'
                    f'    pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]\n{disabled}')
        if command.startswith('pm disable'):
            self.disabled = True
        if command.startswith('pm enable'):
            self.disabled = False
        if command.startswith('stat -c'):
            return '10123:10123'
        if command.startswith('cmd locale get-app-locales'):
            return f'Locales for {PACKAGE} for user 0 are []'
        if command.startswith('date'):
            return '11-24 10:00:00.000'
        if command.startswith('dumpsys activity'):
            top = self.activities[-1] if self.activities else '.activity.MainActivity'
            return f'  topResumedActivity=ActivityRecord{{5b1 u0 {PACKAGE}/{top} t12}}'
        if command.startswith('pidof'):
            return '1234' if self.alive else ''
        return ''

    def root(self):
        if self.shell('id', '-u') != '0':
            raise CheckFailed('adb root is not available on this device; use an emulator image that allows it')


class FakeScreen:
    """Every wait finds a node at once, except the one named in `missing`; `close_fails` fails closing the session."""

    def __init__(self, adb, missing=None, close_fails=False):
        self.adb, self.missing, self.close_fails = adb, missing, close_fails
        self.actions = []

    def node(self, what):
        if self.missing and self.missing in what:
            raise ScreenTimeout(f'{what} did not appear within 20 s')
        return Node(what, '', True, (0, 0, 10, 10))

    def wait(self, predicate, timeout, what, name):
        return self.node(what)

    def settled(self, locate, timeout, what, name):
        return self.node(what)

    def launch(self, package):
        self.actions.append('launch')

    def tap(self, node):
        self.actions.append(f'tap {node.text}')
        opens = {"The main screen's Store": '.activity.FBStoreActivity', 'The Settings': '.activity.SettingsActivity'}
        self.adb.activities += [name for prefix, name in opens.items() if node.text.startswith(prefix)]

    def back(self):
        self.actions.append('back')
        if self.adb.activities and self.adb.activities[-1] != self.adb.stuck_on:
            self.adb.activities.pop()

    def capture(self, name):
        self.actions.append(f'capture {name}')

    def ui(self, *args, check=True):
        self.actions.append(' '.join(args))
        if self.close_fails and args[0] == 'close':
            raise CheckFailed('ui close failed: simulated')
        return {'success': True}


class RunTest(unittest.TestCase):
    def run_check(self, **options):
        screen_options = {k: options.pop(k) for k in ('missing', 'close_fails') if k in options}
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'universal.apk'
            apk.write_bytes(b'apk')
            out = Path(tmp) / 'out'
            out.mkdir()
            adb = FakeAdb(hashlib.sha256(b'apk').hexdigest(), **options)
            screen = FakeScreen(adb, **screen_options)
            with mock.patch.object(fo.time, 'sleep'), mock.patch.object(fo, 'FIREBASE_LOG_TIMEOUT', 0):
                code = fo.run_and_record(fo.FirebaseOffCheck(adb, screen, apk, out))
            result = json.loads((out / 'result.json').read_text())
        return code, result, adb, screen

    def assert_cleaned(self, adb, screen):
        self.assertFalse(adb.disabled, 'the provider must be enabled again')
        self.assertIn('close', screen.actions)
        self.assertIn(f'shell cmd locale set-app-locales {PACKAGE} --locales \'\'', adb.commands)

    def test_a_build_that_works_without_firebase_passes_and_restores_the_device(self):
        code, result, adb, screen = self.run_check()
        self.assertEqual((code, result['verdict'], result['failures']), (0, 'passed', []))
        self.assertEqual(result['disabled_run']['firebase'][fo.FIREBASE_STARTED], False)
        self.assertTrue(all(result['reenabled_run']['firebase'].values()))
        self.assertEqual([s['step'] for s in result['steps']],
                         ['prepared', 'main screen', 'store', 'settings', 'play screen', 'Firebase on again'])
        self.assertLess(adb.commands.index(f'shell pm disable {PACKAGE}/{fo.PROVIDER}'),
                        adb.commands.index('logcat -b all -c'))
        self.assert_cleaned(adb, screen)

    def test_a_crash_with_firebase_off_fails_with_exit_1(self):
        code, result, adb, screen = self.run_check(crash_disabled=CRASH, alive=False)
        self.assertEqual((code, result['verdict']), (1, 'failed'))
        self.assertIn('crash or ANR with Firebase off', result['failures'][0])
        self.assert_cleaned(adb, screen)

    def test_back_that_leaves_the_store_open_is_not_taken_for_the_main_screen(self):
        clock = iter(range(0, 100000, 5))
        with mock.patch.object(fo.time, 'monotonic', side_effect=lambda: next(clock)):
            code, result, adb, screen = self.run_check(stuck_on='.activity.FBStoreActivity')
        self.assertEqual((code, result['verdict']), (1, 'failed'))
        self.assertTrue(any('.activity.MainActivity was not on top' in f and 'FBStoreActivity' in f
                            for f in result['failures']), result['failures'])
        self.assertNotIn('tap The Settings button', screen.actions)
        self.assert_cleaned(adb, screen)

    def test_a_missing_store_message_is_the_apps_failure_and_the_logs_are_still_read(self):
        code, result, adb, screen = self.run_check(missing='Store message', crash_disabled=CRASH)
        self.assertEqual((code, result['verdict']), (1, 'failed'))
        self.assertTrue(any('crash or ANR' in f for f in result['failures']))
        self.assertTrue(any('Store message' in f for f in result['failures']))
        self.assertNotIn('reenabled_run', result)
        self.assert_cleaned(adb, screen)

    def test_firebase_lines_missing_after_enabling_again_fail(self):
        code, result, adb, screen = self.run_check(firebase_lines_enabled=False)
        self.assertEqual((code, result['verdict']), (1, 'failed'))
        self.assertEqual(len(result['failures']), 2)
        self.assert_cleaned(adb, screen)

    def test_a_device_without_root_cannot_run_the_check(self):
        code, result, adb, screen = self.run_check(root=False)
        self.assertEqual((code, result['verdict']), (2, None))
        self.assertIn('adb root', result['error'])
        self.assertNotIn('shell pm disable', ' '.join(adb.commands))
        self.assertIn('close', screen.actions)

    def test_firebase_starting_while_disabled_means_the_check_did_not_run_as_intended(self):
        code, result, adb, screen = self.run_check(firebase_while_disabled=True)
        self.assertEqual((code, result['verdict']), (2, None))
        self.assertIn('Firebase started although', result['error'])
        self.assert_cleaned(adb, screen)

    def test_an_install_failure_is_a_setup_error(self):
        code, result, adb, screen = self.run_check(fail_on=('install /',))
        self.assertEqual((code, result['verdict']), (2, None))
        self.assertIn('install', result['error'])

    def test_a_cleanup_error_after_a_pass_exits_2_and_keeps_the_verdict(self):
        code, result, adb, screen = self.run_check(close_fails=True)
        self.assertEqual((code, result['verdict']), (2, 'passed'))
        self.assertIn('UI session', result['cleanup_errors'][0])

    def test_a_cleanup_error_does_not_hide_an_app_failure(self):
        code, result, adb, screen = self.run_check(crash_disabled=CRASH, close_fails=True)
        self.assertEqual((code, result['verdict']), (1, 'failed'))
        self.assertTrue(result['cleanup_errors'])

    def test_a_provider_left_disabled_is_a_cleanup_error(self):
        code, result, adb, screen = self.run_check(fail_on=('pm enable',))
        self.assertEqual(code, 2)
        self.assertTrue(any('still disabled' in e for e in result['cleanup_errors']))


class ParsingTest(unittest.TestCase):
    def test_disabled_components_are_read_from_dumpsys(self):
        text = ('    User 0: ceDataInode=1 installed=true\n      disabledComponents:\n'
                f'        {fo.PROVIDER}\n        com.example.Other\n      enabledComponents:\n        a.B\n')
        self.assertEqual(fo.disabled_components(text), {fo.PROVIDER, 'com.example.Other'})
        self.assertEqual(fo.disabled_components('    pkgFlags=[ HAS_CODE ]\n'), set())

    def test_the_strings_the_check_reads_are_the_apps_english_strings(self):
        strings = (Path(__file__).resolve().parents[2] / 'app/src/main/res/values/strings.xml').read_text()
        for name, value in (('UnableToAccessServer', fo.UNABLE_TO_ACCESS_SERVER), ('setting', fo.SETTING)):
            self.assertIn(f'<string name="{name}">{value}</string>', strings)


if __name__ == '__main__':
    unittest.main()
