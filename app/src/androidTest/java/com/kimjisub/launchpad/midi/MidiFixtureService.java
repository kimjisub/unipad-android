package com.kimjisub.launchpad.midi;

import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiDeviceService;
import android.media.midi.MidiDeviceStatus;
import android.media.midi.MidiReceiver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Standalone test APK processes cannot use Kotlin libraries deduplicated into the target APK. */
public class MidiFixtureService extends MidiDeviceService {
    static final ConcurrentHashMap<String, MidiFixtureService> INSTANCES = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, Bundle> SNAPSHOTS = new ConcurrentHashMap<>();
    private final List<List<Byte>> lights = Arrays.asList(new ArrayList<>(), new ArrayList<>());
    private volatile MidiDeviceStatus status;

    protected String key() { return "selected"; }

    @Override public void onCreate() { super.onCreate(); INSTANCES.put(key(), this); }
    @Override public void onDestroy() {
        SNAPSHOTS.put(key(), read());
        INSTANCES.remove(key());
        super.onDestroy();
    }
    @Override public MidiReceiver[] onGetInputPortReceivers() {
        MidiReceiver[] receivers = new MidiReceiver[2];
        for (int port = 0; port < receivers.length; port++) {
            final List<Byte> values = lights.get(port);
            receivers[port] = new MidiReceiver() {
                @Override public void onSend(byte[] data, int offset, int count, long timestamp) {
                    synchronized (values) {
                        if (values.size() + count <= 32768) {
                            for (int i = offset; i < offset + count; i++) values.add(data[i]);
                        }
                    }
                }
            };
        }
        return receivers;
    }
    @Override public void onDeviceStatusChanged(MidiDeviceStatus value) { status = value; }

    private Bundle read() {
        Bundle result = new Bundle();
        MidiDeviceStatus current = status;
        MidiDeviceInfo info = getDeviceInfo();
        for (int port = 0; port < 2; port++) {
            List<Byte> values = lights.get(port);
            synchronized (values) {
                byte[] bytes = new byte[values.size()];
                for (int i = 0; i < bytes.length; i++) bytes[i] = values.get(i);
                result.putByteArray("lights" + port, bytes);
            }
            result.putInt("subscribers" + port, current != null && info != null && port < info.getOutputPortCount()
                ? current.getOutputPortOpenCount(port) : 0);
            result.putBoolean("open" + port, current != null && info != null && port < info.getInputPortCount()
                && current.isInputPortOpen(port));
        }
        result.putInt("pid", Process.myPid());
        result.putInt("padPresses", MidiFixturePadActivity.PRESSES.get());
        result.putInt("padReleases", MidiFixturePadActivity.RELEASES.get());
        MidiFixturePadActivity pad = MidiFixturePadActivity.current;
        result.putInt("padTask", pad != null ? pad.getTaskId() : -1);
        return result;
    }

    Bundle command(String method, Bundle extras) {
        switch (method) {
            case "read": return read();
            case "send":
                byte[] bytes = extras.getByteArray("bytes");
                if (bytes == null) throw new IllegalArgumentException("Missing MIDI bytes");
                try { getOutputPortReceivers()[extras.getInt("port")].send(bytes, 0, bytes.length); }
                catch (IOException error) { throw new IllegalStateException(error); }
                break;
            case "clear":
                for (List<Byte> values : lights) synchronized (values) { values.clear(); }
                break;
            case "exit":
                new Handler(Looper.getMainLooper()).postDelayed(() -> Process.killProcess(Process.myPid()), 100);
                break;
            default: throw new IllegalArgumentException("Unknown fixture command");
        }
        return new Bundle();
    }
}
