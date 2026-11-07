#!/usr/bin/env python3
"""Record and normalize the borrowed device before running Android UI tests."""
import argparse
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import re
import subprocess
import time

SETTINGS = (
    ("system", "font_scale"),
    ("global", "window_animation_scale"),
    ("global", "transition_animation_scale"),
    ("global", "animator_duration_scale"),
)
# Display size and density overrides set with `wm`. Tests that turn the display into a tablet or a
# larger screen put them back when they end, but a run stopped inside one (a time limit, a killed
# process) leaves them on the emulator, and every later run starts on that screen.
DISPLAY_OVERRIDES = ("size", "density")
PROBE = "com.kimjisub.launchpad.dev.test/com.kimjisub.launchpad.DeviceSettingsProbe"


def now():
    return datetime.now(timezone.utc).isoformat()


def is_default(key, value):
    if key.startswith("display."):
        return value == ""
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

    def display_override(self, name):
        """The override `wm` reports for the display's size or density, or "" when it has none."""
        match = re.search(rf"^Override {name}: (\S+)$", self.adb("shell", "wm", name), re.MULTILINE)
        return match.group(1) if match else ""

    def snapshot(self):
        values = {f"{namespace}.{key}": self.adb("shell", "settings", "get", namespace, key)
                  for namespace, key in SETTINGS}
        values.update({f"display.{name}": self.display_override(name) for name in DISPLAY_OVERRIDES})
        return {"time": now(), "values": values}

    def reset(self, full_key):
        namespace, key = full_key.split(".")
        if namespace == "display":
            self.adb("shell", "wm", key, "reset")
        else:
            self.adb("shell", "settings", "put", namespace, key, "1.0")

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
                               if not is_default(key, value)]
            self.save(record)
            for full_key in record["reset"]:
                self.reset(full_key)
            deadline = time.monotonic() + 10
            while True:
                record["after"] = self.snapshot()
                self.save(record)
                if all(is_default(key, value) for key, value in record["after"]["values"].items()):
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
