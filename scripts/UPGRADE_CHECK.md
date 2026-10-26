# Upgrade over the published build

`upgrade_check.py` checks that installing a release candidate over the published
build keeps what users saved: pack folders, bookmarks and play history in
`UniPad.db`, and the settings in `shared_prefs/data.xml`. It uses an emulator
that is already running and does not start or stop one.

```sh
python3 scripts/upgrade_check.py --serial "$ANDROID_SERIAL" \
  --old app-release-<published>.aab --new app-release-<candidate>.aab \
  --out "$EVIDENCE_DIRECTORY/upgrade"
```

`--old` and `--new` take bundles, universal `.apks` sets or APKs of the same
package. Bundles are converted with `bundletool build-apks --mode=universal` and
bundletool's debug key, so two release bundles install over each other; this
checks the app's own upgrade code, not a Play-signed update. The run refuses
builds of different packages, different signers or a version code that is not
higher.

The emulator must run Android 11 (API 30) or newer and allow `adb root` (an image
without Play Store): release builds cannot be read with `run-as`, and airplane
mode is switched with `cmd connectivity airplane-mode`. For the run the script
turns on airplane mode and `debug.firebase.analytics.app`. It uninstalls the
package before each scenario, and at the end, however the run ends, uninstalls it
before restoring the network, the analytics flag and the root state.

For each saved state of the tap-order setting (`TraceLogClassic` absent, `true`,
`false`, select with `--scenario`):

1. Install `--old`, open it, add three packs to its pack folder and wait until it
   has made their database rows.
2. In the formats the old build wrote, set a bookmark on one pack, three plays and
   a last-opened time on another, and the list sort (`SortMethod`, `SortOrder`),
   `SlideMode` and the tap-order state. Open the old build again; it must still
   hold those rows and settings, list the three packs and not have crashed.
3. `adb install -r` the new build and open it twice.
4. Fail if the new build does not install, does not reach its pack list or stops
   soon after opening; on any lost, changed or added pack file, any changed, lost
   or added database row, a database that cannot be read, a lower database
   version, any changed or lost setting, a `TraceLogClassic` written when none was
   saved, a pack missing from the list on screen, or a crash or ANR of the package
   in the crash and event logs since the upgrade.

Exit status 0 means every scenario passed, 1 that a check failed (the problems are
printed and in `report.json`), 2 that the run could not reach the upgrade or
stopped on an unexpected error (`setup-error.txt`, with the traceback when there
is one). When a scenario has already failed, a later scenario that cannot start
does not hide it: the run exits 1 and `report.json` lists that scenario under
`not_run`. Each scenario folder keeps `before/` and `after/` with the database,
`data.xml`, pack file hashes, the screen dump and a screenshot, and `result.json`.
Give every run a new, empty output folder. All three scenarios take about four
minutes; a new build that fails to open adds up to two minutes per scenario.
