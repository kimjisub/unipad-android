#!/usr/bin/env python3
"""Export tracked source without service/signing files; add explicitly fake build inputs.

Never run on an existing checkout: the destination must not exist.
This prepares debug builds only; baseline exports are NOT safe to launch.
"""
import argparse
import io
import json
from pathlib import Path
import subprocess
import shutil
import tarfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('revision')
parser.add_argument('destination', type=Path)
args = parser.parse_args()
adb = shutil.which('adb')
if not adb:
    parser.error('Load the machine environment first: adb is missing')
sdk = Path(adb).resolve().parent.parent
if not (sdk / 'platforms').is_dir():
    parser.error('The environment adb does not identify an Android SDK')
revision = subprocess.check_output(['git', 'rev-parse', '--verify', args.revision + '^{commit}'], text=True).strip()
paths = subprocess.check_output(['git', 'ls-tree', '-rz', '--name-only', revision]).decode().split('\0')
safe_paths = [p for p in paths if p and Path(p).name not in ('google-services.json', 'keystore.properties', 'local.properties')
              and Path(p).suffix.lower() not in ('.jks', '.keystore', '.p12', '.pfx')]
# Excluded contents never enter the archive. No local/untracked settings are read.
archive = subprocess.check_output(['git', 'archive', '--format=tar', revision, '--', *safe_paths])
args.destination.mkdir(parents=True, exist_ok=False)
with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
    tar.extractall(args.destination, filter='data')
(args.destination / 'keystore.properties').write_text('storeFile=dummy.jks\nstorePassword=dummy\nkeyAlias=dummy\nkeyPassword=dummy\n')
(args.destination / 'local.properties').write_text(f'sdk.dir={sdk}\n')
# Shape-compatible, invented identifiers, not a real Firebase project or credential.
config = {
    'project_info': {'project_number': '1234567890', 'project_id': 'unipad-local-invalid',
                     'firebase_url': 'https://unipad-local-invalid.invalid',
                     'storage_bucket': 'unipad-local-invalid.invalid'},
    'client': [{'client_info': {'mobilesdk_app_id': '1:1234567890:android:0000000000000000000000',
                              'android_client_info': {'package_name': 'com.kimjisub.launchpad.dev'}},
                'api_key': [{'current_key': 'AIza' + '0' * 35}]}],
    'configuration_version': '1',
}
(args.destination / 'app/google-services.json').write_text(json.dumps(config, indent=2) + '\n')
(args.destination / 'LOCAL_TEST_INPUTS.txt').write_text(
    f'Source: {revision}\nExcluded: service/signing/local settings\n'
    'Invented: keystore.properties, app/google-services.json; local SDK: local.properties\n'
    'No dummy keystore is created. assembleDebug uses Android debug signing.\n'
    'This file and the fake inputs are NOT app verification evidence.\n')
print(f'Prepared debug-only source {revision}: {args.destination}')
