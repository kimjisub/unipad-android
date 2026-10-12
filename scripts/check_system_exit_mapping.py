"""Verify that release optimization preserves the six system-exit exception types."""

import re
import sys
from pathlib import Path

EXCEPTION_NAMES = (
    "LowMemoryExit", "KillSignalExit", "MemoryLimiterExit",
    "InitializationFailureExit", "ExcessiveResourceExit", "FreezerExit",
)
PREFIX = "com.kimjisub.launchpad.diagnostics.SystemExitReporter$"


def main():
    if len(sys.argv) != 2:
        print("Usage: python3 scripts/check_system_exit_mapping.py <release mapping.txt>")
        return 2
    mapping = dict(re.findall(r"^([^# \n]+) -> ([^ \n]+):$", Path(sys.argv[1]).read_text(), re.MULTILINE))
    failed = False
    for name in EXCEPTION_NAMES:
        original = PREFIX + name
        target = mapping.get(original)
        if target != original:
            print(f"FAIL: {original} -> {target or 'missing (removed or merged)'}")
            failed = True
        else:
            print(f"PASS: {original} -> {target}")
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
