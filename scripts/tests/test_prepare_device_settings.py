"""Exercise contaminated devices, failed resets, and app-side acknowledgement."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("prepare_device_settings", Path(__file__).resolve().parents[1] / "prepare_device_settings.py")
settings = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(settings)


class DeviceSettingsTest(unittest.TestCase):
    def device(self, values=None):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        device = settings.DeviceSettings("adb", "borrowed-device", directory.name)
        values = dict(values or {f"{ns}.{key}": "1.0" for ns, key in settings.SETTINGS})
        calls = []

        def adb(*args, **kwargs):
            calls.append(args)
            if args[:2] == ("shell", "settings"):
                key = ".".join(args[3:5])
                if args[2] == "get":
                    return values[key]
                values[key] = args[5]
                return ""
            return "INSTRUMENTATION_RESULT: stream=fontScale=1.0\nDEVICE_SETTINGS_READY\n\nINSTRUMENTATION_CODE: -1"

        device.adb = adb
        return device, values, calls

    def test_resets_all_four_contaminated_settings_and_records_final_values(self):
        original = {f"{ns}.{key}": "2.0" if ns == "system" else "10" for ns, key in settings.SETTINGS}
        device, values, calls = self.device(original)
        device.prepare()
        device.verify()
        values["global.animator_duration_scale"] = "10"
        device.finish()
        record = json.loads(device.path.read_text())
        self.assertEqual(record["before"]["values"], original)
        self.assertEqual(set(record["reset"]), set(original))
        self.assertTrue(all(value == "1.0" for value in record["after"]["values"].values()))
        self.assertEqual(record["final"]["values"]["global.animator_duration_scale"], "10")
        self.assertEqual(record["status"], "ready")
        self.assertIn("verified_at", record)
        self.assertEqual(len([args for args in calls if args[2] == "put"]), 4)
        summary = (device.directory / "device-settings-summary.txt").read_text()
        self.assertIn("system.font_scale='2.0'->'1.0'", summary)
        self.assertEqual(len(summary.splitlines()), 1)

    def test_default_and_absent_values_do_not_get_written(self):
        for value in ("", "null", "1", "1.0", "1.00"):
            with self.subTest(value=value):
                device, _, calls = self.device({f"{ns}.{key}": value for ns, key in settings.SETTINGS})
                device.prepare()
                self.assertFalse(any(args[2] == "put" for args in calls))

    def test_failed_write_stops_preparation_and_preserves_before_values(self):
        device, _, _ = self.device({f"{ns}.{key}": "10" for ns, key in settings.SETTINGS})
        adb = device.adb

        def failing(*args, **kwargs):
            if args[2] == "put":
                raise subprocess.CalledProcessError(1, "adb")
            return adb(*args, **kwargs)

        device.adb = failing
        with self.assertRaises(subprocess.CalledProcessError):
            device.prepare()
        record = json.loads(device.path.read_text())
        self.assertEqual(record["status"], "failed")
        self.assertEqual(record["before"]["values"]["system.font_scale"], "10")

    def test_unapplied_settings_stop_preparation(self):
        device, _, _ = self.device({f"{ns}.{key}": "10" for ns, key in settings.SETTINGS})
        adb = device.adb
        device.adb = lambda *args, **kwargs: "" if args[2] == "put" else adb(*args, **kwargs)
        with patch.object(settings.time, "monotonic", side_effect=[0, 11]):
            with self.assertRaisesRegex(RuntimeError, "did not return"):
                device.prepare()
        self.assertEqual(json.loads(device.path.read_text())["status"], "failed")

    def test_zero_exit_without_app_confirmation_is_a_failure(self):
        for output in ("INSTRUMENTATION_CODE: -1", "DEVICE_SETTINGS_READY\nINSTRUMENTATION_CODE: 0", "INSTRUMENTATION_FAILED"):
            with self.subTest(output=output):
                device, _, _ = self.device()
                device.prepare()
                device.adb = lambda *args, **kwargs: output
                with self.assertRaisesRegex(RuntimeError, "did not confirm"):
                    device.verify()
                self.assertEqual(json.loads(device.path.read_text())["status"], "failed")
                self.assertEqual((device.directory / "device-settings-app.txt").read_text(), output + "\n")

    def test_end_read_failure_is_recorded_and_raised(self):
        device, _, _ = self.device()
        device.prepare()
        device.adb = lambda *args, **kwargs: (_ for _ in ()).throw(subprocess.TimeoutExpired("adb", 30))
        with self.assertRaises(subprocess.TimeoutExpired):
            device.finish()
        self.assertIn("final_error", json.loads(device.path.read_text()))


if __name__ == "__main__":
    unittest.main()
