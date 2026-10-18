"""Prove vulnerable, unavailable and incomplete audits cannot pass as clean."""
import importlib.util
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

CHECKER = Path(__file__).resolve().parents[1] / 'check_release_dependencies.py'
spec = importlib.util.spec_from_file_location('check_release_dependencies', CHECKER)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

CURRENT = {'name': 'com.squareup.okhttp3:okhttp', 'version': '5.5.0'}
OLD = dict(CURRENT, version='4.9.0')


class ReleaseDependencyAuditTest(unittest.TestCase):
    def test_queries_exact_selected_versions_and_transitive_modules(self):
        dependencies = [CURRENT, {'name': 'com.squareup.okio:okio-jvm', 'version': '3.16.0'}]
        requests = []

        def query(queries):
            requests.extend(queries)
            return {'results': [{}, {}]}

        self.assertEqual(checker.audit(dependencies, query), [])
        self.assertEqual(requests, [
            {'package': {'ecosystem': 'Maven', 'name': item['name']}, 'version': item['version']}
            for item in dependencies])

    def test_vulnerable_version_reports_the_module_and_advisory(self):
        findings = checker.audit([OLD], lambda _: {'results': [{'vulns': [{'id': 'GHSA-3cqm-mf7h-prrj'}]}]})
        self.assertEqual(findings, [dict(OLD, id='GHSA-3cqm-mf7h-prrj')])

    def test_checks_vulnerabilities_on_later_pages(self):
        responses = iter([{'results': [{'next_page_token': 'next'}, {}]},
                          {'results': [{'vulns': [{'id': 'GHSA-test'}]}]}])
        requests = []

        def query(queries):
            requests.append(queries)
            return next(responses)

        self.assertEqual(checker.audit([OLD, CURRENT], query), [dict(OLD, id='GHSA-test')])
        self.assertEqual(len(requests[1]), 1)
        self.assertEqual(requests[1][0]['page_token'], 'next')

    def test_rejects_incomplete_and_malformed_responses(self):
        for response in ({}, [], {'results': []}, {'results': [None]},
                         {'results': [{'error': 'unavailable'}]},
                         {'results': [{'vulns': None}]}, {'results': [{'vulns': [{}]}]}):
            with self.subTest(response=response), self.assertRaises(ValueError):
                checker.audit([CURRENT], lambda _: response)

    def test_rejects_repeated_page_tokens(self):
        with self.assertRaisesRegex(ValueError, 'repeated'):
            checker.audit([CURRENT], lambda _: {'results': [{'next_page_token': 'same'}]})

    def test_splits_large_graphs_without_losing_modules(self):
        sizes = []

        def query(queries):
            sizes.append(len(queries))
            return {'results': [{} for _ in queries]}

        self.assertEqual(checker.audit([CURRENT] * 273, query), [])
        self.assertEqual(sizes, [100, 100, 73])

    def test_rejects_empty_or_invalid_dependency_lists(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'dependencies.json'
            for value in ([], {}, [dict(CURRENT, version='')], [{'name': 'module', 'version': '1'}]):
                with self.subTest(value=value), self.assertRaises(ValueError):
                    path.write_text(json.dumps(value))
                    checker.read_dependencies(path)
            path.write_text(json.dumps([CURRENT]))
            self.assertEqual(checker.read_dependencies(path), [CURRENT])

    def test_service_failure_still_fails_after_bounded_retries(self):
        with patch.object(checker.urllib.request, 'urlopen', side_effect=urllib.error.URLError('offline')) as call, \
                patch.object(checker.time, 'sleep'):
            with self.assertRaises(urllib.error.URLError):
                checker.query_osv([])
            self.assertEqual(call.call_count, 3)

    def test_cli_exit_codes_and_saved_evidence(self):
        with tempfile.TemporaryDirectory() as directory, \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            path = Path(directory)
            dependencies = path / 'dependencies.json'
            report = path / 'report.json'
            dependencies.write_text(json.dumps([OLD]))
            args = ['audit', '--dependencies', str(dependencies), '--report', str(report)]
            for findings, code, status in (([], 0, 'clean'), ([dict(OLD, id='GHSA-test')], 1, 'vulnerable')):
                with patch('sys.argv', args), patch.object(checker, 'audit', return_value=findings):
                    self.assertEqual(checker.main(), code)
                    self.assertEqual(json.loads(report.read_text())['status'], status)
            for error in (ValueError('incomplete'), urllib.error.URLError('offline')):
                with patch('sys.argv', args), patch.object(checker, 'audit', side_effect=error):
                    self.assertEqual(checker.main(), 2)
                    self.assertEqual(json.loads(report.read_text())['status'], 'error')


if __name__ == '__main__':
    unittest.main()
