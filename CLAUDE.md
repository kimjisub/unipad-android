# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

UniPad is an Android rhythm game application that enables performance with physical launchpad controllers via USB MIDI. Users can create custom beatmaps using the "unipack" format and share them with the community.

**Key Concepts:**
- **UniPack**: Custom beatmap format containing sounds, LED animations, and autoplay sequences
- **Chain**: Performance sequences that can be switched during playback (similar to scenes/banks)
- **Runners**: Background processors for sound playback, LED animations, and autoplay sequences
- **Drivers**: Hardware abstraction layer for different Launchpad models (MK2, PRO, X, MK3, S, etc.)

## Build Commands

### Prerequisites (Windows)
```bash
# Set JAVA_HOME to Android Studio's bundled JDK
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
```

### Development Build
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install debug APK to connected device
./gradlew installDebug

# Built APK location: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
# Build release APK (requires keystore.properties)
./gradlew assembleRelease
```

**Note:** Create `keystore.properties` in project root with dummy values for debug builds:
```properties
storeFile=dummy.jks
storePassword=dummy
keyAlias=dummy
keyPassword=dummy
```

### Testing

#### Running Tests
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run UI tests (requires connected device/emulator)
./gradlew connectedDebugAndroidTest

# Run specific test class
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kimjisub.launchpad.AppLaunchTest

# Run multiple test classes
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kimjisub.launchpad.AppLaunchTest,com.kimjisub.launchpad.MainActivityTest

# Run all tests (unit + UI)
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

#### Viewing Test Reports
After running tests, open the HTML reports in your browser:
- **Unit tests**: `app/build/reports/tests/testDebugUnitTest/index.html`
- **UI tests**: `app/build/reports/androidTests/connected/index.html`

Windows (PowerShell):
```powershell
# Open unit test report
Start-Process app/build/reports/tests/testDebugUnitTest/index.html

# Open UI test report
Start-Process app/build/reports/androidTests/connected/index.html
```

#### Test Structure
UI tests are organized by feature area:
- `BaseUITest.kt` - Common base class with setup and helpers
- `AppLaunchTest.kt` - App launch and navigation tests (6 tests)
- `MainActivityTest.kt` - Main screen functionality tests (4 tests)
- `PlayActivityTest.kt` - Play activity feature tests (13 tests)
- `SettingsTest.kt` - Settings tests (3 tests)
- `StoreTest.kt` - Store tests (2 tests)
- `ThemeTest.kt` - Theme tests (1 test)
- `DiagnosticTest.kt` - Diagnostic tests (1 test)

#### Multi-API Level Testing

The project supports Android 10+ (API 29+). Gradle Managed Devices test across key API levels for compatibility.

**Configured Test Devices:**
- `pixel2Api29` - Android 10 (API 29, minSdk) - Scoped Storage baseline
- `pixel2Api33` - Android 13 (API 33) - Notification Runtime Permission
- `pixel2Api35` - Android 15 (API 35, targetSdk) - Latest

**Why These API Levels?**
- **API 29**: Minimum supported version (minSdk), Scoped Storage introduced
- **API 33**: Notification runtime permission required
- **API 35**: Target SDK (targetSdk), ensures latest Android compatibility

**Running Multi-API Tests:**

```bash
# Test on a specific API level
./gradlew pixel2Api29DebugAndroidTest
./gradlew pixel2Api33DebugAndroidTest
./gradlew pixel2Api35DebugAndroidTest

# Test on all supported API levels (3 devices, ~15-20 min)
./gradlew allApisDebugAndroidTest

