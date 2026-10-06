#!/usr/bin/env bash
set -euo pipefail

# Use only the emulator named by ANDROID_SERIAL (a local one or the hosted runner's).
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the allocated emulator id}"
MODULES=(app design)
CHECKER="$(dirname "${BASH_SOURCE[0]}")/check_connected_results.py"
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
  python3 "$CHECKER" --gradle-output .ci-results/gradle-ui.txt "${MODULES[@]}" > .ci-results/ui-check.txt 2>&1 || check=$?
  if [[ "$result" -eq 0 && "$check" -ne 0 ]]; then result=1; fi
  printf 'exit_code=%s\nelapsed_seconds=%s\n' "$result" "$SECONDS" > .ci-results/ui-status.txt
  cat .ci-results/ui-check.txt >> .ci-results/ui-status.txt
  rm -f .ci-results/ui-check.txt
  cat .ci-results/ui-status.txt
  exit "$result"
}
trap finish EXIT
adb -s "$ANDROID_SERIAL" logcat -c
tasks=()
for module in "${MODULES[@]}"; do tasks+=(":$module:connectedDebugAndroidTest"); done
./gradlew "${tasks[@]}" --no-daemon --console=plain 2>&1 | tee .ci-results/gradle-ui.txt
