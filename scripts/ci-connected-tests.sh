#!/usr/bin/env bash
set -euo pipefail

# Usage: ci-connected-tests.sh [CLASS[#METHOD]...]
# With no arguments every test of both modules runs; otherwise only the named classes or methods.
# Use only the emulator named by ANDROID_SERIAL (a local one or the hosted runner's).
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the emulator serial from adb devices}"
MODULES=(app design)
CHECKER="$(dirname "${BASH_SOURCE[0]}")/check_connected_results.py"
SELECTION=()
RUNNER_ARGS=()
# Keep the anchored selection, but exclude aggregate suites before APK discovery:
# their children otherwise run again as individually discovered classes.
for test in "$@"; do SELECTION+=(--test "$test"); done
if ! plan="$(python3 "$CHECKER" --plan ${SELECTION[@]+"${SELECTION[@]}"} "${MODULES[@]}")"; then
  echo "$plan" >&2
  exit 2
fi
read -r -a MODULES <<< "$(sed -n 's/^modules=//p' <<< "$plan")"
if (( $# )); then
  RUNNER_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.tests_regex='$(sed -n 's/^tests_regex=//p' <<< "$plan")'")
fi
not_class="$(sed -n 's/^not_class=//p' <<< "$plan")"
if [[ -n "$not_class" ]]; then
  # Quotes preserve comma-separated values through Gradle's property parsing.
  RUNNER_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.notClass='$not_class'")
fi
RESULTS="${CI_RESULTS_DIR:-.ci-results}"
mkdir -p "$RESULTS"
for output in gradle-ui.txt test-starts.txt preflight-build.txt device-settings-app.txt; do
  : > "$RESULTS/$output"
done
SETTINGS="$(dirname "${BASH_SOURCE[0]}")/prepare_device_settings.py"
device_settings() {
  python3 "$SETTINGS" "$1" --serial "$ANDROID_SERIAL" --results "$RESULTS"
}
# Results left by an earlier run must never be counted as this run's tests.
for module in "${MODULES[@]}"; do
  rm -rf "$module/build/outputs/androidTest-results/connected"
done
SECONDS=0
log_pid=""
probe_installed=0
remove_probe_packages() {
  local failed=0
  adb -s "$ANDROID_SERIAL" uninstall com.kimjisub.launchpad.dev > /dev/null || failed=1
  adb -s "$ANDROID_SERIAL" uninstall com.kimjisub.launchpad.dev.test > /dev/null || failed=1
  return "$failed"
}
finish() {
  result=$?
  trap - EXIT
  if [[ "$probe_installed" -eq 1 ]]; then
    if ! remove_probe_packages; then
      if [[ "$result" -eq 0 ]]; then result=1; fi
    fi
  fi
  if [[ -n "$log_pid" ]]; then
    kill "$log_pid" 2>/dev/null || true
    wait "$log_pid" 2>/dev/null || true
  fi
  adb -s "$ANDROID_SERIAL" logcat -d -v threadtime > "$RESULTS"/logcat.txt 2>&1 || true
  # Gradle can succeed without installing the app, so the reports decide; a Gradle failure keeps its code.
  check=0
  python3 "$CHECKER" --gradle-output "$RESULTS"/gradle-ui.txt --test-starts "$RESULTS"/test-starts.txt ${SELECTION[@]+"${SELECTION[@]}"} "${MODULES[@]}" \
    > "$RESULTS"/ui-check.txt 2>&1 || check=$?
  if [[ "$result" -eq 0 && "$check" -ne 0 ]]; then result=1; fi
  if ! device_settings finish; then
    if [[ "$result" -eq 0 ]]; then result=1; fi
  fi
  cat "$RESULTS"/device-settings-summary.txt > "$RESULTS"/ui-status.txt
  printf 'exit_code=%s\nelapsed_seconds=%s\n' "$result" "$SECONDS" >> "$RESULTS"/ui-status.txt
  cat "$RESULTS"/ui-check.txt >> "$RESULTS"/ui-status.txt
  rm -f "$RESULTS"/ui-check.txt
  cat "$RESULTS"/ui-status.txt
  exit "$result"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
device_settings prepare
# A run stopped early leaves the apps installed with the permissions its tests gave them,
# so every run starts from a device these apps are not installed on.
for package in com.kimjisub.launchpad.dev com.kimjisub.launchpad.dev.test com.kimjisub.design.test; do
  adb -s "$ANDROID_SERIAL" uninstall "$package" > /dev/null 2>&1 || true
done
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain 2>&1 | tee "$RESULTS"/preflight-build.txt
probe_installed=1
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
device_settings verify
# Preparation starts the app and creates data; Gradle must still install it fresh.
remove_probe_packages
probe_installed=0
adb -s "$ANDROID_SERIAL" logcat -c
# Stream throughout the run: the device's ring buffer can drop the first starts.
adb -s "$ANDROID_SERIAL" logcat -v threadtime TestRunner:I AndroidTestOrchestrator:I '*:S' > "$RESULTS"/test-starts.txt 2>&1 &
log_pid=$!
tasks=()
for module in "${MODULES[@]}"; do tasks+=(":$module:connectedDebugAndroidTest"); done
./gradlew "${tasks[@]}" --no-daemon --console=plain ${RUNNER_ARGS[@]+"${RUNNER_ARGS[@]}"} 2>&1 | tee "$RESULTS"/gradle-ui.txt
