"""Judge a connected-test run from its JUnit XML, not from Gradle's exit code.

Gradle can finish successfully although the app APK never installed and no app test ran.
A run passes only when every module reports exactly as many tests as its sources declare,
none of them failed, errored or was skipped, and Gradle printed no install failure.

The expected count is the number of `@Test` annotations under `<module>/src/androidTest`.
Counting the sources means the number can never fall behind newly added tests.
Tests filtered by `@SdkSuppress` on an older device, or inherited from an abstract class,
make the counts differ; the run then fails with both numbers instead of passing silently.

A run of selected tests (`--test CLASS[#METHOD]`, repeated) is judged per selection instead:
every test the selected class declares (or the selected method) must be in the JUnit XML,
and no other test may be. `--plan` prints the modules holding the selection and the
`tests_regex` runner argument and aggregate suites to exclude during discovery.
With no selections, `--plan` prints all modules and the same suite exclusions.

`--suite FILE` prints how many tests the JUnit `@Suite.SuiteClasses` in FILE runs, counted
the same way from the listed classes' sources; a listed class without tests is an error.

Usage: check_connected_results.py [--expected-only | --plan | --suite FILE] [--gradle-output FILE]
                                  [--test CLASS[#METHOD]]... MODULE...
Prints `key=value` lines and exits 1 when the run must not count as a success.
"""
import argparse
from collections import Counter, namedtuple
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

TEST_ANNOTATION = re.compile(r"^\s*@(?:org\.junit\.)?Test\b", re.MULTILINE)
TEST_NAME = re.compile(r"^\s*@(?:org\.junit\.)?Test\b(?:\([^)]*\))?\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*"
                       r"(?:(?:public|override|open|internal|suspend|final)\s+)*(?:fun|void)\s+(`[^`]+`|\w+)",
                       re.MULTILINE)
PACKAGE = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
SDK_SUPPRESS = re.compile(r"@SdkSuppress\([^)]*\)")
SUITE_CLASSES = re.compile(r"@(?:Suite\.)?SuiteClasses\((.*?)\)\s*\n", re.DOTALL)
CLASS_LITERAL = re.compile(r"([\w.]+)::class")
INSTALL_FAILURE = re.compile(r"Failed to install APK|INSTALL_FAILED")
RESULTS = Path("build/outputs/androidTest-results/connected")
OUTCOMES = ("failure", "error", "skipped")
SUITE_RUNNER = re.compile(r"@(?:org\.junit\.runner\.)?RunWith\(\s*(?:org\.junit\.runners\.)?Suite(?:::class|\.class)\s*\)")
TEST_START = re.compile(r"\bTestRunner\s*: started: (.+)\(([^()]+)\)\s*$")


SourceClass = namedtuple("SourceClass", "module path names unnamed suppressions")
Case = namedtuple("Case", "classname name outcome")


def source_files(module):
    sources = Path(module, "src/androidTest")
    return (path for pattern in ("*.kt", "*.java") for path in sources.rglob(pattern))


def expected_tests(module):
    return sum(len(TEST_ANNOTATION.findall(path.read_text(encoding="utf-8")))
               for path in source_files(module))


def source_classes(modules):
    """Map each class with tests, named by its package and file name, to its declared tests."""
    classes = {}
    for module in modules:
        for path in source_files(module):
            text = path.read_text(encoding="utf-8")
            annotations = len(TEST_ANNOTATION.findall(text))
            if not annotations:
                continue
            names = [name.strip("`") for name in TEST_NAME.findall(text)]
            package = PACKAGE.search(text)
            class_name = f"{package.group(1)}.{path.stem}" if package else path.stem
            classes[class_name] = SourceClass(module, path, names, annotations - len(names),
                                              SDK_SUPPRESS.findall(text))
    return classes


def reported_cases(module):
    """Return (test cases in the module's JUnit XML, unreadable report errors)."""
    cases, unreadable = [], []
    for report in sorted(Path(module, RESULTS).rglob("TEST-*.xml")):
        try:
            elements = list(ET.parse(report).getroot().iter("testcase"))
        except ET.ParseError as error:
            unreadable.append(f"{module}: unreadable report {report}: {error}")
            continue
        for element in elements:
            outcome = next((name for name in OUTCOMES if element.find(name) is not None), None)
            cases.append(Case(element.get("classname"), element.get("name"), outcome))
    return cases, unreadable


def count_outcomes(cases):
    outcomes = dict.fromkeys(OUTCOMES, 0)
    for case in cases:
        if case.outcome:
            outcomes[case.outcome] += 1
    return outcomes


