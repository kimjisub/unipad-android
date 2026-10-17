"""Verify the connected-test runner fails unless every source test ran and passed."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).resolve().parents[1] / "ci-connected-tests.sh"
COMMAND = ":app:connectedDebugAndroidTest :design:connectedDebugAndroidTest --no-daemon --console=plain"
RESULTS = "build/outputs/androidTest-results/connected"
FIRST = "com.example.FirstTest"
SECOND = "com.example.second.SecondTest"
SUPPRESSED = "@SdkSuppress(minSdkVersion = 29)\n"
SELECTED = ":app:connectedDebugAndroidTest --no-daemon --console=plain " \
           "-Pandroid.testInstrumentationRunnerArguments.tests_regex='^(?:{})$'"
FIRST_REGEX = r"com\.example\.FirstTest#.*"
SECOND_REGEX = r"com\.example\.second\.SecondTest#.*"
SECOND_METHOD_REGEX = r"com\.example\.second\.SecondTest#test1"
SELECTED_COMMAND = SELECTED.format(FIRST_REGEX + "|" + SECOND_METHOD_REGEX)
SELECTED_ALL_COMMAND = SELECTED.format(FIRST_REGEX + "|" + SECOND_REGEX)
SELECTED_FIRST_COMMAND = SELECTED.format(FIRST_REGEX)
SELECTED_SECOND_METHOD_COMMAND = SELECTED.format(SECOND_METHOD_REGEX)
TEST_PACKAGES = ("com.kimjisub.launchpad.dev", "com.kimjisub.launchpad.dev.test", "com.kimjisub.design.test")


def source(tests, class_name="ExampleTest", header=""):
    package, _, name = class_name.rpartition(".")
    body = "".join(f"    @Test\n    fun test{i}() {{}}\n" for i in range(tests))
    return (f"package {package}\n\n" if package else "") + \
        f"{header}class {name} {{\n    @get:Rule val rule = Rule()\n{body}}}\n"


def source_path(class_name):
    return "src/androidTest/java/" + class_name.replace(".", "/") + ".kt"


def case(index, outcome=None, class_name="ExampleTest"):
    return class_name, f"test{index}", outcome


def junit_xml(cases):
    rows = []
    for class_name, name, outcome in cases:
        child = f"<{outcome} message='x'>trace</{outcome}>" if outcome else ""
        rows.append(f"<testcase name='{name}' classname='{class_name}'>{child}</testcase>")
    return ("<?xml version='1.0' encoding='UTF-8' ?>\n<testsuites><testsuite name='ExampleTest'>"
            + "".join(rows) + "</testsuite></testsuites>\n")


def passed(count, class_name="ExampleTest"):
    return [case(i, class_name=class_name) for i in range(count)]


class ConnectedTestRunnerTest(unittest.TestCase):
    def run_runner(self, gradle_exit=0, logcat_exit=0, serial="emulator-5678",
                   sources=None, results=None, gradle_output="gradle-output", stale=None, tests=()):
        """Run the script with fake adb/Gradle; Gradle writes `results` ({module: cases}).

        `sources` maps a module to its ExampleTest count or to {class name: (count, header)}.
        """
        sources = {"app": 2, "design": 1} if sources is None else sources
        results = {"app": passed(2), "design": passed(1)} if results is None else results
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        for module, classes in sources.items():
            if isinstance(classes, int):
                classes = {"ExampleTest": (classes, "")}
            for class_name, (count, header) in classes.items():
                path = root / module / source_path(class_name)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source(count, class_name, header))
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
        result = subprocess.run(["bash", str(RUNNER), *tests], cwd=root, env=env,
                                capture_output=True, text=True)
        return root, result

    def status(self, root):
        return (root / ".ci-results/ui-status.txt").read_text()

    def assert_diagnostics(self, root, exit_code, command=COMMAND):
        self.assertEqual((root / "gradle-args.txt").read_text().strip(), command)
        self.assertIn("gradle-output", (root / ".ci-results/gradle-ui.txt").read_text())
        self.assertIn("diagnostic-output", (root / ".ci-results/logcat.txt").read_text())
        self.assertRegex(self.status(root), rf"exit_code={exit_code}\nelapsed_seconds=\d+\n")
        self.assertEqual((root / "adb-calls.txt").read_text().splitlines(),
                         [f"-s emulator-5678 uninstall {package}" for package in TEST_PACKAGES]
                         + ["-s emulator-5678 logcat -c", "-s emulator-5678 logcat -d -v threadtime"])

    def assert_rejected(self, root, result, *reasons, command=COMMAND):
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assert_diagnostics(root, result.returncode, command)
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
                root, result = self.run_runner(results={"app": [case(0), case(1, outcome)], "design": passed(1)})
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


    def run_selection(self, tests, app_results, app_classes=None):
        """Select tests among two app classes and the design test; Gradle writes `app_results`."""
        app_classes = app_classes or {FIRST: (2, ""), SECOND: (2, "")}
        return self.run_runner(tests=tests, sources={"app": app_classes, "design": 1},
                               results={"app": app_results})

    def test_selected_classes_run_as_one_anchored_regex_and_pass_when_all_ran(self):
        root, result = self.run_selection([FIRST, f"{SECOND}#test1"],
                                          passed(2, FIRST) + [case(1, class_name=SECOND)])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assert_diagnostics(root, 0, SELECTED_COMMAND)
        status = self.status(root)
        for line in (f"{FIRST}_expected=2", f"{FIRST}_discovered=2", f"{SECOND}#test1_expected=1",
                     f"{SECOND}#test1_discovered=1", "verdict=pass"):
            self.assertIn(line + "\n", status)
        self.assertNotIn("design_", status)

    def test_selected_class_missing_from_results_fails_with_its_name(self):
        # Gradle passing `class=A,B` runs only A and still succeeds.
        root, result = self.run_selection([FIRST, SECOND], passed(2, FIRST))
        self.assert_rejected(root, result, f"{SECOND}_discovered=0\n",
                             f"{SECOND}: discovered 0 tests, sources have 2; missing test0, test1",
                             command=SELECTED_ALL_COMMAND)

    def test_selected_test_missing_from_a_class_is_named(self):
        root, result = self.run_selection([FIRST], [case(1, class_name=FIRST)],
                                          app_classes={FIRST: (2, "")})
        self.assert_rejected(root, result, f"{FIRST}: discovered 1 tests, sources have 2; missing test0",
                             command=SELECTED_FIRST_COMMAND)

    def test_sdk_suppressed_class_with_fewer_results_fails_and_says_why(self):
        root, result = self.run_selection([FIRST], [], app_classes={FIRST: (2, SUPPRESSED)})
        self.assert_rejected(root, result, f"{FIRST}: discovered 0 tests, sources have 2",
                             "the class has @SdkSuppress(minSdkVersion = 29)",
                             command=SELECTED_FIRST_COMMAND)

    def test_failed_errored_or_skipped_selected_tests_fail(self):
        for outcome in ("failure", "error", "skipped"):
            with self.subTest(outcome=outcome):
                root, result = self.run_selection([FIRST], [case(0, class_name=FIRST),
                                                            case(1, outcome, FIRST)],
                                                  app_classes={FIRST: (2, "")})
                self.assert_rejected(root, result, f"{FIRST}: 1 {outcome}", command=SELECTED_FIRST_COMMAND)

    def test_tests_outside_the_selection_fail(self):
        root, result = self.run_selection([f"{SECOND}#test1"],
                                          [case(1, class_name=SECOND), case(0, class_name=FIRST)])
        self.assert_rejected(root, result, f"1 tests ran outside the selection: {FIRST}#test0",
                             command=SELECTED_SECOND_METHOD_COMMAND)

    def test_unknown_class_or_method_fails_before_driving_any_device(self):
        for selector in ("com.example.Missing", f"{FIRST}#missing"):
            with self.subTest(selector=selector):
                root, result = self.run_selection([selector], passed(2, FIRST))
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn(selector, result.stderr)
                self.assertFalse((root / "adb-calls.txt").exists())
                self.assertFalse((root / "gradle-args.txt").exists())


if __name__ == "__main__":
    unittest.main()
