#!/usr/bin/env python3
"""Check that a release build keeps working when Firebase never starts.

Some devices disable or skip Firebase's start-up provider (`FirebaseInitProvider`), and
the app then runs without a default FirebaseApp. On one rented, rooted emulator this check:

1. installs the candidate on a clean slate with the small audio-output test pack, disables
   the provider with `pm disable` and sets the app language to English;
2. opens the app and walks main screen -> Store (which must say it cannot reach the
   server) -> Back -> Settings -> Back -> the test pack's play screen -> Back;
3. requires that Firebase did not start, the app is still running and the crash and event
   logs hold no crash or ANR of the app (if the walk stops early, a crash or ANR still fails
   the app, but an app no longer running does so only once the main screen had appeared:
   before that, a screen tool failure or an interruption leaves the app closed and the check
   could not run);
4. enables the provider again, reopens the app and requires Firebase and Crashlytics to
   start (`FirebaseApp initialization successful`, `Initializing Firebase Crashlytics`).

Whatever happens, the provider is enabled again, the app language restored, adb root undone
if the check turned it on, and the screen session closed; each of these steps runs even if an earlier one fails. The app stays
installed. Screen actions use the rental's UI entry; adb only prepares and inspects.

Exit status: 0 passed; 1 the app failed a check; 2 the check could not run or could not
clean up after itself (the verdict, if one was reached, is in result.json).
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shlex
import signal
import subprocess
import sys
import tempfile
import time
import zipfile

from release_store_check import (DOWNLOAD_NEW_PACKS, RELEASE_PACKAGE, SCREEN_TIMEOUT, STORE_BUTTON_DESCRIPTION, Adb,
                                 CheckFailed, Screen, ScreenTimeout, app_locales, device_facts, find, open_play_screen,
                                 push_fixture_pack, verify_installed, verify_rental)
from upgrade_check import SetupError, app_events, universal_apk

PROVIDER = 'com.google.firebase.provider.FirebaseInitProvider'
PACK = 'firebase-off-check'
# English app strings (app/src/main/res/values/strings.xml).
UNABLE_TO_ACCESS_SERVER = 'Unable to access server.'
SETTING = 'Setting'
FIREBASE_STARTED = 'FirebaseInitProvider: FirebaseApp initialization successful'
CRASHLYTICS_STARTED = 'Initializing Firebase Crashlytics'
STORE_TIMEOUT = 30
FIREBASE_LOG_TIMEOUT = 30
MIN_SDK = 33  # per-app language


class AppFailed(CheckFailed):
    """The app itself did something wrong; the check ran as intended."""


def main_entry(root):
    return find(root, text=STORE_BUTTON_DESCRIPTION) or find(root, text=DOWNLOAD_NEW_PACKS)


def disabled_components(dumpsys_package: str) -> set[str]:
    """Component class names listed under `disabledComponents:` in `dumpsys package`."""
    lines = dumpsys_package.splitlines()
    for index, line in enumerate(lines):
        if line.strip() == 'disabledComponents:':
            indent, found = len(line) - len(line.lstrip()), set()
            for entry in lines[index + 1:]:
                if not entry.strip() or len(entry) - len(entry.lstrip()) <= indent:
                    break
                found.add(entry.strip())
            return found
    return set()


def firebase_lines(log: str) -> dict[str, bool]:
    return {FIREBASE_STARTED: FIREBASE_STARTED in log, CRASHLYTICS_STARTED: CRASHLYTICS_STARTED in log}


class FirebaseOffCheck:
    def __init__(self, adb, screen, apk: Path, out: Path, package=RELEASE_PACKAGE):
        self.adb, self.screen, self.apk, self.out, self.package = adb, screen, apk, out, package
        self.component = f'{package}/{PROVIDER}'
        self.result = dict(package=package, verdict=None, steps=[], failures=[], cleanup_errors=[],
                           checker_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        self.locales = None
        self.was_root = None
        self.app_opened = False

    def step(self, name, detail=''):
        self.result['steps'].append(dict(step=name, detail=detail, at=datetime.now(timezone.utc).isoformat()))
        print(f'{name}: {detail}' if detail else name)

    def fail(self, reason):
        self.result['failures'].append(reason)
        print(f'FAIL {reason}', file=sys.stderr)

    # --- preparation (a problem here is the check's, exit 2) ---

    def prepare(self):
        self.was_root = self.adb.shell('id', '-u') == '0'
        self.adb.root()
        facts = self.result['device'] = device_facts(self.adb)
        if not facts['api'].isdigit() or int(facts['api']) < MIN_SDK:
            raise SetupError(f'API {facts["api"]}: this check needs API {MIN_SDK}+ (per-app language)')
        sha = self.result['apk_sha256'] = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.adb.run('uninstall', self.package, check=False, timeout=120)
        self.adb.run('install', str(self.apk), timeout=300)
        verify_installed(self.adb, self.package, sha)
        package = self.adb.shell('dumpsys', 'package', self.package)
        version = re.search(r'versionCode=(\d+).*?\n\s*versionName=(\S+)', package, re.S)
        flags = re.search(r'pkgFlags=\[([^\]]*)\]', package)
        if not version:
            raise SetupError(f'{self.apk} did not install as {self.package}')
        if flags and 'DEBUGGABLE' in flags[1]:
            raise SetupError(f'{self.apk} is a debuggable build; this check is for release builds')
        self.result['app'] = dict(version_code=int(version[1]), version_name=version[2])
        self.adb.shell('am', 'force-stop', self.package)
        self.result['fixture_listing'] = push_fixture_pack(self.adb, self.package, PACK)
        self.adb.shell('pm', 'disable', self.component)
        if PROVIDER not in disabled_components(self.adb.shell('dumpsys', 'package', self.package)):
            raise SetupError(f'{self.component} is not listed as disabled after pm disable')
        self.locales = app_locales(self.adb.shell('cmd', 'locale', 'get-app-locales', self.package))
        self.adb.shell('cmd', 'locale', 'set-app-locales', self.package, '--locales', 'en-US')
        self.adb.run('logcat', '-b', 'all', '-c')
        self.started_at = self.device_time()
        self.step('prepared', f'{self.package} {version[2]} ({version[1]}), {PROVIDER} disabled, '
                              f'API {facts["api"]} {facts["model"]}')

    def device_time(self):
        return self.adb.shell('date', "+'%m-%d %H:%M:%S.000'")

    # --- the walk with Firebase off (a wrong screen or a crash is the app's, exit 1) ---

    def top_activity(self):
        lines = self.adb.shell('dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity"', check=False)
        match = re.search(r'\{[^}]*? (\S+/\S+)', lines)
        return match[1] if match else lines

    def wait_activity(self, name, timeout=SCREEN_TIMEOUT):
        deadline = time.monotonic() + timeout
        while True:
            top = self.top_activity()
            if top.endswith(name):
                return top
            if time.monotonic() >= deadline:
                raise AppFailed(f'{name} was not on top within {timeout} s (top: {top})')
            time.sleep(0.5)

    def back_to_main(self, label):
        self.screen.back()
        # The Store's own title also reads "Store", so the main screen is told by its activity first.
        self.wait_activity('.activity.MainActivity')
        self.screen.settled(main_entry, SCREEN_TIMEOUT, f'The main screen after {label}', f'main-after-{label}')

    def walk(self):
        self.adb.shell('am', 'force-stop', self.package)
        self.screen.launch(self.package)
        store = self.screen.settled(main_entry, SCREEN_TIMEOUT, "The main screen's Store entry", 'main-screen')
        self.app_opened = True
        self.screen.capture('1-main')
        self.step('main screen')
        self.screen.tap(store)
        self.wait_activity('.activity.FBStoreActivity')
        self.screen.wait(lambda root: find(root, text=UNABLE_TO_ACCESS_SERVER), STORE_TIMEOUT,
                         f'The Store message "{UNABLE_TO_ACCESS_SERVER}"', 'store-message')
        self.screen.capture('2-store')
        self.step('store', UNABLE_TO_ACCESS_SERVER)
        self.back_to_main('store')
        settings = self.screen.settled(lambda root: find(root, text=SETTING), SCREEN_TIMEOUT, 'The Settings button',
                                       'settings-button')
        self.screen.tap(settings)
        self.wait_activity('.activity.SettingsActivity')
        self.screen.capture('3-settings')
        self.step('settings')
        self.back_to_main('settings')
        open_play_screen(self.screen, self.package, PACK, SCREEN_TIMEOUT)
        self.screen.capture('4-play')
        self.step('play screen')
        self.screen.back()

    def judge_disabled_run(self):
        time.sleep(3)
        log = self.adb.run('logcat', '-d', '-v', 'threadtime').decode(errors='replace')
        crashes = self.adb.run('logcat', '-d', '-b', 'crash', '-b', 'events', '-T', self.started_at).decode(errors='replace')
        (self.out / 'disabled-logcat.txt').write_text(log)
        (self.out / 'disabled-crash-events.txt').write_text(crashes)
        events = app_events(crashes, self.package)
        self.result['disabled_run'] = dict(firebase=firebase_lines(log), crash_or_anr=events, opened=self.app_opened,
                                           alive=bool(self.adb.shell('pidof', self.package, check=False)))
        if self.result['disabled_run']['firebase'][FIREBASE_STARTED]:
            raise SetupError('Firebase started although its provider was disabled; the check did not run as intended')
        for line in events:
            self.fail(f'crash or ANR with Firebase off: {line}')
        # The walk stops the app before opening it, so an app that never showed the main screen is not running anyway.
        if self.app_opened and not self.result['disabled_run']['alive'] and not events:
            self.fail(f'{self.package} is no longer running after the walk with Firebase off')

    # --- Firebase back on ---

    def enable_again(self):
        self.adb.shell('am', 'force-stop', self.package)
        self.adb.shell('pm', 'enable', self.component)
        if PROVIDER in disabled_components(self.adb.shell('dumpsys', 'package', self.package)):
            raise SetupError(f'{self.component} is still disabled after pm enable')

    def reenabled_run(self):
        self.enable_again()
        self.adb.run('logcat', '-b', 'all', '-c')
        started = self.device_time()
        self.screen.launch(self.package)
        self.screen.settled(main_entry, SCREEN_TIMEOUT, 'The main screen with Firebase on', 'main-reenabled')
        self.screen.capture('5-main-reenabled')
        deadline = time.monotonic() + FIREBASE_LOG_TIMEOUT
        while True:
            log = self.adb.run('logcat', '-d', '-v', 'threadtime').decode(errors='replace')
            found = firebase_lines(log)
            if all(found.values()) or time.monotonic() >= deadline:
                break
            time.sleep(1)
        crashes = self.adb.run('logcat', '-d', '-b', 'crash', '-b', 'events', '-T', started).decode(errors='replace')
        (self.out / 'reenabled-logcat.txt').write_text(log)
        events = app_events(crashes, self.package)
        self.result['reenabled_run'] = dict(firebase=found, crash_or_anr=events)
        for line, seen in found.items():
            if not seen:
                self.fail(f'with the provider enabled again, "{line}" was not logged within {FIREBASE_LOG_TIMEOUT} s')
        for line in events:
            self.fail(f'crash or ANR with Firebase on: {line}')
        self.step('Firebase on again', ', '.join(line for line, seen in found.items() if seen))

    def run(self):
        self.prepare()
        try:
            self.walk()
        except BaseException:
            # A crash explains a screen that never appeared, so read the logs; the walk's error stays the cause
            # unless the logs show the check itself did not run as intended.
            try:
                self.judge_disabled_run()
            except SetupError:
                raise
            except Exception as error:
                self.result['log_read_error'] = f'{type(error).__name__}: {error}'
            raise
        self.judge_disabled_run()
        self.reenabled_run()

    def clean_up(self):
        steps = [('enable provider', lambda: self.adb.shell('pm', 'enable', self.component)),
                 ('stop app', lambda: self.adb.shell('am', 'force-stop', self.package))]
        if self.locales is not None:
            steps.append(('app language', lambda: self.adb.shell(
                'cmd', 'locale', 'set-app-locales', self.package, '--locales', shlex.quote(self.locales))))
        steps.append(('UI session', lambda: self.screen.ui('close')))
        for what, action in steps:
            try:
                action()
            except Exception as error:
                self.result['cleanup_errors'].append(f'{what}: {type(error).__name__}: {error}')
        try:
            if PROVIDER in disabled_components(self.adb.shell('dumpsys', 'package', self.package, check=False)):
                self.result['cleanup_errors'].append(f'{self.component} is still disabled')
        except Exception as error:
            self.result['cleanup_errors'].append(f'read back provider: {type(error).__name__}: {error}')
        if self.was_root is False:
            try:
                self.adb.run('unroot', timeout=30)
                self.adb.run('wait-for-device', timeout=60)
                if self.adb.shell('id', '-u') == '0':
                    self.result['cleanup_errors'].append('adb still runs as root after adb unroot')
            except Exception as error:
                self.result['cleanup_errors'].append(f'adb unroot: {type(error).__name__}: {error}')


def run_and_record(check: FirebaseOffCheck) -> int:
    """Runs the check and writes result.json; an app failure is never replaced by a later setup or cleanup error."""
    result = check.result
    result['started_at'] = datetime.now(timezone.utc).isoformat()
    try:
        check.run()
    except (AppFailed, ScreenTimeout) as error:
        check.fail(f'{type(error).__name__}: {error}')
    except (Exception, KeyboardInterrupt) as error:
        result['error'] = f'{type(error).__name__}: {error}'
        print(f'firebase-off check could not run: {result["error"]}', file=sys.stderr)
    finally:
        check.clean_up()
        if result['failures']:
            result['verdict'] = 'failed'
        elif 'error' not in result:
            result['verdict'] = 'passed'
        result['finished_at'] = datetime.now(timezone.utc).isoformat()
        result['exit_code'] = (1 if result['verdict'] == 'failed'
                               else 2 if 'error' in result or result['cleanup_errors'] else 0)
        (check.out / 'result.json').write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
    for error in result['cleanup_errors']:
        print(f'cleanup failed: {error}', file=sys.stderr)
    print(f'{result["verdict"] or "not judged"}: {check.out / "result.json"}')
    return result['exit_code']


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--apk', required=True, type=Path, help='the candidate: .aab, universal .apks or .apk')
    parser.add_argument('--serial', required=True, help='device id printed by the rental tool')
    parser.add_argument('--generation', required=True, help='generation printed by `devices.py lease <device>`')
    parser.add_argument('--devices-tool', required=True, type=Path, help='the device rental tool, devices.py')
    parser.add_argument('--out', required=True, type=Path, help='new folder for result.json, logs and screenshots')
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args(argv)
    if not args.apk.is_file() or not args.devices_tool.is_file():
        parser.error('--apk and --devices-tool must be existing files')
    if args.out.exists() and any(args.out.iterdir()):
        parser.error(f'{args.out} is not empty; give each run its own folder')
    try:
        verify_rental(args.devices_tool, args.serial, args.generation)
    except CheckFailed as error:
        parser.error(str(error))
    args.out = args.out.resolve()
    args.out.mkdir(parents=True, exist_ok=True)

    def interrupted(signum, frame):
        raise KeyboardInterrupt(f'Signal {signum}')
    signal.signal(signal.SIGTERM, interrupted)
    with tempfile.TemporaryDirectory() as work:
        try:
            apk = universal_apk(args.apk.resolve(), Path(work))
        except (SetupError, subprocess.SubprocessError, OSError, zipfile.BadZipFile) as error:
            print(f'firebase-off check could not run: {error}', file=sys.stderr)
            return 2
        screen = Screen(args.devices_tool.resolve(), args.serial, args.generation, args.out)
        return run_and_record(FirebaseOffCheck(Adb(args.adb, args.serial), screen, apk, args.out))


if __name__ == '__main__':
    sys.exit(main())
