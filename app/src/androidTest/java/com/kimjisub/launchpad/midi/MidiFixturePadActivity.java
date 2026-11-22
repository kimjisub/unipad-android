package com.kimjisub.launchpad.midi;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The other app's window in split-screen tests: one pad that plays the Matrix top-left note
 * through the fixture service's output port, as a MIDI controller app on the same phone does.
 */
public class MidiFixturePadActivity extends Activity {
    static final AtomicInteger PRESSES = new AtomicInteger();
    static final AtomicInteger RELEASES = new AtomicInteger();
    static volatile MidiFixturePadActivity current;
    private static final Set<MidiFixturePadActivity> OPEN = new CopyOnWriteArraySet<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        View pad = new View(this);
        pad.setContentDescription("Fixture pad");
        pad.setBackgroundColor(0xff2e5bff);
        pad.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    send(0x91, 127);
                    PRESSES.incrementAndGet();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    send(0x81, 64);
                    RELEASES.incrementAndGet();
                    break;
                default:
                    break;
            }
            return true;
        });
        setContentView(pad);
        current = this;
        OPEN.add(this);
    }

    @Override protected void onDestroy() {
        OPEN.remove(this);
        if (current == this) current = null;
        super.onDestroy();
    }

    /** Closes every pad window so the split screen ends; the service keeps running. */
    static void close() {
        for (MidiFixturePadActivity activity : OPEN) activity.runOnUiThread(activity::finishAndRemoveTask);
    }

    private static void send(int status, int velocity) {
        MidiFixtureService service = MidiFixtureService.INSTANCES.get("selected");
        if (service == null) return;
        Bundle extras = new Bundle();
        extras.putByteArray("bytes", new byte[] {(byte) status, 64, (byte) velocity});
        extras.putInt("port", 1);
        service.command("send", extras);
    }
}
