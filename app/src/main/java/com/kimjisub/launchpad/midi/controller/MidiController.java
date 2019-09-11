package com.kimjisub.launchpad.midi.controller;

import android.content.Context;

import com.kimjisub.launchpad.activity.LaunchpadActivity;
import com.kimjisub.launchpad.midi.MidiConnection;
import com.kimjisub.launchpad.midi.driver.DriverRef;

public abstract class MidiController {

	void attach() {
		MidiConnection.setController(this);
	}

	void detach() {
		MidiConnection.removeController(this);
	}

	public abstract void onAttach();

	public abstract void onDetach();

	public abstract void onPadTouch(int x, int y, boolean upDown, int velo);

	public abstract void onFunctionkeyTouch(int f, boolean upDown);

	public abstract void onChainTouch(int c, boolean upDown);

	public abstract void onUnknownEvent(int cmd, int sig, int note, int velo);
}
