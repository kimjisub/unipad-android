#!/usr/bin/env python3
"""Check that a native crash in the background reaches Crashlytics with the keys that explain it.

One rented, rooted Android 11+ emulator; a debug app built with -PcrashReportCheck (package
com.kimjisub.launchpad.dev, never the store app). The app is installed fresh, plays a small test
pack, goes to the background and is crashed there with a native signal tagged with a check id.
It is opened twice so it sends the native crash report and its own system exit report, then
Crashlytics is read until both reports of this check are there or the time limit passes.
See CRASH_REPORT_CHECK.md.
"""
import argparse
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import select
import shlex
import signal
import subprocess
import sys
import time
import uuid

from release_store_check import (Adb, CheckFailed, Screen, ScreenTimeout, device_facts, find, open_play_screen,
                                 push_fixture_pack, verify_installed, verify_rental)
from upgrade_check import build_tool

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.kimjisub.launchpad.dev'
RECEIVER = 'com.kimjisub.launchpad.tool.NativeCrashCheckReceiver'
PACK = 'crash-report-check'
PLAY_SCREEN = 'PlayActivity'
CRASH_NATIVE = '5'
PLAY_KEYS = ('play_window_mode', 'play_orientation', 'play_screen_short_side')
INSTALL_KEYS = ('install_source', 'install_splits')
DEBUG_APK = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
# The check id travels through a device shell and into report keys, so it is kept to plain characters.
CHECK_ID = re.compile(r'[A-Za-z0-9._-]{1,80}')

PASSED, FAILED, NOT_CHECKED = 'passed', 'failed', 'not_checked'
EXIT_CODES = {PASSED: 0, FAILED: 1, NOT_CHECKED: 2}
CLEANUP_FAILED = 3


class NotChecked(Exception):
    """The check could not be made (build, device, tools or server reading), which says nothing about the app."""


class AppFailed(Exception):
    """The app did not do its part on the device: a screen did not appear, it did not crash or record the exit."""


# Crashlytics (server) --------------------------------------------------------------------------------------------

def crashlytics_app_id(google_services, package):
    """The Firebase app id of `package`; only the debug package may be read or written by this check."""
    if package != PACKAGE:
        raise NotChecked(f'{package} is not the debug package {PACKAGE}')
    for client in json.loads(Path(google_services).read_text()).get('client', []):
        info = client.get('client_info', {})
        if info.get('android_client_info', {}).get('package_name') == package:
            return info['mobilesdk_app_id']
    raise NotChecked(f'{google_services} has no Firebase app for {package}')


