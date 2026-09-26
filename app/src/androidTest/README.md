# Android UI tests (androidTest)

UI Automator tests that drive the debug build (`com.kimjisub.launchpad.dev`) on an emulator.

## How to run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # macOS
export ANDROID_HOME="$HOME/Library/Android/sdk"

# Whole suite on the connected emulator. Keep the APKs installed: AGP otherwise uninstalls the
# app after the run, which deletes every pack on that device.
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true

# One class
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kimjisub.launchpad.PlayActivityTest
```

Reports: `app/build/reports/androidTests/connected/debug/index.html`,
raw results: `app/build/outputs/androidTest-results/connected/debug/*/test-result.pb`.

## How the tests find things

- **Test pack.** `BaseUITest` writes `TestUniPack` ("UI Test Pack": 8x8, 2 chains, LED,
  autoPlay, silent sounds) into the app workspace before every test, so tests never depend on
  what the emulator happens to hold and never play audio. `testUnipackDeletion` deletes it; the
  next test writes it again.
- **No view ids, no screen coordinates for controls.** Main, Settings, Store and Play screens are
  Compose. Controls are found by content description or text from the app's string resources
  (`str(R.string.…)`), so the tests follow the device language.
- **Main screen**: Store / Import icons in the sort bar, Settings icon in the total panel. The old
  FAB menu (`floatingMenu`, `store`, `setting`, `loadUniPack`) no longer exists.
- **Play screen**: Menu button in the right chrome column opens the option panel (switches found by
  label). AutoPlay is a Play Mode in that panel; its Prev / Play-Pause / Next transport is in the
  chrome column. **Back toggles the option panel and does not leave**; leave with Quit
  (`quitPlayToMain()`).
- Pads and chain buttons are plain views without ids; `tapPad(x, y)` / `tapChain(i)` tap them by
  grid position inside `padArea()`.

## Known limits

- The instrumentation runs inside the app process, so a test cannot kill the app and keep running.
  `testSettingsPersistence` leaves the app and relaunches it with a cleared task instead.
- `StoreTest` needs network access (Firebase). It does not press Download: that would download a
  real pack and count in the production download statistics.
- Real Launchpad (USB MIDI) behaviour cannot be checked on an emulator.

## Baseline

Before measured 2026-09-26, after measured 2026-09-27, on the same emulator for both runs: AVD
`unipad-pixel`, Android 15 (API 35), 2400x1080 landscape, **airplane mode on (no network)**, debug
build 4.1.8, commit `6473b0b9`.

| Run | Total | Passed | Failed | Skipped |
|---|---|---|---|---|
| Before (tests as of `6473b0b9`) | 36 | 23 | 13 | 0 |
| After (this folder) | 36 | 35 | 1 | 0 |

Before, the 13 failures were: `AppLaunchTest.testMainScreenElements` (instrumentation process
crashed while the app package was being updated; environment), `MainActivityTest` x3 (FAB gone),
`PlayActivityTest.testPlayActivityAutoPlayControls` / `testPlayActivityPadViewPatterns` (old
coordinates / old view ids), `SettingsTest` x3 and `StoreTest` x2 (FAB gone, Back from Play/Store
assumptions), plus the two `@Ignore` tests below. Most tests that "passed" only asserted that the
app process was still alive after coordinate taps.

After, the remaining failure is the environment, not the test:

- `StoreTest.testStoreUnipackBrowsing`: the store list needs network; the device harness keeps the
  emulator in airplane mode, so it fails with "Store could not reach the server".

### Slow queries while the play screen animates

UI Automator waits for the screen to go idle before every query (10 s by default) and reads each
node's text and description separately. With Feedback light on, AutoPlay redraws a pad every
300 ms and the screen never goes idle. An earlier version of `testPlayActivityControls` built its
failure message (all on-screen labels) before running the check. That alone took about 27 s, so
the test pack's AutoPlay (then 30 s) had finished before the check ran and the test failed as if
AutoPlay stopped early. The app log showed AutoPlay running its full length. It is not an app
defect. The tests now:

- build the label list only when a check fails (`assertTrueWithLabels`);
- shorten the idle wait while AutoPlay runs with Feedback light (`withShortIdleWait`);
- fail with the elapsed time if the "still running" check comes after AutoPlay would have ended
  anyway, so a slow check cannot be mistaken for an app problem again;
- use a 60 s AutoPlay (`TestUniPack.AUTO_PLAY_MS`): in a full run the check came 16 s after
  selecting AutoPlay (7–10 s when run alone).

### `@Ignore` is counted as a failure by the runner

With AGP 9.4's connected test engine (`com.android.tools.androidtest.testengine`) and the Android
Test Orchestrator, an `@Ignore` test is recorded in `test-result.pb` as status 6 with
`org.opentest4j.TestAbortedException: Test ignored`, and the JUnit XML / HTML report turns that into
a failure with an empty message (`skipped="0"`). This is outside the test sources: it comes from the
runner and report conversion, not from the tests. The two ignored tests
(`testLoadUniPackFABInteraction`, `testReconnectLaunchpadFABInteraction`) were ignored because the
FAB was hard to click; the FAB is gone and both now drive the current screens, so no test is
ignored any more. Avoid `@Ignore` until the runner reports it as skipped.

### Known flakiness

`testPlayActivityUIVisibilityFeatures` failed once while reopening the option panel during its
close animation; the tests now retry Menu until the panel is open. Buttons on the play screen are
recomposed while LEDs and Autoplay run, so taps go through `clickFresh`, which looks the node up
again if it went stale.
