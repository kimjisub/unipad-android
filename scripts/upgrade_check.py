#!/usr/bin/env python3
"""Check that installing a new build over an older one keeps what users saved.

Takes two builds of the same app signed with the same key (bundles, universal
.apks sets or APKs), and on one rooted emulator, for each saved state of the
tap-order setting (absent, on, off):

1. installs the older build on a clean slate and opens it, so it creates its
   own pack rows and settings file;
2. adds three packs (including mapped autoplay files and their backups), a
   bookmark, a play count and list/slide settings in the
   formats the older build uses, and opens it again to read them back;
3. installs the newer build over it (`adb install -r`), opens it twice;
4. compares pack files, database rows, settings values and the packs listed
   on screen before and after, and requires no crash or ANR of the app.

The `legacy-theme` scenario also leaves, just before the upgrade, what builds
before 4.3.0 left for a user who chose the bundled S skin: `SelectedTheme` set to
`zip://sskin` and the theme extracted to `files/themes/sskin` in the app's external files
folder. The newer build
must change the setting to `asset://sskin`, delete that folder and draw its play
screen in the S skin.

Bundles are turned into universal APKs with bundletool's default debug key, so
release bundles can be installed over each other; this is not a Play-signed
update. Needs `adb root` (release builds cannot be read with run-as) and turns
on airplane mode and the analytics debug flag for the run, restoring both;
Android 11 (API 30) or newer.
Exit status: 0 every check passed, 1 a check failed (including the newer build
failing to install, open or stay open), 2 the run could not reach the upgrade.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import struct
import subprocess
import sys
import tempfile
import time
import traceback
import xml.etree.ElementTree as ET
import zipfile

DB_NAME = 'UniPad.db'
PREFS_NAME = 'data.xml'
TRACE_KEY = 'TraceLogClassic'
# Settings 4.1.9 already stored, with the values chosen in the hand check.
SEEDED_PREFS = {'SortMethod': ('int', 0), 'SortOrder': ('boolean', False), 'SlideMode': ('boolean', True)}
SCENARIOS = {'trace-absent': None, 'trace-on': True, 'trace-off': False, 'legacy-theme': None}
# Scenarios that leave an older build's extracted bundled theme selected before the upgrade.
LEGACY_THEMES = {'legacy-theme': 'sskin'}
THEME_KEY = 'SelectedTheme'
THEME_ASSETS = Path(__file__).resolve().parents[1] / 'app/src/main/assets/themes'
# agent-device's snapshot helper holds the accessibility connection, and `uiautomator dump`
# then writes an empty file, which read as packs missing from the list.
SNAPSHOT_HELPER = 'com.callstack.agentdevice.snapshothelper'
# Play screen comparison: grey levels of a 16x9 grid of blocks.
GRID = (16, 9)
PLAY_TIMEOUT_S = 30
# The migrated play screen must differ from the default theme's by at least this mean grey
# level (0-255) and by THEME_MARGIN times more than it differs from the S skin chosen directly.
THEME_MIN_DIFFERENCE = 8
THEME_MARGIN = 4
PACKS = [
    dict(id='upgrade_check_a', title='Upgrade Check A', bookmark=True, open_count=0, opened=False),
    dict(id='upgrade_check_b', title='Upgrade Check B', bookmark=False, open_count=3, opened=True,
         autoplay_backup='autoPlay_2026_11_11-15_32_39'),
    dict(id='upgrade_check_c', title='Upgrade Check C', bookmark=False, open_count=0, opened=False,
         autoplay_backup='autoPlay_previous'),
]
ORIGINAL_AUTOPLAY = 'o 1 1\nd 100\nf 1 1\nc 2\no 2 2\nd 100\nf 2 2\n'
# Mapping the 10 ms silent sound replaces waits and renders presses as taps.
MAPPED_AUTOPLAY = 'd 1000\nt 1 1\nd 10\nc 2\nt 2 2\n'
# Fixed so the before/after rows are comparable by value.
OPENED_AT_MS = 1_790_000_000_000
LAUNCH_TIMEOUT_S = 90
SETTLE_S = 8
SCREEN_ATTEMPTS = 3
MIN_SDK = 30


class SetupError(Exception):
    """The run could not reach the point where an upgrade is judged."""


class AppNotReady(Exception):
    """The app did not reach its pack list, or stopped soon after."""


class PlayNotReady(Exception):
    """The play screen did not open, or did not come to rest; an app failure, not a failed run."""


class EmptyScreenDump(SetupError):
    """`uiautomator dump` gave nothing to read, so the screen cannot be judged."""


# --- pure helpers (unit tested) -------------------------------------------------

def silent_wav(frames: int = 441) -> bytes:
    data = b'\0\0' * frames
    header = struct.pack('<4sI4s4sIHHIIHH4sI', b'RIFF', 36 + len(data), b'WAVE', b'fmt ', 16,
                         1, 1, 44100, 88200, 2, 16, b'data', len(data))
    return header + data


def write_pack(root: Path, title: str, autoplay_backup: str | None = None) -> None:
    """A two-chain 8x8 pack with silent sounds, an LED and an autoplay, readable by 4.x builds."""
    (root / 'sounds').mkdir(parents=True)
    (root / 'keyLED').mkdir()
    (root / 'info').write_text(
        f'title={title}\nproducerName=UniPad upgrade check\nbuttonX=8\nbuttonY=8\nchain=2\nsquareButton=true\n')
    (root / 'sounds/silence.wav').write_bytes(silent_wav())
    (root / 'keySound').write_text(''.join(
        f'{c} {x} {y} silence.wav\n' for c in (1, 2) for x in range(1, 9) for y in range(1, 9)))
    (root / 'keyLED/1 1 1 1').write_text('o 1 1 a 5\nd 100\nf 1 1\n')
    for name, content in autoplay_files(autoplay_backup).items():
        (root / name).write_bytes(content)


def autoplay_files(backup: str | None) -> dict[str, bytes]:
    """The file layout after mapping: a changed autoplay and the untouched original."""
    if backup is None:
        return {'autoPlay': ORIGINAL_AUTOPLAY.encode()}
    return {'autoPlay': MAPPED_AUTOPLAY.encode(), backup: ORIGINAL_AUTOPLAY.encode()}


def autoplay_seed_mismatches(files: dict[str, str]) -> list[str]:
    """Do not accept a baseline whose autoplay or backup was never seeded, or was rewritten."""
    wrong = []
    for pack in PACKS:
        for name, content in autoplay_files(pack.get('autoplay_backup')).items():
            path = f'{pack["id"]}/{name}'
            expected = hashlib.sha256(content).hexdigest()
            if files.get(path) != expected:
                wrong.append(f'{path}: seeded {expected}, read {files.get(path)}')
    return wrong


def parse_prefs(xml_text: str | None) -> dict[str, tuple[str, object]]:
    """SharedPreferences XML as {key: (type, value)}; a missing file is an empty map."""
    if not xml_text:
        return {}
    prefs: dict[str, tuple[str, object]] = {}
    for node in ET.fromstring(xml_text):
        name = node.get('name')
        kind = node.tag
        if kind == 'string':
            value: object = node.text or ''
        elif kind == 'set':
            value = sorted(child.text or '' for child in node)
        elif kind == 'boolean':
            value = node.get('value') == 'true'
        elif kind in ('int', 'long'):
            value = int(node.get('value'))
        elif kind == 'float':
            value = float(node.get('value'))
        else:
            value = node.get('value')
        prefs[name] = (kind, value)
    return prefs


def render_prefs(prefs: dict[str, tuple[str, object]]) -> str:
    root = ET.Element('map')
    for name, (kind, value) in sorted(prefs.items()):
        node = ET.SubElement(root, kind, name=name)
        if kind == 'string':
            node.text = str(value)
        elif kind == 'set':
            for item in value:
                ET.SubElement(node, 'string').text = item
        elif kind == 'boolean':
            node.set('value', 'true' if value else 'false')
        else:
            node.set('value', str(value))
    ET.indent(root, space='    ')
    return "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" + ET.tostring(root, encoding='unicode') + '\n'


def seeded_prefs(existing: dict[str, tuple[str, object]], trace: bool | None) -> dict[str, tuple[str, object]]:
    prefs = {**existing, **SEEDED_PREFS}
    prefs.pop(TRACE_KEY, None)
    if trace is not None:
        prefs[TRACE_KEY] = ('boolean', trace)
    return prefs


def seed_mismatches(prefs: dict[str, tuple[str, object]], trace: bool | None) -> list[str]:
    """Seeded settings the older build does not hold when it is opened again."""
    expected = dict(SEEDED_PREFS)
    if trace is not None:
        expected[TRACE_KEY] = ('boolean', trace)
    wrong = [f'{key}: seeded {value}, read {prefs.get(key)}' for key, value in expected.items() if prefs.get(key) != value]
    if trace is None and TRACE_KEY in prefs:
        wrong.append(f'{TRACE_KEY}: seeded absent, read {prefs[TRACE_KEY]}')
    return wrong


def seed_rows(conn: sqlite3.Connection) -> None:
    for pack in PACKS:
        conn.execute('UPDATE Unipack SET bookmark = ?, openCount = ?, lastOpenedAt = ? WHERE id = ?',
                     (int(pack['bookmark']), pack['open_count'], OPENED_AT_MS if pack['opened'] else None, pack['id']))
    conn.commit()


def read_db(path: Path) -> dict:
    conn = sqlite3.connect(f'file:{path}?mode=ro', uri=True)
    try:
        rows = conn.execute('SELECT id, bookmark, openCount, lastOpenedAt, createdAt FROM Unipack ORDER BY id').fetchall()
        version = conn.execute('PRAGMA user_version').fetchone()[0]
    finally:
        conn.close()
    return dict(user_version=version, rows={r[0]: dict(bookmark=r[1], openCount=r[2], lastOpenedAt=r[3], createdAt=r[4])
                                            for r in rows})


def expected_rows(created_at: dict[str, int]) -> dict[str, dict]:
    return {p['id']: dict(bookmark=int(p['bookmark']), openCount=p['open_count'],
                          lastOpenedAt=OPENED_AT_MS if p['opened'] else None, createdAt=created_at[p['id']])
            for p in PACKS}


def compare(before: dict, after: dict, trace: bool | None, launch_problems: list[str] = ()) -> list[str]:
    """Every reason the upgrade failed: how opening the newer build went, then how the snapshots differ."""
    problems = list(launch_problems)
    if after['files'] != before['files']:
        lost = sorted(set(before['files']) - set(after['files']))
        changed = sorted(k for k in before['files'] if k in after['files'] and before['files'][k] != after['files'][k])
        added = sorted(set(after['files']) - set(before['files']))
        problems.append(f'pack files differ: lost {lost}, changed {changed}, added {added}')
    if 'error' in after['db']:
        problems.append(f'database after the upgrade: {after["db"]["error"]}')
    else:
        for pack_id, row in before['db']['rows'].items():
            if after['db']['rows'].get(pack_id) != row:
                problems.append(f'database row {pack_id}: before {row}, after {after["db"]["rows"].get(pack_id)}')
        for pack_id in sorted(set(after['db']['rows']) - set(before['db']['rows'])):
            problems.append(f'database row {pack_id} appeared after the upgrade')
        if after['db']['user_version'] < before['db']['user_version']:
            problems.append(f'database version went down: {before["db"]["user_version"]} -> {after["db"]["user_version"]}')
    for key, value in before['prefs'].items():
        if after['prefs'].get(key) != value:
            problems.append(f'setting {key}: before {value}, after {after["prefs"].get(key)}')
    if trace is None and TRACE_KEY in after['prefs']:
        problems.append(f'setting {TRACE_KEY} was written by opening the app: {after["prefs"][TRACE_KEY]}')
    missing = sorted(set(before['titles']) - set(after['titles']))
    if missing:
        problems.append(f'packs not listed on screen after the upgrade: {missing}')
    return problems


def file_hashes(listing: str, root: str) -> dict[str, str]:
    """`sha256sum` lines under root as {relative path: hash}."""
    hashes = {}
    for line in listing.splitlines():
        if not line.strip():
            continue
        digest, path = line.split(None, 1)
        hashes[path.strip()[len(root):].lstrip('/')] = digest
    return hashes


def app_events(lines: str, package: str) -> list[str]:
    """Crash, native crash and ANR lines of the package (not a package it prefixes, nor one that mentions it)."""
    name = rf'{re.escape(package)}(?![\w.])'
    # am_crash and am_anr both start [<number>,<number>,<process>,...
    pattern = re.compile(rf'Process: {name}|>>> {name} <<<|am_(crash|anr)\s*: \[\d+,\d+,{name},|ANR in {name}')
    return [line for line in lines.splitlines() if pattern.search(line)]


def screen_texts(ui_xml: str) -> set[str]:
    try:
        root = ET.fromstring(ui_xml)
    except ET.ParseError:
        return set()
    return {node.get('text') for node in root.iter('node') if node.get('text')}


def raw_screen(data: bytes) -> tuple[int, int, memoryview]:
    """(width, height, RGBA bytes) of `screencap` output without -p: a 12- or 16-byte header, then the pixels."""
    if len(data) < 16:
        raise ValueError(f'screencap gave {len(data)} bytes')
    width, height = struct.unpack('<II', data[:8])
    header = len(data) - width * height * 4
    if header not in (12, 16):
        raise ValueError(f'screencap gave {len(data)} bytes for {width}x{height}')
    return width, height, memoryview(data)[header:]


def screen_signature(raw: bytes, grid: tuple[int, int] = GRID, step: int = 4) -> list[float]:
    """Mean grey level of each block of a grid laid over a raw screenshot, sampling every `step`th pixel."""
    width, height, pixels = raw_screen(raw)
    columns, lines = grid
    sums, counts = [0.0] * (columns * lines), [0] * (columns * lines)
    for y in range(0, height, step):
        row, block_row = pixels[y * width * 4:(y + 1) * width * 4], y * lines // height * columns
        stride = 4 * step
        for index, (r, g, b) in enumerate(zip(row[0::stride], row[1::stride], row[2::stride])):
            block = block_row + index * step * columns // width
            sums[block] += r * 0.299 + g * 0.587 + b * 0.114
            counts[block] += 1
    return [total / count if count else 0.0 for total, count in zip(sums, counts)]


def signature_difference(a: list[float], b: list[float]) -> float:
    return sum(abs(x - y) for x, y in zip(a, b)) / len(a)


def theme_screen_problems(migrated: list[float], default: list[float], chosen: list[float], theme: str) -> list[str]:
    """Whether the play screen after the migration is drawn in `theme` (chosen directly) and not the default theme."""
    to_default, to_chosen = signature_difference(migrated, default), signature_difference(migrated, chosen)
    if to_default >= THEME_MIN_DIFFERENCE and to_default >= THEME_MARGIN * to_chosen:
        return []
    return [f'the play screen after the theme migration does not look like {theme}: grey-level difference '
            f'{to_default:.1f} from the default theme, {to_chosen:.1f} from {theme} chosen directly '
            f'(needs at least {THEME_MIN_DIFFERENCE} and {THEME_MARGIN}x the second)']


def legacy_theme_problems(prefs: dict[str, tuple[str, object]], folder_left: bool, theme: str) -> list[str]:
    problems = []
    if prefs.get(THEME_KEY) != ('string', f'asset://{theme}'):
        problems.append(f'setting {THEME_KEY}: expected asset://{theme} after the upgrade, read {prefs.get(THEME_KEY)}')
    if folder_left:
        problems.append(f'the older build\'s extracted theme folder files/themes/{theme} was not deleted')
    return problems


def build_tools_order(name: str) -> tuple:
    """Sort key for build-tools folders: by version, with a preview (`37.0.0-rc1`) before its release."""
    release, _, preview = name.partition('-')
    return [int(x) for x in re.findall(r'\d+', release)], not preview, [int(x) for x in re.findall(r'\d+', preview)]


# --- builds --------------------------------------------------------------------

def build_tool(name: str) -> str:
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:
        raise SetupError('Set ANDROID_HOME to the Android SDK.')
    versions = sorted((Path(sdk) / 'build-tools').glob('*/' + name), key=lambda p: build_tools_order(p.parent.name))
    if not versions:
        raise SetupError(f'No {name} in {sdk}/build-tools.')
    return str(versions[-1])


def universal_apk(source: Path, work: Path) -> Path:
    if source.suffix == '.apk':
        return source
    work.mkdir(parents=True, exist_ok=True)
    apks = source
    if source.suffix == '.aab':
        if not shutil.which('bundletool'):
            raise SetupError('bundletool is needed to turn a bundle into an APK.')
        apks = work / 'universal.apks'
        run(['bundletool', 'build-apks', '--mode=universal', '--overwrite', f'--bundle={source}', f'--output={apks}'])
    elif source.suffix != '.apks':
        raise SetupError(f'{source}: expected .aab, .apks or .apk')
    with zipfile.ZipFile(apks) as archive:
        if 'universal.apk' not in archive.namelist():
            raise SetupError(f'{apks} has no universal.apk; build it with --mode=universal')
        archive.extract('universal.apk', work)
    return work / 'universal.apk'


def apk_identity(apk: Path) -> dict:
    badging = run([build_tool('aapt2'), 'dump', 'badging', str(apk)])
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging)
    if not match:
        raise SetupError(f'{apk}: cannot read the package name and version')
    certs = run([build_tool('apksigner'), 'verify', '--print-certs', str(apk)])
    signers = sorted(set(re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', certs)))
    return dict(package=match[1], version_code=int(match[2]), version_name=match[3], signers=signers,
                sha256=hashlib.sha256(apk.read_bytes()).hexdigest())


def run(cmd: list[str], *, check: bool = True, timeout: float = 600) -> str:
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    if check and result.returncode != 0:
        raise SetupError(f'{" ".join(cmd)} failed ({result.returncode}): {result.stderr.strip() or result.stdout.strip()}')
    return result.stdout


# --- device ---------------------------------------------------------------------

class Device:
    def __init__(self, serial: str):
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or ''
        self.adb = [str(Path(sdk) / 'platform-tools/adb') if sdk else 'adb', '-s', serial]

    def __call__(self, *args: str, check: bool = True, timeout: float = 600) -> str:
        return run([*self.adb, *args], check=check, timeout=timeout)

    def sh(self, command: str, check: bool = True) -> str:
        return self('shell', command, check=check)

    def pull(self, remote: str, local: Path) -> bool:
        return subprocess.run([*self.adb, 'pull', remote, str(local)], capture_output=True).returncode == 0

    def exists(self, remote: str) -> bool:
        return self.sh(f'[ -e {q(remote)} ] && echo yes', check=False).strip() == 'yes'

    def put(self, local: Path, remote: str, owner_of: str) -> None:
        """Copy a file or folder into app storage, owned and labelled like `owner_of`."""
        staging = f'/data/local/tmp/upgrade_check_{local.name}'
        self.sh(f'rm -rf {q(staging)}')
        self('push', str(local), staging)
        owner = self.sh(f'stat -c %u:%g {q(owner_of)}').strip()
        self.sh(f'rm -rf {q(remote)} && cp -r {q(staging)} {q(remote)} && chown -R {owner} {q(remote)} '
                f'&& restorecon -R {q(remote)} && rm -rf {q(staging)}')

    def device_time(self) -> str:
        return self.sh("date +'%m-%d %H:%M:%S.000'").strip()


def q(path: str) -> str:
    return "'" + path.replace("'", "'\\''") + "'"


class UpgradeRun:
    def __init__(self, device: Device, old: Path, new: Path, package: str, out: Path):
        self.d, self.old, self.new, self.package, self.out = device, old, new, package, out
        self.data = f'/data/data/{package}'
        # The app's external files folder (getExternalFilesDir), where packs and extracted themes live.
        self.external = f'/data/media/0/Android/data/{package}/files'
        self.packs_dir = f'{self.external}/UniPack'

    def scenario(self, name: str, trace: bool | None) -> dict:
        folder = self.out / name
        folder.mkdir(parents=True, exist_ok=True)
        before = self.prepare(folder, trace)
        theme = LEGACY_THEMES.get(name)
        if theme:
            self.leave_legacy_theme(folder, theme)
        upgraded_at = self.d.device_time()
        launch_problems = self.upgrade()
        after = self.snapshot(folder / 'after', empty_screen_is_problem=True)
        self.stop()
        # The legacy theme scenario replaced the selected theme after `before`; it is judged on its own.
        compared = {**before, 'prefs': {k: v for k, v in before['prefs'].items() if k != THEME_KEY}} if theme else before
        problems = compare(compared, after, trace, launch_problems)
        if 'screen_error' in after:
            problems.append(f'screen after the upgrade: {after["screen_error"]}')
        if theme and not launch_problems:
            problems += legacy_theme_problems(after['prefs'], self.d.exists(self.theme_dir(theme)), theme)
            problems += self.theme_screen(folder / 'after', theme)
        problems += [f'crash or ANR: {line}' for line in self.events(upgraded_at)]
        result = dict(scenario=name, trace_saved=trace, passed=not problems, problems=problems,
                      before={k: v for k, v in before.items() if k != 'files'},
                      after={k: v for k, v in after.items() if k != 'files'},
                      pack_files=len(after['files']))
        (folder / 'result.json').write_text(json.dumps(result, indent=1, ensure_ascii=False) + '\n')
        return result

    def prepare(self, folder: Path, trace: bool | None) -> dict:
        """Installs the older build with the seeded packs, rows and settings; its snapshot before the upgrade."""
        name = folder.name
        started = self.d.device_time()
        self.d('uninstall', self.package, check=False)
        self.d('install', str(self.old))
        try:
            self.launch()
            created_at = self.wait_for_rows(folder)
            self.stop()
            self.seed(folder, trace)
            self.launch()
        except AppNotReady as error:
            raise SetupError(f'{name}: the older build: {error}') from error
        before = self.snapshot(folder / 'before')
        self.stop()
        if 'error' in before['db']:
            raise SetupError(f'{name}: the older build\'s database: {before["db"]["error"]}')
        if before['db']['rows'] != expected_rows(created_at):
            raise SetupError(f'{name}: the older build changed the seeded rows: {before["db"]["rows"]}')
        if wrong := seed_mismatches(before['prefs'], trace):
            raise SetupError(f'{name}: the older build does not hold the seeded settings: {"; ".join(wrong)}')
        if wrong := autoplay_seed_mismatches(before['files']):
            raise SetupError(f'{name}: the older build does not hold the seeded autoplay files: {"; ".join(wrong)}')
        if missing := {p['title'] for p in PACKS} - set(before['titles']):
            raise SetupError(f'{name}: the older build does not list the packs {sorted(missing)}; see before/screen.xml')
        if events := self.events(started):
            raise SetupError(f'{name}: the older build crashed before the upgrade: {events[0]}')
        return before

    def theme_dir(self, theme: str) -> str:
        return f'{self.external}/themes/{theme}'

    def leave_legacy_theme(self, folder: Path, theme: str) -> None:
        """With the app stopped: the setting and extracted folder a build before 4.3.0 left for `theme`."""
        themes = f'{self.external}/themes'
        packs = q(self.packs_dir)
        self.d.sh(f'mkdir -p {q(themes)} && chown $(stat -c %u:%g {packs}) {q(themes)} '
                  f'&& chmod $(stat -c %a {packs}) {q(themes)} && restorecon {q(themes)}')
        self.d.put(THEME_ASSETS / theme, self.theme_dir(theme), owner_of=themes)
        self.write_theme_setting(folder / 'legacy-theme', f'zip://{theme}')
        listing = self.d.sh(f'ls -lZ {q(self.theme_dir(theme))}', check=False)
        (folder / 'legacy-theme' / 'themes-before.txt').write_text(listing)
        if not self.d.exists(f'{self.theme_dir(theme)}/theme.json'):
            raise SetupError(f'could not leave the extracted {theme} folder: {listing}')

    def write_theme_setting(self, folder: Path, value: str | None) -> None:
        """Sets (or with None removes) the selected theme in the stopped app's settings file and reads it back."""
        folder.mkdir(parents=True, exist_ok=True)
        prefs_remote = f'{self.data}/shared_prefs/{PREFS_NAME}'
        prefs = parse_prefs(self.read(prefs_remote))
        prefs.pop(THEME_KEY, None)
        if value is not None:
            prefs[THEME_KEY] = ('string', value)
        (folder / PREFS_NAME).write_text(render_prefs(prefs))
        self.d.put(folder / PREFS_NAME, prefs_remote, owner_of=f'{self.data}/shared_prefs')
        self.d.sh(f'chmod 660 {q(prefs_remote)}')
        read = parse_prefs(self.read(prefs_remote)).get(THEME_KEY)
        if read != (None if value is None else ('string', value)):
            raise SetupError(f'could not set {THEME_KEY} to {value}: read {read}')

    def theme_screen(self, folder: Path, theme: str) -> list[str]:
        """Play screen after the migration against the default theme and `theme` chosen directly."""
        shots = {}
        for label, setting in (('migrated', ...), ('default', None), (theme, f'asset://{theme}')):
            if setting is not ...:
                self.write_theme_setting(folder / f'play-{label}', setting)
            try:
                shots[label] = self.open_play(PACKS[0]['id'], folder / f'play-{label}.png')
            except PlayNotReady as error:
                return [f'play screen with the {label} theme: {error}']
            finally:
                self.stop()
        differences = dict(to_default=signature_difference(shots['migrated'], shots['default']),
                           to_chosen=signature_difference(shots['migrated'], shots[theme]))
        (folder / 'theme-screen.json').write_text(json.dumps(differences, indent=1) + '\n')
        return theme_screen_problems(shots['migrated'], shots['default'], shots[theme], theme)

    def open_play(self, pack_id: str, png: Path) -> list[float]:
        """Opens the play screen of a pack (root may start the activity directly); its signature once at rest.

        The screenshot at rest, or the last one taken, is saved as `png`."""
        path = f'/storage/emulated/0/Android/data/{self.package}/files/UniPack/{pack_id}'
        self.d.sh(f'am start -n {self.package}/.activity.PlayActivity --es path {q(path)}')
        deadline, previous, top, capture_error = time.monotonic() + PLAY_TIMEOUT_S, None, '', ''
        while time.monotonic() < deadline:
            time.sleep(2)
            top = self.d.sh('dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity"', check=False)
            if '.activity.PlayActivity' not in top:
                continue
            capture = subprocess.run([*self.d.adb, 'exec-out', 'screencap'], capture_output=True, timeout=60)
            try:
                if capture.returncode:
                    raise ValueError(f'screencap exited {capture.returncode}')
                signature = screen_signature(capture.stdout)
            except ValueError as error:
                # A cut-off or failed capture is retried; only the deadline ends the wait.
                previous, capture_error = None, str(error)
                continue
            if previous and signature_difference(signature, previous) < 1:
                self.screenshot(png)
                return signature
            previous = signature
        self.screenshot(png)
        raise PlayNotReady(f'the play screen of {pack_id} did not open and come to rest within {PLAY_TIMEOUT_S} s '
                           f'(on top: {" ".join(top.split())}'
                           f'{f"; last capture: {capture_error}" if capture_error else ""}; see {png.name})')

    def screenshot(self, png: Path) -> None:
        with png.open('wb') as out:
            subprocess.run([*self.d.adb, 'exec-out', 'screencap', '-p'], stdout=out, timeout=60)

    def upgrade(self) -> list[str]:
        """Installs the newer build over the older one and leaves it open the second time; what went wrong."""
        try:
            self.d('install', '-r', str(self.new))
        except SetupError as error:
            return [f'the newer build did not install over the older one: {error}']
        try:
            self.launch()
            self.stop()
            self.launch()
        except AppNotReady as error:
            return [f'after the upgrade, {error}']
        return []

    def events(self, since: str) -> list[str]:
        return app_events(self.d('logcat', '-d', '-b', 'crash', '-b', 'events', '-T', since), self.package)

    def launch(self) -> None:
        component = self.d.sh(f'cmd package resolve-activity --brief -c android.intent.category.LAUNCHER {self.package}').split()[-1]
        self.d.sh(f'am start -n {component}')
        deadline = time.monotonic() + LAUNCH_TIMEOUT_S
        while time.monotonic() < deadline:
            if '.activity.MainActivity' in self.d.sh('dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity"', check=False):
                time.sleep(SETTLE_S)
                if not self.d.sh(f'pidof {self.package}', check=False).strip():
                    raise AppNotReady(f'{self.package} stopped right after opening')
                return
            time.sleep(1)
        raise AppNotReady(f'{self.package} did not reach its pack list within {LAUNCH_TIMEOUT_S} s')

    def stop(self) -> None:
        self.d.sh(f'am force-stop {self.package}')

    def wait_for_rows(self, folder: Path) -> dict[str, int]:
        """Adds the packs and waits until the older build has made a row for each."""
        with tempfile.TemporaryDirectory() as tmp:
            for pack in PACKS:
                write_pack(Path(tmp) / pack['id'], pack['title'], pack.get('autoplay_backup'))
                self.d.put(Path(tmp) / pack['id'], f'{self.packs_dir}/{pack["id"]}', owner_of=self.packs_dir)
        self.stop()
        self.launch()
        deadline = time.monotonic() + LAUNCH_TIMEOUT_S
        while time.monotonic() < deadline:
            rows = self.pull_db(folder / 'seed-db').get('rows', {})
            if all(p['id'] in rows for p in PACKS):
                return {p['id']: rows[p['id']]['createdAt'] for p in PACKS}
            time.sleep(2)
        raise SetupError('the older build made no database rows for the added packs')

    def seed(self, folder: Path, trace: bool | None) -> None:
        local = folder / 'seed-db'
        if 'error' in (db := self.pull_db(local)):
            raise SetupError(f'cannot seed the older build\'s database: {db["error"]}')
        conn = sqlite3.connect(local / DB_NAME)
        try:
            seed_rows(conn)
            conn.execute('PRAGMA wal_checkpoint(TRUNCATE)')
        finally:
            conn.close()
        db_remote = f'{self.data}/databases/{DB_NAME}'
        self.d.sh(f'rm -f {q(db_remote)}-wal {q(db_remote)}-shm')
        self.d.put(local / DB_NAME, db_remote, owner_of=db_remote)
        prefs_remote = f'{self.data}/shared_prefs/{PREFS_NAME}'
        prefs = seeded_prefs(parse_prefs(self.read(prefs_remote)), trace)
        if not self.d.exists(f'{self.data}/shared_prefs'):
            self.d.sh(f'mkdir {self.data}/shared_prefs && chmod 771 {self.data}/shared_prefs && '
                      f'chown $(stat -c %u:%g {self.data}) {self.data}/shared_prefs && restorecon {self.data}/shared_prefs')
        (folder / PREFS_NAME).write_text(render_prefs(prefs))
        self.d.put(folder / PREFS_NAME, prefs_remote, owner_of=f'{self.data}/shared_prefs')
        self.d.sh(f'chmod 660 {q(prefs_remote)}')

    def pull_db(self, local: Path) -> dict:
        shutil.rmtree(local, ignore_errors=True)
        local.mkdir(parents=True)
        for suffix in ('', '-wal', '-shm'):
            self.d.pull(f'{self.data}/databases/{DB_NAME}{suffix}', local / f'{DB_NAME}{suffix}')
        if not (local / DB_NAME).exists():
            return dict(error=f'{DB_NAME} is missing')
        try:
            return read_db(local / DB_NAME)
        except sqlite3.DatabaseError as error:
            return dict(error=f'{DB_NAME} cannot be read: {error}')

    def read(self, remote: str) -> str | None:
        return self.d('exec-out', 'cat', remote) if self.d.exists(remote) else None

    def screen(self) -> str:
        """The UI dump, taken again while it does not list every pack (a dump can fail or catch the list loading)."""
        for attempt in range(SCREEN_ATTEMPTS):
            if attempt:
                time.sleep(2)
            ui = self.d.sh('rm -f /data/local/tmp/upgrade_check_ui.xml; uiautomator dump /data/local/tmp/upgrade_check_ui.xml '
                           '>/dev/null; cat /data/local/tmp/upgrade_check_ui.xml', check=False)
            if {p['title'] for p in PACKS} <= screen_texts(ui):
                break
        if '<hierarchy' not in ui:
            raise EmptyScreenDump(f'uiautomator dump gave no screen hierarchy {SCREEN_ATTEMPTS} times; another UI '
                                  f'automation may hold the accessibility connection (stop {SNAPSHOT_HELPER})')
        return ui

    def snapshot(self, folder: Path, empty_screen_is_problem: bool = False) -> dict:
        """Screen, settings, pack files and database rows. Before the upgrade an empty screen dump means the check
        cannot read the screen (EmptyScreenDump); after it, with the older build's dumps read, it is the newer
        build's problem and is returned as `screen_error`."""
        folder.mkdir(parents=True, exist_ok=True)
        screen_error = None
        try:
            ui = self.screen()
        except EmptyScreenDump as error:
            if not empty_screen_is_problem:
                raise
            ui, screen_error = '', str(error)
        (folder / 'screen.xml').write_text(ui)
        self.screenshot(folder / 'screen.png')
        prefs_text = self.read(f'{self.data}/shared_prefs/{PREFS_NAME}')
        (folder / PREFS_NAME).write_text(prefs_text or '')
        listing = self.d.sh(f'find {q(self.packs_dir)} -type f -exec sha256sum {{}} +', check=False)
        files = file_hashes(listing, self.packs_dir)
        (folder / 'pack-files.txt').write_text(listing)
        titles = sorted(screen_texts(ui) & {p['title'] for p in PACKS})
        snapshot = dict(db=self.pull_db(folder / 'db'), prefs=parse_prefs(prefs_text), files=files, titles=titles)
        if screen_error:
            snapshot['screen_error'] = screen_error
        return snapshot


