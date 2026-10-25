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
mkdir -p .ci-results
# Results left by an earlier run must never be counted as this run's tests.
for module in "${MODULES[@]}"; do
  rm -rf "$module/build/outputs/androidTest-results/connected"
done
SECONDS=0
log_pid=""
finish() {
  result=$?
  trap - EXIT
  if [[ -n "$log_pid" ]]; then
    kill "$log_pid" 2>/dev/null || true
    wait "$log_pid" 2>/dev/null || true
  fi
  adb -s "$ANDROID_SERIAL" logcat -d -v threadtime > .ci-results/logcat.txt 2>&1 || true
  # Gradle can succeed without installing the app, so the reports decide; a Gradle failure keeps its code.
  check=0
  python3 "$CHECKER" --gradle-output .ci-results/gradle-ui.txt --test-starts .ci-results/test-starts.txt ${SELECTION[@]+"${SELECTION[@]}"} "${MODULES[@]}" \
    > .ci-results/ui-check.txt 2>&1 || check=$?
  if [[ "$result" -eq 0 && "$check" -ne 0 ]]; then result=1; fi
  printf 'exit_code=%s\nelapsed_seconds=%s\n' "$result" "$SECONDS" > .ci-results/ui-status.txt
  cat .ci-results/ui-check.txt >> .ci-results/ui-status.txt
  rm -f .ci-results/ui-check.txt
  cat .ci-results/ui-status.txt
  exit "$result"
}
trap finish EXIT
# A run stopped early leaves the apps installed with the permissions its tests gave them,
# so every run starts from a device these apps are not installed on.
for package in com.kimjisub.launchpad.dev com.kimjisub.launchpad.dev.test com.kimjisub.design.test; do
  adb -s "$ANDROID_SERIAL" uninstall "$package" > /dev/null 2>&1 || true
done
adb -s "$ANDROID_SERIAL" logcat -c
# Stream throughout the run: the device's ring buffer can drop the first starts.
adb -s "$ANDROID_SERIAL" logcat -v threadtime TestRunner:I AndroidTestOrchestrator:I '*:S' > .ci-results/test-starts.txt 2>&1 &
log_pid=$!
tasks=()
for module in "${MODULES[@]}"; do tasks+=(":$module:connectedDebugAndroidTest"); done
./gradlew "${tasks[@]}" --no-daemon --console=plain ${RUNNER_ARGS[@]+"${RUNNER_ARGS[@]}"} 2>&1 | tee .ci-results/gradle-ui.txt
