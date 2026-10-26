#!/usr/bin/env python3
"""Check an installed release build against the real Store, the way a user reaches it.

The instrumented Store tests replace the catalogue with an offline fixture. This check drives
the installed app (by default the release package) on one rooted emulator from the outside,
with the device online:

1. the Store screen lists every pack the Store database lists (count and title/producer pairs);
2. one pack — by default the smallest file in the list — downloads, and on a first download
   on Android 13+ the notification permission dialog appears;
3. the pack is then on disk, in the library, and its record starts without history or bookmark.

Every run downloads one pack from the production file host, which adds one to that pack's
public download count, so run it once per release candidate. Packs are sized with HEAD
requests, which download nothing. Analytics from the app are marked as debug events
(`debug.firebase.analytics.app`) during the run, and the app language is set to English;
both are restored afterwards. The downloaded pack and the permission answer stay.

Results, screenshots and screen dumps go to `--out`; `store-check.json` holds the verdict.
"""
import argparse
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
import urllib.request
import xml.etree.ElementTree as ElementTree

# The same Realtime Database node the app subscribes to (`FirebaseStoreCatalog`), read over REST.
STORE_LIST_URL = "https://unipad-e41ab.firebaseio.com/store.json"
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

LIST_TIMEOUT = 30
DOWNLOAD_TIMEOUT = 180
DIALOG_TIMEOUT = 10
SCREEN_TIMEOUT = 20


class CheckFailed(Exception):
    pass


def normalized(text):
    """Titles in the database carry stray tabs and doubled spaces that the screen keeps or drops."""
    return " ".join((text or "").split())


@dataclass
class Node:
    text: str
    description: str
    resource_id: str
    clickable: bool
    scrollable: bool
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


def parse_screen(xml_text):
    """The root of a `uiautomator dump` hierarchy as Nodes."""
    def build(element):
        numbers = [int(value) for value in re.findall(r"-?\d+", element.get("bounds", "[0,0][0,0]"))]
        node = Node(element.get("text", ""), element.get("content-desc", ""), element.get("resource-id", ""),
                    element.get("clickable") == "true", element.get("scrollable") == "true", tuple(numbers[:4]))
        node.children = [build(child) for child in element if child.tag == "node"]
        return node
    root = ElementTree.fromstring(xml_text)
    return Node("", "", "", False, False, (0, 0, 0, 0), [build(child) for child in root if child.tag == "node"])


def find(root, text=None, description=None, resource_id=None):
    for node in root.walk():
        if ((text is None or node.text == text) and (description is None or node.description == description)
                and (resource_id is None or node.resource_id == resource_id)):
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
    """The Store's scrolling pack list: the scrollable node holding clickable rows."""
    for node in root.walk():
        if node.scrollable and any(child.clickable for child in node.children):
            return node
    return None


def store_rows(root):
    """(title, producer) of each Store row fully on screen, with the row node.

    A dump gives each node's visible bounds, so a row cut off at the list's edge is shorter
    than the others and may lack its producer line; only rows of full height are read."""
    listing = store_list(root)
    if listing is None:
        return []
    candidates = [row for row in listing.children if row.clickable and len(row.texts()) >= 2]
    full = max((row.height for row in candidates), default=0)
    return [((normalized(row.texts()[0]), normalized(row.texts()[1])), row)
            for row in candidates if row.height >= full - 2]


def fetch_store_list(url, timeout=30):
    with urllib.request.urlopen(url, timeout=timeout) as response:
        listed = json.load(response)
    if not isinstance(listed, dict) or not listed:
        raise CheckFailed(f"Store list at {url} is empty or not an object")
    packs = []
    for key, pack in listed.items():
        if not isinstance(pack, dict) or not all(isinstance(pack.get(name), str) and pack[name].strip()
                                                 for name in ("code", "URL", "title")):
            raise CheckFailed(f"Store list entry {key} lacks a code, URL or title")
        packs.append(pack)
    return packs


def head_size(url, timeout=15):
    """The file's Content-Length from a HEAD request (nothing is downloaded or counted); None when unknown."""
    request = urllib.request.Request(url, method="HEAD")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            length = response.headers.get("Content-Length")
            return int(length) if response.status == 200 and length and length.isdigit() else None
    except (OSError, ValueError):
        return None


def smallest_pack(packs, size_of):
    """The pack with the smallest served file, with its size; packs whose size is unknown are passed over."""
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


