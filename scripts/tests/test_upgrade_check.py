"""Verify the upgrade check's seeding and its verdict on before/after snapshots."""
import contextlib
import copy
import hashlib
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
    def test_missing_seeded_backup_cannot_become_the_upgrade_baseline(self):
        before = snapshot()
        before['files'] = {f'{pack["id"]}/{name}': hashlib.sha256(content).hexdigest()
                           for pack in uc.PACKS
                           for name, content in uc.autoplay_files(pack.get('autoplay_backup')).items()}
        del before['files']['upgrade_check_b/autoPlay_2026_11_11-15_32_39']
        created = {key: row['createdAt'] for key, row in before['db']['rows'].items()}
        with tempfile.TemporaryDirectory() as tmp:
            run = uc.UpgradeRun(FakeDevice(), Path('old.apk'), Path('new.apk'), 'p', Path(tmp))
            with mock.patch.object(run, 'launch'), mock.patch.object(run, 'wait_for_rows', return_value=created), \
                    mock.patch.object(run, 'seed'), mock.patch.object(run, 'snapshot', return_value=before), \
                    mock.patch.object(run, 'events', return_value=[]):
                with self.assertRaisesRegex(uc.SetupError, 'seeded autoplay files: upgrade_check_b/autoPlay_'):
                    run.prepare(Path(tmp) / 'trace-absent', None)

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
    def test_device_seeding_includes_and_validates_every_autoplay_file(self):
        files = {}

        def put(local, remote, owner_of):
            files.update({f'{local.name}/{p.name}': hashlib.sha256(p.read_bytes()).hexdigest()
                          for p in local.iterdir() if p.is_file()})

        device = FakeDevice()
        device.put = put
        run = uc.UpgradeRun(device, Path('old.apk'), Path('new.apk'), 'p', Path('.'))
        with mock.patch.object(run, 'launch'), mock.patch.object(run, 'pull_db', return_value=snapshot()['db']):
            run.wait_for_rows(Path('.'))
        self.assertEqual(uc.autoplay_seed_mismatches(files), [])
        paths = sorted(p for p in files if Path(p).name.startswith('autoPlay'))
        self.assertEqual(len(paths), 5)
        for path in paths:
            for digest in (None, 'changed'):
                with self.subTest(file=path, digest=digest):
                    damaged = dict(files)
                    if digest is None:
                        del damaged[path]
                    else:
                        damaged[path] = digest
                    wrong = uc.autoplay_seed_mismatches(damaged)
                    self.assertEqual(len(wrong), 1, wrong)
                    self.assertIn(path, wrong[0])

    def test_mapping_fixtures_keep_the_original_in_both_backup_formats(self):
        with tempfile.TemporaryDirectory() as tmp:
            original = Path(tmp) / 'original'
            uc.write_pack(original, 'Original')
            original_bytes = (original / 'autoPlay').read_bytes()
            backups = set()
            for pack in uc.PACKS:
                root = Path(tmp) / pack['id']
                if backup := pack.get('autoplay_backup'):
                    uc.write_pack(root, pack['title'], backup)
                else:
                    uc.write_pack(root, pack['title'])
                if backup := pack.get('autoplay_backup'):
                    backups.add(backup)
                    self.assertEqual((root / backup).read_bytes(), original_bytes)
                    self.assertNotEqual((root / 'autoPlay').read_bytes(), original_bytes)
            self.assertEqual(backups, {'autoPlay_2026_11_11-15_32_39', 'autoPlay_previous'})

    def test_real_autoplay_file_changes_and_deletions_fail_the_upgrade(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / 'packs'
            for pack in uc.PACKS:
                if backup := pack.get('autoplay_backup'):
                    uc.write_pack(root / pack['id'], pack['title'], backup)
                else:
                    uc.write_pack(root / pack['id'], pack['title'])

            def files():
                return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in root.rglob('*') if p.is_file()}

            before = snapshot()
            before['files'] = files()
            targets = sorted(p for p in before['files'] if Path(p).name.startswith('autoPlay'))
            self.assertEqual(len(targets), 5)
            self.assertEqual(uc.compare(before, copy.deepcopy(before), None), [])
            for target in targets:
                path = root / target
                saved = path.read_bytes()
                for action in ('changed', 'lost'):
                    with self.subTest(file=target, action=action):
                        if action == 'changed':
                            path.write_bytes(b'c 2\no 8 8\n')
                        else:
                            path.unlink()
                        after = copy.deepcopy(before)
                        after['files'] = files()
                        problems = uc.compare(before, after, None)
                        self.assertEqual(len(problems), 1, problems)
                        self.assertIn(f"{action} ['{target}']", problems[0])
                        run = uc.UpgradeRun(FakeDevice(), Path('old.apk'), Path('new.apk'), 'p', Path(tmp) / 'results')
                        name = f'{action}-{target.replace("/", "-")}'
                        with mock.patch.object(run, 'prepare', return_value=before), \
                                mock.patch.object(run, 'upgrade', return_value=[]), \
                                mock.patch.object(run, 'snapshot', return_value=after), \
                                mock.patch.object(run, 'events', return_value=[]):
                            result = run.scenario(name, None)
                        report = json.loads((run.out / name / 'result.json').read_text())
                        self.assertFalse(result['passed'])
                        self.assertEqual(report['problems'], problems)
                        path.write_bytes(saved)

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



