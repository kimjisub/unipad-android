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
`tests_regex` runner argument that runs exactly it.

Usage: check_connected_results.py [--expected-only | --plan] [--gradle-output FILE]
                                  [--test CLASS[#METHOD]]... MODULE...
Prints `key=value` lines and exits 1 when the run must not count as a success.
"""
import argparse
from collections import namedtuple
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
INSTALL_FAILURE = re.compile(r"Failed to install APK|INSTALL_FAILED")
RESULTS = Path("build/outputs/androidTest-results/connected")
OUTCOMES = ("failure", "error", "skipped")


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


def judge_module(module, lines, reasons):
    expected = expected_tests(module)
    cases, unreadable = reported_cases(module)
    discovered, outcomes = len(cases), count_outcomes(cases)
    lines += [f"{module}_expected={expected}", f"{module}_discovered={discovered}"]
    lines += [f"{module}_{outcome}={count}" for outcome, count in outcomes.items()]
    reasons += unreadable
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


def plan_selection(selectors, modules):
    classes = source_classes(modules)
    selection, reasons = resolve_selection(selectors, classes)
    if reasons:
        print("\n".join(["verdict=fail"] + [f"reason={reason}" for reason in reasons]))
        return 1
    holding = {classes[class_name].module for class_name, _ in selection.values()}
    print("modules=" + " ".join(module for module in modules if module in holding))
    print("tests_regex=" + tests_regex(selection))
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
                        help="print the modules holding the --test selection and its tests_regex, then exit")
    parser.add_argument("--gradle-output", help="Gradle console output to scan for install failures")
    parser.add_argument("--test", action="append", default=[], metavar="CLASS[#METHOD]",
                        help="judge only this selected class or method (repeatable)")
    parser.add_argument("modules", nargs="+")
    args = parser.parse_args()
    if args.expected_only:
        for module in args.modules:
            print(f"{module}_expected={expected_tests(module)}")
        return 0
    if args.plan:
        if not args.test:
            parser.error("--plan needs at least one --test")
        return plan_selection(args.test, args.modules)
    lines, reasons = [], []
    if args.test:
        judge_selection(args.test, args.modules, lines, reasons)
    else:
        for module in args.modules:
            judge_module(module, lines, reasons)
    if args.gradle_output:
        reasons += install_failures(args.gradle_output)
    lines.append("verdict=" + ("fail" if reasons else "pass"))
    lines += [f"reason={reason}" for reason in reasons]
    print("\n".join(lines))
    return 1 if reasons else 0


if __name__ == "__main__":
    sys.exit(main())
