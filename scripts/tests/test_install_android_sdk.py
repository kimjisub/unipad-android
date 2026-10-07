"""Check SDK installation retries only a closed download connection."""
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
INSTALLER = ROOT / "scripts/install-android-sdk.sh"
DISCONNECTED = "Failed to read HTTP response: Peer disconnected"


class AndroidSdkInstallTest(unittest.TestCase):
    def install(self, failures=0, message=DISCONNECTED, exit_code=7):
        temp = tempfile.TemporaryDirectory(dir=os.environ.get("RUNNER_TEMP"))
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        android = root / "android"
        android.write_text(f"#!{sys.executable}\n" + """
import json, os, pathlib, sys
path = pathlib.Path(os.environ['SDK_CALLS'])
calls = json.loads(path.read_text()) if path.exists() else []
calls.append(sys.argv[1:])
path.write_text(json.dumps(calls))
if len(calls) <= int(os.environ['SDK_FAILURES']):
    print(os.environ['SDK_MESSAGE'], file=sys.stderr)
    sys.exit(int(os.environ['SDK_EXIT']))
print('SDK packages installed')
""")
        android.chmod(0o755)
        sleep = root / "sleep"
        sleep.write_text("#!/bin/bash\nexit 0\n")
        sleep.chmod(0o755)
        env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"],
                   ANDROID_HOME=str(root / "sdk"), RUNNER_TEMP=str(root),
                   SDK_CALLS=str(root / "calls.json"), SDK_FAILURES=str(failures),
                   SDK_MESSAGE=message, SDK_EXIT=str(exit_code))
        command = os.environ.get("SDK_INSTALL_COMMAND", "bash " + shlex.quote(str(INSTALLER)))
        result = subprocess.run(["bash", "-e", "-o", "pipefail", "-c", command],
                                cwd=ROOT, env=env, capture_output=True, text=True, timeout=20)
        calls_path = root / "calls.json"
        calls = json.loads(calls_path.read_text()) if calls_path.exists() else []
        return result, calls

    def test_success_needs_one_install(self):
        result, calls = self.install()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(len(calls), 1)

    def test_closed_connection_retries_the_same_install(self):
        result, calls = self.install(failures=1)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[0], calls[1])
        self.assertIn(DISCONNECTED, result.stdout + result.stderr)

    def test_repeated_closed_connection_keeps_failure_after_three_attempts(self):
        result, calls = self.install(failures=10)
        self.assertEqual(result.returncode, 7, result.stdout + result.stderr)
        self.assertEqual(len(calls), 3)
        self.assertTrue(all(call == calls[0] for call in calls))

    def test_other_failure_is_returned_without_retry(self):
        result, calls = self.install(failures=1, message="Unsupported SDK package", exit_code=42)
        self.assertEqual(result.returncode, 42, result.stdout + result.stderr)
        self.assertEqual(len(calls), 1)
        self.assertIn("Unsupported SDK package", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
