#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
results="${CI_RESULTS_DIR:-.ci-results/base-features}"
# Only the exit handler writes this, so a run that stops before it leaves none, not an earlier verdict.
rm -f "$results/ui-status.txt"
serial="${1:-${ANDROID_SERIAL:-}}"
if [[ -z "$serial" ]]; then
  echo 'Pass the test device serial from adb devices (or set ANDROID_SERIAL).' >&2
  exit 2
fi
adb_bin="${ANDROID_HOME:?Set ANDROID_HOME}/platform-tools/adb"
api="$($adb_bin -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
if (( api < 33 )); then
  echo 'This suite requires API 33+ (per-app language and MediaStore Downloads); see its README for older OS checks.' >&2
  exit 2
fi
if [[ "$($adb_bin -s "$serial" shell settings get global airplane_mode_on | tr -d '\r')" != 1 ]]; then
  echo 'Disable network on the test device before running (see README).' >&2
  exit 2
fi
# The suite runs every @Test of the classes it lists; count them so the footer check keeps up with new tests.
if ! counted="$(python3 scripts/check_connected_results.py \
    --suite app/src/androidTest/java/com/kimjisub/launchpad/basefeatures/BaseFeaturesSuite.kt app)"; then
  echo "$counted" >&2
  exit 2
fi
expected="${counted#suite_expected=}"
mkdir -p "$results"
for output in gradle-ui.txt instrumentation.txt device-settings-app.txt; do
  : > "$results/$output"
done
device_settings() {
  python3 scripts/prepare_device_settings.py "$1" --adb "$adb_bin" --serial "$serial" --results "$results"
}
SECONDS=0
finish() {
  result=$?
  trap - EXIT
  if ! device_settings finish; then
    if [[ "$result" -eq 0 ]]; then result=1; fi
  fi
  cat "$results/device-settings-summary.txt" > "$results/ui-status.txt"
  printf 'exit_code=%s\nelapsed_seconds=%s\n' "$result" "$SECONDS" >> "$results/ui-status.txt"
  cat "$results/ui-status.txt"
  exit "$result"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
device_settings prepare
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain 2>&1 | tee "$results/gradle-ui.txt"
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
device_settings verify
# Match the merged debug-test preparation before the target process starts.
# Direct am instrument bypasses Gradle's force-aot-compilation setup.
for package in com.kimjisub.launchpad.dev com.kimjisub.launchpad.dev.test; do
  "$adb_bin" -s "$serial" shell cmd package compile -f -m speed "$package"
done
output="$results/instrumentation.txt"
: > "$output"
"$adb_bin" -s "$serial" shell am instrument -w -r \
  -e class com.kimjisub.launchpad.basefeatures.BaseFeaturesSuite \
  com.kimjisub.launchpad.dev.test/androidx.test.runner.AndroidJUnitRunner | tee "$output"
# adb/am can return zero even when instrumentation failed. Require the JUnit success footer
# for every test the suite's classes declare; a skipped test (status -4) still counts as run.
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$output"; then
  echo 'Base features failed; inspect the instrumentation output above.' >&2
  exit 1
fi
if grep -q 'INSTRUMENTATION_STATUS_CODE: -4' "$output"; then
  echo 'A base feature test was skipped; the suite must run every test.' >&2
  exit 1
fi
if ! grep -Eq "^OK \\($expected tests\\)" "$output"; then
  echo "Base features did not pass all $expected tests declared by the classes in BaseFeaturesSuite; inspect the instrumentation output above." >&2
  exit 1
fi