class FirebaseMcp:
    """Crashlytics through the Firebase CLI's MCP server, with the CLI's own login (`firebase login`).

    A CLI that ends, stops answering or stops taking input is stopped and the call is `NotChecked`; the next
    call starts a new one.
    """
    def __init__(self, firebase, directory, log, timeout=120):
        self.command = [firebase, 'mcp', '--only', 'crashlytics', '--dir', str(directory)]
        self.timeout = timeout
        self.process, self.buffer, self.next_id, self.starts = None, b'', 1, 0
        self.log = open(log, 'ab')

    def _start(self):
        self.starts += 1
        try:
            self.process = subprocess.Popen(self.command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                            stderr=self.log)
        except OSError as error:
            raise NotChecked(f'Cannot start the Firebase CLI: {error}')
        self.buffer = b''
        self._request('initialize', {'protocolVersion': '2024-11-05', 'capabilities': {},
                                     'clientInfo': {'name': 'crash-report-check', 'version': '1'}})
        self._send({'jsonrpc': '2.0', 'method': 'notifications/initialized'})

    def _send(self, message):
        try:
            self.process.stdin.write((json.dumps(message) + '\n').encode())
            self.process.stdin.flush()
        except (OSError, ValueError) as error:
            raise NotChecked(f'Firebase CLI stopped taking requests ({type(error).__name__}); see firebase-mcp.log')

    def _request(self, method, params):
        self.next_id += 1
        self._send({'jsonrpc': '2.0', 'id': self.next_id, 'method': method, 'params': params})
        deadline = time.monotonic() + self.timeout
        fd = self.process.stdout.fileno()
        while True:
            while b'\n' in self.buffer:
                line, self.buffer = self.buffer.split(b'\n', 1)
                try:
                    message = json.loads(line)
                except ValueError:
                    continue
                if isinstance(message, dict) and message.get('id') == self.next_id:
                    return message
            remaining = deadline - time.monotonic()
            if remaining <= 0 or not select.select([fd], [], [], remaining)[0]:
                raise NotChecked(f'Firebase CLI did not answer {method} within {self.timeout} s')
            chunk = os.read(fd, 65536)
            if not chunk:
                raise NotChecked('Firebase CLI exited; see firebase-mcp.log')
            self.buffer += chunk

    def call(self, tool, arguments):
        try:
            if self.process is None or self.process.poll() is not None:
                self._stop()
                self._start()
            answer = self._request('tools/call', {'name': tool, 'arguments': arguments})
        except NotChecked:
            self._stop()
            raise
        result = answer.get('result') if isinstance(answer.get('result'), dict) else {}
        text = '\n'.join(part.get('text', '') for part in result.get('content', [])
                         if isinstance(part, dict) and part.get('type') == 'text')
        if answer.get('error') or result.get('isError') or not text.strip():
            raise NotChecked(f'{tool}: {text.strip() or answer.get("error") or "no answer"}'[:600])
        return text

    def _stop(self):
        process, self.process = self.process, None
        if process is None:
            return
        try:
            process.terminate()
            process.wait(timeout=10)
        except (subprocess.TimeoutExpired, OSError):
            process.kill()
            process.wait(timeout=10)
        for stream in (process.stdin, process.stdout):
            try:
                stream.close()
            except OSError:
                pass

    def close(self):
        try:
            self._stop()
        finally:
            self.log.close()


def _scalar(value):
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] == "'":
        return value[1:-1].replace("''", "'")
    if len(value) >= 2 and value[0] == value[-1] == '"':
        try:
            return json.loads(value)
        except ValueError:
            return value[1:-1]
    return value


def _mapping(lines):
    """`key: value` lines of one level; folded continuation lines join their key's value."""
    values, key = {}, None
    for line in lines:
        match = re.match(r'^([A-Za-z_][\w.]*):\s?(.*)$', line)
        if match:
            key = match[1]
            values[key] = '' if match[2].strip() in ('>-', '>', '|', '|-') else _scalar(match[2])
        elif key and line.strip():
            values[key] = (values[key] + ' ' + line.strip()).strip()
    return values


def parse_issue_ids(report):
    """Issue ids in a topIssues report, in report order."""
    return list(dict.fromkeys(re.findall(r'^\s+issue:\s*\n\s+id:\s*(\S+)', report, re.M)))


def parse_events(text):
    """Events of `crashlytics_list_events`: each `- name:` item and the fields this check reads."""
    events = []
    for item in re.split(r'^(?=- name:)', text, flags=re.M):
        if not item.startswith('- name:'):
            continue
        fields, field = {}, None
        for line in ('  ' + item[2:]).splitlines():
            match = re.match(r'^  (\w+):\s?(.*)$', line)
            if match:
                field = match[1]
                fields[field] = [] if match[2].strip() in ('|', '|-', '>-', '>') else [match[2]]
            elif field is not None and line.startswith('    '):
                fields[field].append(line[4:])
        first = lambda name: _scalar(next((line for line in fields.get(name, []) if line.strip()), ''))
        issue = _mapping(fields.get('issue', []))
        events.append(dict(
            name=first('name'), event_time=first('eventTime'), received_time=first('receivedTime'),
            installation=first('installationUuid'), issue_id=issue.get('id'), title=issue.get('title'),
            subtitle=issue.get('subtitle'), error_type=issue.get('errorType'),
            version=_mapping(fields.get('version', [])).get('displayName'),
            keys=_mapping(fields.get('customKeys', [])), raw=item.rstrip()))
    return events


