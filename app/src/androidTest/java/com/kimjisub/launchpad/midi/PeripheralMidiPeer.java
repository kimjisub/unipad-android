package com.kimjisub.launchpad.midi;

import android.media.midi.MidiDeviceService;
import android.media.midi.MidiReceiver;
import android.os.Process;
import java.io.IOException;

/** Standalone test APK process: only framework classes, with no target-app dependencies. */
public class PeripheralMidiPeer extends MidiDeviceService {
    private volatile int messages;

    @Override
    public MidiReceiver[] onGetInputPortReceivers() {
        return new MidiReceiver[] {
            new MidiReceiver() {
                @Override
                public void onSend(byte[] data, int offset, int count, long timestamp) throws IOException {
                    getOutputPortReceivers()[0].send(data, offset, count);
                    byte[] counter = {(byte) 0xb0, 111, (byte) (++messages % 128)};
                    getOutputPortReceivers()[0].send(counter, 0, counter.length);
                }
            },
            new MidiReceiver() {
                @Override
                public void onSend(byte[] data, int offset, int count, long timestamp) throws IOException {
                    if (count == 0) return;
                    if (data[offset] == 0) {
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
