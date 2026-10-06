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
No baseline fixture is downloaded. Screenshots are saved in the target app's external files under
`basefeatures-captures/`; pull that directory using the same serial before returning the device.

## Existing coverage and the gaps this suite fills

Existing coverage is an inventory, not a claim that the entire legacy suite passed in this change.
Those tests continue to run separately; their maintenance is covered by the complete run in
[the instrumented test inventory](../../../../../README.md). The merged
`UniPackImportOverlapDeviceTest` already supplies the importer analytics argument using a no-op
sink; this branch retains that file unchanged.

| Base feature | Existing tests | New deterministic screen coverage | Status and remaining check |
|---|---|---|---|
| 1. Start, empty/populated library, settings | `AppLaunchTest`, `MainActivityTest`, `SettingsTest`, `MainActivityListLedTest`, `PackSearchTest` | `launcherEmptyLibrarySettingsThenPopulatedLibrary` | Automatic for launcher, both library states and opening/closing settings. Fresh install permission flows remain a separate check. |
| 2. ZIP and shared-code import, result, opening pack | `UniPackInstallOverlapTest`, `UniPackImportOverlapDeviceTest`, `PackImportUsageTest` check importer files/races/outcomes | `filePickerImportsZipShowsResultAndOpensImportedPack`, `sharedCodeShowsFixtureBeforeDownloading`, `acceptedShareDownloadsFixtureShowsSuccessAndImportedPackOpens` | Automatic for actual document picker/import/result/play and share confirmation/download/result/play. Real share service remains separate. |
| 3. Sound request, keyLED, chains, simultaneous fingers | `SoundRunnerTest`, `LedRunnerDeliveryTest`, `PlayActivityViewModelLedTest`, `PlayActivityTest` | `padTouchRequestsSoundFromLoadedPack`, `twoSimultaneousFingersPlayBothPadsAndReleaseBoth`, `MultiTouchPadModeTest`, `MultiTouchSlideModeTest` (see below) | Partial: automatic request IDs, displayed LED color/on/off, actual chain-button input and injected multi-finger touchscreen gestures in both pad input modes. Audible output/latency and real fingers need a real device/headphones. |
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

The margin point lies between the system back-gesture zone and the leftmost pad or chain button.
Two more cases are not in the table yet: G, the system cancelling the touch while fingers are
down (for example when the app leaves the screen), and H, lifting a finger after the chain
changed. Their fixes are being made separately and their tests join the suite with them.
Injected touches do not prove how many fingers a given touchscreen recognises.

## Fixture and isolation

`FeatureScreen` reuses `TestUniPack` (small generated silent WAVs, 8 × 8 pads, two chains,
keyLED and AutoPlay) without editing its implementation. A second silent WAV gives chain 2 a
different sound ID, so changing only a label cannot pass the chain check. ZIPs contain those
same local files; file import goes through MediaStore Downloads and the Android document picker.
Every owned folder and its saved row are removed after a test. The temporary Downloads entry is
also removed. Preference/locale changes and injected modules are restored in `finally`.

`FakeNetwork` injects Retrofit services backed by an OkHttp application interceptor: it returns
JSON and ZIP bytes for exact allowed URLs and fails on an unexpected URL. The fake catalogue
implements the merged `StoreCatalog` subscription, with the same activity attachment/detachment
contract as production. It needs no Firebase snapshots or SDK-private constructors. Keep the device offline before app launch because
application initialization still initializes Firebase/remote configuration/analytics.

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
- Live share/store servers: run read-only catalog/share metadata availability checks separately and
  use an explicitly designated synthetic download if end-to-end server verification is needed.