def judge(events, check_id, version, crash_requested_ms):
    """Find this check's two reports and list every expected value they lack."""
    native = next((e for e in events if e['keys'].get('check_id') == check_id and e['error_type'] == 'FATAL'), None)
    exit_report = None
    problems = []
    # The system exit report carries no check id: only the installation of this run's crash ties it to the run.
    if native and not native['installation']:
        problems.append('native crash: no installationUuid, so its system exit report cannot be told apart')
    elif native:
        exit_report = next((e for e in events if e is not native and e['installation'] == native['installation']
                            and e['keys'].get('exit_reason') == CRASH_NATIVE), None)
    if native:
        keys = native['keys']
        if keys.get('test_crash') != 'true':
            problems.append(f'native crash: test_crash is {keys.get("test_crash")!r}, expected true')
        if keys.get('screen') != PLAY_SCREEN:
            problems.append(f'native crash: screen is {keys.get("screen")!r}, expected {PLAY_SCREEN}')
        problems += [f'native crash: no {key}' for key in PLAY_KEYS + INSTALL_KEYS if not keys.get(key)]
        if native['version'] != version:
            problems.append(f'native crash: version {native["version"]!r}, expected {version!r}')
    if exit_report:
        keys = exit_report['keys']
        if exit_report['error_type'] != 'NON_FATAL':
            problems.append(f'system exit report: type {exit_report["error_type"]!r}, expected NON_FATAL')
        if keys.get('harness_test') != 'true':
            problems.append(f'system exit report: harness_test is {keys.get("harness_test")!r}, expected true')
        if keys.get('exit_last_screen') != PLAY_SCREEN:
            problems.append(f'system exit report: exit_last_screen is {keys.get("exit_last_screen")!r}, '
                            f'expected {PLAY_SCREEN}')
        if keys.get('exit_pack_open') != 'true':
            problems.append(f'system exit report: exit_pack_open is {keys.get("exit_pack_open")!r}, expected true')
        background = keys.get('exit_background_ms', '')
        if not background.isdigit() or int(background) <= 0:
            problems.append(f'system exit report: exit_background_ms is {background!r}, expected a positive number')
        stamp = keys.get('exit_timestamp', '')
        if not stamp.isdigit() or int(stamp) < crash_requested_ms:
            problems.append(f'system exit report: exit_timestamp {stamp!r} is not after the crash was requested '
                            f'({crash_requested_ms})')
        problems += [f'system exit report: no {key}' for key in INSTALL_KEYS if not keys.get(key)]
        if exit_report['version'] != version:
            problems.append(f'system exit report: version {exit_report["version"]!r}, expected {version!r}')
    # Settled: nothing a later read can add, either because both are there or because they cannot be paired.
    settled = bool(native and (exit_report or not native['installation']))
    return dict(native=native, exit=exit_report, problems=problems, settled=settled)


def iso(moment):
    return moment.strftime('%Y-%m-%dT%H:%M:%SZ')


def read_check_events(mcp, app_id, since):
    """Every event of the debug app since `since`: Crashlytics lists events only per issue."""
    window = {'intervalStartTime': iso(since), 'intervalEndTime': iso(datetime.now(timezone.utc))}
    report = mcp.call('crashlytics_get_report', {'appId': app_id, 'report': 'topIssues', 'pageSize': 50,
                                                 'filter': window})
    events = []
    for issue_id in parse_issue_ids(report):
        text = mcp.call('crashlytics_list_events', {'appId': app_id, 'pageSize': 50,
                                                    'filter': dict(window, issueId=issue_id)})
        issue_events = parse_events(text)
        if not issue_events:
            # An issue listed in the window has events; none read means the answer's format is not understood.
            raise NotChecked(f'No events could be read for issue {issue_id}: {text.strip()[:300]!r}')
        events += issue_events
    return events


