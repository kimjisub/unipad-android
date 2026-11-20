# Release native page alignment

`assembleRelease` and `bundleRelease` automatically run verification tasks. A
verification failure makes Gradle fail, including an otherwise up-to-date build,
and stops the existing fastlane build and upload lanes before a store upload.
Python 3.9 or newer must be available as `python3` on `PATH`, and the configured
Android SDK/NDK must be installed.
Existing unsigned artifacts can be verified without signing credentials. Building
release artifacts continues to use the project's existing signing configuration.

```bash
./gradlew :app:bundleRelease --no-daemon --console=plain
python3 -m unittest discover -s scripts/tests -p 'test_check_page_alignment.py' -v
```

Run the regression command with Python 3.9 as well when changing the checker to
verify compatibility with the minimum supported Python version.

The checks cover:

- Every packaged `.so` in the release APK and every module of the release AAB.
  NDK `llvm-readelf -lW` must succeed and report LOAD segments. Every LOAD segment
  of every `arm64-v8a` and `x86_64` library must have a power-of-two alignment of
  at least `0x4000` (16 KB). Errors identify the archive, ABI and library.
- 32-bit `armeabi-v7a` and `x86` libraries are inspected and their alignment is
  printed, but are exempt from the 64-bit ELF alignment requirement. In
  particular, NDK r29's 32-bit `libc++_shared.so` uses 4 KB alignment. This matches
  [Android's documented ABI scope](https://developer.android.com/guide/practices/page-sizes#elf-alignment).
- APK libraries must be stored uncompressed. SDK `zipalign -c -P 16 -v 4` checks
  the final APK's file offsets, independently of the ELF checks.
- The AAB must explicitly request enabled uncompressed native libraries with
  `PAGE_ALIGNMENT_16K` in `BundleConfig.pb`, parsed with AGP's bundletool schema.
  An AAB is not an installable ZIP: its own library offsets are not checked with
  `zipalign`. Building a bundle also builds and checks the release APK.

The two tasks can also be requested explicitly:
`checkReleaseApkPageAlignment` and `checkReleaseBundlePageAlignment`. They build
their required artifacts and run on every invocation; they do not cache a prior
verification result. Missing artifacts, tools, native libraries, unrecognized
ABIs, malformed ELF output and tool failures fail verification.

For a standalone APK or AAB ELF check:

```bash
python3 scripts/check_page_alignment.py app-release.apk \
  --readelf "$ANDROID_HOME/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" \
  --zipalign "$ANDROID_HOME/build-tools/36.0.0/zipalign"
```

On macOS use the NDK's `darwin-x86_64` folder. Standalone AAB inspection checks
ELF only; the Gradle bundle task additionally verifies `BundleConfig.pb`.

These checks establish packaging alignment, not runtime compatibility: page-size
assumptions in native code still need testing on a 16 KB device. See the
[Android verification instructions](https://developer.android.com/guide/practices/page-sizes#test).
