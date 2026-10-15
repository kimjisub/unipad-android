"""Verify the connected-test runner fails unless every source test ran and passed."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).resolve().parents[1] / "ci-connected-tests.sh"
COMMAND = ":app:connectedDebugAndroidTest :design:connectedDebugAndroidTest --no-daemon --console=plain"
RESULTS = "build/outputs/androidTest-results/connected"
TEST_PACKAGES = ("com.kimjisub.launchpad.dev", "com.kimjisub.launchpad.dev.test", "com.kimjisub.design.test")


def source(tests):
    body = "".join(f"    @Test\n    fun test{i}() {{}}\n" for i in range(tests))
    return f"class ExampleTest {{\n    @get:Rule val rule = Rule()\n{body}}}\n"


def junit_xml(cases):
    rows = []
    for i, outcome in enumerate(cases):
        child = f"<{outcome} message='x'>trace</{outcome}>" if outcome else ""
        rows.append(f"<testcase name='test{i}' classname='ExampleTest'>{child}</testcase>")
    return ("<?xml version='1.0' encoding='UTF-8' ?>\n<testsuites><testsuite name='ExampleTest'>"
            + "".join(rows) + "</testsuite></testsuites>\n")


def passed(count):
    return [None] * count


class ConnectedTestRunnerTest(unittest.TestCase):
    def run_runner(self, gradle_exit=0, logcat_exit=0, serial="emulator-5678",
                   sources=None, results=None, gradle_output="gradle-output", stale=None):
        """Run the script with fake adb/Gradle; Gradle writes `results` ({module: cases})."""
        sources = {"app": 2, "design": 1} if sources is None else sources
        results = {"app": passed(2), "design": passed(1)} if results is None else results
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        for module, tests in sources.items():
            path = root / module / "src/androidTest/java/ExampleTest.kt"
            path.parent.mkdir(parents=True)
            path.write_text(source(tests))
        for module, cases in (stale or {}).items():
            path = root / module / RESULTS / "debug/TEST-old.xml"
            path.parent.mkdir(parents=True)
            path.write_text(junit_xml(cases))
        for module, cases in results.items():
            path = root / "fake-results" / module / RESULTS / "debug/emulator-5678 - 15/TEST-new.xml"
            path.parent.mkdir(parents=True)
            path.write_text(junit_xml(cases))
        (root / "fake-gradle-output.txt").write_text(gradle_output + "\n")
        adb = root / "adb"
        adb.write_text("#!/bin/bash\nprintf '%s\\n' \"$*\" >> adb-calls.txt\n"
                       "if [[ \"$*\" == *'logcat -d'* ]]; then echo diagnostic-output; exit \"$LOGCAT_EXIT\"; fi\n")
        adb.chmod(0o755)
        gradle = root / "gradlew"
        gradle.write_text("#!/bin/bash\nprintf '%s\\n' \"$*\" > gradle-args.txt\n"
                          "if [[ -d fake-results ]]; then cp -R fake-results/. .; fi\n"
                          "cat fake-gradle-output.txt\nexit \"$GRADLE_EXIT\"\n")
        gradle.chmod(0o755)
        env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"],
                   GRADLE_EXIT=str(gradle_exit), LOGCAT_EXIT=str(logcat_exit))
        env.pop("ANDROID_SERIAL", None)
        if serial is not None:
            env["ANDROID_SERIAL"] = serial
        result = subprocess.run(["bash", str(RUNNER)], cwd=root, env=env,
                                capture_output=True, text=True)
        return root, result

    def status(self, root):
        return (root / ".ci-results/ui-status.txt").read_text()

    def assert_diagnostics(self, root, exit_code):
        self.assertEqual((root / "gradle-args.txt").read_text().strip(), COMMAND)
        self.assertIn("gradle-output", (root / ".ci-results/gradle-ui.txt").read_text())
        self.assertIn("diagnostic-output", (root / ".ci-results/logcat.txt").read_text())
        self.assertRegex(self.status(root), rf"exit_code={exit_code}\nelapsed_seconds=\d+\n")
        self.assertEqual((root / "adb-calls.txt").read_text().splitlines(),
                         [f"-s emulator-5678 uninstall {package}" for package in TEST_PACKAGES]
                         + ["-s emulator-5678 logcat -c", "-s emulator-5678 logcat -d -v threadtime"])

    def assert_rejected(self, root, result, *reasons):
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assert_diagnostics(root, result.returncode)
        status = self.status(root)
        self.assertIn("verdict=fail", status)
        for reason in reasons:
            self.assertIn(reason, status)

    def test_success_runs_full_suite_on_allocated_device(self):
        root, result = self.run_runner()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assert_diagnostics(root, 0)
        status = self.status(root)
        for line in ("app_expected=2", "app_discovered=2", "design_expected=1",
                     "design_discovered=1", "verdict=pass"):
            self.assertIn(line + "\n", status)

    def test_gradle_failure_is_not_hidden_by_tee(self):
        root, result = self.run_runner(gradle_exit=7)
        self.assertEqual(result.returncode, 7, result.stderr)
        self.assert_diagnostics(root, 7)

    def test_logcat_failure_does_not_hide_gradle_failure(self):
        root, result = self.run_runner(gradle_exit=7, logcat_exit=9)
        self.assertEqual(result.returncode, 7, result.stderr)
        self.assert_diagnostics(root, 7)

    def test_gradle_failure_keeps_its_exit_code_when_results_are_also_wrong(self):
        root, result = self.run_runner(gradle_exit=7, results={"design": passed(1)})
        self.assertEqual(result.returncode, 7, result.stderr)
        self.assertIn("verdict=fail", self.status(root))

    def test_missing_device_id_fails_before_driving_any_device(self):
        root, result = self.run_runner(serial=None)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((root / "adb-calls.txt").exists())
        self.assertFalse((root / "gradle-args.txt").exists())

    def test_successful_gradle_without_app_results_fails(self):
        root, result = self.run_runner(results={"design": passed(1)})
        self.assert_rejected(root, result, "app_discovered=0\n", "app: no JUnit results")

    def test_discovered_count_must_equal_source_count(self):
        for discovered in (1, 3):
            with self.subTest(discovered=discovered):
                root, result = self.run_runner(results={"app": passed(discovered), "design": passed(1)})
                self.assert_rejected(root, result, "app_expected=2\n", f"app_discovered={discovered}\n",
                                     f"app: discovered {discovered} tests, sources have 2")

    def test_failed_errored_or_skipped_tests_fail(self):
        for outcome in ("failure", "error", "skipped"):
            with self.subTest(outcome=outcome):
                root, result = self.run_runner(results={"app": [None, outcome], "design": passed(1)})
                self.assert_rejected(root, result, "app_discovered=2\n", f"app: 1 {outcome}")

    def test_install_failure_in_gradle_output_fails(self):
        for line in ("Failed to install APK(s): app-debug.apk",
                     "INSTALL_FAILED_VERSION_DOWNGRADE: Downgrade detected"):
            with self.subTest(line=line):
                root, result = self.run_runner(gradle_output=f"gradle-output\n{line}\nBUILD SUCCESSFUL")
                self.assert_rejected(root, result, f"install failure: {line}")

    def test_previous_run_results_are_not_counted(self):
        root, result = self.run_runner(results={"design": passed(1)}, stale={"app": passed(2)})
        self.assert_rejected(root, result, "app_discovered=0\n")
        self.assertFalse((root / "app" / RESULTS / "debug/TEST-old.xml").exists())


if __name__ == "__main__":
    unittest.main()
