#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
serial="${1:-${ANDROID_SERIAL:-}}"
if [[ -z "$serial" ]]; then
  echo 'Pass the serial printed by devices.py (or set ANDROID_SERIAL).' >&2
  exit 2
fi
adb_bin="${ANDROID_HOME:?Set ANDROID_HOME}/platform-tools/adb"
api="$($adb_bin -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
if (( api < 33 )); then
  echo 'This suite requires API 33+ (per-app language and MediaStore Downloads); see its README for older OS checks.' >&2
  exit 2
fi
if [[ "$($adb_bin -s "$serial" shell settings get global airplane_mode_on | tr -d '\r')" != 1 ]]; then
  echo 'Disable network on the borrowed test device before running (see README).' >&2
  exit 2
fi
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Match the merged debug-test preparation before the target process starts.
# Direct am instrument bypasses Gradle's force-aot-compilation setup.
for package in com.kimjisub.launchpad.dev com.kimjisub.launchpad.dev.test; do
  "$adb_bin" -s "$serial" shell cmd package compile -f -m speed "$package"
done
output="$(mktemp "${PAPERCLIP_RUN_SCRATCH_DIR:-${TMPDIR:-/tmp}}/basefeatures.XXXXXX")"
trap 'rm -f "$output"' EXIT
"$adb_bin" -s "$serial" shell am instrument -w -r \
  -e class com.kimjisub.launchpad.basefeatures.BaseFeaturesSuite \
  com.kimjisub.launchpad.dev.test/androidx.test.runner.AndroidJUnitRunner | tee "$output"
# adb/am can return zero even when instrumentation failed. Require the JUnit success footer.
if ! grep -Eq '^OK \(29 tests\)' "$output" || grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$output"; then
  echo 'Base features failed; inspect the instrumentation output above.' >&2
  exit 1
fi
