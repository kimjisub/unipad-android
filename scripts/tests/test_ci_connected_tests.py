"""Verify the connected-test runner fails unless every source test ran and passed."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).resolve().parents[1] / "ci-connected-tests.sh"
CHECKER = RUNNER.with_name("check_connected_results.py")
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
                   sources=None, results=None, gradle_output="gradle-output", stale=None, tests=(), starts=None,
                   probe_output="DEVICE_SETTINGS_READY\nINSTRUMENTATION_CODE: -1\n"):
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
                classes = {"DesignTest" if module == "design" else "ExampleTest": (classes, "")}
            for class_name, (count, header) in classes.items():
                path = root / module / source_path(class_name)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source(count, class_name, header))
        for module, cases in (stale or {}).items():
            path = root / module / RESULTS / "debug/TEST-old.xml"
            path.parent.mkdir(parents=True)
            path.write_text(junit_xml(cases))
        results = {module: [("DesignTest" if module == "design" and classname == "ExampleTest" else classname, name, outcome)
                            for classname, name, outcome in cases] for module, cases in results.items()}
        for module, cases in results.items():
            path = root / "fake-results" / module / RESULTS / "debug/emulator-5678 - 15/TEST-new.xml"
            path.parent.mkdir(parents=True)
            path.write_text(junit_xml(cases))
        (root / "fake-gradle-output.txt").write_text(gradle_output + "\n")
        (root / "probe.txt").write_text(probe_output)
        starts = [item for cases in results.values() for item in cases] if starts is None else starts
        (root / "fake-starts.txt").write_text("".join(
            f"10-25 12:00:00.000 123 456 I TestRunner: started: {name}({classname})\n"
            for classname, name, _ in starts))
        adb = root / "adb"
        adb.write_text("#!/bin/bash\nprintf '%s\\n' \"$*\" >> adb-calls.txt\n"
                       "if [[ \"$*\" == *'logcat -d'* ]]; then echo diagnostic-output; exit \"$LOGCAT_EXIT\"; fi\n"
                       "if [[ \"$*\" == *'TestRunner:I'* ]]; then cat fake-starts.txt; touch logcat-ready; fi\n")
        with adb.open("a") as script:
            script.write('if [[ "$*" == *DeviceSettingsProbe* ]]; then cat probe.txt; fi\n')
        adb.chmod(0o755)
        gradle = root / "gradlew"
        gradle.write_text("#!/bin/bash\n[[ \"$*\" == *assembleDebug* ]] && exit 0\nprintf '%s\\n' \"$*\" > gradle-args.txt\n"
                          "for ((i=0; i<100; i++)); do [[ -f logcat-ready ]] && break; sleep 0.01; done\n"
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

    def test_unconfirmed_app_settings_stop_before_connected_tests(self):
        root, result = self.run_runner(probe_output="INSTRUMENTATION_CODE: -1\n")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((root / "gradle-args.txt").exists())

    def status(self, root):
        return (root / ".ci-results/ui-status.txt").read_text()

    def assert_diagnostics(self, root, exit_code, command=COMMAND):
        self.assertEqual((root / "gradle-args.txt").read_text().strip(), command)
        self.assertIn("gradle-output", (root / ".ci-results/gradle-ui.txt").read_text())
        self.assertIn("diagnostic-output", (root / ".ci-results/logcat.txt").read_text())
        self.assertRegex(self.status(root), rf"exit_code={exit_code}\nelapsed_seconds=\d+\n")
        self.assertCountEqual([line for line in (root / "adb-calls.txt").read_text().splitlines()
                          if not any(part in line for part in ("shell settings", "DeviceSettingsProbe", " install -r "))],
                         [f"-s emulator-5678 uninstall {package}" for package in TEST_PACKAGES]
                         + ["-s emulator-5678 logcat -c", "-s emulator-5678 logcat -d -v threadtime",
                            "-s emulator-5678 logcat -v threadtime TestRunner:I AndroidTestOrchestrator:I *:S"])

    def assert_rejected(self, root, result, *reasons, command=COMMAND):
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assert_diagnostics(root, result.returncode, command)
        status = self.status(root)
        self.assertIn("verdict=fail", status)
        for reason in reasons:
            self.assertIn(reason, status)

    def test_records_settings_before_and_after_the_suite(self):
        root, result = self.run_runner()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((root / ".ci-results/device-settings.json").exists())

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

    def test_duplicate_names_cannot_replace_missing_tests_in_full_run(self):
        root, result = self.run_runner(results={"app": [case(0), case(0)], "design": passed(1)})
        self.assert_rejected(root, result, "duplicate test ExampleTest#test0", "missing test1")

    def test_duplicate_selected_test_is_rejected_explicitly(self):
        root, result = self.run_selection([FIRST], passed(2, FIRST) + [case(0, class_name=FIRST)])
        self.assert_rejected(root, result, f"duplicate test {FIRST}#test0", command=SELECTED_FIRST_COMMAND)

    def test_repeated_starts_fail_even_when_xml_merges_duplicates(self):
        root, result = self.run_runner(starts=passed(2) + [case(0)] + passed(1, "DesignTest"))
        self.assert_rejected(root, result, "duplicate test start ExampleTest#test0")

    def test_missing_start_evidence_fails(self):
        root, result = self.run_runner(starts=[])
        self.assert_rejected(root, result, "missing test start ExampleTest#test0")

    def test_aggregate_suite_is_excluded_from_selected_discovery(self):
        suite = "com.example.AggregateSuite"
        classes = {FIRST: (2, ""), suite: (0, "@RunWith(Suite::class)\n")}
        root, result = self.run_selection([FIRST], passed(2, FIRST), app_classes=classes)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assert_diagnostics(root, 0, SELECTED_FIRST_COMMAND +
                                f" -Pandroid.testInstrumentationRunnerArguments.notClass='{suite}'")

    def test_full_discovery_excludes_all_aggregate_suites(self):
        suites = ("com.example.AggregateSuite", "com.example.OtherSuite")
        classes = {FIRST: (2, ""), suites[0]: (0, "@RunWith(Suite::class)\n"),
                   suites[1]: (0, "@org.junit.runner.RunWith(org.junit.runners.Suite.class)\n")}
        root, result = self.run_runner(sources={"app": classes, "design": 1},
                                       results={"app": passed(2, FIRST), "design": passed(1)})
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assert_diagnostics(root, 0, COMMAND +
                                " -Pandroid.testInstrumentationRunnerArguments.notClass='" + ",".join(suites) + "'")

    def test_suite_children_reported_twice_fail(self):
        suite = "com.example.AggregateSuite"
        root, result = self.run_selection([FIRST], passed(2, FIRST) * 2,
                                          app_classes={FIRST: (2, ""), suite: (0, "@RunWith(Suite::class)\n")})
        self.assert_rejected(root, result, f"duplicate test {FIRST}#test0",
                             command=SELECTED_FIRST_COMMAND +
                             f" -Pandroid.testInstrumentationRunnerArguments.notClass='{suite}'")

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


class TestStartLogTest(unittest.TestCase):
    def judge(self, events, cases=None):
        spec = importlib.util.spec_from_file_location("checker", CHECKER)
        checker = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(checker)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            module = root / "app"
            report = module / RESULTS / "TEST-example.xml"
            report.parent.mkdir(parents=True)
            report.write_text(junit_xml(passed(1) if cases is None else cases))
            log = root / "starts.txt"
            log.write_text("".join(f"10-25 12:00:00.000 {pid} {pid} I {tag}: {message}\n"
                                   for pid, tag, message in events))
            lines, reasons = [], []
            checker.judge_starts(log, [str(module)], lines, reasons)
            return lines, reasons

    def run_events(self, pid, methods=("test0",)):
        return [(pid, "TestRunner", f"run started: {len(methods)} tests"),
                *[(pid, "TestRunner", f"started: {method}(ExampleTest)") for method in methods],
                (pid, "TestRunner", f"run finished: {len(methods)} tests, 0 failed, 0 ignored")]

    def collection(self, methods=("test0",)):
        return [(100, "AndroidTestOrchestrator", "Multiple test parameter null, starting test collection"),
                *self.run_events(200, methods)]

    def test_single_method_collection_is_not_a_second_execution(self):
        lines, reasons = self.judge(self.collection() + self.run_events(300))
        self.assertEqual(reasons, [])
        self.assertIn("collection_starts=1", lines)
        self.assertIn("test_starts=1", lines)

    def test_repeated_real_single_method_runs_fail(self):
        _, reasons = self.judge(self.collection() + self.run_events(300) + self.run_events(400))
        self.assertIn("duplicate test start ExampleTest#test0: 2 starts", reasons)

    def test_suite_and_leaf_duplicates_in_collection_fail(self):
        _, reasons = self.judge(self.collection(("test0", "test0")) + self.run_events(300))
        self.assertIn("duplicate collection test ExampleTest#test0: 2 starts", reasons)

    def test_direct_multiple_test_run_is_not_collection(self):
        lines, reasons = self.judge(self.run_events(300, ("test0", "test1")), passed(2))
        self.assertEqual(reasons, [])
        self.assertIn("collection_starts=0", lines)
        self.assertIn("test_starts=2", lines)

    def test_collection_does_not_replace_missing_execution(self):
        _, reasons = self.judge(self.collection())
        self.assertIn("missing test start ExampleTest#test0", reasons)

    def test_unfinished_collection_is_rejected(self):
        _, reasons = self.judge(self.collection()[:-1] + self.run_events(300))
        self.assertIn("incomplete test collection lifecycle", reasons)

    def test_missing_collection_record_is_rejected(self):
        events = self.collection()
        del events[2]
        _, reasons = self.judge(events + self.run_events(300))
        self.assertIn("incomplete test collection starts", reasons)

    def test_reused_collection_pid_counts_as_execution_after_collection_ends(self):
        lines, reasons = self.judge(self.collection() + self.run_events(200))
        self.assertEqual(reasons, [])
        self.assertIn("test_starts=1", lines)


class SuiteCountTest(unittest.TestCase):
    """`--suite` counts the tests of the classes a JUnit suite lists, from their sources."""

    def count(self, listed, sources):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for class_name, text in sources.items():
                path = root / "app" / source_path(class_name)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
            entries = "".join(f"    {name}::class,\n" for name in listed)
            suite = root / "app" / source_path("com.example.ExampleSuite")
            suite.write_text("package com.example\n\n@RunWith(Suite::class)\n"
                             f"@Suite.SuiteClasses(\n{entries})\nclass ExampleSuite\n", encoding="utf-8")
            return subprocess.run(["python3", str(CHECKER), "--suite", str(suite), "app"],
                                  cwd=root, capture_output=True, text=True)

    def test_counts_only_listed_classes_including_qualified_annotations(self):
        result = self.count(["FirstTest", SECOND], {
            FIRST: source(2, FIRST),
            SECOND: source(1, SECOND).replace("@Test", "@org.junit.Test"),
            "com.example.UnlistedTest": source(5, "com.example.UnlistedTest"),
        })
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout, "suite_expected=3\n")

    def test_listed_class_without_tests_fails_and_names_it(self):
        result = self.count(["FirstTest", "EmptyTest"], {
            FIRST: source(2, FIRST),
            "com.example.EmptyTest": source(0, "com.example.EmptyTest"),
        })
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("verdict=fail", result.stdout)
        self.assertIn("com.example.EmptyTest declares no @Test", result.stdout)


if __name__ == "__main__":
    unittest.main()
