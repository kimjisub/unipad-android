package com.kimjisub.launchpad.midi.driver;

public class MidiFighter extends DriverRef {

	@Override
	public void getSignal(int cmd, int sig, int note, int velo) {

		int x;
		int y;
		if (cmd == 9) {
			if (note >= 36 && note <= 67) {
				x = (67 - note) / 4 + 1;
				y = 4 - (67 - note) % 4;
				onPadTouch(x - 1, y - 1, true, velo);
			} else if (note >= 68 && note <= 99) {
				x = (99 - note) / 4 + 1;
				y = 8 - (99 - note) % 4;
				onPadTouch(x - 1, y - 1, true, velo);
			}

		} else if (cmd == 8) {
			if (note >= 36 && note <= 67) {
				x = (67 - note) / 4 + 1;
				y = 4 - (67 - note) % 4;
				onPadTouch(x - 1, y - 1, false, velo);
			} else if (note >= 68 && note <= 99) {
				x = (99 - note) / 4 + 1;
				y = 8 - (99 - note) % 4;
				onPadTouch(x - 1, y - 1, false, velo);
			}

		}
	}

	@Override
	public void sendPadLED(int x, int y, int velo) {
		x += 1;
		y += 1;

		if (1 <= y && y <= 4)
			sendSignal(9, -110, (-4) * x + y + 67, velo);
		else if (5 <= y && y <= 8)
			sendSignal(9, -110, (-4) * x + y + 95, velo);
	}

	@Override
	public void sendChainLED(int c, int velo) {
	}

	@Override
	public void sendFunctionkeyLED(int f, int velo) {
	}

	@Override
	public void sendClearLED() {
		for (int i = 0; i < 8; i++)
			for (int j = 0; j < 8; j++)
				sendPadLED(i, j, 0);
	}
}