# Test on minimum and maximum only (fastest, ~8-12 min)
./gradlew minAndMaxDebugAndroidTest
```

**How Managed Devices Work:**
1. Gradle automatically downloads required system images (first run only)
2. Creates and starts virtual devices automatically
3. Installs the test APK
4. Runs all tests
5. Collects results and shuts down devices
6. Generates combined test reports

**Test Reports:**
- Individual device reports: `app/build/reports/androidTests/managedDevice/debug/<device-name>/`
- Combined report: `app/build/reports/androidTests/managedDevice/debug/allDevices/`

**First-Time Setup:**
On first run, Gradle will download system images for each API level. This may take 10-30 minutes depending on your connection. Subsequent runs will be much faster as system images are cached.

**Tips:**
- Use `allApisDebugAndroidTest` for comprehensive testing before releases
- Use `minAndMaxDebugAndroidTest` for faster CI/CD pipelines
- Use specific device tasks (e.g., `pixel2Api21DebugAndroidTest`) when debugging API-specific issues
- System images are cached in `$ANDROID_HOME/system-images/`

#### Manual AVD Creation (Optional)

If you prefer to manually create and manage emulators, you can use Android Studio's AVD Manager or command line tools. These AVDs can be used with `connectedAndroidTest`.

**Using Android Studio AVD Manager:**
1. Open Android Studio
2. Go to Tools → Device Manager
3. Click "Create Device"
4. Select a device (e.g., Pixel 2)
5. Select a system image (download if needed)
6. Configure AVD settings and click "Finish"
7. Start the emulator before running tests

**Using Command Line (avdmanager):**

```bash
# Set environment variable
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"  # Windows
# export ANDROID_HOME="$HOME/Library/Android/sdk"  # macOS
# export ANDROID_HOME="$HOME/Android/Sdk"  # Linux

# List available system images
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --list | grep system-images

# Install system images
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "system-images;android-21;google_apis;x86_64"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "system-images;android-29;google_apis;x86_64"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "system-images;android-34;google_apis;x86_64"

# Create AVDs
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  --name "Pixel_2_API_21" \
  --package "system-images;android-21;google_apis;x86_64" \
  --device "pixel_2"

"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  --name "Pixel_2_API_29" \
  --package "system-images;android-29;google_apis;x86_64" \
  --device "pixel_2"

"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  --name "Pixel_2_API_34" \
  --package "system-images;android-34;google_apis;x86_64" \
  --device "pixel_2"

# List created AVDs
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" list avd
```

**Starting and Using Manual AVDs:**

```bash
# Start emulator in background
"$ANDROID_HOME/emulator/emulator" -avd Pixel_2_API_21 -no-snapshot-load &

# Wait for device to boot (check with adb)
"$ANDROID_HOME/platform-tools/adb" wait-for-device

# Run tests on connected device
./gradlew connectedDebugAndroidTest

# Stop emulator
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 emu kill
```

**Comparison: Managed Devices vs Manual AVDs**

| Feature | Managed Devices | Manual AVDs |
|---------|----------------|-------------|
| Setup | Automatic | Manual |
| Creation | First run | Before first use |
| Management | Gradle handles it | You manage it |
| Multi-API testing | Built-in groups | Custom scripts needed |
| CI/CD friendly | Yes | Requires setup |
| Best for | Automated testing | Interactive debugging |

**Recommendation:** Use Managed Devices for automated testing and CI/CD. Use manual AVDs only when you need interactive debugging or specific emulator configurations.

### Linting & Code Quality
```bash
# Run Android lint
./gradlew lint

