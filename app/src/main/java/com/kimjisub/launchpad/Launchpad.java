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

import static com.kimjisub.launchpad.manage.Tools.log;
import static com.kimjisub.launchpad.manage.Tools.logRecv;
import static com.kimjisub.launchpad.manage.Tools.logSig;

/**
 * Created by rlawl ON 2016-02-19.
 * ReCreated by rlawl ON 2016-04-23.
 */


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

	static midiDevice device = midiDevice.S;
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

	private static connectListener listener = null;

	interface connectListener {
		void connect();
	}

	public static void setConnectListener(connectListener listener_) {
		listener = listener_;
	}

	public static void connect() {
		if (listener != null) listener.connect();
	}

	@SuppressLint("CutPasteId")
	void initVar(){
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

		selectComuFunction(mode);

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

	public void selectDeviceXml(View v) {
		selectDevice(Integer.parseInt((String) v.getTag()));
	}

	public void selectDevice(int num) {

		switch (VL_launchpad[num].getId()) {
			case R.id.s:
				device = midiDevice.S;
				break;
			case R.id.mk2:
				device = midiDevice.MK2;
				break;
			case R.id.pro:
				device = midiDevice.Pro;
				break;
			case R.id.piano:
				device = midiDevice.Piano;
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


	public void selectComuFunctionXml(View v) {
		selectComuFunction(Integer.parseInt((String) v.getTag()));
	}

	public void selectComuFunction(int num) {

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
						selectDevice(midiDevice.MK2.value);
						TV_info.append("prediction : MK2\n");
						break;
					case 81://pro
						selectDevice(midiDevice.Pro.value);
						TV_info.append("prediction : Pro\n");
						break;
					case 54://mk2 mini
						selectDevice(midiDevice.S.value);
						TV_info.append("prediction : mk2 mini\n");
						break;
					case 8211://LX 61 piano
						selectDevice(midiDevice.Piano.value);
						TV_info.append("prediction : LX 61 piano\n");
						break;
					case 32822://Arduino Leonardo midi
						selectDevice(midiDevice.MK2.value);
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


	public static class ReceiveTask extends AsyncTask<String, Integer, String> {
		private static getSignalListener listener = null;

		interface getSignalListener {
			void getSignal(int command, int note, int velocity);
		}

		static void setGetSignalListener(getSignalListener listener_) {
			listener = listener_;
		}

		static void getSignal(int command, int note, int velocity) {
			if (listener != null) listener.getSignal(command, note, velocity);
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
								int command = byteArray[i];
								int sig = byteArray[i + 1];
								int note = byteArray[i + 2];
								int velocity = byteArray[i + 3];

								if (device == midiDevice.S || device == midiDevice.MK2) {
									if (command == 11 && sig == -80) {
										if (108 <= note && note <= 111) {
											if (velocity != 0) {
												isShowWatermark = !isShowWatermark;
												showWatermark();
											}
										}
									}
								} else if (device == midiDevice.Pro) {

									if (command == 11 && sig == -80) {
										if (95 <= note && note <= 98) {
											if (velocity != 0) {
												isShowWatermark = !isShowWatermark;
												showWatermark();
											}
										}
									} else if (command == 7 && sig == 46 && velocity == -9) {
										isShowWatermark = !isShowWatermark;
										showWatermark();
									}
								}


								publishProgress(command, note, velocity);
								logRecv(String.format("%-7d%-7d%-7d          %-7d%-7d%-7d%-7d", command, note, velocity, byteArray[i], byteArray[i + 1], byteArray[i + 2], byteArray[i + 3]));
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

	static void showWatermark() {
		if (isShowWatermark) {
			if (device == midiDevice.S || device == midiDevice.MK2) {
				sendFuncLED(11, 108, 61);
				sendFuncLED(11, 109, 40);
				sendFuncLED(11, 110, 61);
				sendFuncLED(11, 111, 40);
			} else if (device == midiDevice.Pro) {
				sendFuncLED(11, 95, 61);
				sendFuncLED(11, 96, 40);
				sendFuncLED(11, 97, 61);
				sendFuncLED(11, 98, 40);
			}
			chainRefresh(chain);
		} else {
			if (device == midiDevice.S || device == midiDevice.MK2) {
				sendFuncLED(11, 108, 0);
				sendFuncLED(11, 109, 0);
				sendFuncLED(11, 110, 0);
				sendFuncLED(11, 111, 0);
			} else if (device == midiDevice.Pro) {
				sendFuncLED(11, 95, 0);
				sendFuncLED(11, 96, 0);
				sendFuncLED(11, 97, 0);
				sendFuncLED(11, 98, 0);
			}
			chainRefresh();
		}

	}

	static void sendBuffer(byte command, byte sig, byte note, byte velocity) {
		try {
			byte[] buffer = {command, sig, note, velocity};
			usbDeviceConnection.bulkTransfer(usbEndpoint_out, buffer, buffer.length, 1000);
		} catch (Exception ignored) {
		}
	}

	@SuppressLint("StaticFieldLeak")
	static void send(final byte command, final byte sig, final byte note, final byte velocity) {
		if (usbDeviceConnection != null) {
			if (mode == 0) {
				try {
					(new AsyncTask<String, Integer, String>() {
						@Override
						protected String doInBackground(String... params) {
							sendBuffer(command, sig, note, velocity);
							return null;
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				} catch (Exception ignore) {
					logSig("런치패드 led 에러");
				}
			} else if (mode == 1) {
				sendBuffer(command, sig, note, velocity);
			}
		}
	}


	static void sendBtnLED(final int command, final int note, final int velocity) {
		send((byte) command, (byte) -112, (byte) note, (byte) velocity);
	}

	static void sendFuncLED(final int command, final int note, final int velocity) {
		send((byte) command, (byte) -80, (byte) note, (byte) velocity);
	}


	static void btnLED(int i, int j, int velo) {
		if (i >= 0 && i <= 7 && j >= 0 && j <= 7) {
			if (device == midiDevice.S)
				sendBtnLED(9, i * 16 + j, LaunchpadColor.SCode[velo]);
			if (device == midiDevice.MK2)
				sendBtnLED(9, 10 * (8 - i) + j + 1, velo);
			if (device == midiDevice.Pro)
				sendBtnLED(9, 10 * (8 - i) + j + 1, velo);
		}
	}

	static void chainLED(int c, int velo) {
		if (device == midiDevice.S)
			sendBtnLED(9, c * 16 + 8, LaunchpadColor.SCode[velo]);
		if (device == midiDevice.MK2)
			sendBtnLED(9, 10 * (8 - c) + 9, velo);
		if (device == midiDevice.Pro)
			sendFuncLED(11, 10 * (8 - c) + 9, velo);
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

	static void chainRefresh() {
		log("chainRefresh()");

		for (int i = 0; i < 8; i++) {
			chainLED(i, 0);
		}
	}


	@SuppressLint("StaticFieldLeak")
	@Override
	protected void onDestroy() {
		super.onDestroy();
		(new AsyncTask<String, Integer, String>() {
			@Override
			protected String doInBackground(String... params) {
				isShowWatermark = true;
				if (device == midiDevice.Pro)
					isShowWatermark = !isShowWatermark;

				showWatermark();
				return null;
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
}