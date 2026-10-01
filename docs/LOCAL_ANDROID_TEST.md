# Local Android test build and lifecycle

This is a local screen/pack test variant, not a production-equivalent telemetry or
online Store test. Never launch the baseline APK. Independent QA must inspect the
actual artifacts before the device tester runs the lifecycle below.

## Build without production settings

Load the machine environment before **every** shell command:

```sh
. /Users/kimjisub/GitHub/unipad/project/paperclip/env.sh && python3 tools/prepare-local-test-build.py e4dae47486c004c837c605818fd1b6d12831d993 "$PAPERCLIP_RUN_SCRATCH_DIR/baseline"
. /Users/kimjisub/GitHub/unipad/project/paperclip/env.sh && cd "$PAPERCLIP_RUN_SCRATCH_DIR/baseline" && ./gradlew :app:assembleDebug --console=plain
```

The exporter requires a new destination. It selects tracked files from the exact
commit, excluding service, signing and local machine settings **before** creating
the archive. It never reads or copies the excluded files or local untracked files.
It creates missing `keystore.properties` with `storeFile=dummy.jks` and the other
three values `dummy`, as described in `CLAUDE.md`. It creates `local.properties`
from the SDK's `adb` found in the loaded environment. No dummy keystore is made;
the existing debug build type uses Android's standard debug signing. Build source
and signing configuration stay unchanged.

The submitted source tracks a service configuration, which is deliberately excluded.
The exported copy instead gets invented, nonproduction `app/google-services.json`
values for `com.kimjisub.launchpad.dev`, project `unipad-local-invalid`, and an
`.invalid` database/storage host. This is a fake build input, not a real service
registration or proof of isolation. Do not build release APKs from this copy.
`LOCAL_TEST_INPUTS.txt` identifies the exact source and generated inputs.

For the protected APK, pass the reviewed safety commit instead of the baseline:

```sh
. /Users/kimjisub/GitHub/unipad/project/paperclip/env.sh && python3 tools/prepare-local-test-build.py <safety-commit> "$PAPERCLIP_RUN_SCRATCH_DIR/protected"
. /Users/kimjisub/GitHub/unipad/project/paperclip/env.sh && cd "$PAPERCLIP_RUN_SCRATCH_DIR/protected" && ./gradlew :app:assembleDebug :app:processReleaseMainManifest -x :app:processReleaseGoogleServices --console=plain
```

The release manifest inspection skips only the release service-resource task: the
fake configuration intentionally has no release-package client. This does not
produce a release APK or change any build source.

Each output is `app/build/outputs/apk/debug/app-debug.apk` in its separate copy.
Both use package `com.kimjisub.launchpad.dev`, version `4.1.8` / `115`: neither a
new package suffix nor a version change is needed for isolation. Identify them by
source commit, SHA-256, and decoded manifest; never substitute one for the other.
Only the protected APK is eligible for local execution. Build copies are temporary;
publish the APK and verification evidence on the issue before the run ends.

## Why these controls precede the first event

`app/src/debug/AndroidManifest.xml` contributes three metadata values to the final
application before `FirebaseInitProvider` initializes the default Firebase app.
Providers run before `BaseApplication.onCreate()`, which calls Remote Config.
Disabling collection later inside that application method would be too late.

