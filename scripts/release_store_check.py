#!/usr/bin/env python3
"""Check an installed release build against the real Store, the way a user reaches it.

The instrumented Store tests replace the catalogue with an offline fixture. This check drives
the installed app (by default the release package) on one rented, rooted emulator from the
outside, with the device online:

1. the Store screen lists every pack the Store database lists (count and title/producer pairs);
2. one pack — by default the smallest file in the list — downloads, and on a first download
   on Android 13+ the notification permission dialog appears;
3. the pack is then on disk, in the library, and its record starts without history or bookmark.

Every run downloads one pack from the production file host, which adds one to that pack's
public download count, so run it once per release candidate. Packs are sized with HEAD
requests, which download nothing. Analytics from the app are marked as debug events
(`debug.firebase.analytics.app`) during the run, and the app language is set to English.
Afterwards the app language is set back, and the analytics property is set back to its value or,
when it had none, to `.none.` (Firebase's off value; a system property cannot be removed).
The downloaded pack and the permission answer stay. Run it on a fresh installation: the
library is not scrolled, so the new pack must be on its first screen.

Everything on the screen — launching the app, reading the screen, presses, drags, Back and
screenshots — goes through the device rental's UI entry (`devices.py ui DEVICE --generation
TOKEN -- ...`, which drives agent-device in the rental's own session), so only the rented device
and its session are used. The session is closed when the check ends, however it ends. adb is
used only to inspect and prepare the app: root, API level, package flags and notification
permission state, the app language and analytics property (set and restored), stopping the app
before launch, the pack folders, and a copy of the app database.

Results, screenshots and screen snapshots go to `--out`; `store-check.json` holds the verdict.
"""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
import json
from pathlib import Path
import re
import shlex
import sqlite3
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request

# The same Realtime Database node the app subscribes to (`FirebaseStoreCatalog`), read over REST.
STORE_LIST_URL = "https://unipad-e41ab.firebaseio.com/store.json"
# Where the app downloads an entry without a URL of its own (`FBStoreActivity.DOWNLOAD_BASE_URL`).
LEGACY_DOWNLOAD_URL = "https://us-central1-unipad-e41ab.cloudfunctions.net/downloadUniPackLegacy"
RELEASE_PACKAGE = "com.kimjisub.launchpad"
DATABASE = "UniPad.db"
PERMISSION_ALLOW = "com.android.permissioncontroller:id/permission_allow_button"

# English app strings (app/src/main/res/values/strings.xml); the check sets the app language to English.
STORE_BUTTON_DESCRIPTION = "Store"
DOWNLOAD_NEW_PACKS = "Download new packs"
STORE_COUNT_LABEL = "Store UniPacks"
DOWNLOAD = "Download"
DOWNLOADED = "Downloaded"
FAILED = "Failed"
# Each row ends with its LED and AutoPlay indicators (`option_indicator_format`, "%s ●").
INDICATOR_SUFFIX = " ●"

LIST_TIMEOUT = 30
DOWNLOAD_TIMEOUT = 180
DIALOG_TIMEOUT = 10
SCREEN_TIMEOUT = 20
UI_COMMAND_TIMEOUT = 90
# Drags past the last new row before the list counts as read to its end.
UNCHANGED_SCREENS = 2


class CheckFailed(Exception):
    pass


def normalized(text):
    """Titles in the database carry stray tabs and doubled spaces that the screen keeps or drops."""
    return " ".join((text or "").split())


@dataclass
class Node:
    text: str
    identifier: str
    clickable: bool
    bounds: tuple
    children: list = field(default_factory=list)

    @property
    def center(self):
        left, top, right, bottom = self.bounds
        return (left + right) // 2, (top + bottom) // 2

    @property
    def height(self):
        return self.bounds[3] - self.bounds[1]

    def walk(self):
        yield self
        for child in self.children:
            yield from child.walk()

    def texts(self):
        return [node.text for node in self.walk() if node.text]