# Format Kotlin code (if ktlint is configured)
./gradlew ktlintFormat
```

## Architecture

### Core Components

#### 1. UniPack System (`app/src/main/java/com/kimjisub/launchpad/unipack/`)
- **UniPack.kt**: Abstract base class for beatmap packages
  - Manages sound tables (3D arrays: chain x X x Y)
  - Manages LED animation tables (3D arrays: chain x X x Y)
  - Implements circular queue for multi-mapped sounds/LEDs
  - Key methods: `Sound_get()`, `Sound_push()`, `led_get()`, `led_push()`
- **UniPackFolder.kt**: File-based UniPack implementation

#### 2. Runners (`app/src/main/java/com/kimjisub/launchpad/unipack/runner/`)
Execute unipack content during playback:
- **SoundRunner**: Manages audio playback using Android MediaPlayer pools
- **LedRunner**: Handles LED animation timing and rendering
- **AutoPlayRunner**: Executes pre-programmed sequences with guide overlays
- **ChainObserver**: Observable pattern for chain switching events

#### 3. MIDI Connection (`app/src/main/java/com/kimjisub/launchpad/midi/`)
- **MidiConnection.kt**: Singleton managing USB MIDI communication
  - Uses coroutines (Dispatchers.IO) for receiving MIDI signals
  - Handles device detection and driver selection by product ID
  - Implements listener pattern for controllers
- **Drivers** (`midi/driver/`): Hardware-specific implementations
  - LaunchpadMK2, LaunchpadPRO, LaunchpadX, LaunchpadMK3, LaunchpadS
  - MidiFighter, Matrix, MasterKeyboard (generic MIDI)
  - Each driver translates between pad coordinates and MIDI notes

#### 4. PlayActivity (`app/src/main/java/com/kimjisub/launchpad/activity/PlayActivity.kt`)
Main performance activity (1200+ lines):
- Initializes runners and MIDI controller
- Manages UI state (checkboxes for feedback, LED, autoplay, trace log, recording)
- Handles pad touches and converts to sound/LED events
- **ChannelManager**: Priority-based LED channel system (GUIDE > PRESSED > LED > CHAIN > UI)
- Implements recording functionality (generates command log)
- Manages volume control via launchpad side buttons

#### 5. Database Layer (`app/src/main/java/com/kimjisub/launchpad/db/`)
- **AppDatabase.kt**: Room database ("UniPad.db")
- **Unipack** entity: Stores metadata, open count, last opened date
- **UnipackRepository**: Repository pattern for database operations
- Uses Koin for dependency injection

#### 6. Managers (`app/src/main/java/com/kimjisub/launchpad/manager/`)
- **PreferenceManager**: Wraps SharedPreferences for app settings
- **WorkspaceManager**: Manages unipack file storage locations
- **FileManager**: Utility for file operations
- **ChannelManager**: Multi-channel LED priority system
- **ColorManager**: Color conversion for LED values
- **ThemeResources**: Dynamic theme/skin loading from external packages

### Application Flow

1. **BaseApplication.kt**: Initializes Koin DI, Firebase, notification channels, logger
2. **SplashActivity**: Entry point, handles permissions and initial setup
3. **MainActivity**: Main hub for browsing/selecting unipacks
   - MainListFragment: RecyclerView of installed unipacks
   - MainTotalPanelFragment/MainPackPanelFragment: Detail panels
4. **PlayActivity**: Performance mode (loads unipack → starts runners → MIDI loop)
5. **FBStoreActivity**: Browse/download community unipacks from Firebase

### Data Structures

**Sound/LED Tables:**
```plaintext
// 3D arrays indexed by [chain][x][y]
soundTable: Array<Array<Array<ArrayList<Sound>?>>>
ledAnimationTable: Array<Array<Array<ArrayList<LedAnimation>?>>>

