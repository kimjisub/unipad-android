#!/usr/bin/env python3
"""Observe release telemetry on one explicitly rented, rooted Android emulator.

No crash is triggered and no data-safety judgement is made. Unknown event values
are retained. The caller rents/returns the device; screen actions use its UI entry.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shlex
import signal
import sys
import subprocess
import time

from release_store_check import (Adb, CheckFailed, Screen, Node, device_facts, find, open_play_screen,
                                 push_fixture_pack, verify_installed, verify_rental)

PACKAGE = 'com.kimjisub.launchpad'
DEBUG_PROP = 'debug.firebase.analytics.app'
LOG_PROP = 'log.tag.FA-SVC'
PACK = 'telemetry-observer'


def split_values(text):
    """Split only top-level commas: nested/future Bundle values remain intact."""
    start, depth = 0, 0
    for index, char in enumerate(text):
        # Bundle.toString() does not quote or escape string values.
        if char in '[{(':
            depth += 1
        elif char in ']})':
            depth = max(0, depth - 1)
        elif char == ',' and depth == 0:
            yield text[start:index].strip()
            start = index + 1
    yield text[start:].strip()


def brace_blocks(text, label):
    """Read balanced blocks in Analytics' upload protocol text."""
    for match in re.finditer(r'\b' + label + r'\s*\{', text):
        depth = 1
        for index in range(match.end(), len(text)):
            depth += (text[index] == '{') - (text[index] == '}')
            if depth == 0:
                yield text[match.end():index], text[match.start():index + 1]
                break


def event_record(name, parameters, raw):
    debug = next((value for key, value in parameters.items() if key == '_dbg' or key.endswith('(_dbg)')), None)
    return dict(name=name, parameters=parameters, debug=None if debug is None else debug == '1', raw=raw)


def parse_events(log):
    """Keep all complete logged events and their uncoerced values, including aliases."""
    events = []
    for line in log.splitlines():
        match = re.search(r'Logging event: origin=[^,]+,name=([^,]+),params=Bundle\[\{(.*)\}\]', line)
        if not match:
            match = re.search(r'Logging event \(FE\): ([^,]+), Bundle\[\{(.*)\}\]', line)
        if match:
            parameters = dict(part.split('=', 1) for part in split_values(match[2]) if '=' in part)
            events.append(event_record(match[1], parameters, line))
    # logcat prefixes each line of a multi-line upload. Remove just that prefix.
    plain = re.sub(r'^.*?\b(?:FA-SVC|FA)\s*:\s?', '', log, flags=re.M)
    for body, raw in brace_blocks(plain, 'event'):
        name = re.search(r'^\s*name:\s*(.+)', body, re.M)
        if not name:
            continue
        parameters = {}
        for param, _ in brace_blocks(body, r'params?'):
            key = re.search(r'^\s*name:\s*(.+)', param, re.M)
            value = re.search(r'^\s*(?:string|int|double|float)_value:\s*(.*)', param, re.M)
            if key:
                parameters[key[1].strip()] = value[1].strip() if value else param.strip()
        events.append(event_record(name[1].strip(), parameters, raw))
    return events


def parse_session(keys, userlog):
    """Crashlytics userlog is a binary queue; extract only complete lifecycle lines."""
    try:
        values = json.loads(keys)
    except ValueError as error:
        raise CheckFailed(f'Crashlytics keys are not JSON: {error}') from error
    if not isinstance(values, dict):
        raise CheckFailed('Crashlytics keys are not an object')
    text = userlog.decode('utf-8', errors='replace')
    transitions = re.findall(r'\d+ [A-Za-z_][\w.$]* (?:resumed|paused|destroyed)(?=\r?\n|$)', text)
    return dict(keys=values, screen=values.get('screen'), transitions=transitions)


