# Base features preflight

This is a deterministic **debug-build integration suite**, separate from the legacy UI tests.
It taps real screens and feeds the real pack parser, importer, runners, database and MIDI driver.
It does not contact production pack servers, increment real download statistics or play audio
through the host speaker. Nothing is skipped to get a passing result.

## Run only this suite

Use a dedicated, disposable app installation on API 33 or newer with the system language set to
English and no screen-lock PIN. The suite refuses an installation containing non-fixture packs.
It only removes its named fixture folders/rows; it does not clear the whole database or app data.

Start a freshly wiped emulator that nothing else is using on a fixed port, so its serial is known,
wait until it has finished booting, then turn its network off:

```bash
"$ANDROID_HOME/emulator/emulator" -avd <your-api-33-or-newer-avd> -port 5580 -wipe-data -no-snapshot-load &
# Every command names this emulator; never touch another device.
export ANDROID_SERIAL=emulator-5580
adb -s "$ANDROID_SERIAL" wait-for-device
until [ "$(adb -s "$ANDROID_SERIAL" shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
adb -s "$ANDROID_SERIAL" shell cmd connectivity airplane-mode enable
adb -s "$ANDROID_SERIAL" shell svc wifi disable
adb -s "$ANDROID_SERIAL" shell svc data disable

# One command builds, installs and runs only BaseFeaturesSuite, failing on JUnit failures.
./scripts/run-base-features.sh "$ANDROID_SERIAL"

# Three consecutive full runs, without resetting the installation between runs.
for run in 1 2 3; do ./scripts/run-base-features.sh "$ANDROID_SERIAL" || break; done

adb -s "$ANDROID_SERIAL" shell cmd connectivity airplane-mode disable
adb -s "$ANDROID_SERIAL" shell svc wifi enable
adb -s "$ANDROID_SERIAL" shell svc data enable
adb -s "$ANDROID_SERIAL" emu kill
```

For Gradle/Orchestrator, keep `ANDROID_SERIAL` set to this emulator (without it, Gradle runs on every
connected device):

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kimjisub.launchpad.basefeatures.BaseFeaturesSuite \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

The shell runner uses an explicit serial for every device action and checks the JUnit footer:
`adb shell am instrument` alone can exit zero after a failed test. The footer must count exactly
the `@Test` methods declared by the classes `BaseFeaturesSuite` lists
(`scripts/check_connected_results.py --suite`), and no test may be skipped.
It does not start a device. Before instrumentation it prepares both installed debug packages
with `cmd package compile -f -m speed`, matching the merged Gradle debug test preparation.
This completes code verification outside the app startup deadline; it does not change app startup,
assertions or timeouts. Debuggable packages may report a `verify` compiler filter rather than
full optimization. Archive `dumpsys package dexopt` with the run output.
No baseline fixture is downloaded. Screenshots are copied by the shell to
`/data/local/tmp/unipad_tests/basefeatures-captures/`, so they survive the target and test
applications being uninstalled at the end of a complete Gradle run. Pull them with
`adb -s <serial> pull /data/local/tmp/unipad_tests` before stopping the emulator.
Clear that shell-owned folder before a new run to avoid mistaking older captures for new ones.
Readiness failure screenshots, activity state and hierarchy are kept under the same root.

## Screen transition synchronization

`FeatureScreen` retains the 10-second node, scroll and state deadlines. On API 34+,
queries clear `UiAutomation`'s accessibility cache before reading the current window;
locale recreation, rotation and DocumentsUI replacement can otherwise leave a previous
window or null child nodes in the cached tree. Click helpers reacquire only on a stale
node, within the same deadline. Query and click operations temporarily cap UI Automator's
implicit idle wait at 500 ms, as the legacy helper does: playing animation can continue
producing accessibility events and replace a node during the default 10-second idle wait.
The explicit 10-second checks and their expected controls remain unchanged; the previous
idle setting is restored after each operation. `ActivityReadiness`, shared with legacy
launcher tests, waits within the feature tests' existing 10-second budget for the resumed
activity to be laid out and for its window or an app-owned dialog to have focus before
querying controls. Legacy launches wait up to 45 seconds for the requested activity to finish creation,
then up to 45 seconds for MainActivity to resume, then up to 20 seconds for its window
to be laid out and focused (an app-owned dialog may hold focus). The former "App did not start"
check is now this window-readiness check; the later main-screen content checks remain.
A missing-node capture also saves the hierarchy and
resumed activity's locale, layout size and focus state.

