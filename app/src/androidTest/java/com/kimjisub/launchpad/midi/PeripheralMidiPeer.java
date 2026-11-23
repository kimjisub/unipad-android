package com.kimjisub.launchpad.midi;

import android.media.midi.MidiDeviceService;
import android.media.midi.MidiReceiver;
import android.os.Process;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone test APK process: only framework classes, with no target-app dependencies.
 * Control port commands (first byte): 0 reset, 1 terminate, 2 stop reading presses, 3 read again,
 * 4 send the remaining bytes as the computer's lights, 5 report the bytes that reached the press port
 * as {@code F0 7D} followed by five 7-bit digits, most significant first, and {@code F7}.
 */
public class PeripheralMidiPeer extends MidiDeviceService {
    private volatile int messages;
    private final AtomicLong pressBytes = new AtomicLong();
    /** While set, the press port stops reading, like a computer with no program reading MIDI. */
    private volatile CountDownLatch stalled;

    @Override
    public MidiReceiver[] onGetInputPortReceivers() {
        return new MidiReceiver[] {
            new MidiReceiver() {
                @Override
                public void onSend(byte[] data, int offset, int count, long timestamp) throws IOException {
                    pressBytes.addAndGet(count);
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
                    if (data[offset] == 4) {
                        getOutputPortReceivers()[0].send(data, offset + 1, count - 1);
                        return;
                    }
                    if (data[offset] == 5) {
                        long received = pressBytes.get();
                        byte[] report = new byte[8];
                        report[0] = (byte) 0xf0;
                        report[1] = 0x7d;
                        for (int digit = 0; digit < 5; digit++) report[6 - digit] = (byte) ((received >> (7 * digit)) & 0x7f);
                        report[7] = (byte) 0xf7;
                        getOutputPortReceivers()[0].send(report, 0, report.length);
                        return;
                    }
                    if (data[offset] == 0) {
                        // A reset also ends a stall left by an earlier test.
                        CountDownLatch stall = stalled;
                        stalled = null;
                        if (stall != null) stall.countDown();
                        messages = 0;
                        pressBytes.set(0);
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