def parse_screen(snapshot):
    """The root of an agent-device snapshot (`{"data": {"nodes": [...]}}`) as a Node tree.

    Snapshot nodes come flat in drawing order and name their parent by index; a node's label is
    its text or content description, its identifier its resource ID or exposed test tag, and its
    rect the part visible on screen."""
    nodes = (snapshot.get("data") or {}).get("nodes") if isinstance(snapshot, dict) else None
    if not isinstance(nodes, list):
        raise CheckFailed("The screen snapshot has no node list")
    root = Node("", "", False, (0, 0, 0, 0))
    built = {}
    for entry in nodes:
        rect = entry.get("rect") or {}
        left, top = rect.get("x", 0), rect.get("y", 0)
        built[entry["index"]] = Node(entry.get("label") or "", entry.get("identifier") or "", bool(entry.get("hittable")),
                                     (left, top, left + rect.get("width", 0), top + rect.get("height", 0)))
    for entry in nodes:
        built.get(entry.get("parentIndex"), root).children.append(built[entry["index"]])
    return root


def find(root, text=None, identifier=None):
    for node in root.walk():
        if (text is None or node.text == text) and (identifier is None or node.identifier == identifier):
            return node
    return None


def value_after(root, label):
    """The text drawn right after `label` (the Store panel's counts sit beside their labels)."""
    nodes = [node for node in root.walk() if node.text]
    for index, node in enumerate(nodes[:-1]):
        if node.text == label:
            return nodes[index + 1].text
    return None


def store_list(root):
    """The Store's pack list: the node whose children are clickable rows with text."""
    for node in root.walk():
        if any(child.clickable and child.texts() for child in node.children):
            return node
    return None


def row_key(title, producer):
    """What a Store row shows of a pack: its title and producer, leaving out an empty one, which draws no text."""
    return tuple(text for text in (normalized(title), normalized(producer)) if text)


def row_texts(row):
    """A row's texts without the LED and AutoPlay indicators that end it."""
    texts = row.texts()
    for _ in range(2):
        if texts and texts[-1].endswith(INDICATOR_SUFFIX):
            texts = texts[:-1]
    return texts


def store_rows(root):
    """(row_key, row node) of each Store row fully on screen, top to bottom.

    A snapshot gives each node's visible bounds, so a row cut off at the list's edge is shorter
    than the others and may lack its producer line; only rows of full height are read."""
    listing = store_list(root)
    if listing is None:
        return []
    candidates = [row for row in listing.children if row.clickable and row.texts()]
    full = max((row.height for row in candidates), default=0)
    return [(tuple(normalized(text) for text in row_texts(row)), row)
            for row in candidates if row.height >= full - 2]


def merge_screen(seen, keys):
    """Append a screen's rows to the rows seen so far, past the longest run both share.

    Each swipe moves the list by less than its height, so consecutive screens overlap;
    aligning on that overlap counts two rows that show the same pack twice."""
    for overlap in range(min(len(seen), len(keys)), 0, -1):
        if seen[-overlap:] == keys[:overlap]:
            return keys[overlap:]
    return keys


def fetch_store_list(url, timeout=30):
    """The entries the Store screen lists: every object under the node, as the app adds every child it can read."""
    with urllib.request.urlopen(url, timeout=timeout) as response:
        try:
            listed = json.load(response)
        except ValueError as error:
            raise CheckFailed(f"Store list at {url} is not JSON: {error}") from error
    if not isinstance(listed, dict) or not listed:
        raise CheckFailed(f"Store list at {url} is empty or not an object")
    return [pack for pack in listed.values() if isinstance(pack, dict)]


def text_field(pack, name):
    value = pack.get(name)
    return value if isinstance(value, str) else None


def download_url(pack):
    """The address the app downloads the pack from: its own URL, else the legacy download function by code."""
    url = text_field(pack, "URL")
    if url and url.startswith("http"):
        return url
    return f"{LEGACY_DOWNLOAD_URL}?code={urllib.parse.quote(pack['code'], safe='')}"


def head_size(url, timeout=15):
    """The file's Content-Length from a HEAD request (nothing is downloaded or counted); None when unknown."""
    request = urllib.request.Request(url, method="HEAD")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            length = response.headers.get("Content-Length")
            return int(length) if response.status == 200 and length and length.isdigit() else None
    except (OSError, ValueError):
        return None


def downloadable(packs):
    """Packs the check can download and find again: the app needs a code to download, and the
    row is found by what it shows, so that must be unique in the list."""
    shown = Counter(row_key(pack.get("title"), pack.get("producerName")) for pack in packs)
    return [pack for pack in packs
            if text_field(pack, "code") and shown[row_key(pack.get("title"), pack.get("producerName"))] == 1]