def restore_device(device: Device, steps: list[str]) -> None:
    """Uninstalls what the run installed, then undoes its device changes; a failed step does not stop the rest."""
    for step in steps:
        try:
            if step == 'airplane-off':
                device.sh('cmd connectivity airplane-mode disable', check=False)
            elif step.startswith('analytics:'):
                device.sh(f'setprop debug.firebase.analytics.app {step.split(":", 1)[1]}', check=False)
            elif step == 'unroot':
                device('unroot', check=False)
                device('wait-for-device', check=False)
            elif step.startswith('uninstall:'):
                device('uninstall', step.split(':', 1)[1], check=False)
        except Exception as error:  # keep restoring the rest
            print(f'could not restore the device ({step}): {error}', file=sys.stderr)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'), help='emulator serial (default ANDROID_SERIAL)')
    parser.add_argument('--old', type=Path, required=True, help='the published build: .aab, universal .apks or .apk')
    parser.add_argument('--new', type=Path, required=True, help='the candidate build, signed with the same key')
    parser.add_argument('--out', type=Path, required=True, help='new folder for the evidence')
    parser.add_argument('--scenario', choices=sorted(SCENARIOS), action='append',
                        help='run only these scenarios (default all four)')
    args = parser.parse_args(argv)
    if not args.serial:
        parser.error('pass --serial or set ANDROID_SERIAL')
    if args.out.exists() and any(args.out.iterdir()):
        parser.error(f'{args.out} is not empty; give each run its own folder')
    args.out.mkdir(parents=True, exist_ok=True)
    device = Device(args.serial)
    restore: list[str] = []
    installed: list[str] = []
    try:
        old = universal_apk(args.old.resolve(), args.out / 'old-build')
        new = universal_apk(args.new.resolve(), args.out / 'new-build')
        builds = dict(old=apk_identity(old), new=apk_identity(new))
        if builds['old']['package'] != builds['new']['package']:
            raise SetupError(f'the builds are different apps: {builds["old"]["package"]} and {builds["new"]["package"]}')
        if builds['old']['signers'] != builds['new']['signers']:
            raise SetupError('the builds are signed with different keys, so one cannot be installed over the other')
        if builds['new']['version_code'] <= builds['old']['version_code']:
            raise SetupError(f'the new build is not newer: {builds["old"]["version_code"]} -> {builds["new"]["version_code"]}')
        package = builds['old']['package']
        sdk = device.sh('getprop ro.build.version.sdk').strip()
        if not sdk.isdigit() or int(sdk) < MIN_SDK:
            raise SetupError(f'the device runs API {sdk}; the check switches airplane mode with a command that needs API {MIN_SDK}')
        was_root = device.sh('id -u').strip() == '0'
        if not was_root:
            device('root')
            device('wait-for-device')
            if device.sh('id -u').strip() != '0':
                raise SetupError('adb root is refused on this device; use an emulator image without Play Store')
            restore.append('unroot')
        airplane = device.sh('settings get global airplane_mode_on').strip()
        if airplane != '1':
            device.sh('cmd connectivity airplane-mode enable')
            restore.append('airplane-off')
        analytics = device.sh('getprop debug.firebase.analytics.app').strip() or '.none.'
        device.sh(f'setprop debug.firebase.analytics.app {package}')
        restore.append(f'analytics:{analytics}')
        # Its own next command restarts the helper, so stopping it only frees `uiautomator dump`.
        device.sh(f'am force-stop {SNAPSHOT_HELPER}', check=False)
        upgrade = UpgradeRun(device, old, new, package, args.out)
        installed.append(f'uninstall:{package}')
        results: list[dict] = []
        not_run: dict[str, str] = {}
        for name in args.scenario or list(SCENARIOS):
            try:
                results.append(upgrade.scenario(name, SCENARIOS[name]))
            except Exception:
                # A failure already found stays the verdict; the scenarios left are listed as not run.
                if all(r['passed'] for r in results):
                    raise
                not_run[name] = traceback.format_exc().rstrip()
    except Exception as error:
        detail = str(error) if isinstance(error, SetupError) else traceback.format_exc()
        print(f'upgrade check could not run: {detail}', file=sys.stderr)
        (args.out / 'setup-error.txt').write_text(detail.rstrip() + '\n')
        return 2
    finally:
        restore_device(device, installed + list(reversed(restore)))
    report = dict(checked_at=dt.datetime.now().astimezone().isoformat(timespec='seconds'), builds=builds, results=results,
                  not_run=not_run, passed=not not_run and all(r['passed'] for r in results))
    (args.out / 'report.json').write_text(json.dumps(report, indent=1, ensure_ascii=False) + '\n')
    print(f'{builds["old"]["version_name"]} ({builds["old"]["version_code"]}) -> '
          f'{builds["new"]["version_name"]} ({builds["new"]["version_code"]}), {package}')
    for r in results:
        print(f'{"PASS" if r["passed"] else "FAIL"} {r["scenario"]}: {len(r["after"]["db"].get("rows", {}))} rows, '
              f'{r["pack_files"]} pack files, settings {sorted(r["after"]["prefs"])}')
        for problem in r['problems']:
            print(f'  - {problem}')
    for name, detail in not_run.items():
        print(f'NOT RUN {name}: {detail.splitlines()[-1]}')
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
