# Android instrumented tests

The suite drives the debug app (`com.kimjisub.launchpad.dev`), its real Compose screens,
and native pad/chain views. Store tests supply a controlled catalogue to the real
`FBStoreActivity`; they do not need Firebase, network access, or production downloads.

For the deterministic 27-test release subset, coverage inventory, and signed-candidate limitations,
see [Base features](java/com/kimjisub/launchpad/basefeatures/README.md).

## Run on a borrowed API 35 emulator

Use JDK 21 and the Android SDK. For Paperclip runs on the maintenance Mac, load the machine
environment before every shell command and borrow a device through the harness:

```bash
. /Users/kimjisub/GitHub/unipad/project/paperclip/env.sh && python3 "$HARNESS/devices.py" up-android
```

Use only the printed serial as `ANDROID_SERIAL`. Run from this repository. Keep the APKs
installed after the check: uninstalling deletes every pack in the debug app's storage.

```bash
ANDROID_SERIAL=<printed-serial> ./gradlew :app:connectedDebugAndroidTest :design:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --console=plain

# A focused check; the final verification must still run both complete suites.
ANDROID_SERIAL=<printed-serial> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kimjisub.launchpad.StoreTest --console=plain
```

Debug variants request AGP's device code preparation before execution using
`android.experimental.force-aot-compilation`. This retains a fresh Orchestrator process per
test, the same APKs, all assertions, and normal release variant configuration. The offline
API 35 run without preparation captured startup ANRs during instrumentation DEX loading and
verification, before four tests entered their bodies. Android may use its debug-compatible
verification filter rather than native optimization; the purpose is to finish code validation
before starting the timed app process. This functional suite is not a cold-start benchmark.
The property is also used by the [AndroidX benchmark plugin](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/benchmark/gradle-plugin/src/main/kotlin/androidx/benchmark/gradle/BenchmarkPlugin.kt).

Repeat the exact complete-suite command three times. Each invocation must execute the connected
tasks; reports must show zero failures and zero skips. In local verification, a comma-separated
class selector executed only its first class; use one class/method per focused invocation and
check the resulting names/counts. The complete-suite command deliberately has no selector. Archive the XML and raw runner output
between runs because the next invocation overwrites them. Gradle tasks marked `SKIPPED` are not
skipped JUnit tests; use the test report totals. Do not add `@Ignore`, assumptions, class exclusion
filters, or weaker assertions to make a failing screen test green.

Reports for each module: `<module>/build/reports/androidTests/connected/debug/index.html`.
Raw results and logcats: `<module>/build/outputs/androidTest-results/connected/debug/`.
`BaseUITest` screenshots: `/data/local/tmp/unipad_tests` on the borrowed device; pull them with
`adb -s <printed-serial> pull`. Keep the emulator offline so app startup cannot send test traffic;
the catalogue fixture does not disable other application network clients. Restore the original
network state and return the device when finished:

```bash
. /Users/kimjisub/GitHub/unipad/project/paperclip/env.sh && python3 "$HARNESS/devices.py" down
```

Debug verification configures without `keystore.properties`; never copy real signing
credentials. Release signing still requires a real local key and is outside this verification. Pull requests run `.github/workflows/unit-tests.yml`; use its fake Firebase fixture
(`cp .github/fixtures/google-services.json app/google-services.json`) locally. Run debug assembly,
all debug/release Kotlin compiles, `lint`, and both modules' available unit tests locally.

## How controls and data are located

- Launch helpers await the requested activity's completed `onCreate` lifecycle event before
  existing accessibility/screen assertions. They retain the former `startActivitySync` launch
  bound (45 seconds) without waiting for global queue idleness; screen deadlines are unchanged.
- Store transition waits treat a temporarily absent Compose root as an empty pending result,
  then still require the exact store list and its displayed state within the same deadline.
- `BaseUITest` writes `TestUniPack` before each test: an 8x8 pack with two chains, LED,
  AutoPlay and silent WAV files. No existing user pack is selected or deleted.
- Main's pack row/detail use `main_pack_<folder>` / `main_detail_<folder>` semantics tags,
  exported as resource IDs for the shared UI Automator helper. Selection waits for the exact
  pack detail and retries a refreshed row only while that detail is absent.
- Settings, Theme and other Play controls use translated text/content descriptions. Old FAB and `fragment_panel` IDs are gone. Back in Play opens/closes the option
  panel; Quit returns to Main.
