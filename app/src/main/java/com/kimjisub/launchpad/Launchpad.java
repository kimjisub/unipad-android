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

import com.kimjisub.launchpad.manage.LaunchpadDriver;
import com.kimjisub.launchpad.manage.SaveSetting;

import static com.kimjisub.launchpad.Launchpad.MidiDevice.MK2;
import static com.kimjisub.launchpad.Launchpad.MidiDevice.Piano;
import static com.kimjisub.launchpad.Launchpad.MidiDevice.Pro;
import static com.kimjisub.launchpad.Launchpad.MidiDevice.S;
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
	
	public enum MidiDevice {
		S(0), MK2(1), Pro(2), Piano(3);
		
		private final int value;
		
		MidiDevice(int value) {
			this.value = value;
		}
	}
	
	static MidiDevice device = S;
	static int mode = 0;
	public static LaunchpadDriver.DriverRef driver = new LaunchpadDriver.Nothing();
	public static LaunchpadDriver.DriverRef.OnConnectionEventListener onConnectionEventListener;
	public static LaunchpadDriver.DriverRef.OnGetSignalListener onGetSignalListener;
	public static LaunchpadDriver.DriverRef.OnSendSignalListener onSendSignalListener;
	
	
	
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
		
		onSendSignalListener = new LaunchpadDriver.DriverRef.OnSendSignalListener() {
			@SuppressLint("StaticFieldLeak")
			@Override
			public void onSend(final byte cmd, final byte sig, final byte note, final byte velo) {
				if (usbDeviceConnection != null) {
					if (mode == 0) {
						try {
							(new AsyncTask<String, Integer, String>() {
								@Override
								protected String doInBackground(String... params) {
									sendBuffer(cmd, sig, note, velo);
									return null;
								}
							}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
						} catch (Exception ignore) {
							logSig("런치패드 led 에러");
						}
					} else if (mode == 1) {
						sendBuffer(cmd, sig, note, velo);
					}
				}
			}
		};
	}
	
	public static void updateDriver() {
		logRecv("updateDriver");
		driver.setOnConnectionEventListener(onConnectionEventListener);
		driver.setOnGetSignalListener(onGetSignalListener);
		driver.setOnSendSignalListener(onSendSignalListener);
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
				driver = new LaunchpadDriver.LaunchpadS();
				break;
			case R.id.mk2:
				device = MK2;
				driver = new LaunchpadDriver.LaunchpadMK2();
				break;
			case R.id.pro:
				device = Pro;
				driver = new LaunchpadDriver.LaunchpadPRO();
				break;
			case R.id.piano:
				device = Piano;
				driver = new LaunchpadDriver.Piano();
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
	
	// ========================================================================================= ReceiveTask
	
	public static class ReceiveTask extends AsyncTask<String, Integer, String> {
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			driver.onConnected();
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
								
								publishProgress(cmd, sig, note, velocity);
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
			driver.getSignal(progress[0], progress[1], progress[2], progress[3]);
		}
		
		@Override
		protected void onPostExecute(String result) {
			driver.onDisconnected();
		}
	}
	
	
	static void sendBuffer(byte cmd, byte sig, byte note, byte velocity) {
		try {
			byte[] buffer = {cmd, sig, note, velocity};
			usbDeviceConnection.bulkTransfer(usbEndpoint_out, buffer, buffer.length, 1000);
		} catch (Exception ignored) {
		}
	}
	
	
	@Override
	protected void onResume() {
		super.onResume();
		initVar();
	}
	
	
	@SuppressLint("StaticFieldLeak")
	@Override
	protected void onDestroy() {
		super.onDestroy();
		updateDriver();
	}
}