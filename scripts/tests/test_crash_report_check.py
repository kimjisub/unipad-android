"""Offline checks for the crash report check: report parsing, matching, waiting and verdicts."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import textwrap
import unittest
from unittest.mock import Mock
import unittest.mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import crash_report_check as crc
from release_store_check import CheckFailed, Screen, ScreenTimeout

CRASH_MS = 1794379190000


def event(name, installation, issue_id, title, subtitle, error_type, keys, version='4.2.2 (121)'):
    lines = [f'- name: |', f'    projects/1/apps/x/events/{name}', '  eventTime: |', "    '2026-11-11T06:40:01Z'",
             '  issue: |', f'    id: {issue_id}', f"    title: '{title}'", f'    subtitle: {subtitle}',
             f'    errorType: {error_type}', '  version: |', '    displayVersion: 4.2.2',
             f"    displayName: {version}", '  customKeys: |']
    for key, value in keys.items():
        lines += value if isinstance(value, list) else [f'    {key}: {value}']
    if installation is not None:
        lines += ['  installationUuid: |', f'    {installation}']
    return '\n'.join(lines)


NATIVE_KEYS = {'install_splits': 'none', 'play_window_mode': 'full_screen', 'screen': 'PlayActivity',
               'check_id': 'run-1', 'install_source': 'unknown', 'play_orientation': 'landscape',
               'play_screen_short_side': 'lt_600dp', 'test_crash': "'true'"}
EXIT_KEYS = {'exit_background_ms': "'8006'", 'exit_last_screen': 'PlayActivity', 'exit_pack_open': "'true'",
             'exit_reason': "'5'", 'exit_timestamp': f"'{CRASH_MS + 4000}'", 'harness_test': "'true'",
             'install_source': 'unknown', 'install_splits': 'none',
             'native_frames': ['    native_frames: >-', '      #00 libc.so 0xb70f8 (kill+8) | #01 libjavacore.so',
                               '      0x29ee0 (Linux_kill+36)']}


def native(installation='INSTALL-A', **changes):
    return event('n1', installation, 'f4e2', '[libc.so]', 'SIGSEGV', 'FATAL', dict(NATIVE_KEYS, **changes))


def exit_event(installation='INSTALL-A', **changes):
    return event('e1', installation, '531e', 'SystemExitReporter.exceptionFor',
                 'SystemExitReporter$NativeCrashExit - Where the system saw a native crash', 'NON_FATAL',
                 dict(EXIT_KEYS, **changes))


class ParsingTest(unittest.TestCase):
    def test_reads_fields_keys_and_folded_values(self):
        events = crc.parse_events(native() + '\n\n' + exit_event())
        self.assertEqual(len(events), 2)
        self.assertEqual(events[0]['keys']['check_id'], 'run-1')
        self.assertEqual(events[0]['keys']['test_crash'], 'true')
        self.assertEqual(events[0]['error_type'], 'FATAL')
        self.assertEqual(events[0]['version'], '4.2.2 (121)')
        self.assertEqual(events[1]['installation'], 'INSTALL-A')
        self.assertEqual(events[1]['keys']['exit_background_ms'], '8006')
        self.assertTrue(events[1]['keys']['native_frames'].startswith('#00 libc.so'))
        self.assertIn('Linux_kill+36', events[1]['keys']['native_frames'])

    def test_issue_ids_come_from_the_report_groups(self):
        report = ('name: projects/1/reports/topIssues\ngroups: |\n  - metrics:\n      - eventsCount: 1\n'
                  '    issue:\n      id: f4e2\n      title: x\n  - metrics:\n      - eventsCount: 1\n'
                  '    issue:\n      id: 531e\n')
        self.assertEqual(crc.parse_issue_ids(report), ['f4e2', '531e'])

    def test_only_the_debug_package_has_an_app_id(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'google-services.json'
            path.write_text(json.dumps({'client': [
                {'client_info': {'mobilesdk_app_id': 'store', 'android_client_info': {'package_name': 'com.kimjisub.launchpad'}}},
                {'client_info': {'mobilesdk_app_id': 'dev', 'android_client_info': {'package_name': crc.PACKAGE}}}]}))
            self.assertEqual(crc.crashlytics_app_id(path, crc.PACKAGE), 'dev')
            with self.assertRaises(crc.NotChecked):
                crc.crashlytics_app_id(path, 'com.kimjisub.launchpad')

    def test_manifest_switch(self):
        on = ('A: http://schemas.android.com/apk/res/android:name(0x01010003)="firebase_crashlytics_collection_enabled"'
              ' (Raw: "x")\n            A: http://schemas.android.com/apk/res/android:value(0x01010024)=true\n')
        self.assertTrue(crc.crash_reports_on(on))
        self.assertFalse(crc.crash_reports_on(on.replace('=true', '=false')))
        self.assertFalse(crc.crash_reports_on(''))


class JudgeTest(unittest.TestCase):
    def judge(self, *texts):
        return crc.judge(crc.parse_events('\n'.join(texts)), 'run-1', '4.2.2 (121)', CRASH_MS)

    def test_both_reports_with_expected_values(self):
        found = self.judge(exit_event(), native())
        self.assertTrue(found['native'] and found['exit'])
        self.assertEqual(found['problems'], [])

    def test_exit_report_of_another_installation_is_not_this_run(self):
        found = self.judge(native(), exit_event(installation='INSTALL-B'))
        self.assertIsNone(found['exit'])

    def test_other_check_id_is_not_this_run(self):
        self.assertIsNone(self.judge(native(check_id='run-0'), exit_event())['native'])

    def test_each_missing_or_wrong_value_is_named(self):
        found = self.judge(native(screen='MainActivity', play_orientation=''),
                           exit_event(exit_pack_open="'false'", exit_background_ms="'0'",
                                      exit_timestamp=f"'{CRASH_MS - 1}'", install_source=''))
        text = '\n'.join(found['problems'])
        for expected in ('screen is', 'no play_orientation', 'exit_pack_open', 'exit_background_ms',
                         'exit_timestamp', 'system exit report: no install_source'):
            self.assertIn(expected, text)

    def test_reports_without_an_installation_id_are_never_paired(self):
        # Without the installation the exit report (which has no check id) could be anyone's.
        found = self.judge(native(installation=None), exit_event(installation=None))
        self.assertTrue(found['native'])
        self.assertIsNone(found['exit'])
        self.assertTrue(found['settled'])
        self.assertIn('no installationUuid', '\n'.join(found['problems']))
        verdict, reason = crc.server_verdict(found, [dict(seconds=0, events=2)], 300)
        self.assertEqual(verdict, crc.FAILED)
        self.assertIn('no installationUuid', reason)

    def test_wrong_version_is_a_problem(self):
        found = crc.judge(crc.parse_events(native() + '\n' + exit_event()), 'run-1', '4.2.3 (122)', CRASH_MS)
        self.assertEqual(len(found['problems']), 2)


class WaitTest(unittest.TestCase):
    def run_wait(self, answers, timeout=300, interval=60):
        now = [0.0]
        reads = iter(answers)

        def read():
            answer = next(reads)
            if isinstance(answer, Exception):
                raise answer
            return crc.parse_events(answer)

        def sleep(seconds):
            now[0] += seconds
        polls = []
        found = crc.wait_for_reports(read, 'run-1', '4.2.2 (121)', CRASH_MS, timeout, interval, polls,
                                     sleep=sleep, clock=lambda: now[0])
        return found, polls

    def test_waits_until_the_exit_report_arrives(self):
        found, polls = self.run_wait(['', native(), crc.NotChecked('429'), native() + '\n' + exit_event()])
        self.assertTrue(found['exit'])
        self.assertEqual([p.get('error') for p in polls], [None, None, '429', None])
        self.assertEqual(crc.server_verdict(found, polls, 300)[0], crc.PASSED)

    def test_missing_report_at_the_time_limit_fails(self):
        found, polls = self.run_wait([native()] * 10, timeout=120)
        self.assertEqual(len(polls), 3)
        verdict, reason = crc.server_verdict(found, polls, 120)
        self.assertEqual(verdict, crc.FAILED)
        self.assertIn('system exit report', reason)

    def test_failed_last_read_is_not_checked_rather_than_failed(self):
        found, polls = self.run_wait([native(), crc.NotChecked('down'), crc.NotChecked('down')], timeout=120)
        verdict, reason = crc.server_verdict(found, polls, 120)
        self.assertEqual(verdict, crc.NOT_CHECKED)
        self.assertIn('down', reason)

    def test_wrong_values_fail_after_both_arrive(self):
        found, polls = self.run_wait([native() + '\n' + exit_event(exit_last_screen='MainActivity')])
        self.assertEqual(len(polls), 1)
        self.assertEqual(crc.server_verdict(found, polls, 300)[0], crc.FAILED)


    def test_interrupted_wait_keeps_the_reads_made(self):
        result = dict(check_id='run-1', apk=dict(version='4.2.2 (121)'), crash_requested_ms=CRASH_MS)

        def interrupt(seconds):
            raise KeyboardInterrupt('Signal 15')
        with tempfile.TemporaryDirectory() as folder, self.assertRaises(KeyboardInterrupt):
            crc.check_server(result, lambda: crc.parse_events(native()), Path(folder), 300, 60, sleep=interrupt)
        self.assertEqual(result['server_polls'], [dict(seconds=0, events=1, native=True, exit=False)])

    def test_check_server_writes_both_reports(self):
        result = dict(check_id='run-1', apk=dict(version='4.2.2 (121)'), crash_requested_ms=CRASH_MS)
        with tempfile.TemporaryDirectory() as folder:
            verdict = crc.check_server(result, lambda: crc.parse_events(native() + '\n' + exit_event()),
                                       Path(folder), 300, 60)
            self.assertTrue((Path(folder) / 'server-exit.txt').read_text().startswith('- name:'))
        self.assertEqual(verdict[0], crc.PASSED)
        self.assertEqual(result['server']['system_exit']['installation'], 'INSTALL-A')


# A stand-in for `firebase mcp`: answers `initialize`, then each tools/call per its mode, and keeps a count of
# how many times it was started.
FAKE_CLI = textwrap.dedent('''\
    import json, sys
    from pathlib import Path
    count = Path(sys.argv[0] + '.starts')
    starts = int(count.read_text()) + 1 if count.exists() else 1
    count.write_text(str(starts))
    mode = Path(sys.argv[0] + '.mode').read_text().split(',')[starts - 1]
    for line in sys.stdin:
        message = json.loads(line)
        if message.get('method') == 'initialize':
            print(json.dumps({'jsonrpc': '2.0', 'id': message['id'], 'result': {}}), flush=True)
        elif message.get('method') == 'tools/call':
            if mode == 'exit':
                sys.exit(1)
            text = 'not logged in' if mode == 'error-then-exit' else 'name: projects/1/reports/topIssues'
            print(json.dumps({'jsonrpc': '2.0', 'id': message['id'], 'result': {
                'isError': mode == 'error-then-exit', 'content': [{'type': 'text', 'text': text}]}}), flush=True)
            if mode == 'error-then-exit':
                sys.exit(1)
''')


class FirebaseCliTest(unittest.TestCase):
    def cli(self, folder, modes):
        script = Path(folder) / 'firebase'
        script.write_text(f'#!{sys.executable}\n' + FAKE_CLI)
        script.chmod(0o755)
        Path(str(script) + '.mode').write_text(','.join(modes))
        return crc.FirebaseMcp(str(script), folder, Path(folder) / 'firebase-mcp.log', timeout=20)

    def test_a_cli_that_ended_is_started_again_and_never_breaks_the_pipe(self):
        with tempfile.TemporaryDirectory() as folder:
            mcp = self.cli(folder, ['error-then-exit', 'error-then-exit', 'answer'])
            try:
                with self.assertRaisesRegex(crc.NotChecked, 'not logged in'):
                    mcp.call('crashlytics_get_report', {})
                mcp.process.wait(timeout=20)
                with self.assertRaisesRegex(crc.NotChecked, 'not logged in'):
                    mcp.call('crashlytics_get_report', {})
                mcp.process.wait(timeout=20)
                self.assertIn('topIssues', mcp.call('crashlytics_get_report', {}))
                self.assertEqual(mcp.starts, 3)
            finally:
                mcp.close()

    def test_a_cli_that_ends_mid_call_is_not_checked_and_replaced(self):
        with tempfile.TemporaryDirectory() as folder:
            mcp = self.cli(folder, ['exit', 'answer'])
            try:
                with self.assertRaisesRegex(crc.NotChecked, 'exited'):
                    mcp.call('crashlytics_get_report', {})
                self.assertIsNone(mcp.process)
                self.assertIn('topIssues', mcp.call('crashlytics_get_report', {}))
                self.assertEqual(mcp.starts, 2)
            finally:
                mcp.close()

    def test_a_cli_that_ends_between_calls_breaks_no_pipe_and_is_replaced(self):
        with tempfile.TemporaryDirectory() as folder:
            mcp = self.cli(folder, ['answer', 'answer'])
            try:
                mcp.call('crashlytics_get_report', {})
                process = mcp.process
                process.kill()
                process.wait(timeout=20)
                # It ends after the liveness check and before the request is written.
                process.poll = lambda: None
                with self.assertRaisesRegex(crc.NotChecked, 'stopped taking requests \\(BrokenPipeError\\)'):
                    mcp.call('crashlytics_get_report', {})
                self.assertIn('topIssues', mcp.call('crashlytics_get_report', {}))
                self.assertEqual(mcp.starts, 2)
            finally:
                mcp.close()

    def test_issue_listed_without_readable_events_is_not_checked(self):
        mcp = Mock()
        mcp.call.side_effect = ['groups: |\n  - metrics:\n      - eventsCount: 1\n    issue:\n      id: f4e2\n',
                                'events: []']
        with self.assertRaisesRegex(crc.NotChecked, 'f4e2'):
            crc.read_check_events(mcp, 'dev', crc.datetime.now(crc.timezone.utc))


class DeviceVerdictTest(unittest.TestCase):

    def setUp(self):
        # Device steps write evidence (logcat.txt, snapshots) into the output folder.
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.out = Path(folder.name)
    def check(self):
        check = crc.CrashReportCheck(Mock(), Mock(), Path('app.apk'), self.out)
        for name in ('prepare', 'open_play_screen', 'crash_in_background', 'send_reports'):
            setattr(check, name, Mock())
        return check

    def test_normal_path_leaves_the_verdict_to_the_server(self):
        check = self.check()
        self.assertEqual(crc.run_device(check, 'run-1'), (None, None))
        check.send_reports.assert_called_once()
        self.assertEqual(check.adb.shell.call_args.args, ('am', 'force-stop', crc.PACKAGE))
        check.screen.ui.assert_called_with('close')

    def test_preparation_failure_is_not_checked_and_never_opens_the_app(self):
        check = self.check()
        check.prepare.side_effect = crc.NotChecked('install failed')
        self.assertEqual(crc.run_device(check, 'run-1'), (crc.NOT_CHECKED, 'install failed'))
        check.open_play_screen.assert_not_called()
        check.screen.ui.assert_called_with('close')

    def test_app_failure_stays_failed_when_cleanup_also_fails(self):
        check = self.check()
        check.crash_in_background.side_effect = crc.AppFailed('The app was still running')
        check.adb.shell.side_effect = CheckFailed('adb gone')
        check.screen.ui.side_effect = CheckFailed('ui gone')
        self.assertEqual(crc.run_device(check, 'run-1'), (crc.FAILED, 'The app was still running'))
        self.assertEqual(len(check.result['cleanup_errors']), 2)
        check.send_reports.assert_not_called()

    def test_preparation_device_errors_are_not_checked(self):
        adb = Mock()
        adb.root.side_effect = CheckFailed('adb root is not available')
        check = crc.CrashReportCheck(adb, Mock(), Path('app.apk'), self.out)
        verdict, reason = crc.run_device(check, 'run-1')
        self.assertEqual(verdict, crc.NOT_CHECKED)
        self.assertIn('adb root is not available', reason)

    def test_installed_app_that_differs_is_not_checked(self):
        adb = Mock()
        adb.shell.side_effect = lambda *args, **kwargs: {'getprop': '34', 'pm': 'package:/data/app/base.apk'}.get(
            args[0], 'other-hash  /data/app/base.apk')
        check = crc.CrashReportCheck(adb, Mock(), Path('app.apk'), self.out)
        check.result['apk'] = dict(sha256='apk-hash')
        verdict, reason = crc.run_device(check, 'run-1')
        self.assertEqual(verdict, crc.NOT_CHECKED)
        self.assertIn('does not match', reason)
        check.screen.launch.assert_not_called()

    def test_prepare_refuses_android_10(self):
        adb = Mock()
        adb.shell.return_value = '29'
        check = crc.CrashReportCheck(adb, Mock(), Path('app.apk'), self.out)
        with self.assertRaisesRegex(crc.NotChecked, 'API 29'):
            check.prepare()
        adb.run.assert_not_called()

    def test_reopen_without_the_recorded_exit_is_an_app_failure(self):
        adb = Mock()
        adb.run.return_value = b'11-11 06:40:01 D SomethingElse: started\n'
        check = crc.CrashReportCheck(adb, Mock(), Path('app.apk'), self.out, launch_seconds=0)
        with self.assertRaisesRegex(crc.AppFailed, 'did not record the native crash exit'):
            check.send_reports()

    def test_check_id_is_quoted_for_the_device_shell(self):
        adb = Mock()
        pids = iter(['123', 'stopped'])
        adb.shell.side_effect = lambda *args, **kwargs: (next(pids) if args[0] == 'pidof' else
                                                         '1794379190' if args[0] == 'date' else '')
        check = crc.CrashReportCheck(adb, Mock(), Path('app.apk'), self.out, background_seconds=0)
        check.crash_in_background('a;b c')
        broadcast = next(call.args for call in adb.shell.call_args_list if call.args[:2] == ('am', 'broadcast'))
        self.assertEqual(broadcast[-2:], ('check_id', "'a;b c'"))

    def test_exit_codes(self):
        self.assertEqual(crc.exit_code(crc.PASSED, []), 0)
        self.assertEqual(crc.exit_code(crc.PASSED, ['ui gone']), crc.CLEANUP_FAILED)
        self.assertEqual(crc.exit_code(crc.FAILED, ['ui gone']), 1)
        self.assertEqual(crc.exit_code(crc.NOT_CHECKED, []), 2)


if __name__ == '__main__':
    unittest.main()


class DeviceErrorKindTest(unittest.TestCase):
    """Only what the app did fails the run; tool, adb and rental errors after preparation are not checked."""

    def setUp(self):
        # Device steps write evidence (logcat.txt, snapshots) into the output folder.
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.out = Path(folder.name)

    def device(self, pids, broadcast=None, logcat=b'SystemExitReporter: Recorded system exit reason=5'):
        adb = Mock()
        pids = iter(pids)

        def shell(*args, **kwargs):
            if args[0] == 'pidof':
                answer = next(pids)
                if isinstance(answer, Exception):
                    raise answer
                return answer
            if args[:2] == ('am', 'broadcast') and broadcast:
                raise broadcast
            return '1794379190' if args[0] == 'date' else ''
        adb.shell.side_effect = shell
        adb.run.return_value = logcat
        check = crc.CrashReportCheck(adb, Mock(), Path('app.apk'), self.out, timeout=0, background_seconds=0,
                                     launch_seconds=0, crash_seconds=0)
        check.prepare = Mock()
        return check

    def test_ui_command_time_out_is_not_checked(self):
        check = self.device(['123', 'stopped'])

        def run(*args, **kwargs):
            raise subprocess.TimeoutExpired('devices.py ui', 660)
        check.screen = Screen(Path('devices.py'), 'emulator-5574', 'g1', self.out, run=run)
        verdict, reason = crc.run_device(check, 'run-1')
        self.assertEqual(verdict, crc.NOT_CHECKED)
        self.assertIn('TimeoutExpired', reason)

    def test_adb_lost_while_waiting_for_the_crash_is_not_checked(self):
        check = self.device(['123', CheckFailed('adb shell pidof failed: device offline')])
        verdict, reason = crc.run_device(check, 'run-1')
        self.assertEqual(verdict, crc.NOT_CHECKED)
        self.assertIn('device offline', reason)

    def test_failed_broadcast_is_not_checked(self):
        check = self.device(['123'], broadcast=CheckFailed('adb shell am broadcast failed: closed'))
        self.assertEqual(crc.run_device(check, 'run-1')[0], crc.NOT_CHECKED)

    def test_empty_pidof_answer_is_not_read_as_ended(self):
        check = self.device(['123', ''])
        verdict, reason = crc.run_device(check, 'run-1')
        self.assertEqual(verdict, crc.NOT_CHECKED)
        self.assertIn("answered ''", reason)

    def test_screen_that_never_appears_fails(self):
        check = self.device(['123', 'stopped'])
        check.screen.wait.side_effect = ScreenTimeout('The play screen did not appear within 60 s')
        verdict, reason = crc.run_device(check, 'run-1')
        self.assertEqual(verdict, crc.FAILED)
        self.assertIn('did not appear', reason)

    def test_app_still_running_after_the_crash_request_fails(self):
        check = self.device(['123'] * 50)
        verdict, reason = crc.run_device(check, 'run-1')
        self.assertEqual(verdict, crc.FAILED)
        self.assertIn('still running', reason)

    def test_app_gone_before_the_crash_request_fails(self):
        check = self.device(['stopped'])
        self.assertEqual(crc.run_device(check, 'run-1')[0], crc.FAILED)

    def test_missing_exit_record_fails(self):
        check = self.device(['123', 'stopped'], logcat=b'nothing recorded')
        verdict, reason = crc.run_device(check, 'run-1')
        self.assertEqual(verdict, crc.FAILED)
        self.assertIn('did not record', reason)

    def test_normal_path_reaches_the_server_part(self):
        check = self.device(['123', 'stopped'])
        self.assertEqual(crc.run_device(check, 'run-1'), (None, None))
        self.assertEqual(check.result['crash_requested_ms'], 1794379190000)


class CommandLineTest(unittest.TestCase):
    def test_check_id_with_spaces_or_shell_characters_is_refused_at_start(self):
        for value in ('release 4.2.2', 'x;id', '', 'a' * 81):
            with self.subTest(value=value), tempfile.TemporaryDirectory() as folder:
                with self.assertRaises(SystemExit) as stop, unittest.mock.patch('sys.stderr'):
                    crc.main(['--apk', 'app.apk', '--serial', 's', '--generation', 'g', '--devices-tool', __file__,
                              '--out', folder, '--check-id', value])
                self.assertEqual(stop.exception.code, 2)
                self.assertEqual(list(Path(folder).iterdir()), [])

    def test_plain_check_id_is_accepted(self):
        self.assertEqual(crc.check_id_argument('release-4.2.2_121'), 'release-4.2.2_121')