- `StoreTest` uses `createEmptyComposeRule` with the activity's real semantics tree. Tags
  `store_list`, `store_pack_<Firebase child key>` and `store_detail` identify the list, the exact
  row and its detail. The test checks the title and producer inside the detail, as well as the
  Download affordance. It scrolls to a known final row and verifies the row's bounds against
  system bar/cutout insets. No control is guessed by its coordinates.
- `StoreCatalog` is an activity-scoped Koin factory. Production always uses
  `FirebaseStoreCatalog`, retaining the original Firebase child/count subscriptions, key fallback,
  error handling and detach on activity destruction. Tests replace only this factory with 30
  uniquely named packs and restore the default factory and previous count afterward.
- Play options use `play_options` and `play_option_<R.string id>` tags, exported as resource IDs.
  Opening waits until the panel's full width is visible; closing waits until it is gone. Option
  lookup refetches the scroll panel and searches for the exact row with a visible switch.
  Segmented Play Mode buttons use a separate text lookup; they are not switch rows.
- Pads/chain buttons are native views without accessibility IDs. Helpers read actual `PadView` /
  `ChainView` bounds on the main thread and send device taps to those bounds. They do not infer
  pad geometry from unnamed accessibility buttons or wait for accessibility idle on every pad tap.
  The tap-order test also reads the native text views.
- Wait for an observed state, then assert it. Compose actions synchronize with recomposition;
  UI Automator helpers refetch nodes that went stale and use a 500 ms global idle wait,
  restored after each test. LED/AutoPlay animations can keep the screen busy indefinitely;
  all explicit state waits and assertions retain their original timeouts and expectations.
  Failure-only label collection avoids consuming the entire AutoPlay while building diagnostics.

## Screen test inventory

| Class and test | What it verifies |
|---|---|
| `AppLaunchTest.testAppLaunch` | Launch and remain running after permission handling. |
| `AppLaunchTest.testSplashScreenTransition` | Startup reaches the current main screen. |
| `AppLaunchTest.testMainScreenElements` | The seeded pack is present on Main. |
| `AppLaunchTest.testNavigationFlow` | App remains running after taps in the lower screen area (smoke check). |
| `AppLaunchTest.testPermissionHandling` | App remains usable after launch permissions are handled. |
| `AppLaunchTest.testPlayActivityNavigation` | Select the pack, enter Play, Back opens options, Quit returns to Main. |
| `MainActivityTest.testMainNavigationButtonsAndSettingsReturn` | Current Store/Import/Settings buttons exist; Settings opens and returns. |
| `MainActivityTest.testMainScreenSorting` | Each sort method is saved/shown; order toggles and restores. |
| `MainActivityTest.testStoreActivityNavigation` | Store opens from Main and Back returns. |
| `MainActivityTest.testUnipackDeletion` | Confirmed delete removes files/history, preserves another history, and reinstall resets history. |
| `MainActivityTest.testUnipackDeletionCancel` | Cancel preserves files and history. |
| `MainActivityTest.testUnipackDeletionFailureKeepsHistory` | Read-only pack deletion reports an error and keeps history; fixture failure is an assertion, not a skip. |
| `SettingsTest.testSettingsActivityNavigation` | Settings opens and returns to Main. |
| `SettingsTest.testSettingsModification` | Tap-order setting changes its switch and preference; category navigation works. |
| `SettingsTest.testSettingsPersistence` | Changed slide setting survives cleared-task relaunch. |
| `SettingsTest.testInfoRowsFollowDeviceLanguage` | Language/push identifier hints use translated resources. |
| `SettingsTest.testLinksWithoutHandlingAppKeepSettingsOpen` | Missing URL/mail handlers leave Settings open with an error. |
| `ThemeTest.testThemeActivityNavigation` | Open themes, apply an available alternate theme, open Add, and return; restore original theme. Refetch Apply during preview recomposition and require the stored selection within the existing deadline. |
| `StoreTest.testStoreActivityNavigation` | Real store activity opens and Back returns with the fixture installed. |
| `StoreTest.testStoreUnipackBrowsing` | Scroll both ends, select a known row, verify its detail, close detail then Store. |
| `StoreTest.testStoreLastRowClearOfSystemBars` | Final row is outside system bars/cutouts and opens the correct detail. |
| `StoreTest.testNoPermissionDialogBeforeDownload` | On API 24–29, only required storage permissions are granted as test setup. Real launcher starts Splash, then Main/Store/detail browsing without dismissing permission dialogs. Notification permission is denied before startup and remains denied after browsing on API 33+. Lifecycle assertions prevent bypassing Splash. |
| `PlayActivityTest.testPlayActivityControls` | Feedback/LED controls toggle; AutoPlay transport appears and Play/Pause changes. |
| `PlayActivityTest.testPlayActivityChainSwitching` | Chain controls can switch while Play remains active. |
| `PlayActivityTest.testPlayActivityRecording` | Recording setting toggles and play input remains available. |
| `PlayActivityTest.testPlayActivityVolumeControl` | Hardware volume changes and pad taps keep Play active. |
| `PlayActivityTest.testPlayActivityAutoPlayControls` | Pause, Next, Previous, Resume and disable use current transport controls. |
| `PlayActivityTest.testPlayActivityPadViewPatterns` | Corners, lines, diagonals, ring, rapid, grid, spiral and zigzag input keep Play active. |
| `PlayActivityTest.testImportOpensPickerAndCancelReturnsToMain` | Current Import opens the system picker; cancel returns to Main. No longer ignored. |
| `PlayActivityTest.testReconnectOpensDeviceSelectionFromSettings` | Settings reconnect opens device selection and returns. No longer ignored. |
| `PlayActivityTest.testPlayActivityLEDAnimation` | LED/feedback options and repeated pad input keep Play active. |
| `PlayActivityTest.testPlayActivityTraceLogFeature` | Trace Log toggles through input and chain changes. |
| `PlayActivityTest.testTapOrderNumbersNeedTraceLogSwitch` | Exact tap order numbers occur on the expected pads only with Trace Log enabled. |
| `PlayActivityTest.testPlayActivityUIVisibilityFeatures` | Hide UI hides/restores Menu; Back still reaches options; watermark switches restore. |
| `PlayActivityTest.testPlayActivityRecordingClipboard` | Stopping recording copies chain and touch commands to the clipboard. |
| `PlayActivityTest.testPlayScreenStaysClearOfSystemBarsAndCutout` | Pad grid, chains, Menu and Quit stay within the safe area. |
| `PlayOptionPanelSkinContrastTest.defaultSkin`, `darkSkin`, `midGreySkin`, `knownLimitGreySkin` | Actual screenshot text/icon contrast on each skin, including documented colour limits. |
| `DiagnosticTest.testDiagnoseUIHierarchy` | Diagnostic screen/tree capture; not a replacement for a behavioural assertion. |