def duplicate_cases(cases):
    counts = Counter((case.classname, case.name) for case in cases)
    return [f"duplicate test {classname}#{name}: {count} reports"
            for (classname, name), count in sorted(counts.items()) if count > 1]


def judge_module(module, lines, reasons):
    expected = expected_tests(module)
    cases, unreadable = reported_cases(module)
    discovered, outcomes = len(cases), count_outcomes(cases)
    lines += [f"{module}_expected={expected}", f"{module}_discovered={discovered}"]
    lines += [f"{module}_{outcome}={count}" for outcome, count in outcomes.items()]
    reasons += unreadable + duplicate_cases(cases)
    classes = source_classes([module])
    expected_names = {(classname, name) for classname, source in classes.items() for name in source.names}
    reported_names = {(case.classname, case.name) for case in cases}
    for classname, name in sorted(expected_names - reported_names):
        reasons.append(f"{module}: missing {name} in {classname}")
    for classname, name in sorted(reported_names - expected_names):
        reasons.append(f"{module}: unexpected test {classname}#{name}")
    if discovered == 0:
        reasons.append(f"{module}: no JUnit results (sources have {expected})")
    elif discovered != expected:
        reasons.append(f"{module}: discovered {discovered} tests, sources have {expected}")
    reasons += [f"{module}: {count} {outcome}" for outcome, count in outcomes.items() if count]


def resolve_selection(selectors, classes):
    """Return ({selector: (class name, expected test names)}, reasons a selector names no test)."""
    selection, reasons = {}, []
    for selector in selectors:
        class_name, _, method = selector.partition("#")
        source = classes.get(class_name)
        if source is None:
            reasons.append(f"{selector}: no class with @Test methods has this name")
            continue
        if source.unnamed:
            reasons.append(f"{selector}: {source.unnamed} @Test methods in {source.path} have unreadable names")
        if method and method not in source.names:
            reasons.append(f"{selector}: {class_name} declares no @Test {method}")
            continue
        selection[selector] = (class_name, [method] if method else source.names)
    return selection, reasons


def tests_regex(selection):
    """AndroidJUnitRunner matches tests_regex against `class#method`."""
    alternatives = (re.escape(class_name) + "#" + (".*" if selector == class_name else re.escape(names[0]))
                    for selector, (class_name, names) in selection.items())
    return "^(?:" + "|".join(alternatives) + ")$"


def aggregate_suites(modules):
    suites = []
    for module in modules:
        for path in source_files(module):
            text = path.read_text(encoding="utf-8")
            if SUITE_RUNNER.search(text):
                package = PACKAGE.search(text)
                suites.append(f"{package.group(1)}.{path.stem}" if package else path.stem)
    return sorted(suites)


def plan_selection(selectors, modules):
    classes = source_classes(modules)
    selection, reasons = resolve_selection(selectors, classes)
    if reasons:
        print("\n".join(["verdict=fail"] + [f"reason={reason}" for reason in reasons]))
        return 1
    holding = {classes[class_name].module for class_name, _ in selection.values()} if selectors else set(modules)
    planned_modules = [module for module in modules if module in holding]
    print("modules=" + " ".join(planned_modules))
    print("not_class=" + ",".join(aggregate_suites(planned_modules)))
    if selectors:
        print("tests_regex=" + tests_regex(selection))
    return 0


def suite_expected(suite, modules):
    """Print the suite's test count; listed classes resolve like Kotlin names in the suite's package."""
    text = Path(suite).read_text(encoding="utf-8")
    listed = SUITE_CLASSES.search(text)
    names = CLASS_LITERAL.findall(listed.group(1)) if listed else []
    package = PACKAGE.search(text)
    classes = source_classes(modules)
    reasons = [] if names else [f"{suite}: no @Suite.SuiteClasses with ::class entries"]
    expected = 0
    for name in names:
        class_name = name if "." in name or not package else f"{package.group(1)}.{name}"
        source = classes.get(class_name)
        if source is None:
            reasons.append(f"{suite}: {class_name} declares no @Test in {' '.join(modules)}")
        else:
            expected += len(source.names) + source.unnamed
    if reasons:
        print("\n".join(["verdict=fail"] + [f"reason={reason}" for reason in reasons]))
        return 1
    print(f"suite_expected={expected}")
    return 0


