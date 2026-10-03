#!/usr/bin/env bash
set -euo pipefail

# Use only the device allocated by the local harness or the hosted runner.
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the allocated emulator id}"
mkdir -p .ci-results
SECONDS=0
finish() {
  result=$?
  trap - EXIT
  adb -s "$ANDROID_SERIAL" logcat -d -v threadtime > .ci-results/logcat.txt 2>&1 || true
  printf 'exit_code=%s\nelapsed_seconds=%s\n' "$result" "$SECONDS" > .ci-results/ui-status.txt
  exit "$result"
}
trap finish EXIT
adb -s "$ANDROID_SERIAL" logcat -c
./gradlew :app:connectedDebugAndroidTest :design:connectedDebugAndroidTest --no-daemon --console=plain 2>&1 | tee .ci-results/gradle-ui.txt
