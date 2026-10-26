"""Check the release Store check's screen reading, pack choice and refusals without a device."""
import importlib.util
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("release_store_check", SCRIPTS / "release_store_check.py")
check = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(check)

# A real dump of the release Store screen on a 2400x1080 emulator: five full rows and one cut off at the bottom.
STORE_SCREEN = (SCRIPTS / "fixtures" / "release-store-check" / "store-list.xml").read_text()

PERMISSION_UNANSWERED = """
      android.permission.POST_NOTIFICATIONS: granted=false, flags=[ USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
"""
PERMISSION_DENIED = """
      android.permission.POST_NOTIFICATIONS: granted=false, flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED]
"""
PACKAGE = """
    versionCode=120 minSdk=24 targetSdk=37
    versionName=4.2.1
    flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA LARGE_HEAP ]
"""


class ScreenTest(unittest.TestCase):
    def test_reads_only_rows_shown_in_full(self):
        rows = check.store_rows(check.parse_screen(STORE_SCREEN))
        self.assertEqual([key for key, _ in rows], [
            ("OMFG - Hello", "W.D Zyro, 김지섭"),
            ("Martin Garrix & MOTi - Virus", "Voltapix, 김지섭, 이은학, jehongjeon"),
            ("Sia - Move Your Body (Alan Walker Remix)", "DEMVIL, Nythyc, TROPSEE"),
            ("Porter Robinson & Madeon - Shelter", "Clement Show"),
            ("Avicii - Wake Me Up", "Gabrielleao Upf's & LucasG2"),
        ])

    def test_a_row_cut_off_at_the_edge_is_not_read_with_its_indicator_as_producer(self):
        def row(top, bottom, *texts):
            children = "".join(f'<node text="{text}" clickable="false" bounds="[1149,{top}][1378,{bottom}]" />'
                               for text in texts)
            return f'<node text="" clickable="true" bounds="[1084,{top}][2358,{bottom}]">{children}</node>'
        screen = check.parse_screen(
            '<hierarchy><node text="" scrollable="true" bounds="[1042,158][2400,1080]">'
            + row(179, 337, "Full", "Producer", "LED ●", "AUTOPLAY ●")
            + row(980, 1080, "Cut", "LED ●", "AUTOPLAY ●")
            + "</node></hierarchy>")
        self.assertEqual([key for key, _ in check.store_rows(screen)], [("Full", "Producer")])

    def test_reads_the_store_count_beside_its_label(self):
        root = check.parse_screen(STORE_SCREEN)
        self.assertEqual(check.value_after(root, check.STORE_COUNT_LABEL), "78")
        self.assertIsNone(check.value_after(root, "Not on screen"))

    def test_row_tap_lands_inside_the_row(self):
        _, row = check.store_rows(check.parse_screen(STORE_SCREEN))[0]
        self.assertEqual(row.center, (1721, 258))

    def test_titles_compare_without_stray_whitespace(self):
        self.assertEqual(check.normalized("Alan Walker - Tired (We Rabbitz Remix)\t"),
                         "Alan Walker - Tired (We Rabbitz Remix)")
        self.assertEqual(check.normalized(None), "")


class PackChoiceTest(unittest.TestCase):
    def test_picks_the_smallest_served_file_and_passes_over_unknown_sizes(self):
        packs = [{"code": "big", "URL": "u/big"}, {"code": "gone", "URL": "u/gone"},
                 {"code": "small", "URL": "u/small"}, {"code": "a-small", "URL": "u/a-small"}]
        sizes = {"u/big": 9, "u/gone": None, "u/small": 2, "u/a-small": 2}
        pack, size = check.smallest_pack(packs, sizes.get)
        self.assertEqual((pack["code"], size), ("a-small", 2))

    def test_no_sized_pack_fails(self):
        with self.assertRaises(check.CheckFailed):
            check.smallest_pack([{"code": "gone", "URL": "u"}], lambda url: None)

    def test_store_list_entries_need_code_url_and_title(self):
        with tempfile.TemporaryDirectory() as directory:
            listed = Path(directory) / "store.json"
            listed.write_text(json.dumps({"-1": {"code": "a", "URL": "https://x/a.zip", "title": "A"}}))
            self.assertEqual([pack["code"] for pack in check.fetch_store_list(listed.as_uri())], ["a"])
            listed.write_text(json.dumps({"-1": {"code": "a", "URL": "", "title": "A"}}))
            with self.assertRaises(check.CheckFailed):
                check.fetch_store_list(listed.as_uri())
            listed.write_text("null")
            with self.assertRaises(check.CheckFailed):
                check.fetch_store_list(listed.as_uri())