// Each cell contains ArrayList for multi-mapping support
// Circular queue: Sound_push() rotates, Sound_get() retrieves current
```

**Channel Priority (ChannelManager):**
```
GUIDE (autoplay hints) > PRESSED (user touch) > LED (animation) > CHAIN (selected) > UI (watermark)
```

## Development Notes

### Build Configuration (Full Modernization 2025-2026)
The project was fully modernized with the following changes:

1. **Gradle 8.0 → 9.3.1**: Updated for AGP 9.0+ support
2. **AGP 8.2.2 → 9.0.1**: Built-in Kotlin support, compileSdk/targetSdk 36
3. **Kotlin 1.9.0-Beta → 2.3.10**: Unified Kotlin version; `kotlin-android` plugin removed (AGP 9.0 built-in)
4. **KSP 2.1.21-2.0.1 → 2.3.6**: New standalone versioning (no longer tied to Kotlin version)
5. **Java target 1.8 → 17**: sourceCompatibility, targetCompatibility, and jvmTarget all set to 17
6. **JCenter removed**: All repositories migrated to google(), mavenCentral(), and JitPack
7. **JCenter library replacements**:
   - `com.polyak:icon-switch:1.0.0` → `com.github.polyak01:IconSwitch:09d0124d07` (JitPack)
   - `com.azoft.carousellayoutmanager:carousel` → `com.mig35:carousellayoutmanager:1.4.6` (mavenCentral)
   - `gun0912.ted:tedpermission` → Removed, replaced with AndroidX Activity Result API
   - `cn.aigestudio.wheelpicker:WheelPicker` → Removed (unused)
   - `com.amitshekhar.android:debug-db` → Commented out (JCenter only)
8. **Deprecated API replacements**:
   - AsyncTask → Coroutines (MidiConnection.kt)
   - Handler() → Handler(Looper.getMainLooper())
   - ProgressDialog → AlertDialog + ProgressBar
   - Environment.getExternalStorageDirectory() → context.getExternalFilesDir(null)
   - TedPermission → AndroidX ActivityResultContracts.RequestMultiplePermissions()
9. **Firebase**: Uses BOM 34.10.0 (no individual version numbers on Firebase deps)
10. **Scoped Storage**: requestLegacyExternalStorage removed, storage permissions have maxSdkVersion=32
11. **AGP 9.0 migration**: `kotlin-gradle-plugin` classpath removed, `managedDevices.devices` → `localDevices`, `nonTransitiveRClass`/`nonFinalResIds` set to true, `--add-opens` JVM args removed

### Known Issues
- **NullSafeMutableLiveData lint**: Disabled due to Kotlin 2.x incompatibility with lifecycle-lint
- **Jetifier still required**: `android.enableJetifier=true` kept because IconSwitch library uses `android.support.*` bytecode
- **OSS Licenses plugin**: `OssLicensesCleanUp` tasks disabled via workaround due to Gradle 9.x task dependency validation incompatibility

### ProGuard Configuration
Release builds use multiple ProGuard configs:
- `proguard-common.pro`: General rules
- `proguard-firebase.pro`: Firebase-specific keeps
- `proguard-okhttp3.pro`: OkHttp/networking rules
- `proguard-retrofit2.pro`: Retrofit API rules

### Dependencies
- **Kotlin 2.3.10** with coroutines 1.10.2 and serialization 1.10.0
- **KSP 2.3.6**: Annotation processing for Room (standalone versioning)
- **AndroidX**: AppCompat 1.7.1, ConstraintLayout 2.2.1, Core-KTX 1.17.0, Lifecycle 2.10.0, Room 2.8.4
- **Koin 4.1.1**: Dependency injection
- **Firebase BOM 34.10.0**: Firestore, Realtime Database, Messaging, Analytics, Crashlytics, Performance, Remote Config
- **Compose BOM 2026.02.01**: Material3, UI, Runtime
- **Retrofit 2.11.0** + OkHttp 4.12.0: API networking
- **Material 1.13.0**: Material Design components
- **Splitties 3.0.0**: Android utilities
- **zip4j 2.11.6**: UniPack archive handling

### Key Files
- `app/build.gradle`: Build configuration, requires `keystore.properties` for release
- `build.gradle`: Project-level config with dependency versions
- Design module (`design/`): Custom views (PadView, ChainView) with LED rendering

### MIDI Implementation
- USB MIDI via Android USB Host API
- 4-byte MIDI messages: [cmd, sig, note, velocity]
- Device detection by USB product ID
- Bulk transfer with 1000ms timeout
- LED velocity values: 0-127 (driver-specific color mapping)

### UniPack Format
UniPacks are ZIP archives containing:
- `info.json`: Metadata (title, producer, pad size, chain count)
- `sound/`: Audio files organized by chain/position
- `led/`: LED animation definitions
- `autoplay/`: Autoplay sequence data
