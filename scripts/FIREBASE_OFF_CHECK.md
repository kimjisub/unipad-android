# Release build with Firebase never started

Firebase starts from a content provider (`FirebaseInitProvider`) that the system
creates before the app's own code. When that provider does not run — a device that
disables it, or a process started without it — there is no default `FirebaseApp`, and
any code that reaches Firebase without checking crashes the app at launch
(4.2.2 crashes with `IllegalStateException: Default FirebaseApp is not initialized`).

`firebase_off_check.py` checks a release candidate for this on one rented, rooted
emulator:

1. uninstalls the package, installs the candidate, adds the small audio-output test pack
   (`firebase-off-check`), disables the provider with `pm disable` (and reads it back from
   `dumpsys package`), and sets the app language to English;
2. opens the app and walks main screen → Store, which must show
   "Unable to access server." → Back → Settings → Back → the test pack's play screen →
   Back, taking a screenshot of each screen;
3. requires that Firebase did not start (no `FirebaseApp initialization successful` in the
   log), that the app is still running and that the crash and event logs hold no crash or
   ANR of the app since the provider was disabled;
4. enables the provider again, reopens the app and requires both
   `FirebaseInitProvider: FirebaseApp initialization successful` and
   `Initializing Firebase Crashlytics` in the log within 30 seconds, again without a
   crash or ANR.

Rent an Android 14 (API 34) or newer emulator that allows `adb root` (the rental's
default images do), read its lease, run, and return it:

```sh
python3 "$HARNESS/devices.py" up-android 14
python3 "$HARNESS/devices.py" lease <device-id>
python3 scripts/firebase_off_check.py \
  --apk app-release-<candidate>.aab \
  --serial <device-id> --generation <lease-generation> \
  --devices-tool "$HARNESS/devices.py" \
  --out "$EVIDENCE_DIRECTORY/firebase-off"
python3 "$HARNESS/devices.py" down
```

`--apk` takes a bundle, a universal `.apks` set or an APK. A bundle is turned into a
universal APK with bundletool's debug key, as in [the upgrade check](UPGRADE_CHECK.md);
the build must not be debuggable. Screen actions (open, read, press, Back, screenshots) go
through the rental's UI entry; adb only installs, disables and enables the provider, sets
the language and reads logs. API 33 is the lowest level accepted (per-app language).

Whatever happens, the provider is enabled again, the app stopped, its language restored,
`adb root` undone if the check turned it on, and the screen session closed; each of these runs even if an earlier one fails, and the
provider's state and adb's user are read back. The candidate and the test pack stay installed.

Exit status:

- `0` every check passed and the device was restored;
- `1` the app failed: a crash or ANR, a screen or the Store message that did not appear,
  the app gone after the walk, or Firebase not starting once the provider is enabled
  again. This verdict stands even when a later step or the clean-up also fails;
- `2` the check could not run or finish (no root, install refused, the screen tool
  failing, the run interrupted, Firebase starting although the provider was disabled) or
  could not restore the device. `result.json` keeps the verdict reached before, if any.

When the walk stops early, the logs are still read. A crash or ANR there is the app's
failure (exit 1): a build that needs Firebase dies on opening, and the screen tool then
fails with no process to look at. The app no longer running counts against it only once
the main screen had appeared; the walk stops the app before opening it, so a screen tool
failure or an interruption before that point is exit 2. Firebase starting although the
provider was disabled is exit 2 even if the walk stopped on a missing screen.

`--out` must be a new or empty folder. It receives `result.json` (steps, device, app
version, APK hash, the Firebase lines seen in each run, crash lines, `cleanup_errors`),
the screenshots `1-main.png` … `5-main-reenabled.png`, `disabled-logcat.txt`,
`disabled-crash-events.txt` and `reenabled-logcat.txt`. When a screen does not appear,
its last snapshot and screenshot are saved under the name in the message. A run of 4.3.0
on a busy Mac took about four minutes; a build that crashes at launch fails in under a
minute.

To check the verdicts and exit codes without an emulator:

```sh
python3 -m unittest discover -s scripts/tests -p test_firebase_off_check.py -v
```
