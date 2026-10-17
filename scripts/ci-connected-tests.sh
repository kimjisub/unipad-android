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
# Gradle hands the device only the first of comma-separated classes, so a selection
# runs as one anchored tests_regex and is judged test by test.
if (( $# )); then
  for test in "$@"; do SELECTION+=(--test "$test"); done
  if ! plan="$(python3 "$CHECKER" --plan "${SELECTION[@]}" "${MODULES[@]}")"; then
    echo "$plan" >&2
    exit 2
  fi
  read -r -a MODULES <<< "$(sed -n 's/^modules=//p' <<< "$plan")"
  RUNNER_ARGS=("-Pandroid.testInstrumentationRunnerArguments.tests_regex='$(sed -n 's/^tests_regex=//p' <<< "$plan")'")
fi
mkdir -p .ci-results
# Results left by an earlier run must never be counted as this run's tests.
for module in "${MODULES[@]}"; do
  rm -rf "$module/build/outputs/androidTest-results/connected"
done
SECONDS=0
finish() {
  result=$?
  trap - EXIT
  adb -s "$ANDROID_SERIAL" logcat -d -v threadtime > .ci-results/logcat.txt 2>&1 || true
  # Gradle can succeed without installing the app, so the reports decide; a Gradle failure keeps its code.
  check=0
  python3 "$CHECKER" --gradle-output .ci-results/gradle-ui.txt ${SELECTION[@]+"${SELECTION[@]}"} "${MODULES[@]}" \
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
tasks=()
for module in "${MODULES[@]}"; do tasks+=(":$module:connectedDebugAndroidTest"); done
./gradlew "${tasks[@]}" --no-daemon --console=plain ${RUNNER_ARGS[@]+"${RUNNER_ARGS[@]}"} 2>&1 | tee .ci-results/gradle-ui.txt