def smallest_pack(packs, size_of):
    """The pack with the smallest served file, with its size. Only packs with their own file URL
    are sized (a HEAD request to the legacy function could count as a download); packs whose
    size is unknown are passed over."""
    packs = [pack for pack in downloadable(packs) if download_url(pack) == text_field(pack, "URL")]
    with ThreadPoolExecutor(max_workers=8) as pool:
        sizes = list(pool.map(lambda pack: size_of(pack["URL"]), packs))
    sized = [(size, pack["code"], pack) for size, pack in zip(sizes, packs) if size]
    if not sized:
        raise CheckFailed("No Store pack answered a HEAD request with its size")
    size, _, pack = min(sized, key=lambda entry: entry[:2])
    return pack, size


def notification_permission(dumpsys_package):
    """(granted, answered) of POST_NOTIFICATIONS from `dumpsys package`; answered means the user chose."""
    match = re.search(r"android\.permission\.POST_NOTIFICATIONS: granted=(\w+), flags=\[([^\]]*)\]", dumpsys_package)
    if not match:
        return False, False
    flags = match.group(2)
    return match.group(1) == "true", "USER_SET" in flags or "USER_FIXED" in flags


def app_locales(output):
    """The locales list from `cmd locale get-app-locales`, e.g. 'Locales for pkg for user 0 are [en-US]'."""
    match = re.search(r"are \[(.*)\]", output)
    return match.group(1).strip() if match else ""


def pack_record(database_files, code):
    """(bookmark, openCount, lastOpenedAt) of the pack's Unipack row in copies of the app database, or None."""
    connection = sqlite3.connect(database_files)
    try:
        return connection.execute("SELECT bookmark, openCount, lastOpenedAt FROM Unipack WHERE id = ?", (code,)).fetchone()
    finally:
        connection.close()


class Adb:
    """adb on the rented device, for inspecting and preparing the app; never for the screen."""
    def __init__(self, adb, serial):
        self.command = [adb, "-s", serial]

    def run(self, *args, timeout=60, check=True):
        result = subprocess.run(self.command + list(args), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                timeout=timeout)
        if check and result.returncode != 0:
            raise CheckFailed(f"adb {' '.join(args)} failed: {result.stderr.decode(errors='replace').strip()}")
        return result.stdout

    def shell(self, *args, timeout=60, check=True):
        return self.run("shell", *args, timeout=timeout, check=check).decode(errors="replace").strip()

    def root(self):
        self.run("root", timeout=30)
        self.run("wait-for-device", timeout=60)
        if self.shell("id", "-u") != "0":
            raise CheckFailed("adb root is not available on this device; use an emulator image that allows it")

    def file_exists(self, path):
        return self.shell("test", "-f", path, "&&", "echo", "yes", check=False) == "yes"


class Screen:
    """The rented device's screen, through the rental's UI entry (`devices.py ui`)."""
    def __init__(self, devices_tool, serial, generation, out, run=subprocess.run):
        self.command = [sys.executable, str(devices_tool), "ui", serial, "--generation", generation, "--"]
        self.out = out
        self._run = run

    def ui(self, *args, check=True):
        result = self._run(self.command + [str(arg) for arg in args], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                           timeout=UI_COMMAND_TIMEOUT)
        output = result.stdout.decode(errors="replace")
        try:
            answer = json.loads(output)
        except ValueError:
            answer = None
        if check and (result.returncode != 0 or not isinstance(answer, dict) or not answer.get("success")):
            detail = result.stderr.decode(errors="replace").strip() or output.strip()
            raise CheckFailed(f"ui {' '.join(str(arg) for arg in args)} failed: {detail[-600:]}")
        return answer

    def launch(self, package):
        self.ui("open", package)

    def close(self):
        self.ui("close", check=False)

    def screen(self, name=None):
        snapshot = self.ui("snapshot")
        if name:
            (self.out / f"{name}.json").write_text(json.dumps(snapshot, indent=1, ensure_ascii=False) + "\n")
        return parse_screen(snapshot)

    def capture(self, name):
        self.ui("screenshot", self.out / f"{name}.png")

    def tap(self, node):
        self.ui("press", *node.center)

    def swipe(self, node, up=True):
        """Drag across two thirds of the node over 600 ms: up shows what lies below, down what lies above."""
        x, (top, bottom) = node.center[0], (node.bounds[1], node.bounds[3])
        near_top, near_bottom = top + (bottom - top) // 6, bottom - (bottom - top) // 6
        start, end = (near_bottom, near_top) if up else (near_top, near_bottom)
        self.ui("gesture", "pan", x, start, 0, end - start, 600)

    def back(self):
        self.ui("back", "--system")

    def wait(self, predicate, timeout, what, name):
        deadline = time.monotonic() + timeout
        while True:
            root = self.screen()
            found = predicate(root)
            if found:
                return found
            if time.monotonic() >= deadline:
                self.screen(name)
                self.capture(name)
                raise CheckFailed(f"{what} did not appear within {timeout} s (see {name}.png)")
            time.sleep(0.5)


