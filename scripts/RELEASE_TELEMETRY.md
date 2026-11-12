# Release telemetry observation

The main manifest disables advertising ID collection with
`google_analytics_adid_collection_enabled=false`. The release manifest removes `AD_ID`,
`ACCESS_ADSERVICES_AD_ID` and `ACCESS_ADSERVICES_ATTRIBUTION` permissions added
by libraries. Analytics events and Crashlytics collection remain enabled in
release builds. Advertising ID based advertising measurement and attribution
are unavailable; Analytics uses its separate app-instance ID for user metrics.
See [Firebase collection settings](https://firebase.google.com/docs/analytics/android/configure-data-collection)
and [Analytics identifiers](https://support.google.com/analytics/answer/11593727?hl=en).

The CI manifest check rejects release builds that restore these permissions or
omit the advertising ID opt-out. It also checks debug collection stays off:

```sh
python3 scripts/check_debug_telemetry_manifest.py \
  app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml \
  app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml
```

`observe_release_telemetry.py` observes an APK built in release mode on an
explicitly rented, rooted Android emulator. It records the first screen press's
Analytics event, the current Crashlytics session's screen context and the
advertising ID fields of the Analytics uploads read while it waits for those.
It never triggers a crash. The advertising ID is the only value it judges: a run
fails when an upload carries it. Everything else is recorded, not judged.

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

The candidate is installed with `adb install -r`. `--installer <package>` adds
`-i <package>`, so the installer of record is that package, and the command fails
unless `dumpsys package` reads it back. Use `--installer com.android.vending` to
see the value an install from Google Play records; without it an adb install
records no installer. The JSON keeps the requested installer and the one read back
under `installer`, and each new session's `install_source` key (or `null`) under
`install_source`. The value is recorded, not judged. Its installed package hash must
match the supplied APK. Existing application data is
kept, and the reserved `telemetry-observer` pack is populated from the existing
audio-output fixture (sound files go in `sounds/`, owned by the app). This pack
and the installation stay on the emulator. Before launching the app, the command
sets `files/` and the fixture tree to the app owner, removes group/other access
and restores app-specific SELinux labels, including when `files/` did not exist.
The JSON includes `ls -ldZ` output for `files/` and `files/UniPack`. Use a fresh rental for an isolated
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
`input_source` parameter, a new session's `screen` key plus lifecycle lines, and
at least one Analytics upload bundle of the app; they impose no whitelist on the
observed values.

The advertising ID is the one judged value. Each upload bundle's top-level
`resettable_device_id` and `limited_ad_tracking` are read from the verbose upload
log (nested user properties and events are skipped); an absent field is `null`.
`advertising_id` records the number of the app's bundles, how many carried either
field with a value, and each distinct pair of values. A bundle with either field
not empty sets `status` to `failed`, adds the reason to `failures` and exits
nonzero, after the same cleanup. A build without the opt-out sends the device's
advertising ID with `limited_ad_tracking: false`; with the opt-out the ID is empty
and `limited_ad_tracking` is absent. Bundles of other apps uploaded in the same
log are not counted. Session files prove local
context collection, not server receipt of an actual crash report; that is checked
with a debug build by `crash_report_check.py` ([CRASH_REPORT_CHECK.md](CRASH_REPORT_CHECK.md)).

`--timeout` bounds each screen wait and the telemetry polling phase (default
60 seconds); individual device commands also have time limits. Analytics uploads
may take a few seconds before `_dbg` is visible. The polling phase waits for it
up to the deadline, but a missing `_dbg` alone does not invalidate otherwise
complete observations: the JSON records it as unknown. Missing required
observations, preparation errors, interrupts and cleanup failures exit nonzero.

On success, failure, Ctrl-C or termination, the app is stopped, Analytics debug
marking is set to `.none.`, and `log.tag.FA-SVC` is restored to its previous value.
Both properties are read back and cleanup errors are recorded independently.
Closing the screen connection checks both the command exit code and its success
response; either failure is recorded in `cleanup.errors` and makes the command
exit nonzero, while preserving the observations and writing the result JSON.
An uncatchable process kill cannot run cleanup; return the device rental after
any interrupted command. Each round needs a new output filename; archive the
JSON and diagnostics before rerunning.

Offline regression checks are included in the existing scripts test discovery:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_observe_release_telemetry.py' -v
python3 -m unittest discover -s scripts/tests -v
```
