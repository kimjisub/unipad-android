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
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

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
	
	static int 런치패드기종 = 1;
	static int 런치패드통신방법 = 0;
	
	static boolean 런치패드상태표시 = true;
	static int 체인기록 = -1;
	
	
	static final int S = 0;
	static final int MK2 = 1;
	static final int Pro = 2;
	static final int Piano = 3;
	
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
		
		런치패드기종 = 정보.설정.기본런치패드.불러오기(Launchpad.this);
		런치패드통신방법 = 정보.설정.런치패드통신방법.불러오기(Launchpad.this);
		
		런치패드기종선택(V_목록[런치패드기종]);
		
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
				런치패드기종 = 0;
				break;
			case R.id.mk2:
				런치패드기종 = 1;
				break;
			case R.id.pro:
				런치패드기종 = 2;
				break;
			case R.id.piano:
				런치패드기종 = 3;
				break;
		}
		
		for (int i = 0; i < V_목록.length; i++) {
			if (런치패드기종 == i) {
				V_목록[i].setBackgroundColor(getResources().getColor(R.color.text1));
				for (TextView 텍스트뷰 : TV_목록[i])
					텍스트뷰.setTextColor(getResources().getColor(R.color.dark1));
			} else {
				V_목록[i].setBackgroundColor(getResources().getColor(R.color.dark1));
				for (TextView 텍스트뷰 : TV_목록[i])
					텍스트뷰.setTextColor(getResources().getColor(R.color.text1));
			}
		}
		
		
		정보.설정.기본런치패드.저장하기(Launchpad.this, 런치패드기종);
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
		
		
		정보.설정.런치패드통신방법.저장하기(Launchpad.this, 런치패드통신방법);
	}
	
	
	private boolean 장치선택(UsbDevice device) {
		TextView info = (TextView) findViewById(R.id.info);
		int interface_ = 0;
		
		if (device == null) {
			Log.d("com.kimjisub.sig", "USB 에러 : device == null");
			return false;
		} else {
			try {
				Log.d("com.kimjisub.sig", "DeviceName : " + device.getDeviceName());
				Log.d("com.kimjisub.sig", "DeviceClass : " + device.getDeviceClass());
				Log.d("com.kimjisub.sig", "DeviceId : " + device.getDeviceId());
				Log.d("com.kimjisub.sig", "DeviceProtocol : " + device.getDeviceProtocol());
				Log.d("com.kimjisub.sig", "DeviceSubclass : " + device.getDeviceSubclass());
				Log.d("com.kimjisub.sig", "InterfaceCount : " + device.getInterfaceCount());
				Log.d("com.kimjisub.sig", "VendorId : " + device.getVendorId());
			} catch (Exception e) {
			}
			try {
				Log.d("com.kimjisub.sig", "ProductId : " + device.getProductId());
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
			Log.d("com.kimjisub.sig", "USB 에러 : 연결 == null");
			return false;
		}
		if (연결.claimInterface(인터페이스, true)) {
			(new 데이터수신()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			return true;
		} else {
			Log.d("com.kimjisub.sig", "USB 에러 : 연결.claimInterface(인터페이스, true)");
			return false;
		}
	}
	
	
	static class 데이터수신 extends AsyncTask<String, Integer, String> {
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
				Log.d("com.kimjisub.sig", "USB 시작");
				
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
								
								if (런치패드기종 == S || 런치패드기종 == MK2) {
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
								} else if (런치패드기종 == Pro) {
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
								Log.d("com.kimjisub.sigRecv", 로그);
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
				
				Log.d("com.kimjisub.sig", "USB 끝");
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
					Log.d("com.kimjisub.sig", "런치패드 LED 에러");
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
			if (런치패드기종 == S)
				데이터송신(9, i * 16 + j, S코드[velo]);
			if (런치패드기종 == MK2)
				데이터송신(9, 10 * (8 - i) + j + 1, velo);
			if (런치패드기종 == Pro)
				데이터송신(9, 10 * (8 - i) + j + 1, velo);
		}
	}
	
	static void 런치패드체인LED(int c, int velo) {
		if (런치패드기종 == S)
			데이터송신(9, c * 16 + 8, S코드[velo]);
		if (런치패드기종 == MK2)
			데이터송신(9, 10 * (8 - c) + 9, velo);
		if (런치패드기종 == Pro)
			기능키데이터송신(11, 10 * (8 - c) + 9, velo);
	}
	
	static void 런치패드체인초기화(int 체인) {
		화면.log("런치패드체인초기화 (" + 체인 + ")");
		
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
		화면.log("런치패드체인초기화 ()");
		
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
				
				if (런치패드기종 == S || 런치패드기종 == MK2) {
					if (런치패드상태표시) {
						기능키데이터송신(11, 108, 61);
						기능키데이터송신(11, 109, 40);
						기능키데이터송신(11, 110, 61);
						기능키데이터송신(11, 111, 40);
						런치패드체인초기화(체인기록);
					}
				} else if (런치패드기종 == Pro) {
					런치패드상태표시 = !런치패드상태표시;
					런치패드체인초기화(체인기록);
				}
				return null;
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		finishActivity(this);
	}
	
	
	static final int[] 색코드 = new int[]{
		0x000000 - 0xFF000000,
		0xfafafa - 0x88000000,//1
		0xfafafa - 0x55000000,//2
		0xfafafa,//3
		0xf8bbd0,
		0xef5350,//5
		0xe57373,
		0xef9a9a,
		
		0xfff3e0,
		0xffa726,
		0xffb960,//10
		0xffcc80,
		0xffe0b2,
		0xffee58,
		0xfff59d,
		0xfff9c4,
		
		0xdcedc8,
		0x8bc34a,//17
		0xaed581,
		0xbfdf9f,
		0x5ee2b0,
		0x00ce3c,
		0x00ba43,
		0x119c3f,
		
		0x57ecc1,
		0x00e864,
		0x00e05c,
		0x00d545,
		0x7afddd,
		0x00e4c5,
		0x00e0b2,
		0x01eec6,
		
		0x49efef,
		0x00e7d8,
		0x00e5d1,
		0x01efde,
		0x6addff,
		0x00dafe,
		0x01d6ff,
		0x08acdc,
		
		0x73cefe,
		0x0d9bf7,
		0x148de4,
		0x2a77c9,
		0x8693ff,
		0x2196f3,//45
		0x4668f6,
		0x4153dc,
		
		0xb095ff,
		0x8453fd,
		0x634acd,
		0x5749c5,
		0xffb7ff,
		0xe863fb,
		0xd655ed,
		0xd14fe9,
		
		0xfc99e3,
		0xe736c2,
		0xe52fbe,
		0xe334b6,
		0xed353e,
		0xffa726,//61
		0xf4df0b,
		0x8bc34a,//63
		
		0x5cd100,//64
		0x00d29e,
		0x2388ff,
		0x3669fd,
		0x00b4d0,
		0x475cdc,
		0xfafafa - 0x22000000,//70
		0xfafafa - 0x33000000,//71
		
		0xf72737,
		0xd2ea7b,
		0xc8df10,
		0x7fe422,
		0x00c931,
		0x00d7a6,
		0x00d8fc,
		0x0b9bfc,
		
		0x585cf5,
		0xac59f0,
		0xd980dc,
		0xb8814a,
		0xff9800,
		0xabdf22,
		0x9ee154,
		0x66bb6a,//87
		
		0x3bda47,
		0x6fdeb9,
		0x27dbda,
		0x9cc8fd,
		0x79b8f7,
		0xafafef,
		0xd580eb,
		0xf74fca,
		
		0xea8a1f,
		0xdbdb08,
		0x9cd60d,
		0xf3d335,
		0xc8af41,
		0x00ca69,
		0x24d2b0,
		0x757ebe,
		
		0x5388db,
		0xe5c5a6,
		0xe93b3b,
		0xf9a2a1,
		0xed9c65,
		0xe1ca72,
		0xb8da78,
		0x98d52c,
		
		0x626cbd,
		0xcac8a0,
		0x90d4c2,
		0xceddfe,
		0xbeccf7,
		0xfafafa - 0xAA000000,//117
		0xfafafa - 0x88000000,//118
		0xfafafa - 0x55000000,//119
		
		0xfe1624,
		0xcd2724,
		0x9ccc65,//122
		0x009c1b,
		0xffff00,//124
		0xbeb212,
		0xf5d01d,//126
		0xe37829,
		
	};
	
	static final int[] S코드 = new int[]{
		0,//0
		61,
		62,
		63,
		1,
		2,
		3,
		3,
		
		21,//8
		63,
		62,
		61,
		53,
		53,
		53,
		53,
		
		53,//16
		56,
		56,
		56,
		56,
		56,
		56,
		56,
		
		56,//24
		56,
		56,
		56,
		53,
		53,
		53,
		53,
		
		53,//32
		53,
		53,
		53,
		53,
		53,
		53,
		53,
		
		53,//40
		53,
		53,
		53,
		53,
		53,
		53,
		53,
		
		53,//48
		53,
		53,
		53,
		37,
		39,
		39,
		39,
		
		37,//56
		39,
		39,
		39,
		3,
		55,
		57,
		56,
		
		56,//64
		40,
		53,
		53,
		53,
		53,
		53,
		53,
		
		3,//72
		57,
		57,
		56,
		56,
		56,
		53,
		53,
		
		53,//80
		53,
		53,
		53,
		58,
		56,
		56,
		56,
		
		56,//88
		56,
		56,
		53,
		53,
		53,
		47,
		63,
		
		59,//96
		57,
		57,
		57,
		57,
		56,
		56,
		53,
		
		53,//104
		53,
		3,
		19,
		53,
		53,
		53,
		53,
		
		53,//112
		53,
		53,
		53,
		53,
		53,
		53,
		53,
		
		3,//120
		3,
		56,
		56,
		57,
		57,
		57,
		57,
		
	};

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