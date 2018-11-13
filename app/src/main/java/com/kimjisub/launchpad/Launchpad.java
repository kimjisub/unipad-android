package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kimjisub.launchpad.manage.LaunchpadDriver;
import com.kimjisub.launchpad.manage.Log;
import com.kimjisub.launchpad.manage.SettingManager;

import java.util.Iterator;
import java.util.Objects;

import static com.kimjisub.launchpad.Launchpad.MidiDevice.MK2;
import static com.kimjisub.launchpad.Launchpad.MidiDevice.MidiFighter;
import static com.kimjisub.launchpad.Launchpad.MidiDevice.Piano;
import static com.kimjisub.launchpad.Launchpad.MidiDevice.Pro;
import static com.kimjisub.launchpad.Launchpad.MidiDevice.S;

public class Launchpad extends BaseActivity {
	
	RelativeLayout RL_err;
	TextView TV_info;
	LinearLayout[] LL_Launchpad;
	LinearLayout[] LL_mode;
	
	
	static UsbManager usbManager;
	
	static UsbDevice usbDevice;
	static UsbInterface usbInterface;
	static UsbEndpoint usbEndpoint_in;
	static UsbEndpoint usbEndpoint_out;
	static UsbDeviceConnection usbDeviceConnection;
	static boolean isRun = false;
	
	public enum MidiDevice {
		S(0), MK2(1), Pro(2), MidiFighter(3), Piano(4);
		
		private final int value;
		
		MidiDevice(int value) {
			this.value = value;
		}
	}
	
