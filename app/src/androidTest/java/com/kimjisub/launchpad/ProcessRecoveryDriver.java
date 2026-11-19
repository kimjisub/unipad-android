package com.kimjisub.launchpad;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Self-targeted instrumentation. Uses only platform classes: app dependencies are not in this APK. */
@android.annotation.TargetApi(33)
public class ProcessRecoveryDriver extends Instrumentation {
    private static final String PACKAGE = "com.kimjisub.launchpad.dev";
    private static final String FOLDER = "/storage/emulated/0/Android/data/" + PACKAGE + "/files/UniPack/process-recovery-pack";
    private static final String PROBE = PACKAGE + "/com.kimjisub.launchpad.tool.ProcessRecoveryProbe";
    private static final String EVENTS = "files/process-recovery-events.jsonl";
    private static final String EVIDENCE = "/data/local/tmp/unipad_tests/process-recovery";

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    private interface Condition { boolean get(); }
    private interface NodeMatcher { boolean matches(AccessibilityNodeInfo node); }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static String quote(String text) { return "'" + text.replace("'", "'\"'\"'") + "'"; }
    private String shell(String command) {
        // UiAutomation tokenizes its command rather than interpreting shell quoting.
        // Feed scripts to a real shell through stdin so quotes, redirects and pipelines work.
        ParcelFileDescriptor[] pipes = getUiAutomation().executeShellCommandRw("sh");
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(pipes[0])) {
            try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])) {
                output.write(("exec 2>&1\n" + command + "\nexit\n").getBytes(StandardCharsets.UTF_8));
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (Exception error) { throw new IllegalStateException("Shell command failed: " + command, error); }
    }
    private String app(String command) { return shell("run-as " + PACKAGE + " sh -c " + quote(command)); }
    private void await(String message, Condition condition) {
        long end = SystemClock.elapsedRealtime() + 30000;
        while (!condition.get()) {
            require(SystemClock.elapsedRealtime() < end, message);
            SystemClock.sleep(100);
        }
    }
    private List<JSONObject> records() {
        List<JSONObject> rows = new ArrayList<>();
        for (String line : app("cat " + EVENTS).split("\n")) {
            try { rows.add(new JSONObject(line)); } catch (org.json.JSONException ignored) { /* A writer may still be appending. */ }
        }
        return rows;
    }
    private JSONObject ready(String pid) {
        JSONObject result = null;
        for (JSONObject row : records()) if (row.optString("event").equals("ready") && row.optString("pid").equals(pid)) result = row;
        return result;
    }
    private int plays(String pid) {
        int count = 0;
        for (JSONObject row : records()) if (row.optString("event").equals("play") && row.optString("pid").equals(pid)) count++;
        return count;
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, NodeMatcher matches) {
        if (node == null || !node.refresh()) return null;
        if (matches.matches(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), matches);
            if (found != null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo node(String description, NodeMatcher matches) {
        // Wait for the selected control's screen coordinates to settle after rotation/Recents animation.
        final AccessibilityNodeInfo[] found = {null};
        final Rect[] previous = {null};
        final long[] changed = {SystemClock.elapsedRealtime()};
        await(description + " missing or moving", () -> {
            found[0] = find(getUiAutomation().getRootInActiveWindow(), matches);
            if (found[0] == null) { previous[0] = null; changed[0] = SystemClock.elapsedRealtime(); return false; }
            Rect bounds = new Rect(); found[0].getBoundsInScreen(bounds);
            if (!bounds.equals(previous[0])) { previous[0] = bounds; changed[0] = SystemClock.elapsedRealtime(); }
            return !bounds.isEmpty() && SystemClock.elapsedRealtime() - changed[0] >= 500;
        });
        return found[0];
    }
    private void tap(int x, int y) {
        long down = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { require(getUiAutomation().injectInputEvent(event, true), "Touch injection failed"); }
            finally { event.recycle(); }
        }
    }
    private void tap(AccessibilityNodeInfo node) {
        require(node.refresh(), "Control disappeared before press");
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds); tap(bounds.centerX(), bounds.centerY());
    }
    private void key(int key) {
        // The action/code constructor leaves event times at zero; the OS rejects it as stale.
        long down = SystemClock.uptimeMillis();
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent event = new KeyEvent(down, SystemClock.uptimeMillis(), action, key, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
            require(getUiAutomation().injectInputEvent(event, true), "Key injection failed: " + key + "/" + action);
        }
    }
    private void tree(AccessibilityNodeInfo node, StringBuilder text, int depth) {
        if (node == null) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        for (int i = 0; i < depth; i++) text.append(' ');
        text.append(node.getViewIdResourceName()).append(" ").append(node.getText())
            .append(" ").append(node.getContentDescription()).append(" ").append(bounds).append("\n");
        for (int i = 0; i < node.getChildCount(); i++) tree(node.getChild(i), text, depth + 1);
    }
    private void capture(String name) throws Exception {
        File stage = getContext().getExternalFilesDir(null);
        require(stage != null, "No capture staging folder");
        require(stage.getParentFile().setExecutable(true, false) && stage.setExecutable(true, false), "Cannot expose capture folder");
        File image = new File(stage, name + ".png");
        File hierarchy = new File(stage, name + ".txt");
        try {
            Bitmap bitmap = getUiAutomation().takeScreenshot(); require(bitmap != null, "Screenshot failed");
            try (FileOutputStream output = new FileOutputStream(image)) {
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), "PNG encoding failed");
            } finally { bitmap.recycle(); }
            StringBuilder text = new StringBuilder(); tree(getUiAutomation().getRootInActiveWindow(), text, 0);
            try (FileOutputStream output = new FileOutputStream(hierarchy)) {
                output.write(text.toString().getBytes(StandardCharsets.UTF_8));
            }
            for (File file : new File[]{image, hierarchy}) {
                require(file.setReadable(true, false), "Cannot expose capture");
                shell("cp " + file.getPath() + " " + EVIDENCE + "/" + file.getName());
                require(shell("stat -c %s " + EVIDENCE + "/" + file.getName()).equals(Long.toString(file.length())), "Capture copy failed");
            }
        } finally { image.delete(); hierarchy.delete(); }
    }
    private void pressPad(String pid) throws Exception {
        JSONObject state = ready(pid); require(state != null, "No loaded play screen");
        require(state.getString("title").equals("Process Recovery Silent Pack"), "Wrong pack title: " + state);
        require(state.getString("path").equals(FOLDER), "Wrong pack path: " + state);
        int before = plays(pid); tap(state.getInt("x"), state.getInt("y"));
        await("Pad did not request sound in process " + pid, () -> plays(pid) == before + 1);
    }
    @Override public void onStart() {
        String outcome = "PROCESS_RECOVERY_FAILED";
        String detail;
        try {
            require(android.os.Build.VERSION.SDK_INT >= 33, "API 33+ required");
            require(shell("printf '%s' 'process recovery shell ready'").equals("process recovery shell ready"),
                "Shell script transport did not preserve arguments");
            android.accessibilityservice.AccessibilityServiceInfo info = getUiAutomation().getServiceInfo();
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            getUiAutomation().setServiceInfo(info);
            require(shell("settings get global airplane_mode_on").equals("1"), "Offline device required");
            shell("rm -rf " + EVIDENCE + "; mkdir -p " + EVIDENCE);
            shell("am force-stop " + PACKAGE);
            app("mkdir -p files; rm -f " + EVENTS);
            require(app("pm enable " + PROBE).contains("enabled"), "Could not enable debug observer");
            long startedAt = System.currentTimeMillis();
            require(shell("logcat -b all -c && echo cleared").equals("cleared"), "Cannot clear system log buffers");
            android.content.Intent launch = getContext().getPackageManager().getLaunchIntentForPackage(PACKAGE);
            require(launch != null, "Target launch intent missing");
            getContext().startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            tap(node("Fixture row", n -> "main_pack_process-recovery-pack".equals(n.getViewIdResourceName())));
            tap(node("Play control", n -> "Play".contentEquals(n.getText() == null ? "" : n.getText())));
            String oldPid = shell("pidof " + PACKAGE);
            require(oldPid.matches("[0-9]+") && !oldPid.equals(Integer.toString(android.os.Process.myPid())), "No independent app process: " + oldPid);
            await("Pack never finished loading", () -> ready(oldPid) != null);
            pressPad(oldPid); capture("before-home"); key(KeyEvent.KEYCODE_HOME);
            Pattern stopped = Pattern.compile("(?s)ActivityRecord\\{[^\\n]*" + Pattern.quote(PACKAGE) + "/[^\\n}]*\\.PlayActivity[^\\n]*\\n(?:(?!ActivityRecord\\{).)*?\\b(?:mState|state)=STOPPED\\b");
            await("System has not acknowledged the stopped play activity", () -> stopped.matcher(shell("dumpsys activity activities")).find());
            shell("am kill " + PACKAGE);
            await("am kill did not end the background app process", () -> shell("pidof " + PACKAGE).isEmpty());
            key(KeyEvent.KEYCODE_APP_SWITCH);
            String appLabel = getContext().getPackageManager().getApplicationInfo(PACKAGE,
                PackageManager.ApplicationInfoFlags.of(0)).loadLabel(getContext().getPackageManager()).toString();
            AccessibilityNodeInfo card = node("Recent task for " + appLabel, n -> {
                if (n.getViewIdResourceName() == null || !n.getViewIdResourceName().endsWith(":id/snapshot")) return false;
                // Other tasks, including instrumentation's empty activity, can precede this card.
                AccessibilityNodeInfo owner = n.getParent();
                return owner != null && appLabel.contentEquals(owner.getContentDescription() == null ? "" : owner.getContentDescription());
            });
            capture("recent-apps"); tap(card);
            final String[] restoredPid = {""};
            await("Recents did not start a new app process", () -> {
                restoredPid[0] = shell("pidof " + PACKAGE);
                return restoredPid[0].matches("[0-9]+") && !restoredPid[0].equals(oldPid);
            });
            String newPid = restoredPid[0];
            await("Restored play screen never finished loading", () -> ready(newPid) != null);
            JSONObject restored = ready(newPid);
            require(restored.getInt("taskId") == ready(oldPid).getInt("taskId"), "Returned to another task");
            require(restored.getBoolean("savedState"), "Fresh launch instead of saved state restoration");
            pressPad(newPid); capture("after-return");
            require(shell("pidof " + PACKAGE).equals(newPid), "Restored process exited after pad input");
            final String[] exits = {""};
            await("System exit history did not record the terminated process", () -> {
                exits[0] = shell("dumpsys activity exit-info " + PACKAGE);
                return ProcessRecoveryLogs.hasExit(exits[0], PACKAGE, oldPid, startedAt);
            });
            shell("logcat -d -b crash -b events -v threadtime > " + EVIDENCE + "/errors.txt");
            String errors = shell("cat " + EVIDENCE + "/errors.txt");
            ProcessRecoveryLogs.verify(exits[0], errors, PACKAGE, oldPid, newPid, startedAt);
            detail = "old_pid=" + oldPid + " new_pid=" + newPid + " started_at_ms=" + startedAt + " saved_state=true same_pack=true sound_request=true";
            outcome = "PROCESS_RECOVERY_PASSED";
        } catch (Throwable error) {
            detail = android.util.Log.getStackTraceString(error);
            try { capture("failure"); } catch (Throwable captureError) { detail += "\n" + captureError; }
        } finally {
            shell("logcat -d -v threadtime > " + EVIDENCE + "/logcat.txt");
            shell("dumpsys activity activities > " + EVIDENCE + "/activities.txt");
            shell("dumpsys activity exit-info " + PACKAGE + " > " + EVIDENCE + "/exit-info.txt");
            shell("run-as " + PACKAGE + " cat " + EVENTS + " > " + EVIDENCE + "/events.jsonl");
            app("rm -f " + EVENTS);
            shell("rm -rf " + FOLDER);
            app("pm disable " + PROBE); shell("am force-stop " + PACKAGE);
        }
        Bundle result = new Bundle(); result.putString("stream", "\n" + outcome + "\n" + detail + "\n");
        finish(outcome.equals("PROCESS_RECOVERY_PASSED") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
