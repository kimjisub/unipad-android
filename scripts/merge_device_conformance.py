#!/usr/bin/env python3
"""Put device observations into the shared Android corpus result, for the cases the host cannot observe.

The host report (UniPackConformanceCasesTest's android.json) leaves a case unverified when its harness
cannot see it: file names a macOS disk cannot keep apart and original archive identity. The device
recorder writes one record per case (conformance-files/<id>.json). A record replaces the host's empty result only when it ran the same
corpus and input, comes from the recorder for that kind of case, and its own status agrees with its
actual value. Anything else stays unverified, with the reason. The result is then checked with
check_unipack_conformance.py.

    python3 scripts/merge_device_conformance.py build/unipack-conformance/android.json \\
        <pulled conformance-files> -o android-merged.json
"""
import argparse
import hashlib
import json
from pathlib import Path

from check_unipack_conformance import (DEVICE_SOURCES, check_report, device_evidence, device_evidence_problem,
                                       device_kind, same_json, stored_names_changed)


def load_records(folders):
    """Every device record by case id, with the file it came from and its SHA-256."""
    records = {}
    for folder in folders:
        for path in sorted(Path(folder).glob("*.json")):
            raw = path.read_bytes()
            record = json.loads(raw)
            if "id" not in record or "source" not in record:
                continue
            if record["id"] in records:
                raise SystemExit(f"{record['id']}: two device records ({records[record['id']][1]} and {path})")
            records[record["id"]] = (record, path, hashlib.sha256(raw).hexdigest())
    return records


def refusal(case, host, record, corpus_hash):
    """Why this record cannot stand for the case, or None when it can."""
    if record.get("platform") != "android":
        return "the device record is not android's"
    if record.get("corpusSha256") != corpus_hash:
        return "the device record ran another corpus"
    if record.get("fingerprint") != case["fingerprint"]:
        return "the device record ran other input"
    if host.get("actual") is not None:
        return "the host observed this case itself"
    kind = device_kind(case, stored_names_changed(case, host.get("storedInputPaths")))
    if kind is None or record.get("source") != DEVICE_SOURCES[kind]:
        return f"{record.get('source')} does not observe this kind of case"
    if record.get("actual") is None:
        return "the device record has no actual value"
    if record.get("completed") is False:
        return "the device recorder did not complete: " + str(record.get("detail", ""))
    evidence = device_evidence(kind, record)
    why = device_evidence_problem(kind, evidence)
    if why is not None:
        return why
    # Extraction that changed a sound's bytes is the app's failure, whatever name it chose.
    contents_changed = kind == "sound-identity" and not evidence["contentsPreserved"]
    status = "pass" if not contents_changed and same_json(record["actual"], case.get("expected")) else "fail"
    if record.get("status") != status:
        return f"the device record says {record.get('status')} but its actual value is a {status}"
    return None


def merge(corpus, corpus_hash, host, records):
    cases = {case["id"]: case for case in corpus["cases"]}
    merged = dict(host)
    used = []
    out = []
    for result in host["cases"]:
        found = records.get(result["id"])
        if found is None:
            out.append(result)
            continue
        record, path, digest = found
        why = refusal(cases[result["id"]], result, record, corpus_hash)
        if why is not None:
            out.append({**result, "deviceRefused": f"{path.name}: {why}"})
            continue
        case = cases[result["id"]]
        kind = device_kind(case, stored_names_changed(case, result.get("storedInputPaths")))
        entry = {
            "id": result["id"], "fingerprint": result["fingerprint"], "status": record["status"],
            "detail": f"observed on {record.get('device', 'an Android device')} API {record.get('sdkInt', '?')} by {record['source']}",
            "actual": record["actual"], "observedOn": "device", "source": record["source"],
            "deviceEvidence": device_evidence(kind, record),
            "deviceRecord": {"file": path.name, "sha256": digest},
        }
        if "storedInputPaths" in result:
            entry["storedInputPaths"] = result["storedInputPaths"]
        out.append(entry)
        used.append(entry["deviceRecord"] | {"id": result["id"]})
    unknown = sorted(records.keys() - {r["id"] for r in host["cases"]})
    if unknown:
        raise SystemExit(f"device records for cases the host report does not have: {', '.join(unknown)}")
    merged["cases"] = out
    merged["deviceObservations"] = used
    return merged


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("host", type=Path)
    parser.add_argument("devices", type=Path, nargs="+")
    parser.add_argument("-o", "--output", type=Path, required=True)
    parser.add_argument("--corpus", type=Path, default=Path("app/src/test/resources/unipack-conformance/corpus.json"))
    args = parser.parse_args()
    raw = args.corpus.read_bytes()
    corpus_hash = hashlib.sha256(raw).hexdigest()
    if args.corpus.with_name("corpus.sha256").read_text() != f"{corpus_hash}  corpus.json\n":
        raise SystemExit("corpus digest file does not match corpus bytes")
    corpus = json.loads(raw)
    host = json.loads(args.host.read_text())
    if host.get("corpusSha256") != corpus_hash:
        raise SystemExit("the host report ran another corpus")
    merged = merge(corpus, corpus_hash, host, load_records(args.devices))
    args.output.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + "\n")
    for result in merged["cases"]:
        if "deviceRefused" in result:
            print(f"{result['id']}: not merged, {result['deviceRefused']}")
    print(f"{len(merged['deviceObservations'])} device observations merged")
    problems = check_report(corpus, merged, corpus_hash)
    for problem in problems:
        print(problem)
    print(f"{len(problems)} report problems")
    return int(bool(problems))


if __name__ == "__main__":
    raise SystemExit(main())