def wait_for_reports(read, check_id, version, crash_requested_ms, timeout, interval, polls, sleep=time.sleep,
                     clock=time.monotonic):
    """Read until both reports are there or `timeout` passes; reading errors are retried at the next poll.

    Each read is appended to `polls` as it happens, so an interruption keeps the reads made until then.
    """
    begin = clock()
    found = dict(native=None, exit=None, problems=[], settled=False)
    while True:
        at = round(clock() - begin)
        try:
            events = read()
            found = judge(events, check_id, version, crash_requested_ms)
            polls.append(dict(seconds=at, events=len(events), native=bool(found['native']), exit=bool(found['exit'])))
        except NotChecked as error:
            polls.append(dict(seconds=at, error=str(error)))
        if found['settled'] or clock() + interval > begin + timeout:
            return found
        sleep(interval)


def server_verdict(found, polls, timeout):
    """Missing reports fail only when the last read worked: a failed read says nothing about the app."""
    missing = [name for name, event in (('native crash report', found['native']),
                                        ('system exit report', found['exit'])) if not event]
    if missing and (not polls or 'error' in polls[-1]):
        last = polls[-1]['error'] if polls else 'no read'
        return NOT_CHECKED, f'Crashlytics could not be read at the end ({last}); missing then: {", ".join(missing)}'
    if missing:
        return FAILED, '; '.join([f'Not on the server {timeout:.0f} s after reopening: {", ".join(missing)}']
                                 + found['problems'])
    if found['problems']:
        return FAILED, '; '.join(found['problems'])
    return PASSED, 'both reports arrived with the expected values'


def check_server(result, read, out, timeout, interval, sleep=time.sleep, clock=time.monotonic):
    """Wait for this run's reports and keep what the server returned; `result` holds every read as it happens."""
    polls = result['server_polls'] = []
    found = wait_for_reports(read, result['check_id'], result['apk']['version'], result['crash_requested_ms'],
                             timeout, interval, polls, sleep, clock)
    result['server'] = {kind: event and {k: v for k, v in event.items() if k != 'raw'}
                        for kind, event in (('native_crash', found['native']), ('system_exit', found['exit']))}
    for kind, event in (('native', found['native']), ('exit', found['exit'])):
        if event:
            (out / f'server-{kind}.txt').write_text(event['raw'] + '\n')
    result['server_problems'] = found['problems']
    return server_verdict(found, polls, timeout)


def exit_code(verdict, cleanup_errors):
    """A cleanup failure never replaces the verdict, but a passed run that left the device unclean is not 0."""
    return CLEANUP_FAILED if verdict == PASSED and cleanup_errors else EXIT_CODES[verdict]


# Device ----------------------------------------------------------------------------------------------------------

def apk_facts(apk):
    """Package, version and whether crash reports are switched on, from the APK itself."""
    aapt2 = build_tool('aapt2')
    badging = subprocess.run([aapt2, 'dump', 'badging', str(apk)], capture_output=True, text=True, timeout=120)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging.stdout)
    if not match:
        raise NotChecked(f'{apk}: cannot read the package and version')
    manifest = subprocess.run([aapt2, 'dump', 'xmltree', '--file', 'AndroidManifest.xml', str(apk)],
                              capture_output=True, text=True, timeout=120).stdout
    return dict(package=match[1], version_code=int(match[2]), version_name=match[3],
                version=f'{match[3]} ({match[2]})', sha256=hashlib.sha256(Path(apk).read_bytes()).hexdigest(),
                crash_reports_on=crash_reports_on(manifest), crash_receiver=RECEIVER in manifest)


def crash_reports_on(manifest):
    match = re.search(r'"firebase_crashlytics_collection_enabled"[^\n]*\n\s*A: [^\n]*:value\([^)]*\)="?(\w+)',
                      manifest)
    return bool(match) and match[1] == 'true'


