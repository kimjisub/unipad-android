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

# A real snapshot of the release Store screen on a 2400x1080 emulator, taken through the rental's UI
# entry and trimmed to the fields read: five full rows and one cut off at the bottom.
STORE_SCREEN = json.loads((SCRIPTS / "fixtures" / "release-store-check" / "store-list.json").read_text())

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
        screen = snapshot([store_row("Full", "Producer", top=179), store_row("Cut", "", top=980, height=100)])
        self.assertEqual([key for key, _ in check.store_rows(screen)], [("Full", "Producer")])

    def test_a_pack_without_producer_is_read_by_its_title_alone(self):
        screen = snapshot([store_row("B", "", top=179), store_row("C", "Producer", top=337)])
        self.assertEqual([key for key, _ in check.store_rows(screen)], [("B",), ("C", "Producer")])
        self.assertEqual(check.row_key("B", None), ("B",))
        self.assertEqual(check.row_key("C\t", "Producer"), ("C", "Producer"))

    def test_screens_merge_on_their_overlap_and_keep_repeated_rows(self):
        self.assertEqual(check.merge_screen([], [("A",), ("X",)]), [("A",), ("X",)])
        self.assertEqual(check.merge_screen([("A",), ("X",)], [("X",), ("X",), ("B",)]), [("X",), ("B",)])
        self.assertEqual(check.merge_screen([("A",), ("X",)], [("A",), ("X",)]), [])
        self.assertEqual(check.merge_screen([("A",)], [("B",)]), [("B",)])

    def test_reads_the_store_count_beside_its_label(self):
        root = check.parse_screen(STORE_SCREEN)
        self.assertEqual(check.value_after(root, check.STORE_COUNT_LABEL), "78")
        self.assertIsNone(check.value_after(root, "Not on screen"))

    def test_row_tap_lands_inside_the_row(self):
        _, row = check.store_rows(check.parse_screen(STORE_SCREEN))[0]
        self.assertEqual(row.center, (1721, 258))

    def test_a_snapshot_without_nodes_is_a_failure_not_an_empty_screen(self):
        for answer in ({"success": True, "data": {"message": "unchanged"}}, {"success": True}, None):
            with self.assertRaisesRegex(check.CheckFailed, "no node list"):
                check.parse_screen(answer)

    def test_finds_nodes_by_label_and_by_exposed_identifier(self):
        root = check.parse_screen({"data": {"nodes": [
            {"index": 0, "rect": {"x": 0, "y": 0, "width": 100, "height": 100}},
            {"index": 1, "parentIndex": 0, "identifier": "main_pack__Joanna", "hittable": True,
             "rect": {"x": 10, "y": 20, "width": 30, "height": 40}},
            {"index": 2, "parentIndex": 1, "label": "Joanna", "rect": {"x": 10, "y": 20, "width": 30, "height": 20}}]}})
        self.assertEqual(check.find(root, identifier="main_pack__Joanna").center, (25, 40))
        self.assertEqual(check.find(root, text="Joanna").bounds, (10, 20, 40, 40))
        self.assertIsNone(check.find(root, text="Tired"))

    def test_titles_compare_without_stray_whitespace(self):
        self.assertEqual(check.normalized("Alan Walker - Tired (We Rabbitz Remix)\t"),
                         "Alan Walker - Tired (We Rabbitz Remix)")
        self.assertEqual(check.normalized(None), "")


def rect(left, top, width, height):
    return {"x": left, "y": top, "width": width, "height": height}


def store_row(title, producer, top, height=158):
    """One Store row as snapshot entries: the clickable row, then its texts, as the app draws them
    (an empty producer draws no text)."""
    texts = [text for text in (title, producer) if text] + ["LED ●", "AUTOPLAY ●"]
    return [{"hittable": True, "rect": rect(1084, top, 1274, height)}] + [
        {"label": text, "rect": rect(1149, top, 229, min(63, height))} for text in texts]