class DeviceStateTest(unittest.TestCase):
    def test_notification_permission_answer(self):
        self.assertEqual(check.notification_permission(PERMISSION_UNANSWERED), (False, False))
        self.assertEqual(check.notification_permission(PERMISSION_DENIED), (False, True))
        self.assertEqual(check.notification_permission(""), (False, False))

    def test_app_locales(self):
        self.assertEqual(check.app_locales("Locales for p for user 0 are []"), "")
        self.assertEqual(check.app_locales("Locales for p for user 0 are [ko-KR]"), "ko-KR")

    def test_pack_record_reads_the_room_row(self):
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "UniPad.db"
            connection = sqlite3.connect(database)
            connection.execute("CREATE TABLE Unipack (id TEXT PRIMARY KEY, bookmark INTEGER, openCount INTEGER, "
                               "lastOpenedAt INTEGER, createdAt INTEGER)")
            connection.execute("INSERT INTO Unipack VALUES ('_Joanna', 0, 0, NULL, 1)")
            connection.commit()
            connection.close()
            self.assertEqual(check.pack_record(database, "_Joanna"), (0, 0, None))
            self.assertIsNone(check.pack_record(database, "_Tired"))


class FakeDevice:
    def __init__(self, sdk="35", package=PACKAGE, folders="", database=False):
        self.answers = {"getprop": sdk, "dumpsys": package, "find": folders, "test": "yes" if database else ""}
        self.commands = []

    def shell(self, *args, **kwargs):
        self.commands.append(args)
        return self.answers.get(args[0], "")

    def run(self, *args, **kwargs):
        return b""


class RefusalTest(unittest.TestCase):
    def store_check(self, device, pack=None):
        return check.StoreCheck(device, check.RELEASE_PACKAGE, "unused", pack)

    def test_refuses_before_api_33(self):
        with self.assertRaisesRegex(check.CheckFailed, "API 32"):
            self.store_check(FakeDevice(sdk="32")).check_installed()

    def test_refuses_a_debuggable_build(self):
        package = PACKAGE.replace("HAS_CODE", "HAS_CODE DEBUGGABLE")
        with self.assertRaisesRegex(check.CheckFailed, "debuggable"):
            self.store_check(FakeDevice(package=package)).check_installed()

    def test_refuses_a_missing_app(self):
        with self.assertRaisesRegex(check.CheckFailed, "not installed"):
            self.store_check(FakeDevice(package="Unable to find package")).check_installed()

    def test_reads_version_of_a_release_build(self):
        store_check = self.store_check(FakeDevice())
        store_check.check_installed()
        self.assertEqual((store_check.result["version_name"], store_check.result["version_code"]), ("4.2.1", 120))

    def test_refuses_a_pack_already_in_the_library_before_changing_the_device(self):
        device = FakeDevice(folders="/storage/emulated/0/Android/data/com.kimjisub.launchpad/files/UniPack/_Joanna")
        store_check = self.store_check(device, pack="_Joanna")
        packs = [{"code": "_Joanna", "URL": "file:///nonexistent/_Joanna.zip", "title": "Joanna"}]
        with self.assertRaisesRegex(check.CheckFailed, "already in this app"):
            store_check.choose_pack(packs)
        self.assertFalse(any(args[0] in ("setprop", "cmd", "input", "am") for args in device.commands))

    def test_unknown_pack_code_fails(self):
        with self.assertRaisesRegex(check.CheckFailed, "not in the Store list"):
            self.store_check(FakeDevice(), pack="missing").choose_pack(
                [{"code": "_Joanna", "URL": "file:///nonexistent", "title": "Joanna"}])


if __name__ == "__main__":
    unittest.main()
