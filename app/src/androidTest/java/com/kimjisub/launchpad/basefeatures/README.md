# Base features preflight

This is a deterministic **debug-build integration suite**, separate from the legacy UI tests.
It taps real screens and feeds the real pack parser, importer, runners, database and MIDI driver.
It does not contact production pack servers, increment real download statistics or play audio
through the host speaker. Nothing is skipped to get a passing result.

## Run only this suite

Use a dedicated, disposable app installation on API 33 or newer with the system language set to
English and no screen-lock PIN. The suite refuses an installation containing non-fixture packs.
It only removes its named fixture folders/rows; it does not clear the whole database or app data.

On the maintenance machine, borrow and return the device through the harness:

```bash
. /Users/kimjisub/GitHub/unipad/project/paperclip/env.sh
python3 "$HARNESS/devices.py" up-android --fresh
# Set this to the serial printed above; never use another worker's device.
export ANDROID_SERIAL='<printed serial>'
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
python3 "$HARNESS/devices.py" down
```

For Gradle/Orchestrator on an exclusively borrowed device:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kimjisub.launchpad.basefeatures.BaseFeaturesSuite \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

The shell runner uses an explicit serial for every device action and checks the JUnit footer:
`adb shell am instrument` alone can exit zero after a failed test. It does not start a device. Before instrumentation it prepares both installed debug packages
with `cmd package compile -f -m speed`, matching the merged Gradle debug test preparation.
This completes code verification outside the app startup deadline; it does not change app startup,
assertions or timeouts. Debuggable packages may report a `verify` compiler filter rather than
full optimization. Archive `dumpsys package dexopt` with the run output.
No baseline fixture is downloaded. Screenshots are copied by the shell to
`/data/local/tmp/unipad_tests/basefeatures-captures/`, so they survive the target and test
applications being uninstalled at the end of a complete Gradle run. Pull them with
`adb -s <printed-serial> pull /data/local/tmp/unipad_tests` before returning the device.
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
| 3. Sound request, keyLED, chains, simultaneous fingers | `SoundRunnerTest`, `LedRunnerDeliveryTest`, `PlayActivityViewModelLedTest`, `PlayActivityTest` | `padTouchRequestsSoundFromLoadedPack`, `twoSimultaneousFingersPlayBothPadsAndReleaseBoth`, `MultiTouchPadModeTest`, `MultiTouchSlideModeTest`, `MouseInputTest`, `HeldPadLayoutChangeTest`, `MixedPointerInputTest` (see below) | Partial: automatic request IDs, displayed LED color/on/off, actual chain-button input, injected multi-finger touchscreen gestures in both pad input modes and an injected mouse on pads and chains. Audible output/latency and real fingers need a real device/headphones. |
| 4. AutoPlay start/pause/stop, practice hints | `AutoPlayRunnerTest`, `PlayActivityTest.testPlayActivityAutoPlayControls` | `autoplayStartsPausesResumesAndStopsThroughScreenControls`, `guideAndStepPracticeShowHintsAndStepWaitsForPadInput` | Partial: automatic controls, frozen/resumed progress, sound requests, guide light and practice waiting/advancing. Completing an entire practice sequence remains a separate candidate/device check; this suite checks entry and advancement. |
| 5. MIDI discovery, input, output | `MidiConnectionLifecycleTest`, driver tests, logo/velocity tests | `virtualLaunchpadIsDiscoveredPadInputPlaysAndKeyLedSendsPackets` | Partial: synthetic app-layer connection/banner, real Launchpad S decoder, pad/chain input, sound request, encoded LED on/off output, detach. USB enumeration/permission, cable/electrical behavior and real MIDI service require hardware. |
| 6. Store/download, delete/history/bookmark | `StoreTest` uses an offline `StoreCatalog`; `UniPackDownloadPathTest`, `MainActivityTest.testUnipackDeletion`, `UnipackRepositoryDeleteTest` | `offlineCatalogDownloadUpdatesResultAndLibrary`, `deletePackRemovesFilesHistoryAndBookmarkReinstallStartsFresh` | Automatic fake catalog → ZIP → downloaded state/library; delete removes files and only its record, reinstall starts without history/bookmark. Real catalog/server availability separate. |
| 7. Language, skin, rotation, bars/cutout | `SettingsTest.testInfoRowsFollowDeviceLanguage`, `ThemeTest`, `PlayActivityTest.testPlayScreenStaysClearOfSystemBarsAndCutout`, `StoreTest.testStoreLastRowClearOfSystemBars` | `languageAndSkinSelectionPersistAndPlayLoadsSelectedSkin`, `rotationKeepsPackAndChainAndPadsStayClearOfBarsAndCutout` | Partial: per-app English/Korean settings, apply/persist/render skin, both landscape rotations, pack/chain preservation, bounds vs actual system insets. Run with a cutout profile for cutout coverage; gesture/three-button modes and other form factors need repeated device configurations. |
| 8. Background/lock/return while playing | `AudioFocusPolicyTest` covers focus policy; transfer lifecycle test covers a different screen | `playingSurvivesHomeAndScreenLockWithoutLosingPackOrChain` | Partial: Home, screen off/on, silence on leave, same activity/viewmodel/process/chain and a new sound request after returning. OS process eviction/OEM lock/power policies need hardware/long-duration checks. |

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
Two more cases are not in the table yet: G, the system cancelling the touch while fingers are
down (for example when the app leaves the screen), and H, lifting a finger after the chain
changed. Their fixes are being made separately and their tests join the suite with them.
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

