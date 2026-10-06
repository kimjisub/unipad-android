# Android pull request checks

Every pull request runs these stable checks on Ubuntu 24.04:

| Check name | Command | Timeout |
| --- | --- | --- |
| `unit-tests` | `./gradlew :app:testDebugUnitTest :design:testDebugUnitTest --no-daemon --console=plain`, then `python3 -m unittest discover -s scripts/tests -v` | 20 minutes |
| `Android debug build` | `./gradlew assembleDebug :app:compileDebugKotlin :app:compileReleaseKotlin :design:compileDebugKotlin :design:compileReleaseKotlin --no-daemon --console=plain` | 25 minutes |
| `Android lint` | `./gradlew lintDebug --no-daemon --console=plain` | 25 minutes |

The UI suite (`bash scripts/ci-connected-tests.sh`) is not a hosted check; run it
locally before every push, as described below.

Setup uses Java 21 and the SDK/NDK/CMake versions in
`actions/setup-android/action.yml`. Copy `.github/fixtures/google-services.json`
to `app/google-services.json` in an isolated test checkout before running these
commands. The fixture is intentionally fake and has no Firebase access.
No release signing key is needed.

The UI job uses API 35, Google APIs, x86_64 and KVM. It runs
`:app:connectedDebugAndroidTest :design:connectedDebugAndroidTest` without test
filters, including the merged base-feature tests. Tests use their
own local fixtures; live services and physical MIDI/audio hardware are outside
this check's coverage. A local run on an API 35 ARM emulator uses the same Gradle
command, but host CPU and GPU differ.

For local runs, start a freshly wiped API 35 emulator that nothing else is using
and set `ANDROID_SERIAL` to its serial from `adb devices`. Record its current Wi-Fi/mobile data
state, disable both with `adb -s "$ANDROID_SERIAL" shell svc wifi disable` and
`adb -s "$ANDROID_SERIAL" shell svc data disable`, and restore that state before
stopping the emulator. Hosted runners disable both before testing, then dispose
of their emulator. The script
never chooses or starts another device. Hosted GitHub runners instead use the
pinned [Android Emulator Runner action](https://github.com/ReactiveCircus/android-emulator-runner)
for their isolated, disposable emulator.

`python3 -m unittest discover -s scripts/tests -v` runs in the `unit-tests` check,
with fake `adb` and Gradle and no device. It checks that the UI runner keeps
Gradle failures through log capture, requires the allocated device id, and
fails when the app was not installed, a module has no results, a test was
failed, errored or skipped, or the number of tests run differs from the sources.

Unit reports, lint reports and UI reports are uploaded even on failure and kept
for seven days. UI artifacts also include logcat, full Gradle output and elapsed
time/exit status captured before the emulator is shut down. GitHub job start and
completion timestamps provide the duration for every check. Cancellation or a
hard job timeout may interrupt diagnostic collection.

Lint keeps the existing project rules and fails on errors; no baseline or new
exclusions are introduced here. Repository branch protection is configured by
the technical lead after review and merge, using the exact check names above.

## Existing unit-test and signing safeguards

The LED press race tests keep both sets of 3,000 trials and all 200 inputs per
trial. Both the final LED state and main-thread delivery assertions remain.
Previously their barrier and worker join could wait forever, and an exception
on the tick worker was not reported to JUnit. Each now has a three-minute test
deadline, five-second barrier/join limits, and explicit worker failure reporting.
The worker is always asked to stop, including if a press or drain fails.

On the current baseline, the reported hang did not reproduce: the two unchanged
race tests passed in 26.6 seconds and the full suite finished in 80 seconds on
an Apple silicon Mac. These results do not establish the cause of the earlier
interruption. The deadlines make a future stalled run fail instead of waiting
indefinitely; the workflow also caps the test step at 12 minutes and the job at
20 minutes. The repetition counts are not reduced and no tests are excluded.

Release signing remains required: without `keystore.properties`, debug tests
can configure, but the release signing configuration has no key and release
signing validation fails. Existing release keys are loaded only when that local
file exists; CI never creates one.

The fixture supports both debug and release package names for local compilation.
Its API key has the SDK-required shape and an all-zero suffix; it grants no
access to a real Firebase project. A malformed unit-only key previously threw
in Firebase Installations when the real app was launched for UI tests. Keeping
a syntactically valid fake lets offline device tests exercise app startup with
the normal SDK initialization. No SDK collection or crash detection is disabled.
