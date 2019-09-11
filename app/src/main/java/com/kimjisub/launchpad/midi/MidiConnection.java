package com.kimjisub.launchpad.midi;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;

import com.kimjisub.launchpad.activity.LaunchpadActivity;
import com.kimjisub.launchpad.midi.controller.MidiController;
import com.kimjisub.launchpad.midi.driver.DriverRef;
import com.kimjisub.launchpad.midi.driver.LaunchpadMK2;
import com.kimjisub.launchpad.midi.driver.LaunchpadPRO;
import com.kimjisub.launchpad.midi.driver.LaunchpadS;
import com.kimjisub.launchpad.midi.driver.MasterKeyboard;
import com.kimjisub.launchpad.midi.driver.MidiFighter;
import com.kimjisub.launchpad.midi.driver.Noting;
import com.kimjisub.manager.Log;

import java.util.Iterator;
import java.util.Objects;

public abstract class MidiConnection {
	private static UsbManager usbManager;
	private static UsbInterface usbInterface;
	private static UsbEndpoint usbEndpoint_in;
	private static UsbEndpoint usbEndpoint_out;
	private static UsbDeviceConnection usbDeviceConnection;

	private static DriverRef.OnCycleListener onCycleListener;
	private static DriverRef.OnGetSignalListener onGetSignalListener;
	private static DriverRef.OnSendSignalListener onSendSignalListener;

	public static DriverRef driver = new Noting();
	public static MidiController controller;


	private static boolean isRun = false;
	private static int mode = 0;

	public static void initConnection(Intent intent, UsbManager usbManager_) {
		usbManager = usbManager_;
		UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction()))
			initDevice(usbDevice);
		else {
			Iterator<UsbDevice> deviceIterator = Objects.requireNonNull(usbManager_).getDeviceList().values().iterator();
			if (deviceIterator.hasNext())
				initDevice(deviceIterator.next());
		}

		onCycleListener = new DriverRef.OnCycleListener() {
			@Override
			public void onConnected() {
				controller.onAttach();
			}

			@Override
			public void onDisconnected() {
				controller.onDetach();
			}
		};

		onSendSignalListener = (cmd, sig, note, velo) -> {
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
						//Log.midiDetail("MIDI send thread execute fail");
					}
				} else if (mode == 1)
					sendBuffer(cmd, sig, note, velo);
			}
		};

		onGetSignalListener = new DriverRef.OnGetSignalListener() {
			@Override
			public void onPadTouch(int x, int y, boolean upDown, int velo) {
				controller.onPadTouch(x, y, upDown, velo);
			}

			@Override
			public void onFunctionkeyTouch(int f, boolean upDown) {
				controller.onFunctionkeyTouch(f, upDown);
			}

			@Override
			public void onChainTouch(int c, boolean upDown) {
				controller.onChainTouch(c, upDown);
			}

			@Override
			public void onUnknownEvent(int cmd, int sig, int note, int velo) {
				controller.onUnknownEvent(cmd, sig, note, velo);
			}
		};

		setDriverListener();
	}

	private static void initDevice(UsbDevice device) {
		int interfaceNum = 0;

		if (device == null) {
			Log.midiDetail("USB 에러 : device == null");
			return;
		}

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
			onUiLog("ProductId : " + device.getProductId() + "\n");
			Class driver;
			switch (device.getProductId()) {
				case 8:
					driver = MidiFighter.class;
					onUiLog("prediction : MidiFighter\n");
					break;
				case 105:
					driver = LaunchpadMK2.class;
					onUiLog("prediction : MK2\n");
					break;
				case 81:
					driver = LaunchpadPRO.class;
					onUiLog("prediction : Pro\n");
					break;
				case 54:
					driver = LaunchpadS.class;
					onUiLog("prediction : mk2 mini\n");
					break;
				case 8211:
					driver = MasterKeyboard.class;
					onUiLog("prediction : LX 61 piano\n");
					break;
				case 32822:
					driver = LaunchpadPRO.class;
					onUiLog("prediction : Arduino Leonardo midi\n");
					interfaceNum = 3;
					break;
				default:
					driver = MasterKeyboard.class;
					onUiLog("prediction : unknown\n");
					break;
			}
			setDriver(driver);
		} catch (Exception e) {
			e.printStackTrace();
		}

		for (int i = interfaceNum; i < device.getInterfaceCount(); i++) {
			UsbInterface ui = device.getInterface(i);
			if (ui.getEndpointCount() > 0) {
				usbInterface = ui;
				onUiLog("Interface : (" + (i + 1) + "/" + device.getInterfaceCount() + ")\n");
				break;
			}
		}
		for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
			UsbEndpoint ep = usbInterface.getEndpoint(i);
			if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
				onUiLog("Endpoint_In : (" + (i + 1) + "/" + usbInterface.getEndpointCount() + ")\n");
				usbEndpoint_in = ep;
			} else if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
				onUiLog("Endpoint_OUT : (" + (i + 1) + "/" + usbInterface.getEndpointCount() + ")\n");
				usbEndpoint_out = ep;
			} else {
				onUiLog("Endpoint_Unknown : (" + (i + 1) + "/" + usbInterface.getEndpointCount() + ")\n");
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

		onConnectedListener();

		return;
	}


	public static void setMode(int mode_) {
		mode = mode_;

		onChangeMode(mode);
	}

	static void sendBuffer(byte cmd, byte sig, byte note, byte velocity) {
		try {
			byte[] buffer = {cmd, sig, note, velocity};
			usbDeviceConnection.bulkTransfer(usbEndpoint_out, buffer, buffer.length, 1000);
		} catch (Exception ignored) {
		}
	}

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
			driver.getSignal(progress[0], progress[1], progress[2], progress[3]);
		}

		@Override
		protected void onPostExecute(String result) {
			driver.onDisconnected();
		}
	}

	// Driver /////////////////////////////////////////////////////////////////////////////////////////

	public static void setDriver(Class cls) {
		if (driver != null) {
			driver.sendClearLED();
			driver.onDisconnected();
			driver = new Noting();
		}

		try {
			driver = (DriverRef) cls.newInstance();
			setDriverListener();
			if (isRun)
				driver.onConnected();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		}

		onChangeDriver(cls);
	}

	public static void setDriverListener() {
		driver.setOnCycleListener(onCycleListener);
		driver.setOnGetSignalListener(onGetSignalListener);
		driver.setOnSendSignalListener(onSendSignalListener);
	}

	// Controller /////////////////////////////////////////////////////////////////////////////////////////

	public static void setController(MidiController controller_) {
		controller = controller_;
	}

	public static void removeController(MidiController controller_) {
		if (controller != null && controller == controller_)
			controller = null;
	}

	// Listener /////////////////////////////////////////////////////////////////////////////////////////

	static Listener listener;

	public interface Listener {
		void onConnectedListener();

		void onChangeDriver(Class cls);

		void onChangeMode(int mode);

		void onUiLog(String log);
	}

	public static void setListener(Listener listener_) {
		listener = listener_;

		onChangeDriver(driver.getClass());
		onChangeMode(mode);
	}

	public static void removeListener() {
		listener = null;
	}

	private static void onConnectedListener() {
		if (listener != null)
			listener.onConnectedListener();
	}

	private static void onChangeDriver(Class cls) {
		if (listener != null)
			listener.onChangeDriver(cls);
	}

	private static void onChangeMode(int mode) {
		if (listener != null)
			listener.onChangeMode(mode);
	}

	private static void onUiLog(String log) {
		if (listener != null)
			listener.onUiLog(log);
	}
}
