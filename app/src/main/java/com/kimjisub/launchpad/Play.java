package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.github.mmin18.widget.RealtimeBlurView;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.kimjisub.design.Chain;
import com.kimjisub.design.Pad;
import com.kimjisub.launchpad.manage.LaunchpadColor;
import com.kimjisub.launchpad.manage.LaunchpadDriver;
import com.kimjisub.launchpad.manage.Log;
import com.kimjisub.launchpad.manage.SettingManager;
import com.kimjisub.launchpad.manage.ThemePack;
import com.kimjisub.launchpad.manage.Unipack;
import com.kimjisub.launchpad.playManager.ColorManager;
import com.kimjisub.unipad.designkit.SyncCheckBox;
import com.vungle.warren.LoadAdCallback;
import com.vungle.warren.PlayAdCallback;
import com.vungle.warren.Vungle;
import com.vungle.warren.error.VungleException;

import java.util.ArrayList;

import static com.kimjisub.launchpad.manage.Constant.VUNGLE;


public class Play extends BaseActivity {
	
	RelativeLayout RL_rootView;
	ImageView IV_background;
	ImageView IV_custom_logo;
	LinearLayout LL_pads;
	LinearLayout LL_chainsRight;
	LinearLayout LL_chainsLeft;
	LinearLayout LL_autoPlayControlView;
	ProgressBar PB_autoPlayProgressBar;
	ImageView IV_prev;
	ImageView IV_play;
	ImageView IV_next;
	RelativeLayout RL_option_view;
	RealtimeBlurView RBV_option_blur;
	RelativeLayout RL_option_window;
	Button BTN_option_quit;
	AdView AV_adview;
	
	CheckBox CB1_feedbackLight;
	CheckBox CB1_LED;
	CheckBox CB1_autoPlay;
	CheckBox CB1_traceLog;
	CheckBox CB1_record;
	CheckBox[] CB1s;
	
	CheckBox CB2_feedbackLight;
	CheckBox CB2_LED;
	CheckBox CB2_autoPlay;
	CheckBox CB2_traceLog;
	CheckBox CB2_record;
	CheckBox CB2_hideUI;
	CheckBox CB2_watermark;
	CheckBox CB2_proLightMode;
	CheckBox[] CB2s;
	
	SyncCheckBox SCV_feedbackLight;
	SyncCheckBox SCV_LED;
	SyncCheckBox SCV_autoPlay;
	SyncCheckBox SCV_traceLog;
	SyncCheckBox SCV_record;
	SyncCheckBox SCV_hideUI;
	SyncCheckBox SCV_watermark;
	SyncCheckBox SCV_proLightMode;
	
	
	void initVar() {
		RL_rootView = findViewById(R.id.rootView);
		IV_custom_logo = findViewById(R.id.custom_logo);
		IV_background = findViewById(R.id.background);
		LL_pads = findViewById(R.id.pads);
		LL_chainsRight = findViewById(R.id.chainsRight);
		LL_chainsLeft = findViewById(R.id.chainsLeft);
		LL_autoPlayControlView = findViewById(R.id.autoPlayControlView);
		PB_autoPlayProgressBar = findViewById(R.id.autoPlayProgressBar);
		IV_prev = findViewById(R.id.prev);
		IV_play = findViewById(R.id.play);
		IV_next = findViewById(R.id.next);
		RL_option_view = findViewById(R.id.option_view);
		RBV_option_blur = findViewById(R.id.option_blur);
		RL_option_window = findViewById(R.id.option_window);
		BTN_option_quit = findViewById(R.id.quit);
		AV_adview = findViewById(R.id.adView);
		
		CB1_feedbackLight = findViewById(R.id.CB1_feedbackLight);
		CB1_LED = findViewById(R.id.CB1_LED);
		CB1_autoPlay = findViewById(R.id.CB1_autoPlay);
		CB1_traceLog = findViewById(R.id.CB1_traceLog);
		CB1_record = findViewById(R.id.CB1_record);
		CB1s = new CheckBox[]{CB1_feedbackLight, CB1_LED, CB1_autoPlay, CB1_traceLog, CB1_record};
		
		CB2_feedbackLight = findViewById(R.id.CB2_feedbackLight);
		CB2_LED = findViewById(R.id.CB2_LED);
		CB2_autoPlay = findViewById(R.id.CB2_autoPlay);
		CB2_traceLog = findViewById(R.id.CB2_traceLog);
		CB2_record = findViewById(R.id.CB2_record);
		CB2_hideUI = findViewById(R.id.CB2_hideUI);
		CB2_watermark = findViewById(R.id.CB2_watermark);
		CB2_proLightMode = findViewById(R.id.CB2_proLightMode);
		CB2s = new CheckBox[]{CB2_feedbackLight, CB2_LED, CB2_autoPlay, CB2_traceLog, CB2_record, CB2_hideUI, CB2_watermark, CB2_proLightMode};
		
		SCV_feedbackLight = new SyncCheckBox(CB1_feedbackLight, CB2_feedbackLight);
		SCV_LED = new SyncCheckBox(CB1_LED, CB2_LED);
		SCV_autoPlay = new SyncCheckBox(CB1_autoPlay, CB2_autoPlay);
		SCV_traceLog = new SyncCheckBox(CB1_traceLog, CB2_traceLog);
		SCV_record = new SyncCheckBox(CB1_record, CB2_record);
		SCV_hideUI = new SyncCheckBox(CB2_hideUI);
		SCV_watermark = new SyncCheckBox(CB2_watermark);
		SCV_proLightMode = new SyncCheckBox(CB2_proLightMode);
	}
	
