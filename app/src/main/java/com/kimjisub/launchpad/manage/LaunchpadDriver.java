package com.kimjisub.launchpad.manage;

public class LaunchpadDriver {
	
	public static abstract class DriverRef {
		
		// ================================================================================= OnGetSignalListener
		
		private OnGetSignalListener onGetSignalListener = null;
		
		public interface OnGetSignalListener {
			void onPadTouch(int x, int y, boolean upDown, int velo);
			
			void onFunctionkeyTouch(int f, boolean upDown);
			
			void onChainTouch(int c, boolean upDown);
			
			void onUnknownEvent(int cmd, int sig, int note, int velo);
		}
		
		public DriverRef setOnGetSignalListener(OnGetSignalListener listener) {
			onGetSignalListener = listener;
			return this;
		}
		
		void onPadTouch(int x, int y, boolean upDown, int velo) {
			if (onGetSignalListener != null)
				onGetSignalListener.onPadTouch(x, y, upDown, velo);
		}
		
		void onFunctionkeyTouch(int f, boolean upDown) {
			if (onGetSignalListener != null)
				onGetSignalListener.onFunctionkeyTouch(f, upDown);
		}
		
		void onChainTouch(int c, boolean upDown) {
			if (onGetSignalListener != null)
				onGetSignalListener.onChainTouch(c, upDown);
		}
		
		void onUnknownEvent(int cmd, int sig, int note, int velo) {
			if (onGetSignalListener != null)
				onGetSignalListener.onUnknownEvent(cmd, sig, note, velo);
		}
		
		public abstract void getSignal(int cmd, int sig, int note, int velo);
		
		
		// ================================================================================= OnSendSignalListener
		
		private OnSendSignalListener onSendSignalListener = null;
		
		public interface OnSendSignalListener {
			void onSend(int cmd, int sig, int note, int velo);
		}
		
		public DriverRef setOnSendSignalListener(OnSendSignalListener listener) {
			onSendSignalListener = listener;
			return this;
		}
		
		void onSend(int cmd, int sig, int note, int velo) {
			if (onSendSignalListener != null)
				onSendSignalListener.onSend(cmd, sig, note, velo);
		}
		
		void onSend09(final int note, final int velo) {
			onSend((byte) 9, (byte) -112, (byte) note, (byte) velo);
		}
		
		void onSend11(final int note, final int velo) {
			onSend((byte) 11, (byte) -80, (byte) note, (byte) velo);
		}
		
		public void sendSignal(int cmd, int sig, int note, int velo) {
			onSend(cmd, sig, note, velo);
		}
		
		public abstract void sendPadLED(int x, int y, int velo);
		
		public abstract void sendChainLED(int c, int velo);
		
		public abstract void sendFunctionkeyLED(int f, int velo);
		
	}
	
	public static class LaunchpadS extends DriverRef {
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
				else if (y == 9)
					onChainTouch(x - 1, velo != 0);
			} else if (cmd == 11) {
			
			}
		}
		
		@Override
		public void sendPadLED(int x, int y, int velo) {
			onSend09(x * 16 + y, LaunchpadColor.SCode[velo]);
		}
		
		@Override
		public void sendFunctionkeyLED(int f, int velo) {
			if (0 <= f && f <= 15)
				onSend((byte) circleCode[f][0], (byte) circleCode[f][1], (byte) circleCode[f][2], (byte) LaunchpadColor.SCode[velo]);
		}
		
		@Override
		public void sendChainLED(int c, int velo) {
			if (0 <= c && c <= 7)
				sendFunctionkeyLED(c + 8, velo);
		}
	}
	
	public static class LaunchpadMK2 extends DriverRef {
		static final int[][] circleCode = {
			{11, -80, 104},
			{11, -80, 105},
			{11, -80, 106},
			{11, -80, 107},
			{11, -80, 108},
			{11, -80, 109},
			{11, -80, 110},
			{11, -80, 111},
			{9, -112, 89},
			{9, -112, 79},
			{9, -112, 69},
			{9, -112, 59},
			{9, -112, 49},
			{9, -112, 39},
			{9, -112, 29},
			{9, -112, 19},
		};
		
		@Override
		public void getSignal(int cmd, int sig, int note, int velo) {
			if (cmd == 9) {
				int x = 9 - (note / 10);
				int y = note % 10;
				
				if (y >= 1 && y <= 8)
					onPadTouch(x - 1, y - 1, velo != 0, velo);
				else if (y == 9)
					onChainTouch(x - 1, velo != 0);
			} else if (cmd == 11) {
			}
		}
		
		@Override
		public void sendPadLED(int x, int y, int velo) {
			onSend09(10 * (8 - x) + y + 1, velo);
		}
		
		@Override
		public void sendFunctionkeyLED(int f, int velo) {
			if (0 <= f && f <= 15)
				onSend((byte) circleCode[f][0], (byte) circleCode[f][1], (byte) circleCode[f][2], (byte) velo);
		}
		
		@Override
		public void sendChainLED(int c, int velo) {
			if (0 <= c && c <= 7)
				sendFunctionkeyLED(c + 8, velo);
		}
	}
	
	public static class LaunchpadPRO extends DriverRef {
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
			} else if (cmd == 11) {
				int x = 9 - (note / 10);
				int y = note % 10;
				
				if (y == 9)
					onChainTouch(x - 1, velo != 0);
			}
		}
		
		@Override
		public void sendPadLED(int x, int y, int velo) {
			onSend09(10 * (8 - x) + y + 1, velo);
		}
		
		@Override
		public void sendFunctionkeyLED(int f, int velo) {
			if (0 <= f && f <= 31)
				onSend((byte) circleCode[f][0], (byte) circleCode[f][1], (byte) circleCode[f][2], (byte) velo);
		}
		
		@Override
		public void sendChainLED(int c, int velo) {
			if (0 <= c && c <= 7)
				sendFunctionkeyLED(c + 8, velo);
		}
	}
	public static class Piano extends DriverRef {
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
		public void sendFunctionkeyLED(int f, int velo) {
		}
		
		@Override
		public void sendChainLED(int c, int velo) {
		}
	}
}