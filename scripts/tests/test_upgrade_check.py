"""Verify the upgrade check's seeding and its verdict on before/after snapshots."""
import copy
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
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
        ])
        self.assertEqual(len(uc.app_events(lines, package)), 4)
        self.assertEqual(uc.app_events('I am_proc_start: [0,5136,10208,com.kimjisub.launchpad,x]', package), [])

    def test_screen_texts_read_node_text(self):
        xml = ('<hierarchy><node text="Upgrade Check A"><node text="" /><node text="Upgrade Check B" /></node>'
               '</hierarchy>')
        self.assertEqual(uc.screen_texts(xml), {'Upgrade Check A', 'Upgrade Check B'})
        self.assertEqual(uc.screen_texts('ERROR: could not get idle state.'), set())


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
