"""Verify the upgrade check's seeding and its verdict on before/after snapshots."""
import contextlib
import copy
import io
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest import mock
import wave

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import upgrade_check as uc  # noqa: E402

PREFS_419 = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="SortMethod" value="0" />
    <boolean name="SortOrder" value="false" />
    <boolean name="SlideMode" value="true" />
    <long name="PrevStoreCount" value="78" />
    <string name="SelectedTheme">com.kimjisub.launchpad</string>
</map>
"""
SCHEMA = ('CREATE TABLE `Unipack` (`id` TEXT NOT NULL, `bookmark` INTEGER NOT NULL, `openCount` INTEGER NOT NULL, '
          '`lastOpenedAt` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))')


def snapshot(trace=None):
    created = {p['id']: 1_792_979_540_000 + i for i, p in enumerate(uc.PACKS)}
    prefs = uc.seeded_prefs(uc.parse_prefs(PREFS_419), trace)
    return dict(db=dict(user_version=2, rows=uc.expected_rows(created)), prefs=prefs,
                files={f'{p["id"]}/info': 'aa' for p in uc.PACKS} | {'upgrade_check_a/sounds/silence.wav': 'bb'},
                titles=sorted(p['title'] for p in uc.PACKS))


class PrefsTest(unittest.TestCase):
    def test_round_trip_keeps_every_type(self):
        prefs = uc.parse_prefs(PREFS_419)
        self.assertEqual(prefs['SortMethod'], ('int', 0))
        self.assertEqual(prefs['SortOrder'], ('boolean', False))
        self.assertEqual(prefs['PrevStoreCount'], ('long', 78))
        self.assertEqual(prefs['SelectedTheme'], ('string', 'com.kimjisub.launchpad'))
        self.assertEqual(uc.parse_prefs(uc.render_prefs(prefs)), prefs)

    def test_missing_file_is_empty(self):
        self.assertEqual(uc.parse_prefs(None), {})
        self.assertEqual(uc.parse_prefs(''), {})

    def test_seed_sets_each_trace_state_and_keeps_other_keys(self):
        existing = uc.parse_prefs(PREFS_419) | {uc.TRACE_KEY: ('boolean', True)}
        self.assertNotIn(uc.TRACE_KEY, uc.seeded_prefs(existing, None))
        self.assertEqual(uc.seeded_prefs(existing, False)[uc.TRACE_KEY], ('boolean', False))
        self.assertEqual(uc.seeded_prefs({}, True)[uc.TRACE_KEY], ('boolean', True))
        self.assertEqual(uc.seeded_prefs(existing, None)['PrevStoreCount'], ('long', 78))
        for key, value in uc.SEEDED_PREFS.items():
            self.assertEqual(uc.seeded_prefs({}, None)[key], value)

    def test_seeded_settings_must_be_read_back_by_the_older_build(self):
        for trace in uc.SCENARIOS.values():
            self.assertEqual(uc.seed_mismatches(uc.seeded_prefs(uc.parse_prefs(PREFS_419), trace), trace), [])
        rewritten = uc.seeded_prefs({}, False) | {'SlideMode': ('boolean', False)}
        self.assertEqual(len(uc.seed_mismatches(rewritten, False)), 1)
        self.assertIn('SlideMode', uc.seed_mismatches(rewritten, False)[0])
        self.assertIn(uc.TRACE_KEY, uc.seed_mismatches(uc.seeded_prefs({}, None), False)[0])
        self.assertIn('seeded absent', uc.seed_mismatches(uc.seeded_prefs({}, True), None)[0])


class DatabaseTest(unittest.TestCase):
    def test_seeded_rows_read_back_as_expected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / uc.DB_NAME
            conn = sqlite3.connect(path)
            conn.execute(SCHEMA)
            conn.execute('PRAGMA user_version = 2')
            created = {}
            for i, pack in enumerate(uc.PACKS):
                created[pack['id']] = 1000 + i
                conn.execute('INSERT INTO Unipack VALUES (?, 0, 0, NULL, ?)', (pack['id'], 1000 + i))
            uc.seed_rows(conn)
            conn.close()
            db = uc.read_db(path)
        self.assertEqual(db['user_version'], 2)
        self.assertEqual(db['rows'], uc.expected_rows(created))
        self.assertEqual(sum(r['bookmark'] for r in db['rows'].values()), 1)
        self.assertEqual(sum(r['openCount'] for r in db['rows'].values()), 3)


class CompareTest(unittest.TestCase):
    def test_identical_snapshots_pass_for_every_trace_state(self):
        for trace in uc.SCENARIOS.values():
            self.assertEqual(uc.compare(snapshot(trace), snapshot(trace), trace), [])

    def test_new_settings_keys_and_higher_database_version_are_allowed(self):
        after = snapshot(True)
        after['prefs']['Watermark'] = ('boolean', True)
        after['db']['user_version'] = 3
        self.assertEqual(uc.compare(snapshot(True), after, True), [])

    def assert_one_problem(self, after, trace, text):
        problems = uc.compare(snapshot(trace), after, trace)
        self.assertEqual(len(problems), 1, problems)
        self.assertIn(text, problems[0])

    def test_each_loss_is_reported(self):
        cases = []
        lost_file = snapshot(); del lost_file['files']['upgrade_check_a/info']; cases.append((lost_file, 'pack files differ'))
        changed = snapshot(); changed['files']['upgrade_check_a/info'] = 'cc'; cases.append((changed, "changed ['upgrade_check_a/info']"))
        bookmark = snapshot(); bookmark['db']['rows']['upgrade_check_a']['bookmark'] = 0; cases.append((bookmark, 'row upgrade_check_a'))
        count = snapshot(); count['db']['rows']['upgrade_check_b']['openCount'] = 0; cases.append((count, 'row upgrade_check_b'))
        row = snapshot(); del row['db']['rows']['upgrade_check_c']; cases.append((row, 'after None'))
        wiped = snapshot(); wiped['db']['user_version'] = 1; cases.append((wiped, 'version went down'))
        sort = snapshot(); sort['prefs']['SortMethod'] = ('int', 4); cases.append((sort, 'setting SortMethod'))
        slide = snapshot(); del slide['prefs']['SlideMode']; cases.append((slide, 'setting SlideMode'))
        listed = snapshot(); listed['titles'] = listed['titles'][:2]; cases.append((listed, 'not listed on screen'))
        for after, text in cases:
            with self.subTest(text):
                self.assert_one_problem(after, None, text)

    def test_trace_setting_changes_are_reported(self):
        flipped = snapshot(False)
        flipped['prefs'][uc.TRACE_KEY] = ('boolean', True)
        self.assert_one_problem(flipped, False, f'setting {uc.TRACE_KEY}')
        written = snapshot(None)
        written['prefs'][uc.TRACE_KEY] = ('boolean', True)
        self.assert_one_problem(written, None, 'written by opening the app')

    def test_unreadable_database_and_failed_start_are_reported(self):
        after = snapshot()
        after['db'] = dict(error='UniPad.db cannot be read: no such column: openCount')
        self.assert_one_problem(after, None, 'no such column')
        problems = uc.compare(snapshot(), snapshot(), None, ['after the upgrade, app stopped right after opening'])
        self.assertEqual(problems, ['after the upgrade, app stopped right after opening'])

    def test_a_row_that_appears_is_reported(self):
        after = snapshot()
        after['db']['rows']['other'] = copy.deepcopy(after['db']['rows']['upgrade_check_c'])
        self.assert_one_problem(after, None, 'row other appeared')


class DeviceOutputTest(unittest.TestCase):
    def test_file_hashes_are_relative_to_the_pack_folder(self):
        listing = ('ab12  /data/media/0/Android/data/p/files/UniPack/upgrade_check_a/keyLED/1 1 1 1\n'
                   'cd34  /data/media/0/Android/data/p/files/UniPack/upgrade_check_a/info\n')
        self.assertEqual(uc.file_hashes(listing, '/data/media/0/Android/data/p/files/UniPack'),
                         {'upgrade_check_a/keyLED/1 1 1 1': 'ab12', 'upgrade_check_a/info': 'cd34'})

    def test_app_events_match_only_the_package(self):
        package = 'com.kimjisub.launchpad'
        lines = '\n'.join([
            'E AndroidRuntime: Process: com.kimjisub.launchpad, PID: 5136',
            'E AndroidRuntime: Process: com.kimjisub.launchpad.dev, PID: 7000',
            'F DEBUG   : pid: 5136, tid: 5150, name: RenderThread  >>> com.kimjisub.launchpad <<<',
            'I am_crash: [5136,0,com.kimjisub.launchpad,948452932,java.lang.IllegalStateException]',
            'I am_anr  : [0,5136,com.kimjisub.launchpad,953695812,Input dispatching timed out]',
            'I am_anr  : [0,7000,com.kimjisub.launchpad.dev,953695812,Input dispatching timed out]',
            'I am_proc_start: [0,5136,10208,com.kimjisub.launchpad,next-top-activity]',
            'I am_crash: [0,7100,com.android.systemui,1,java.lang.RuntimeException,com.kimjisub.launchpad gone]',
            'I am_crash: [0,5136,com.kimjisub.launchpad,948452932,java.lang.IllegalStateException]',
        ])
        self.assertEqual(len(uc.app_events(lines, package)), 5)
        self.assertEqual(uc.app_events('I am_proc_start: [0,5136,10208,com.kimjisub.launchpad,x]', package), [])

    def test_screen_texts_read_node_text(self):
        xml = ('<hierarchy><node text="Upgrade Check A"><node text="" /><node text="Upgrade Check B" /></node>'
               '</hierarchy>')
        self.assertEqual(uc.screen_texts(xml), {'Upgrade Check A', 'Upgrade Check B'})
        self.assertEqual(uc.screen_texts('ERROR: could not get idle state.'), set())


class BuildToolsTest(unittest.TestCase):
    def test_a_release_sorts_after_its_preview(self):
        names = ['37.0.0', '36.1.0', '37.0.0-rc1', '9.0.0', '37.0.0-rc2']
        self.assertEqual(sorted(names, key=uc.build_tools_order), ['9.0.0', '36.1.0', '37.0.0-rc1', '37.0.0-rc2', '37.0.0'])


class FakeDevice:
    """Records adb calls; answers the questions main() asks before the scenarios."""
    answers = {'getprop ro.build.version.sdk': '35', 'id -u': '0', 'settings get global airplane_mode_on': '0'}

    def __init__(self, serial=None):
        self.calls = []

    def __call__(self, *args, check=True, timeout=600):
        self.calls.append(args)
        return ''

    def sh(self, command, check=True):
        self.calls.append(('shell', command))
        return self.answers.get(command, '')

    def device_time(self):
        return '10-26 12:00:00.000'


class ScenarioTest(unittest.TestCase):
    def test_a_newer_build_that_crashes_at_start_fails_the_check(self):
        with tempfile.TemporaryDirectory() as tmp:
            run = uc.UpgradeRun(FakeDevice(), Path('old.apk'), Path('new.apk'), 'com.kimjisub.launchpad', Path(tmp))
            after = snapshot(True)
            after['titles'] = []
            crash = 'E AndroidRuntime: Process: com.kimjisub.launchpad, PID: 5136'
            with mock.patch.object(run, 'prepare', return_value=snapshot(True)), \
                    mock.patch.object(run, 'launch', side_effect=uc.AppNotReady('com.kimjisub.launchpad stopped right after opening')), \
                    mock.patch.object(run, 'snapshot', return_value=after), \
                    mock.patch.object(run, 'events', return_value=[crash]) as events:
                result = run.scenario('trace-on', True)
            saved = json.loads((Path(tmp) / 'trace-on/result.json').read_text())
        self.assertFalse(result['passed'])
        self.assertEqual(saved['problems'], result['problems'])
        self.assertIn('after the upgrade, com.kimjisub.launchpad stopped right after opening', result['problems'][0])
        self.assertIn(f'crash or ANR: {crash}', result['problems'])
        events.assert_called_once_with('10-26 12:00:00.000')

    def test_a_newer_build_that_does_not_install_fails_the_check(self):
        class RefusingDevice(FakeDevice):
            def __call__(self, *args, check=True, timeout=600):
                raise uc.SetupError('INSTALL_FAILED_INVALID_APK')

        run = uc.UpgradeRun(RefusingDevice(), Path('old.apk'), Path('new.apk'), 'p', Path('.'))
        with mock.patch.object(run, 'launch') as launch:
            problems = run.upgrade()
        self.assertIn('did not install over the older one: INSTALL_FAILED_INVALID_APK', problems[0])
        launch.assert_not_called()


class MainTest(unittest.TestCase):
    def run_main(self, scenario, scenarios=('trace-on',)):
        identity = dict(package='com.kimjisub.launchpad', signers=['ab'], version_name='4.2.1')
        devices = []

        def make_device(serial):
            devices.append(FakeDevice())
            return devices[-1]

        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        out = Path(tmp.name) / 'out'
        with mock.patch.object(uc, 'universal_apk', side_effect=lambda source, work: source), \
                mock.patch.object(uc, 'apk_identity', side_effect=lambda apk: identity | dict(version_code=int(apk.stem))), \
                mock.patch.object(uc, 'Device', side_effect=make_device), \
                mock.patch.object(uc.UpgradeRun, 'scenario', side_effect=scenario), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = uc.main(['--serial', 'emulator-5554', '--old', '118.apk', '--new', '120.apk', '--out', str(out),
                            *[arg for name in scenarios for arg in ('--scenario', name)]])
        return code, out, devices[0].calls

    def test_a_failed_scenario_exits_1_with_a_report(self):
        failed = dict(scenario='trace-on', passed=False, problems=['after the upgrade, app stopped'],
                      after=dict(db=dict(error='UniPad.db is missing'), prefs={}), pack_files=0)
        code, out, _ = self.run_main(lambda name, trace: failed)
        self.assertEqual(code, 1)
        self.assertFalse(json.loads((out / 'report.json').read_text())['passed'])

    def test_a_later_scenario_that_cannot_start_keeps_the_earlier_failure(self):
        failed = dict(scenario='trace-off', passed=False, problems=['crash or ANR: Process: com.kimjisub.launchpad'],
                      after=dict(db=dict(rows={}), prefs={}), pack_files=16)

        def run(name, trace):
            if name == 'trace-on':
                raise uc.SetupError('trace-on: the older build: did not reach its pack list within 90 s')
            return failed
        code, out, calls = self.run_main(run, scenarios=('trace-off', 'trace-on'))
        self.assertEqual(code, 1)
        report = json.loads((out / 'report.json').read_text())
        self.assertFalse(report['passed'])
        self.assertEqual([r['scenario'] for r in report['results']], ['trace-off'])
        self.assertIn('did not reach its pack list', report['not_run']['trace-on'])
        self.assertFalse((out / 'setup-error.txt').exists())
        self.assertIn(('uninstall', 'com.kimjisub.launchpad'), calls)

    def test_a_first_scenario_that_cannot_start_exits_2(self):
        def run(name, trace):
            raise uc.SetupError('the older build: did not reach its pack list within 90 s')
        code, out, _ = self.run_main(run, scenarios=('trace-off', 'trace-on'))
        self.assertEqual(code, 2)
        self.assertFalse((out / 'report.json').exists())

    def test_an_unexpected_error_exits_2_and_uninstalls_before_restoring_the_network(self):
        def broken(name, trace):
            raise sqlite3.OperationalError('database is locked')
        code, out, calls = self.run_main(broken)
        self.assertEqual(code, 2)
        self.assertIn('database is locked', (out / 'setup-error.txt').read_text())
        self.assertFalse((out / 'report.json').exists())
        uninstall = calls.index(('uninstall', 'com.kimjisub.launchpad'))
        self.assertGreater(calls.index(('shell', 'cmd connectivity airplane-mode disable')), uninstall)


class PackTest(unittest.TestCase):
    def test_pack_has_the_files_a_pack_needs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / 'pack'
            uc.write_pack(root, 'Upgrade Check A')
            self.assertIn('title=Upgrade Check A\n', (root / 'info').read_text())
            self.assertEqual(len((root / 'keySound').read_text().splitlines()), 128)
            with wave.open(str(root / 'sounds/silence.wav')) as sound:
                self.assertEqual((sound.getnchannels(), sound.getframerate(), sound.getnframes()), (1, 44100, 441))
            self.assertTrue((root / 'keyLED/1 1 1 1').is_file())
            self.assertTrue((root / 'autoPlay').is_file())


if __name__ == '__main__':
    unittest.main()