def raw_halves(left, right, width=64, height=36, header=16):
    """`screencap` raw output: left half one grey level, right half another."""
    import struct
    row = bytes([left, left, left, 255]) * (width // 2) + bytes([right, right, right, 255]) * (width - width // 2)
    return struct.pack('<III', width, height, 1) + b'\0' * (header - 12) + row * height


class LegacyThemeTest(unittest.TestCase):
    def test_raw_screenshots_with_either_header_are_read(self):
        for header in (12, 16):
            width, height, pixels = uc.raw_screen(raw_halves(0, 200, header=header))
            self.assertEqual((width, height, len(pixels)), (64, 36, 64 * 36 * 4))
        with self.assertRaises(ValueError):
            uc.raw_screen(raw_halves(0, 200)[:-2])

    def test_signature_is_the_grey_level_of_each_block(self):
        signature = uc.screen_signature(raw_halves(0, 200), step=1)
        self.assertEqual(len(signature), 16 * 9)
        self.assertEqual(signature[:8], [0.0] * 8)
        self.assertEqual([round(v, 6) for v in signature[8:16]], [200.0] * 8)
        self.assertEqual(uc.screen_signature(raw_halves(0, 200), step=4)[:16], signature[:16])

    def test_the_migrated_screen_must_look_like_the_theme_and_not_the_default(self):
        skin, default = uc.screen_signature(raw_halves(30, 120)), uc.screen_signature(raw_halves(10, 10))
        nearly_skin = [value + 1 for value in skin]
        self.assertEqual(uc.theme_screen_problems(nearly_skin, default, skin, 'sskin'), [])
        problems = uc.theme_screen_problems(default, default, skin, 'sskin')
        self.assertIn('does not look like sskin', problems[0])
        between = [(a + b) / 2 for a, b in zip(skin, default)]
        self.assertTrue(uc.theme_screen_problems(between, default, skin, 'sskin'))

    def test_the_setting_must_become_the_bundled_id_and_the_folder_must_go(self):
        migrated = {uc.THEME_KEY: ('string', 'asset://sskin'), 'SortMethod': ('int', 0)}
        self.assertEqual(uc.legacy_theme_problems(migrated, False, 'sskin'), [])
        kept = uc.legacy_theme_problems({uc.THEME_KEY: ('string', 'zip://sskin')}, True, 'sskin')
        self.assertEqual(len(kept), 2)
        self.assertIn('expected asset://sskin', kept[0])
        self.assertIn('files/themes/sskin was not deleted', kept[1])
        self.assertIn('read None', uc.legacy_theme_problems({}, False, 'sskin')[0])

    def test_the_seeded_theme_folder_is_the_bundled_theme(self):
        self.assertTrue((uc.THEME_ASSETS / 'sskin' / 'theme.json').is_file())
        self.assertEqual(uc.LEGACY_THEMES, {'legacy-theme': 'sskin'})
        self.assertIn('legacy-theme', uc.SCENARIOS)

    def test_the_legacy_state_is_left_after_the_older_build_and_judged_after_the_upgrade(self):
        class ThemeDevice(FakeDevice):
            folder_left = False

            def exists(self, remote):
                return self.folder_left

        with tempfile.TemporaryDirectory() as tmp:
            device = ThemeDevice()
            run = uc.UpgradeRun(device, Path('old.apk'), Path('new.apk'), 'p', Path(tmp))
            order = []
            after = snapshot()
            after['prefs'] = {**after['prefs'], uc.THEME_KEY: ('string', 'asset://sskin')}
            with mock.patch.object(run, 'prepare', return_value=snapshot()), \
                    mock.patch.object(run, 'leave_legacy_theme', side_effect=lambda f, t: order.append(('leave', t))), \
                    mock.patch.object(run, 'upgrade', side_effect=lambda: order.append('upgrade') or []), \
                    mock.patch.object(run, 'snapshot', return_value=after), \
                    mock.patch.object(run, 'theme_screen', return_value=[]) as screen, \
                    mock.patch.object(run, 'events', return_value=[]):
                passed = run.scenario('legacy-theme', None)
                device.folder_left = True
                after['prefs'] = snapshot()['prefs']
                failed = run.scenario('legacy-theme', None)
                skipped = run.scenario('trace-absent', None)
        self.assertEqual(order[:2], [('leave', 'sskin'), 'upgrade'])
        self.assertTrue(passed['passed'], passed['problems'])
        self.assertFalse(failed['passed'])
        self.assertEqual(len(failed['problems']), 2)
        self.assertTrue(skipped['passed'])
        self.assertEqual(screen.call_count, 2)
        self.assertNotIn(('leave', 'sskin'), order[4:])

    def test_theme_checks_wait_for_a_newer_build_that_opened(self):
        with tempfile.TemporaryDirectory() as tmp:
            run = uc.UpgradeRun(FakeDevice(), Path('old.apk'), Path('new.apk'), 'p', Path(tmp))
            with mock.patch.object(run, 'prepare', return_value=snapshot()), mock.patch.object(run, 'leave_legacy_theme'), \
                    mock.patch.object(run, 'upgrade', return_value=['after the upgrade, p stopped right after opening']), \
                    mock.patch.object(run, 'snapshot', return_value=snapshot()), \
                    mock.patch.object(run, 'theme_screen') as screen, mock.patch.object(run, 'events', return_value=[]):
                result = run.scenario('legacy-theme', None)
        self.assertEqual(result['problems'], ['after the upgrade, p stopped right after opening'])
        screen.assert_not_called()

    def test_play_screens_are_taken_migrated_then_default_then_chosen(self):
        with tempfile.TemporaryDirectory() as tmp:
            run = uc.UpgradeRun(FakeDevice(), Path('old.apk'), Path('new.apk'), 'p', Path(tmp))
            settings = []
            shots = iter([uc.screen_signature(raw_halves(30, 120)), uc.screen_signature(raw_halves(10, 10)),
                          uc.screen_signature(raw_halves(31, 121))])
            with mock.patch.object(run, 'write_theme_setting', side_effect=lambda f, v: settings.append(v)), \
                    mock.patch.object(run, 'open_play', side_effect=lambda pack, png: next(shots)) as open_play:
                problems = run.theme_screen(Path(tmp), 'sskin')
        self.assertEqual(problems, [])
        self.assertEqual(settings, [None, 'asset://sskin'])
        self.assertEqual([c.args for c in open_play.call_args_list],
                         [('upgrade_check_a', Path(tmp) / f'play-{n}.png') for n in ('migrated', 'default', 'sskin')])

    def test_the_theme_setting_is_written_beside_the_other_settings_and_read_back(self):
        with tempfile.TemporaryDirectory() as tmp:
            run = uc.UpgradeRun(FakeDevice(), Path('old.apk'), Path('new.apk'), 'p', Path(tmp))
            written = {}

            def put(local, remote, owner_of):
                written['xml'] = local.read_text()
            run.d.put = put
            with mock.patch.object(run, 'read', side_effect=lambda remote: written.get('xml', PREFS_419)):
                run.write_theme_setting(Path(tmp) / 'w', 'zip://sskin')
                prefs = uc.parse_prefs(written['xml'])
                self.assertEqual(prefs[uc.THEME_KEY], ('string', 'zip://sskin'))
                self.assertEqual(prefs['PrevStoreCount'], ('long', 78))
                run.write_theme_setting(Path(tmp) / 'w', None)
                self.assertNotIn(uc.THEME_KEY, uc.parse_prefs(written['xml']))
            with mock.patch.object(run, 'read', return_value=PREFS_419), self.assertRaisesRegex(uc.SetupError, 'could not set'):
                run.write_theme_setting(Path(tmp) / 'w', 'zip://sskin')

    def test_an_empty_screen_dump_is_reported_as_such(self):
        run = uc.UpgradeRun(FakeDevice(), Path('old.apk'), Path('new.apk'), 'p', Path('.'))
        with mock.patch.object(uc.time, 'sleep'), self.assertRaisesRegex(uc.EmptyScreenDump, uc.SNAPSHOT_HELPER):
            run.screen()
        self.assertTrue(issubclass(uc.EmptyScreenDump, uc.SetupError))

    def test_the_snapshot_helper_is_stopped_before_the_scenarios(self):
        devices = []

        def make(serial):
            devices.append(FakeDevice())
            return devices[-1]
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(uc, 'Device', side_effect=make), \
                mock.patch.object(uc, 'universal_apk', side_effect=lambda source, work: source), \
                mock.patch.object(uc, 'apk_identity', side_effect=[dict(package='p', signers=['k'], version_code=1, version_name='a'),
                                                                   dict(package='p', signers=['k'], version_code=2, version_name='b')]), \
                mock.patch.object(uc.UpgradeRun, 'scenario', side_effect=uc.SetupError('stop here')), \
                contextlib.redirect_stderr(io.StringIO()):
            uc.main(['--serial', 's', '--old', 'o.apk', '--new', 'n.apk', '--out', f'{tmp}/out', '--scenario', 'legacy-theme'])
        self.assertIn(('shell', f'am force-stop {uc.SNAPSHOT_HELPER}'), devices[0].calls)

if __name__ == '__main__':
    unittest.main()
