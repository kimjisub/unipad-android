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
CASE_INSENSITIVE_HOST = ("this host cannot store names differing only in letter case; "
                         "run on a case-sensitive filesystem before confirming this case")


class MergeDeviceConformanceTest(unittest.TestCase):
    def setUp(self):
        self.corpus = {"cases": [
            {"id": "S", "fingerprint": "f-s", "expectation": "determined", "expected": EXPECTED, "soundIdentity": True},
            {"id": "C", "fingerprint": "f-c", "expectation": "determined", "expected": EXPECTED,
             "requiresCaseSensitiveFiles": True, "unobserved": {"android": CASE_INSENSITIVE_HOST}},
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
        report = self.merged()
        self.corpus["cases"][0]["unobserved"] = {"android": "needs a speaker"}
        self.assertIn("S: device observation cannot lift a restriction outside file names",
                      merger.check_report(self.corpus, report, "digest"))

    def test_a_record_for_a_case_with_another_restriction_stays_unverified_with_the_reason(self):
        self.corpus["cases"][0]["screenInputs"] = True
        self.corpus["cases"][1]["unobserved"] = {"android": "needs a speaker"}
        report = self.merged()
        self.assertEqual({"S": "unverified", "C": "unverified", "H": "pass"}, self.statuses(report))
        self.assertEqual([], report["deviceObservations"])
        self.assertIn("needs the play screen's input", report["cases"][0]["deviceRefused"])
        self.assertIn("unobserved on android: needs a speaker", report["cases"][1]["deviceRefused"])
        self.assertEqual([], merger.check_report(self.corpus, report, "digest"))

    def test_a_case_sensitive_case_unobserved_for_another_reason_keeps_that_restriction(self):
        report = self.merged()
        self.corpus["cases"][1]["unobserved"] = {"android": "needs a speaker"}
        self.assertIn("C: device observation cannot lift a restriction outside file names",
                      merger.check_report(self.corpus, report, "digest"))

    def test_a_device_record_never_replaces_a_host_observation(self):
        self.records["H"] = dict(self.records["C"], id="H", fingerprint="f-h")
        report = self.merged()
        self.assertNotIn("observedOn", report["cases"][2])
        self.assertIn("host observed", report["cases"][2]["deviceRefused"])

    def test_default_storage_failure_replaces_even_a_passing_host_observation(self):
        self.host["cases"][0].update(actual=EXPECTED, status="pass")
        self.records["S"].update(storageLocation="external", archiveExtracted=True,
            filesystem={"caseSensitive": False, "normalizationSensitive": False},
            storage=[{"contentsPreserved": False}], identityTraced=False, status="fail")
        report = self.merged()
        self.assertEqual("fail", self.statuses(report)["S"])
        self.assertIn("S: the app's archive extraction changed a sound file's contents",
                      merger.check_report(self.corpus, report, "digest"))

    def test_preserved_archive_names_can_pass_on_default_storage_that_merges_names(self):
        self.records["S"].update(storageLocation="external", archiveExtracted=True,
            filesystem={"caseSensitive": False, "normalizationSensitive": False})
        report = self.merged()
        self.assertEqual("pass", self.statuses(report)["S"])
        self.assertEqual([], merger.check_report(self.corpus, report, "digest"))

    def test_default_storage_is_chosen_over_internal_storage_regardless_of_input_order(self):
        import tempfile
        import json
        with tempfile.TemporaryDirectory() as root:
            internal, external = Path(root) / "internal", Path(root) / "external"
            internal.mkdir(); external.mkdir()
            record = dict(self.records["S"], storageLocation="internal")
            (internal / "S.json").write_text(json.dumps(record))
            record.update(storageLocation="external", status="fail")
            (external / "S.json").write_text(json.dumps(record))
            for folders in ([internal, external], [external, internal]):
                chosen = merger.load_records(folders)["S"][0]
                self.assertEqual("external", chosen["storageLocation"])
                self.assertEqual("fail", chosen["status"])

    def test_default_archive_case_variants_replace_a_case_sensitive_host_result(self):
        self.host["caseSensitiveFileSystem"] = True
        self.host["cases"][1].update(actual=EXPECTED, status="pass")
        self.records["C"].update(source="device-archive-files", storageLocation="external", archiveExtracted=True,
            identityTraced=True, storage=[{"contentsPreserved": True}],
            filesystem={"caseSensitive": False, "normalizationSensitive": False})
        report = self.merged()
        self.assertEqual("device", report["cases"][1]["observedOn"])
        self.assertEqual([], merger.check_report(self.corpus, report, "digest"))

    def test_default_storage_wins_when_legacy_and_new_internal_records_are_both_present(self):
        import itertools
        import tempfile
        import json
        with tempfile.TemporaryDirectory() as root:
            folders = [Path(root) / name for name in ("legacy", "internal", "external")]
            for folder, location in zip(folders, (None, "internal", "external")):
                folder.mkdir()
                record = dict(self.records["S"])
                if location:
                    record["storageLocation"] = location
                (folder / "S.json").write_text(json.dumps(record))
            for order in itertools.permutations(folders):
                self.assertEqual("external", merger.load_records(order)["S"][0]["storageLocation"])

    def test_malformed_device_evidence_is_reported_instead_of_crashing(self):
        for evidence in (None, [], "invalid"):
            report = self.merged()
            report["cases"][0]["deviceEvidence"] = evidence
            self.assertIn("S: device observation carries no storage evidence",
                          merger.check_report(self.corpus, report, "digest"))

    def test_required_file_cases_cannot_be_certified_without_default_storage(self):
        self.corpus["cases"][0]["id"] = "KS-G620"
        self.host["cases"][0]["id"] = "KS-G620"
        self.records["KS-G620"] = self.records.pop("S")
        self.records["KS-G620"]["id"] = "KS-G620"
        with self.assertRaisesRegex(SystemExit, "default external storage observations missing: KS-G620"):
            self.merged()

    def test_invalid_default_record_cannot_fall_back_to_a_host_pass(self):
        self.corpus["cases"][0]["id"] = "KS-G620"
        self.host["cases"][0].update(id="KS-G620", actual=EXPECTED, status="pass")
        record = self.records.pop("S")
        record.update(id="KS-G620", storageLocation="external", actual={"loaded": False})
        self.records["KS-G620"] = record
        with self.assertRaisesRegex(SystemExit, "default external storage observation refused"):
            self.merged()

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