The language/skin check waits for the initial Settings window to be laid out and focused,
and for the requested locale to reach a ready Settings window before scrolling. It still
checks Korean and English text, fresh-screen scroll position, the requested skin preference after the Apply handler runs and
rendered pad pixels. The ZIP check always opens the real picker drawer, selects Downloads
inside that drawer and confirms the Downloads toolbar before selecting the ZIP. It still
checks the result text, pack title, Play now action and imported pack id; a result dialog
owns focus, so MainActivity focus is not a prerequisite for inspecting that dialog.

## Existing coverage and the gaps this suite fills

Existing coverage is an inventory, not a claim that the entire legacy suite passed in this change.
Those tests continue to run separately; their maintenance is covered by the complete run in
[the instrumented test inventory](../../../../../README.md). The merged
`UniPackImportOverlapDeviceTest` already supplies the importer analytics argument using a no-op
sink; this suite does not change that file.

| Base feature | Existing tests | New deterministic screen coverage | Status and remaining check |
|---|---|---|---|
| 1. Start, empty/populated library, settings | `AppLaunchTest`, `MainActivityTest`, `SettingsTest`, `MainActivityListLedTest`, `PackSearchTest` | `launcherEmptyLibrarySettingsThenPopulatedLibrary` | Automatic for launcher, both library states and opening/closing settings. Fresh install permission flows remain a separate check. |
| 2. ZIP import, result, opening pack; retired share links | `UniPackInstallOverlapTest`, `UniPackImportOverlapDeviceTest`, `PackImportUsageTest` check importer files/races/outcomes | `filePickerImportsZipShowsResultAndOpensImportedPack`, `sharedCodeLinkHasNoHandlingActivity`, `retiredScreenHasNoActivityRegistration` | Automatic for actual document picker/import/result/play, unchanged size display, no main-thread file access during import/result rendering, and absence of the retired share link/screen. |
| 3. Sound request, keyLED, chains, simultaneous fingers and cancellation | `SoundRunnerTest`, `LedRunnerDeliveryTest`, `PlayActivityViewModelLedTest`, `PlayActivityTest` | `padTouchRequestsSoundFromLoadedPack`, `padPressedWhileTheClosedOptionsWindowFadesOutPlays`, `twoSimultaneousFingersPlayBothPadsAndReleaseBoth`, `fingerLiftedAfterChainChangeStopsOnlyTheLoopItStarted`, `MultiTouchPadModeTest`, `MultiTouchSlideModeTest`, `MouseInputTest`, `HeldPadLayoutChangeTest`, `HeldPadLeavingScreenTest`, `MixedPointerInputTest`, `PadEdgeTouchTest` (see below) | Partial: automatic request IDs, displayed LED color/on/off, actual chain-button input, a pad pressed while the closed options window still fades out, injected multi-finger touchscreen gestures in both pad input modes, per-pad and whole-gesture cancellation, repeat cancellation, re-press and finite-sample release rules, lifting or cancelling a finger after the chain changed, for sounds and keyLED loops (the shared chain-release cases), a held pad released when the play screen pauses behind another activity or Home hides it, an injected mouse on pads and chains, and presses at every pad's edges and corners on square and stretched packs. Audible output/latency and real fingers need a real device/headphones. |
| 4. AutoPlay start/pause/stop, practice hints | `AutoPlayRunnerTest`, `PlayActivityTest.testPlayActivityAutoPlayControls` | `autoplayStartsPausesResumesAndStopsThroughScreenControls`, `guideAndStepPracticeShowHintsAndStepWaitsForPadInput`, `stepPracticeContinuesPastAMoveToAChainThePackLacks`, `stepPracticeFollowsHintsAcrossChainsAndEndsOnlyAfterTheLastPad` | Partial: automatic controls, frozen/resumed progress, sound requests, guide light, practice waiting/advancing, a pad whose sound moves to a chain the pack lacks, and a whole step practice: a short four-pad sequence over two chains is followed by pressing each hinted pad and the hinted chain button. Practice does not move on a press on the wrong chain or an unhinted pad, and only the last pad ends it (mode off, runner stopped, controls gone). Packs with long sequences are not stepped through to the end. |
| 5. MIDI discovery, input, output | `MidiConnectionLifecycleTest`, driver tests, logo/velocity tests | `virtualLaunchpadIsDiscoveredPadInputPlaysAndKeyLedSendsPackets` | Partial: synthetic app-layer connection/banner, real Launchpad S decoder, pad/chain input, sound request, encoded LED on/off output, detach. USB enumeration/permission, cable/electrical behavior and real MIDI service require hardware. |
| 6. Store/download, delete/history/bookmark | `StoreTest` uses an offline `StoreCatalog`; `UniPackDownloadPathTest`, `MainActivityTest.testUnipackDeletion`, `UnipackRepositoryDeleteTest` | `offlineCatalogDownloadUpdatesResultAndLibrary`, `deletePackRemovesFilesHistoryAndBookmarkReinstallStartsFresh`, `deleteConfirmationIgnoresRightAndMiddleClicksButNotLeftClicks` | Automatic fake catalog → ZIP → downloaded state/library; delete removes files and only its record, reinstall starts without history/bookmark; injected right/middle mouse clicks on the confirmation do not delete, a left click still acts. Real catalog/server availability separate. |
| 7. Language, skin, rotation, bars/cutout, keyboard | `SettingsTest.testInfoRowsFollowDeviceLanguage`, `ThemeTest`, `PlayActivityTest.testPlayScreenStaysClearOfSystemBarsAndCutout`, `StoreTest.testStoreLastRowClearOfSystemBars` | `languageAndSkinSelectionPersistAndPlayLoadsSelectedSkin`, `rotationKeepsPackAndChainAndPadsStayClearOfBarsAndCutout`, `mainScreenButtonsStayClearOfSideNavigationBarAndCutout`, `settingsScreenStaysClearOfSideNavigationBarAndCutout`, `themeScreenStaysClearOfSideNavigationBarAndCutout`, `transferScreenStaysClearOfSideNavigationBarAndCutout`, `emptyLibraryGuideStaysClearOfGestureNavigation`, `mainScreenSearchKeyboardCoversLeftPanelWithoutSqueezingIt`, `mainScreenSearchResultsScrollAboveKeyboard`, `mainScreenNoSearchResultsScrollAboveKeyboard` | Partial: per-app English/Korean settings, apply/persist/render skin, both landscape rotations, pack/chain preservation, bounds vs actual system insets. The main, settings (both categories), theme and transfer screen tests turn on three-button navigation and a punch-hole cutout themselves (`DisplayOverlays`, restored afterwards) and check every control on screen lies clear of the side bar and the cutout in both landscape rotations. The empty library test turns on gesture navigation and the largest font size (2.0, restored afterwards), scrolls the guide to its end and checks its buttons stay above the gesture handle. The search keyboard covers the main screen's left panel without resizing it; dragging the results in the strip left above the keyboard brings the last row, and the "no results" clear button, above it. Below API 35 the main window is not edge-to-edge and is never told the keyboard's height, so the keyboard still covers the end of the list there; those two keyboard tests check that only from API 35. The play screen rotation test still runs in the device's own navigation mode and cutout profile, so three-button navigation on the play screen and other form factors need repeated device configurations. |
| 8. Background/lock/return while playing | `AudioFocusPolicyTest` covers focus policy; transfer lifecycle test covers a different screen | `playingSurvivesHomeAndScreenLockWithoutLosingPackOrChain` | Partial: Home, screen off/on, silence on leave, same activity/viewmodel/process/chain and a new sound request after returning. OS process eviction/OEM lock/power policies need hardware/long-duration checks. |

