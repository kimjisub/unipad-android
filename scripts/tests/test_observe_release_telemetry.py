"""Offline parsing and cleanup checks for the release telemetry observer."""
import json
import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import observe_release_telemetry as observe


class ParsingTest(unittest.TestCase):
    def test_keeps_every_parameter_and_unknown_nested_values(self):
        line = ('10-26 11:00:00.846 1400 7527 V FA-SVC : Logging event: origin=app,'
                'name=play_first_input,params=Bundle[{trigger=pad, input_source=screen, '
                'future=Bundle[{a=1, b=two}], list=[a, b], ga_screen_id(_si)=-123}]')
        event, = observe.parse_events(line)
        self.assertEqual(event['name'], 'play_first_input')
        self.assertEqual(event['parameters'], {'trigger': 'pad', 'input_source': 'screen',
                         'future': 'Bundle[{a=1, b=two}]', 'list': '[a, b]', 'ga_screen_id(_si)': '-123'})
        self.assertIsNone(event['debug'])
        self.assertEqual(event['raw'], line)

    def test_debug_is_associated_with_the_event_not_a_nearby_event(self):
        lines = ('FA: Logging event (FE): other, Bundle[{_dbg=1}]\n'
                 'FA: Logging event (FE): play_first_input, Bundle[{input_source=screen}]\n'
                 'FA-SVC: Logging event: origin=app,name=play_first_input,'
                 'params=Bundle[{input_source=midi, debug(_dbg)=1, unknown=keep}]')
        events = observe.parse_events(lines)
        self.assertIsNone(events[1]['debug'])
        self.assertTrue(events[2]['debug'])
        self.assertEqual(events[2]['parameters']['unknown'], 'keep')

    def test_upload_protocol_text_retains_all_parameters(self):
        text = '''event {
  name: play_first_input
  params {
    name: input_source
    string_value: screen
  }
  params {
    name: _dbg
    int_value: 1
  }
  params {
    name: future
    double_value: 0.5
  }
}'''
        event, = observe.parse_events(text)
        self.assertTrue(event['debug'])
        self.assertEqual(event['parameters'], {'input_source': 'screen', '_dbg': '1', 'future': '0.5'})

    def test_does_not_invent_event_from_truncated_or_unrelated_logs(self):
        self.assertEqual(observe.parse_events('name=play_first_input,params=Bundle[{input_source=screen'), [])
        self.assertEqual(observe.parse_events('Something else with play_first_input and _dbg=1'), [])

    def test_crash_keys_and_binary_queue_log(self):
        result = observe.parse_session('{"screen":"PlayActivity","future":"keep"}',
                     b'\x00\x00\x00\x16123 MainActivity paused\n\x18125 PlayActivity resumed\n'
                     b'garbage\n!130 MainActivity destroyed\n')
        self.assertEqual(result['screen'], 'PlayActivity')
        self.assertEqual(result['keys']['future'], 'keep')
        self.assertEqual(result['transitions'], ['123 MainActivity paused', '125 PlayActivity resumed',
                                                '130 MainActivity destroyed'])

    def test_missing_screen_is_observable_and_malformed_keys_fail(self):
        self.assertIsNone(observe.parse_session('{}', b'')['screen'])
        for text in ('[1]', 'invalid'):
            with self.assertRaises(observe.CheckFailed):
                observe.parse_session(text, b'')


class ScreenTest(unittest.TestCase):
    def test_release_grid_container_locates_first_pad_without_view_ids(self):
        from release_store_check import Node
        root = Node('', '', False, (0, 0, 2400, 1080), [
            Node('', '', False, (716, 24, 1684, 992)),
            Node('', '', False, (595, 24, 716, 992)),
            Node('', 'play_logo', False, (2122, 42, 2358, 101))])
        self.assertEqual(observe.first_pad(root).center, (776, 84))
        root.children.pop()
        self.assertIsNone(observe.first_pad(root))

    def test_an_unrelated_square_is_not_accepted_as_a_grid(self):
        from release_store_check import Node
        root = Node('', '', False, (0, 0, 2400, 1080), [
            Node('', '', False, (100, 100, 900, 900)),
            Node('', 'play_logo', False, (2122, 42, 2358, 101))])
        self.assertIsNone(observe.first_pad(root))


class PreparationTest(unittest.TestCase):
    def test_fixture_uses_sounds_directory_and_preserves_app_owner(self):
        adb = Mock()
        def shell(*args, **kwargs):
            if args[0] == 'dumpsys':
                return 'versionCode=120\n versionName=4.2.1\n flags=[ HAS_CODE ]'
            if args[0] == 'pm':
                return 'package:/data/app/base.apk'
            if args[0] == 'sha256sum':
                return hashlib.sha256(b'apk').hexdigest() + '  /data/app/base.apk'
            if args[0] == 'stat':
                return '10207:10207'
            if args[:2] == ('getprop', observe.DEBUG_PROP):
                return observe.PACKAGE
            return ''
        adb.shell.side_effect = shell
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'candidate.apk'
            apk.write_bytes(b'apk')
            observer = observe.Observer(adb, Mock(), apk)
            observer.prepare()
        base = f'/data/user/0/{observe.PACKAGE}/files/UniPack'
        pushes = [call.args for call in adb.run.call_args_list if call.args[0] == 'push']
        self.assertTrue(any(args[2] == f'{base}/{observe.PACK}/sounds/pcm.wav' for args in pushes))
        self.assertTrue(any(args[2] == f'{base}/{observe.PACK}/keySound' for args in pushes))
        self.assertIn(unittest.mock.call('chown', '-R', '10207:10207', base), adb.shell.call_args_list)
        self.assertIn(unittest.mock.call('restorecon', '-RF', base), adb.shell.call_args_list)

    def test_different_installed_apk_is_not_reported_as_the_candidate(self):
        adb = Mock()
        adb.shell.side_effect = lambda *args, **kwargs: ('package:/data/app/base.apk' if args[0] == 'pm' else 'different')
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'candidate.apk'
            apk.write_bytes(b'apk')
            with self.assertRaisesRegex(observe.CheckFailed, 'does not match'):
                observe.Observer(adb, Mock(), apk).prepare()

    def test_root_refusal_stops_before_install_or_app_input(self):
        adb, ui = Mock(), Mock()
        adb.root.side_effect = observe.CheckFailed('root unavailable')
        with self.assertRaisesRegex(observe.CheckFailed, 'root unavailable'):
            observe.Observer(adb, ui, Path('candidate.apk')).prepare()
        adb.run.assert_not_called()
        ui.launch.assert_not_called()


