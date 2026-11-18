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
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --continue --console=plain 2>&1 | tee "$results/gradle-ui.txt"
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Restore the debug probe's disabled default after an interrupted external driver;
# remove only its owned fixture/observation files before target instrumentation.
"$adb_bin" -s "$serial" shell am force-stop com.kimjisub.launchpad.dev
# Component state changes use the debuggable app's UID; Android can refuse the shell UID.
"$adb_bin" -s "$serial" shell run-as com.kimjisub.launchpad.dev pm disable com.kimjisub.launchpad.dev/com.kimjisub.launchpad.tool.ProcessRecoveryProbe
"$adb_bin" -s "$serial" shell run-as com.kimjisub.launchpad.dev rm -f files/process-recovery-events.jsonl
# The shell has the external-storage mount; run-as can access only the private record reliably.
"$adb_bin" -s "$serial" shell rm -rf /storage/emulated/0/Android/data/com.kimjisub.launchpad.dev/files/UniPack/process-recovery-pack
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
base_result=0
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$output"; then
  echo 'Base features failed; inspect the instrumentation output above.' >&2
  base_result=1
fi
if grep -q 'INSTRUMENTATION_STATUS_CODE: -4' "$output"; then
  echo 'A base feature test was skipped; the suite must run every test.' >&2
  base_result=1
fi
if ! grep -Eq "^OK \\($expected tests\\)" "$output"; then
  echo "Base features did not pass all $expected tests declared by the classes in BaseFeaturesSuite; inspect the instrumentation output above." >&2
  base_result=1
fi
# Collect the independent process-death result even when another feature failed.
# Its success must never turn a failed or incomplete base suite into a passing preflight.
# This instrumentation targets the test APK itself, so am kill can end the app while
# the driver survives to restore its recent task and verify a genuinely new process.
output="$results/process-recovery-setup.txt"
"$adb_bin" -s "$serial" shell am instrument -w -r \
  com.kimjisub.launchpad.dev.test/com.kimjisub.launchpad.ProcessRecoverySetup | tee "$output"
if ! grep -q '^PROCESS_RECOVERY_PREPARED$' "$output" || ! grep -q '^INSTRUMENTATION_CODE: -1$' "$output"; then
  echo 'Process recovery fixture preparation failed.' >&2
  exit 1
fi
output="$results/process-recovery.txt"
"$adb_bin" -s "$serial" shell am instrument -w -r \
  com.kimjisub.launchpad.dev.test/com.kimjisub.launchpad.ProcessRecoveryDriver | tee "$output"
"$adb_bin" -s "$serial" pull /data/local/tmp/unipad_tests/process-recovery "$results/process-recovery"
if grep -Eq 'PROCESS_RECOVERY_FAILED|INSTRUMENTATION_FAILED|Process crashed' "$output" || \
    ! grep -q '^PROCESS_RECOVERY_PASSED$' "$output" || \
    ! grep -q '^INSTRUMENTATION_CODE: -1$' "$output"; then
  echo 'Background process recovery failed or did not finish; inspect process-recovery.txt and its captures.' >&2
  exit 1
fi
exit "$base_result"
