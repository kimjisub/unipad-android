#!/usr/bin/env bash
# Usage: run-incoming-call-probe.sh <serial> <method> <outfile>
# Needs the emulator radio on (airplane mode off) and the debug app and test APKs installed.
set -u
serial=$1; method=$2; out=$3
a(){ adb -s "$serial" "$@"; }
a logcat -c
a shell am instrument -w -r -e class "com.kimjisub.launchpad.basefeatures.IncomingCallProbe#$method" \
  com.kimjisub.launchpad.dev.test/androidx.test.runner.AndroidJUnitRunner > "$out.instr" 2>&1 &
ipid=$!
wait_log(){ for _ in $(seq 1 300); do a logcat -d -s CALL_PROBE:I | grep -q "$1" && return 0; kill -0 $ipid 2>/dev/null || return 1; sleep 0.5; done; return 1; }
if wait_log ": ready"; then echo "$(date +%T) call"; a emu gsm call 5551234; fi
if wait_log "ringing-recorded"; then echo "$(date +%T) cancel"; a emu gsm cancel 5551234; fi
wait $ipid; echo "instrument exit=$?"
a logcat -d -v time -s CALL_PROBE:I ReceivedInput:I > "$out.log"
grep -E "^OK|FAILURES|Tests run|stack=" "$out.instr" | head -5