def snapshot(rows, before=()):
    """A snapshot answer: `before` entries under the screen, then a list holding the rows' entries."""
    nodes = [{"index": 0, "rect": rect(0, 0, 2400, 1080)}]
    for entry in before:
        nodes.append(dict(entry, index=len(nodes), parentIndex=0))
    listing = len(nodes)
    nodes.append({"index": listing, "parentIndex": 0, "rect": rect(1042, 158, 1358, 922)})
    for row in rows:
        row_index = len(nodes)
        nodes.append(dict(row[0], index=row_index, parentIndex=listing))
        nodes.extend(dict(text, index=row_index + offset, parentIndex=row_index) for offset, text in enumerate(row[1:], 1))
    return check.parse_screen({"success": True, "data": {"nodes": nodes}})


def store_screen(count, rows):
    """A Store screen: the count beside its label and the rows (title, producer) in its list."""
    return snapshot([store_row(title, producer, top=179 + 158 * index) for index, (title, producer) in enumerate(rows)],
                    before=[{"label": check.STORE_COUNT_LABEL, "rect": rect(0, 0, 100, 50)},
                            {"label": str(count), "rect": rect(0, 50, 100, 50)}])


class ScreenDevice:
    """Serves one Store screen per swipe, staying on the last, as a list scrolled to its end does."""
    def __init__(self, screens, cycle=False):
        self.screens = screens
        self.cycle = cycle
        self.index = 0
        self.saved = []

    def screen(self, name=None):
        if name:
            self.saved.append(name)
        return self.screens[self.index]

    def wait(self, predicate, timeout, what, name):
        found = predicate(self.screen())
        if not found:
            raise check.CheckFailed(f"{what} did not appear")
        return found

    def capture(self, name):
        self.saved.append(name)

    def swipe(self, node, up=True):
        self.swipes = getattr(self, "swipes", 0) + 1
        self.index = (self.index + 1) % len(self.screens) if self.cycle else min(self.index + 1, len(self.screens) - 1)


class ListingTest(unittest.TestCase):
    PACKS = [{"title": "A", "producerName": "p"}, {"title": "X", "producerName": "q"},
             {"title": "X", "producerName": "q"}, {"title": "B"}]

    def listing(self, screens, device=None):
        store_check = check.StoreCheck(FakeDevice(), device or ScreenDevice(screens), check.RELEASE_PACKAGE, "unused")
        check.time.sleep, sleep = (lambda seconds: None), check.time.sleep
        try:
            store_check.check_listing(self.PACKS)
        finally:
            check.time.sleep = sleep
        return store_check.result["listing"]

    def test_two_packs_showing_the_same_title_and_producer_count_twice(self):
        listing = self.listing([store_screen(4, [("A", "p"), ("X", "q")]), store_screen(4, [("X", "q"), ("X", "q")]),
                                store_screen(4, [("X", "q"), ("B", "")])])
        self.assertEqual((listing["rows_seen"], listing["missing"], listing["extra"]), (4, [], []))

    def test_one_of_two_alike_packs_missing_from_the_screen_fails(self):
        with self.assertRaisesRegex(check.CheckFailed, "3 rows for 4 listed packs"):
            self.listing([store_screen(4, [("A", "p"), ("X", "q")]), store_screen(4, [("X", "q"), ("B", "")])])

    def test_a_list_that_keeps_alternating_stops_at_the_screen_limit_with_its_last_screen(self):
        device = ScreenDevice([store_screen(4, [("A", "p"), ("X", "q")]), store_screen(4, [("B", ""), ("Y", "r")])],
                              cycle=True)
        with self.assertRaisesRegex(check.CheckFailed, r"still showed new rows after 7 screens"):
            self.listing(None, device)
        self.assertEqual(device.swipes, 6)
        self.assertEqual(device.saved[-2:], ["store-list-unsettled", "store-list-unsettled"])


