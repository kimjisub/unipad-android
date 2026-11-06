#!/usr/bin/env python3
"""Independently check corpus report coverage and verdicts; never infer a missing pass."""
import argparse
import hashlib
import json
from pathlib import Path


def same_json(left, right):
    """JSON booleans, numbers and strings must not impersonate each other."""
    if type(left) is not type(right):
        return False
    if isinstance(left, dict):
        return left.keys() == right.keys() and all(same_json(value, right[key]) for key, value in left.items())
    if isinstance(left, list):
        return len(left) == len(right) and all(same_json(a, b) for a, b in zip(left, right))
    return left == right


# A case the host harness cannot observe may be observed on an Android device instead; each kind of
# case has the one device recorder that can see it (merge_device_conformance.py adds the records).
DEVICE_SOURCES = {
    "sound-identity": "device-archive-decoder-runner",
    "files": "device-internal-files",
}

# The play screen's own input path (finger tracker, controller entry, the screen's auto-play
# listener); the only way the host harness may observe a screen-input case.
PLAY_SCREEN_INPUTS = "play-screen"


def device_kind(case, changed_names):
    if case.get("soundIdentity", False):
        return "sound-identity"
    if case.get("requiresCaseSensitiveFiles", False) or changed_names:
        return "files"
    return None


def check_report(corpus, report, corpus_hash):
    problems = []
    if report.get("platform") != "android":
        problems.append("report platform is not android")
    if report.get("corpusSha256") != corpus_hash:
        problems.append("report corpus hash does not match the input corpus")
    if report.get("assertions") != "checked":
        problems.append("report did not assert its results")
    cases = {case["id"]: case for case in corpus["cases"]}
    if len(cases) != len(corpus["cases"]):
        problems.append("corpus contains duplicate case IDs")
    seen = set()
    for result in report.get("cases", []):
        case_id = result.get("id")
        if case_id in seen:
            problems.append(f"{case_id}: duplicate result")
        seen.add(case_id)
        if case_id not in cases:
            problems.append(f"{case_id}: unregistered result")
            continue
        case = cases[case_id]
        if result.get("fingerprint") != case["fingerprint"]:
            problems.append(f"{case_id}: result fingerprint differs")
        status = result.get("status")
        actual = result.get("actual")
        stored_paths = result.get("storedInputPaths")
        changed_names = False
        if stored_paths is not None:
            if (not isinstance(stored_paths, list) or not all(isinstance(path, str) for path in stored_paths)
                    or len(stored_paths) != len(set(stored_paths))):
                problems.append(f"{case_id}: invalid stored input path inventory")
            else:
                expected_paths = [entry["path"] for entry in case.get("files", [])]
                changed_names = bool(expected_paths) and sorted(stored_paths) != sorted(expected_paths)
        input_adapter = result.get("inputAdapter")
        if input_adapter is not None and not (case.get("screenInputs", False) and input_adapter == PLAY_SCREEN_INPUTS):
            problems.append(f"{case_id}: input adapter {input_adapter} cannot observe this case")
            continue
        restricted = ((case.get("screenInputs", False) and input_adapter != PLAY_SCREEN_INPUTS)
                      or "android" in case.get("unobserved", {})
                      or changed_names
                      or case.get("soundIdentity", False)
                      or (case.get("observation") not in (None, "runner")
                          and (case["observation"] not in {"audio-cost", "audio-file", "audio-wave", "audio-selection", "auto-map"}
                               or result.get("operationAdapter") != case["observation"]))
                      or (case.get("requiresCaseSensitiveFiles", False)
                          and report.get("caseSensitiveFileSystem") is not True))
        if result.get("observedOn") == "device":
            kind = device_kind(case, changed_names)
            if not restricted or kind is None:
                problems.append(f"{case_id}: device observation replaces a host observation")
                continue
            if result.get("source") != DEVICE_SOURCES[kind]:
                problems.append(f"{case_id}: device observation from {result.get('source')} cannot observe a {kind} case")
                continue
            restricted = False
        elif "observedOn" in result:
            problems.append(f"{case_id}: unknown observation place {result['observedOn']}")
            continue
        if actual is None:
            if status != "unverified":
                problems.append(f"{case_id}: missing observation cannot be {status}")
            if not restricted and case.get("expectation") == "determined":
                problems.append(f"{case_id}: observable case has no result")
            continue
        if restricted:
            problems.append(f"{case_id}: generic runner claimed an observation outside its seam")
            continue
        if case.get("expectation") != "determined":
            if status != "unverified":
                problems.append(f"{case_id}: undetermined case cannot be {status}")
            continue
        known = case.get("known", {}).get("android")
        if same_json(actual, case.get("expected")):
            if status != "pass":
                problems.append(f"{case_id}: actual matches expected but status is {status}")
            if known is not None:
                problems.append(f"{case_id}: pinned difference stopped occurring")
        else:
            if status != "fail":
                problems.append(f"{case_id}: wrong observation cannot be {status}")
            accepted = [] if known is None else [known["actual"]] + [x["actual"] for x in known.get("otherActuals", [])]
            if not any(same_json(actual, pinned) for pinned in accepted):
                problems.append(f"{case_id}: unexpected app result")
    for case_id in sorted(cases.keys() - seen):
        problems.append(f"{case_id}: missing result")
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--corpus", type=Path, default=Path("app/src/test/resources/unipack-conformance/corpus.json"))
    args = parser.parse_args()
    raw = args.corpus.read_bytes()
    declared = args.corpus.with_name("corpus.sha256").read_text()
    digest = hashlib.sha256(raw).hexdigest()
    if declared != f"{digest}  corpus.json\n":
        raise SystemExit("corpus digest file does not match corpus bytes")
    problems = check_report(json.loads(raw), json.loads(args.report.read_text()), digest)
    for problem in problems:
        print(problem)
    print(f"{len(problems)} report problems")
    return int(bool(problems))


if __name__ == "__main__":
    raise SystemExit(main())
