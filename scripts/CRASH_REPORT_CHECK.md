# Crash report delivery check

`crash_report_check.py` checks that a native crash in the background reaches Crashlytics, and
that both reports about it carry the keys that explain it:

- the native crash report: `test_crash`, `check_id`, `screen` (`PlayActivity`), the play window
  keys (`play_window_mode`, `play_orientation`, `play_screen_short_side`) and the install keys
  (`install_source`, `install_splits`);
- the app's own system exit report (`SystemExitReporter$NativeCrashExit`) from the same
  installation: `exit_reason` 5, `harness_test`, `exit_last_screen` (`PlayActivity`),
  `exit_pack_open` (`true`), a positive `exit_background_ms`, an `exit_timestamp` after the crash
  was requested, and the install keys.

Both must also show the APK's version (`4.2.2 (121)` style). Values the check does not name are
kept in the result as they arrived.

It only ever uses the debug package `com.kimjisub.launchpad.dev` and its Firebase app from
`app/google-services.json`; another package is refused before anything is installed. Debug
reports are marked `harness_test` / `test_crash` so they are not read as users' crashes.

## Run

Rent an Android 11 or later emulator that allows `adb root`, then:

```sh
python3 "$HARNESS/devices.py" up-android 14
python3 "$HARNESS/devices.py" lease <device-id>
python3 scripts/crash_report_check.py --build \
  --serial <device-id> --generation <lease-generation> \
  --devices-tool "$HARNESS/devices.py" --out /path/to/new-folder
python3 "$HARNESS/devices.py" down
```

`--build` builds this checkout with `./gradlew --no-daemon -PcrashReportCheck :app:assembleDebug`
and records the commit and whether the checkout had uncommitted changes. Pass `--apk` instead to
use a debug APK already built with `-PcrashReportCheck`; an APK without crash reports switched on
or without `NativeCrashCheckReceiver` is refused. The Firebase CLI (`--firebase`, default
`firebase`) must be logged in to an account that can read the project's Crashlytics.

The command does not rent or return devices. Return the rental as soon as it finishes, including
after a failure.

## What it does

1. Uninstalls the debug app and installs the APK fresh (a new Crashlytics installation id, and no
   crash collection choice saved by an earlier run), then adds the small `crash-report-check`
   test pack from `app/src/androidTest/assets/audio-output`.
2. Opens the pack's play screen, goes Home and waits 8 s.
3. Sends `NativeCrashCheckReceiver` a new `check_id` (default `crash-check-<UTC time>-<random>`),
   which crashes the app with SIGSEGV, and waits up to 30 s for the process to end.
4. Opens the app, waits 20 s and requires the app's log line recording the native crash exit;
   stops it, opens it again and waits 20 s so the recorded report is sent.
5. Reads Crashlytics every `--poll-seconds` (60) until both reports of this `check_id` are there
   or `--server-timeout` (1800 s) passes. Crashlytics lists events only per issue, so each read
   takes the issues since the run started and their events.

The device is left with the debug app installed and stopped; the UI session is closed.

## Result

`--out` must be a new folder. It receives `crash-report-check.json` (verdict, reason, check id,
APK hash and version, device, steps with times, local evidence, each server read, both reports'
fields and keys, and every value problem), `server-native.txt` and `server-exit.txt` (the
reports as Crashlytics returned them), `logcat.txt`, `play.png`, `firebase-mcp.log`, and on a
screen that did not appear, its snapshot and screenshot.

| Exit | Verdict | Meaning |
|------|---------|---------|
| 0 | `passed` | Both reports arrived with the expected values |
| 1 | `failed` | The app did not do its part (play screen, crash, recorded exit), a report did not arrive in time, or a value is wrong |
| 2 | `not_checked` | Nothing was learned about the app: rental, build, APK, install, root, Android 10 or older, interrupted, or Crashlytics could not be read at the end |
| 3 | `passed` | Passed, but stopping the app or closing the UI session failed (`cleanup_errors`) |

A cleanup failure is recorded beside the verdict and never replaces it. A report that is missing
at the time limit is `failed` only when the last server read worked; when that read failed the
run is `not_checked`.

## Offline checks

```sh
python3 -m unittest discover -s scripts/tests -p 'test_crash_report_check.py' -v
```
