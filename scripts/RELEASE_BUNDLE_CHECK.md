# Static checks of a release candidate

`check_release_bundle.py` compares a release candidate with the last published build
without a device:

```sh
python3 scripts/check_release_bundle.py \
  --previous app-release-<published>.aab --candidate app-release-<candidate>.aab \
  --out "$EVIDENCE_DIRECTORY/static"
```

Both builds may be bundles, universal `.apks` sets or APKs. A bundle is turned into a
universal APK with `bundletool build-apks --mode=universal` (bundletool on `PATH`); the
bytecode is read with the newest `dexdump` in `$ANDROID_HOME/build-tools` and the merged
manifest with `$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer` (Homebrew's
apkanalyzer cannot find the SDK's build tools and is not used).

What fails the check:

- **Diagnostic keys**: each system exit key the app writes to crash reports
  (`exit_foreground_screen`, `exit_foreground_pack_open`, `exit_foreground_ms`,
  `exit_process_ms`, `exit_last_screen`, from `SystemExitReporter`) must still be loaded
  by a `const-string` in the candidate's bytecode. R8 removing or renaming one would
  drop that field from every report.
- **Forbidden calls**: no reference to `Landroid/media/MediaPlayer;.<init>` in the
  candidate. The system media player crashed in vendor implementations; 4.2.2 has one,
  4.3.0 none.
- **Permissions**: the set of `uses-permission` names must equal the published build's.
  A planned change is named with `--expect-permission +NAME` (added) or `-NAME`
  (removed), once the store declarations match; an expected change that is not in the
  candidate also fails.
- **Device reach**: a `uses-feature` that is required in the candidate but was absent or
  optional before, since devices without it could no longer install the app.
- **Advertising ID**: `google_analytics_adid_collection_enabled` must be `false` and no
  advertising permission may be present (the same rules as
  `check_debug_telemetry_manifest.py`).
- The candidate's version code must be higher than the published build's.

Other manifest changes are listed, not judged: features added as optional or no longer
required, removed features, `queries` entries and broadcast receivers added or removed,
and changed permission attributes such as `maxSdkVersion`.

Exit status: `0` every check passed, `1` a check failed, `2` the check could not run (a
missing file or tool, an unreadable build); `setup-error.txt` then holds the reason.

`--out` must be a new or empty folder. It receives `summary.txt` (the counts per build,
permissions, manifest changes and the verdict, also printed), `report.json` (everything
read from both builds and the verdict), `manifest-previous.xml`, `manifest-candidate.xml`
and `manifest.diff`. Comparing 4.2.2 with 4.3.0 took about 35 seconds.

To check the verdicts without builds:

```sh
python3 -m unittest discover -s scripts/tests -p test_check_release_bundle.py -v
```