These two classes check what happens to a pad that is still held when something other than a
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
for the held finger), the held pad must already be dark and silent at that moment; the test sends
nothing more for that finger, since Android refuses input for a gesture it ended. Without a cancel,
the held pad is either released at the change or kept until the finger lifts; lit and looping must
agree, and no other pad may play. After the lift nothing is lit or looping, and the first tap in the
new layout plays and lights the pad under it. The play screen releases held pads when it lays the
grid out again (`PadTouchTracker.setGrid`); on the API 35 and 37 emulators the system did not cancel
the touch for any of the four changes.

`MixedPointerInputTest` holds a pad with one pointer while the other presses and lifts from a
second pad, then the first lifts: mouse first and finger first, each with Slide Mode off and on.
The mouse is `Mouse`. The finger is `Touchscreen`, a second touchscreen created as a kernel input
device with the platform `uinput` shell tool: events injected through UiAutomation all arrive as
the same device (`-1`), and Android then ends the held mouse press as soon as an injected finger
lands, which a real mouse and touchscreen (two devices) do not cause by themselves. Each case checks
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
that cancelled-mouse teardown sends no new window event and a new mouse helper can hover. It runs separately
from the 47-test release preflight. Layout-test cleanup waits within the existing 10-second checks
for the display override to disappear and the play window's landscape orientation and original
bounds to return before the next test starts.

| Checked on the API 35 and 37 emulators | Result |
|---|---|
| Held pad through portrait/landscape and window shrink/grow, finger UP after the change | Pass; released at the relayout, no system cancel |
| Mouse then finger, finger then mouse, Slide Mode off and on | Pass; Android cancelled the first device at the second device's press in all four |

Both classes are in `BaseFeaturesSuite`. To run only them on the borrowed device:

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
`adb -s "$ANDROID_SERIAL" shell wm size reset` before returning the device.

Not established by these tests:

- Both devices held at once in one window: needs an Android version or device that streams two
  devices to the same window; the API 35 and 37 emulators always took the cancel path.
- Free-form or split-screen window resizing: the emulator's display size was changed instead;
  freeform windows are not enabled on the borrowed emulator.
- A real USB mouse, trackpad or finger, and the sound actually heard: a recorded play or stop
  request proves only that the app asked for it.

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
borrowed device. Drive the eight features with external UI automation/manual checks and attach
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
  the borrowed device; a rectangular emulator cannot prove behavior around a notch.
- Live store servers: run read-only catalog availability checks separately and
  use an explicitly designated synthetic download if end-to-end server verification is needed.
