package com.kimjisub.launchpad.midi.driver;

public class Noting extends DriverRef {
	@Override
	public void getSignal(int cmd, int sig, int note, int velo) {
	}

	@Override
	public void sendPadLED(int x, int y, int velo) {
	}

	@Override
	public void sendChainLED(int c, int velo) {
	}

	@Override
	public void sendFunctionkeyLED(int f, int velo) {
	}

	@Override
	public void sendClearLED() {
	}

}