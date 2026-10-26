#!/usr/bin/env python3
"""Record and normalize the borrowed device before running Android UI tests."""
import argparse
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import subprocess
import time

SETTINGS = (
    ("system", "font_scale"),
    ("global", "window_animation_scale"),
    ("global", "transition_animation_scale"),
    ("global", "animator_duration_scale"),
)
PROBE = "com.kimjisub.launchpad.dev.test/com.kimjisub.launchpad.DeviceSettingsProbe"


def now():
    return datetime.now(timezone.utc).isoformat()


def is_default(value):
    if value in ("", "null"):
        return True
    try:
        return Decimal(value) == 1
    except InvalidOperation:
        return False


class DeviceSettings:
    def __init__(self, adb, serial, directory):
        self.command = [adb, "-s", serial]
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.path = self.directory / "device-settings.json"

    def adb(self, *args, timeout=30):
        return subprocess.run(self.command + list(args), check=True, text=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout).stdout.strip()

    def snapshot(self):
        return {"time": now(), "values": {f"{namespace}.{key}": self.adb(
            "shell", "settings", "get", namespace, key) for namespace, key in SETTINGS}}

    def save(self, record):
        self.path.write_text(json.dumps(record, indent=2) + "\n")
        before = record.get("before", {}).get("values", {})
        after = record.get("after", {}).get("values", {})
        changes = "; ".join(f"{key}={before.get(key)!r}->{after.get(key)!r}"
                            for key in record.get("reset", [])) or "none"
        summary = (f"device_settings: status={record['status']} time={record['started_at']} "
                   f"reset={changes} verified_at={record.get('verified_at', 'pending')}")
        (self.directory / "device-settings-summary.txt").write_text(summary + "\n")

    def prepare(self):
        record = {"started_at": now(), "status": "preparing", "reset": []}
        self.save(record)  # Replace any summary from a previous run even if the device is unreachable.
        try:
            record["before"] = self.snapshot()
            record["reset"] = [key for key, value in record["before"]["values"].items()
                               if not is_default(value)]
            self.save(record)
            for full_key in record["reset"]:
                namespace, key = full_key.split(".")
                self.adb("shell", "settings", "put", namespace, key, "1.0")
            deadline = time.monotonic() + 10
            while True:
                record["after"] = self.snapshot()
                self.save(record)
                if all(is_default(value) for value in record["after"]["values"].values()):
                    break
                if time.monotonic() >= deadline:
                    raise RuntimeError("Device settings did not return to their defaults")
                time.sleep(0.2)
            record["status"] = "awaiting_app"
        except (subprocess.SubprocessError, RuntimeError) as error:
            record["status"] = "failed"
            record["error"] = str(error)
            raise
        finally:
            self.save(record)

    def verify(self):
        record = json.loads(self.path.read_text())
        try:
            output = self.adb("shell", "am", "instrument", "-w", "-r", PROBE, timeout=60)
            (self.directory / "device-settings-app.txt").write_text(output + "\n")
            # am instrument may report failure with exit code zero. Require our app-side verdict.
            if "DEVICE_SETTINGS_READY\n" not in output + "\n" or "INSTRUMENTATION_CODE: -1" not in output:
                raise RuntimeError("The app did not confirm the default device settings")
            record["status"] = "ready"
            record["verified_at"] = now()
        except (subprocess.SubprocessError, RuntimeError) as error:
            record["status"] = "failed"
            record["error"] = str(error)
            raise
        finally:
            self.save(record)

    def finish(self):
        record = json.loads(self.path.read_text())
        try:
            record["final"] = self.snapshot()
        except subprocess.SubprocessError as error:
            record["final_error"] = str(error)
            raise
        finally:
            self.save(record)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("prepare", "verify", "finish"))
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--results", required=True)
    args = parser.parse_args()
    settings = DeviceSettings(args.adb, args.serial, args.results)
    try:
        getattr(settings, args.phase)()
    except (subprocess.SubprocessError, RuntimeError) as error:
        parser.exit(1, f"Device settings {args.phase} failed: {error}\n")


if __name__ == "__main__":
    main()