class PackChoiceTest(unittest.TestCase):
    def test_picks_the_smallest_served_file_and_passes_over_unknown_sizes(self):
        packs = [{"code": code, "URL": f"https://u/{code}", "title": code} for code in ("big", "gone", "small", "a-small")]
        sizes = {"https://u/big": 9, "https://u/gone": None, "https://u/small": 2, "https://u/a-small": 2}
        pack, size = check.smallest_pack(packs, sizes.get)
        self.assertEqual((pack["code"], size), ("a-small", 2))

    def test_sizes_only_packs_the_app_downloads_from_their_own_url(self):
        packs = [{"code": "legacy", "title": "L"}, {"code": "relative", "URL": "x.zip", "title": "R"},
                 {"code": "own", "URL": "https://x/own.zip", "title": "O"}]
        asked = []
        pack, _ = check.smallest_pack(packs, lambda url: asked.append(url) or 1)
        self.assertEqual((pack["code"], asked), ("own", ["https://x/own.zip"]))
        self.assertEqual(check.download_url(packs[0]), check.LEGACY_DOWNLOAD_URL + "?code=legacy")
        self.assertEqual(check.download_url({"code": "a b/c", "URL": "x.zip"}),
                         check.LEGACY_DOWNLOAD_URL + "?code=a%20b%2Fc")

    def test_passes_over_packs_without_code_or_shown_like_another(self):
        packs = [{"URL": "https://x/a.zip", "title": "A"},
                 {"code": "b1", "URL": "https://x/b1.zip", "title": "B", "producerName": "p"},
                 {"code": "b2", "URL": "https://x/b2.zip", "title": "B ", "producerName": "p"},
                 {"code": "c", "URL": "https://x/c.zip", "title": "C"}]
        self.assertEqual([pack["code"] for pack in check.downloadable(packs)], ["c"])
        with self.assertRaisesRegex(check.CheckFailed, "same title and producer"):
            check.StoreCheck(FakeDevice(), None, check.RELEASE_PACKAGE, "unused", "b1").choose_pack(packs)

    def test_no_sized_pack_fails(self):
        with self.assertRaises(check.CheckFailed):
            check.smallest_pack([{"code": "gone", "URL": "https://u/gone", "title": "G"}], lambda url: None)

    def test_store_list_keeps_every_object_entry_as_the_app_lists_it(self):
        with tempfile.TemporaryDirectory() as directory:
            listed = Path(directory) / "store.json"
            listed.write_text(json.dumps({"-1": {"code": "a", "URL": "https://x/a.zip", "title": "A"},
                                          "-2": {"title": "No code or URL"}, "-3": "not a pack"}))
            self.assertEqual([pack["title"] for pack in check.fetch_store_list(listed.as_uri())], ["A", "No code or URL"])
            for body in ("null", "{}", "<html>Service unavailable</html>"):
                listed.write_text(body)
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

    def root(self):
        pass

    def file_exists(self, path):
        return self.answers["test"] == "yes"

    def shell(self, *args, **kwargs):
        self.commands.append(args)
        return self.answers.get(args[0], "")

    def run(self, *args, **kwargs):
        return b""


class RefusalTest(unittest.TestCase):
    def store_check(self, device, pack=None):
        return check.StoreCheck(device, None, check.RELEASE_PACKAGE, "unused", pack)

    def test_refuses_before_api_33(self):
        with self.assertRaisesRegex(check.CheckFailed, "API 32"):
            self.store_check(FakeDevice(sdk="32")).check_installed()

    def test_refuses_an_unreadable_api_level(self):
        with self.assertRaisesRegex(check.CheckFailed, "not an API level"):
            self.store_check(FakeDevice(sdk="")).check_installed()

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


class RecordedScreen:
    """A Screen whose UI commands are recorded instead of sent."""
    def __init__(self):
        self.commands = []

    def close(self):
        self.commands.append(("close",))


