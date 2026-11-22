package com.kimjisub.launchpad.midi;

import android.media.midi.MidiReceiver;

public class InputOnlyMidiFixtureService extends MidiFixtureService {
    @Override protected String key() { return "inputOnly"; }
    @Override public MidiReceiver[] onGetInputPortReceivers() { return new MidiReceiver[0]; }
}
