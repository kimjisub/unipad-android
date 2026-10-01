#!/usr/bin/env python3
"""Check the local-test boundary in source or decoded/merged manifests."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
TOOLS = '{http://schemas.android.com/tools}'
EXPECTED = {
    'firebase_analytics_collection_deactivated': 'true',
    'firebase_performance_collection_deactivated': 'true',
    'firebase_crashlytics_collection_enabled': 'false',
}


def internet_permissions(root: ET.Element) -> list[ET.Element]:
    return [e for e in root
            if e.tag in ('uses-permission', 'uses-permission-sdk-23')
            and e.get(ANDROID + 'name') == 'android.permission.INTERNET']


def check(debug: Path, release: Path, source: bool = False) -> None:
    root = ET.parse(debug).getroot()
    app = root.find('application')
    assert app is not None, 'missing application'
    metadata = {e.get(ANDROID + 'name'): e.get(ANDROID + 'value')
                for e in app.findall('meta-data')}
    for name, value in EXPECTED.items():
        assert metadata.get(name) == value, f'{name}: expected {value}'
    internet = internet_permissions(root)
    if source:
        assert len(internet) == 1 and internet[0].get(TOOLS + 'node') == 'remove', 'missing INTERNET removal'
    else:
        assert not internet, 'local-test APK can open network sockets'
        assert root.get('package') == 'com.kimjisub.launchpad.dev', 'unexpected test package'
        assert not root.get(ANDROID + 'sharedUserId'), 'test package shares a UID'
    public = ET.parse(release).getroot()
    assert internet_permissions(public), 'public network permission changed'
    assert not any(e.get(ANDROID + 'name') in EXPECTED
                   for e in public.findall('application/meta-data')), 'test controls leaked into public manifest'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('debug', type=Path)
    parser.add_argument('release', type=Path)
    parser.add_argument('--source', action='store_true')
    args = parser.parse_args()
    check(args.debug, args.release, args.source)
    print('PASS: test collection disabled, test network blocked, public manifest preserved')
