package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import com.kimjisub.launchpad.manage.LaunchpadColor;
import com.kimjisub.launchpad.manage.SaveSetting;

import static com.kimjisub.launchpad.Launchpad.midiDevice.*;
import static com.kimjisub.launchpad.manage.Tools.log;
import static com.kimjisub.launchpad.manage.Tools.logRecv;
import static com.kimjisub.launchpad.manage.Tools.logSig;

public class Launchpad extends BaseActivity {

	TextView TV_info;
	View[] VL_launchpad;
	TextView[][] TVL_launchpad;
	View[] VL_mode;
	TextView[][] TVL_mode;


	static UsbManager usbManager;

	static UsbDevice usbDevice;
	static UsbInterface usbInterface;
	static UsbEndpoint usbEndpoint_in;
	static UsbEndpoint usbEndpoint_out;
	static UsbDeviceConnection usbDeviceConnection;
	static boolean isRun = false;

	static midiDevice device = S;
	static int mode = 0;

	static boolean isShowWatermark = true;
	static int chain = -1;

	public enum midiDevice {
		S(0), MK2(1), Pro(2), Piano(3);

		private final int value;

		midiDevice(int value) {
			this.value = value;
		}
	}

	@SuppressLint("CutPasteId")
	void initVar() {
		TV_info = findViewById(R.id.info);
		VL_launchpad = new View[]{
			findViewById(R.id.s),
			findViewById(R.id.mk2),
			findViewById(R.id.pro),
			findViewById(R.id.piano)
		};

		TVL_launchpad = new TextView[][]{
			{findViewById(R.id.s)},
			{findViewById(R.id.mk2)},
			{findViewById(R.id.pro1), findViewById(R.id.pro2)},
			{findViewById(R.id.piano)}
		};

		VL_mode = new View[]{
			findViewById(R.id.speedFirst),
			findViewById(R.id.avoidAfterimage)
		};
		TVL_mode = new TextView[][]{
			{findViewById(R.id.speedFirst)},
			{findViewById(R.id.avoidAfterimage)}
		};
	}

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_usbmidi);
		initVar();


		mode = SaveSetting.LaunchpadConnectMethod.load(Launchpad.this);

		selectDevice(device.value);

		selectMode(mode);

		Intent intent = getIntent();
		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction()))
			selectDevice(usbDevice);
		/*else {
			Iterator<UsbDevice> deviceIterator = ((UsbManager) getSystemService(Context.USB_SERVICE)).getDeviceList().values().iterator();
			if (deviceIterator.hasNext())
				selectDevice(deviceIterator.next());
		}*/


		(new Handler()).postDelayed(new Runnable() {
			@Override
			public void run() {
				finish();
			}
		}, 2000);
	}


	private boolean selectDevice(UsbDevice device) {

		int interfaceNum = 0;

		if (device == null) {
			logSig("USB 에러 : device == null");
			return false;
		} else {
			try {
				logSig("DeviceName : " + device.getDeviceName());
				logSig("DeviceClass : " + device.getDeviceClass());
				logSig("DeviceId : " + device.getDeviceId());
				logSig("DeviceProtocol : " + device.getDeviceProtocol());
				logSig("DeviceSubclass : " + device.getDeviceSubclass());
				logSig("InterfaceCount : " + device.getInterfaceCount());
				logSig("VendorId : " + device.getVendorId());
			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				logSig("ProductId : " + device.getProductId());
				TV_info.append("ProductId : " + device.getProductId() + "\n");
				switch (device.getProductId()) {
					case 105://mk2
						selectDevice(MK2.value);
						TV_info.append("prediction : MK2\n");
						break;
					case 81://pro
						selectDevice(Pro.value);
						TV_info.append("prediction : Pro\n");
						break;
					case 54://mk2 mini
						selectDevice(S.value);
						TV_info.append("prediction : mk2 mini\n");
						break;
					case 8211://LX 61 piano
						selectDevice(Piano.value);
						TV_info.append("prediction : LX 61 piano\n");
						break;
					case 32822://Arduino Leonardo midi
						selectDevice(MK2.value);
						TV_info.append("prediction : Arduino Leonardo midi\n");
						interfaceNum = 3;
						break;
					default:
						TV_info.append("prediction : unknown\n");
						break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		for (int i = interfaceNum; i < device.getInterfaceCount(); i++) {
			UsbInterface ui = device.getInterface(i);
			if (ui.getEndpointCount() > 0) {
				usbInterface = ui;
				TV_info.append("Interface : (" + (i + 1) + "/" + device.getInterfaceCount() + ")\n");
				break;
			}
		}
		for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
			UsbEndpoint ep = usbInterface.getEndpoint(i);
			if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
				TV_info.append("Endpoint_In : (" + (i + 1) + "/" + usbInterface.getEndpointCount() + ")\n");
				usbEndpoint_in = ep;
			} else if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
				TV_info.append("Endpoint_OUT : (" + (i + 1) + "/" + usbInterface.getEndpointCount() + ")\n");
				usbEndpoint_out = ep;
			} else {
				TV_info.append("Endpoint_Unknown : (" + (i + 1) + "/" + usbInterface.getEndpointCount() + ")\n");
			}
		}
		usbDeviceConnection = usbManager.openDevice(device);
		if (usbDeviceConnection == null) {
			logSig("USB 에러 : usbDeviceConnection == null");
			return false;
		}
		if (usbDeviceConnection.claimInterface(usbInterface, true)) {
			(new ReceiveTask()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			return true;
		} else {
			logSig("USB 에러 : usbDeviceConnection.claimInterface(usbInterface, true)");
			return false;
		}
	}

	// ========================================================================================= 설정 선택

	public void selectDeviceXml(View v) {
		selectDevice(Integer.parseInt((String) v.getTag()));
	}

	public void selectDevice(int num) {

		switch (VL_launchpad[num].getId()) {
			case R.id.s:
				device = S;
				break;
			case R.id.mk2:
				device = MK2;
				break;
			case R.id.pro:
				device = Pro;
				break;
			case R.id.piano:
				device = Piano;
				break;
		}

		for (int i = 0; i < VL_launchpad.length; i++) {
			if (device.value == i) {
				VL_launchpad[i].setBackgroundColor(getResources().getColor(R.color.text1));
				for (TextView textView : TVL_launchpad[i])
					textView.setTextColor(getResources().getColor(R.color.dark1));
			} else {
				VL_launchpad[i].setBackgroundColor(getResources().getColor(R.color.dark1));
				for (TextView textView : TVL_launchpad[i])
					textView.setTextColor(getResources().getColor(R.color.text1));
			}
		}
	}

	public void selectModeXml(View v) {
		selectMode(Integer.parseInt((String) v.getTag()));
	}

	public void selectMode(int num) {

		switch (VL_mode[num].getId()) {
			case R.id.speedFirst:
				mode = 0;
				break;
			case R.id.avoidAfterimage:
				mode = 1;
				break;
		}

		for (int i = 0; i < VL_mode.length; i++) {
			if (mode == i) {
				VL_mode[i].setBackgroundColor(getResources().getColor(R.color.text1));
				for (TextView textView : TVL_mode[i])
					textView.setTextColor(getResources().getColor(R.color.dark1));
			} else {
				VL_mode[i].setBackgroundColor(getResources().getColor(R.color.dark1));
				for (TextView textView : TVL_mode[i])
					textView.setTextColor(getResources().getColor(R.color.text1));
			}
		}


		SaveSetting.LaunchpadConnectMethod.save(Launchpad.this, mode);
	}

	// ========================================================================================= 리스너

	public static class ReceiveTask extends AsyncTask<String, Integer, String> {
		private static eventListener listener = null;

		interface eventListener {
			void onConnect();

			void onGetSignal(int cmd, int note, int velocity);

			void onPadTouch(int x, int y, boolean upDown);

			void onChainChange(int c);
		}

		static void setEventListener(eventListener listener_) {
			listener = listener_;
		}

		public static void connect() {
			if (listener != null) listener.onConnect();
		}

		static void getSignal(int cmd, int note, int velocity) {
			if (listener != null) {
				listener.onGetSignal(cmd, note, velocity);

				switch (Launchpad.device) {
					case S:
						if (cmd == 9 && velocity != 0) {
							int x = note / 16 + 1;
							int y = note % 16 + 1;
							if (y >= 1 && y <= 8) {
								listener.onPadTouch(x - 1, y - 1, true);
							} else if (y == 9) {
								listener.onChainChange(x - 1);
							}
						} else if (cmd == 9 && velocity == 0) {
							int x = note / 16 + 1;
							int y = note % 16 + 1;
							if (y >= 1 && y <= 8) {
								listener.onPadTouch(x - 1, y - 1, false);
							}
						} else if (cmd == 11) {
						}
						break;
					case MK2:
						if (cmd == 9 && velocity != 0) {
							int x = 9 - (note / 10);
							int y = note % 10;
							if (y >= 1 && y <= 8) {
								listener.onPadTouch(x - 1, y - 1, true);
							} else if (y == 9) {
								listener.onChainChange(x - 1);
							}
						} else if (cmd == 9 && velocity == 0) {
							int x = 9 - (note / 10);
							int y = note % 10;
							if (y >= 1 && y <= 8) {
								listener.onPadTouch(x - 1, y - 1, false);
							}
						} else if (cmd == 11) {
						}
						break;
					case Pro:
						if (cmd == 9 && velocity != 0) {
							int x = 9 - (note / 10);
							int y = note % 10;
							if (y >= 1 && y <= 8) {
								listener.onPadTouch(x - 1, y - 1, true);
							}
						} else if (cmd == 9 && velocity == 0) {
							int x = 9 - (note / 10);
							int y = note % 10;
							if (y >= 1 && y <= 8) {
								listener.onPadTouch(x - 1, y - 1, false);
							}
						} else if (cmd == 11 && velocity != 0) {
							int x = 9 - (note / 10);
							int y = note % 10;
							if (y == 9) {
								listener.onChainChange(x - 1);
							}
						}
						break;
					case Piano:
						int x;
						int y;

						if (cmd == 9 && velocity != 0) {
							if (note >= 36 && note <= 67) {
								x = (67 - note) / 4 + 1;
								y = 4 - (67 - note) % 4;
								listener.onPadTouch(x - 1, y - 1, true);
							} else if (note >= 68 && note <= 99) {
								x = (99 - note) / 4 + 1;
								y = 8 - (99 - note) % 4;
								listener.onPadTouch(x - 1, y - 1, true);
							}

						} else if (velocity == 0) {
							if (note >= 36 && note <= 67) {
								x = (67 - note) / 4 + 1;
								y = 4 - (67 - note) % 4;
								listener.onPadTouch(x - 1, y - 1, false);
							} else if (note >= 68 && note <= 99) {
								x = (99 - note) / 4 + 1;
								y = 8 - (99 - note) % 4;
								listener.onPadTouch(x - 1, y - 1, false);
							}
						}
						break;
				}
			}
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			connect();
		}

		@Override
		protected String doInBackground(String... params) {
			if (!isRun) {
				isRun = true;
				logSig("USB 시작");

				long prevTime = System.currentTimeMillis();
				int count = 0;
				byte[] byteArray = new byte[usbEndpoint_in.getMaxPacketSize()];
				while (isRun) {
					try {
						int length = usbDeviceConnection.bulkTransfer(usbEndpoint_in, byteArray, byteArray.length, 1000);
						if (length >= 4) {
							for (int i = 0; i < length; i += 4) {
								int cmd = byteArray[i];
								int sig = byteArray[i + 1];
								int note = byteArray[i + 2];
								int velocity = byteArray[i + 3];

								if (device == S || device == MK2) {
									if (cmd == 11 && sig == -80) {
										if (108 <= note && note <= 111) {
											if (velocity != 0)
												toggleWatermark();
										}
									}
								} else if (device == Pro) {
									if (cmd == 11 && sig == -80) {
										if (95 <= note && note <= 98) {
											if (velocity != 0)
												toggleWatermark();
										}
									} else if (cmd == 7 && sig == 46 && velocity == -9)
										toggleWatermark();
								}


								publishProgress(cmd, note, velocity);
								logRecv(String.format("%-7d%-7d%-7d%-7d", cmd, sig, note, velocity));
							}
						} else if (length == -1) {
							long currTime = System.currentTimeMillis();
							if (prevTime != currTime) {
								count = 0;
								prevTime = currTime;
							} else {
								count++;
								if (count > 10)
									break;
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
						break;
					}
				}

				logSig("USB 끝");
			}
			isRun = false;
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {
			getSignal(progress[0], progress[1], progress[2]);
		}

		@Override
		protected void onPostExecute(String result) {
		}
	}

	static void toggleWatermark() {
		isShowWatermark = !isShowWatermark;
		showWatermark();
	}

	static void showWatermark() {
		if (isShowWatermark) {
			if (device == S || device == MK2) {
				send11(108, 61);
				send11(109, 40);
				send11(110, 61);
				send11(111, 40);
			} else if (device == Pro) {
				send11(95, 61);
				send11(96, 40);
				send11(97, 61);
				send11(98, 40);
			}
			chainRefresh(chain);
		} else {
			if (device == S || device == MK2) {
				send11(108, 0);
				send11(109, 0);
				send11( 110, 0);
				send11(111, 0);
			} else if (device == Pro) {
				send11(95, 0);
				send11(96, 0);
				send11(97, 0);
				send11( 98, 0);
			}
			chainRefresh(chain);
		}

	}

	static void sendBuffer(byte cmd, byte sig, byte note, byte velocity) {
		try {
			byte[] buffer = {cmd, sig, note, velocity};
			usbDeviceConnection.bulkTransfer(usbEndpoint_out, buffer, buffer.length, 1000);
		} catch (Exception ignored) {
		}
	}

	@SuppressLint("StaticFieldLeak")
	static void send(final byte cmd, final byte sig, final byte note, final byte velocity) {
		if (usbDeviceConnection != null) {
			if (mode == 0) {
				try {
					(new AsyncTask<String, Integer, String>() {
						@Override
						protected String doInBackground(String... params) {
							sendBuffer(cmd, sig, note, velocity);
							return null;
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				} catch (Exception ignore) {
					logSig("런치패드 led 에러");
				}
			} else if (mode == 1) {
				sendBuffer(cmd, sig, note, velocity);
			}
		}
	}


	static void send09(final int note, final int velocity) {
		send((byte) 9, (byte) -112, (byte) note, (byte) velocity);
	}

	static void send11(final int note, final int velocity) {
		send((byte) 11, (byte) -80, (byte) note, (byte) velocity);
	}


	static void btnLED(int i, int j, int velo) {
		if (i >= 0 && i <= 7 && j >= 0 && j <= 7) {
			if (device == S)
				send09(i * 16 + j, LaunchpadColor.SCode[velo]);
			if (device == MK2)
				send09(10 * (8 - i) + j + 1, velo);
			if (device == Pro)
				send09(10 * (8 - i) + j + 1, velo);
		}
	}

	static void chainLED(int c, int velo) {
		if (0 <= c && c <= 7)
			circleBtnLED(c + 8, velo);
	}


	static void circleBtnLED(int num, int velo) {
		if (device == S && 0 <= num && num <= 15)
			send((byte) S_circleCode[num][0], (byte) S_circleCode[num][1], (byte) S_circleCode[num][2], (byte) LaunchpadColor.SCode[velo]);
		if (device == MK2 && 0 <= num && num <= 15)
			send((byte) MK2_circleCode[num][0], (byte) MK2_circleCode[num][1], (byte) MK2_circleCode[num][2], (byte) velo);
		if (device == Pro && 0 <= num && num <= 31)
			send((byte) Pro_circleCode[num][0], (byte) Pro_circleCode[num][1], (byte) Pro_circleCode[num][2], (byte) velo);
	}

	static void chainRefresh(int c) {
		log("chainRefresh(" + c + ")");

		for (int i = 0; i < 8; i++) {
			if (i == c) {
				if (isShowWatermark)
					chainLED(i, 119);
				else
					chainLED(i, 0);
			} else
				chainLED(i, 0);
		}

		chain = c;
	}


	@SuppressLint("StaticFieldLeak")
	@Override
	protected void onDestroy() {
		super.onDestroy();
		(new AsyncTask<String, Integer, String>() {
			@Override
			protected String doInBackground(String... params) {
				isShowWatermark = true;
				if (device == Pro)
					isShowWatermark = !isShowWatermark;

				showWatermark();
				return null;
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	static final int[][] S_circleCode = {
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
	static final int[][] MK2_circleCode = {
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
	static final int[][] Pro_circleCode = {
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
}