## Touch cancellation regression (base feature 3)

`twoSimultaneousFingersPlayBothPadsAndReleaseBoth` retains its simultaneous-pointer
press/release check and then runs three isolated scenarios, each with a fresh synthetic pack:

- `cancelledTouchesReleaseOnlyTheirPadsAndCanBePressedAgain`: injects DOWN/CANCEL/UP through the actual window
  and cancels an individual pointer with a cancelled POINTER_UP. A single cancelled pad loses its pressed light and
  looping keyLED execution and requests stopping its infinite voice. With two fingers held,
  a POINTER_UP carrying FLAG_CANCELED releases only that finger and preserves the other.
  A fresh press/release also leaves the other voice running. Repeated whole-gesture CANCEL
  and a following UP request no extra stop; a fresh press works. Finite samples continue on either UP or CANCEL.
- `wholeTouchStreamCancellationReleasesBothPads`: injects two touchscreen pointers through
  `UiAutomation` and cancels their whole stream. Both pressed lights, keyLED executions and
  infinite voices must release, with one stop request for each voice.
- `slideTouchCancellationStillReleasesBothPads`: injects two pointers and CANCEL through
  the actual window with Slide Mode enabled (checked as applied on the play screen) and verifies both infinite voices stop, with no extra stop from
  a repeated CANCEL or following UP.