class Device:
    def __init__(self, adb, serial, out):
        self.command = [adb, "-s", serial]
        self.out = out

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

    def screen(self, name=None):
        self.shell("uiautomator", "dump", "/data/local/tmp/release-store-check.xml", timeout=30)
        xml_text = self.run("exec-out", "cat", "/data/local/tmp/release-store-check.xml").decode(errors="replace")
        if name:
            (self.out / f"{name}.xml").write_text(xml_text)
        return parse_screen(xml_text)

    def capture(self, name):
        (self.out / f"{name}.png").write_bytes(self.run("exec-out", "screencap", "-p", timeout=30))

    def tap(self, node):
        x, y = node.center
        self.shell("input", "tap", str(x), str(y))

    def swipe_up(self, node):
        left, top, right, bottom = node.bounds
        x = (left + right) // 2
        self.shell("input", "swipe", str(x), str(bottom - (bottom - top) // 6), str(x), str(top + (bottom - top) // 6), "600")

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
    def __init__(self, device, package, list_url, pack_code=None):
        self.device = device
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
        found = self.device.shell("find", self.app_data("files"), f"/storage/emulated/0/Android/data/{self.package}/files",
                                  "-maxdepth", "2", "-type", "d", "-name", code, check=False)
        return [line for line in found.splitlines() if line.strip() and "No such file" not in line]

    def read_record(self, code):
        with tempfile.TemporaryDirectory() as directory:
            copy = Path(directory) / DATABASE
            for suffix in ("", "-wal", "-shm"):
                source = self.app_data("databases", DATABASE + suffix)
                if self.device.shell("test", "-f", source, "&&", "echo", "yes", check=False) == "yes":
                    Path(str(copy) + suffix).write_bytes(self.device.run("exec-out", "cat", source))
            if not copy.exists():
                return None
            return pack_record(copy, code)

    def check_installed(self):
        sdk = int(self.device.shell("getprop", "ro.build.version.sdk"))
        if sdk < 33:
            raise CheckFailed(f"API {sdk}: this check needs API 33+ (per-app language, notification permission)")
        package = self.device.shell("dumpsys", "package", self.package)
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
            pack = next((pack for pack in packs if pack["code"] == self.pack_code), None)
            if pack is None:
                raise CheckFailed(f"Pack {self.pack_code} is not in the Store list")
            size = head_size(pack["URL"])
        else:
            pack, size = smallest_pack(packs, head_size)
        self.result["pack"] = {"code": pack["code"], "title": normalized(pack["title"]), "size": size,
                               "download_count_before": pack.get("downloadCount")}
        self.step("pack", f"{pack['code']} \"{normalized(pack['title'])}\" {size} bytes")
        if self.pack_folders(pack["code"]) or self.read_record(pack["code"]) is not None:
            raise CheckFailed(f"{pack['code']} is already in this app's library or database; "
                              "use a fresh installation or another --pack")
        return pack

    def open_store(self):
        self.device.shell("am", "force-stop", self.package)
        launcher = self.device.shell("cmd", "package", "resolve-activity", "--brief",
                                     "-c", "android.intent.category.LAUNCHER", self.package).splitlines()[-1].strip()
        if "/" not in launcher:
            raise CheckFailed(f"{self.package} has no launcher activity ({launcher})")
        self.device.shell("am", "start", "-W", "-n", launcher, timeout=60)
        entry = self.device.wait(lambda root: find(root, description=STORE_BUTTON_DESCRIPTION)
                                 or find(root, text=DOWNLOAD_NEW_PACKS), SCREEN_TIMEOUT, "The main screen's Store entry", "main-screen")
        self.device.capture("main-screen")
        self.device.tap(entry)

    def check_listing(self, packs):
        expected = len(packs)
        self.device.wait(lambda root: value_after(root, STORE_COUNT_LABEL) == str(expected), LIST_TIMEOUT,
                         f"Store count {expected}", "store-count")
        self.device.capture("store-list")
        wanted = {(normalized(pack["title"]), normalized(pack.get("producerName"))) for pack in packs}
        seen, unchanged = {}, 0
        listing = store_list(self.device.screen("store-list"))
        while unchanged < 2:
            rows = store_rows(self.device.screen())
            new = [key for key, _ in rows if key not in seen]
            seen.update(rows)
            unchanged = 0 if new else unchanged + 1
            self.device.swipe_up(listing)
            time.sleep(0.4)
        missing, extra = sorted(wanted - set(seen)), sorted(set(seen) - wanted)
        self.result["listing"] = {"count": expected, "rows_seen": len(seen), "missing": missing, "extra": extra}
        if missing or extra:
            raise CheckFailed(f"Store screen rows differ from the Store list: missing {missing}, extra {extra}")
        self.step("list", f"{expected} packs counted and all {len(seen)} rows match the Store list")

    def find_row(self, pack):
        key = (normalized(pack["title"]), normalized(pack.get("producerName")))
        for _ in range(60):
            root = self.device.screen()
            row = dict(store_rows(root)).get(key)
            if row:
                return row
            listing = store_list(root)
            self.device.shell("input", "swipe", str(listing.center[0]), str(listing.bounds[1] + listing.height // 6),
                              str(listing.center[0]), str(listing.bounds[3] - listing.height // 6), "600")
            time.sleep(0.4)
        raise CheckFailed(f"Row of {pack['code']} not found while scrolling back up the Store list")

    def download(self, pack, permission_before):
        self.device.tap(self.find_row(pack))
        button = self.device.wait(lambda root: find(root, text=DOWNLOAD), SCREEN_TIMEOUT, "The Download button", "store-detail")
        self.device.capture("store-detail")
        started = time.monotonic()
        self.device.tap(button)
        expect_dialog = not permission_before[0] and not permission_before[1]
        dialog = None
        if expect_dialog:
            dialog = self.device.wait(lambda root: find(root, resource_id=PERMISSION_ALLOW), DIALOG_TIMEOUT,
                                      "The notification permission dialog", "notification-permission")
            self.device.capture("notification-permission")
            self.device.tap(dialog)
        state = self.device.wait(lambda root: (find(root, text=DOWNLOADED) and DOWNLOADED) or (find(root, text=FAILED) and FAILED),
                                 DOWNLOAD_TIMEOUT, "The download result", "download-result")
        seconds = round(time.monotonic() - started, 1)
        self.device.capture("store-downloaded")
        if state == FAILED:
            raise CheckFailed(f"The app showed \"{FAILED}\" for {pack['code']} after {seconds} s")
        self.device.screen("store-downloaded")
        self.result["download"] = {"seconds": seconds, "notification_dialog": bool(dialog)}
        self.step("download", f"{DOWNLOADED} after {seconds} s; notification permission dialog "
                              f"{'shown and allowed' if dialog else 'not asked (already answered)'}")

    def check_library(self, pack):
        code = pack["code"]
        folders = self.pack_folders(code)
        if len(folders) != 1 or self.device.shell("test", "-f", f"{folders[0]}/info", "&&", "echo", "yes", check=False) != "yes":
            raise CheckFailed(f"Expected one folder of {code} with an info file, found {folders}")
        for _ in range(2):
            self.device.shell("input", "keyevent", "KEYCODE_BACK")
            time.sleep(1)
        item = self.device.wait(lambda root: find(root, resource_id=f"main_pack_{code}"), SCREEN_TIMEOUT,
                                f"{code} in the library", "library")
        self.device.tap(item)
        time.sleep(1.5)
        self.device.capture("library")
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
        self.device.root()
        dumpsys = self.check_installed()
        permission_before = notification_permission(dumpsys)
        packs = fetch_store_list(self.list_url)
        pack = self.choose_pack(packs)
        locales = app_locales(self.device.shell("cmd", "locale", "get-app-locales", self.package))
        analytics = self.device.shell("getprop", "debug.firebase.analytics.app")
        self.device.shell("cmd", "locale", "set-app-locales", self.package, "--locales", "en-US")
        self.device.shell("setprop", "debug.firebase.analytics.app", self.package)
        try:
            self.open_store()
            self.check_listing(packs)
            self.download(pack, permission_before)
            self.check_library(pack)
        finally:
            self.device.shell("cmd", "locale", "set-app-locales", self.package, "--locales", shlex.quote(locales), check=False)
            self.device.shell("setprop", "debug.firebase.analytics.app", analytics or ".none.", check=False)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--serial", required=True, help="the one emulator to drive (adb serial)")
    parser.add_argument("--out", required=True, type=Path, help="folder for the verdict, screenshots and screen dumps")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--package", default=RELEASE_PACKAGE)
    parser.add_argument("--pack", help="Store code of the pack to download (default: the smallest file)")
    parser.add_argument("--list-url", default=STORE_LIST_URL)
    args = parser.parse_args(argv)
    args.out.mkdir(parents=True, exist_ok=True)
    check = StoreCheck(Device(args.adb, args.serial, args.out), args.package, args.list_url, args.pack)
    started = time.time()
    try:
        check.run()
        check.result["verdict"] = "passed"
    except (CheckFailed, subprocess.SubprocessError, OSError) as error:
        check.result.update(verdict="failed", error=f"{type(error).__name__}: {error}")
        print(f"FAILED: {error}", file=sys.stderr)
    check.result["started_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime(started))
    check.result["seconds"] = round(time.time() - started, 1)
    (args.out / "store-check.json").write_text(json.dumps(check.result, indent=2, ensure_ascii=False) + "\n")
    return 0 if check.result["verdict"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
