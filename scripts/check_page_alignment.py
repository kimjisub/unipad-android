#!/usr/bin/env python3
"""Check every packaged native library and APK ZIP alignment before release.

AAB ZIP offsets are not installable APK offsets; Gradle separately checks the
bundle's PAGE_ALIGNMENT_16K request using bundletool's BundleConfig parser.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

PAGE_SIZE = 16384
ABIS_64 = {'arm64-v8a', 'x86_64'}
ABIS_32 = {'armeabi-v7a', 'x86'}


def check_archive(archive: Path, readelf: Path, zipalign: Path | None) -> list[str]:
    errors = []
    try:
        with zipfile.ZipFile(archive) as package, tempfile.TemporaryDirectory(prefix='page-alignment-') as folder:
            libraries = [entry for entry in package.infolist() if entry.filename.endswith('.so')]
            if not libraries:
                errors.append(f'{archive}: no native libraries found')
            for entry in libraries:
                label = f'{archive.name}!{entry.filename}'
                abi = Path(entry.filename).parent.name
                if abi not in ABIS_64 | ABIS_32:
                    errors.append(f'{label}: unrecognized native ABI {abi!r}')
                    continue
                if archive.suffix == '.apk' and entry.compress_type != zipfile.ZIP_STORED:
                    errors.append(f'{label}: native library must be uncompressed in the APK')
                # Do not extract archive paths onto the filesystem.
                library = Path(folder) / 'library.so'
                library.write_bytes(package.read(entry))
                try:
                    result = subprocess.run([str(readelf), '-lW', str(library)], capture_output=True, text=True, check=False)
                    if result.returncode:
                        raise ValueError(f'llvm-readelf exited {result.returncode}: {result.stderr.strip()}')
                    loads = [int(line.split()[-1], 16) for line in result.stdout.splitlines()
                             if line.split() and line.split()[0] == 'LOAD']
                    if not loads:
                        raise ValueError('no ELF LOAD segments found')
                    if abi in ABIS_64:
                        invalid = [value for value in loads if value < PAGE_SIZE or value & (value - 1)]
                        if invalid:
                            raise ValueError('ELF LOAD alignment must be at least 0x4000; found ' +
                                             ', '.join(hex(value) for value in invalid))
                        print(f'PASS: {label}: all {len(loads)} LOAD segments are 16 KB aligned')
                    else:
                        # Android's 16 KB page-size requirement applies to 64-bit ABIs.
                        print(f'INFO: {label}: 32-bit ELF inspected; LOAD alignment ' +
                              ', '.join(hex(value) for value in loads))
                except (OSError, ValueError) as error:
                    errors.append(f'{label}: {error}')
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        errors.append(f'{archive}: {error}')

    if archive.suffix == '.apk':
        if zipalign is None:
            errors.append(f'{archive}: zipalign is required for APK verification')
        else:
            try:
                result = subprocess.run([str(zipalign), '-c', '-P', '16', '-v', '4', str(archive)],
                                        capture_output=True, text=True, check=False)
                print(result.stdout, end='')
                if result.returncode:
                    errors.append(f'{archive}: zipalign exited {result.returncode}: {result.stderr.strip()}')
            except OSError as error:
                errors.append(f'{archive}: zipalign: {error}')
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archives', type=Path, nargs='+')
    parser.add_argument('--readelf', type=Path, required=True)
    parser.add_argument('--zipalign', type=Path)
    args = parser.parse_args()
    errors = []
    for archive in args.archives:
        if archive.suffix not in {'.apk', '.aab'}:
            errors.append(f'{archive}: expected an APK or AAB')
        else:
            errors.extend(check_archive(archive, args.readelf, args.zipalign))
    for error in errors:
        print(f'FAIL: {error}', file=sys.stderr)
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
