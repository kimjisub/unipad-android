package com.kimjisub.launchpad.midi.driver;

import com.kimjisub.manager.Log;

public abstract class DriverRef {

	// ============================================================================================= OnCycleListener

	private OnCycleListener onCycleListener = null;

	public DriverRef setOnCycleListener(OnCycleListener listener) {
		onCycleListener = listener;
		return this;
	}

	public interface OnCycleListener {
		void onConnected();

		void onDisconnected();
	}

	////

	public void onConnected() {
		if (onCycleListener != null)
			onCycleListener.onConnected();
	}

	public void onDisconnected() {
		if (onCycleListener != null)
			onCycleListener.onDisconnected();
	}


	// ============================================================================================= OnGetSignalListener

	private OnGetSignalListener onGetSignalListener = null;

	public DriverRef setOnGetSignalListener(OnGetSignalListener listener) {
		onGetSignalListener = listener;
		return this;
	}

	public interface OnGetSignalListener {
		void onPadTouch(int x, int y, boolean upDown, int velo);

		void onFunctionkeyTouch(int f, boolean upDown);

		void onChainTouch(int c, boolean upDown);

		void onUnknownEvent(int cmd, int sig, int note, int velo);
	}

	////

	public abstract void getSignal(int cmd, int sig, int note, int velo);

	void onPadTouch(int x, int y, boolean upDown, int velo) {
		Log.midiDetail("onPadTouch(" + x + ", " + y + ", " + upDown + ", " + velo + ")");
		if (onGetSignalListener != null)
			onGetSignalListener.onPadTouch(x, y, upDown, velo);
	}

	void onFunctionkeyTouch(int f, boolean upDown) {
		Log.midiDetail("onFunctionkeyTouch(" + f + ", " + upDown + ")");
		if (onGetSignalListener != null)
			onGetSignalListener.onFunctionkeyTouch(f, upDown);
	}

	void onChainTouch(int c, boolean upDown) {
		Log.midiDetail("onChainTouch(" + c + ", " + upDown + ")");
		if (onGetSignalListener != null)
			onGetSignalListener.onChainTouch(c, upDown);
	}

	void onUnknownEvent(int cmd, int sig, int note, int velo) {
		Log.midiDetail("onUnknownEvent(" + cmd + ", " + sig + ", " + note + ", " + velo + ")");
		if (onGetSignalListener != null)
			onGetSignalListener.onUnknownEvent(cmd, sig, note, velo);
	}

	// ============================================================================================= OnSendSignalListener

	private OnSendSignalListener onSendSignalListener = null;

	public DriverRef setOnSendSignalListener(OnSendSignalListener listener) {
		onSendSignalListener = listener;
		return this;
	}

	public interface OnSendSignalListener {
		void onSend(final byte cmd, final byte sig, final byte note, final byte velo);
	}

	////

	void onSend(final byte cmd, final byte sig, final byte note, final byte velo) {
		if (onSendSignalListener != null)
			onSendSignalListener.onSend(cmd, sig, note, velo);
	}

	void sendSignal(int cmd, int sig, int note, int velo) {
		onSend((byte) cmd, (byte) sig, (byte) note, (byte) velo);
	}

	void send09Signal(final int note, final int velo) {
		sendSignal((byte) 9, (byte) -112, (byte) note, (byte) velo);
	}

	void send11Signal(final int note, final int velo) {
		sendSignal((byte) 11, (byte) -80, (byte) note, (byte) velo);
	}

	public abstract void sendPadLED(int x, int y, int velo);

	public abstract void sendChainLED(int c, int velo);

	public abstract void sendFunctionkeyLED(int f, int velo);

	public abstract void sendClearLED();
}