def judge_selection(selectors, modules, lines, reasons):
    classes = source_classes(modules)
    selection, unresolved = resolve_selection(selectors, classes)
    reasons += unresolved
    cases = []
    for module in modules:
        module_cases, unreadable = reported_cases(module)
        cases += module_cases
        reasons += unreadable
    reasons += duplicate_cases(cases)
    for module in modules:
        expected = {(class_name, name) for class_name, names in selection.values()
                    if classes[class_name].module == module for name in names}
        discovered = len(reported_cases(module)[0])
        lines += [f"{module}_expected={len(expected)}", f"{module}_discovered={discovered}"]
    claimed = set()
    for selector, (class_name, expected) in selection.items():
        ran = [case for case in cases if case.classname == class_name
               and (selector == class_name or case.name == expected[0])]
        claimed.update(ran)
        lines += [f"{selector}_expected={len(expected)}", f"{selector}_discovered={len(ran)}"]
        missing = [name for name in expected if name not in {case.name for case in ran}]
        if missing or len(ran) != len(expected):
            reason = f"{selector}: discovered {len(ran)} tests, sources have {len(expected)}"
            if missing:
                reason += "; missing " + ", ".join(missing)
            suppressions = classes[class_name].suppressions
            if suppressions:
                reason += "; the class has " + ", ".join(suppressions) + ", which skips it on older devices"
            reasons.append(reason)
        outcomes = count_outcomes(ran)
        reasons += [f"{selector}: {count} {outcome}" for outcome, count in outcomes.items() if count]
    unselected = [f"{case.classname}#{case.name}" for case in cases if case not in claimed]
    if unselected:
        reasons.append(f"{len(unselected)} tests ran outside the selection: " + ", ".join(unselected))


def judge_starts(path, modules, lines, reasons):
    """The streaming runner log retains starts even when XML merges repeated names."""
    try:
        text = Path(path).read_text(encoding="utf-8", errors="replace")
    except OSError as error:
        reasons.append(f"test starts unreadable: {error}")
        return
    starts = Counter((match.group(2), match.group(1))
                     for line in text.splitlines() if (match := TEST_START.search(line)))
    reported = {(case.classname, case.name) for module in modules for case in reported_cases(module)[0]}
    lines += [f"test_starts={sum(starts.values())}", f"test_starts_unique={len(starts)}"]
    for (classname, name), count in sorted(starts.items()):
        if count > 1:
            reasons.append(f"duplicate test start {classname}#{name}: {count} starts")
        if (classname, name) not in reported:
            reasons.append(f"test start without JUnit result {classname}#{name}")
    for classname, name in sorted(reported - starts.keys()):
        reasons.append(f"missing test start {classname}#{name}")


def install_failures(gradle_output):
    try:
        text = Path(gradle_output).read_text(encoding="utf-8", errors="replace")
    except OSError as error:
        return [f"gradle output unreadable: {error}"]
    return [f"install failure: {line.strip()}" for line in text.splitlines()
            if INSTALL_FAILURE.search(line)]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--expected-only", action="store_true",
                        help="print the source test counts and exit")
    parser.add_argument("--plan", action="store_true",
                        help="print modules, suite exclusions and an optional selection regex, then exit")
    parser.add_argument("--suite", metavar="FILE",
                        help="print the number of tests the JUnit suite in FILE runs, then exit")
    parser.add_argument("--test-starts", help="streamed TestRunner log to check for repeated or missing starts")
    parser.add_argument("--gradle-output", help="Gradle console output to scan for install failures")
    parser.add_argument("--test", action="append", default=[], metavar="CLASS[#METHOD]",
                        help="judge only this selected class or method (repeatable)")
    parser.add_argument("modules", nargs="+")
    args = parser.parse_args()
    if args.expected_only:
        for module in args.modules:
            print(f"{module}_expected={expected_tests(module)}")
        return 0
    if args.suite:
        return suite_expected(args.suite, args.modules)
    if args.plan:
        return plan_selection(args.test, args.modules)
    lines, reasons = [], []
    if args.test:
        judge_selection(args.test, args.modules, lines, reasons)
    else:
        for module in args.modules:
            judge_module(module, lines, reasons)
    if args.test_starts:
        judge_starts(args.test_starts, args.modules, lines, reasons)
    if args.gradle_output:
        reasons += install_failures(args.gradle_output)
    lines.append("verdict=" + ("fail" if reasons else "pass"))
    lines += [f"reason={reason}" for reason in reasons]
    print("\n".join(lines))
    return 1 if reasons else 0


if __name__ == "__main__":
    sys.exit(main())
