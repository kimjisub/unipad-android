package com.kimjisub.launchpad.activity;

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
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.databinding.ActivityUsbmidiBinding;
import com.kimjisub.launchpad.manager.PreferenceManager;
import com.kimjisub.launchpad.midi.MidiConnection;
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

public class LaunchpadActivity extends BaseActivity {

	ActivityUsbmidiBinding b;
	LinearLayout[] LL_Launchpad;
	LinearLayout[] LL_mode;

	@SuppressLint({"CutPasteId", "StaticFieldLeak"})
	void initVar() {
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
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		b = setContentViewBind(R.layout.activity_usbmidi);
		initVar();

		MidiConnection.setListener(new MidiConnection.Listener() {
			@Override
			public void onConnectedListener() {
				b.err.setVisibility(View.GONE);
			}

			@Override
			public void onChangeDriver(Class cls) {
				selectDriver(cls);
			}

			@Override
			public void onChangeMode(int mode) {
				selectMode(mode);
			}

			@Override
			public void onUiLog(String log) {
				b.info.append(log + "\n");
			}
		});

		MidiConnection.setMode(PreferenceManager.LaunchpadConnectMethod.load(LaunchpadActivity.this));

		Intent intent = getIntent();
		MidiConnection.initConnection(intent, (UsbManager) getSystemService(Context.USB_SERVICE));


		(new Handler()).postDelayed(this::finish, 2000);
	}

	// Select Driver /////////////////////////////////////////////////////////////////////////////////////////

	public void selectDriver(View v) {
		int index = Integer.parseInt((String) v.getTag());
		selectDriver(new Class[]{LaunchpadS.class, LaunchpadMK2.class, LaunchpadPRO.class, MidiFighter.class, MasterKeyboard.class}[index]);
	}


	public void selectDriver(Class cls) {
		int index = 0;
		switch (cls.getSimpleName()) {
			case "LaunchpadS":
				index = 0;
				break;
			case "LaunchpadMK2":
				index = 1;
				break;
			case "LaunchpadPRO":
				index = 2;
				break;
			case "MidiFighter":
				index = 3;
				break;
			case "MasterKeyboard":
				index = 4;
				break;
		}

		for (int i = 0; i < LL_Launchpad.length; i++) {
			if (index == i) {
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
	}


	// Select Mode /////////////////////////////////////////////////////////////////////////////////////////

	public void selectModeXml(View v) {
		selectMode(Integer.parseInt((String) v.getTag()));
	}

	public void selectMode(int mode) {
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

		PreferenceManager.LaunchpadConnectMethod.save(LaunchpadActivity.this, mode);
	}



	// Controller /////////////////////////////////////////////////////////////////////////////////////////

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