"""Keep native release checks fail-closed, including late LOAD segments and ZIP errors."""
from contextlib import redirect_stdout
import importlib.util
import io
import os
import sys
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

CHECKER = Path(__file__).resolve().parents[1] / 'check_page_alignment.py'


class PageAlignmentCliTest(unittest.TestCase):
    def test_cli_starts_with_running_python(self):
        result = subprocess.run([sys.executable, str(CHECKER), '--help'],
                                capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('--readelf', result.stdout)


class PageAlignmentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('check_page_alignment', CHECKER)
        cls.checker = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.checker)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def archive(self, suffix='.apk', libs=None, compressed=False):
        path = self.root / ('release' + suffix)
        with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED if compressed else zipfile.ZIP_STORED) as archive:
            for name in libs if libs is not None else ['lib/arm64-v8a/libaudio.so', 'lib/x86_64/libaudio.so']:
                archive.writestr(name, b'fixture')
        return path

    def run_check(self, archive, readelf='LOAD 0 0 0 0 0 R E 0x4000\n', elf_status=0, zip_status=0):
        self.commands = []
        def run(command, **kwargs):
            self.commands.append(command)
            if str(command[0]) == 'readelf':
                return subprocess.CompletedProcess(command, elf_status, readelf, '')
            return subprocess.CompletedProcess(command, zip_status, 'zip verdict', '')
        with patch.object(self.checker.subprocess, 'run', side_effect=run), redirect_stdout(io.StringIO()):
            return self.checker.check_archive(archive, Path('readelf'), Path('zipalign'))

    def test_accepts_both_64_bit_abis_and_checks_zip(self):
        self.assertEqual(self.run_check(self.archive()), [])
        self.assertEqual(self.commands[-1], ['zipalign', '-c', '-P', '16', '-v', '4', str(self.root / 'release.apk')])

    def test_rejects_four_kilobyte_load(self):
        errors = self.run_check(self.archive(), 'LOAD 0 0 0 0 0 R E 0x1000\n')
        self.assertEqual(len(errors), 2)
        self.assertTrue(all('0x1000' in error and 'libaudio.so' in error for error in errors))

    def test_checks_every_load_segment(self):
        errors = self.run_check(self.archive(), 'LOAD 0 0 0 0 0 R E 0x4000\nLOAD 0 0 0 0 0 RW 0x1000\n')
        self.assertEqual(len(errors), 2)

    def test_accepts_larger_page_alignment(self):
        self.assertEqual(self.run_check(self.archive(), 'LOAD 0 0 0 0 0 RW 0x10000\n'), [])

    def test_rejects_non_power_of_two_alignment(self):
        self.assertEqual(len(self.run_check(self.archive(), 'LOAD 0 0 0 0 0 RW 0x6000\n')), 2)

    def test_rejects_missing_or_malformed_loads(self):
        for output in ('', 'not an ELF', 'LOAD 0 R E unknown\n'):
            with self.subTest(output=output):
                self.assertEqual(len(self.run_check(self.archive(), output)), 2)

    def test_rejects_readelf_failure_even_with_aligned_output(self):
        self.assertEqual(len(self.run_check(self.archive(), elf_status=1)), 2)

    def test_rejects_zipalign_failure(self):
        self.assertIn('zipalign', self.run_check(self.archive(), zip_status=1)[0])

    def test_requires_zipalign_for_apk(self):
        with patch.object(self.checker.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, 'LOAD 0 0 0 0 0 R 0x4000\n', '')):
            self.assertTrue(self.checker.check_archive(self.archive(), Path('readelf'), None))

    def test_rejects_compressed_apk_libraries(self):
        self.assertEqual(len(self.run_check(self.archive(compressed=True))), 2)

    def test_checks_all_bundle_modules_without_zipaligning_aab(self):
        path = self.archive('.aab', ['base/lib/arm64-v8a/libaudio.so', 'feature/lib/x86_64/libextra.so'], compressed=True)
        self.assertEqual(len(self.run_check(path, 'LOAD 0 0 0 0 0 R 0x1000\n')), 2)
        self.assertEqual(len(self.commands), 2)

    def test_reports_32_bit_elf_without_applying_64_bit_requirement(self):
        path = self.archive(libs=['lib/armeabi-v7a/libc++_shared.so', 'lib/x86/libc++_shared.so'])
        self.assertEqual(self.run_check(path, 'LOAD 0 0 0 0 0 R 0x1000\n'), [])
        self.assertEqual(len(self.commands), 3)

    def test_rejects_unrecognized_abi(self):
        self.assertTrue(self.run_check(self.archive(libs=['lib/new-abi/libaudio.so'])))

    def test_rejects_missing_native_libraries(self):
        self.assertTrue(self.run_check(self.archive(libs=[])))

    def test_rejects_missing_or_invalid_archive(self):
        path = self.root / 'missing.apk'
        self.assertTrue(self.run_check(path))
        path.write_text('not a zip')
        self.assertTrue(self.run_check(path))

    def test_missing_tools_fail_closed(self):
        with patch.object(self.checker.subprocess, 'run', side_effect=FileNotFoundError('missing tool')):
            self.assertTrue(self.checker.check_archive(self.archive(), Path('readelf'), Path('zipalign')))


