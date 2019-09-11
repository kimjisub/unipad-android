package com.kimjisub.launchpad.midi.driver;

import com.kimjisub.launchpad.manager.LaunchpadColor;

public class LaunchpadS extends DriverRef {
	static final int[][] circleCode = {
			{11, -80, 104},
			{11, -80, 105},
			{11, -80, 106},
			{11, -80, 107},
			{11, -80, 108},
			{11, -80, 109},
			{11, -80, 110},
			{11, -80, 111},
			{9, -112, 8},
			{9, -112, 24},
			{9, -112, 40},
			{9, -112, 56},
			{9, -112, 72},
			{9, -112, 88},
			{9, -112, 104},
			{9, -112, 120},
	};

	@Override
	public void getSignal(int cmd, int sig, int note, int velo) {
		if (cmd == 9) {
			int x = note / 16 + 1;
			int y = note % 16 + 1;

			if (y >= 1 && y <= 8)
				onPadTouch(x - 1, y - 1, velo != 0, velo);
			else if (y == 9) {
				onChainTouch(x - 1, velo != 0);
				onFunctionkeyTouch(x - 1 + 8, velo != 0);
			}
		} else if (cmd == 11) {
			if (104 <= note && note <= 111)
				onFunctionkeyTouch(note - 104, velo != 0);
		}
	}

	@Override
	public void sendPadLED(int x, int y, int velo) {
		send09Signal(x * 16 + y, LaunchpadColor.SCode[velo]);
	}

	@Override
	public void sendChainLED(int c, int velo) {
		if (0 <= c && c <= 7)
			sendFunctionkeyLED(c + 8, velo);
	}

	@Override
	public void sendFunctionkeyLED(int f, int velo) {
		if (0 <= f && f <= 15)
			sendSignal((byte) circleCode[f][0], (byte) circleCode[f][1], (byte) circleCode[f][2], (byte) LaunchpadColor.SCode[velo]);
	}

	@Override
	public void sendClearLED() {
		for (int i = 0; i < 8; i++)
			for (int j = 0; j < 8; j++)
				sendPadLED(i, j, 0);
		for (int i = 0; i < 16; i++)
			sendFunctionkeyLED(i, 0);
	}
}