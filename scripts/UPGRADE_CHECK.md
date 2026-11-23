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

There are four scenarios, run in this order unless `--scenario` names some: one for
each saved state of the tap-order setting (`trace-absent`, `trace-on`, `trace-off`:
`TraceLogClassic` absent, `true`, `false`) and `legacy-theme`, which has no tap-order
setting saved and adds the bundled theme step below. Each scenario:

1. Install `--old`, open it, add three packs to its pack folder and wait until it
   has made their database rows.
2. In the formats the old build wrote, set a bookmark on one pack, three plays and
   a last-opened time on another, and the list sort (`SortMethod`, `SortOrder`),
   `SlideMode` and the tap-order state. Open the old build again; it must still
   hold those rows and settings, list the three packs and not have crashed.
   Pack A keeps its original `autoPlay`. Packs B and C contain a changed
   `autoPlay` plus the original saved as `autoPlay_2026_11_11-15_32_39` (the
   dated backup used by older builds) and `autoPlay_previous`, respectively.
   These are seeded file layouts equivalent to completed automatic mapping;
   the check does not run the mapper or test restoring a backup. Before the
   upgrade, all five autoplay and backup files must match their seeded hashes.
3. `adb install -r` the new build and open it twice.
4. Fail if the new build does not install, does not reach its pack list or stops
   soon after opening; on any lost, changed or added pack file, any changed, lost
   or added database row, a database that cannot be read, a lower database
   version, any changed or lost setting, a `TraceLogClassic` written when none was
   saved, a pack missing from the list on screen, or a crash or ANR of the package
   in the crash and event logs since the upgrade.

In `legacy-theme`, after step 2 and with the older build stopped, the run leaves what
builds before 4.3.0 left for a user who chose the bundled S skin: `SelectedTheme` set to
`zip://sskin` in `data.xml`, and the theme's files (copied from
`app/src/main/assets/themes/sskin`) in `themes/sskin` of the app's external files folder
(`Android/data/<package>/files`, where the older builds extracted it). This state is
written directly, so it does not depend on which build `--old` is. After the upgrade,
besides the checks above, the scenario fails unless `SelectedTheme` reads
`asset://sskin`, the `themes/sskin` folder is gone, and the play screen is drawn in the
S skin. For the play screen it opens the first pack's play screen three times and
screenshots it once it stops changing: as the upgrade left it, then with no theme
selected (the default theme), then with `asset://sskin` selected directly. The first
must differ from the default theme's by at least 8 grey levels (mean over a 16x9 grid,
0–255) and by at least four times more than it differs from the S skin chosen directly;
the 4.2.2 → 4.3.0 run measured 98.9 and 0.0. These are in
`legacy-theme/after/theme-screen.json`, with `play-migrated.png`, `play-default.png` and
`play-sskin.png`; `legacy-theme/legacy-theme/` keeps the settings and folder listing
left before the upgrade. The theme checks are skipped when the newer build already
failed to install or open. A play screen that does not open and come to rest within 30
seconds (the app crashed drawing the theme, or hangs) fails the scenario like any other
check: its crash and ANR lines and `result.json` are kept, the remaining screenshots are
not taken, and the run exits 1.

The pack file comparison includes every `autoPlay` and both backup formats:
rewriting or deleting any of them fails the upgrade with its relative path in
`problems`. The original and mapped contents differ, so replacing a mapped
autoplay with its backup also fails. Every tap-order scenario covers both
backup formats, regardless of the version supplied with `--old`.

Exit status 0 means every scenario passed, 1 that a check failed (the problems are
printed and in `report.json`), 2 that the run could not reach the upgrade or
stopped on an unexpected error (`setup-error.txt`, with the traceback when there
is one). When a scenario has already failed, a later scenario that cannot start
does not hide it: the run exits 1 and `report.json` lists that scenario under
`not_run`. Each scenario folder keeps `before/` and `after/` with the database,
`data.xml`, pack file hashes, the screen dump and a screenshot, and `result.json`.
Give every run a new, empty output folder. On a busy Mac all four scenarios took
eight minutes (4.2.2 → 4.3.0, 2026-11-24) and `legacy-theme` alone three; a new build
that fails to open adds up to two minutes per scenario. A shell or tool that ends
commands after ten minutes can run the scenarios in two calls with `--scenario`, each
with its own output folder.

The screen is read with `uiautomator dump`. The device rental's snapshot helper
(`com.callstack.agentdevice.snapshothelper`) holds the accessibility connection while it
runs and the dump then comes back empty, so the run stops that helper before the first
scenario (the rental's next screen command starts it again). If the dump is still empty
after three tries, the run stops with exit status 2 and says so, rather than reporting the
packs as missing from the list.

To check the fixtures and failure verdicts without an emulator:

```sh
python3 -m unittest discover -s scripts/tests -p test_upgrade_check.py -v
```

These tests change and delete each of the five temporary autoplay/backup files,
then require a failed scenario report naming that file. They also reject a
missing backup before the upgrade begins. App launch and installation are
simulated in these tests; use the emulator command above to check a real update.