def first_pad(root):
    """Locate the first pad even when snapshots omit individual pad children.

    Identify the square grid by its adjacent one-cell-wide chain strip, then
    derive cell (1, 1) from those measured bounds for the fixed 8x8 fixture.
    Pad and chain children share touchSpace IDs, so always use grid geometry.
    No device resolution, obfuscated class name or fixed screen point is used.
    """
    if find(root, identifier='play_logo') is None:
        return None
    for parent in root.walk():
        for grid in parent.children:
            left, top, right, bottom = grid.bounds
            width, height = right - left, bottom - top
            if width < 160 or abs(width - height) > 2:
                continue
            for strip in parent.children:
                sl, st, sr, sb = strip.bounds
                if (abs(st - top) <= 2 and abs(sb - bottom) <= 2
                        and abs((sr - sl) * 8 - width) <= 8
                        and (abs(sr - left) <= 2 or abs(sl - right) <= 2)):
                    return Node('', '', True, (left, top, left + width // 8, top + height // 8))
    return None


class Observer:
    def __init__(self, adb, screen, apk, timeout=60):
        self.adb, self.ui, self.apk, self.timeout = adb, screen, apk, timeout
        self.result = dict(package=PACKAGE, status='not_observed', events=[], sessions=[],
                           observer_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())

    def prepare(self):
        self.adb.root()
        self.result['device'] = device_facts(self.adb)
        self.result['apk_sha256'] = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        # Stop an existing installation before replacement; never clear its data.
        self.adb.shell('am', 'force-stop', PACKAGE)
        self.adb.run('install', '-r', str(self.apk), timeout=180)
        verify_installed(self.adb, PACKAGE, self.result['apk_sha256'])
        package = self.adb.shell('dumpsys', 'package', PACKAGE)
        version = re.search(r'versionCode=(\d+).*?\n\s*versionName=(\S+)', package, re.S)
        if not version:
            raise CheckFailed('Candidate did not install as the release package')
        if re.search(r'\bDEBUGGABLE\b', package):
            raise CheckFailed('The installed candidate is debuggable; use a release APK')
        self.result['app'] = dict(version_code=int(version[1]), version_name=version[2])
        self.adb.shell('am', 'force-stop', PACKAGE)
        self.adb.shell('setprop', DEBUG_PROP, PACKAGE)
        self.adb.shell('setprop', LOG_PROP, 'VERBOSE')
        enabled = self.adb.shell('getprop', DEBUG_PROP)
        if enabled != PACKAGE:
            raise CheckFailed('Analytics debug marking could not be enabled')
        self.result['analytics_debug_during'] = enabled
        self.adb.run('logcat', '-c')
        base = f'/data/user/0/{PACKAGE}'
        self.sessions_path = f'{base}/files/.crashlytics.v3/{PACKAGE}/open-sessions'
        self.previous_sessions = set(self.adb.shell('ls', '-1', self.sessions_path, check=False).splitlines())
        self.result['fixture_directory_listing'] = push_fixture_pack(self.adb, PACKAGE, PACK)

    def drive(self):
        pad = open_play_screen(self.ui, PACKAGE, PACK, self.timeout, first_pad, 'A screen pad', 'missing-pad')
        self.result['pad_bounds'] = pad.bounds
        self.ui.tap(pad)
        self.ui.back()

    def collect(self):
        deadline = time.monotonic() + self.timeout
        while True:
            log = self.adb.run('logcat', '-d', '-v', 'threadtime', 'FA:V', 'FA-SVC:V', '*:S').decode(errors='replace')
            self.result['analytics_log'] = log
            self.result['events'] = parse_events(log)
            sessions, read_errors = [], []
            for session in self.adb.shell('ls', '-1', self.sessions_path, check=False).splitlines():
                if session in self.previous_sessions or not re.fullmatch(r'[A-Za-z0-9_-]+', session):
                    continue
                path = f'{self.sessions_path}/{session}'
                try:
                    keys = self.adb.shell('cat', f'{path}/keys')
                    userlog = self.adb.run('exec-out', 'cat', f'{path}/userlog')
                    record = parse_session(keys, userlog)
                    record.update(id=session, raw_keys=keys, userlog_hex=userlog.hex())
                    sessions.append(record)
                except (CheckFailed, ValueError) as error:
                    read_errors.append(f'{session}: {error}')
            self.result['sessions'], self.result['session_read_errors'] = sessions, read_errors
            first = [event for event in self.result['events'] if event['name'] == 'play_first_input']
            # Presence, not a whitelist of expected values. Unknown values remain evidence.
            complete = (any('input_source' in event['parameters'] for event in first)
                        and any(session['screen'] is not None and session['transitions'] for session in sessions))
            debug = [event['debug'] for event in first if event['debug'] is not None]
            self.result['first_input_debug'] = any(debug) if debug else None
            if complete and (debug or time.monotonic() >= deadline):
                return
            if time.monotonic() >= deadline:
                raise CheckFailed('Not observed before timeout: first input with input_source and/or screen key with lifecycle lines')
            time.sleep(1)


def run_and_record(observer, output):
    result, adb = observer.result, observer.adb
    result['started_at'] = datetime.now(timezone.utc).isoformat()
    cleanup = result['cleanup'] = dict(errors=[])
    old_tag = None
    try:
        old_tag = adb.shell('getprop', LOG_PROP)
        observer.prepare()
        observer.drive()
        observer.collect()
        result['status'] = 'observed'
    except (Exception, KeyboardInterrupt) as error:
        result['error'] = f'{type(error).__name__}: {error}'
    finally:
        # Each operation is independent, so a failed reset cannot skip the next.
        try:
            adb.shell('am', 'force-stop', PACKAGE)
        except Exception as error:
            cleanup['errors'].append(f'Stop app: {type(error).__name__}: {error}')
        for prop, value in ((DEBUG_PROP, '.none.'), (LOG_PROP, old_tag)):
            if value is None:
                continue
            try:
                adb.shell('setprop', prop, shlex.quote(value))
            except Exception as error:
                cleanup['errors'].append(f'{prop}: {type(error).__name__}: {error}')
        for prop, key, expected in ((DEBUG_PROP, 'analytics_debug', '.none.'), (LOG_PROP, 'log_tag', old_tag)):
            try:
                cleanup[key] = adb.shell('getprop', prop)
                if cleanup[key] != expected:
                    cleanup['errors'].append(f'{prop}: read back {cleanup[key]!r}, expected {expected!r}')
            except Exception as error:
                cleanup['errors'].append(f'{prop}: {type(error).__name__}: {error}')
        try:
            # Screen.close() is best-effort; this report must retain close failures.
            observer.ui.ui('close')
        except Exception as error:
            cleanup['errors'].append(f'UI close: {type(error).__name__}: {error}')
        result['finished_at'] = datetime.now(timezone.utc).isoformat()
        result['exit_code'] = 0 if result['status'] == 'observed' and not cleanup['errors'] else 1
        output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
    return result['exit_code']


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--serial', required=True, help='Device id printed by the rental tool')
    parser.add_argument('--generation', required=True, help='Generation printed by the rental lease command')
    parser.add_argument('--devices-tool', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path, help='Output JSON file; use a new filename for each round')
    parser.add_argument('--timeout', type=float, default=60)
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args(argv)
    if not args.apk.is_file() or not args.devices_tool.is_file() or args.timeout <= 0:
        parser.error('An existing APK, device tool and positive timeout are required')
    if args.out.exists():
        parser.error('Output already exists; archive the previous round before running again')
    try:
        verify_rental(args.devices_tool, args.serial, args.generation)
    except CheckFailed as error:
        parser.error(str(error))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    observer = Observer(Adb(args.adb, args.serial),
                        Screen(args.devices_tool, args.serial, args.generation, args.out.parent), args.apk, args.timeout)
    observer.result['serial'] = args.serial
    # SIGTERM also takes the ordinary cleanup path when the caller cancels a run.
    def interrupted(signum, frame):
        raise KeyboardInterrupt(f'Signal {signum}')
    signal.signal(signal.SIGTERM, interrupted)
    code = run_and_record(observer, args.out)
    print(f'{observer.result["status"]}: {args.out}')
    return code


if __name__ == '__main__':
    sys.exit(main())