class RealPageAlignmentTest(unittest.TestCase):
    """Compile actual Android ELFs and use SDK zipalign, rather than mocked verdicts."""

    @classmethod
    def setUpClass(cls):
        sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '/nonexistent')))
        readelfs = sorted(sdk.glob('ndk/*/toolchains/llvm/prebuilt/*/bin/llvm-readelf'))
        zipaligns = sorted(sdk.glob('build-tools/*/zipalign'))
        if not readelfs or not zipaligns:
            raise unittest.SkipTest('Android NDK and build-tools are required for real ELF/ZIP regression cases')
        cls.readelf = readelfs[-1]
        cls.zipalign = zipaligns[-1]
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        for abi, target in [('arm64-v8a', 'aarch64-linux-android24'), ('x86_64', 'x86_64-linux-android24')]:
            for alignment in (4096, 16384):
                output = cls.root / f'{abi}-{alignment}.so'
                subprocess.run([str(cls.readelf.with_name('clang')), f'--target={target}', '-nostdlib',
                                '-shared', '-fPIC', f'-Wl,-z,max-page-size={alignment}',
                                f'-Wl,-z,common-page-size={alignment}', '-x', 'c', '-', '-o', str(output)],
                               input='int value; int* get_value(void) { return &value; }',
                               text=True, capture_output=True, check=True)

    def packaged(self, alignment, zip_alignment):
        raw = self.root / 'raw.apk'
        with zipfile.ZipFile(raw, 'w') as archive:
            for abi in ('arm64-v8a', 'x86_64'):
                archive.write(self.root / f'{abi}-{alignment}.so', f'lib/{abi}/libfixture.so')
        aligned = self.root / 'aligned.apk'
        subprocess.run([str(self.zipalign), '-f', '-P', str(zip_alignment), '4', str(raw), str(aligned)],
                       capture_output=True, check=True)
        return subprocess.run([sys.executable, str(CHECKER), str(aligned), '--readelf', str(self.readelf),
                               '--zipalign', str(self.zipalign)], capture_output=True, text=True, check=False)

    def test_real_16k_elf_and_zip_pass(self):
        result = self.packaged(16384, 16)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('Verification successful', result.stdout)

    def test_real_4k_elf_fails_even_in_16k_aligned_zip(self):
        result = self.packaged(4096, 16)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn('arm64-v8a/libfixture.so', result.stderr)
        self.assertIn('x86_64/libfixture.so', result.stderr)
        self.assertIn('0x1000', result.stderr)
        self.assertIn('Verification successful', result.stdout)

    def test_real_16k_elf_fails_in_4k_aligned_zip(self):
        result = self.packaged(16384, 4)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn('zipalign exited 1', result.stderr)
        self.assertIn('LOAD segments are 16 KB aligned', result.stdout)


if __name__ == '__main__':
    unittest.main()
