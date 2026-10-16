"""Check resolved Maven release dependencies against OSV, without extra packages."""
import argparse
import json
from pathlib import Path
import sys
import time
import urllib.error
import urllib.request

OSV_URL = 'https://api.osv.dev/v1/querybatch'


def read_dependencies(path):
    dependencies = json.loads(path.read_text())
    if not isinstance(dependencies, list) or not dependencies:
        raise ValueError('Expected a nonempty resolved dependency list')
    for dependency in dependencies:
        if (not isinstance(dependency, dict)
                or not isinstance(dependency.get('name'), str)
                or len(dependency['name'].split(':')) != 2
                or not all(dependency['name'].split(':'))
                or not isinstance(dependency.get('version'), str)
                or not dependency['version'].strip()):
            raise ValueError(f'Invalid Maven dependency: {dependency!r}')
    return dependencies


def query_osv(queries):
    request = urllib.request.Request(
        OSV_URL, data=json.dumps({'queries': queries}).encode(),
        headers={'Content-Type': 'application/json', 'User-Agent': 'release-dependency-check'},
        method='POST')
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code not in (429, 500, 502, 503, 504) or attempt == 2:
                raise
        except (urllib.error.URLError, TimeoutError):
            if attempt == 2:
                raise
        time.sleep(2 ** attempt)


def audit(dependencies, query=query_osv):
    findings = []
    for offset in range(0, len(dependencies), 100):
        batch = dependencies[offset:offset + 100]
        pending = [(dependency, {
            'package': {'ecosystem': 'Maven', 'name': dependency['name']},
            'version': dependency['version'],
        }, set()) for dependency in batch]
        # OSV can paginate individual results within a batch.
        for _ in range(20):
            response = query([item[1] for item in pending])
            results = response.get('results') if isinstance(response, dict) else None
            if not isinstance(results, list) or len(results) != len(pending):
                raise ValueError('OSV returned an incomplete result list')
            next_pending = []
            for (dependency, request, seen_tokens), result in zip(pending, results):
                if not isinstance(result, dict) or 'error' in result:
                    raise ValueError(f'Invalid OSV result for {dependency["name"]}: {result!r}')
                vulns = result.get('vulns', [])
                if not isinstance(vulns, list):
                    raise ValueError('Invalid OSV vulnerability list')
                for vuln in vulns:
                    if not isinstance(vuln, dict) or not isinstance(vuln.get('id'), str) or not vuln['id']:
                        raise ValueError('Invalid OSV vulnerability identifier')
                    findings.append(dict(dependency, id=vuln['id']))
                token = result.get('next_page_token')
                if token:
                    if not isinstance(token, str) or token in seen_tokens:
                        raise ValueError('Invalid or repeated OSV page token')
                    next_pending.append((dependency, dict(request, page_token=token), seen_tokens | {token}))
            pending = next_pending
            if not pending:
                break
        else:
            raise ValueError('OSV pagination did not finish')
    return findings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dependencies', type=Path, default=Path('build/security/release-dependencies.json'))
    parser.add_argument('--report', type=Path, default=Path('build/security/osv-report.json'))
    args = parser.parse_args()
    try:
        dependencies = read_dependencies(args.dependencies)
        findings = audit(dependencies)
        report = {'status': 'vulnerable' if findings else 'clean',
                  'dependencies': dependencies, 'findings': findings}
        for finding in findings:
            print(f'{finding["name"]}:{finding["version"]}: https://osv.dev/vulnerability/{finding["id"]}')
        print(f'Checked {len(dependencies)} resolved Maven release dependencies; '
              f'{len(findings)} vulnerability matches')
        code = 1 if findings else 0
    except (OSError, ValueError, urllib.error.URLError) as error:
        report = {'status': 'error', 'error': str(error)}
        print(f'Dependency audit failed: {error}', file=sys.stderr)
        code = 2
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    return code


if __name__ == '__main__':
    sys.exit(main())