class CollectionTest(unittest.TestCase):
    def test_retains_unknown_input_source_and_debug_flag_from_upload(self):
        adb = Mock()
        adb.shell.side_effect = lambda *args, **kwargs: ('new' if args[0] == 'ls' else '{"screen":"MainActivity"}')
        log = ('Logging event: origin=app,name=play_first_input,'
               'params=Bundle[{input_source=future-controller, new_value=keep, _dbg=1}]')
        adb.run.side_effect = lambda *args, **kwargs: log.encode() if args[0] == 'logcat' else b'1 PlayActivity paused\n'
        observer = observe.Observer(adb, Mock(), Path('candidate.apk'), timeout=0)
        observer.sessions_path, observer.previous_sessions = '/sessions', {'old'}
        observer.collect()
        self.assertEqual(observer.result['events'][0]['parameters']['input_source'], 'future-controller')
        self.assertTrue(observer.result['first_input_debug'])
        self.assertEqual(observer.result['sessions'][0]['id'], 'new')

    def test_does_not_accept_a_stale_session_or_an_event_without_input_source(self):
        for log, previous in (
                ('Logging event: origin=app,name=play_first_input,params=Bundle[{_dbg=1}]', set()),
                ('Logging event: origin=app,name=play_first_input,params=Bundle[{input_source=screen}]', {'old'})):
            adb = Mock()
            adb.shell.side_effect = lambda *args, **kwargs: ('old' if args[0] == 'ls' else '{"screen":"MainActivity"}')
            adb.run.side_effect = lambda *args, **kwargs: log.encode() if args[0] == 'logcat' else b'1 PlayActivity paused\n'
            observer = observe.Observer(adb, Mock(), Path('candidate.apk'), timeout=0)
            observer.sessions_path, observer.previous_sessions = '/sessions', previous
            with self.assertRaisesRegex(observe.CheckFailed, 'Not observed'):
                observer.collect()


class CleanupTest(unittest.TestCase):
    def run_observer(self, failure=None, cleanup_failure=False):
        props = {observe.DEBUG_PROP: 'old.app', observe.LOG_PROP: 'WARN'}
        adb = Mock()
        def shell(*args, **kwargs):
            if args[0] == 'getprop':
                return props.get(args[1], '')
            if args[0] == 'setprop':
                if cleanup_failure and args[1:] == (observe.DEBUG_PROP, '.none.'):
                    raise observe.CheckFailed('reset refused')
                props[args[1]] = args[2]
            return ''
        adb.shell.side_effect = shell
        observer = observe.Observer(adb, Mock(), Path('candidate.apk'), timeout=0)
        observer.prepare = Mock(side_effect=failure)
        observer.drive = Mock()
        observer.collect = Mock()
        with tempfile.TemporaryDirectory() as directory:
            code = observe.run_and_record(observer, Path(directory) / 'result.json')
            result = json.loads((Path(directory) / 'result.json').read_text())
        return code, result, props, adb

    def test_success_resets_and_reads_both_properties(self):
        code, result, props, _ = self.run_observer()
        self.assertEqual(code, 0)
        self.assertEqual(props, {observe.DEBUG_PROP: '.none.', observe.LOG_PROP: 'WARN'})
        self.assertEqual(result['cleanup']['analytics_debug'], '.none.')
        self.assertEqual(result['cleanup']['log_tag'], 'WARN')

    def test_failure_and_interrupt_restore_properties_and_keep_failure(self):
        for error in (observe.CheckFailed('root unavailable'), RuntimeError('UI failed'), KeyboardInterrupt()):
            code, result, props, _ = self.run_observer(error)
            self.assertNotEqual(code, 0)
            self.assertEqual(props[observe.DEBUG_PROP], '.none.')
            self.assertEqual(props[observe.LOG_PROP], 'WARN')
            self.assertEqual(result['status'], 'not_observed')

    def test_reset_failure_is_nonzero_and_still_restores_log_tag(self):
        code, result, props, adb = self.run_observer(cleanup_failure=True)
        self.assertNotEqual(code, 0)
        self.assertEqual(props[observe.LOG_PROP], 'WARN')
        self.assertTrue(result['cleanup']['errors'])
        self.assertIn(unittest.mock.call('getprop', observe.DEBUG_PROP), adb.shell.call_args_list)


if __name__ == '__main__':
    unittest.main()
