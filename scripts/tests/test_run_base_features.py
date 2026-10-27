"""Verify the base-features runner passes only when every test its suite's classes declare passed."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from forced_stop import kill_once_started

SCRIPTS = Path(__file__).resolve().parents[1]
SUITE_DIR = "app/src/androidTest/java/com/kimjisub/launchpad/basefeatures"
SUITE = """@RunWith(Suite::class)
@Suite.SuiteClasses(
    FirstTest::class,
    SecondTest::class,
)
class BaseFeaturesSuite
"""


def test_class(name, tests):
    body = "".join(f"    @Test fun case{i}() {{}}\n" for i in range(tests))
    return f"class {name} : PlaybackScreenTest() {{\n{body}}}\n"


def instrumentation(tests, footer=None, status_codes=()):
    lines = [f"INSTRUMENTATION_STATUS_CODE: {code}" for code in status_codes]
    return "\n".join(lines + [footer or f"OK ({tests} tests)"]) + "\n"


class BaseFeaturesRunnerTest(unittest.TestCase):
    def run_runner(self, output, classes=None, probe_output="DEVICE_SETTINGS_READY\nINSTRUMENTATION_CODE: -1\n",
                   earlier=None, kill_during_suite=False):
        """Run the script against fake adb/Gradle; `am instrument` prints `output`.

        `earlier` maps result file names to contents a previous run left behind. With
        `kill_during_suite` the suite hangs and the runner is SIGKILLed; the exit code is returned.
        """
        classes = {"FirstTest": 2, "SecondTest": 3} if classes is None else classes
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        (root / "scripts").mkdir()
        for script in ("run-base-features.sh", "check_connected_results.py", "prepare_device_settings.py"):
            if (SCRIPTS / script).exists():
                shutil.copy(SCRIPTS / script, root / "scripts" / script)
        suite_dir = root / SUITE_DIR
        suite_dir.mkdir(parents=True)
        (suite_dir / "BaseFeaturesSuite.kt").write_text(SUITE)
        (suite_dir / "PlaybackScreenTest.kt").write_text("abstract class PlaybackScreenTest\n")
        for name, tests in classes.items():
            (suite_dir / f"{name}.kt").write_text(test_class(name, tests))
        (root / "instrumentation.txt").write_text(output)
        (root / "probe.txt").write_text(probe_output)
        for name, content in (earlier or {}).items():
            path = root / ".ci-results/base-features" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
        adb = root / "sdk/platform-tools/adb"
        adb.parent.mkdir(parents=True)
        adb.write_text("#!/bin/bash\n"
                       "case \"$*\" in\n"
                       "  *getprop*) echo 34 ;;\n"
                       "  *airplane_mode_on*) echo 1 ;;\n"
                       "  *DeviceSettingsProbe*) cat probe.txt ;;\n"
                       "  *'am instrument'*) touch suite-ran; [[ -n \"${HANG:-}\" ]] && sleep 60; cat instrumentation.txt ;;\n"
                       "esac\n")
        adb.chmod(0o755)
        gradle = root / "gradlew"
        gradle.write_text("#!/bin/bash\ntouch gradle-ran\n")
        gradle.chmod(0o755)
        env = dict(os.environ, ANDROID_HOME=str(root / "sdk"))
        command = ["bash", "scripts/run-base-features.sh", "emulator-5678"]
        if kill_during_suite:
            env["HANG"] = "1"
            return root, kill_once_started(command, root, env, "suite-ran")
        result = subprocess.run(command, cwd=root, env=env, capture_output=True, text=True)
        return root, result

    def test_unconfirmed_app_settings_stop_before_running_the_suite(self):
        root, result = self.run_runner(instrumentation(5), probe_output="INSTRUMENTATION_CODE: -1\n")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((root / "suite-ran").exists())

    def test_records_settings_before_and_after_the_suite(self):
        root, result = self.run_runner(instrumentation(5))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((root / ".ci-results/base-features/device-settings.json").exists())

    def test_run_stopped_during_suite_leaves_no_earlier_summary(self):
        root, exit_code = self.run_runner(instrumentation(5), earlier={"ui-status.txt": "exit_code=0\n"},
                                          kill_during_suite=True)
        self.assertEqual(exit_code, -9)
        self.assertFalse((root / ".ci-results/base-features/ui-status.txt").exists())

    def test_refused_suite_leaves_no_earlier_summary(self):
        root, result = self.run_runner(instrumentation(2), classes={"FirstTest": 2},
                                       earlier={"ui-status.txt": "exit_code=0\n"})
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertFalse((root / ".ci-results/base-features/ui-status.txt").exists())

    def test_passes_when_footer_counts_every_declared_test(self):
        _, result = self.run_runner(instrumentation(5))
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_fails_when_fewer_or_more_tests_ran_than_declared(self):
        for tests in (4, 6, 50):
            with self.subTest(tests=tests):
                _, result = self.run_runner(instrumentation(tests))
                self.assertEqual(result.returncode, 1)
                self.assertIn("did not pass all 5 tests", result.stderr)

    def test_fails_on_failed_instrumentation_even_with_matching_count(self):
        for footer in ("FAILURES!!!\nTests run: 5,  Failures: 1", "INSTRUMENTATION_FAILED: x\nOK (5 tests)"):
            with self.subTest(footer=footer):
                _, result = self.run_runner(instrumentation(5, footer=footer))
                self.assertEqual(result.returncode, 1)
                self.assertIn("Base features failed", result.stderr)

    def test_fails_when_a_test_was_skipped(self):
        _, result = self.run_runner(instrumentation(5, status_codes=(1, 0, -4)))
        self.assertEqual(result.returncode, 1)
        self.assertIn("skipped", result.stderr)

    def test_listed_class_without_source_fails_before_building(self):
        root, result = self.run_runner(instrumentation(2), classes={"FirstTest": 2})
        self.assertEqual(result.returncode, 2)
        self.assertIn("SecondTest", result.stderr)
        self.assertFalse((root / "gradle-ran").exists())


if __name__ == "__main__":
    unittest.main()
