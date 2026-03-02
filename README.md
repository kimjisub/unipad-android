# UniPad Android Project

<a href='https://play.google.com/store/apps/details?id=com.kimjisub.launchpad&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' height="100px" src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/></a>

**English** | [한국어](README.ko.md)

## Overview

UniPad is a performance-based rhythm game that enables connection with a launchpad and allows users to create their own beatmaps using the unique "unipack" format, fostering creativity and sharing within the community.

## Features

- Create custom beatmaps: Design your unique beatmaps and rhythms for various songs.
- Share beatmaps: Share your creations with other users in the community.
- Download community beatmaps: Access a diverse library of beatmaps created by others.
- In-app song library: Utilize the Public Domain licensed music library provided by the app.
- Frequent updates and bug fixes: Stay up-to-date with the latest features and improvements.

## Getting Started

### Prerequisites

To run this project locally, you need to have the following software installed on your computer:

1. Android Studio (latest version)
2. Android SDK (API 29-35, Android 10+)
3. Git (for cloning the repository)
4. Java Development Kit (JDK) - Android Studio's bundled JDK is recommended

### Installation

To set up this project on your local machine, follow these steps:

1. Clone the repository:
```bash
git clone https://github.com/kimjisub/unipad-android.git
cd unipad-android
```

2. Open the project in Android Studio and let it sync the Gradle files.

3. Once the sync is done, build the project and run it on your Android device or emulator.

## Building the Project

### Development Build

```bash
# Windows: Set JAVA_HOME to Android Studio's bundled JDK
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"

# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug
```

The built APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

```bash
# Build release APK (requires keystore.properties)
./gradlew assembleRelease
```

**Note:** For release builds, create `keystore.properties` in the project root with your signing credentials:
```properties
storeFile=path/to/your/keystore.jks
storePassword=yourStorePassword
keyAlias=yourKeyAlias
keyPassword=yourKeyPassword
```

## Testing

For comprehensive testing documentation including environment setup, test execution, multi-API level testing, and troubleshooting:

**📖 [Testing Guide](docs/TESTING.md)**

## Project Structure

- **UniPack System**: Custom beatmap format with sound tables, LED animations, and autoplay sequences
- **MIDI Connection**: USB MIDI communication supporting various Launchpad models (MK2, PRO, X, MK3, S)
- **Runners**: Background processors for sound playback, LED animations, and autoplay sequences
- **Database**: Room database for managing unipack metadata
- **Architecture**: MVVM with Koin dependency injection

For detailed technical documentation, see [CLAUDE.md](CLAUDE.md).

## Contributing

We welcome contributions to this project! Please follow these steps if you would like to contribute:

1. Fork the repository.
2. Create a new branch with a descriptive name (e.g., `feat/new-functionality` or `fix/bug`).
3. Commit your changes to the new branch.
4. Push your changes to your forked repository.
5. Create a new pull request in the main repository.

## License

This project is licensed under the [GNU Lesser General Public License v2.1](LICENSE.md) - see the `LICENSE.md` file for details.

## Acknowledgements

- Thank you to the original UniPad app for inspiring this open-source project.
- Thank you to all our contributors and users for making this project possible.
- For questions or more information, feel free to reach out to 0226daniel@gmail.com.
