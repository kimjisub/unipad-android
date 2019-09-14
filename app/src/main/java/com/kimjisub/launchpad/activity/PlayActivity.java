package com.kimjisub.launchpad.activity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.anjlab.android.iab.v3.TransactionDetails;
import com.kimjisub.design.Chain;
import com.kimjisub.design.Pad;
import com.kimjisub.design.manage.SyncCheckBox;
import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.databinding.ActivityPlayBinding;
import com.kimjisub.launchpad.manager.BillingManager;
import com.kimjisub.launchpad.manager.ChanelManager;
import com.kimjisub.launchpad.manager.ChanelManager.Chanel;
import com.kimjisub.launchpad.manager.Functions;
import com.kimjisub.launchpad.manager.PreferenceManager;
import com.kimjisub.launchpad.manager.ThemeResources;
import com.kimjisub.launchpad.manager.Unipack;
import com.kimjisub.launchpad.midi.MidiConnection;
import com.kimjisub.launchpad.midi.controller.MidiController;
import com.kimjisub.manager.Log;
import com.vungle.warren.LoadAdCallback;
import com.vungle.warren.PlayAdCallback;
import com.vungle.warren.Vungle;
import com.vungle.warren.error.VungleException;

import java.io.File;
import java.util.ArrayList;

import static com.kimjisub.launchpad.manager.ChanelManager.Chanel.*;
import static com.kimjisub.launchpad.manager.ChanelManager.Chanel.GUIDE;
import static com.kimjisub.launchpad.manager.Constant.VUNGLE;
import static com.kimjisub.manager.Log.log;

public class PlayActivity extends BaseActivity {
	ActivityPlayBinding b;
	CheckBox[] CB1s;
	CheckBox[] CB2s;
	SyncCheckBox SCV_feedbackLight;
	SyncCheckBox SCV_LED;
	SyncCheckBox SCV_autoPlay;
	SyncCheckBox SCV_traceLog;
	SyncCheckBox SCV_record;
	SyncCheckBox SCV_hideUI;
	SyncCheckBox SCV_watermark;
	SyncCheckBox SCV_proLightMode;

	BillingManager billingManager;

	final long DELAY = 1;

	// =============================================================================================
	ThemeResources theme;

	Unipack unipack;
	boolean unipackLoaded = false;
	boolean UILoaded = false;
	boolean enable = true;

	Pad[][] U_pads;
	Chain[] U_chains;

	LEDTask ledTask;
	AutoPlayTask autoPlayTask;

	SoundPool soundPool;
	int[][][] stopID;

	int chain = 0;
	AudioManager audioManager;
	ChanelManager chanelManager;


	// ============================================================================================= Manager
	ArrayList[][][] traceLog_table;
	int[] traceLog_nextNum;
	long rec_prevEventMS;
	String rec_log = "";
	boolean bool_toggleOptionWindow = false;

	// ============================================================================================= 특성 다른 LED 처리

	void initVar() {

		CB1s = new CheckBox[]{b.CB1FeedbackLight, b.CB1LED, b.CB1AutoPlay, b.CB1TraceLog, b.CB1Record};
		CB2s = new CheckBox[]{b.CB2FeedbackLight, b.CB2LED, b.CB2AutoPlay, b.CB2TraceLog, b.CB2Record, b.CB2HideUI, b.CB2Watermark, b.CB2ProLightMode};

		SCV_feedbackLight = new SyncCheckBox(b.CB1FeedbackLight, b.CB2FeedbackLight);
		SCV_LED = new SyncCheckBox(b.CB1LED, b.CB2LED);
		SCV_autoPlay = new SyncCheckBox(b.CB1AutoPlay, b.CB2AutoPlay);
		SCV_traceLog = new SyncCheckBox(b.CB1TraceLog, b.CB2TraceLog);
		SCV_record = new SyncCheckBox(b.CB1Record, b.CB2Record);
		SCV_hideUI = new SyncCheckBox(b.CB2HideUI);
		SCV_watermark = new SyncCheckBox(b.CB2Watermark);
		SCV_proLightMode = new SyncCheckBox(b.CB2ProLightMode);

		SCV_watermark.forceSetChecked(true);

		/*if (!BillingManager.isShowAds())
			AV_adview.setVisibility(View.GONE);*/

		billingManager = new BillingManager(PlayActivity.this, new BillingManager.BillingEventListener() {
			@Override
			public void onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details) {

			}

			@Override
			public void onPurchaseHistoryRestored() {

			}

			@Override
			public void onBillingError(int errorCode, @Nullable Throwable error) {

			}

			@Override
			public void onBillingInitialized() {

			}

			@Override
			public void onRefresh() {
				setProMode(billingManager.isUnlockProTools());
			}
		});
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		b = setContentViewBind(R.layout.activity_play);
		initVar();

		String path = getIntent().getStringExtra("path");
		File file = new File(path);

