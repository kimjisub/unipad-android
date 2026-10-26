# Release telemetry observation

`observe_release_telemetry.py` observes an APK built in release mode on an
explicitly rented, rooted Android emulator. It records the first screen press's
Analytics event and the current Crashlytics session's screen context. It never
triggers a crash or decides whether the observations satisfy a data-safety declaration.

Rent a fresh Android 15 emulator with the device rental tool, then read its lease:

```sh
python3 "$HARNESS/devices.py" up-android 15 --fresh
python3 "$HARNESS/devices.py" lease <device-id>
python3 scripts/observe_release_telemetry.py \
  --apk /path/to/universal.apk \
  --serial <device-id> --generation <lease-generation> \
  --devices-tool "$HARNESS/devices.py" \
  --out /path/to/round.json
python3 "$HARNESS/devices.py" down
```

Return the rental as soon as observation finishes, including when the command
fails. This script does not start or return devices. `--apk` accepts a standalone
APK or a universal APK extracted from a bundletool archive, not an AAB or APKS
archive. Root must work; a production image that refuses `adb root` fails before
installation. The command uses only the named device and validates its rental
generation before changing it. All screen actions use the rental's UI entry.

The candidate is installed with `adb install -r`. Its installed package hash must
match the supplied APK. Existing application data is
kept, and the reserved `telemetry-observer` pack is populated from the existing
audio-output fixture (sound files go in `sounds/`, owned by the app). This pack
and the installation stay on the emulator. Use a fresh rental for an isolated
observation. The app is stopped before debug Analytics is enabled, then launched,
and its pack row, Play control and pad are located in the screen hierarchy.
The first cell is derived from the square grid and adjacent chain strip for the
fixed 8x8 fixture, whether or not individual pad views appear in the snapshot.
Pad and chain buttons share view IDs, so those IDs are not used to select a pad.
No instrumented test build or production catalogue download is used.

The JSON retains the candidate hash, version, device/OS, UTC timestamps, all
complete logged Analytics events and parameters, raw Analytics log, `_dbg`
observation, each new Crashlytics session's keys, lifecycle lines and raw userlog
as hexadecimal. Unknown values are retained. A missing debug field is `null`,
not evidence that debug marking was absent. Presence checks require an
`input_source` parameter and a new session's `screen` key plus lifecycle lines;
they impose no whitelist on the observed values. Session files prove local
context collection, not server receipt of an actual crash report.

`--timeout` bounds each screen wait and the telemetry polling phase (default
60 seconds); individual device commands also have time limits. Analytics uploads
may take a few seconds before `_dbg` is visible. The polling phase waits for it
up to the deadline, but a missing `_dbg` alone does not invalidate otherwise
complete observations: the JSON records it as unknown. Missing required
observations, preparation errors, interrupts and cleanup failures exit nonzero.

On success, failure, Ctrl-C or termination, the app is stopped, Analytics debug
marking is set to `.none.`, and `log.tag.FA-SVC` is restored to its previous value.
Both properties are read back and cleanup errors are recorded independently.
An uncatchable process kill cannot run cleanup; return the device rental after
any interrupted command. Each round needs a new output filename; archive the
JSON and diagnostics before rerunning.

Offline regression checks are included in the existing scripts test discovery:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_observe_release_telemetry.py' -v
python3 -m unittest discover -s scripts/tests -v
```