These are synthetic events delivered to real screen input paths, not physical-finger checks.
`RecordingAudio.loops` records the requested repeat count; `stoppedVoices` records stop keys
(including empty key 0 on initial press). Resetting request history also resets the silence boundary
used by `ringingLoops`, so a fresh fixture starts with consistent playback tracking.
The `TouchCancellation` log tag prints the scenarios
and request snapshots. This is request evidence only, not proof of audible playback or silence.
Screenshots wait for the actual pad LED drawable to clear and two display frames to draw.
Captured PNGs include `single-pad-cancel`, `one-of-two-pads-cancel`, `whole-cancel-before`
and `whole-cancel-after`, plus the same pair with a `slide-cancel` prefix.
The former per-pad input ignored CANCEL. The current Compose input already turns
CANCEL into releases, so these checks protect that behavior without restoring the old listeners.
Every gesture goes through `Fingers`. The OS rejects duplicate events after CANCEL; defensive
repeat events are dispatched directly to the real Activity window (`Fingers.redeliverEnd`).
The first cancellation always uses UiAutomation. A failed screenshot after a failed release
check is attached to that failure instead of replacing it.

Run the existing test entry, or run the full BaseFeaturesSuite as above:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kimjisub.launchpad.basefeatures.TouchPlaybackTest#twoSimultaneousFingersPlayBothPadsAndReleaseBoth \
  --no-daemon --console=plain
adb -s "$ANDROID_SERIAL" logcat -d -s TouchCancellation:I '*:S'
```

## Lifting a finger after the chain changed (base feature 3)

`fingerLiftedAfterChainChangeStopsOnlyTheLoopItStarted` plays the six cases of the shared
`chain-release-v1` corpus (`app/src/sharedTest/assets/chain-release-v1`, a byte copy of
unipad.io `meta/unipack-conformance/chain-release-v1`, checked against its manifest) with
injected fingers on the play screen, Slide Mode off: a pad held while another finger taps a chain
button, a pack that moves the chain 100 ms after a sound, two held pads with one lifted, a
cancelled finger followed by a new press of the same pad, a pad cycling through infinite, once and
three-times sounds, and a plain press and release. The corpus packs have no lights, so the test
gives each one a keyLED animation per sound line (`ChainReleaseLights`, written next to the unpacked
pack; the corpus files are unchanged): it lights a cell of its own in the pack's last columns and
repeats as often as its sound plays, endlessly for an infinite loop. Each step checks the sound
requests, which loops are still running, the chain, the press lights and which of those cells are
lit; lifting or cancelling a finger must stop only the infinite loop and the endless LED animation
that finger started.

The screen runs in real time, so it does not check when finite samples end, the instant before a
pack's timer moves the chain, the cells of finite LED animations, or a lift of a finger the system
already ended (the system never delivers it). `ChainReleaseCorpusTest` replays the same cases
through `PadTouchTracker`, `PlayActivityViewModel`, `SoundRunner` and `LedRunner` on a virtual clock
and checks all of these. Both are request and app LED state evidence only, not audible output or a
connected Launchpad's lights. The `ChainRelease` log tag prints every step and the
corpus files' SHA-256; captures are `chain-release-CR-001-<step>` and `chain-release-CR-003-<step>`.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.kimjisub.launchpad.unipack.runner.ChainReleaseCorpusTest'
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kimjisub.launchpad.basefeatures.TouchPlaybackTest#fingerLiftedAfterChainChangeStopsOnlyTheLoopItStarted \
  --no-daemon --console=plain
adb -s "$ANDROID_SERIAL" logcat -d -s ChainRelease:I '*:S'
```