		try {
			log("[01] Start Load Unipack " + path);
			unipack = new Unipack(file, true);

			log("[02] Check ErrorDetail");
			if (unipack.ErrorDetail != null) {
				new AlertDialog.Builder(PlayActivity.this)
						.setTitle(unipack.CriticalError ? lang(R.string.error) : lang(R.string.warning))
						.setMessage(unipack.ErrorDetail)
						.setPositiveButton(unipack.CriticalError ? lang(R.string.quit) : lang(R.string.accept), unipack.CriticalError ? (dialogInterface, i) -> finish() : null)
						.setCancelable(false)
						.show();
			}

			log("[03] Init Vars");
			U_pads = new Pad[unipack.buttonX][unipack.buttonY];
			U_chains = new Chain[32];
			chanelManager = new ChanelManager(unipack.buttonX, unipack.buttonY);

			log("[04] Start LEDTask (isKeyLED = " + unipack.isKeyLED + ")");
			if (unipack.isKeyLED) {
				ledTask = new LEDTask();
				ledTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}

			log("[05] Set Button Layout (squareButton = " + unipack.squareButton + ")");
			if (unipack.squareButton) {
				if (!unipack.isKeyLED) {
					SCV_LED.setVisibility(View.GONE);
					SCV_LED.setLocked(true);
				}

				if (!unipack.isAutoPlay) {
					SCV_autoPlay.setVisibility(View.GONE);
					SCV_autoPlay.setLocked(true);
				}
			} else {
				b.getRoot().setPadding(0, 0, 0, 0);

				SCV_feedbackLight.setVisibility(View.GONE);
				SCV_LED.setVisibility(View.GONE);
				SCV_autoPlay.setVisibility(View.GONE);

				SCV_traceLog.setVisibility(View.GONE);
				SCV_record.setVisibility(View.GONE);

				SCV_feedbackLight.setLocked(true);
				SCV_LED.setLocked(true);
				SCV_autoPlay.setLocked(true);

				SCV_traceLog.setLocked(true);
				SCV_record.setLocked(true);
			}

			log("[06] Set CheckBox Checked");
			if (unipack.isKeyLED) {
				SCV_feedbackLight.setChecked(false);
				SCV_LED.setChecked(true);
			} else
				SCV_feedbackLight.setChecked(true);


			(new AsyncTask<String, String, String>() {

				ProgressDialog progressDialog;

				@Override
				protected void onPreExecute() {
					log("[07] onPreExecute");

					progressDialog = new ProgressDialog(PlayActivity.this);
					progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
					progressDialog.setTitle(lang(R.string.loading));
					progressDialog.setMessage(lang(R.string.wait_a_sec));
					progressDialog.setCancelable(false);

					int soundNum = 0;
					for (int i = 0; i < unipack.chain; i++)
						for (int j = 0; j < unipack.buttonX; j++)
							for (int k = 0; k < unipack.buttonY; k++)
								if (unipack.soundTable[i][j][k] != null)
									soundNum += unipack.soundTable[i][j][k].size();

					soundPool = new SoundPool(soundNum, AudioManager.STREAM_MUSIC, 0);
					stopID = new int[unipack.chain][unipack.buttonX][unipack.buttonY];

					progressDialog.setMax(soundNum);
					progressDialog.show();
					super.onPreExecute();
				}

				@Override
				protected String doInBackground(String... params) {
					log("[08] doInBackground");

					try {

						for (int i = 0; i < unipack.chain; i++) {
							for (int j = 0; j < unipack.buttonX; j++) {
								for (int k = 0; k < unipack.buttonY; k++) {
									ArrayList arrayList = unipack.soundTable[i][j][k];
									if (arrayList != null) {
										for (int l = 0; l < arrayList.size(); l++) {
											Unipack.Sound e = unipack.soundTable[i][j][k].get(l);
											e.id = soundPool.load(e.file.getPath(), 1);
											publishProgress();
										}
									}
								}
							}
						}
						unipackLoaded = true;
					} catch (Exception e) {
						Log.err("[08] doInBackground");
						e.printStackTrace();
					}

					return null;
				}

				@Override
				protected void onProgressUpdate(String... progress) {
					progressDialog.incrementProgressBy(1);
				}

				@Override
				protected void onPostExecute(String result) {
					log("[09] onPostExecute");
					if (unipackLoaded) {
						try {
							if (progressDialog != null && progressDialog.isShowing())
								progressDialog.dismiss();
						} catch (Exception e) {
							e.printStackTrace();
						}
						try {
							if (skin_init(0))
								showUI();
						} catch (ArithmeticException | NullPointerException e) {
							e.printStackTrace();
						}
					} else {
						showToast(R.string.outOfCPU);
						finish();
					}
					super.onPostExecute(result);
				}
			}).execute();


		} catch (OutOfMemoryError e) {
			e.printStackTrace();
			showToast(R.string.outOfMemory);
			finish();
		} catch (Exception e) {
			e.printStackTrace();
			showToast(R.string.exceptionOccurred);
			finish();
		}
	}

	boolean skin_init(int num) {
		log("[10] skin_init (" + num + ")");
		String packageName = PreferenceManager.SelectedTheme.load(PlayActivity.this);
		if (num >= 2) {
			try {
				theme = new ThemeResources(PlayActivity.this, true);
			} catch (Exception ignore) {
			}
			return true;
		}
		try {
			theme = new ThemeResources(PlayActivity.this, packageName, true);
			return true;
		} catch (OutOfMemoryError e) {
			e.printStackTrace();
			requestRestart(this);
			showToast(lang(R.string.skinMemoryErr) + "\n" + packageName);
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			showToast(lang(R.string.skinErr) + "\n" + packageName);
			PreferenceManager.SelectedTheme.save(PlayActivity.this, getPackageName());
			return skin_init(num + 1);
		}
	}

	// ============================================================================================= LEDTask

	@SuppressLint("ClickableViewAccessibility")
	void showUI() {
		log("[11] showUI");
		int buttonSizeX;
		int buttonSizeY;

		if (unipack.squareButton) {
			buttonSizeX = buttonSizeY = Math.min(Scale_PaddingHeight / unipack.buttonX, Scale_PaddingWidth / unipack.buttonY);

		} else {
			buttonSizeX = Scale_Width / unipack.buttonY;
			buttonSizeY = Scale_Height / unipack.buttonX;
		}

		b.purchase.setOnCheckedChangeListener((compoundButton, b) -> {
			compoundButton.setChecked(false);
			startActivity(new Intent(PlayActivity.this, SettingActivity.class));
		});

		SCV_feedbackLight.setOnCheckedChange((isChecked) -> {
			padInit();
			refreshWatermark();
		});
		SCV_LED.setOnCheckedChange((isChecked) -> {
			if (unipack.isKeyLED) {
				if (!isChecked)
					LEDInit();
			}
			refreshWatermark();
		});
		SCV_autoPlay.setOnCheckedChange((isChecked) -> {
			if (isChecked) {
				autoPlayTask = new AutoPlayTask();
				try {
					autoPlayTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				} catch (Exception e) {
					log("autoplay thread execute fail");
					SCV_autoPlay.setChecked(false);
					e.printStackTrace();
				}
			} else {
				autoPlayTask.loop = false;
				padInit();
				LEDInit();
				autoPlay_removeGuide();
				b.autoPlayControlView.setVisibility(View.GONE);
			}
			refreshWatermark();
		});
		SCV_traceLog.setOnLongClick(() -> {
			traceLog_init();
			showToast(R.string.traceLogClear);
			refreshWatermark();
		});
		SCV_record.setOnCheckedChange((isChecked) -> {
			if (SCV_record.isChecked()) {
				rec_prevEventMS = System.currentTimeMillis();
				rec_log = "c " + (chain + 1);
			} else {
				Functions.INSTANCE.putClipboard(this, rec_log);
				showToast(R.string.copied);
				rec_log = "";
			}
			refreshWatermark();
		});
		SCV_hideUI.setOnCheckedChange(isChecked -> {
			b.optionView.setVisibility(isChecked ? View.GONE : View.VISIBLE);
			refreshWatermark();
		});
		SCV_watermark.setOnCheckedChange(isChecked -> {
			refreshWatermark();
			refreshWatermark();
		});
		SCV_proLightMode.setOnCheckedChange(isChecked -> {
			proLightMode(isChecked);
			refreshWatermark();
		});
		b.prev.setOnClickListener(v -> autoPlay_prev());
		b.play.setOnClickListener(v -> {
			if (autoPlayTask.isPlaying)
				autoPlay_stop();
			else
				autoPlay_play();
		});
		b.next.setOnClickListener(v -> autoPlay_after());
		b.optionBlur.setOnClickListener(v -> {
			if (bool_toggleOptionWindow) toggleOptionWindow(false);
		});
		b.quit.setOnClickListener(v -> finish());

		b.pads.removeAllViews();
		b.chainsRight.removeAllViews();
		b.chainsLeft.removeAllViews();


		for (int i = 0; i < unipack.buttonX; i++) {
			LinearLayout row = new LinearLayout(this);
			row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

			for (int j = 0; j < unipack.buttonY; j++) {
				final Pad pad = new Pad(this);
				pad.setLayoutParams(new LinearLayout.LayoutParams(buttonSizeX, buttonSizeY));


				final int finalI = i;
				final int finalJ = j;
				pad.setOnTouchListener((v, event) -> {
					switch (event.getAction()) {
						case MotionEvent.ACTION_DOWN:
							padTouch(finalI, finalJ, true);
							break;
						case MotionEvent.ACTION_UP:
							padTouch(finalI, finalJ, false);
							break;
					}
					return false;
				});
				U_pads[i][j] = pad;
				row.addView(pad);
			}
			b.pads.addView(row);
		}

		for (int i = 0; i < 32; i++) {
			final int finalI = i;

			U_chains[i] = new Chain(this);
			U_chains[i].setLayoutParams(new RelativeLayout.LayoutParams(buttonSizeX, buttonSizeY));

			if (0 <= i && i <= 7) {
				U_chains[i].setOnClickListener(v -> chainChange(finalI));
				b.chainsRight.addView(U_chains[i]);
			}
			if (16 <= i && i <= 23) {
				U_chains[i].setOnClickListener(v -> chainChange(finalI));
				b.chainsLeft.addView(U_chains[i], 0);
			}
		}

		traceLog_init();
		chainChange(chain);
		proLightMode(SCV_proLightMode.isChecked());

		skin_set();

		UILoaded = true;
		MidiConnection.INSTANCE.setController(midiController);
	}

	void skin_set() {
		log("[12] skin_set");
		b.background.setImageDrawable(theme.playbg);
		if (theme.custom_logo != null)
			b.customLogo.setImageDrawable(theme.custom_logo);
		else
			b.customLogo.setImageDrawable(null);


		for (int i = 0; i < unipack.buttonX; i++)
			for (int j = 0; j < unipack.buttonY; j++) {
				U_pads[i][j].setBackgroundImageDrawable(theme.btn);
				U_pads[i][j].setTraceLogTextColor(theme.trace_log);
			}

		if (unipack.buttonX < 16 && unipack.buttonY < 16) {
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++)
					U_pads[i][j].setPhantomImageDrawable(theme.phantom);

			if (unipack.buttonX % 2 == 0 && unipack.buttonY % 2 == 0 && unipack.squareButton && theme.phantom_ != null) {
				int x = unipack.buttonX / 2 - 1;
				int y = unipack.buttonY / 2 - 1;

				U_pads[x][y].setPhantomImageDrawable(theme.phantom_);

				U_pads[x + 1][y].setPhantomImageDrawable(theme.phantom_);
				U_pads[x + 1][y].setPhantomRotation(270);

				U_pads[x][y + 1].setPhantomImageDrawable(theme.phantom_);
				U_pads[x][y + 1].setPhantomRotation(90);

				U_pads[x + 1][y + 1].setPhantomImageDrawable(theme.phantom_);
				U_pads[x + 1][y + 1].setPhantomRotation(180);
			}
		}

		for (int i = 0; i < 32; i++) {
			if (theme.isChainLED) {
				U_chains[i].setBackgroundImageDrawable(theme.btn);
				U_chains[i].setPhantomImageDrawable(theme.chainled);
			} else {
				U_chains[i].setPhantomImageDrawable(theme.chain);
				U_chains[i].setLedVisibility(View.GONE);
			}
		}

		chainBtnsRefresh();

		b.prev.setBackground(theme.xml_prev);
		b.play.setBackground(theme.xml_play);
		b.next.setBackground(theme.xml_next);


		for (CheckBox cb1 : CB1s) {
			cb1.setTextColor(theme.checkbox);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
				cb1.setButtonTintList(ColorStateList.valueOf(theme.checkbox));
		}
		for (CheckBox cb2 : CB2s) {
			cb2.setTextColor(theme.option_window_checkbox);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
				cb2.setButtonTintList(ColorStateList.valueOf(theme.option_window_checkbox));
		}
		//RL_option_window.setBackgroundColor(theme.option_window);
		//IB_option_quit.setBackgroundColor(theme.option_window_btn);
		//IB_option_quit.setTextColor(theme.option_window_btn_text);

	}

	// ============================================================================================= AutoPlayTask

	void setLED(int x, int y) {
		if (enable) {
			setLEDUI(x, y);
			setLEDLaunchpad(x, y);
		}
	}

	void setLEDUI(int x, int y) {
		ChanelManager.Item Item = chanelManager.get(x, y);

		if (x != -1) {
			if (Item != null) {
				switch (Item.getChanel()) {
					case GUIDE:
						U_pads[x][y].setLedBackgroundColor(Item.getColor());
						break;
					case PRESSED:
						U_pads[x][y].setLedBackground(theme.btn_);
						break;
					case LED:
						U_pads[x][y].setLedBackgroundColor(Item.getColor());
						break;
				}
			} else
				U_pads[x][y].setLedBackgroundColor(0);
		} else {
			int c = y - 8;
			if (0 <= c && c < 32)
				if (theme.isChainLED) {
					if (Item != null) {
						switch (Item.getChanel()) {
							case GUIDE:
								U_chains[c].setLedBackgroundColor(Item.getColor());
								break;
							case PRESSED:
								U_chains[c].setLedBackgroundColor(Item.getColor());
								break;
							case LED:
								U_chains[c].setLedBackgroundColor(Item.getColor());
								break;
						}
					} else
						U_chains[c].setLedBackgroundColor(0);
				} else {
					if (Item != null) {
						switch (Item.getChanel()) {
							case GUIDE:
								U_chains[c].setBackgroundImageDrawable(theme.chain__);
								break;
							case PRESSED:
								U_chains[c].setBackgroundImageDrawable(theme.chain_);
								break;
							case LED:
								U_chains[c].setBackgroundImageDrawable(theme.chain);
								break;
						}
					} else
						U_chains[c].setBackgroundImageDrawable(theme.chain);
				}

		}
	}

	void setLEDLaunchpad(int x, int y) {
		ChanelManager.Item Item = chanelManager.get(x, y);

		if (x != -1) {
			if (Item != null)
				MidiConnection.INSTANCE.getDriver().sendPadLED(x, y, Item.getCode());
			else
				MidiConnection.INSTANCE.getDriver().sendPadLED(x, y, 0);
		} else {
			if (Item != null)
				MidiConnection.INSTANCE.getDriver().sendFunctionkeyLED(y, Item.getCode());
			else
				MidiConnection.INSTANCE.getDriver().sendFunctionkeyLED(y, 0);

		}

	}

	void LEDInit() {
		log("LEDInit");
		if (unipack.isKeyLED) {
			try {
				for (int i = 0; i < unipack.buttonX; i++) {
					for (int j = 0; j < unipack.buttonY; j++) {
						if (ledTask.isEventExist(i, j))
							ledTask.eventShutdown(i, j);

						chanelManager.remove(i, j, LED);
						setLED(i, j);
					}
				}

				for (int i = 0; i < 36; i++) {
					if (ledTask.isEventExist(-1, i))
						ledTask.eventShutdown(-1, i);

					chanelManager.remove(-1, i, LED);
					setLED(-1, i);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	void autoPlay_play() {
		log("autoPlay_play");
		padInit();
		LEDInit();
		autoPlayTask.isPlaying = true;

		b.play.setBackground(theme.xml_pause);

		if (unipack.isKeyLED) {
			SCV_LED.setChecked(true);
			SCV_feedbackLight.setChecked(false);
		} else {
			SCV_feedbackLight.setChecked(true);
		}
		autoPlayTask.beforeStartPlaying = true;
	}

	void autoPlay_stop() {
		log("autoPlay_stop");
		autoPlayTask.isPlaying = false;

		padInit();
		LEDInit();

		b.play.setBackground(theme.xml_play);


		autoPlayTask.achieve = -1;

		SCV_feedbackLight.setChecked(false);
		SCV_LED.setChecked(false);
	}

	void autoPlay_prev() {
		log("autoPlay_prev");
		padInit();
		LEDInit();
		int progress = autoPlayTask.progress - 40;
		if (progress < 0) progress = 0;
		autoPlayTask.progress = progress;
		if (!autoPlayTask.isPlaying) {
			log("!isPlaying");
			autoPlayTask.achieve = -1;
			autoPlayTask.check();
		}
		b.autoPlayProgressBar.setProgress(autoPlayTask.progress);
	}

	void autoPlay_after() {
		log("autoPlay_after");
		padInit();
		LEDInit();
		autoPlayTask.progress += 40;
		autoPlayTask.achieve = -1;
		if (!autoPlayTask.isPlaying) {
			log("!isPlaying");
			autoPlayTask.achieve = -1;
			autoPlayTask.check();
		}
		b.autoPlayProgressBar.setProgress(autoPlayTask.progress);
	}

	void autoPlay_guidePad(int x, int y, boolean onOff) {
		//log("autoPlay_guidePad (" + buttonX + ", " + buttonY + ", " + onOff + ")");
		if (onOff) {
			chanelManager.add(x, y, GUIDE, -1, 17);
			setLED(x, y);
		} else {
			chanelManager.remove(x, y, GUIDE);
			setLED(x, y);
		}
	}

	// ============================================================================================= pad, chain Event

	void autoPlay_guideChain(int c, boolean onOff) {
		log("autoPlay_guideChain (" + c + ", " + onOff + ")");
		if (onOff) {
			chanelManager.add(-1, 8 + c, GUIDE, -1, 17);
			setLED(-1, 8 + c);
		} else {
			chanelManager.remove(-1, 8 + c, GUIDE);
			setLED(-1, 8 + c);
			chainBtnsRefresh();
		}
	}

	void autoPlay_removeGuide() {
		log("autoPlay_removeGuide");
		try {
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++) {
					chanelManager.remove(i, j, GUIDE);
					setLED(i, j);
				}
			for (int i = 0; i < 32; i++) {
				chanelManager.remove(-1, i, GUIDE);
				setLED(-1, i);
			}
			chainBtnsRefresh();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void autoPlay_checkGuide(int x, int y) {
		//log("autoPlay_checkGuide (" + buttonX + ", " + buttonY + ")");
		if (autoPlayTask != null && autoPlayTask.loop && !autoPlayTask.isPlaying) {

			ArrayList<Unipack.AutoPlay> guideItems = autoPlayTask.guideItems;
			if (guideItems != null) {
				for (Unipack.AutoPlay autoPlay : guideItems) {
					int func = autoPlay.func;
					int currChain = autoPlay.currChain;
					int x_ = autoPlay.x;
					int y_ = autoPlay.y;
					//int c = autoPlayTable.c;
					//int d = autoPlayTable.d;

					if (func == Unipack.AutoPlay.ON && x == x_ && y == y_ && currChain == chain) {
						autoPlayTask.achieve++;
						break;
					}
				}
			}
		}
	}

	void padTouch(int x, int y, boolean upDown) {
		//log("padTouch (" + buttonX + ", " + buttonY + ", " + upDown + ")");
		try {
			if (upDown) {
				soundPool.stop(stopID[chain][x][y]);
				Unipack.Sound e = unipack.Sound_get(chain, x, y);
				stopID[chain][x][y] = soundPool.play(e.id, 1.0F, 1.0F, 0, e.loop, 1.0F);

				unipack.Sound_push(chain, x, y);

				if (SCV_record.isChecked()) {
					long currTime = System.currentTimeMillis();
					rec_addLog("d " + (currTime - rec_prevEventMS));
					rec_addLog("t " + (x + 1) + " " + (y + 1));
					rec_prevEventMS = currTime;
				}
				if (SCV_traceLog.isChecked())
					traceLog_log(x, y);

				if (SCV_feedbackLight.isChecked()) {
					chanelManager.add(x, y, PRESSED, -1, 3);
					setLED(x, y);
				}

				if (SCV_LED.isChecked())
					ledTask.addEvent(x, y);

				autoPlay_checkGuide(x, y);


				if (e.wormhole != -1)
					new Handler().postDelayed(() -> chainChange(e.wormhole), 100);
			} else {
				if (unipack.Sound_get(chain, x, y).loop == -1)
					soundPool.stop(stopID[chain][x][y]);

				chanelManager.remove(x, y, PRESSED);
				setLED(x, y);

				if (SCV_LED.isChecked()) {
					LEDTask.LEDEvent e = ledTask.searchEvent(x, y);
					if (e != null && e.loop == 0)
						ledTask.eventShutdown(x, y);
				}
			}
		} catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
			e.printStackTrace();
		}
	}

	// ============================================================================================= Launchpad Connection

	void padInit() {
		log("padInit");
		for (int i = 0; i < unipack.buttonX; i++)
			for (int j = 0; j < unipack.buttonY; j++)
				padTouch(i, j, false);
	}

	void chainChange(int num) {
		//log("chainChange (" + num + ")");
		try {
			if (num >= 0 && num < unipack.chain) {
				chain = num;
				chainBtnsRefresh();
			}

			// 다중매핑 초기화
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++) {
					unipack.Sound_push(chain, i, j, 0);
					unipack.LED_push(chain, i, j, 0);
				}


			// 녹음 chain 추가
			if (SCV_record.isChecked()) {
				long currTime = System.currentTimeMillis();
				rec_addLog("d " + (currTime - rec_prevEventMS));
				rec_addLog("chain " + (chain + 1));
				rec_prevEventMS = currTime;
			}

			// 순서기록 표시
			traceLog_show();
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}
	}

	// ============================================================================================= Trace Log

	void chainBtnsRefresh() {
		log("chainBtnsRefresh");

		try {
			// chain
			for (int i = 0; i < 8; i++) {
				if (i == chain)
					chanelManager.add(-1, 8 + i, Chanel.CHAIN, -1, 3);
				else
					chanelManager.remove(-1, 8 + i, Chanel.CHAIN);
				setLEDUI(-1, 8 + i);
				setLEDLaunchpad(-1, 8 + i);
			}

			// volume
			float maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
			float currVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
			float currPercent = currVolume / maxVolume;
			int currLevel = Math.round(currPercent * 7) + 1;
			if (currLevel == 1)
				currLevel = 0;

			for (int i = 0; i < 8; i++) {
				if (8 - i <= currLevel)
					chanelManager.add(-1, i + 8, Chanel.UI, -1, 40);
				else
					chanelManager.remove(-1, i + 8, Chanel.UI);
				setLED(-1, i + 8);
			}
		} catch (Exception e) {
		}
	}

	MidiController midiController = new MidiController() {
		@Override
		public void onAttach() {
			updateLP();
		}

		@Override
		public void onDetach() {

		}

		@Override
		public void onPadTouch(int x, int y, boolean upDown, int velo) {
			if (!bool_toggleOptionWindow) {
				padTouch(x, y, upDown);
			}
		}

		@Override
		public void onFunctionkeyTouch(int f, boolean upDown) {
			if (upDown) {
				if (!bool_toggleOptionWindow) {
					switch (f) {
						case 0:
							SCV_feedbackLight.toggleChecked();
							break;
						case 1:
							SCV_LED.toggleChecked();
							break;
						case 2:
							SCV_autoPlay.toggleChecked();
							break;
						case 3:
							toggleOptionWindow();
							break;
						case 4:
						case 5:
						case 6:
						case 7:
							SCV_watermark.toggleChecked();
							break;
					}
				} else {
					if (0 <= f && f <= 7)
						switch (f) {
							case 0:
								SCV_feedbackLight.toggleChecked();
								break;
							case 1:
								SCV_LED.toggleChecked();
								break;
							case 2:
								SCV_autoPlay.toggleChecked();
								break;
							case 3:
								toggleOptionWindow();
								break;
							case 4:
								SCV_hideUI.toggleChecked();
								break;
							case 5:
								SCV_watermark.toggleChecked();
								break;
							case 6:
								SCV_proLightMode.toggleChecked();
								break;
							case 7:
								finish();
								break;
						}
					else if (8 <= f && f <= 15) {
						float maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
						int currLevel = 8 - (f - 8) - 1;
						float currPercent = (float) currLevel / (float) 7;
						int currVolume = (int) (maxVolume * currPercent);

						audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currVolume, 0);
					}
				}
			}
		}

		@Override
		public void onChainTouch(int c, boolean upDown) {
			if (!bool_toggleOptionWindow) {
				if (upDown && unipack.chain > c)
					chainChange(c);
			}
		}

		@Override
		public void onUnknownEvent(int cmd, int sig, int note, int velo) {
			if (cmd == 7 && sig == 46 && note == 0 && velo == -9)
				updateLP();
		}
	};

	void updateLP() {
		chainChange(chain);
		refreshWatermark();
	}

	// TraceLog /////////////////////////////////////////////////////////////////////////////////////////

	void traceLog_show() {
		//log("traceLog_show");
		for (int i = 0; i < unipack.buttonX; i++) {
			for (int j = 0; j < unipack.buttonY; j++) {
				U_pads[i][j].setTraceLogText("");
				for (int k = 0; k < traceLog_table[chain][i][j].size(); k++)
					U_pads[i][j].appendTraceLog(traceLog_table[chain][i][j].get(k) + " ");
			}
		}
	}

	void traceLog_log(int x, int y) {
		//log("traceLog_log (" + buttonX + ", " + buttonY + ")");
		traceLog_table[chain][x][y].add(traceLog_nextNum[chain]++);
		U_pads[x][y].setTraceLogText("");
		for (int i = 0; i < traceLog_table[chain][x][y].size(); i++)
			U_pads[x][y].appendTraceLog(traceLog_table[chain][x][y].get(i) + " ");
	}

	void traceLog_init() {
		log("traceLog_init");
		traceLog_table = new ArrayList[unipack.chain][unipack.buttonX][unipack.buttonY];
		traceLog_nextNum = new int[unipack.chain];

		for (int i = 0; i < unipack.chain; i++) {
			for (int j = 0; j < unipack.buttonX; j++)
				for (int k = 0; k < unipack.buttonY; k++)
					if (traceLog_table[i][j][k] == null)
						traceLog_table[i][j][k] = new ArrayList<>();
					else
						traceLog_table[i][j][k].clear();
			traceLog_nextNum[i] = 1;
		}
		try {
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++)
					U_pads[i][j].setTraceLogText("");
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	void rec_addLog(String msg) {
		rec_log += "\n" + msg;
	}

	void setProMode(boolean bool) {
		b.purchase.setVisibility(bool ? View.GONE : View.VISIBLE);
		b.proTools.setAlpha(bool ? 1 : 0.3f);
		SCV_hideUI.setLocked(!bool);
		SCV_watermark.setLocked(!bool);
		SCV_proLightMode.setLocked(!bool);
	}


	// ============================================================================================= setProMode

	void refreshWatermark() {
		log("refreshWatermark");
		int[] topBar = new int[8];

		boolean UI;
		boolean UI_UNIPAD;
		boolean CHAIN;

		if (!bool_toggleOptionWindow) {
			if (SCV_watermark.isChecked()) {
				UI = false;
				UI_UNIPAD = true;
				CHAIN = true;
			} else {
				UI = false;
				UI_UNIPAD = false;
				CHAIN = false;
			}
		} else {
			if (!SCV_hideUI.isChecked()) {
				UI = true;
				UI_UNIPAD = false;
				CHAIN = false;
			} else {
				UI = false;
				UI_UNIPAD = false;
				CHAIN = false;
			}
		}


		chanelManager.setCirIgnore(Chanel.UI, !UI);
		chanelManager.setCirIgnore(Chanel.UI_UNIPAD, !UI_UNIPAD);
		chanelManager.setCirIgnore(Chanel.CHAIN, !CHAIN);

		if (!bool_toggleOptionWindow) {
			topBar[0] = 0;
			topBar[1] = 0;
			topBar[2] = 0;
			topBar[3] = 0;
			topBar[4] = 61;
			topBar[5] = 40;
			topBar[6] = 61;
			topBar[7] = 40;

			for (int i = 0; i < 8; i++) {
				if (topBar[i] != 0)
					chanelManager.add(-1, i, Chanel.UI_UNIPAD, -1, topBar[i]);
				else
					chanelManager.remove(-1, i, Chanel.UI_UNIPAD);
				setLED(-1, i);
			}
		} else {
			topBar[0] = SCV_feedbackLight.isLocked() ? 0 : (SCV_feedbackLight.isChecked() ? 3 : 1);
			topBar[1] = SCV_LED.isLocked() ? 0 : (SCV_LED.isChecked() ? 52 : 55);
			topBar[2] = SCV_autoPlay.isLocked() ? 0 : (SCV_autoPlay.isChecked() ? 17 : 19);
			topBar[3] = 0;
			topBar[4] = SCV_hideUI.isLocked() ? 0 : (SCV_hideUI.isChecked() ? 3 : 1);
			topBar[5] = SCV_watermark.isLocked() ? 0 : (SCV_watermark.isChecked() ? 61 : 11);
			topBar[6] = SCV_proLightMode.isLocked() ? 0 : (SCV_proLightMode.isChecked() ? 40 : 43);
			topBar[7] = 5;

			for (int i = 0; i < 8; i++) {
				if (topBar[i] != 0)
					chanelManager.add(-1, i, Chanel.UI, -1, topBar[i]);
				else
					chanelManager.remove(-1, i, Chanel.UI);
				setLED(-1, i);
			}
		}

		chainBtnsRefresh();
	}

	// ============================================================================================= Watermark

	void proLightMode(boolean bool) {
		if (bool) {
			for (Chain chain : U_chains) {
				chain.setVisibility(View.VISIBLE);
				//chain.setLedVisibility(View.VISIBLE);
			}
		} else {
			if (unipack.chain > 1) {
				for (int i = 0; i < 32; i++) {
					Chain chain = U_chains[i];

					if (i < unipack.chain)
						chain.setVisibility(View.VISIBLE);
					else
						chain.setVisibility(View.INVISIBLE);

					//chain.setLedVisibility(View.INVISIBLE);
				}
			} else {
				for (Chain chain : U_chains)
					chain.setVisibility(View.INVISIBLE);
			}

		}


		chanelManager.setCirIgnore(Chanel.LED, !bool);
		chainBtnsRefresh();
	}

	// ============================================================================================= Pro Mode

	void toggleOptionWindow() {
		toggleOptionWindow(!bool_toggleOptionWindow);
	}

	// ============================================================================================= Option Window

	void toggleOptionWindow(boolean bool) {
		bool_toggleOptionWindow = bool;
		refreshWatermark();
		if (bool) {
			Animation a = new Animation() {
				@Override
				protected void applyTransformation(float interpolatedTime, Transformation t) {
					b.optionBlur.setAlpha(interpolatedTime);
					b.optionWindow.setAlpha(interpolatedTime);
				}
			};
			a.setDuration(200);
			a.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
					b.optionBlur.setVisibility(View.VISIBLE);
					b.optionWindow.setVisibility(View.VISIBLE);
				}

				@Override
				public void onAnimationEnd(Animation animation) {
				}

				@Override
				public void onAnimationRepeat(Animation animation) {

				}
			});
			b.optionBlur.startAnimation(a);
		} else {
			Animation a = new Animation() {
				@Override
				protected void applyTransformation(float interpolatedTime, Transformation t) {
					b.optionBlur.setAlpha(1 - interpolatedTime);
					b.optionWindow.setAlpha(1 - interpolatedTime);
				}
			};
			a.setDuration(500);
			a.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {

				}

				@Override
				public void onAnimationEnd(Animation animation) {
					b.optionBlur.setVisibility(View.INVISIBLE);
					b.optionWindow.setVisibility(View.INVISIBLE);
				}

				@Override
				public void onAnimationRepeat(Animation animation) {

				}
			});
			b.optionBlur.startAnimation(a);
		}
	}


	// Activity /////////////////////////////////////////////////////////////////////////////////////////

	@Override
	public void onBackPressed() {
		toggleOptionWindow();
	}

	@Override
	public void onResume() {
		super.onResume();
		//initVar();
		audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, new ContentObserver(new Handler()) {
			@Override
			public boolean deliverSelfNotifications() {
				return super.deliverSelfNotifications();
			}

			@Override
			public void onChange(boolean selfChange) {
				log("changed volume 1");
				chainBtnsRefresh();
				super.onChange(selfChange);
			}

			@Override
			public void onChange(boolean selfChange, Uri uri) {
				log("changed volume 2");
				chainBtnsRefresh();
				super.onChange(selfChange, uri);
			}
		});

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		if (UILoaded)
			MidiConnection.INSTANCE.setController(midiController);

		if (Scale_PaddingHeight == 0) {
			log("padding 크기값들이 잘못되었습니다.");
			requestRestart(PlayActivity.this);
		}

		if (billingManager.isShowAds()) {
			/*AdRequest adRequest = new AdRequest.Builder().build();
			AV_adview.loadAd(adRequest);*/

			if (Vungle.isInitialized()) {
				Vungle.loadAd(VUNGLE.PLAY_END, new LoadAdCallback() {
					@Override
					public void onAdLoad(String placementReferenceId) {
						Log.vungle("PLAY_END loadAd : placementReferenceId == " + placementReferenceId);
					}

					@Override
					public void onError(String placementReferenceId, Throwable throwable) {
						Log.vungle("PLAY_END loadAd : getLocalizedMessage() == " + throwable.getLocalizedMessage());
						try {
							VungleException ex = (VungleException) throwable;

							if (ex.getExceptionCode() == VungleException.VUNGLE_NOT_INTIALIZED)
								initVungle();
						} catch (ClassCastException cex) {
							Log.vungle(cex.getMessage());
						}
					}
				});
			} else
				Log.vungle("PLAY_END loadAd : isInitialized() == false");
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (autoPlayTask != null)
			autoPlayTask.loop = false;
		if (ledTask != null)
			ledTask.isPlaying = false;
		if (soundPool != null) {
			for (int i = 0; i < unipack.chain; i++) {
				for (int j = 0; j < unipack.buttonX; j++) {
					for (int k = 0; k < unipack.buttonY; k++) {
						ArrayList arrayList = unipack.soundTable[i][j][k];
						if (arrayList != null) {
							for (int l = 0; l < arrayList.size(); l++) {
								if (unipack.soundTable[i][j][k].get(l) != null) {
									try {
										soundPool.unload(unipack.soundTable[i][j][k].get(l).id);
									} catch (Exception e) {
										e.printStackTrace();
									}
								}
							}
						}
					}
				}
			}
			soundPool.release();
			soundPool = null;
		}
		//LEDInit();
		//padInit();

		enable = false;
		MidiConnection.INSTANCE.removeController(midiController);

		if (unipackLoaded) {
			if (billingManager.isShowAds()) {
				if (checkAdsCooltime()) {
					updateAdsCooltime();

					if (Math.random() * 10 > 3)
						showAdmob();
					else {
						if (Vungle.canPlayAd(VUNGLE.PLAY_END)) {
							Vungle.playAd(VUNGLE.PLAY_END, null, new PlayAdCallback() {
								@Override
								public void onAdStart(String placementReferenceId) {
									Log.vungle("PLAY_END playAd : onAdStart()");
								}

								@Override
								public void onAdEnd(String placementReferenceId, boolean completed, boolean isCTAClicked) {
									Log.vungle("PLAY_END onAdEnd : onAdEnd()");
								}

								@Override
								public void onError(String placementReferenceId, Throwable throwable) {
									Log.vungle("PLAY_END onError : onError() == " + throwable.getLocalizedMessage());
									try {
										VungleException ex = (VungleException) throwable;

										if (ex.getExceptionCode() == VungleException.VUNGLE_NOT_INTIALIZED)
											initVungle();
									} catch (ClassCastException cex) {
										Log.vungle(cex.getMessage());
									}
								}
							});
						}
					}
				}
			}
		}
	}

	// Thread /////////////////////////////////////////////////////////////////////////////////////////

	@SuppressLint("StaticFieldLeak")
	class LEDTask extends AsyncTask<String, Integer, String> {

		boolean isPlaying = true;
		LEDTask.LED[][] btnLED;
		LEDTask.LED[] cirLED;
		ArrayList<LEDTask.LEDEvent> LEDEvents;

		LEDTask() {
			btnLED = new LED[unipack.buttonX][unipack.buttonY];
			cirLED = new LED[36];
			LEDEvents = new ArrayList<>();
		}

		LEDTask.LEDEvent searchEvent(int x, int y) {
			LEDTask.LEDEvent res = null;
			try {
				for (int i = 0; i < LEDEvents.size(); i++) {
					LEDTask.LEDEvent e = LEDEvents.get(i);
					if (e.equal(x, y)) {
						res = e;
						break;
					}
				}
			} catch (IndexOutOfBoundsException ee) {
				ee.printStackTrace();
			}
			return res;
		}

		boolean isEventExist(int x, int y) {
			return searchEvent(x, y) != null;
		}

		void addEvent(int x, int y) {
			if (ledTask.isEventExist(x, y)) {
				LEDTask.LEDEvent e = ledTask.searchEvent(x, y);
				e.isShutdown = true;
			}
			LEDTask.LEDEvent e = new LEDEvent(x, y);
			if (e.noError)
				LEDEvents.add(e);
		}

		void eventShutdown(int x, int y) {
			searchEvent(x, y).isShutdown = true;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}

		@Override
		protected String doInBackground(String... params) {

			while (isPlaying) {
				long currTime = System.currentTimeMillis();

				for (int i = 0; i < LEDEvents.size(); i++) {
					LEDTask.LEDEvent e = LEDEvents.get(i);

					if (e != null && e.isPlaying && !e.isShutdown) {
						if (e.delay == -1)
							e.delay = currTime;

						for (; ; e.index++) {

							if (e.index >= e.syntaxs.size()) {
								e.loopProgress++;
								e.index = 0;
							}

							if (e.loop != 0 && e.loop <= e.loopProgress) {
								e.isPlaying = false;
								break;
							}

							if (e.delay <= currTime) {


								Unipack.LED.Syntax syntax = e.syntaxs.get(e.index);

								int func = syntax.func;
								int x = syntax.x;
								int y = syntax.y;
								int color = syntax.color;
								int velo = syntax.velo;
								int delay = syntax.delay;


								try {
									switch (func) {
										case Unipack.LED.Syntax.ON:
											if (x != -1) {
												chanelManager.add(x, y, Chanel.LED, color, velo);
												setLEDLaunchpad(x, y);
												publishProgress(x, y);
												btnLED[x][y] = new LED(e.buttonX, e.buttonY, x, y, color, velo);
											} else {
												chanelManager.add(x, y, Chanel.LED, color, velo);
												setLEDLaunchpad(x, y);
												publishProgress(x, y);
												cirLED[y] = new LED(e.buttonX, e.buttonY, x, y, color, velo);
											}

											break;
										case Unipack.LED.Syntax.OFF:

											if (x != -1) {
												if (btnLED[x][y] != null && btnLED[x][y].equal(e.buttonX, e.buttonY)) {
													chanelManager.remove(x, y, Chanel.LED);
													setLEDLaunchpad(x, y);
													publishProgress(x, y);
													btnLED[x][y] = null;
												}
											} else {
												if (cirLED[y] != null && cirLED[y].equal(e.buttonX, e.buttonY)) {
													chanelManager.remove(x, y, Chanel.LED);
													setLEDLaunchpad(x, y);
													publishProgress(x, y);
													cirLED[y] = null;
												}
											}


											break;
										case Unipack.LED.Syntax.DELAY:
											e.delay += delay;
											break;
									}
								} catch (ArrayIndexOutOfBoundsException ee) {
									ee.printStackTrace();
								}

							} else
								break;
						}


					} else if (e == null) {
						LEDEvents.remove(i);
						log("led 오류 e == null");
					} else if (e.isShutdown) {
						for (int x = 0; x < unipack.buttonX; x++) {
							for (int y = 0; y < unipack.buttonY; y++) {
								if (btnLED[x][y] != null && btnLED[x][y].equal(e.buttonX, e.buttonY)) {
									chanelManager.remove(x, y, Chanel.LED);
									setLEDLaunchpad(x, y);
									publishProgress(x, y);
									btnLED[x][y] = null;
								}
							}
						}

						for (int y = 0; y < cirLED.length; y++) {
							if (cirLED[y] != null && cirLED[y].equal(e.buttonX, e.buttonY)) {
								chanelManager.remove(-1, y, Chanel.LED);
								setLEDLaunchpad(-1, y);
								publishProgress(-1, y);
								cirLED[y] = null;
							}
						}
						LEDEvents.remove(i);
					} else if (!e.isPlaying) {
						LEDEvents.remove(i);
					}
				}

				try {
					Thread.sleep(DELAY);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}


			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... p) {
			try {
				setLEDUI(p[0], p[1]);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void onPostExecute(String result) {
		}

		class LED {
			int buttonX;
			int buttonY;
			int x;
			int y;
			int color;
			int velo;

			public LED(int buttonX, int buttonY, int x, int y, int color, int velo) {
				this.buttonX = buttonX;
				this.buttonY = buttonY;
				this.x = x;
				this.y = y;
				this.color = color;
				this.velo = velo;
			}

			boolean equal(int buttonX, int buttonY) {
				return (this.buttonX == buttonX) && (this.buttonY == buttonY);
			}
		}

		class LEDEvent {
			boolean noError = false;

			ArrayList<Unipack.LED.Syntax> syntaxs;
			int index = 0;
			long delay = -1;

			int buttonX;
			int buttonY;
			boolean isPlaying = true;
			boolean isShutdown = false;
			int loop;
			int loopProgress = 0;

			LEDEvent(int buttonX, int buttonY) {
				this.buttonX = buttonX;
				this.buttonY = buttonY;

				Unipack.LED e = unipack.LED_get(chain, buttonX, buttonY);
				unipack.LED_push(chain, buttonX, buttonY);
				if (e != null) {
					syntaxs = e.syntaxs;
					loop = e.loop;
					noError = true;
				}
			}

			boolean equal(int buttonX, int buttonY) {
				return (this.buttonX == buttonX) && (this.buttonY == buttonY);
			}
		}
	}

	@SuppressLint("StaticFieldLeak")
	class AutoPlayTask extends AsyncTask<String, Integer, String> {

		boolean loop = true;
		boolean isPlaying = false;
		int progress = 0;

		boolean beforeStartPlaying = true;
		boolean afterMatchChain = false;
		int beforeChain = -1;


		ArrayList<Unipack.AutoPlay> guideItems = new ArrayList<>();
		int achieve = 0;

		AutoPlayTask() {
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			if (unipack.squareButton)
				b.autoPlayControlView.setVisibility(View.VISIBLE);
			b.autoPlayProgressBar.setMax(unipack.autoPlayTable.size());
			b.autoPlayProgressBar.setProgress(0);
			autoPlay_play();
		}

		@Override
		protected String doInBackground(String... params) {

			long delay = 0;
			long startTime = System.currentTimeMillis();


			while (progress < unipack.autoPlayTable.size() && loop) {
				long currTime = System.currentTimeMillis();

				if (isPlaying) {
					if (beforeStartPlaying) {
						beforeStartPlaying = false;
						log("beforeStartPlaying");
						publishProgress(8);
					}

					if (delay <= currTime - startTime) {
						Unipack.AutoPlay e = unipack.autoPlayTable.get(progress);


						int func = e.func;
						int currChain = e.currChain;
						int num = e.num;
						int x = e.x;
						int y = e.y;
						int c = e.c;
						int d = e.d;

						switch (func) {
							case Unipack.AutoPlay.ON:
								if (chain != currChain)
									publishProgress(3, currChain);
								unipack.Sound_push(currChain, x, y, num);
								unipack.LED_push(currChain, x, y, num);
								publishProgress(1, x, y);

								break;
							case Unipack.AutoPlay.OFF:
								if (chain != currChain)
									publishProgress(3, currChain);
								publishProgress(2, x, y);
								break;
							case Unipack.AutoPlay.CHAIN:
								publishProgress(3, c);
								break;
							case Unipack.AutoPlay.DELAY:
								delay += d;
								break;
						}
						progress++;
					}

				} else {
					beforeStartPlaying = true;
					if (delay <= currTime - startTime)
						delay = currTime - startTime;

					if (guideItems.size() != 0 && guideItems.get(0).currChain != chain) { // 현재 체인이 다음 연습 체인이 아닌 경우
						if (beforeChain == -1 || beforeChain != chain) {
							beforeChain = chain;
							afterMatchChain = true;
							publishProgress(8);
							publishProgress(5, guideItems.get(0).currChain);
						}
					} else {
						if (afterMatchChain) {
							afterMatchChain = false;
							publishProgress(9);
							for (int i = 0; i < unipack.chain; i++)
								publishProgress(7, i);
							beforeChain = -1;

							for (int i = 0; i < guideItems.size(); i++) {
								Unipack.AutoPlay e = guideItems.get(i);

								switch (e.func) {
									case Unipack.AutoPlay.ON:
										publishProgress(4, e.x, e.y);
										break;
								}
							}
						}


						check();
					}
				}


				try {
					Thread.sleep(DELAY);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			return null;
		}

		void check() {
			if (achieve >= guideItems.size() || achieve == -1) {
				achieve = 0;


				for (int i = 0; i < guideItems.size(); i++) {
					Unipack.AutoPlay e = guideItems.get(i);

					switch (e.func) {
						case Unipack.AutoPlay.ON:
							publishProgress(6, e.x, e.y);
							break;
					}
				}

				guideItems.clear();

				int addedDelay = 0;
				boolean complete = false;

				for (; progress < unipack.autoPlayTable.size() && (addedDelay <= 20 || !complete); progress++) {
					Unipack.AutoPlay e = unipack.autoPlayTable.get(progress);

					switch (e.func) {
						case Unipack.AutoPlay.ON:
							unipack.Sound_push(e.currChain, e.x, e.y, e.num);
							unipack.LED_push(e.currChain, e.x, e.y, e.num);
							publishProgress(4, e.x, e.y);
							complete = true;
							guideItems.add(e);
							log(e.currChain + " " + e.x + " " + e.y);
							break;
						case Unipack.AutoPlay.DELAY:
							if (complete)
								addedDelay += e.d;
							break;
					}
				}
			}
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {
			if (progress[0] == 1) {
				padTouch(progress[1], progress[2], true);
			} else if (progress[0] == 2) {
				padTouch(progress[1], progress[2], false);
			} else if (progress[0] == 3) {
				chainChange(progress[1]);
			} else if (progress[0] == 4) {
				autoPlay_guidePad(progress[1], progress[2], true);
			} else if (progress[0] == 5) {
				autoPlay_guideChain(progress[1], true);
			} else if (progress[0] == 6) {
				autoPlay_guidePad(progress[1], progress[2], false);
			} else if (progress[0] == 7) {
				autoPlay_guideChain(progress[1], false);
			} else if (progress[0] == 8) {
				autoPlay_removeGuide();
			} else if (progress[0] == 9) {
				chainBtnsRefresh();
			}
			b.autoPlayProgressBar.setProgress(this.progress);
		}

		@Override
		protected void onPostExecute(String result) {
			SCV_autoPlay.setChecked(false);
			if (unipack.isKeyLED) {
				SCV_LED.setChecked(true);
				SCV_feedbackLight.setChecked(false);
			} else {
				SCV_feedbackLight.setChecked(true);
			}
			b.autoPlayControlView.setVisibility(View.GONE);
		}

	}
}