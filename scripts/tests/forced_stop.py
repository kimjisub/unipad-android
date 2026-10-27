"""Stop a runner script the way a killed agent run or lost session does: SIGKILL, no exit handler."""
import contextlib
import os
from pathlib import Path
import signal
import subprocess
import time


def kill_once_started(command, cwd, env, marker, timeout=30):
    """Start `command`, wait until its fake test step creates `marker` in `cwd`, then SIGKILL its process group."""
    with subprocess.Popen(command, cwd=cwd, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                          start_new_session=True) as runner:
        try:
            deadline = time.monotonic() + timeout
            while not (Path(cwd) / marker).exists():
                if runner.poll() is not None or time.monotonic() > deadline:
                    raise AssertionError(f"runner never reached its test step (exit {runner.returncode})")
                time.sleep(0.05)
        finally:
            with contextlib.suppress(ProcessLookupError):
                os.killpg(runner.pid, signal.SIGKILL)
            runner.wait()
    return runner.returncode