## Several fingers on the play screen

`Fingers` injects one multi-finger touchscreen gesture through `UiAutomation.injectInputEvent`
(`ACTION_DOWN`, `ACTION_POINTER_DOWN`, `ACTION_MOVE`, `ACTION_POINTER_UP`, `ACTION_UP`), so the app
receives it through the normal window dispatch. Results are the runner's sound requests
(`RecordingAudio.plays`) and the `PRESSED` light channel of every pad. Each case runs in both pad
input modes: Slide Mode off (the first-install default; a finger stays on the pad it first pressed)
and on (dragging moves the press). The test sets the preference and restores it afterwards.

| Case | Slide Mode off: `MultiTouchPadModeTest` | Slide Mode on: `MultiTouchSlideModeTest` |
|---|---|---|
| A. Two pads at once | `TouchPlaybackTest.twoSimultaneousFingersPlayBothPadsAndReleaseBoth` | `twoSimultaneousFingersPlayBothPadsAndReleaseBoth` |
| B. Five-finger chord | `fiveSimultaneousFingersPlayAndLightEveryPad` | same |
| C. Hold one pad, tap another three times | `heldPadStaysLitWhileAnotherPadIsTappedRepeatedly` | same |
| D. Drag onto the neighbour | `dragOntoNeighbourKeepsFirstPadAndPlaysNothingNew` (by design) | `dragOntoNeighbourMovesPressAndPlaysNewPad` |
| E. Two fingers dragged together | `twoFingersDraggedTogetherEachKeepTheirFirstPad` | `twoFingersDraggedTogetherEachMoveTheirOwnPad` |
| F. Lift one of two fingers | `liftingOneOfTwoFingersReleasesOnlyItsPad` | same |
| I. Palm on the margin beside the grid while a pad is held | `palmTouchingEdgeWhilePlayingPlaysNothingAndPadsKeepPlaying` | same |
| I. Palm resting on the margin before any pad is touched | `palmRestingOnEdgeBeforePlayingPlaysNothingAndPadsStillPlay` | same |
| J. Palm resting on the margin, another finger taps the chain buttons | `chainButtonSwitchesChainWhilePalmRestsOnEdge` | same |
| K. A pad held, another finger taps the chain buttons | `chainButtonSwitchesChainWhilePadIsHeld` | same |

The margin point lies between the system back-gesture zone and the leftmost pad or chain button.
In J and K the chain buttons are tapped to the second chain and back, and must request no sound.
Case G cancellation is checked by the touch cancellation regression above in both modes.
Case H, lifting a finger after the chain changed, is checked above. Leaving the screen while
fingers remain held remains a separate check.
Injected touches do not prove how many fingers a given touchscreen recognises.

## A mouse on the play screen

`Mouse` injects what a USB mouse or trackpad sends an app (Android laptops, Chromebooks, tablets
with a mouse): hover moves while no button is held, then `ACTION_DOWN`, `ACTION_BUTTON_PRESS`,
`ACTION_MOVE`, `ACTION_BUTTON_RELEASE` and `ACTION_UP`, from the mouse source with the mouse tool
type. `MouseInputTest` checks that a left click plays a pad for as long as it is held, that
hovering plays nothing, a held drag with Slide Mode off and on, that right and middle presses
leave no pad playing, and that a left click on a chain button switches chain after the pointer
crossed the pads (Compose used to stop handing the embedded chain buttons that press).

## A pad held while the screen changes or another pointer joins

