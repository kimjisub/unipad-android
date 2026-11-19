"""Compile and exercise the same platform-independent Java parser the device driver uses."""
from pathlib import Path
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[2] / "app/src/androidTest/java/com/kimjisub/launchpad/ProcessRecoveryLogs.java"
HARNESS = r'''
package com.kimjisub.launchpad;
import java.text.SimpleDateFormat;
import java.util.Locale;
public class LogCases {
    static final String PKG = "com.kimjisub.launchpad.dev";
    static final String OLD = "25701", NEW = "25937";
    static final String NOW = "2026-11-19 11:42:00.000";
    static long start;
    static String exit(String timestamp, String pid, String pkg, int reason, int subreason) {
        return "ACTIVITY MANAGER PROCESS EXIT INFO\n        ApplicationExitInfo #0:\n"
            + "          timestamp=" + timestamp + " pid=" + pid + " realUid=10193 packageUid=10193\n"
            + "          process=" + pkg + " reason=" + reason + " (DESCRIPTION) subreason=" + subreason + " (DETAIL) status=0\n";
    }
    static String event(String tag, String payload) {
        return "11-19 11:42:03.706   537   565 I " + tag + ": [" + payload + "]\n";
    }
    static String started(String pid, String pkg) { return event("am_proc_start", "0," + pid + ",10193," + pkg + ",next-top-activity,component"); }
    static String fatal(String pid, String message) { return "11-19 11:42:04.000 " + pid + " 123 F AndroidRuntime: " + message + "\n"; }
    public static void main(String[] args) throws Exception {
        start = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).parse(NOW).getTime();
        String history = exit("2026-11-19 11:42:01.951", OLD, PKG, 10, 24);
        String events = started(NEW, PKG);
        boolean reject = false;
        switch (args[0]) {
            case "subreasons":
                for (int subreason : new int[]{4,5,6}) ProcessRecoveryLogs.verify(exit("2026-11-19 11:42:01.951", OLD, PKG, 3, subreason), events, PKG, OLD, NEW, start);
                return;
            case "old_crash": history += exit("2026-11-19 11:41:00.000", OLD, PKG, 4, 0); break;
            case "other_process_history": history += exit("2026-11-19 11:42:03.000", "100", PKG, 5, 0); break;
            case "test_package_history": history += exit("2026-11-19 11:42:03.000", NEW, PKG + ".test", 6, 0); break;
            case "unrelated_fatals": events += fatal("12345", "FATAL EXCEPTION: main") + fatal("12345", "Fatal signal 11"); break;
            case "unrelated_events": events += event("am_crash", "0,100," + PKG + ",123,x") + event("am_anr", "0," + NEW + "," + PKG + ".test,123,x"); break;
            case "wrong_start_pid": events = started(OLD, PKG); reject = true; break;
            case "wrong_start_package": events = started(NEW, PKG + ".test"); reject = true; break;
            case "missing_start": events = ""; reject = true; break;
            case "new_crash": history += exit("2026-11-19 11:42:04.000", NEW, PKG, 4, 0); reject = true; break;
            case "old_native_crash": history = exit("2026-11-19 11:42:01.951", OLD, PKG, 5, 0); reject = true; break;
            case "new_anr": history += exit("2026-11-19 11:42:04.000", NEW, PKG, 6, 0); reject = true; break;
            case "crash_event": events += event("am_crash", "0," + OLD + "," + PKG + ",123,x"); reject = true; break;
            case "anr_event": events += event("am_anr", "0," + NEW + "," + PKG + ",123,x"); reject = true; break;
            case "java_fatal": events += fatal(NEW, "FATAL EXCEPTION: main"); reject = true; break;
            case "native_fatal": events += fatal(OLD, "Fatal signal 11 (SIGSEGV)"); reject = true; break;
            case "reused_pid":
                if (ProcessRecoveryLogs.hasExit(exit("2026-11-19 11:41:00.000", OLD, PKG, 10, 24), PKG, OLD, start)) throw new AssertionError("Old reused PID counted");
                return;
            case "wrong_exit_package":
                if (ProcessRecoveryLogs.hasExit(exit("2026-11-19 11:42:01.951", OLD, PKG + ".test", 10, 24), PKG, OLD, start)) throw new AssertionError("Wrong process counted");
                return;
            case "current_exit":
                if (!ProcessRecoveryLogs.hasExit(history, PKG, OLD, start)) throw new AssertionError("Current exit missing");
                return;
            default: throw new AssertionError("Unknown case");
        }
        try { ProcessRecoveryLogs.verify(history, events, PKG, OLD, NEW, start); }
        catch (AssertionError error) { if (reject) return; throw error; }
        if (reject) throw new AssertionError("Invalid records accepted");
    }
}
'''


class ProcessRecoveryLogsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        harness = cls.root / "LogCases.java"
        harness.write_text(HARNESS)
        subprocess.run(["javac", "-d", str(cls.root), str(SOURCE), str(harness)], check=True, capture_output=True, text=True)

    def test_real_exit_fields_and_current_process_scope(self):
        for case in ("subreasons", "old_crash", "other_process_history", "test_package_history", "unrelated_fatals", "unrelated_events",
                     "wrong_start_pid", "wrong_start_package", "missing_start", "new_crash", "old_native_crash", "new_anr",
                     "crash_event", "anr_event", "java_fatal", "native_fatal", "reused_pid", "wrong_exit_package", "current_exit"):
            with self.subTest(case=case):
                result = subprocess.run(["java", "-cp", str(self.root), "com.kimjisub.launchpad.LogCases", case], capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
