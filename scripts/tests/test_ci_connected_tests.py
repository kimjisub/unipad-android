"""Verify failed Gradle tests stay failed and retain device diagnostics."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).resolve().parents[1] / "ci-connected-tests.sh"
COMMAND = ":app:connectedDebugAndroidTest :design:connectedDebugAndroidTest --no-daemon --console=plain"
TEST_PACKAGES = ("com.kimjisub.launchpad.dev", "com.kimjisub.launchpad.dev.test", "com.kimjisub.design.test")


class ConnectedTestRunnerTest(unittest.TestCase):
    def run_runner(self, gradle_exit=0, logcat_exit=0, serial="emulator-5678"):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        adb = root / "adb"
        adb.write_text("#!/bin/bash\nprintf '%s\\n' \"$*\" >> adb-calls.txt\n"
                       "if [[ \"$*\" == *'logcat -d'* ]]; then echo diagnostic-output; exit \"$LOGCAT_EXIT\"; fi\n")
        adb.chmod(0o755)
        gradle = root / "gradlew"
        gradle.write_text("#!/bin/bash\nprintf '%s\\n' \"$*\" > gradle-args.txt\necho gradle-output\nexit \"$GRADLE_EXIT\"\n")
        gradle.chmod(0o755)
        env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"],
                   GRADLE_EXIT=str(gradle_exit), LOGCAT_EXIT=str(logcat_exit))
        env.pop("ANDROID_SERIAL", None)
        if serial is not None:
            env["ANDROID_SERIAL"] = serial
        result = subprocess.run(["bash", str(RUNNER)], cwd=root, env=env,
                                capture_output=True, text=True)
        return root, result

    def assert_diagnostics(self, root, exit_code):
        self.assertEqual((root / "gradle-args.txt").read_text().strip(), COMMAND)
        self.assertIn("gradle-output", (root / ".ci-results/gradle-ui.txt").read_text())
        self.assertIn("diagnostic-output", (root / ".ci-results/logcat.txt").read_text())
        self.assertRegex((root / ".ci-results/ui-status.txt").read_text(),
                         rf"exit_code={exit_code}\nelapsed_seconds=\d+\n")
        self.assertEqual((root / "adb-calls.txt").read_text().splitlines(),
                         [f"-s emulator-5678 uninstall {package}" for package in TEST_PACKAGES]
                         + ["-s emulator-5678 logcat -c", "-s emulator-5678 logcat -d -v threadtime"])

    def test_success_runs_full_suite_on_allocated_device(self):
        root, result = self.run_runner()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assert_diagnostics(root, 0)

    def test_gradle_failure_is_not_hidden_by_tee(self):
        root, result = self.run_runner(gradle_exit=7)
        self.assertEqual(result.returncode, 7, result.stderr)
        self.assert_diagnostics(root, 7)

    def test_logcat_failure_does_not_hide_gradle_failure(self):
        root, result = self.run_runner(gradle_exit=7, logcat_exit=9)
        self.assertEqual(result.returncode, 7, result.stderr)
        self.assert_diagnostics(root, 7)

    def test_missing_device_id_fails_before_driving_any_device(self):
        root, result = self.run_runner(serial=None)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((root / "adb-calls.txt").exists())
        self.assertFalse((root / "gradle-args.txt").exists())