class StoreCheck:
    def __init__(self, adb, screen, package, list_url, pack_code=None):
        self.adb = adb
        self.screen = screen
        self.package = package
        self.list_url = list_url
        self.pack_code = pack_code
        self.result = {"package": package, "list_url": list_url, "steps": []}

    def step(self, name, detail):
        self.result["steps"].append({"step": name, "detail": detail})
        print(f"{name}: {detail}", flush=True)

    def app_data(self, *path):
        return "/".join((f"/data/data/{self.package}",) + path)

    def pack_folders(self, code):
        """Folders named after the pack in every place the app keeps packs (internal and external storage)."""
        found = self.adb.shell("find", self.app_data("files"), f"/storage/emulated/0/Android/data/{self.package}/files",
                                  "-maxdepth", "2", "-type", "d", "-name", code, check=False)
        return [line for line in found.splitlines() if line.strip() and "No such file" not in line]

    def read_record(self, code):
        with tempfile.TemporaryDirectory() as directory:
            copy = Path(directory) / DATABASE
            for suffix in ("", "-wal", "-shm"):
                source = self.app_data("databases", DATABASE + suffix)
                if self.adb.file_exists(source):
                    Path(str(copy) + suffix).write_bytes(self.adb.run("exec-out", "cat", source))
            if not copy.exists():
                return None
            return pack_record(copy, code)

    def check_installed(self):
        sdk = self.adb.shell("getprop", "ro.build.version.sdk")
        if not sdk.isdigit():
            raise CheckFailed(f"ro.build.version.sdk is {sdk!r}, not an API level")
        sdk = int(sdk)
        if sdk < 33:
            raise CheckFailed(f"API {sdk}: this check needs API 33+ (per-app language, notification permission)")
        package = self.adb.shell("dumpsys", "package", self.package)
        version = re.search(r"versionCode=(\d+).*?\n\s*versionName=(\S+)", package, re.S)
        if not version:
            raise CheckFailed(f"{self.package} is not installed")
        flags = re.search(r"pkgFlags=\[([^\]]*)\]", package) or re.search(r"\bflags=\[([^\]]*)\]", package)
        if flags and "DEBUGGABLE" in flags.group(1):
            raise CheckFailed(f"{self.package} is a debuggable build; this check is for release builds")
        self.result.update(api=sdk, version_name=version.group(2), version_code=int(version.group(1)))
        self.step("app", f"{self.package} {version.group(2)} ({version.group(1)}), release build, API {sdk}")
        return package

    def choose_pack(self, packs):
        if self.pack_code:
            pack = next((pack for pack in packs if text_field(pack, "code") == self.pack_code), None)
            if pack is None:
                raise CheckFailed(f"Pack {self.pack_code} is not in the Store list")
            if pack not in downloadable(packs):
                raise CheckFailed(f"Pack {self.pack_code} shows the same title and producer as another pack; choose another")
            url = download_url(pack)
            size = head_size(url) if url == text_field(pack, "URL") else None
        else:
            pack, size = smallest_pack(packs, head_size)
        title = normalized(pack.get("title"))
        self.result["pack"] = {"code": pack["code"], "title": title, "url": download_url(pack), "size": size,
                               "download_count_before": pack.get("downloadCount")}
        self.step("pack", f"{pack['code']} \"{title}\" {size} bytes")
        if self.pack_folders(pack["code"]) or self.read_record(pack["code"]) is not None:
            raise CheckFailed(f"{pack['code']} is already in this app's library or database; "
                              "use a fresh installation of the candidate")
        return pack

    def open_store(self):
        self.adb.shell("am", "force-stop", self.package)
        self.screen.launch(self.package)
        entry = self.screen.wait(lambda root: find(root, text=STORE_BUTTON_DESCRIPTION)
                                 or find(root, text=DOWNLOAD_NEW_PACKS), SCREEN_TIMEOUT, "The main screen's Store entry", "main-screen")
        self.screen.capture("main-screen")
        self.screen.tap(entry)

    def check_listing(self, packs):
        expected = len(packs)
        self.screen.wait(lambda root: value_after(root, STORE_COUNT_LABEL) == str(expected), LIST_TIMEOUT,
                         f"Store count {expected}", "store-count")
        self.screen.capture("store-list")
        wanted = Counter(row_key(pack.get("title"), pack.get("producerName")) for pack in packs)
        seen, unchanged = [], 0
        root = self.screen.screen("store-list")
        listing = store_list(root)
        if listing is None:
            raise CheckFailed("The Store screen has no pack list (see store-list.json)")
        # Every drag before the end shows at least one new row, so a list of N packs is read
        # within N screens plus the unchanged ones; a list still changing past that never settles.
        limit = expected + UNCHANGED_SCREENS + 1
        for screens in range(1, limit + 1):
            new = merge_screen(seen, [key for key, _ in store_rows(root)])
            seen.extend(new)
            unchanged = 0 if new else unchanged + 1
            if unchanged >= UNCHANGED_SCREENS:
                break
            if screens < limit:
                self.screen.swipe(listing)
                time.sleep(0.4)
                root = self.screen.screen()
        else:
            self.screen.screen("store-list-unsettled")
            self.screen.capture("store-list-unsettled")
            self.result["listing"] = {"count": expected, "rows_seen": len(seen), "settled": False}
            raise CheckFailed(f"The Store list still showed new rows after {limit} screens "
                              f"({len(seen)} rows read for {expected} listed packs; see store-list-unsettled.png)")
        shown = Counter(seen)
        missing, extra = sorted((wanted - shown).elements()), sorted((shown - wanted).elements())
        self.result["listing"] = {"count": expected, "rows_seen": len(seen), "missing": missing, "extra": extra}
        if missing or extra or len(seen) != expected:
            raise CheckFailed(f"Store screen shows {len(seen)} rows for {expected} listed packs: "
                              f"missing {missing}, extra {extra}")
        self.step("list", f"{expected} packs counted and all {len(seen)} rows match the Store list")

    def find_row(self, pack):
        key = row_key(pack.get("title"), pack.get("producerName"))
        for _ in range(60):
            root = self.screen.screen()
            row = dict(store_rows(root)).get(key)
            if row:
                return row
            listing = store_list(root)
            if listing is None:
                self.screen.screen("store-find-row")
                raise CheckFailed(f"The Store screen lost its pack list while looking for {pack['code']} "
                                  "(see store-find-row.json)")
            self.screen.swipe(listing, up=False)
            time.sleep(0.4)
        raise CheckFailed(f"Row of {pack['code']} not found while scrolling back up the Store list")

    def download(self, pack, permission_before):
        self.screen.tap(self.find_row(pack))
        button = self.screen.wait(lambda root: find(root, text=DOWNLOAD), SCREEN_TIMEOUT, "The Download button", "store-detail")
        self.screen.capture("store-detail")
        started = time.monotonic()
        self.screen.tap(button)
        expect_dialog = not permission_before[0] and not permission_before[1]
        dialog = None
        if expect_dialog:
            dialog = self.screen.wait(lambda root: find(root, identifier=PERMISSION_ALLOW), DIALOG_TIMEOUT,
                                      "The notification permission dialog", "notification-permission")
            self.screen.capture("notification-permission")
            self.screen.tap(dialog)
        state = self.screen.wait(lambda root: (find(root, text=DOWNLOADED) and DOWNLOADED) or (find(root, text=FAILED) and FAILED),
                                 DOWNLOAD_TIMEOUT, "The download result", "download-result")
        seconds = round(time.monotonic() - started, 1)
        self.screen.capture("store-downloaded")
        if state == FAILED:
            raise CheckFailed(f"The app showed \"{FAILED}\" for {pack['code']} after {seconds} s")
        self.screen.screen("store-downloaded")
        self.result["download"] = {"seconds": seconds, "notification_dialog": bool(dialog)}
        self.step("download", f"{DOWNLOADED} after {seconds} s; notification permission dialog "
                              f"{'shown and allowed' if dialog else 'not asked (already answered)'}")

    def check_library(self, pack):
        code = pack["code"]
        folders = self.pack_folders(code)
        if len(folders) != 1 or not self.adb.file_exists(f"{folders[0]}/info"):
            raise CheckFailed(f"Expected one folder of {code} with an info file, found {folders}")
        for _ in range(2):
            self.screen.back()
            time.sleep(1)
        item = self.screen.wait(lambda root: find(root, identifier=f"main_pack_{code}"), SCREEN_TIMEOUT,
                                f"{code} in the library", "library")
        self.screen.tap(item)
        time.sleep(1.5)
        self.screen.capture("library")
        record = self.read_record(code)
        self.result["library"] = {"folder": folders[0], "record": record}
        if record is None:
            raise CheckFailed(f"{code} has no Unipack record after it was shown in the library")
        bookmark, open_count, last_opened = record
        if bookmark or open_count or last_opened is not None:
            raise CheckFailed(f"{code} record does not start fresh: bookmark={bookmark} openCount={open_count} "
                              f"lastOpenedAt={last_opened}")
        self.step("library", f"{folders[0]} with info; listed; record bookmark=0 openCount=0 lastOpenedAt=none")

    def run(self):
        self.adb.root()
        dumpsys = self.check_installed()
        permission_before = notification_permission(dumpsys)
        packs = fetch_store_list(self.list_url)
        pack = self.choose_pack(packs)
        locales = app_locales(self.adb.shell("cmd", "locale", "get-app-locales", self.package))
        analytics = self.adb.shell("getprop", "debug.firebase.analytics.app")
        try:
            self.adb.shell("cmd", "locale", "set-app-locales", self.package, "--locales", "en-US")
            self.adb.shell("setprop", "debug.firebase.analytics.app", self.package)
            self.open_store()
            self.check_listing(packs)
            self.download(pack, permission_before)
            self.check_library(pack)
        finally:
            self.screen.close()
            self.adb.shell("cmd", "locale", "set-app-locales", self.package, "--locales", shlex.quote(locales), check=False)
            self.adb.shell("setprop", "debug.firebase.analytics.app", analytics or ".none.", check=False)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--serial", required=True, help="the rented emulator (adb serial)")
    parser.add_argument("--generation", required=True, help="the rental's generation (`devices.py lease DEVICE`)")
    parser.add_argument("--devices-tool", required=True, type=Path, help="the device rental tool, devices.py")
    parser.add_argument("--out", required=True, type=Path, help="folder for the verdict, screenshots and screen snapshots")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--package", default=RELEASE_PACKAGE)
    parser.add_argument("--pack", help="Store code of the pack to download (default: the smallest file)")
    parser.add_argument("--list-url", default=STORE_LIST_URL)
    args = parser.parse_args(argv)
    if not args.devices_tool.is_file():
        parser.error(f"--devices-tool {args.devices_tool} does not exist")
    args.out = args.out.resolve()
    args.out.mkdir(parents=True, exist_ok=True)
    screen = Screen(args.devices_tool.resolve(), args.serial, args.generation, args.out)
    return run_and_record(StoreCheck(Adb(args.adb, args.serial), screen, args.package, args.list_url, args.pack),
                          args.out)


def run_and_record(check, out):
    """Run the check and write its verdict; any error, expected or not, is a failed verdict on file."""
    started = time.time()
    try:
        check.run()
        check.result["verdict"] = "passed"
    except Exception as error:
        check.result.update(verdict="failed", error=f"{type(error).__name__}: {error}")
        print(f"FAILED: {type(error).__name__}: {error}", file=sys.stderr)
    check.result["started_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime(started))
    check.result["seconds"] = round(time.time() - started, 1)
    (out / "store-check.json").write_text(json.dumps(check.result, indent=2, ensure_ascii=False, default=str) + "\n")
    return 0 if check.result["verdict"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
