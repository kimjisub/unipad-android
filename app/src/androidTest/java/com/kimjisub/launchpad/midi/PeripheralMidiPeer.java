package com.kimjisub.launchpad.midi;

import android.media.midi.MidiDeviceService;
import android.media.midi.MidiReceiver;
import android.os.Process;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/** Standalone test APK process: only framework classes, with no target-app dependencies. */
public class PeripheralMidiPeer extends MidiDeviceService {
    private volatile int messages;
    /** While set, the press port stops reading, like a computer with no program reading MIDI. */
    private volatile CountDownLatch stalled;

    @Override
    public MidiReceiver[] onGetInputPortReceivers() {
        return new MidiReceiver[] {
            new MidiReceiver() {
                @Override
                public void onSend(byte[] data, int offset, int count, long timestamp) throws IOException {
                    CountDownLatch stall = stalled;
                    if (stall != null) {
                        try { stall.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                    getOutputPortReceivers()[0].send(data, offset, count);
                    byte[] counter = {(byte) 0xb0, 111, (byte) (++messages % 128)};
                    getOutputPortReceivers()[0].send(counter, 0, counter.length);
                }
            },
            new MidiReceiver() {
                @Override
                public void onSend(byte[] data, int offset, int count, long timestamp) throws IOException {
                    if (count == 0) return;
                    if (data[offset] == 2) {
                        // The new stall is in place before the previous one lets its sender go on.
                        CountDownLatch previous = stalled;
                        stalled = new CountDownLatch(1);
                        if (previous != null) previous.countDown();
                        byte[] stalling = {(byte) 0xb0, 111, 127};
                        getOutputPortReceivers()[0].send(stalling, 0, stalling.length);
                        return;
                    }
                    if (data[offset] == 3) {
                        CountDownLatch stall = stalled;
                        stalled = null;
                        if (stall != null) stall.countDown();
                        return;
                    }
                    if (data[offset] == 0) {
                        // A reset also ends a stall left by an earlier test.
                        CountDownLatch stall = stalled;
                        stalled = null;
                        if (stall != null) stall.countDown();
                        messages = 0;
                        byte[] reset = {(byte) 0xb0, 111, 0};
                        getOutputPortReceivers()[0].send(reset, 0, reset.length);
                    } else {
                        // Only this dedicated peer process is terminated.
                        Process.killProcess(Process.myPid());
                    }
                }
            }
        };
    }
}
