"""Offline checks for the crash report check: report parsing, matching, waiting and verdicts."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import crash_report_check as crc
from release_store_check import CheckFailed

CRASH_MS = 1794379190000


def event(name, installation, issue_id, title, subtitle, error_type, keys, version='4.2.2 (121)'):
    lines = [f'- name: |', f'    projects/1/apps/x/events/{name}', '  eventTime: |', "    '2026-11-11T06:40:01Z'",
             '  issue: |', f'    id: {issue_id}', f"    title: '{title}'", f'    subtitle: {subtitle}',
             f'    errorType: {error_type}', '  version: |', '    displayVersion: 4.2.2',
             f"    displayName: {version}", '  customKeys: |']
    for key, value in keys.items():
        lines += value if isinstance(value, list) else [f'    {key}: {value}']
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


def native(**changes):
    return event('n1', 'INSTALL-A', 'f4e2', '[libc.so]', 'SIGSEGV', 'FATAL', dict(NATIVE_KEYS, **changes))


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
        return crc.wait_for_reports(read, 'run-1', '4.2.2 (121)', CRASH_MS, timeout, interval,
                                    sleep=sleep, clock=lambda: now[0])

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


class DeviceVerdictTest(unittest.TestCase):
    def check(self):
        check = crc.CrashReportCheck(Mock(), Mock(), Path('app.apk'), Path('.'))
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
        check.crash_in_background.side_effect = CheckFailed('The app was still running')
        check.adb.shell.side_effect = CheckFailed('adb gone')
        check.screen.ui.side_effect = CheckFailed('ui gone')
        self.assertEqual(crc.run_device(check, 'run-1'), (crc.FAILED, 'The app was still running'))
        self.assertEqual(len(check.result['cleanup_errors']), 2)
        check.send_reports.assert_not_called()

    def test_prepare_turns_device_errors_into_not_checked(self):
        adb = Mock()
        adb.root.side_effect = CheckFailed('adb root is not available')
        check = crc.CrashReportCheck(adb, Mock(), Path('app.apk'), Path('.'))
        with self.assertRaises(crc.NotChecked):
            check.prepare()

    def test_prepare_refuses_android_10(self):
        adb = Mock()
        adb.shell.return_value = '29'
        check = crc.CrashReportCheck(adb, Mock(), Path('app.apk'), Path('.'))
        with self.assertRaisesRegex(crc.NotChecked, 'API 29'):
            check.prepare()
        adb.run.assert_not_called()

    def test_reopen_without_the_recorded_exit_is_an_app_failure(self):
        adb = Mock()
        adb.run.return_value = b'11-11 06:40:01 D SomethingElse: started\n'
        check = crc.CrashReportCheck(adb, Mock(), Path('app.apk'), Path('.'), launch_seconds=0)
        with self.assertRaisesRegex(CheckFailed, 'did not record the native crash exit'):
            check.send_reports()

    def test_exit_codes(self):
        self.assertEqual(crc.exit_code(crc.PASSED, []), 0)
        self.assertEqual(crc.exit_code(crc.PASSED, ['ui gone']), crc.CLEANUP_FAILED)
        self.assertEqual(crc.exit_code(crc.FAILED, ['ui gone']), 1)
        self.assertEqual(crc.exit_code(crc.NOT_CHECKED, []), 2)


if __name__ == '__main__':
    unittest.main()
