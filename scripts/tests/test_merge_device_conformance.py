import copy
import importlib.util
import sys
from pathlib import Path
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("merge_device_conformance", SCRIPTS / "merge_device_conformance.py")
merger = importlib.util.module_from_spec(spec)
spec.loader.exec_module(merger)

EXPECTED = {"loaded": True}


class MergeDeviceConformanceTest(unittest.TestCase):
    def setUp(self):
        self.corpus = {"cases": [
            {"id": "S", "fingerprint": "f-s", "expectation": "determined", "expected": EXPECTED, "soundIdentity": True},
            {"id": "C", "fingerprint": "f-c", "expectation": "determined", "expected": EXPECTED,
             "requiresCaseSensitiveFiles": True, "unobserved": {"android": "case-insensitive host"}},
            {"id": "H", "fingerprint": "f-h", "expectation": "determined", "expected": EXPECTED},
        ]}
        self.host = {"platform": "android", "corpusSha256": "digest", "assertions": "checked", "caseSensitiveFileSystem": False,
                     "cases": [{"id": "S", "fingerprint": "f-s", "status": "unverified", "actual": None},
                               {"id": "C", "fingerprint": "f-c", "status": "unverified", "actual": None},
                               {"id": "H", "fingerprint": "f-h", "status": "pass", "actual": EXPECTED}]}
        filesystem = {"caseSensitive": True, "normalizationSensitive": True}
        self.records = {
            "S": {"platform": "android", "id": "S", "fingerprint": "f-s", "corpusSha256": "digest", "status": "pass",
                  "actual": EXPECTED, "source": "device-archive-decoder-runner", "identityTraced": True,
                  "storage": [{"contentsPreserved": True}], "filesystem": filesystem},
            "C": {"platform": "android", "id": "C", "fingerprint": "f-c", "corpusSha256": "digest", "status": "pass",
                  "actual": EXPECTED, "source": "device-internal-files", "filesystem": filesystem},
        }

    def merged(self):
        records = {k: (v, Path(f"{k}.json"), "sha") for k, v in self.records.items()}
        return merger.merge(self.corpus, "digest", copy.deepcopy(self.host), records)

    def statuses(self, report):
        return {case["id"]: case["status"] for case in report["cases"]}

    def test_matching_records_replace_the_host_gaps_and_pass_the_check(self):
        report = self.merged()
        self.assertEqual({"S": "pass", "C": "pass", "H": "pass"}, self.statuses(report))
        self.assertEqual([], merger.check_report(self.corpus, report, "digest"))
        self.assertEqual(2, len(report["deviceObservations"]))

    def test_a_failing_device_result_stays_a_failure(self):
        self.records["C"].update(actual={"loaded": False}, status="fail")
        report = self.merged()
        self.assertEqual("fail", self.statuses(report)["C"])
        self.assertIn("C: unexpected app result", merger.check_report(self.corpus, report, "digest"))

    def test_a_status_that_disagrees_with_the_actual_value_is_refused(self):
        self.records["C"].update(actual={"loaded": False})
        report = self.merged()
        self.assertEqual("unverified", self.statuses(report)["C"])
        self.assertIn("pass but its actual value is a fail", report["cases"][1]["deviceRefused"])

    def test_records_of_another_corpus_input_or_recorder_are_refused(self):
        for change in ({"corpusSha256": "other"}, {"fingerprint": "other"}, {"source": "device-internal-files"}):
            with self.subTest(change=change):
                record = self.records["S"]
                saved = dict(record)
                record.update(change)
                self.assertEqual("unverified", self.statuses(self.merged())["S"])
                record.clear(); record.update(saved)

    def test_untraced_identity_missing_storage_or_merged_names_are_refused(self):
        for change in ({"identityTraced": False}, {"storage": []},
                       {"filesystem": {"caseSensitive": True, "normalizationSensitive": False}}):
            with self.subTest(change=change):
                saved = copy.deepcopy(self.records["S"])
                self.records["S"].update(change)
                self.assertEqual("unverified", self.statuses(self.merged())["S"])
                self.records["S"] = saved

    def test_changed_sound_contents_are_a_failure_even_with_the_expected_name(self):
        self.records["S"].update(storage=[{"contentsPreserved": True}, {"contentsPreserved": False}])
        self.assertEqual("unverified", self.statuses(self.merged())["S"], "a record claiming pass is refused")
        self.records["S"]["status"] = "fail"
        report = self.merged()
        self.assertEqual("fail", self.statuses(report)["S"])
        self.assertIn("S: the app's archive extraction changed a sound file's contents",
                      merger.check_report(self.corpus, report, "digest"))

    def test_the_checker_rechecks_the_device_evidence(self):
        for change, problem in (
                ({"normalizationSensitive": False}, "S: device storage merges names the case keeps apart"),
                ({"identityTraced": False}, "S: device could not trace every selected sound to its archive entry"),
                ({"storageEntries": 0}, "S: device recorded no archive entry")):
            with self.subTest(change=change):
                report = self.merged()
                report["cases"][0]["deviceEvidence"].update(change)
                self.assertIn(problem, merger.check_report(self.corpus, report, "digest"))
        report = self.merged()
        report["cases"][0]["status"] = "pass"
        report["cases"][0]["deviceEvidence"]["contentsPreserved"] = False
        problems = merger.check_report(self.corpus, report, "digest")
        self.assertIn("S: changed sound contents cannot be pass", problems)

    def test_a_device_observation_lifts_only_file_name_restrictions(self):
        self.corpus["cases"][0]["unobserved"] = {"android": "needs a speaker"}
        report = self.merged()
        self.assertIn("S: device observation cannot lift a restriction outside file names",
                      merger.check_report(self.corpus, report, "digest"))

    def test_a_device_record_never_replaces_a_host_observation(self):
        self.records["H"] = dict(self.records["C"], id="H", fingerprint="f-h")
        report = self.merged()
        self.assertNotIn("observedOn", report["cases"][2])
        self.assertIn("host observed", report["cases"][2]["deviceRefused"])

    def test_the_checker_refuses_a_device_observation_from_the_wrong_recorder(self):
        report = self.merged()
        report["cases"][0]["source"] = "device-internal-files"
        self.assertIn("S: device observation from device-internal-files cannot observe a sound-identity case",
                      merger.check_report(self.corpus, report, "digest"))
        report = self.merged()
        report["cases"][2].update(observedOn="device", source="device-internal-files")
        self.assertIn("H: device observation replaces a host observation", merger.check_report(self.corpus, report, "digest"))


if __name__ == "__main__":
    unittest.main()