@contextmanager
def app_must_show():
    """A screen read in time that never showed what was waited for is the app's failure, not the tools'."""
    try:
        yield
    except ScreenTimeout as error:
        raise AppFailed(str(error)) from error


class CrashReportCheck:
    def __init__(self, adb, screen, apk, out, timeout=60, background_seconds=8, launch_seconds=20, crash_seconds=30):
        self.adb, self.screen, self.apk, self.out = adb, screen, apk, out
        self.timeout, self.background_seconds, self.launch_seconds = timeout, background_seconds, launch_seconds
        self.crash_seconds = crash_seconds
        self.result = dict(package=PACKAGE, steps=[], local={})
        self.data = f'/data/user/0/{PACKAGE}'

    def step(self, name, detail):
        self.result['steps'].append(dict(step=name, at=datetime.now(timezone.utc).isoformat(), detail=detail))
        print(f'[{name}] {detail}', flush=True)

    def device_ms(self):
        # toybox date has no sub-second format; whole seconds keep the later comparison safe.
        return int(self.adb.shell('date', '+%s')) * 1000

    def prepare(self):
        """Fresh installation with the test pack. Everything here is preparation, not the app's behaviour."""
        self.adb.root()
        self.result['device'] = device_facts(self.adb)
        sdk = int(self.result['device']['api'])
        if sdk < 30:
            raise NotChecked(f'API {sdk}: the app reports system exits from Android 11 (API 30)')
        # Uninstalling clears a crash collection choice saved by an earlier run and gives a new installation id,
        # which ties the system exit report to this run's crash.
        self.adb.run('uninstall', PACKAGE, check=False)
        self.adb.run('install', str(self.apk), timeout=300)
        verify_installed(self.adb, PACKAGE, self.result['apk']['sha256'])
        self.result['local']['fixture_directory_listing'] = push_fixture_pack(self.adb, PACKAGE, PACK)
        self.adb.run('logcat', '-c')
        self.step('prepare', f'{PACKAGE} installed fresh on API {sdk}; test pack {PACK} added')

    def open_play_screen(self):
        with app_must_show():
            open_play_screen(self.screen, PACKAGE, PACK, self.timeout)
        self.screen.capture('play')
        self.step('play', 'play screen open')

    def app_pid(self):
        """The app's pid, or '' when it is not running. An answer that is neither is a tool error."""
        answer = self.adb.shell('pidof', PACKAGE, '||', 'echo', 'stopped')
        if answer == 'stopped':
            return ''
        if not re.fullmatch(r'\d+( \d+)*', answer):
            raise NotChecked(f'pidof {PACKAGE} answered {answer!r}')
        return answer

    def crash_in_background(self, check_id):
        self.screen.ui('home')
        time.sleep(self.background_seconds)
        self.result['local']['background_record'] = self.adb.shell(
            'cat', f'{self.data}/shared_prefs/system_exit_diagnostics.xml', check=False)
        pid = self.app_pid()
        if not pid:
            raise AppFailed(f'The app was not running {self.background_seconds} s after going to the background')
        self.result['crash_requested_ms'] = self.device_ms()
        # adb joins its arguments into one device shell command line.
        self.adb.shell('am', 'broadcast', '-n', f'{PACKAGE}/{RECEIVER}', '--es', 'check_id', shlex.quote(check_id))
        deadline = time.monotonic() + self.crash_seconds
        while self.app_pid() == pid:
            if time.monotonic() > deadline:
                raise AppFailed(f'The app (pid {pid}) was still running {self.crash_seconds} s after the crash was '
                                f'requested')
            time.sleep(0.5)
        self.result['local']['exit_info'] = self.adb.shell('dumpsys', 'activity', 'exit-info', PACKAGE)[:6000]
        self.step('crash', f'pid {pid} ended after the native crash request ({self.background_seconds} s in the '
                           f'background)')

    def open_and_wait(self, name):
        self.screen.launch(PACKAGE)
        with app_must_show():
            self.screen.wait(lambda root: find(root, identifier=f'main_pack_{PACK}'), self.timeout,
                             'The library after reopening', name)
        time.sleep(self.launch_seconds)

    def send_reports(self):
        """The first launch sends the native crash and records the system exit; the second sends that record."""
        self.open_and_wait('reopen-1')
        log = self.adb.run('logcat', '-d', '-v', 'threadtime').decode(errors='replace')
        recorded = [line for line in log.splitlines() if 'Recorded system exit reason=5' in line]
        if not recorded:
            raise AppFailed(f'The app did not record the native crash exit within {self.launch_seconds} s of '
                              f'reopening')
        self.step('reopen-1', recorded[-1].split('SystemExitReporter', 1)[-1].strip(' :'))
        self.adb.shell('am', 'force-stop', PACKAGE)
        self.open_and_wait('reopen-2')
        self.adb.shell('am', 'force-stop', PACKAGE)
        log = self.adb.run('logcat', '-d', '-v', 'threadtime').decode(errors='replace')
        (self.out / 'logcat.txt').write_text(log)
        self.result['local']['crashlytics_files'] = self.adb.shell(
            'ls', '-R', f'{self.data}/files/.crashlytics.v3/{PACKAGE}', check=False)[:6000]
        self.step('reopen-2', 'opened again to send the system exit report')

    def clean_up(self):
        for what, action in (('stop app', lambda: self.adb.shell('am', 'force-stop', PACKAGE)),
                             ('UI session', lambda: self.screen.ui('close'))):
            try:
                action()
            except Exception as error:
                self.result.setdefault('cleanup_errors', []).append(f'{what}: {type(error).__name__}: {error}')


