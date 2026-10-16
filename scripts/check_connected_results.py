"""Judge a connected-test run from its JUnit XML, not from Gradle's exit code.

Gradle can finish successfully although the app APK never installed and no app test ran.
A run passes only when every module reports exactly as many tests as its sources declare,
none of them failed, errored or was skipped, and Gradle printed no install failure.

The expected count is the number of `@Test` annotations under `<module>/src/androidTest`.
Counting the sources means the number can never fall behind newly added tests.
Tests filtered by `@SdkSuppress` on an older device, or inherited from an abstract class,
make the counts differ; the run then fails with both numbers instead of passing silently.
`--suite-expected` counts the same way for a JUnit suite: the `@Test` annotations in the
sources of the classes its `@Suite.SuiteClasses` lists.

Usage: check_connected_results.py [--expected-only] [--gradle-output FILE] MODULE...
       check_connected_results.py --suite-expected SUITE_SOURCE
Prints `key=value` lines and exits 1 when the run must not count as a success.
"""
import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

TEST_ANNOTATION = re.compile(r"^\s*@(?:org\.junit\.)?Test\b", re.MULTILINE)
SUITE_CLASSES = re.compile(r"@(?:Suite\.)?SuiteClasses\s*\((.*?)\)", re.DOTALL)
SUITE_MEMBER = re.compile(r"\b(\w+)(?:::|\.)class\b")
INSTALL_FAILURE = re.compile(r"Failed to install APK|INSTALL_FAILED")
RESULTS = Path("build/outputs/androidTest-results/connected")
OUTCOMES = ("failure", "error", "skipped")


def declared_tests(path):
    return len(TEST_ANNOTATION.findall(path.read_text(encoding="utf-8")))


def expected_tests(module):
    sources = Path(module, "src/androidTest")
    return sum(declared_tests(path) for pattern in ("*.kt", "*.java") for path in sources.rglob(pattern))


def suite_expected_tests(suite_source):
    """Count the tests of every class a suite lists; each must have its source beside the suite."""
    suite_source = Path(suite_source)
    listed = SUITE_CLASSES.search(suite_source.read_text(encoding="utf-8"))
    if not listed:
        raise ValueError(f"{suite_source}: no @Suite.SuiteClasses list")
    total = 0
    for name in SUITE_MEMBER.findall(listed.group(1)):
        sources = [path for extension in ("kt", "java") for path in suite_source.parent.glob(f"{name}.{extension}")]
        if len(sources) != 1:
            raise ValueError(f"{suite_source}: no single source file for {name} beside the suite")
        total += declared_tests(sources[0])
    return total


def reported_tests(module):
    """Return (testcase count, {outcome: count}, unreadable report errors)."""
    discovered, outcomes, unreadable = 0, dict.fromkeys(OUTCOMES, 0), []
    for report in sorted(Path(module, RESULTS).rglob("TEST-*.xml")):
        try:
            cases = list(ET.parse(report).getroot().iter("testcase"))
        except ET.ParseError as error:
            unreadable.append(f"{report}: {error}")
            continue
        discovered += len(cases)
        for case in cases:
            for outcome in OUTCOMES:
                if case.find(outcome) is not None:
                    outcomes[outcome] += 1
    return discovered, outcomes, unreadable


def judge_module(module, lines, reasons):
    expected = expected_tests(module)
    discovered, outcomes, unreadable = reported_tests(module)
    lines += [f"{module}_expected={expected}", f"{module}_discovered={discovered}"]
    lines += [f"{module}_{outcome}={count}" for outcome, count in outcomes.items()]
    reasons += [f"{module}: unreadable report {error}" for error in unreadable]
    if discovered == 0:
        reasons.append(f"{module}: no JUnit results (sources have {expected})")
    elif discovered != expected:
        reasons.append(f"{module}: discovered {discovered} tests, sources have {expected}")
    reasons += [f"{module}: {count} {outcome}" for outcome, count in outcomes.items() if count]


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
    parser.add_argument("--gradle-output", help="Gradle console output to scan for install failures")
    parser.add_argument("--suite-expected", metavar="SUITE_SOURCE",
                        help="print the test count of the classes a suite source lists and exit")
    parser.add_argument("modules", nargs="*")
    args = parser.parse_args()
    if args.suite_expected:
        try:
            print(f"suite_expected={suite_expected_tests(args.suite_expected)}")
        except (OSError, ValueError) as error:
            print(error, file=sys.stderr)
            return 2
        return 0
    if not args.modules:
        parser.error("name at least one module")
    if args.expected_only:
        for module in args.modules:
            print(f"{module}_expected={expected_tests(module)}")
        return 0
    lines, reasons = [], []
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