The expected number of tests is the count of `@Test` annotations in each module's
`src/androidTest`; print it with `python3 scripts/check_connected_results.py --expected-only app design`.
A green report with fewer discovered tests is insufficient: Gradle can finish successfully when
the app APK failed to install and no app test ran. GitHub CI does not run the UI suite; run
`ANDROID_SERIAL=<device> bash scripts/ci-connected-tests.sh` on a local emulator before pushing.
It clears earlier results, then fails unless each module's JUnit XML lists exactly the expected
number with no failure, error or skip and Gradle printed no install failure. It records the counts
and reasons in `.ci-results/ui-status.txt`. On API 24–28 `UniPackImportOverlapDeviceTest` is
filtered by `@SdkSuppress`, so that script expects an API 29+ device.

The former FAB method names now describe Import, reconnect and the Main navigation buttons.
The issue records the old-to-new mapping so earlier failure reports remain traceable.
Some smoke checks only prove that the app stays on its screen; stronger basic-function scenarios
are being added separately under JIS-179. They are not claimed here as audio or hardware proof.

## Other instrumented checks (included in the complete run)

| Class | Tests / purpose |
|---|---|
| `PlayOptionPanelContrastTest` | `defaultSkinPanelUsesDarkReadableText`, `fallbackDarkPanelKeepsWhiteText`, `skinPanelColoursStayReadable`: panel text contrast. `defaultSkinKeepsItsAccentAndDarkensOnlyQuit`, `darkSkinLiftsDefaultAccentAndKeepsQuit`, `midGreySkinMarksStayReadable`: accent/arrow/Quit contrast. `readableSkinsKeepTheirTextColours`, `midGreyCardFillsThinJustEnoughForBodyContrast`, `midGreySkinTextReachesBodyContrast`, `knownLimitsGetTheMostReadableColour`, `readableSkinAccentIsPreserved`: text/fill contrast limits and preserved readable colours. |
| `AppDatabaseMigrationTest` | `migrate1To2_keepsBookmarksAndPlayHistory`, `v1Database_opensThroughTheAppBuilder`, `newerDatabase_isWipedInsteadOfThrowing`: real SQLite upgrade/open/downgrade. |
| `UnipackRepositoryDeleteTest` | `delete_removesOnlyThatPacksRow`, `reinstallAfterDelete_startsWithoutHistory`, `delete_missingRowCountsAsDeleted`: real Room delete history behaviour. |
| `ZipThemeResourcesTest` | `zipTheme_loadsIconAndImages`, `zipTheme_acceptsTheAlternateStemsAndExtensions`: theme image resource loading. |
| `TransferConfigurationLifecycleTest` | `workspaceSourceSurvivesLifecycleAndEdits`, `sourceSelectionSurvivesLifecycle`: filtering, selection, recreation and background/foreground. |
| `UniPackImportOverlapDeviceTest` | `overlappingImportsKeepBothResultsAndExistingPack`: simultaneous imports preserve both packs and the existing pack. API 29+ platform APIs; API 35 full runs do not skip it. |
| design `ExampleInstrumentedTest.useAppContext` | Design module instrumentation uses the expected target package. |