def run_device(check, check_id):
    """The device part: (None, None) when the app did its part, otherwise its verdict; cleans up whatever happened.

    Only what the app did (`AppFailed`) fails the run. adb, the rental's UI entry or any other tool going wrong
    says nothing about the app and leaves it not checked.
    """
    try:
        check.prepare()
        check.open_play_screen()
        check.crash_in_background(check_id)
        check.send_reports()
        return None, None
    except AppFailed as error:
        return FAILED, str(error)
    except NotChecked as error:
        return NOT_CHECKED, str(error)
    except Exception as error:
        return NOT_CHECKED, f'Device or tool error: {type(error).__name__}: {error}'
    finally:
        check.clean_up()


# Command ---------------------------------------------------------------------------------------------------------

def build_apk(out):
    """Debug app of this checkout with crash reports switched on."""
    commit = subprocess.run(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], capture_output=True, text=True).stdout.strip()
    dirty = bool(subprocess.run(['git', '-C', str(ROOT), 'status', '--porcelain'],
                                capture_output=True, text=True).stdout.strip())
    with open(out / 'build.log', 'wb') as log:
        built = subprocess.run([str(ROOT / 'gradlew'), '--no-daemon', '-PcrashReportCheck', ':app:assembleDebug'],
                               cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=3600)
    if built.returncode:
        raise NotChecked('The debug build failed; see build.log')
    return DEBUG_APK, dict(commit=commit, uncommitted_changes=dirty)


