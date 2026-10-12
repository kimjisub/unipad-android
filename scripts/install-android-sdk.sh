#!/usr/bin/env bash
set -euo pipefail

attempt_log=$(mktemp "${RUNNER_TEMP:?}/android-sdk-install.XXXXXX")
trap 'rm -f "$attempt_log"' EXIT

for attempt in 1 2 3; do
  if android --no-metrics --sdk="${ANDROID_HOME:?}" sdk install \
    platform-tools platforms/android-37.0 build-tools/36.0.0 \
    ndk/29.0.14206865 cmake/3.22.1 2>&1 | tee "$attempt_log"; then
    exit 0
  else
    install_statuses=("${PIPESTATUS[@]}")
    install_status=${install_statuses[0]}
    if (( install_status == 0 )); then
      exit "${install_statuses[1]}"
    fi
  fi

  # Retry the observed download interruption; package and configuration failures stay fatal.
  if (( attempt == 3 )) || ! grep -Fq \
    'Failed to read HTTP response: Peer disconnected' "$attempt_log"; then
    exit "$install_status"
  fi
  delay=$((attempt * 2))
  printf 'SDK download connection closed; retrying in %s seconds (attempt %s/3).\n' "$delay" "$attempt"
  sleep "$delay"
done