`StoreCatalogTest` is a JVM unit test for the extracted feed: original key fallback, added/changed
pack events, count forwarding and detaching both subscriptions.

## Verification history

The September 26–27 record predates this work: 36 tests, 13 failures before the first FAB migration,
then 35 passes and one Store browsing failure on offline API 35. By `f093afba`, that migration was
already in main. JIS-178 reruns the complete current suite before changing it, preserves every
existing screen scenario, and records the new baseline and three final runs on the issue.

Historical ignored Import/Reconnect tests had been reported as failures by the connected test
engine. They are now ordinary tests of the actual Import and Settings controls. No test is ignored.

## Limits

- This suite does not prove Firebase production availability or production downloads. It tests
  store UI with a controlled catalogue; real HTTP/import behaviour has separate code tests.
- The instrumentation lives in the app process. Settings persistence rebuilds the cleared task;
  it does not kill and restart that process.
- Emulator checks do not prove real USB MIDI hardware behavior, audible output, or every OS/device.
- The deletion failure fixture requires storage that honors permission bits. If it cannot be made
  read-only, the test fails explicitly and restores permissions instead of silently skipping.

## Overlapping file imports (isolated device regression)

`UniPackImportOverlapDeviceTest` runs the real importer and pack reader against two controlled
Android input pipes. Both ZIPs have the same display name and different metadata and silent WAV
files. A third pack already exists under that name. Both input requests must reach their gates
before either receives bytes; the first import then finishes before the second input is released.
The test checks separate output folders, every relative file path and SHA-256, the unchanged
existing pack, parsed titles and sound counts, and temporary ZIP cleanup. Timestamps and operation
ids appear in the instrumentation output. The main thread only constructs the importers and runs
their normal callbacks; all waits have a 15-second limit and run off the main thread.

This test requires API 29+ for `ContentResolver.wrap`. It uses a unique directory under the app's
cache, redirects only its import cache and input resolver, removes that directory in `finally`,
and cancels only notifications carrying its unique ZIP name. It never uses the library workspace,
plays sound, launches an activity, or contacts a pack server. Failure and cancellation preservation
remain covered separately by `UniPackInstallOverlapTest`; this device test covers two successes.

Borrow an emulator through the maintenance harness first and use only the printed serial:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class com.kimjisub.launchpad.UniPackImportOverlapDeviceTest \
  com.kimjisub.launchpad.dev.test/androidx.test.runner.AndroidJUnitRunner
```

Keep the device offline during this check so application startup cannot send test traffic.
Restore its prior network state and return it through the harness afterward. Direct instrumentation
avoids Gradle uninstalling existing app data. `adb` exits zero even for a JUnit failure: require
`OK (1 test)` and status code `0` for the test, not just the shell exit code.

For the before/after comparison, export parent `c7bb0af67578be3cea90ea812499a0f81aa336e3`
of fix merge `5a8bca8d5d6b3ac90786ef49857e86b52a3980af` into a separate run-owned directory,
copy this exact test there, build both APKs, and run the same command on the borrowed device.
That source selects the output path in each constructor before creating the folder, so this
schedule deterministically completes both imports into the same folder and fails the distinct
folder assertion; the final hashes also show the second pack replacing the first. Record the
exported source, test hash, APK hashes, raw output, and exit codes. Do not switch the working branch.
