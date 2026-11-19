package com.kimjisub.launchpad;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** System records used by the independent process-recovery driver; no Android dependencies. */
final class ProcessRecoveryLogs {
    private static final Pattern EXIT_HEADER = Pattern.compile("(?m)^\\s*ApplicationExitInfo #\\d+:\\s*$");
    private static final Pattern EXIT_TIME_PID = Pattern.compile("(?m)^\\s*timestamp=(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) pid=(\\d+)\\b");
    // The field starts at whitespace: subreason= is a different field, never an exit reason.
    private static final Pattern EXIT_PROCESS_REASON = Pattern.compile("(?m)^\\s*process=(\\S+)\\s+reason=(\\d+)\\b");

    private static boolean currentExit(String record, String packageName, String pid, long startedAt) {
        Matcher identity = EXIT_TIME_PID.matcher(record);
        Matcher process = EXIT_PROCESS_REASON.matcher(record);
        if (!identity.find() || !process.find() || !identity.group(2).equals(pid) || !process.group(1).equals(packageName)) return false;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
        format.setLenient(false);
        try {
            // dumpsys uses the device's local timezone, as does this in-device parser.
            // A reused PID from an earlier run cannot satisfy this run's exit check.
            return format.parse(identity.group(1)).getTime() >= startedAt;
        } catch (ParseException error) {
            throw new AssertionError("Cannot read exit timestamp: " + record, error);
        }
    }

    static boolean hasExit(String history, String packageName, String pid, long startedAt) {
        if (!history.contains("ACTIVITY MANAGER PROCESS EXIT INFO")) return false;
        for (String record : EXIT_HEADER.split(history)) {
            if (currentExit(record, packageName, pid, startedAt)) return true;
        }
        return false;
    }

    static void verify(String history, String events, String packageName, String oldPid, String newPid, long startedAt) {
        String exactPackage = Pattern.quote(packageName);
        if (!Pattern.compile("\\bam_proc_start:\\s*\\[\\d+,\\s*" + Pattern.quote(newPid)
                + ",\\s*\\d+,\\s*" + exactPackage + ",").matcher(events).find())
            throw new AssertionError("System event log has no start for restored process " + newPid);
        String pids = "(?:" + Pattern.quote(oldPid) + "|" + Pattern.quote(newPid) + ")";
        if (Pattern.compile("\\bam_(?:crash|anr):\\s*\\[\\d+,\\s*" + pids + ",\\s*" + exactPackage + ",").matcher(events).find())
            throw new AssertionError("Crash or ANR in recovery processes: " + events);
        // AndroidRuntime/libc fatal lines carry the crashing process in the threadtime PID column.
        if (Pattern.compile("(?m)^\\S+\\s+\\S+\\s+" + pids + "\\s+\\d+\\s+\\S\\s+[^\\n]*(?:FATAL EXCEPTION|Fatal signal)").matcher(events).find())
            throw new AssertionError("Fatal error in recovery processes: " + events);
        for (String record : EXIT_HEADER.split(history)) {
            if (!currentExit(record, packageName, oldPid, startedAt) && !currentExit(record, packageName, newPid, startedAt)) continue;
            Matcher reason = EXIT_PROCESS_REASON.matcher(record);
            if (reason.find()) {
                int value = Integer.parseInt(reason.group(2));
                if (value == 4 || value == 5 || value == 6)
                    throw new AssertionError("System reported crash/native crash/ANR: " + record);
            }
        }
    }
}
