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

    def test_screen_case_requires_the_play_screen_input_path(self):
        self.corpus["cases"][0]["screenInputs"] = True
        self.assertIn("A: generic runner claimed an observation outside its seam", self.check())
        self.report["cases"][0]["inputAdapter"] = "play-screen"
        self.assertEqual([], self.check())
        self.report["cases"][0]["inputAdapter"] = "pad-calls"
        self.assertIn("A: input adapter pad-calls cannot observe this case", self.check())
        self.report["cases"][0].update(status="unverified", actual=None)
        self.report["cases"][0].pop("inputAdapter")
        self.assertEqual([], self.check())

    def test_the_play_screen_input_path_cannot_claim_other_cases(self):
        self.report["cases"][0]["inputAdapter"] = "play-screen"
        self.assertIn("A: input adapter play-screen cannot observe this case", self.check())

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

    def test_operations_and_sound_identity_cannot_be_reported_as_pack_parsing(self):
        for field, value in [("observation", "audio-cost"), ("observation", "future-operation"), ("soundIdentity", True)]:
            with self.subTest(field=field, value=value):
                self.corpus["cases"][0][field] = value
                self.assertIn("A: generic runner claimed an observation outside its seam", self.check())
                self.report["cases"][0].update(status="unverified", actual=None)
                self.assertEqual([], self.check())
                self.report["cases"][0].update(status="pass", actual={"loaded": True})
                self.corpus["cases"][0].pop(field)

    def test_faithful_host_identity_observation_is_checked(self):
        case = self.corpus["cases"][0]
        case.update(soundIdentity=True, files=[{"path": "sounds/a.wav"}])
        result = self.report["cases"][0]
        result.update(identityAdapter="host-archive-runner", storedInputPaths=["sounds/a.wav"],
                      identityEvidence={"identityTraced": True, "contentsPreserved": True, "storageEntries": 1})
        self.assertEqual([], self.check())
        for field, value in [("identityTraced", False), ("contentsPreserved", False), ("storageEntries", 0)]:
            with self.subTest(field=field):
                saved = copy.deepcopy(result["identityEvidence"])
                result["identityEvidence"][field] = value
                self.assertTrue(self.check())
                result["identityEvidence"] = saved
        result["storedInputPaths"] = ["sounds/A.wav"]
        self.assertTrue(self.check())

    def test_case_sensitive_host_lifts_only_the_file_name_restriction(self):
        case = self.corpus["cases"][0]
        case.update(requiresCaseSensitiveFiles=True, unobserved={"android": checker.CASE_INSENSITIVE_HOST})
        self.report["caseSensitiveFileSystem"] = True
        self.assertEqual([], self.check())
        case["unobserved"]["android"] = "needs a speaker"
        self.assertTrue(self.check())

    def test_required_file_cases_cannot_be_missing_unverified_or_wrong(self):
        self.assertTrue(checker.check_report(self.corpus, self.report, "digest", require_file_cases=True))
        self.corpus["cases"][0]["id"] = "KS-G601"
        self.report["cases"][0].update(id="KS-G601", status="unverified", actual=None)
        problems = checker.check_report(self.corpus, self.report, "digest", require_file_cases=True)
        self.assertIn("KS-G601: required file case was not observed and passed on the host", problems)

    def test_required_file_gate_accepts_complete_host_evidence_and_rejects_each_gap(self):
        # Validator fixtures only: actual observations are produced by the JVM suite.
        path = Path(__file__).resolve().parents[2] / "app/src/test/resources/unipack-conformance/corpus.json"
        cases = [case for case in json.loads(path.read_bytes())["cases"]
                 if case["id"] in checker.REQUIRED_FILE_CASE_IDS]
        self.assertEqual(13, len(cases))
        results = []
        for case in cases:
            result = {"id": case["id"], "fingerprint": case["fingerprint"], "status": "pass", "actual": case["expected"]}
            if case.get("soundIdentity"):
                result.update(identityAdapter="host-archive-runner", storedInputPaths=[f["path"] for f in case["files"]],
                              identityEvidence={"identityTraced": True, "contentsPreserved": True,
                                                "storageEntries": len(case["files"])})
            results.append(result)
        corpus = {"cases": cases}
        report = dict(self.report, caseSensitiveFileSystem=True, cases=results)
        self.assertEqual([], checker.check_report(corpus, report, "digest", require_file_cases=True))
        for index, case in enumerate(cases):
            for mutation in ({"actual": None, "status": "unverified"}, {"actual": {"wrong": True}},
                             {"observedOn": "device"}):
                with self.subTest(case=case["id"], mutation=mutation):
                    changed = copy.deepcopy(report)
                    changed["cases"][index].update(mutation)
                    self.assertTrue(checker.check_report(corpus, changed, "digest", require_file_cases=True))

    def test_every_registered_g6_case_rejects_missing_wrong_and_unknown_results(self):
        corpus_path = Path(__file__).resolve().parents[2] / "app/src/test/resources/unipack-conformance/corpus.json"
        added = json.loads(corpus_path.read_bytes())["cases"]
        self.assertEqual(391, len(added))
        for case in added:
            with self.subTest(case=case["id"]):
                # Fabricated validator fixtures, never runtime observations of the app.
                restricted = "android" in case.get("unobserved", {}) or case.get("requiresCaseSensitiveFiles") or case.get("soundIdentity") or case.get("observation") not in (None, "runner")
                result = {"id": case["id"], "fingerprint": case["fingerprint"],
                          "status": "unverified" if restricted else "pass",
                          "actual": None if restricted else case["expected"]}
                if case.get("screenInputs") and not restricted:
                    result["inputAdapter"] = "play-screen"
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

    def test_the_corpus_leaves_case_sensitive_cases_unobserved_only_for_their_file_names(self):
        corpus_path = Path(__file__).resolve().parents[2] / "app/src/test/resources/unipack-conformance/corpus.json"
        cases = [case for case in json.loads(corpus_path.read_bytes())["cases"]
                 if case.get("requiresCaseSensitiveFiles") and "android" in case.get("unobserved", {})]
        self.assertEqual(["KS-G617", "KS-G618", "KS-G619"], [case["id"] for case in cases])
        for case in cases:
            self.assertTrue(checker.unobserved_for_file_names(case), case["id"])


if __name__ == "__main__":
    unittest.main()