- Analytics: `firebase_analytics_collection_deactivated=true` disables collection
  and takes priority over runtime collection settings. [Official configuration](https://firebase.google.com/docs/analytics/android/configure-data-collection).
- Performance: `firebase_performance_collection_deactivated=true` prevents runtime
  re-enabling. The performance build plugin still instruments code; instrumentation
  cost is retained, but performance reporting is disabled. [Official configuration](https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android).
- Crashlytics: `firebase_crashlytics_collection_enabled=false` disables automatic
  reporting, **not local crash-report storage**. A saved API override can take
  precedence over this metadata. Never run with old test data or call
  `sendUnsentReports` / enable collection. [Official API precedence and persistence](https://firebase.google.com/docs/reference/android/com/google/firebase/crashlytics/FirebaseCrashlytics).
- The debug manifest removes `android.permission.INTERNET` from all merged
  declarations. Android denies this app's network sockets even when the emulator
  reconnects. This is an app permission boundary, not an airplane-mode argument.
  [Android permission](https://developer.android.com/reference/android/Manifest.permission#INTERNET).

The main/release manifest retains INTERNET and has none of these test controls.
The parser, runners, activities, shared conformance suite, and release collection
behavior are not edited. Online Store, downloads, push, Remote Config fetching,
and network-dependent checks are unavailable in this test variant. USB and local
pack screens still require actual device verification; compilation is not that
verification. Do not count Store failures as production failures in this variant.

## Artifact gate (QA)

1. Record full source commit, build command/result, package/version, APK SHA-256,
   and standard debug certificate fingerprint (`apksigner verify --print-certs`).
2. Decode the **actual APK** with the environment SDK's `apkanalyzer manifest print`.
   If the loaded environment leaves `ANDROID_HOME` empty, set it to the parent of
   the `platform-tools` directory of `command -v adb`, the same SDK the exporter
   records in `local.properties`; do not point to a different SDK.
   Check all three literal values and the absence of INTERNET and shared user ID.
   Compare with the protected merged debug manifest and merged release manifest.
3. Run `tools/check-local-test-manifest.py <debug-xml> <release-xml>` separately
   against the merged debug XML and the decoded APK XML. The source-only check is
   `python3 tools/check-local-test-manifest.py app/src/debug/AndroidManifest.xml app/src/main/AndroidManifest.xml --source`.
4. Baseline must fail the safety check. Release must retain INTERNET with no
   deactivation keys. Inspect the manifest merger report if results differ.
5. Confirm the protected exported copy contains only the documented invented
   service input. Do not inspect production service settings for this comparison.

## Lifecycle (device tester, after QA)

All commands require the environment prefix above. Start only with
`python3 "$HARNESS/devices.py" up-android`, use only the printed serial, and run
`python3 "$HARNESS/devices.py" down` in a cleanup/finally path before reporting.
Record emulator OS/API, device ID, time, APK identity, and each actual result.
No emulator was started by the build/manifest checks.

Before launching anything:

1. Inspect packages on the borrowed emulator using `adb -s <serial> shell pm list packages`.
   Require the test package to be absent on this initial run. If it already exists,
   stop and establish its ownership/history with the PM; do not clear unknown data.
   The release package and other apps must not be touched. A newly borrowed device
   is not necessarily a wiped device.
2. Install only the approved protected APK with `adb -s <serial> install <apk>`
   (no `-r`). Do not use `installDebug` or an instrumentation runner which could
   install or launch a different artifact. Confirm package/version, APK path and
   granted permissions using `dumpsys package com.kimjisub.launchpad.dev`.
3. While it is stopped, clear **only this newly installed test app's** data:
   `adb -s <serial> shell pm clear com.kimjisub.launchpad.dev`. Require `Success`.
   Verify `run-as com.kimjisub.launchpad.dev pwd` and list its private files with
   `run-as com.kimjisub.launchpad.dev find . -type f`. Verify no old preferences,
   databases or Crashlytics sessions exist, including device-protected app data
   under `/data/user_de/0/com.kimjisub.launchpad.dev` where accessible. If cleanup
   or inspection fails, do not launch. No restored/previous saved collection value
   may survive. Package backup is disabled in the main manifest; no data restore.
4. Require no shared UID and no granted/requested INTERNET. Check the app UID
   cannot open a socket before launch, for example
   `adb -s <serial> shell run-as com.kimjisub.launchpad.dev /system/bin/toybox nc -w 2 127.0.0.1 9`.
   Require a permission denial, not merely timeout or connection refused. This
   contacts no external service. If the shell tool is unsupported, record that
   gap and stop for QA rather than calling a timeout proof of isolation.

After these gates, launch `com.kimjisub.launchpad.dev/com.kimjisub.launchpad.activity.SplashActivity`
with `adb -s <serial> shell am start -W -n <component>` and observe the real screen.
Record log evidence of initialization/collection state without claiming that quiet
logs prove no transmission. Check a normal first launch, force-stop/relaunch, and a
network-connected/reconnected run on this borrowed device. Use the same artifact
and data between these runs; do not enable collection. Recheck package permission
and socket denial when connected. Check no initialization failure blocks the local
screen. Do not deliberately generate crashes or send events to a server.

At the end, **before restoring connectivity or replacing the APK**, force-stop the
test package, record only test-owned file names/counts (do not publish file contents),
`pm clear` with `Success`, and verify the old test files/preferences/crash reports
are gone. Uninstall **only the owned test package**, require `Success`, and verify
it is absent. Then return the device with `devices.py down` even if checks fail.
Do not delete another app's data, Google Play services data, shared storage roots,
or the device's data. If cleanup fails, leave the test app stopped and report the
failure to the PM; never replace it with a collection-enabled APK. Reverting the
manifest is allowed only after test data is cleared and the owned app uninstalled.

## Limits and other platforms

The permission check covers this app UID, not every possible other process.
Analytics can use Google Play services; app data deletion does not prove deletion
of a pre-existing service-side queue. Therefore this procedure begins with an
absent package, clean test app data, Analytics deactivation **before provider
initialization**, and exclusively invented service inputs. It never claims that
these steps clean historical test events already handed to another service or
prove server-side absence. If prior production-configured test execution or a
service-side queue is suspected, stop and ask QA/PM to assess it without deleting
other apps' data. Network traces/server readings are not performed by this build
check. Device lifecycle, reconnect behavior, real screens, and local report cleanup
must be reported independently by the device tester.

The existing iOS harness uses `-UniPadFirebaseLocalOnly YES`; Android's current
application has no equivalent startup argument, so an iOS flag must not be copied
as Android evidence. Web uses its separate local/test analytics path, which this
Android change does not configure or verify. Neither platform's earlier checks
prove this APK safe. The parent's 20 synthetic packs / 60 comparison rows continue
in the parent issue, not as a passed playback result of this preparation.
