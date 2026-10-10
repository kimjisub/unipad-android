#!/usr/bin/env python3
"""Check that a debug manifest switches Firebase collection off and a release manifest does not.

Both arguments are merged manifests (Gradle's merged output, or `apkanalyzer manifest print` of an APK).
"""
import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
INTERNET = 'android.permission.INTERNET'
DEBUG_COLLECTION_OFF = {
    'firebase_analytics_collection_deactivated': 'true',
    'firebase_performance_collection_deactivated': 'true',
    'firebase_crashlytics_collection_enabled': 'false',
}


def metadata(root: ET.Element) -> dict[str, str | None]:
    return {e.get(ANDROID + 'name'): e.get(ANDROID + 'value') for e in root.findall('application/meta-data')}


def has_internet(root: ET.Element) -> bool:
    return any(e.tag in ('uses-permission', 'uses-permission-sdk-23') and e.get(ANDROID + 'name') == INTERNET
               for e in root)


def problems(debug: Path, release: Path) -> list[str]:
    found = []
    debug_values = metadata(ET.parse(debug).getroot())
    for name, value in DEBUG_COLLECTION_OFF.items():
        if debug_values.get(name) != value:
            found.append(f'debug: {name} is {debug_values.get(name)!r}, expected {value!r}')
    public = ET.parse(release).getroot()
    if not has_internet(public):
        found.append(f'release: {INTERNET} is missing')
    found += [f'release: debug-only {name} is present' for name in DEBUG_COLLECTION_OFF if name in metadata(public)]
    return found


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('debug', type=Path)
    parser.add_argument('release', type=Path)
    args = parser.parse_args()
    errors = problems(args.debug, args.release)
    for error in errors:
        print(f'FAIL: {error}')
    if errors:
        sys.exit(1)
    print('PASS: debug collection off; release keeps network access and its own collection settings')
