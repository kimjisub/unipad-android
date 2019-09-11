package com.kimjisub.launchpad.midi.driver;

import com.kimjisub.manager.Log;

public class LaunchpadPRO extends DriverRef {
	static final int[][] circleCode = {
			{11, -80, 91},
			{11, -80, 92},
			{11, -80, 93},
			{11, -80, 94},
			{11, -80, 95},
			{11, -80, 96},
			{11, -80, 97},
			{11, -80, 98},
			{11, -80, 89},
			{11, -80, 79},
			{11, -80, 69},
			{11, -80, 59},
			{11, -80, 49},
			{11, -80, 39},
			{11, -80, 29},
			{11, -80, 19},
			{11, -80, 8},
			{11, -80, 7},
			{11, -80, 6},
			{11, -80, 5},
			{11, -80, 4},
			{11, -80, 3},
			{11, -80, 2},
			{11, -80, 1},
			{11, -80, 10},
			{11, -80, 20},
			{11, -80, 30},
			{11, -80, 40},
			{11, -80, 50},
			{11, -80, 60},
			{11, -80, 70},
			{11, -80, 80}
	};

	@Override
	public void getSignal(int cmd, int sig, int note, int velo) {
		if (cmd == 9) {
			int x = 9 - (note / 10);
			int y = note % 10;

			if (y >= 1 && y <= 8)
				onPadTouch(x - 1, y - 1, velo != 0, velo);
		}
		if (cmd == 11 && sig == -80) {
			if (91 <= note && note <= 98) {
				onFunctionkeyTouch(note - 91, velo != 0);
			}
			if (19 <= note && note <= 89 && note % 10 == 9) {
				int c = 9 - (note / 10) - 1;
				onChainTouch(c, velo != 0);
				onFunctionkeyTouch(c + 8, velo != 0);
			}
			if (1 <= note && note <= 8) {
				onChainTouch(8 - note + 16 - 8, velo != 0);
				onFunctionkeyTouch(8 - note + 16, velo != 0);
			}
			if (10 <= note && note <= 80 && note % 10 == 0) {
				onChainTouch((note / 10) - 1 + 24 - 8, velo != 0);
				onFunctionkeyTouch((note / 10) - 1 + 24, velo != 0);
			}

		} else {
			onUnknownEvent(cmd, sig, note, velo);

			if (cmd == 7 && sig == 46 && note == 0 && velo == -9)
				Log.midiDetail("PRO > Live Mode");
			else if (cmd == 23 && sig == 47 && note == 0 && velo == -9)
				Log.midiDetail("PRO > Note Mode");
			else if (cmd == 23 && sig == 47 && note == 1 && velo == -9)
				Log.midiDetail("PRO > Drum Mode");
			else if (cmd == 23 && sig == 47 && note == 2 && velo == -9)
				Log.midiDetail("PRO > Fade Mode");
			else if (cmd == 23 && sig == 47 && note == 3 && velo == -9)
				Log.midiDetail("PRO > Programmer Mode");
		}
	}

	@Override
	public void sendPadLED(int x, int y, int velo) {
		send09Signal(10 * (8 - x) + y + 1, velo);
	}

	@Override
	public void sendChainLED(int c, int velo) {
		if (0 <= c && c <= 7)
			sendFunctionkeyLED(c + 8, velo);
	}

	@Override
	public void sendFunctionkeyLED(int f, int velo) {
		if (0 <= f && f <= 31)
			sendSignal((byte) circleCode[f][0], (byte) circleCode[f][1], (byte) circleCode[f][2], (byte) velo);
	}


	@Override
	public void sendClearLED() {
		for (int i = 0; i < 8; i++)
			for (int j = 0; j < 8; j++)
				sendPadLED(i, j, 0);
		for (int i = 0; i < 32; i++)
			sendFunctionkeyLED(i, 0);
	}
}