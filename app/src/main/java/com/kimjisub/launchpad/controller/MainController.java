package com.kimjisub.launchpad.controller;

public class MainController extends BaseController {

	void setDriver() {
		LaunchpadActivity.setDriverListener(MainActivity.this,
				new LaunchpadDriver.DriverRef.OnConnectionEventListener() {
					@Override
					public void onConnected() {
						Log.driverCycle("MainActivity onConnected()");
						updateLP();
					}

					@Override
					public void onDisconnected() {
						Log.driverCycle("MainActivity onDisconnected()");
					}
				}, new LaunchpadDriver.DriverRef.OnGetSignalListener() {
					@Override
					public void onPadTouch(int x, int y, boolean upDown, int velo) {
						if (!((x == 3 || x == 4) && (y == 3 || y == 4))) {
							if (upDown)
								LaunchpadActivity.driver.sendPadLED(x, y, new int[]{40, 61}[(int) (Math.random() * 2)]);
							else
								LaunchpadActivity.driver.sendPadLED(x, y, 0);
						}
					}

					@Override
					public void onFunctionkeyTouch(int f, boolean upDown) {
						if (f == 0 && upDown) {
							if (havePrev()) {
								togglePlay(lastPlayIndex - 1);
								b.recyclerView.smoothScrollToPosition(lastPlayIndex);
								//b.recyclerView.smoothScrollToPosition(0, list.find(lastPlayIndex).packViewSimple.getTop() + (-Scale_Height / 2) + (list.find(lastPlayIndex).packViewSimple.getHeight() / 2));
							} else
								showSelectLPUI();
						} else if (f == 1 && upDown) {
							if (haveNext()) {
								togglePlay(lastPlayIndex + 1);
								b.recyclerView.smoothScrollToPosition(lastPlayIndex);
							} else
								showSelectLPUI();
						} else if (f == 2 && upDown) {
							if (haveNow())
								list.get(lastPlayIndex).packViewSimple.onPlayClick();
						}
					}

					@Override
					public void onChainTouch(int c, boolean upDown) {
					}

					@Override
					public void onUnknownEvent(int cmd, int sig, int note, int velo) {
						if (cmd == 7 && sig == 46 && note == 0 && velo == -9)
							updateLP();
					}
				});
	}

	void updateLP() {
		showWatermark();
		showSelectLPUI();
	}

	boolean haveNow() {
		return 0 <= lastPlayIndex && lastPlayIndex <= list.size() - 1;
	}

	boolean haveNext() {
		return lastPlayIndex < list.size() - 1;
	}

	boolean havePrev() {
		return 0 < lastPlayIndex;
	}

	void showSelectLPUI() {
		if (havePrev())
			LaunchpadActivity.driver.sendFunctionkeyLED(0, 63);
		else
			LaunchpadActivity.driver.sendFunctionkeyLED(0, 5);

		if (haveNow())
			LaunchpadActivity.driver.sendFunctionkeyLED(2, 61);
		else
			LaunchpadActivity.driver.sendFunctionkeyLED(2, 0);

		if (haveNext())
			LaunchpadActivity.driver.sendFunctionkeyLED(1, 63);
		else
			LaunchpadActivity.driver.sendFunctionkeyLED(1, 5);
	}

	void showWatermark() {
		LaunchpadActivity.driver.sendPadLED(3, 3, 61);
		LaunchpadActivity.driver.sendPadLED(3, 4, 40);
		LaunchpadActivity.driver.sendPadLED(4, 3, 40);
		LaunchpadActivity.driver.sendPadLED(4, 4, 61);
	}
}
