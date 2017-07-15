package com.kimjisub.launchpad;

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
 * Created by rlawl on 2016-02-19.
 * ReCreated by rlawl on 2016-04-23.
 */


public class Launchpad extends BaseActivity {
	static UsbManager 메니저;
	
	static UsbDevice 장치;
	static UsbInterface 인터페이스;
	static UsbEndpoint 엔드포인트_입력;
	static UsbEndpoint 엔드포인트_출력;
	static UsbDeviceConnection 연결;
	static boolean 실행중 = false;
	
	static midiDevice 런치패드기종 = midiDevice.S;
	static int 런치패드통신방법 = 0;
	
	static boolean 런치패드상태표시 = true;
	static int 체인기록 = -1;
	
	public enum midiDevice {
		S(0), MK2(1), Pro(2), Piano(3);
		
		private final int value;
		
		midiDevice(int value) {
			this.value = value;
		}
	}
	
	View[] V_목록;
	TextView[][] TV_목록;
	
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
	
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_usbmidi);
		
		V_목록 = new View[]{
			findViewById(R.id.s),
			findViewById(R.id.mk2),
			findViewById(R.id.pro),
			findViewById(R.id.piano)
		};
		
		TV_목록 = new TextView[][]{
			{(TextView) findViewById(R.id.s)},
			{(TextView) findViewById(R.id.mk2)},
			{(TextView) findViewById(R.id.pro1), (TextView) findViewById(R.id.pro2)},
			{(TextView) findViewById(R.id.piano)}
		};
		
		런치패드통신방법 = SaveSetting.LaunchpadConnectMethod.load(Launchpad.this);
		
		런치패드기종선택(V_목록[런치패드기종.value]);
		
		통신방법선택(new View[]{
			findViewById(R.id.speedFirst),
			findViewById(R.id.avoidAfterimage)
		}[런치패드통신방법]);
		
		Intent 인텐트 = getIntent();
		메니저 = (UsbManager) getSystemService(Context.USB_SERVICE);
		장치 = 인텐트.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(인텐트.getAction()))
			장치선택(장치);
		/*else {
			Iterator<UsbDevice> deviceIterator = ((UsbManager) getSystemService(Context.USB_SERVICE)).getDeviceList().values().iterator();
			if (deviceIterator.hasNext())
				장치선택(deviceIterator.next());
		}*/
		
		
		(new Handler()).postDelayed(new Runnable() {
			@Override
			public void run() {
				finish();
			}
		}, 2000);
	}
	
	public void 런치패드기종선택(View v) {
		
		
		switch (v.getId()) {
			case R.id.s:
				런치패드기종 = midiDevice.S;
				break;
			case R.id.mk2:
				런치패드기종 = midiDevice.MK2;
				break;
			case R.id.pro:
				런치패드기종 = midiDevice.Pro;
				break;
			case R.id.piano:
				런치패드기종 = midiDevice.Piano;
				break;
		}
		
		for (int i = 0; i < V_목록.length; i++) {
			if (런치패드기종.value == i) {
				V_목록[i].setBackgroundColor(getResources().getColor(R.color.text1));
				for (TextView 텍스트뷰 : TV_목록[i])
					텍스트뷰.setTextColor(getResources().getColor(R.color.dark1));
			} else {
				V_목록[i].setBackgroundColor(getResources().getColor(R.color.dark1));
				for (TextView 텍스트뷰 : TV_목록[i])
					텍스트뷰.setTextColor(getResources().getColor(R.color.text1));
			}
		}
	}
	
	public void 통신방법선택(View v) {
		
		View[] V_목록 = new View[]{
			findViewById(R.id.speedFirst),
			findViewById(R.id.avoidAfterimage)
		};
		TextView[][] TV_목록 = new TextView[][]{
			{(TextView) findViewById(R.id.speedFirst)},
			{(TextView) findViewById(R.id.avoidAfterimage)}
		};
		
		switch (v.getId()) {
			case R.id.speedFirst:
				런치패드통신방법 = 0;
				break;
			case R.id.avoidAfterimage:
				런치패드통신방법 = 1;
				break;
		}
		
		for (int i = 0; i < V_목록.length; i++) {
			if (런치패드통신방법 == i) {
				V_목록[i].setBackgroundColor(getResources().getColor(R.color.text1));
				for (TextView 텍스트뷰 : TV_목록[i])
					텍스트뷰.setTextColor(getResources().getColor(R.color.dark1));
			} else {
				V_목록[i].setBackgroundColor(getResources().getColor(R.color.dark1));
				for (TextView 텍스트뷰 : TV_목록[i])
					텍스트뷰.setTextColor(getResources().getColor(R.color.text1));
			}
		}
		
		
		SaveSetting.LaunchpadConnectMethod.save(Launchpad.this, 런치패드통신방법);
	}
	
	
	private boolean 장치선택(UsbDevice device) {
		TextView info = (TextView) findViewById(R.id.info);
		int interface_ = 0;
		
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
				info.append("ProductId : " + device.getProductId() + "\n");
				switch (device.getProductId()) {
					case 105://mk2
						런치패드기종선택(findViewById(R.id.mk2));
						info.append("prediction : MK2\n");
						break;
					case 81://pro
						런치패드기종선택(findViewById(R.id.pro));
						info.append("prediction : Pro\n");
						break;
					case 54://mk2 mini
						런치패드기종선택(findViewById(R.id.s));
						info.append("prediction : mk2 mini\n");
						break;
					case 8211://LX 61 piano
						런치패드기종선택(findViewById(R.id.piano));
						info.append("prediction : LX 61 piano\n");
						break;
					case 32822://Arduino Leonardo midi
						런치패드기종선택(findViewById(R.id.mk2));
						info.append("prediction : Arduino Leonardo midi\n");
						interface_ = 3;
						break;
					default:
						info.append("prediction : unknown\n");
						break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		for (int i = interface_; i < device.getInterfaceCount(); i++) {
			UsbInterface ui = device.getInterface(i);
			if (ui.getEndpointCount() > 0) {
				인터페이스 = ui;
				info.append("Interface : (" + (i + 1) + "/" + device.getInterfaceCount() + ")\n");
				break;
			}
		}
		for (int i = 0; i < 인터페이스.getEndpointCount(); i++) {
			UsbEndpoint ep = 인터페이스.getEndpoint(i);
			if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
				info.append("Endpoint_In : (" + (i + 1) + "/" + 인터페이스.getEndpointCount() + ")\n");
				엔드포인트_입력 = ep;
			} else if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
				info.append("Endpoint_OUT : (" + (i + 1) + "/" + 인터페이스.getEndpointCount() + ")\n");
				엔드포인트_출력 = ep;
			} else {
				info.append("Endpoint_Unknown : (" + (i + 1) + "/" + 인터페이스.getEndpointCount() + ")\n");
			}
		}
		연결 = 메니저.openDevice(device);
		if (연결 == null) {
			logSig("USB 에러 : 연결 == null");
			return false;
		}
		if (연결.claimInterface(인터페이스, true)) {
			(new 데이터수신()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			return true;
		} else {
			logSig("USB 에러 : 연결.claimInterface(인터페이스, true)");
			return false;
		}
	}
	
	
	public static class 데이터수신 extends AsyncTask<String, Integer, String> {
		private static getSignalListener listener = null;
		
		interface getSignalListener {
			void getSignal(int command, int note, int velocity);
		}
		
		public static void setGetSignalListener(getSignalListener listener_) {
			listener = listener_;
		}
		
		public static void getSignal(int command, int note, int velocity) {
			if (listener != null) listener.getSignal(command, note, velocity);
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			connect();
		}
		
		@Override
		protected String doInBackground(String... params) {
			if (!실행중) {
				실행중 = true;
				logSig("USB 시작");
				
				long 이전시간 = System.currentTimeMillis();
				int count = 0;
				byte[] byteArray = new byte[엔드포인트_입력.getMaxPacketSize()];
				while (실행중) {
					try {
						int length = 연결.bulkTransfer(엔드포인트_입력, byteArray, byteArray.length, 1000);
						if (length >= 4) {
							for (int i = 0; i < length; i += 4) {
								/*int command = byteArray[i] & 15;
								int sig = byteArray[i + 1];
								int note = (byte) (byteArray[i + 2] & 127);
								int velocity = (byte) (byteArray[i + 3] & 127);*/
								int command = byteArray[i];
								int sig = byteArray[i + 1];
								int note = byteArray[i + 2];
								int velocity = byteArray[i + 3];
								
								if (런치패드기종 == midiDevice.S || 런치패드기종 == midiDevice.MK2) {
									if (command == 11 && sig == -80) {
										if (108 <= note && note <= 111) {
											if (velocity != 0) {
												런치패드상태표시 = !런치패드상태표시;
												
												if (런치패드상태표시) {
													기능키데이터송신(11, 108, 61);
													기능키데이터송신(11, 109, 40);
													기능키데이터송신(11, 110, 61);
													기능키데이터송신(11, 111, 40);
													
													런치패드체인초기화(체인기록);
												} else {
													기능키데이터송신(11, 108, 0);
													기능키데이터송신(11, 109, 0);
													기능키데이터송신(11, 110, 0);
													기능키데이터송신(11, 111, 0);
													런치패드체인초기화();
												}
											}
										}
									}
								} else if (런치패드기종 == midiDevice.Pro) {
									if (command == 11 && sig == -80) {
										if (95 <= note && note <= 98) {
											if (velocity != 0) {
												런치패드상태표시 = !런치패드상태표시;
												
												if (런치패드상태표시) {
													기능키데이터송신(11, 95, 61);
													기능키데이터송신(11, 96, 40);
													기능키데이터송신(11, 97, 61);
													기능키데이터송신(11, 98, 40);
													
													런치패드체인초기화(체인기록);
												} else {
													기능키데이터송신(11, 95, 0);
													기능키데이터송신(11, 96, 0);
													기능키데이터송신(11, 97, 0);
													기능키데이터송신(11, 98, 0);
													런치패드체인초기화();
												}
											}
										}
									} else if (command == 7 && sig == 46 && velocity == -9) {
										런치패드상태표시 = !런치패드상태표시;
										
										if (런치패드상태표시) {
											기능키데이터송신(11, 95, 61);
											기능키데이터송신(11, 96, 40);
											기능키데이터송신(11, 97, 61);
											기능키데이터송신(11, 98, 40);
											
											런치패드체인초기화(체인기록);
										} else {
											기능키데이터송신(11, 95, 0);
											기능키데이터송신(11, 96, 0);
											기능키데이터송신(11, 97, 0);
											기능키데이터송신(11, 98, 0);
											런치패드체인초기화();
										}
									}
								}
								
								
								publishProgress(command, note, velocity);
								String 로그 = String.format("%-7d%-7d%-7d          %-7d%-7d%-7d%-7d", command, note, velocity, byteArray[i], byteArray[i + 1], byteArray[i + 2], byteArray[i + 3]);
								logRecv(로그);
							}
						} else if (length == -1) {
							long 현재시간 = System.currentTimeMillis();
							if (이전시간 != 현재시간) {
								count = 0;
								이전시간 = 현재시간;
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
			실행중 = false;
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
	
	static void 송신(byte command, byte sig, byte note, byte velocity) {
		try {
			byte[] buffer = {command, sig, note, velocity};
			연결.bulkTransfer(엔드포인트_출력, buffer, buffer.length, 1000);
		} catch (Exception ignored) {
		}
	}
	
	
	static void 데이터송신(final int command, final int note, final int velocity) {
		if (연결 != null) {
			if (런치패드통신방법 == 0) {
				try {
					(new AsyncTask<String, Integer, String>() {
						@Override
						protected String doInBackground(String... params) {
							송신((byte) command, (byte) -112, (byte) note, (byte) velocity);
							return null;
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				} catch (Exception ignore) {
					logSig("런치패드 LED 에러");
				}
			} else if (런치패드통신방법 == 1) {
				송신((byte) command, (byte) -112, (byte) note, (byte) velocity);
			}
		}
	}
	
	static void 기능키데이터송신(final int command, final int note, final int velocity) {
		
		if (연결 != null) {
			if (런치패드통신방법 == 0) {
				(new AsyncTask<String, Integer, String>() {
					@Override
					protected String doInBackground(String... params) {
						송신((byte) command, (byte) -80, (byte) note, (byte) velocity);
						return null;
					}
				}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			} else if (런치패드통신방법 == 1) {
				송신((byte) command, (byte) -80, (byte) note, (byte) velocity);
			}
		}
	}
	
	
	static void 런치패드패드LED(int i, int j, int velo) {
		if (i >= 0 && i <= 7 && j >= 0 && j <= 7) {
			if (런치패드기종 == midiDevice.S)
				데이터송신(9, i * 16 + j, LaunchpadColor.SCode[velo]);
			if (런치패드기종 == midiDevice.MK2)
				데이터송신(9, 10 * (8 - i) + j + 1, velo);
			if (런치패드기종 == midiDevice.Pro)
				데이터송신(9, 10 * (8 - i) + j + 1, velo);
		}
	}
	
	static void 런치패드체인LED(int c, int velo) {
		if (런치패드기종 == midiDevice.S)
			데이터송신(9, c * 16 + 8, LaunchpadColor.SCode[velo]);
		if (런치패드기종 == midiDevice.MK2)
			데이터송신(9, 10 * (8 - c) + 9, velo);
		if (런치패드기종 == midiDevice.Pro)
			기능키데이터송신(11, 10 * (8 - c) + 9, velo);
	}
	
	static void 런치패드체인초기화(int 체인) {
		log("런치패드체인초기화 (" + 체인 + ")");
		
		for (int i = 0; i < 8; i++) {
			if (i == 체인) {
				if (런치패드상태표시)
					런치패드체인LED(i, 119);
				else
					런치패드체인LED(i, 0);
			} else
				런치패드체인LED(i, 0);
		}
		
		체인기록 = 체인;
	}
	
	static void 런치패드체인초기화() {
		log("런치패드체인초기화 ()");
		
		for (int i = 0; i < 8; i++) {
			런치패드체인LED(i, 0);
		}
	}
	
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		(new AsyncTask<String, Integer, String>() {
			@Override
			protected String doInBackground(String... params) {
				
				if (런치패드기종 == midiDevice.S || 런치패드기종 == midiDevice.MK2) {
					if (런치패드상태표시) {
						기능키데이터송신(11, 108, 61);
						기능키데이터송신(11, 109, 40);
						기능키데이터송신(11, 110, 61);
						기능키데이터송신(11, 111, 40);
						런치패드체인초기화(체인기록);
					}
				} else if (런치패드기종 == midiDevice.Pro) {
					런치패드상태표시 = !런치패드상태표시;
					런치패드체인초기화(체인기록);
				}
				return null;
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		finishActivity(this);
	}
	
	
	

	/*void 종료() {
		try {
			if (연결 != null) {
				연결.releaseInterface(인터페이스);
				연결.close();
			}
			실행중 = false;
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), "Error at cleanup: " + e.toString(), Toast.LENGTH_SHORT).show();
		}
	}*/
}