class UiEntryTest(unittest.TestCase):
    """Screen work goes only to the rental's UI entry, for the rented device and generation."""
    def screen(self, answers):
        self.calls = []

        def run(command, **kwargs):
            self.calls.append(command)
            returncode, stdout = answers.pop(0) if answers else (0, json.dumps({"success": True, "data": {}}))
            return check.subprocess.CompletedProcess(command, returncode, stdout.encode(), b"refused by the rental")
        return check.Screen(Path("/tools/devices.py"), "emulator-5560", "gen1", Path("/out"), run=run)

    def test_every_screen_action_is_a_ui_command_on_the_rented_device(self):
        screen = self.screen([(0, json.dumps(STORE_SCREEN))])
        rows = check.store_rows(screen.screen())
        screen.launch(check.RELEASE_PACKAGE)
        screen.tap(rows[0][1])
        screen.swipe(check.store_list(check.parse_screen(STORE_SCREEN)))
        screen.back()
        screen.capture("library")
        screen.close()
        prefix = [check.sys.executable, "/tools/devices.py", "ui", "emulator-5560", "--generation", "gen1", "--"]
        self.assertTrue(all(call[:len(prefix)] == prefix for call in self.calls))
        self.assertEqual([call[len(prefix):] for call in self.calls], [
            ["snapshot"], ["open", check.RELEASE_PACKAGE], ["press", "1721", "258"],
            ["gesture", "pan", "1721", "927", "0", "-616", "600"], ["back", "--system"],
            ["screenshot", "/out/library.png"], ["close"]])

    def test_a_refused_or_unsuccessful_ui_command_fails_the_check(self):
        for answer in ((1, ""), (0, json.dumps({"success": False, "error": {"code": "DEVICE_IN_USE"}})), (0, "not json")):
            screen = self.screen([answer])
            with self.assertRaisesRegex(check.CheckFailed, "ui press 1 2 failed"):
                screen.ui("press", 1, 2)

    def test_closing_the_session_never_raises(self):
        screen = self.screen([(1, "")])
        screen.close()
        self.assertEqual(self.calls[0][-1], "close")


class VerdictTest(unittest.TestCase):
    def verdict(self, device, list_body, pack=None):
        with tempfile.TemporaryDirectory() as directory:
            listed = Path(directory) / "store.json"
            listed.write_text(list_body)
            store_check = check.StoreCheck(device, RecordedScreen(), check.RELEASE_PACKAGE, listed.as_uri(), pack)
            self.assertEqual(check.run_and_record(store_check, Path(directory)), 1)
            return json.loads((Path(directory) / "store-check.json").read_text())

    def test_a_store_list_that_is_not_json_is_a_failed_verdict(self):
        device = FakeDevice()
        result = self.verdict(device, "<html>Service unavailable</html>")
        self.assertEqual(result["verdict"], "failed")
        self.assertRegex(result["error"], "^CheckFailed: .* is not JSON")
        self.assertFalse(any(args[0] in ("setprop", "cmd", "input", "am") for args in device.commands))

    def test_an_unexpected_error_is_a_failed_verdict_too(self):
        # The copied database is empty, so reading the record raises sqlite3.OperationalError.
        result = self.verdict(FakeDevice(database=True), json.dumps({"-1": {"code": "a", "title": "A"}}), pack="a")
        self.assertEqual(result["verdict"], "failed")
        self.assertRegex(result["error"], "^OperationalError: no such table")


class RestoreTest(unittest.TestCase):
    def test_a_failed_language_change_still_restores_language_and_analytics(self):
        class FailingDevice(FakeDevice):
            def shell(self, *args, **kwargs):
                if args[:2] == ("cmd", "locale") and "en-US" in args:
                    self.commands.append(args)
                    raise check.CheckFailed("set-app-locales failed")
                if args[0] == "getprop" and args[1] == "debug.firebase.analytics.app":
                    self.commands.append(args)
                    return ""
                if args[:2] == ("cmd", "locale"):
                    self.commands.append(args)
                    return "Locales for p for user 0 are [ko-KR]"
                return super().shell(*args, **kwargs)

        device = FailingDevice()
        screen = RecordedScreen()
        store_check = check.StoreCheck(device, screen, check.RELEASE_PACKAGE, "unused", "a")
        store_check.choose_pack = lambda packs: packs[0]
        fetch, check.fetch_store_list = check.fetch_store_list, lambda url: [{"code": "a", "title": "A"}]
        try:
            with self.assertRaisesRegex(check.CheckFailed, "set-app-locales failed"):
                store_check.run()
        finally:
            check.fetch_store_list = fetch
        self.assertIn(("cmd", "locale", "set-app-locales", check.RELEASE_PACKAGE, "--locales", "ko-KR"), device.commands)
        self.assertIn(("setprop", "debug.firebase.analytics.app", ".none."), device.commands)
        self.assertEqual(screen.commands, [("close",)])


if __name__ == "__main__":
    unittest.main()