	static MidiDevice device = null;
	static int mode = 0;
	static LaunchpadDriver.DriverRef driver = new LaunchpadDriver.Nothing();
	@SuppressLint("StaticFieldLeak")
	private static Activity driverFrom = null;
	private static LaunchpadDriver.DriverRef.OnConnectionEventListener onConnectionEventListener;
	private static LaunchpadDriver.DriverRef.OnGetSignalListener onGetSignalListener;
	private static LaunchpadDriver.DriverRef.OnSendSignalListener onSendSignalListener;
	
	
	@SuppressLint({"CutPasteId", "StaticFieldLeak"})
	void initVar() {
		RL_err = findViewById(R.id.err);
		TV_info = findViewById(R.id.info);
		LL_Launchpad = new LinearLayout[]{
			findViewById(R.id.s),
			findViewById(R.id.mk2),
			findViewById(R.id.pro),
			findViewById(R.id.midifighter),
			findViewById(R.id.piano)
		};
		LL_mode = new LinearLayout[]{
			findViewById(R.id.speedFirst),
			findViewById(R.id.avoidAfterimage)
		};
		
		setDriverListener((cmd, sig, note, velo) -> {
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
						Log.midiDetail("런치패드 led 에러");
					}
				} else if (mode == 1)
					sendBuffer(cmd, sig, note, velo);
			}
		});
	}
	
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_usbmidi);
		initVar();
		
		mode = SettingManager.LaunchpadConnectMethod.load(Launchpad.this);
		
		selectDevice(device);
		selectMode(mode);
		
		Intent intent = getIntent();
		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction()))
			initDevice(usbDevice);
		else {
			Iterator<UsbDevice> deviceIterator = ((UsbManager) Objects.requireNonNull(getSystemService(Context.USB_SERVICE))).getDeviceList().values().iterator();
			if (deviceIterator.hasNext())
				initDevice(deviceIterator.next());
		}
		
		
		(new Handler()).postDelayed(this::finish, 2000);
	}
	
	
	private void initDevice(UsbDevice device) {
		
		RL_err.setVisibility(View.GONE);
		
		int interfaceNum = 0;
		
		if (device == null) {
			Log.midiDetail("USB 에러 : device == null");
			return;
		} else {
			try {
				Log.midiDetail("DeviceName : " + device.getDeviceName());
				Log.midiDetail("DeviceClass : " + device.getDeviceClass());
				Log.midiDetail("DeviceId : " + device.getDeviceId());
				Log.midiDetail("DeviceProtocol : " + device.getDeviceProtocol());
				Log.midiDetail("DeviceSubclass : " + device.getDeviceSubclass());
				Log.midiDetail("InterfaceCount : " + device.getInterfaceCount());
				Log.midiDetail("VendorId : " + device.getVendorId());
			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				Log.midiDetail("ProductId : " + device.getProductId());
				TV_info.append("ProductId : " + device.getProductId() + "\n");
				switch (device.getProductId()) {
					case 8:
						selectDevice(MidiFighter.value);
						TV_info.append("prediction : MidiFighter\n");
						break;
					case 105:
						selectDevice(MK2.value);
						TV_info.append("prediction : MK2\n");
						break;
					case 81:
						selectDevice(Pro.value);
						TV_info.append("prediction : Pro\n");
						break;
					case 54:
						selectDevice(S.value);
						TV_info.append("prediction : mk2 mini\n");
						break;
					case 8211:
						selectDevice(Piano.value);
						TV_info.append("prediction : LX 61 piano\n");
						break;
					case 32822:
						selectDevice(MK2.value);
						TV_info.append("prediction : Arduino Leonardo midi\n");
						interfaceNum = 3;
						break;
					default:
						selectDevice(Piano.value);
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
			Log.midiDetail("USB 에러 : usbDeviceConnection == null");
			return;
		}
		if (usbDeviceConnection.claimInterface(usbInterface, true)) {
			(new ReceiveTask()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		} else {
			Log.midiDetail("USB 에러 : usbDeviceConnection.claimInterface(usbInterface, true)");
		}
	}
	
	// ========================================================================================= 설정 선택
	
	public void selectDeviceXml(View v) {
		selectDevice(Integer.parseInt((String) v.getTag()));
	}
	
	public void selectDevice(MidiDevice m) {
		if (m != null)
			selectDevice(m.value);
	}
	
	public void selectDevice(int num) {
		
		switch (LL_Launchpad[num].getId()) {
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
			case R.id.midifighter:
				device = MidiFighter;
				driver = new LaunchpadDriver.MidiFighter();
				break;
			case R.id.piano:
				device = Piano;
				driver = new LaunchpadDriver.Piano();
				break;
		}
		
		for (int i = 0; i < LL_Launchpad.length; i++) {
			if (device.value == i) {
				LL_Launchpad[i].setBackgroundColor(color(R.color.gray1));
				int count = LL_Launchpad[i].getChildCount();
				for (int j = 0; j < count; j++) {
					TextView textView = (TextView) LL_Launchpad[i].getChildAt(j);
					textView.setTextColor(color(R.color.background1));
				}
			} else {
				LL_Launchpad[i].setBackgroundColor(color(R.color.background1));
				int count = LL_Launchpad[i].getChildCount();
				for (int j = 0; j < count; j++) {
					TextView textView = (TextView) LL_Launchpad[i].getChildAt(j);
					textView.setTextColor(color(R.color.gray1));
				}
			}
		}
		
		setDriverListener();
	}
	
	
	public void selectModeXml(View v) {
		selectMode(Integer.parseInt((String) v.getTag()));
	}
	
	public void selectMode(int num) {
		
		switch (LL_mode[num].getId()) {
			case R.id.speedFirst:
				mode = 0;
				break;
			case R.id.avoidAfterimage:
				mode = 1;
				break;
		}
		
		for (int i = 0; i < LL_mode.length; i++) {
			if (mode == i) {
				LL_mode[i].setBackgroundColor(color(R.color.gray1));
				int count = LL_mode[i].getChildCount();
				for (int j = 0; j < count; j++) {
					TextView textView = (TextView) LL_mode[i].getChildAt(j);
					textView.setTextColor(color(R.color.background1));
				}
			} else {
				LL_mode[i].setBackgroundColor(color(R.color.background1));
				int count = LL_mode[i].getChildCount();
				for (int j = 0; j < count; j++) {
					TextView textView = (TextView) LL_mode[i].getChildAt(j);
					textView.setTextColor(color(R.color.gray1));
				}
			}
		}
		
		SettingManager.LaunchpadConnectMethod.save(Launchpad.this, mode);
	}
	
	// ========================================================================================= ReceiveTask
	
	public static class ReceiveTask extends AsyncTask<String, Integer, String> {
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			driver.onConnected();
		}
		
		@SuppressLint("DefaultLocale")
		@Override
		protected String doInBackground(String... params) {
			if (!isRun) {
				isRun = true;
				Log.midiDetail("USB 시작");
				
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
								Log.midi(String.format("%-7d%-7d%-7d%-7d", cmd, sig, note, velocity));
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
				
				Log.midiDetail("USB 끝");
			}
			isRun = false;
			return null;
		}
		
		@Override
		protected void onProgressUpdate(Integer... progress) {
//			try {
			driver.getSignal(progress[0], progress[1], progress[2], progress[3]);
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
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
	
	
	// ========================================================================================= Driver
	
	static void setDriverListener(Activity activity, LaunchpadDriver.DriverRef.OnConnectionEventListener listener1, LaunchpadDriver.DriverRef.OnGetSignalListener listener2) {
		Log.driverCycle("1");
		if(driverFrom != null){
			Launchpad.driver.sendClearLED();
			driver.onDisconnected();
		}
		
		driverFrom = activity;
		onConnectionEventListener = listener1;
		onGetSignalListener = listener2;
		
		setDriverListener();
		driver.onConnected();
	}
	
	static void setDriverListener(LaunchpadDriver.DriverRef.OnSendSignalListener listener) {
		Log.driverCycle("2");
		onSendSignalListener = listener;
		
		setDriverListener();
	}
	
	public static void removeDriverListener(Activity activity) {
		Log.driverCycle("3");
		if (driverFrom == activity) {
			Launchpad.driver.sendClearLED();
			driver.onDisconnected();
			
			driverFrom = null;
			onConnectionEventListener = null;
			onGetSignalListener = null;
			
			setDriverListener();
		}
	}
	
	public static void setDriverListener() {
		Log.driverCycle("설정");
		driver.setOnConnectionEventListener(onConnectionEventListener);
		driver.setOnGetSignalListener(onGetSignalListener);
		driver.setOnSendSignalListener(onSendSignalListener);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		initVar();
	}
	
	
	@SuppressLint("StaticFieldLeak")
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
}