These classes check what happens to a pad that is still held when something other than a
plain lift ends it. `HeldPadTest` gives every first-chain pad its own endless loop (`loop` 0 in
`keySound`, a copy of the silent WAV per pad), so each sound request names the pad that made it and
a pad left playing shows as a loop nothing stopped (`RecordingAudio.ringingLoops`). Each case
first shows the held pad played, lit and loops with the pointer still down, then makes the change.
`ReceivedInput` records every touch and mouse event the play window received (action, device,
source, pointer ID and tool type; moves left out), logs it under the `ReceivedInput` tag and
quotes it in failure messages.

`HeldPadLayoutChangeTest` holds a pad with a finger (`Fingers`) through:

| Test | Change while the pad is held |
|---|---|
| `heldPadStopsWhenScreenTurnsToPortrait` | landscape → portrait (the screen's requested orientation) |
| `heldPadStopsWhenScreenTurnsBackToLandscape` | portrait → landscape |
| `heldPadStopsWhenWindowShrinks` | display shrunk to two thirds with `wm size`, then restored |
| `heldPadStopsWhenWindowGrowsBack` | shrunk display returned with `wm size reset`; the window must match its original bounds and no override may remain |

The pads must be laid out again. If the system cancels the touch at the change (one `ACTION_CANCEL`
for the held finger), the held pad must already be dark and silent at that moment. The test then
injects one `ACTION_CANCEL` of its own for that finger (`Fingers.forgetCancelled()`), allowed to go
undelivered: Android 17 still counts an injected gesture as down after the system cancelled it in
the window, and refuses the next `ACTION_DOWN` ("pointers already down") until the injecting side
ends it too. The window, whose touch already ended, must receive nothing from that cancel
(`HeldPadTest.forgetCancelledFingers`). Without a cancel,
the held pad is either released at the change or kept until the finger lifts; lit and looping must
agree, and no other pad may play. After the lift nothing is lit or looping, and the first tap in the
new layout plays and lights the pad under it. The play screen releases held pads when it lays the
grid out again (`PadTouchTracker.setGrid`); on the API 35 and 37 emulators the system did not cancel
the touch for any of the four changes.

`HeldPadLeavingScreenTest` holds a pad with a finger, Slide Mode off and on, while the play screen
leaves the front in two ways. `heldPadStopsWhenPlayScreenIsPaused` and
`heldPadStopsWhenPlayScreenIsPausedInSlideMode` pause it behind a floating activity
(`ActivityScenario.moveToState(STARTED)`) that leaves it visible, as the notification shade or a
call screen does. `heldPadStopsWhenAppGoesHome` and `heldPadStopsWhenAppGoesHomeInSlideMode` press
Home, as when switching apps. With the finger still down, the held pad's loop must stop and its
press light go off. The play screen then comes back (`moveToState(RESUMED)`, or its task brought to
the front as from recent apps); once its window is ready again the finger lifts (or the system's
cancel, which is logged, stands for the lift), nothing is lit or looping, and pressing the same pad
again plays, lights and loops it until it is lifted.

On the API 35 and 37 emulators the system cancelled the held touch when Home hid the play window, so
the Home cases pass through the shared cancellation handling, including the injected cancel above
that lets the same pad be pressed again on Android 17. It did not cancel it when the play screen
only paused; the paused cases are the ones that check the play screen lets go of the pad itself
(`padTouch.cancel()` in `PlayActivity.onPause`).

`MixedPointerInputTest` holds a pad with one pointer while the other presses and lifts from a
second pad, then the first lifts: mouse first and finger first, each with Slide Mode off and on.
The mouse is `Mouse`. The finger is `Touchscreen`, a second touchscreen created as a kernel input
device with the platform `uinput` shell tool: events injected through UiAutomation all arrive as
the same device (`-1`). With a held injected mouse press, the API 35 emulator ends that press as
soon as an injected finger lands, and the API 37 emulator refuses the finger (`Invalid DOWN event -
pointers already down for device DeviceId(-1)`); a real mouse and touchscreen are two devices. Each case checks
one `TOOL_TYPE_MOUSE` pointer and one `TOOL_TYPE_FINGER` pointer from the touchscreen's device ID
went down and ended.

Android may still end the first device's gesture in a window when another device starts one there
(input flag `enable_multi_device_same_window_stream` off, as on the API 35 and 37 emulators; the system
logs `Canceling pointers for device …`). The test accepts that only as one `ACTION_CANCEL` for
the first pointer before the second device's press, and then requires the first pad to stop at
that moment. Otherwise both pads must play until each is lifted. Either way, lifting one pointer
must not stop the other's pad or leave its own playing. A cancelled injected mouse press is
forgotten during the assertions without sending a release. During teardown, a cancel resets
system gesture monitors that can still hold the injected mouse device after the window's cancel;
window delivery is not required for that reset. All active input injections still require success. The kernel touchscreen still receives a
physical lift, so its active slot is released even if the window cancelled its gesture.

Every virtual touchscreen has a unique name and physical port. Android can group input nodes
sharing a port even when their names differ. Failed registration and failed finger cleanup both
close the shell streams, ending `uinput` and removing its device. `InputToolsCleanupTest` checks
that overlapping devices resolve to different IDs, both disappear on close, and a lookup failure
after registration removes the device while preserving the original failure. It also checks
that cancelled-mouse teardown sends no new window event and a new mouse helper can hover; its
finger also comes from a `Touchscreen`. It runs separately
from the release preflight. Layout-test cleanup waits within the existing 10-second checks
for the display override to disappear and the play window's landscape orientation and original
bounds to return before the next test starts.

| Checked on the API 35 and 37 emulators | Result |
|---|---|
| Held pad through portrait/landscape and window shrink/grow, finger UP after the change | Pass; released at the relayout, no system cancel |
| Mouse then finger, finger then mouse, Slide Mode off and on | Pass; Android cancelled the first device at the second device's press in all four |
| Held pad while the play screen pauses, Slide Mode off and on (API 35 only) | Pass; no system cancel, the play screen released the pad |
| Held pad while Home hides the play screen, Slide Mode off and on (API 35 only) | Pass; the system cancelled the touch |

All three classes are in `BaseFeaturesSuite`. To run only one of them on the emulator:

```bash
ANDROID_SERIAL="$ANDROID_SERIAL" ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kimjisub.launchpad.basefeatures.HeldPadLayoutChangeTest
```

Check that all four layout cases ran and archive the XML and raw runner output before
starting the mixed-input invocation, which overwrites those results. Run one class per
Gradle invocation; a comma-separated selector can execute only its first class.

```bash
ANDROID_SERIAL="$ANDROID_SERIAL" ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kimjisub.launchpad.basefeatures.MixedPointerInputTest
```

`HeldPadLayoutChangeTest` resets the display size after each test; if a run is killed, run
`adb -s "$ANDROID_SERIAL" shell wm size reset` before using the device for anything else.

Not established by these tests:

- Both devices held at once in one window: needs an Android version or device that streams two
  devices to the same window; the API 35 and 37 emulators always took the cancel path.
- Free-form or split-screen window resizing: the emulator's display size was changed instead;
  freeform windows are not enabled on the test emulator.
- A real USB mouse, trackpad or finger, and the sound actually heard: a recorded play or stop
  request proves only that the app asked for it.

## Device settings the screen tests change

`DisplayOverlays` switches the emulated navigation mode and display cutout, and
`emptyLibraryGuideStaysClearOfGestureNavigation` sets the largest font size; each is restored in
`finally`. If a run is killed in between, put the emulator back before returning it:

```bash
adb -s "$ANDROID_SERIAL" shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
adb -s "$ANDROID_SERIAL" shell cmd overlay disable com.android.internal.display.cutout.emulation.hole
adb -s "$ANDROID_SERIAL" shell settings put system font_scale 1.0
# Check: only navbar.gestural is marked [x] among the navbar/cutout overlays.
adb -s "$ANDROID_SERIAL" shell cmd overlay list | grep -E 'navbar|cutout'
```

Use the navigation mode the emulator had before (`navbar.threebutton` instead of `navbar.gestural`)
if it was not gesture navigation.

## Pressing the edges of a pad

`PadEdgeTouchTest` rewrites the fixture as a single chain in which every pad has its own silent
sound file, opens it and presses each pad with one injected finger at its centre and, 4 px inside
the pad, the middle of every edge and every corner. Every press must request that pad's own sound,
and every pad must lie inside the grid the touch position is divided over. It runs three shapes:
a 4 × 3 pack with stretched pads (`squareButton=false`, the shape of the drum packs that use it),
an 8 × 8 stretched pack and an 8 × 8 square pack. Stretched pads used to be sized from the play
area before its padding was taken off, so the lower edge of each pad played the pad below and the
right edge the pad beside it; the square check keeps the usual layout covered by the same presses.

## Fixture and isolation

`FeatureScreen` reuses `TestUniPack` (small generated silent WAVs, 8 × 8 pads, two chains,
keyLED and AutoPlay) without editing its implementation. A second silent WAV gives chain 2 a
different sound ID, so changing only a label cannot pass the chain check. ZIPs contain those
same local files; file import goes through MediaStore Downloads and the Android document picker.
Every owned folder and its saved row are removed after a test. The temporary Downloads entry is
also removed. Preference/locale changes and injected modules are restored in `finally`.

`FakeNetwork` injects Retrofit services backed by an OkHttp application interceptor: it returns
ZIP bytes for the exact store fixture URL and fails on an unexpected URL. The fake catalogue
implements the merged `StoreCatalog` subscription, with the same activity attachment/detachment
contract as production. It needs no Firebase snapshots or SDK-private constructors. Keep the device offline before app launch because
application initialization still initializes Firebase/analytics.

`RecordingAudio` uses the real WAV decoder and records `SoundRunner.Engine` start/load/play/stop
requests without starting native audio output. `MidiConnection.attachTransport` temporarily wires
a driver to a packet recorder and the same connection observers/controller listeners. Tests
initialize the normal listener wiring, then attach and detach on the main thread. It refuses to
replace a real connection. Normal application code never calls it.

The production seams are optional scoped service/engine providers. Normal launches keep the original shared
Retrofit client and Oboe engine. The existing `appModule` catalogue binding keeps its production
Firebase implementation; only tests replace it. The MIDI transport entry point has no normal caller. Existing runner/MIDI unit tests
are retained to check the default paths.

## Signed release candidate

**Do not report this debug suite as verification of a signed release candidate.** Current Gradle
configuration builds androidTest against the `.dev` debug application; instrumentation lives in
the target process and needs target classes/Koin. A production-signed, non-debuggable, R8-minified
candidate has a different package/signature and may have removed those classes/seams. Rebuilding
or re-signing it for this suite would change the artifact being verified.

QA should run this suite on a debug build of the **same source commit**, record the commit and APK
hashes, then separately install the exact signed candidate APK/splits from the approved build on a
test device. Drive the eight features with external UI automation/manual checks and attach
candidate hashes, captures and results. The signed-candidate check must include startup, local ZIP
import/playback, skin/rotation, Home/lock/return and real audible output. Real server/USB checks
are separate and explicitly reported when hardware/network is unavailable. Do not replace the
candidate gate with a debug pass. A future standalone external UIAutomator test APK can cover
black-box candidate screens without using in-process injection; it cannot observe internal sound
requests or replace production repositories in an unchanged candidate.

## Things the emulator cannot establish

- USB Launchpad: use a physical supported controller, verify permission/discovery, simultaneous pads,
  chain selection, LED on/off and detach/reconnect on the exact candidate.
- Audible output/latency: perform a silent-safe headphone check, including sample timing and recovery
  after background/focus loss. A recorded play request proves only that the app asked to play.
- API 24–32: this suite's platform language/Downloads APIs require API 33+. Existing unit tests still
  apply; run candidate startup/import/playback on oldest supported real OS versions. No `@Ignore`
  or conditional pass hides this limitation.
- OEM process eviction, long locks, low-memory conditions, tablets and cutouts: run separate candidate
  checks on those devices/configurations. The rotation test asserts only insets actually present on
  the device under test; a rectangular emulator cannot prove behavior around a notch.
- Live store servers: run read-only catalog availability checks separately and
  use an explicitly designated synthetic download if end-to-end server verification is needed.