	// =========================================================================================
	
	ThemePack.Resources theme;
	
	Unipack unipack;
	boolean unipackLoaded = false;
	boolean UILoaded = false;
	
	Pad[][] U_pads;
	Chain[] U_chains;
	
	LEDTask ledTask;
	AutoPlayTask autoPlayTask;
	
	SoundPool soundPool;
	int[][][] stopID;
	
	int chain = 0;
	
	boolean isFeedbackLight;
	boolean isLEDEvent;
	boolean isTraceLog;
	boolean isRecord;
	boolean isShowWatermark;
	
	final long DELAY = 1;
	
	
	// ========================================================================================= Manager
	
	ColorManager colorManager;
	
	// ========================================================================================= 앱 시작
	
	@SuppressLint("StaticFieldLeak")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_play);
		initVar();
		
		// ================================================================================= URL 불러오기
		String URL = getIntent().getStringExtra("URL");
		Log.log("[01] Start Load Unipack " + URL);
		unipack = new Unipack(URL, true);
		
		try {
			Log.log("[02] Check ErrorDetail");
			if (unipack.ErrorDetail != null) {
				new AlertDialog.Builder(Play.this)
					.setTitle(unipack.CriticalError ? lang(R.string.error) : lang(R.string.warning))
					.setMessage(unipack.ErrorDetail)
					.setPositiveButton(unipack.CriticalError ? lang(R.string.quit) : lang(R.string.accept), unipack.CriticalError ? (dialogInterface, i) -> finish() : null)
					.setCancelable(false)
					.show();
			}
			
			Log.log("[03] Init Vars");
			U_pads = new Pad[unipack.buttonX][unipack.buttonY];
			U_chains = new Chain[32];
			colorManager = new ColorManager(unipack.buttonX, unipack.buttonY);
			
			Log.log("[04] Start LEDTask (isKeyLED = " + unipack.isKeyLED + ")");
			if (unipack.isKeyLED) {
				ledTask = new LEDTask();
				ledTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
			
			Log.log("[05] Set Button Layout (squareButton = " + unipack.squareButton + ")");
			if (unipack.squareButton) {
				if (!unipack.isKeyLED)
					SCV_LED.setVisibility(View.GONE);
				
				if (!unipack.isAutoPlay)
					SCV_autoPlay.setVisibility(View.GONE);
			} else {
				RL_rootView.setPadding(0, 0, 0, 0);
				
				SCV_feedbackLight.setVisibility(View.GONE);
				SCV_LED.setVisibility(View.GONE);
				SCV_autoPlay.setVisibility(View.GONE);
				
				SCV_traceLog.setVisibility(View.GONE);
				SCV_record.setVisibility(View.GONE);
			}
			
			Log.log("[06] Set CheckBox Checked");
			if (unipack.isKeyLED) {
				SCV_LED.setChecked(true);
				SCV_feedbackLight.setChecked(false);
			}
			
			isFeedbackLight = SCV_feedbackLight.isChecked();
			isLEDEvent = SCV_LED.isChecked();
			isTraceLog = SCV_traceLog.isChecked();
			isRecord = SCV_record.isChecked();
			isShowWatermark = SCV_watermark.isChecked();
			
			
			(new AsyncTask<String, String, String>() {
				
				ProgressDialog progressDialog;
				
				@Override
				protected void onPreExecute() {
					Log.log("[07] onPreExecute");
					
					progressDialog = new ProgressDialog(Play.this);
					progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
					progressDialog.setTitle(lang(R.string.loading));
					progressDialog.setMessage(lang(R.string.wait));
					progressDialog.setCancelable(false);
					
					int soundNum = 0;
					for (int i = 0; i < unipack.chain; i++)
						for (int j = 0; j < unipack.buttonX; j++)
							for (int k = 0; k < unipack.buttonY; k++)
								if (unipack.sound[i][j][k] != null)
									soundNum += unipack.sound[i][j][k].size();
					
					soundPool = new SoundPool(soundNum, AudioManager.STREAM_MUSIC, 0);
					stopID = new int[unipack.chain][unipack.buttonX][unipack.buttonY];
					
					progressDialog.setMax(soundNum);
					progressDialog.show();
					super.onPreExecute();
				}
				
				@Override
				protected String doInBackground(String... params) {
					Log.log("[08] doInBackground");
					
					try {
						
						for (int i = 0; i < unipack.chain; i++) {
							for (int j = 0; j < unipack.buttonX; j++) {
								for (int k = 0; k < unipack.buttonY; k++) {
									ArrayList arrayList = unipack.sound[i][j][k];
									if (arrayList != null) {
										for (int l = 0; l < arrayList.size(); l++) {
											Unipack.Sound e = unipack.sound[i][j][k].get(l);
											e.id = soundPool.load(e.URL, 1);
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
					Log.log("[09] onPostExecute");
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
			
			
		} catch (OutOfMemoryError ignore) {
			showToast(R.string.outOfMemory);
			finish();
		}
	}
	
	boolean skin_init(int num) {
		Log.log("[10] skin_init (" + num + ")");
		String packageName = SettingManager.SelectedTheme.load(Play.this);
		if (num >= 2) {
			try {
				ThemePack mTheme = new ThemePack(Play.this, packageName);
				mTheme.loadDefaultThemeResources();
				theme = mTheme.resources;
			} catch (Exception ignore) {
			}
			return true;
		}
		try {
			ThemePack mTheme = new ThemePack(Play.this, packageName);
			mTheme.loadThemeResources();
			theme = mTheme.resources;
			return true;
		} catch (OutOfMemoryError e) {
			e.printStackTrace();
			requestRestart(this);
			showToast(lang(R.string.skinMemoryErr) + "\n" + packageName);
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			showToast(lang(R.string.skinErr) + "\n" + packageName);
			SettingManager.SelectedTheme.save(Play.this, getPackageName());
			return skin_init(num + 1);
		}
	}
	
	@SuppressLint("ClickableViewAccessibility")
	void showUI() {
		Log.log("[11] showUI");
		int buttonSizeX;
		int buttonSizeY;
		
		if (unipack.squareButton) {
			buttonSizeX = buttonSizeY = Math.min(Scale_PaddingHeight / unipack.buttonX, Scale_PaddingWidth / unipack.buttonY);
			
		} else {
			buttonSizeX = Scale_Width / unipack.buttonY;
			buttonSizeY = Scale_Height / unipack.buttonX;
		}
		
		
		SCV_feedbackLight.setOnCheckedChange((isChecked) -> {
			padInit();
			isFeedbackLight = isChecked;
		});
		SCV_LED.setOnCheckedChange((isChecked) -> {
			if (unipack.isKeyLED) {
				isLEDEvent = isChecked;
				if (!isLEDEvent)
					LEDInit();
			}
		});
		SCV_autoPlay.setOnCheckedChange((isChecked) -> {
			if (isChecked) {
				autoPlayTask = new AutoPlayTask();
				try {
					autoPlayTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				} catch (Exception e) {
					SCV_autoPlay.setChecked(false);
					e.printStackTrace();
				}
			} else {
				autoPlayTask.loop = false;
				padInit();
				LEDInit();
				autoPlay_removeGuide();
			}
		});
		SCV_traceLog.setOnCheckedChange((isChecked) -> isTraceLog = isChecked);
		SCV_traceLog.setOnLongClick(() -> {
			traceLog_init();
			showToast(R.string.traceLogClear);
		});
		SCV_record.setOnCheckedChange((isChecked) -> {
			isRecord = isChecked;
			if (isRecord) {
				rec_prevEventMS = System.currentTimeMillis();
				rec_log = "c " + (chain + 1);
			} else {
				putClipboard(rec_log);
				showToast(R.string.copied);
				rec_log = "";
			}
		});
		SCV_hideUI.setOnCheckedChange(isChecked -> {
			if (isChecked)
				RL_option_view.setVisibility(View.GONE);
			else
				RL_option_view.setVisibility(View.VISIBLE);
		});
		SCV_watermark.setOnCheckedChange(this::toggleWatermark);
		SCV_proLightMode.setOnCheckedChange(this::proLightMode);
		IV_prev.setOnClickListener(v -> autoPlay_prev());
		IV_play.setOnClickListener(v -> {
			if (autoPlayTask.isPlaying)
				autoPlay_stop();
			else
				autoPlay_play();
		});
		IV_next.setOnClickListener(v -> autoPlay_after());
		RBV_option_blur.setOnClickListener(v -> {
			if (bool_toggleOptionWindow) toggleOptionWindow(false);
		});
		BTN_option_quit.setOnClickListener(v -> finish());
		
		LL_pads.removeAllViews();
		LL_chainsRight.removeAllViews();
		LL_chainsLeft.removeAllViews();
		
		
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
			LL_pads.addView(row);
		}
		
		for (int i = 0; i < 32; i++) {
			final int finalI = i;
			
			U_chains[i] = new Chain(this);
			U_chains[i].setLayoutParams(new RelativeLayout.LayoutParams(buttonSizeX, buttonSizeY));
			
			if (0 <= i && i <= 7) {
				U_chains[i].setOnClickListener(v -> chainChange(finalI));
				LL_chainsRight.addView(U_chains[i]);
			}
			if (16 <= i && i <= 23) {
				U_chains[i].setOnClickListener(v -> chainChange(finalI));
				LL_chainsLeft.addView(U_chains[i], 0);
			}
		}
		
		traceLog_init();
		chainChange(chain);
		proLightMode(SCV_proLightMode.isChecked());
		
		skin_set();
		
		UILoaded = true;
		updateDriver();
	}
	
	void skin_set() {
		Log.log("[12] skin_set");
		IV_background.setImageDrawable(theme.playbg);
		if (theme.custom_logo != null)
			IV_custom_logo.setImageDrawable(theme.custom_logo);
		else
			IV_custom_logo.setImageDrawable(null);
		
		
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
		
		chainBtnsRefrash();
		
		IV_prev.setBackground(theme.xml_prev);
		IV_play.setBackground(theme.xml_play);
		IV_next.setBackground(theme.xml_next);
		
		
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
		//BTN_option_quit.setBackgroundColor(theme.option_window_btn);
		//BTN_option_quit.setTextColor(theme.option_window_btn_text);
		
	}
	
	// ========================================================================================= 특성 다른 LED 처리
	
	void setLED(int x, int y) {
		setLEDUI(x, y);
		setLEDLaunchpad(x, y);
	}
	
	void setLEDUI(int x, int y) {
		ColorManager.Item Item = colorManager.get(x, y);
		
		if (x != -1) {
			if (Item != null) {
				switch (Item.chanel) {
					case ColorManager.GUIDE:
						U_pads[x][y].setLedBackgroundColor(Item.color);
						break;
					case ColorManager.PRESSED:
						U_pads[x][y].setLedBackground(theme.btn_);
						break;
					case ColorManager.LED:
						U_pads[x][y].setLedBackgroundColor(Item.color);
						break;
				}
			} else
				U_pads[x][y].setLedBackgroundColor(0);
		} else {
			int c = y - 8;
			if (0 <= c && c < 32)
				if (theme.isChainLED) {
					if (Item != null) {
						switch (Item.chanel) {
							case ColorManager.GUIDE:
								U_chains[c].setLedBackgroundColor(Item.color);
								break;
							case ColorManager.PRESSED:
								U_chains[c].setLedBackgroundColor(Item.color);
								break;
							case ColorManager.LED:
								U_chains[c].setLedBackgroundColor(Item.color);
								break;
						}
					} else
						U_chains[c].setLedBackgroundColor(0);
				} else {
					if (Item != null) {
						switch (Item.chanel) {
							case ColorManager.GUIDE:
								U_chains[c].setBackgroundImageDrawable(theme.chain__);
								break;
							case ColorManager.PRESSED:
								U_chains[c].setBackgroundImageDrawable(theme.chain_);
								break;
							case ColorManager.LED:
								U_chains[c].setBackgroundImageDrawable(theme.chain);
								break;
						}
					} else
						U_chains[c].setBackgroundImageDrawable(theme.chain);
				}
			
		}
	}
	
	void setLEDLaunchpad(int x, int y) {
		ColorManager.Item Item = colorManager.get(x, y);
		
		if (x != -1) {
			if (Item != null)
				Launchpad.driver.sendPadLED(x, y, Item.code);
			else
				Launchpad.driver.sendPadLED(x, y, 0);
		} else {
			if (Item != null)
				Launchpad.driver.sendFunctionkeyLED(y, Item.code);
			else
				Launchpad.driver.sendFunctionkeyLED(y, 0);
			
		}
		
	}
	
	// ========================================================================================= LEDTask
	
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
												colorManager.add(x, y, ColorManager.LED, color, velo);
												setLEDLaunchpad(x, y);
												publishProgress(x, y);
												btnLED[x][y] = new LED(e.buttonX, e.buttonY, x, y, color, velo);
											} else {
												colorManager.add(x, y, ColorManager.LED, color, velo);
												setLEDLaunchpad(x, y);
												publishProgress(x, y);
												cirLED[y] = new LED(e.buttonX, e.buttonY, x, y, color, velo);
											}
											
											break;
										case Unipack.LED.Syntax.OFF:
											
											if (x != -1) {
												if (btnLED[x][y] != null && btnLED[x][y].equal(e.buttonX, e.buttonY)) {
													colorManager.remove(x, y, ColorManager.LED);
													setLEDLaunchpad(x, y);
													publishProgress(x, y);
													btnLED[x][y] = null;
												}
											} else {
												if (cirLED[y] != null && cirLED[y].equal(e.buttonX, e.buttonY)) {
													colorManager.remove(x, y, ColorManager.LED);
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
						Log.log("led 오류 e == null");
					} else if (e.isShutdown) {
						for (int x = 0; x < unipack.buttonX; x++) {
							for (int y = 0; y < unipack.buttonY; y++) {
								if (btnLED[x][y] != null && btnLED[x][y].equal(e.buttonX, e.buttonY)) {
									colorManager.remove(x, y, ColorManager.LED);
									setLEDLaunchpad(x, y);
									publishProgress(x, y);
									btnLED[x][y] = null;
								}
							}
						}
						
						for (int y = 0; y < cirLED.length; y++) {
							if (cirLED[y] != null && cirLED[y].equal(e.buttonX, e.buttonY)) {
								colorManager.remove(-1, y, ColorManager.LED);
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
	}
	
	void LEDInit() {
		Log.log("LEDInit");
		if (unipack.isKeyLED) {
			try {
				for (int i = 0; i < unipack.buttonX; i++) {
					for (int j = 0; j < unipack.buttonY; j++) {
						if (ledTask.isEventExist(i, j))
							ledTask.eventShutdown(i, j);
						
						colorManager.remove(i, j, ColorManager.LED);
						setLED(i, j);
					}
				}
				
				for (int i = 0; i < 36; i++) {
					if (ledTask.isEventExist(-1, i))
						ledTask.eventShutdown(-1, i);
					
					colorManager.remove(-1, i, ColorManager.LED);
					setLED(-1, i);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	// ========================================================================================= AutoPlayTask
	
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
				LL_autoPlayControlView.setVisibility(View.VISIBLE);
			PB_autoPlayProgressBar.setMax(unipack.autoPlay.size());
			PB_autoPlayProgressBar.setProgress(0);
			autoPlay_play();
		}
		
		@Override
		protected String doInBackground(String... params) {
			
			long delay = 0;
			long startTime = System.currentTimeMillis();
			
			
			while (progress < unipack.autoPlay.size() && loop) {
				long currTime = System.currentTimeMillis();
				
				if (isPlaying) {
					if (beforeStartPlaying) {
						beforeStartPlaying = false;
						Log.log("beforeStartPlaying");
						publishProgress(8);
					}
					
					if (delay <= currTime - startTime) {
						Unipack.AutoPlay e = unipack.autoPlay.get(progress);
						
						
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
				
				for (; progress < unipack.autoPlay.size() && (addedDelay <= 20 || !complete); progress++) {
					Unipack.AutoPlay e = unipack.autoPlay.get(progress);
					
					switch (e.func) {
						case Unipack.AutoPlay.ON:
							unipack.Sound_push(e.currChain, e.x, e.y, e.num);
							unipack.LED_push(e.currChain, e.x, e.y, e.num);
							publishProgress(4, e.x, e.y);
							complete = true;
							guideItems.add(e);
							Log.log(e.currChain + " " + e.x + " " + e.y);
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
				chainBtnsRefrash();
			}
			PB_autoPlayProgressBar.setProgress(this.progress);
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
			LL_autoPlayControlView.setVisibility(View.GONE);
		}
		
	}
	
	void autoPlay_play() {
		Log.log("autoPlay_play");
		padInit();
		LEDInit();
		autoPlayTask.isPlaying = true;
		
		IV_play.setBackground(theme.xml_pause);
		
		if (unipack.isKeyLED) {
			SCV_LED.setChecked(true);
			SCV_feedbackLight.setChecked(false);
		} else {
			SCV_feedbackLight.setChecked(true);
		}
		autoPlayTask.beforeStartPlaying = true;
	}
	
	void autoPlay_stop() {
		Log.log("autoPlay_stop");
		autoPlayTask.isPlaying = false;
		
		padInit();
		LEDInit();
		
		IV_play.setBackground(theme.xml_play);
		
		
		autoPlayTask.achieve = -1;
		
		SCV_feedbackLight.setChecked(false);
		SCV_LED.setChecked(false);
	}
	
	void autoPlay_prev() {
		Log.log("autoPlay_prev");
		padInit();
		LEDInit();
		int progress = autoPlayTask.progress - 40;
		if (progress < 0) progress = 0;
		autoPlayTask.progress = progress;
		if (!autoPlayTask.isPlaying) {
			Log.log("!isPlaying");
			autoPlayTask.achieve = -1;
			autoPlayTask.check();
		}
		PB_autoPlayProgressBar.setProgress(autoPlayTask.progress);
	}
	
	void autoPlay_after() {
		Log.log("autoPlay_after");
		padInit();
		LEDInit();
		autoPlayTask.progress += 40;
		autoPlayTask.achieve = -1;
		if (!autoPlayTask.isPlaying) {
			Log.log("!isPlaying");
			autoPlayTask.achieve = -1;
			autoPlayTask.check();
		}
		PB_autoPlayProgressBar.setProgress(autoPlayTask.progress);
	}
	
	void autoPlay_guidePad(int x, int y, boolean onOff) {
		//log("autoPlay_guidePad (" + buttonX + ", " + buttonY + ", " + onOff + ")");
		if (onOff) {
			colorManager.add(x, y, ColorManager.GUIDE, LaunchpadColor.ARGB[63] + 0xFF000000, 63);
			setLED(x, y);
		} else {
			colorManager.remove(x, y, ColorManager.GUIDE);
			setLED(x, y);
		}
	}
	
	void autoPlay_guideChain(int c, boolean onOff) {
		//log("autoPlay_guideChain (" + c + ", " + onOff + ")");
		if (onOff) {
			colorManager.add(-1, 8 + c, ColorManager.GUIDE, 0xFF8bc34a, 63);
			setLED(-1, 8 + c);
		} else {
			colorManager.remove(-1, 8 + c, ColorManager.GUIDE);
			setLED(-1, 8 + c);
			chainBtnsRefrash();
		}
	}
	
	void autoPlay_removeGuide() {
		Log.log("autoPlay_removeGuide");
		try {
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++)
					autoPlay_guidePad(i, j, false);
			for (int i = 0; i < unipack.chain; i++) {
				autoPlay_guideChain(i, false);
			}
			chainBtnsRefrash();
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
					//int c = autoPlay.c;
					//int d = autoPlay.d;
					
					if (func == Unipack.AutoPlay.ON && x == x_ && y == y_ && currChain == chain) {
						autoPlayTask.achieve++;
						break;
					}
				}
			}
		}
	}
	
	// ========================================================================================= pad, chain Event
	
	void padTouch(int x, int y, boolean upDown) {
		//log("padTouch (" + buttonX + ", " + buttonY + ", " + upDown + ")");
		try {
			if (upDown) {
				soundPool.stop(stopID[chain][x][y]);
				Unipack.Sound e = unipack.Sound_get(chain, x, y);
				stopID[chain][x][y] = soundPool.play(e.id, 1.0F, 1.0F, 0, e.loop, 1.0F);
				
				unipack.Sound_push(chain, x, y);
				
				if (isRecord) {
					long currTime = System.currentTimeMillis();
					rec_addLog("d " + (currTime - rec_prevEventMS));
					rec_addLog("t " + (x + 1) + " " + (y + 1));
					rec_prevEventMS = currTime;
				}
				if (isTraceLog)
					traceLog_log(x, y);
				
				if (isFeedbackLight) {
					colorManager.add(x, y, ColorManager.PRESSED, LaunchpadColor.ARGB[119] + 0xFF000000, 119);
					setLED(x, y);
				}
				
				if (isLEDEvent)
					ledTask.addEvent(x, y);
				
				autoPlay_checkGuide(x, y);
				
				
				if (e.wormhole != -1)
					new Handler().postDelayed(() -> chainChange(e.wormhole), 100);
			} else {
				if (unipack.Sound_get(chain, x, y).loop == -1)
					soundPool.stop(stopID[chain][x][y]);
				
				if (isFeedbackLight) {
					colorManager.remove(x, y, ColorManager.PRESSED);
					setLED(x, y);
				}
				
				if (isLEDEvent) {
					LEDTask.LEDEvent e = ledTask.searchEvent(x, y);
					if (e != null && e.loop == 0)
						ledTask.eventShutdown(x, y);
				}
			}
		} catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
			e.printStackTrace();
		}
	}
	
	void padInit() {
		Log.log("padInit");
		for (int i = 0; i < unipack.buttonX; i++)
			for (int j = 0; j < unipack.buttonY; j++)
				padTouch(i, j, false);
	}
	
	void chainChange(int num) {
		//log("chainChange (" + num + ")");
		try {
			if (num >= 0 && num < unipack.chain) {
				chain = num;
				chainBtnsRefrash();
			}
			
			// 다중매핑 초기화
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++) {
					unipack.Sound_push(chain, i, j, 0);
					unipack.LED_push(chain, i, j, 0);
				}
			
			
			// 녹음 chain 추가
			if (isRecord) {
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
	
	void chainBtnsRefrash() {
		Log.log("chainBtnsRefrash");
		for (int i = 0; i < unipack.chain; i++) {
			
			if (i == chain) {
				colorManager.add(-1, 8 + i, ColorManager.PRESSED, 0xFFdfe5ee, 119);
			} else {
				colorManager.remove(-1, 8 + i, ColorManager.PRESSED);
			}
			setLEDUI(-1, 8 + i);
			setLEDLaunchpad(-1, 8 + i);
		}
	}
	
	// ========================================================================================= Launchpad Connection
	
	void updateDriver() {
		Launchpad.setDriverListener(Play.this,
			new LaunchpadDriver.DriverRef.OnConnectionEventListener() {
				@Override
				public void onConnected() {
					onConnected_();
					(new Handler()).postDelayed(this::onConnected_, 3000);
				}
				
				void onConnected_() {
					Launchpad.driver.sendFunctionkeyLED(0, 0);
					Launchpad.driver.sendFunctionkeyLED(1, 0);
					Launchpad.driver.sendFunctionkeyLED(2, 0);
					Launchpad.driver.sendFunctionkeyLED(3, 0);
					Launchpad.driver.sendFunctionkeyLED(4, 0);
					Launchpad.driver.sendFunctionkeyLED(5, 0);
					Launchpad.driver.sendFunctionkeyLED(6, 0);
					Launchpad.driver.sendFunctionkeyLED(7, 0);
					
					chainChange(chain);
					toggleWatermark(true);
				}
				
				@Override
				public void onDisconnected() {
				}
			}, new LaunchpadDriver.DriverRef.OnGetSignalListener() {
				@Override
				public void onPadTouch(int x, int y, boolean upDown, int velo) {
					padTouch(x, y, upDown);
				}
				
				@Override
				public void onFunctionkeyTouch(int f, boolean upDown) {
					if (4 <= f && f <= 7 && upDown)
						SCV_watermark.toggleChecked();
				}
				
				@Override
				public void onChainTouch(int c, boolean upDown) {
					if (upDown && unipack.chain > c)
						chainChange(c);
				}
				
				@Override
				public void onUnknownEvent(int cmd, int sig, int note, int velo) {
				}
			});
	}
	
	// ========================================================================================= Trace Log
	
	ArrayList[][][] traceLog_table;
	int[] traceLog_nextNum;
	
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
		Log.log("traceLog_init");
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
	
	// ========================================================================================= Trace Log
	
	long rec_prevEventMS;
	String rec_log = "";
	
	void rec_addLog(String msg) {
		rec_log += "\n" + msg;
	}
	
	void putClipboard(String msg) {
		ClipboardManager clipboardManager = (ClipboardManager) Play.this.getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clipData = ClipData.newPlainText("LABEL", msg);
		assert clipboardManager != null;
		clipboardManager.setPrimaryClip(clipData);
	}
	
	// ========================================================================================= Watermark
	
	void toggleWatermark(boolean bool) {
		isShowWatermark = bool;
		
		if (isShowWatermark) {
			for (int i = 4; i <= 7; i++) {
				colorManager.add(-1, i, ColorManager.GUIDE, -1, i % 2 == 0 ? 61 : 40);
				setLED(-1, i);
			}
		} else {
			for (int i = 4; i <= 7; i++) {
				colorManager.remove(-1, i, ColorManager.GUIDE);
				setLED(-1, i);
			}
		}
		
		colorManager.setCirIgnore(ColorManager.PRESSED, !isShowWatermark);
		chainBtnsRefrash();
	}
	
	// ========================================================================================= Pro Mode
	
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
		
		
		colorManager.setCirIgnore(ColorManager.LED, !bool);
		chainBtnsRefrash();
	}
	
	// ========================================================================================= Option Window
	
	boolean bool_toggleOptionWindow = false;
	
	void toggleOptionWindow() {
		toggleOptionWindow(!bool_toggleOptionWindow);
	}
	
	void toggleOptionWindow(boolean bool) {
		bool_toggleOptionWindow = bool;
		if (bool) {
			Animation a = new Animation() {
				@Override
				protected void applyTransformation(float interpolatedTime, Transformation t) {
					RBV_option_blur.setAlpha(interpolatedTime);
					RL_option_window.setAlpha(interpolatedTime);
				}
			};
			a.setDuration(200);
			a.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
					RBV_option_blur.setVisibility(View.VISIBLE);
					RL_option_window.setVisibility(View.VISIBLE);
				}
				
				@Override
				public void onAnimationEnd(Animation animation) {
				}
				
				@Override
				public void onAnimationRepeat(Animation animation) {
				
				}
			});
			RBV_option_blur.startAnimation(a);
		} else {
			Animation a = new Animation() {
				@Override
				protected void applyTransformation(float interpolatedTime, Transformation t) {
					RBV_option_blur.setAlpha(1 - interpolatedTime);
					RL_option_window.setAlpha(1 - interpolatedTime);
				}
			};
			a.setDuration(500);
			a.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
				
				}
				
				@Override
				public void onAnimationEnd(Animation animation) {
					RBV_option_blur.setVisibility(View.INVISIBLE);
					RL_option_window.setVisibility(View.INVISIBLE);
				}
				
				@Override
				public void onAnimationRepeat(Animation animation) {
				
				}
			});
			RBV_option_blur.startAnimation(a);
		}
	}
	
	// ========================================================================================= Activity
	
	@Override
	public void onBackPressed() {
		toggleOptionWindow();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		//initVar();
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		if (UILoaded)
			updateDriver();
		
		if (Scale_PaddingHeight == 0) {
			Log.log("padding 크기값들이 잘못되었습니다.");
			requestRestart(Play.this);
		}
		
		AdRequest adRequest = new AdRequest.Builder().build();
		AV_adview.loadAd(adRequest);
		
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
						ArrayList arrayList = unipack.sound[i][j][k];
						if (arrayList != null) {
							for (int l = 0; l < arrayList.size(); l++) {
								if (unipack.sound[i][j][k].get(l) != null) {
									try {
										soundPool.unload(unipack.sound[i][j][k].get(l).id);
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
		LEDInit();
		padInit();
		
		
		Launchpad.driver.sendClearLED();
		Launchpad.removeDriverListener(Play.this);
		
		if (unipackLoaded) {
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