def check_id_argument(value):
    if not CHECK_ID.fullmatch(value):
        raise argparse.ArgumentTypeError('use 1 to 80 letters, digits, ".", "_" or "-"')
    return value


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--apk', type=Path, help='debug APK built with -PcrashReportCheck')
    source.add_argument('--build', action='store_true', help='build this checkout with -PcrashReportCheck first')
    parser.add_argument('--serial', required=True, help='the rented emulator (adb serial)')
    parser.add_argument('--generation', required=True, help="the rental's generation (`devices.py lease DEVICE`)")
    parser.add_argument('--devices-tool', required=True, type=Path, help='the device rental tool, devices.py')
    parser.add_argument('--out', required=True, type=Path, help='new folder for the verdict and evidence')
    parser.add_argument('--check-id', type=check_id_argument,
                        help='tag of this run in the reports (default: crash-check-<time>-<random>)')
    parser.add_argument('--server-timeout', type=float, default=360, help='seconds to wait for both reports')
    parser.add_argument('--poll-seconds', type=float, default=45, help='seconds between Crashlytics reads')
    parser.add_argument('--firebase', default='firebase', help='Firebase CLI, logged in to the project')
    parser.add_argument('--google-services', type=Path, default=ROOT / 'app/google-services.json')
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args(argv)
    if args.out.exists() and any(args.out.iterdir()):
        parser.error('--out already has files; use a new folder for each run')
    if not args.devices_tool.is_file() or args.server_timeout <= 0 or args.poll_seconds <= 0:
        parser.error('An existing device tool and positive time limits are required')
    args.out = args.out.resolve()
    args.out.mkdir(parents=True, exist_ok=True)
    check_id = args.check_id or (f'crash-check-{datetime.now(timezone.utc):%Y%m%dT%H%M%SZ}-'
                                 f'{uuid.uuid4().hex[:6]}')

    def interrupted(signum, frame):
        raise KeyboardInterrupt(f'Signal {signum}')
    signal.signal(signal.SIGTERM, interrupted)

    started = datetime.now(timezone.utc)
    # Crashlytics keeps whole seconds; a minute of margin covers clock differences.
    since = started - timedelta(minutes=1)
    result = dict(check_id=check_id, started_at=started.isoformat())
    verdict, reason, mcp = None, None, None
    try:
        verify_rental(args.devices_tool, args.serial, args.generation)
        result['app_id'] = crashlytics_app_id(args.google_services, PACKAGE)
        if args.build:
            apk, result['build'] = build_apk(args.out)
        else:
            apk = args.apk.resolve()
        result['apk'] = dict(path=str(apk), **apk_facts(apk))
        if result['apk']['package'] != PACKAGE:
            raise NotChecked(f'The APK is {result["apk"]["package"]}, not the debug package {PACKAGE}')
        if not result['apk']['crash_reports_on'] or not result['apk']['crash_receiver']:
            raise NotChecked('The APK does not send crash reports or lacks the crash receiver; '
                             'build it with -PcrashReportCheck')
        mcp = FirebaseMcp(args.firebase, ROOT, args.out / 'firebase-mcp.log')
        read = lambda: read_check_events(mcp, result['app_id'], since)
        try:
            # A login or permission problem ends the run here, before the device is touched.
            read()
        except NotChecked as error:
            raise NotChecked(f'Crashlytics could not be read before the device run: {error}')
        check = CrashReportCheck(Adb(args.adb, args.serial),
                                 Screen(args.devices_tool.resolve(), args.serial, args.generation, args.out),
                                 apk, args.out)
        check.result.update(result)
        result = check.result
        verdict, reason = run_device(check, check_id)
        if verdict is None:
            verdict, reason = check_server(result, read, args.out, args.server_timeout, args.poll_seconds)
    except (NotChecked, CheckFailed) as error:
        verdict, reason = NOT_CHECKED, str(error)
    except KeyboardInterrupt as error:
        verdict, reason = NOT_CHECKED, f'Interrupted: {error}'
    except Exception as error:
        verdict, reason = NOT_CHECKED, f'Tool error: {type(error).__name__}: {error}'
    finally:
        if mcp:
            result['firebase_cli_starts'] = mcp.starts
            mcp.close()
    result.update(verdict=verdict, reason=reason, finished_at=datetime.now(timezone.utc).isoformat())
    (args.out / 'crash-report-check.json').write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
    print(f'{verdict}: {reason} ({args.out / "crash-report-check.json"})')
    if result.get('cleanup_errors'):
        print('cleanup failed: ' + '; '.join(result['cleanup_errors']), file=sys.stderr)
    return exit_code(verdict, result.get('cleanup_errors'))


if __name__ == '__main__':
    sys.exit(main())
