package com.kimjisub.launchpad.midi.driver;

public class MasterKeyboard extends DriverRef {
	@Override
	public void getSignal(int cmd, int sig, int note, int velo) {

		int x;
		int y;

		if (cmd == 9) {
			if (note >= 36 && note <= 67) {
				x = (67 - note) / 4 + 1;
				y = 4 - (67 - note) % 4;
				onPadTouch(x - 1, y - 1, velo != 0, velo);
			} else if (note >= 68 && note <= 99) {
				x = (99 - note) / 4 + 1;
				y = 8 - (99 - note) % 4;
				onPadTouch(x - 1, y - 1, velo != 0, velo);
			}

		} else if (velo == 0) {
			if (note >= 36 && note <= 67) {
				x = (67 - note) / 4 + 1;
				y = 4 - (67 - note) % 4;
				onPadTouch(x - 1, y - 1, false, 0);
			} else if (note >= 68 && note <= 99) {
				x = (99 - note) / 4 + 1;
				y = 8 - (99 - note) % 4;
				onPadTouch(x - 1, y - 1, false, 0);
			}
		}
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