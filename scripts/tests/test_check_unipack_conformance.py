import copy
import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("check_unipack_conformance", Path(__file__).resolve().parents[1] / "check_unipack_conformance.py")
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class ConformanceReportTest(unittest.TestCase):
    def setUp(self):
        self.corpus = {"cases": [{"id": "A", "fingerprint": "fingerprint", "expectation": "determined", "expected": {"loaded": True}}]}
        self.report = {"platform": "android", "corpusSha256": "digest", "assertions": "checked", "caseSensitiveFileSystem": False,
                       "cases": [{"id": "A", "fingerprint": "fingerprint", "status": "pass", "actual": {"loaded": True}}]}

    def check(self):
        return checker.check_report(self.corpus, self.report, "digest")

    def test_complete_observation(self):
        self.assertEqual([], self.check())

    def test_omitted_case(self):
        self.report["cases"] = []
        self.assertIn("A: missing result", self.check())

    def test_duplicate_case(self):
        self.report["cases"].append(copy.deepcopy(self.report["cases"][0]))
        self.assertIn("A: duplicate result", self.check())

    def test_unregistered_case(self):
        self.report["cases"][0]["id"] = "unknown"
        self.assertIn("unknown: unregistered result", self.check())

    def test_wrong_observation_claimed_as_pass(self):
        self.report["cases"][0]["actual"] = {"loaded": False}
        self.assertIn("A: wrong observation cannot be pass", self.check())

    def test_numbers_cannot_impersonate_json_booleans(self):
        self.report["cases"][0]["actual"] = {"loaded": 1}
        self.assertIn("A: wrong observation cannot be pass", self.check())

    def test_a_changed_stored_filename_is_unverified_instead_of_an_app_result(self):
        self.corpus["cases"][0]["files"] = [{"path": "sounds/cafe\u0301.wav"}]
        self.report["cases"][0].update(status="unverified", actual=None, storedInputPaths=["sounds/caf\u00e9.wav"])
        self.assertEqual([], self.check())
        self.report["cases"][0].update(status="pass", actual={"loaded": True})
        self.assertTrue(self.check())

    def test_preserved_filenames_cannot_excuse_a_missing_observation(self):
        self.corpus["cases"][0]["files"] = [{"path": "sounds/a.wav"}]
        self.report["cases"][0].update(status="unverified", actual=None, storedInputPaths=["sounds/a.wav"])
        self.assertIn("A: observable case has no result", self.check())

    def test_no_observation_claimed_as_pass(self):
        self.report["cases"][0]["actual"] = None
        self.assertIn("A: missing observation cannot be pass", self.check())

    def test_undetermined_claimed_as_pass(self):
        self.corpus["cases"][0]["expectation"] = "undetermined"
        self.assertIn("A: undetermined case cannot be pass", self.check())

    def test_screen_case_requires_screen_runner(self):
        self.corpus["cases"][0]["screenInputs"] = True
        self.assertIn("A: generic runner claimed an observation outside its seam", self.check())
        self.report["cases"][0].update(status="unverified", actual=None)
        self.assertEqual([], self.check())

    def test_case_insensitive_disk_cannot_confirm_colliding_files(self):
        self.corpus["cases"][0]["requiresCaseSensitiveFiles"] = True
        self.assertIn("A: generic runner claimed an observation outside its seam", self.check())

    def test_expected_failure_prediction_does_not_accept_an_unpinned_result(self):
        self.corpus["cases"][0]["expectedFailing"] = ["android"]
        self.report["cases"][0].update(status="fail", actual={"loaded": False})
        self.assertIn("A: unexpected app result", self.check())

    def test_pinned_failure_requires_the_exact_observation(self):
        self.corpus["cases"][0]["known"] = {"android": {"status": "fail", "actual": {"loaded": False}}}
        self.report["cases"][0].update(status="fail", actual={"loaded": False})
        self.assertEqual([], self.check())
        self.report["cases"][0]["actual"] = {"loaded": "different"}
        self.assertIn("A: unexpected app result", self.check())

    def test_fingerprint_and_provenance(self):
        self.report.update(platform="ios", corpusSha256="old", assertions="skipped")
        self.report["cases"][0]["fingerprint"] = "changed"
        self.assertEqual(4, len(self.check()))

    def test_every_registered_g6_case_rejects_missing_wrong_and_unknown_results(self):
        corpus_path = Path(__file__).resolve().parents[2] / "app/src/test/resources/unipack-conformance/corpus.json"
        added = [case for case in json.loads(corpus_path.read_bytes())["cases"] if "-G6" in case["id"]]
        self.assertEqual(49, len(added))
        for case in added:
            with self.subTest(case=case["id"]):
                # Fabricated validator fixtures, never runtime observations of the app.
                restricted = case.get("screenInputs") or "android" in case.get("unobserved", {}) or case.get("requiresCaseSensitiveFiles")
                result = {"id": case["id"], "fingerprint": case["fingerprint"],
                          "status": "unverified" if restricted else "pass",
                          "actual": None if restricted else case["expected"]}
                fake = {"platform": "android", "corpusSha256": "digest", "assertions": "checked",
                        "caseSensitiveFileSystem": False, "cases": [result]}
                sample = {"cases": [case]}
                self.assertEqual([], checker.check_report(sample, fake, "digest"))
                missing = copy.deepcopy(fake)
                missing["cases"] = []
                self.assertIn(f"{case['id']}: missing result", checker.check_report(sample, missing, "digest"))
                wrong = copy.deepcopy(fake)
                wrong["cases"][0].update(status="pass", actual={"injectedObservation": True})
                self.assertTrue(checker.check_report(sample, wrong, "digest"))
                unknown = copy.deepcopy(fake)
                unknown["cases"][0]["id"] = "unregistered"
                self.assertIn("unregistered: unregistered result", checker.check_report(sample, unknown, "digest"))


if __name__ == "__main__":
    unittest.main()
