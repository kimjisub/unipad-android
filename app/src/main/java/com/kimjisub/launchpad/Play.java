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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.kimjisub.launchpad.manage.LaunchpadColor;
import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.ThemePack;
import com.kimjisub.launchpad.manage.UIManager;
import com.kimjisub.launchpad.manage.Unipack;

import java.util.ArrayList;

import static com.kimjisub.launchpad.manage.Tools.log;
import static com.kimjisub.launchpad.manage.Tools.logErr;

public class Play extends BaseActivity {

	// ========================================================================================= UI 변수 선언 및 초기화

	RelativeLayout RL_rootView;
	ImageView IV_background;
	CheckBox CB_누른키표시;
	CheckBox CB_LED효과;
	CheckBox CB_자동재생;
	CheckBox CB_순서기록;
	CheckBox CB_녹음;
	LinearLayout LL_pads;
	LinearLayout LL_chains;
	LinearLayout LL_자동재생제어뷰;
	ProgressBar PB_진행도;
	ImageView IV_prev;
	ImageView IV_play;
	ImageView IV_next;

	void initVar() {
		RL_rootView = findViewById(R.id.rootView);
		IV_background = findViewById(R.id.background);
		CB_누른키표시 = findViewById(R.id.누른키표시);
		CB_LED효과 = findViewById(R.id.LED효과);
		CB_자동재생 = findViewById(R.id.자동재생);
		CB_순서기록 = findViewById(R.id.순서기록);
		CB_녹음 = findViewById(R.id.녹음);
		LL_pads = findViewById(R.id.pads);
		LL_chains = findViewById(R.id.chains);
		LL_자동재생제어뷰 = findViewById(R.id.자동재생제어뷰);
		PB_진행도 = findViewById(R.id.진행도);
		IV_prev = findViewById(R.id.prev);
		IV_play = findViewById(R.id.play);
		IV_next = findViewById(R.id.next);
	}

	// =========================================================================================

	ThemePack.Resources theme;

	Unipack unipack;
	boolean loaded = false;

	RelativeLayout[][] RL_btns;
	RelativeLayout[] RL_chains;

	LEDTask ledTask;
	AutoPlayTask autoPlayTask;

	SoundPool soundPool;
	int[][][] stopID;

	int chain = 0;

	boolean bool_pressedShow;
	boolean bool_LEDEvent;
	boolean bool_traceLog;
	boolean bool_record;

	final long DELAY = 1;

	// ========================================================================================= 다중 sound 처리

	void soundItemPush(int c, int x, int y) {
		//log("soundItemPush (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			Unipack.Sound tmp = unipack.sound[c][x][y].get(0);
			unipack.sound[c][x][y].remove(0);
			unipack.sound[c][x][y].add(tmp);
		} catch (NullPointerException ignored) {
		} catch (IndexOutOfBoundsException ee) {
			logErr("soundItemPush (" + c + ", " + x + ", " + y + ")");
			ee.printStackTrace();
		}
	}

	void soundItemPush(int c, int x, int y, int num) {
		//log("soundItemPush (" + c + ", " + buttonX + ", " + buttonY + ", " + num + ")");
		try {
			ArrayList<Unipack.Sound> e = unipack.sound[c][x][y];
			if (unipack.sound[c][x][y].get(0).num != num)
				while (true) {
					Unipack.Sound tmp = e.get(0);
					e.remove(0);
					e.add(tmp);
					if (e.get(0).num == num % e.size())
						break;
				}
		} catch (NullPointerException ignored) {
		} catch (IndexOutOfBoundsException ee) {
			logErr("soundItemPush (" + c + ", " + x + ", " + y + ", " + num + ")");
			ee.printStackTrace();
		}
	}

	Unipack.Sound soundItem_get(int c, int x, int y) {
		//log("soundItem_get (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			return unipack.sound[c][x][y].get(0);
		} catch (NullPointerException ignored) {
			return new Unipack.Sound();
		} catch (IndexOutOfBoundsException ee) {
			logErr("soundItem_get (" + c + ", " + x + ", " + y + ")");
			ee.printStackTrace();
			return new Unipack.Sound();
		}
	}

	// ========================================================================================= 다중 LED 처리

	void LEDItem_push(int c, int x, int y) {
		//log("LEDItem_push (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			Unipack.LED e = unipack.led[c][x][y].get(0);
			unipack.led[c][x][y].remove(0);
			unipack.led[c][x][y].add(e);
		} catch (NullPointerException ignored) {
		} catch (IndexOutOfBoundsException ee) {
			logErr("LEDItem_push (" + c + ", " + x + ", " + y + ")");
			ee.printStackTrace();
		}
	}

	void LEDItem_push(int c, int x, int y, int num) {
		//log("LEDItem_push (" + c + ", " + buttonX + ", " + buttonY + ", " + num + ")");
		try {
			ArrayList<Unipack.LED> e = unipack.led[c][x][y];
			if (e.get(0).num != num)
				while (true) {
					Unipack.LED tmp = e.get(0);
					e.remove(0);
					e.add(tmp);
					if (e.get(0).num == num % e.size())
						break;
				}
		} catch (NullPointerException ignored) {
		} catch (IndexOutOfBoundsException ee) {
			logErr("LEDItem_push (" + c + ", " + x + ", " + y + ", " + num + ")");
			ee.printStackTrace();
		}
	}

	Unipack.LED LEDItem_get(int c, int x, int y) {
		//log("LEDItem_get (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			return unipack.led[c][x][y].get(0);
		} catch (NullPointerException ignored) {
			return null;
		} catch (IndexOutOfBoundsException ee) {
			logErr("LEDItem_get (" + c + ", " + x + ", " + y + ")");
			ee.printStackTrace();
			return null;
		}
	}

	// ========================================================================================= 특성 다른 LED 처리

	static class ColorManager {
		static final int GUIDE = 0;
		static final int PRESSED = 1;
		static final int LED = 2;

		static Item[][][] Items = null;

		static class Item {
			int x;
			int y;
			int chanel;
			int color;
			int code;

			Item(int x, int y, int chanel, int color, int code) {
				this.x = x;
				this.y = y;
				this.chanel = chanel;
				this.color = color;
				this.code = code;
			}
		}

		static void init(int x, int y) {
			Items = new Item[x][y][3];
		}

		static Item get(int x, int y) {
			Item ret = null;
			for (int i = 0; i < 3; i++) {
				if (Items[x][y][i] != null) {
					ret = Items[x][y][i];
					break;
				}
			}
			return ret;
		}

		static void add(int x, int y, int chanel, int color_, int code_) {
			Items[x][y][chanel] = new Item(x, y, chanel, color_, code_);
		}

		static void remove(int x, int y, int chanel) {
			Items[x][y][chanel] = null;
		}

	}

	void setLED(int x, int y, ColorManager.Item Item) {
		setLEDUI(x, y, Item);
		setLEDLaunchpad(x, y, Item);
	}

	void setLEDUI(int x, int y, ColorManager.Item Item) {
		if (Item != null) {
			if (Item.chanel == ColorManager.PRESSED)
				RL_btns[x][y].findViewById(R.id.LED).setBackground(theme.btn_);
			else
				RL_btns[x][y].findViewById(R.id.LED).setBackgroundColor(Item.color);
		} else {
			RL_btns[x][y].findViewById(R.id.LED).setBackgroundColor(0);
		}
	}

	void setLEDLaunchpad(int x, int y, ColorManager.Item Item) {
		if (Item != null)
			Launchpad.btnLED(x, y, Item.code);
		else
			Launchpad.btnLED(x, y, 0);

	}

	// ========================================================================================= 앱 시작

	boolean skin_init(int num) {
		log("skin_init (" + num + ")");
		String packageName = SaveSetting.SelectedTheme.load(Play.this);
		if (num >= 2)
			return false;
		try {
			ThemePack mTheme = new ThemePack(Play.this, packageName);
			mTheme.loadThemeResources();
			theme = mTheme.resources;
			return true;
		} catch (OutOfMemoryError e) {
			e.printStackTrace();
			requestRestart(this);
			Toast.makeText(Play.this, lang(R.string.skinMemoryErr) + "\n" + packageName, Toast.LENGTH_LONG).show();
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(Play.this, lang(R.string.skinErr) + "\n" + packageName, Toast.LENGTH_LONG).show();
			SaveSetting.SelectedTheme.save(Play.this, getPackageName());
			return skin_init(num + 1);
		}
	}

	void skin_set() {
		log("skin_set");
		IV_background.setImageDrawable(theme.playbg);
		for (int i = 0; i < unipack.buttonX; i++)
			for (int j = 0; j < unipack.buttonY; j++) {
				RL_btns[i][j].findViewById(R.id.background).setBackground(theme.btn);
				((TextView) RL_btns[i][j].findViewById(R.id.traceLog)).setTextColor(theme.trace_log);
			}

		if (unipack.buttonX < 16 && unipack.buttonY < 16) {
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++)
					RL_btns[i][j].findViewById(R.id.phantom).setBackground(theme.phantom);

			if (unipack.buttonX % 2 == 0 && unipack.buttonY % 2 == 0 && unipack.squareButton && theme.phantom_ != null) {
				int x = unipack.buttonX / 2 - 1;
				int y = unipack.buttonY / 2 - 1;

				RL_btns[x][y].findViewById(R.id.phantom).setBackground(theme.phantom_);

				RL_btns[x + 1][y].findViewById(R.id.phantom).setBackground(theme.phantom_);
				RL_btns[x + 1][y].findViewById(R.id.phantom).setRotation(270);

				RL_btns[x][y + 1].findViewById(R.id.phantom).setBackground(theme.phantom_);
				RL_btns[x][y + 1].findViewById(R.id.phantom).setRotation(90);

				RL_btns[x + 1][y + 1].findViewById(R.id.phantom).setBackground(theme.phantom_);
				RL_btns[x + 1][y + 1].findViewById(R.id.phantom).setRotation(180);
			}
		}

		for (int i = 0; i < unipack.chain; i++) {
			if (theme.isChainLED)
				RL_chains[i].findViewById(R.id.버튼).setBackground(theme.chainled);
			else {
				RL_chains[i].findViewById(R.id.버튼).setBackground(theme.chain);
				RL_chains[i].findViewById(R.id.LED).setVisibility(View.GONE);
			}
		}

		chainInit();

		IV_prev.setBackground(theme.xml_prev);
		IV_play.setBackground(theme.xml_play);
		IV_next.setBackground(theme.xml_next);

		CB_누른키표시.setTextColor(theme.setting_btn);
		CB_LED효과.setTextColor(theme.setting_btn);
		CB_자동재생.setTextColor(theme.setting_btn);
		CB_순서기록.setTextColor(theme.setting_btn);
		CB_녹음.setTextColor(theme.setting_btn);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			CB_누른키표시.setButtonTintList(ColorStateList.valueOf(theme.setting_btn));
			CB_LED효과.setButtonTintList(ColorStateList.valueOf(theme.setting_btn));
			CB_자동재생.setButtonTintList(ColorStateList.valueOf(theme.setting_btn));
			CB_순서기록.setButtonTintList(ColorStateList.valueOf(theme.setting_btn));
			CB_녹음.setButtonTintList(ColorStateList.valueOf(theme.setting_btn));
		}

	}

	@SuppressLint("StaticFieldLeak")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		String URL = getIntent().getStringExtra("URL");
		log("unipack URL : " + URL);
		unipack = new Unipack(URL, true);

		setContentView(R.layout.activity_play);
		initVar();

		try {
			if (unipack.ErrorDetail != null) {
				new AlertDialog.Builder(Play.this)
					.setTitle(unipack.CriticalError ? lang(R.string.error) : lang(R.string.warning))
					.setMessage(unipack.ErrorDetail)
					.setPositiveButton(lang(R.string.accept), null)
					.setCancelable(false)
					.show();
			}
			RL_btns = new RelativeLayout[unipack.buttonX][unipack.buttonY];
			RL_chains = new RelativeLayout[unipack.chain];
			ColorManager.init(unipack.buttonX, unipack.buttonY);

			if (unipack.isKeyLED) {
				ledTask = new LEDTask();
				ledTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}

			if (unipack.squareButton) {
				if (!unipack.isKeyLED)
					CB_LED효과.setVisibility(View.GONE);

				if (!unipack.isAutoPlay)
					CB_자동재생.setVisibility(View.GONE);
			} else {
				RL_rootView.setPadding(0, 0, 0, 0);

				CB_누른키표시.setVisibility(View.GONE);
				CB_LED효과.setVisibility(View.GONE);
				CB_자동재생.setVisibility(View.GONE);

				CB_순서기록.setVisibility(View.GONE);
				CB_녹음.setVisibility(View.GONE);
			}

			if (unipack.isKeyLED) {
				CB_LED효과.setChecked(true);
				CB_누른키표시.setChecked(false);
			}


			bool_pressedShow = CB_누른키표시.isChecked();
			bool_LEDEvent = CB_LED효과.isChecked();
			bool_traceLog = CB_순서기록.isChecked();
			bool_record = CB_녹음.isChecked();

			stopID = new int[unipack.chain][unipack.buttonX][unipack.buttonY];


			traceLog_init();

			if (skin_init(0)) {
				(new AsyncTask<String, String, String>() {

					ProgressDialog progressDialog;

					@Override
					protected void onPreExecute() {

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

						soundPool = new SoundPool(unipack.chain * unipack.buttonX * unipack.buttonY, AudioManager.STREAM_MUSIC, 0);

						progressDialog.setMax(soundNum);
						progressDialog.show();
						super.onPreExecute();
					}

					@Override
					protected String doInBackground(String... params) {

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
							loaded = true;
						} catch (Exception e) {
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
						super.onPostExecute(result);

						if (loaded) {
							try {
								if (progressDialog != null && progressDialog.isShowing())
									progressDialog.dismiss();
							} catch (Exception e) {
								e.printStackTrace();
							}
							try {
								showUI();
							} catch (ArithmeticException | NullPointerException e) {
								e.printStackTrace();
							}
						} else {
							Toast.makeText(Play.this, lang(R.string.outOfCPU), Toast.LENGTH_LONG).show();
							finish();
						}
					}
				}).execute();
			}

		} catch (OutOfMemoryError ignore) {
			Toast.makeText(Play.this, lang(R.string.outOfMemory), Toast.LENGTH_SHORT).show();
			finish();
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	void showUI() {
		log("showUI");
		int buttonX;
		int buttonY;

		if (unipack.squareButton) {
			buttonX = buttonY = Math.min(UIManager.Scale[UIManager.PaddingHeight] / unipack.buttonX, UIManager.Scale[UIManager.PaddingWidth] / unipack.buttonY);

		} else {
			buttonX = UIManager.Scale[UIManager.Width] / unipack.buttonY;
			buttonY = UIManager.Scale[UIManager.Height] / unipack.buttonX;
		}


		CB_누른키표시.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				padInit();
				bool_pressedShow = isChecked;
			}
		});
		CB_LED효과.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (unipack.isKeyLED) {
					bool_LEDEvent = isChecked;
					if (!bool_LEDEvent)
						LEDInit();
				}
			}
		});
		CB_자동재생.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					autoPlayTask = new AutoPlayTask();
					try {
						autoPlayTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					} catch (Exception e) {
						buttonView.setChecked(false);
						e.printStackTrace();
					}
				} else {
					autoPlayTask.loop = false;
					padInit();
					LEDInit();
					autoPlay_removeGuide();
				}
			}
		});
		CB_순서기록.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				bool_traceLog = isChecked;
			}
		});
		CB_순서기록.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {

				traceLog_init();
				Toast.makeText(Play.this, lang(R.string.traceLogClear), Toast.LENGTH_SHORT).show();
				return false;
			}
		});
		CB_녹음.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				bool_record = isChecked;
				if (bool_record) {
					rec_prevEventMS = System.currentTimeMillis();
					rec_log = "c " + (chain + 1);
				} else {
					putClipboard(rec_log);
					Toast.makeText(Play.this, lang(R.string.copied), Toast.LENGTH_SHORT).show();
					rec_log = "";
				}
			}
		});
		IV_prev.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				autoPlay_prev();
			}
		});
		IV_play.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (autoPlayTask.isPlaying)
					autoPlay_stop();
				else
					autoPlay_play();
			}
		});
		IV_next.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				autoPlay_after();
			}
		});


		LL_pads.removeAllViews();
		LL_chains.removeAllViews();


		for (int i = 0; i < unipack.buttonX; i++) {
			LinearLayout row = new LinearLayout(this);
			row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

			for (int j = 0; j < unipack.buttonY; j++) {
				final RelativeLayout RL_btn = (RelativeLayout) View.inflate(this, R.layout.button, null);
				RL_btn.setLayoutParams(new LinearLayout.LayoutParams(buttonX, buttonY));


				final int finalI = i;
				final int finalJ = j;
				RL_btn.setOnTouchListener(new View.OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {
						switch (event.getAction()) {
							case MotionEvent.ACTION_DOWN:
								padTouch(finalI, finalJ, true);
								break;
							case MotionEvent.ACTION_UP:
								padTouch(finalI, finalJ, false);
								break;
						}
						return false;
					}
				});
				RL_btns[i][j] = RL_btn;
				row.addView(RL_btn);
			}
			LL_pads.addView(row);
		}

		if (unipack.chain > 1) {
			for (int i = 0; i < unipack.chain; i++) {
				RL_chains[i] = (RelativeLayout) View.inflate(this, R.layout.chain, null);
				RL_chains[i].setLayoutParams(new RelativeLayout.LayoutParams(buttonX, buttonY));

				final int finalI = i;
				RL_chains[i].findViewById(R.id.버튼).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						chainChange(finalI);
					}
				});

				LL_chains.addView(RL_chains[i]);
			}
		}
		chainChange(chain);

		Launchpad.ReceiveTask.setEventListener(new Launchpad.ReceiveTask.eventListener() {
			@Override
			public void onConnect() {
				(new Handler()).postDelayed(new Runnable() {
					@Override
					public void run() {
						chainChange(chain);
					}
				}, 3000);
			}

			@Override
			public void onGetSignal(int cmd, int note, int velocity) {
				if (cmd == 11 && velocity == 127)
					switch (Launchpad.device) {
						case S:
							if (note == 104)
								CB_LED효과.toggle();
							else if (note == 105)
								CB_자동재생.toggle();
							else if (note == 106) {
								if (CB_자동재생.isChecked())
									autoPlay_prev();
							} else if (note == 107) {
								if (CB_자동재생.isChecked()) {
									if (autoPlayTask.isPlaying)
										autoPlay_stop();
									else
										autoPlay_play();
								}
							}
							break;
						case MK2:
							if (note == 104)
								CB_LED효과.toggle();
							else if (note == 105)
								CB_자동재생.toggle();
							else if (note == 106) {
								if (CB_자동재생.isChecked())
									autoPlay_prev();
							} else if (note == 107) {
								if (CB_자동재생.isChecked()) {
									if (autoPlayTask.isPlaying)
										autoPlay_stop();
									else
										autoPlay_play();
								}
							}
							break;
						case Pro:
							if (note == 91)
								CB_LED효과.toggle();
							else if (note == 92)
								CB_자동재생.toggle();
							else if (note == 93) {
								if (CB_자동재생.isChecked())
									autoPlay_prev();
							} else if (note == 94) {
								if (CB_자동재생.isChecked()) {
									if (autoPlayTask.isPlaying)
										autoPlay_stop();
									else
										autoPlay_play();
								}
							}
							break;
					}
			}

			@Override
			public void onPadTouch(int x, int y, boolean upDown) {
				padTouch(x, y, upDown);
			}

			@Override
			public void onChainChange(int c) {
				if (unipack.chain > c)
					chainChange(c);
			}
		});

		skin_set();
	}

	// ========================================================================================= LEDTask

	@SuppressLint("StaticFieldLeak")
	class LEDTask extends AsyncTask<String, Integer, String> {

		boolean isPlaying = true;
		LEDTask.LED[][] LED;
		ArrayList<LEDTask.LEDEvent> LEDEvents;

		LEDTask() {
			LED = new LED[unipack.buttonX][unipack.buttonY];
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

				Unipack.LED e = LEDItem_get(chain, buttonX, buttonY);
				LEDItem_push(chain, buttonX, buttonY);
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
												ColorManager.add(x, y, ColorManager.LED, color, velo);
												setLEDLaunchpad(x, y, ColorManager.get(x, y));
												publishProgress(x, y);
												LED[x][y] = new LED(e.buttonX, e.buttonY, x, y, color, velo);
											} else {
												Launchpad.circleBtnLED(y, velo);
												publishProgress(x, y, color);
											}

											break;
										case Unipack.LED.Syntax.OFF:
											if (x != -1) {
												if (LED[x][y] != null && LED[x][y].equal(e.buttonX, e.buttonY)) {
													ColorManager.remove(x, y, ColorManager.LED);
													setLEDLaunchpad(x, y, ColorManager.get(x, y));
													publishProgress(x, y);
													LED[x][y] = null;
												}
											} else {
												Launchpad.circleBtnLED(y, 0);
												publishProgress(x, y, 0xffa1b2cc);
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
								if (LED[x][y] != null && LED[x][y].equal(e.buttonX, e.buttonY)) {
									ColorManager.remove(x, y, ColorManager.LED);
									setLEDLaunchpad(x, y, ColorManager.get(x, y));
									publishProgress(x, y);
									LED[x][y] = null;
								}
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
				if (p.length == 2)
					setLEDUI(p[0], p[1], ColorManager.get(p[0], p[1]));
				else if (p.length == 3) {
					int c = p[1] - 8;
					int color = p[2];

					if (c != chain && 0 <= c && c < unipack.chain)
						if (theme.isChainLED)
							RL_chains[c].findViewById(R.id.LED).setBackgroundColor(color);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void onPostExecute(String result) {
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

						ColorManager.remove(i, j, ColorManager.LED);
						setLED(i, j, ColorManager.get(i, j));
					}
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
				LL_자동재생제어뷰.setVisibility(View.VISIBLE);
			PB_진행도.setMax(unipack.autoPlay.size());
			PB_진행도.setProgress(0);
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
						log("beforeStartPlaying");
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
								soundItemPush(currChain, x, y, num);
								LEDItem_push(currChain, x, y, num);
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

					if (e.func == Unipack.AutoPlay.ON || e.func == Unipack.AutoPlay.DELAY) {

						switch (e.func) {
							case Unipack.AutoPlay.ON:
								soundItemPush(e.currChain, e.x, e.y, e.num);
								LEDItem_push(e.currChain, e.x, e.y, e.num);
								publishProgress(4, e.x, e.y);
								complete = true;
								break;
							case Unipack.AutoPlay.DELAY:
								if (complete)
									addedDelay += e.d;
								break;
						}
						if (e.func == Unipack.AutoPlay.ON)
							guideItems.add(e);
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
				chainInit();
			}
			PB_진행도.setProgress(this.progress);
		}

		@Override
		protected void onPostExecute(String result) {
			CB_자동재생.setChecked(false);
			if (unipack.isKeyLED) {
				CB_LED효과.setChecked(true);
				CB_누른키표시.setChecked(false);
			} else {
				CB_누른키표시.setChecked(true);
			}
			LL_자동재생제어뷰.setVisibility(View.GONE);
		}

	}

	void autoPlay_play() {
		log("autoPlay_play");
		padInit();
		LEDInit();
		autoPlayTask.isPlaying = true;

		IV_play.setBackground(theme.xml_pause);

		if (unipack.isKeyLED) {
			CB_LED효과.setChecked(true);
			CB_누른키표시.setChecked(false);
		} else {
			CB_누른키표시.setChecked(true);
		}
		autoPlayTask.beforeStartPlaying = true;
	}

	void autoPlay_stop() {
		log("autoPlay_stop");
		autoPlayTask.isPlaying = false;

		padInit();
		LEDInit();

		IV_play.setBackground(theme.xml_play);


		autoPlayTask.achieve = -1;

		CB_누른키표시.setChecked(false);
		CB_LED효과.setChecked(false);
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
		PB_진행도.setProgress(autoPlayTask.progress);
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
		PB_진행도.setProgress(autoPlayTask.progress);
	}

	void autoPlay_guidePad(int x, int y, boolean onOff) {
		//log("autoPlay_guidePad (" + buttonX + ", " + buttonY + ", " + onOff + ")");
		if (onOff) {
			ColorManager.add(x, y, ColorManager.GUIDE, LaunchpadColor.ARGB[63] + 0xFF000000, 63);
			setLED(x, y, ColorManager.get(x, y));
		} else {
			ColorManager.remove(x, y, ColorManager.GUIDE);
			setLED(x, y, ColorManager.get(x, y));
		}
	}

	void autoPlay_guideChain(int c, boolean onOff) {
		//log("autoPlay_guideChain (" + c + ", " + onOff + ")");
		if (onOff) {
			if (theme.isChainLED)
				RL_chains[c].findViewById(R.id.LED).setBackgroundColor(0xFF8bc34a);
			else
				RL_chains[c].findViewById(R.id.버튼).setBackground(theme.chain__);
			Launchpad.chainLED(c, 63);
		} else
			chainInit();
	}

	void autoPlay_removeGuide() {
		log("autoPlay_removeGuide");
		try {
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++)
					autoPlay_guidePad(i, j, false);
			chainInit();
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
					int c = autoPlay.c;
					int d = autoPlay.d;

					if (func == Unipack.AutoPlay.ON && x == x_ && y == y_ && currChain == chain) {
						autoPlayTask.achieve++;
						break;
					}
				}
			}
		}
	}

	// =========================================================================================

	void padTouch(int x, int y, boolean upDown) {
		//log("padTouch (" + buttonX + ", " + buttonY + ", " + upDown + ")");
		try {
			if (upDown) {
				soundPool.stop(stopID[chain][x][y]);
				Unipack.Sound e = soundItem_get(chain, x, y);
				stopID[chain][x][y] = soundPool.play(e.id, 1.0F, 1.0F, 0, e.loop, 1.0F);

				soundItemPush(chain, x, y);

				if (bool_record) {
					long currTime = System.currentTimeMillis();
					rec_addLog("d " + (currTime - rec_prevEventMS));
					rec_addLog("t " + (x + 1) + " " + (y + 1));
					rec_prevEventMS = currTime;
				}
				if (bool_traceLog)
					traceLog_log(x, y);

				if (bool_pressedShow) {
					ColorManager.add(x, y, ColorManager.PRESSED, LaunchpadColor.ARGB[119] + 0xFF000000, 119);
					setLED(x, y, ColorManager.get(x, y));
				}

				if (bool_LEDEvent)
					ledTask.addEvent(x, y);

				autoPlay_checkGuide(x, y);
			} else {
				if (soundItem_get(chain, x, y).loop == -1)
					soundPool.stop(stopID[chain][x][y]);

				if (bool_pressedShow) {
					ColorManager.remove(x, y, ColorManager.PRESSED);
					setLED(x, y, ColorManager.get(x, y));
				}

				if (bool_LEDEvent) {
					LEDTask.LEDEvent e = ledTask.searchEvent(x, y);
					if (e != null && e.loop == 0)
						ledTask.eventShutdown(x, y);
				}
			}
		} catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
			e.printStackTrace();
		}
	}

	void chainChange(int num) {
		//log("chainChange (" + num + ")");
		try {
			if (unipack.chain > 1 && num >= 0 && num < unipack.chain) {
				chain = num;
				chainInit();
			}

			// 다중매핑 초기화
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++) {
					soundItemPush(chain, i, j, 0);
					LEDItem_push(chain, i, j, 0);
				}


			// 녹음 chain 추가
			if (bool_record) {
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

	void padInit() {
		log("padInit");
		for (int i = 0; i < unipack.buttonX; i++)
			for (int j = 0; j < unipack.buttonY; j++)
				padTouch(i, j, false);
	}

	void chainInit() {
		log("chainInit");
		if (unipack.chain > 1) {
			for (int i = 0; i < unipack.chain; i++) {

				if (i == chain) {
					if (theme.isChainLED)
						RL_chains[i].findViewById(R.id.LED).setBackgroundColor(0xFFdfe5ee);
					else
						RL_chains[i].findViewById(R.id.버튼).setBackground(theme.chain_);
				} else {
					if (theme.isChainLED)
						RL_chains[i].findViewById(R.id.LED).setBackgroundColor(0xFFa1b2cc);
					else
						RL_chains[i].findViewById(R.id.버튼).setBackground(theme.chain);
				}
			}
			Launchpad.chainRefresh(chain);
		}
	}


	// =========================================================================================


	// ========================================================================================= Trace Log

	ArrayList[][][] traceLog_table;
	int[] traceLog_nextNum;

	void traceLog_show() {
		//log("traceLog_show");
		for (int i = 0; i < unipack.buttonX; i++) {
			for (int j = 0; j < unipack.buttonY; j++) {
				((TextView) RL_btns[i][j].findViewById(R.id.traceLog)).setText("");
				for (int k = 0; k < traceLog_table[chain][i][j].size(); k++)
					((TextView) RL_btns[i][j].findViewById(R.id.traceLog)).append(traceLog_table[chain][i][j].get(k) + " ");
			}
		}
	}

	void traceLog_log(int x, int y) {
		//log("traceLog_log (" + buttonX + ", " + buttonY + ")");
		traceLog_table[chain][x][y].add(traceLog_nextNum[chain]++);
		((TextView) RL_btns[x][y].findViewById(R.id.traceLog)).setText("");
		for (int i = 0; i < traceLog_table[chain][x][y].size(); i++)
			((TextView) RL_btns[x][y].findViewById(R.id.traceLog)).append(traceLog_table[chain][x][y].get(i) + " ");
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
					((TextView) RL_btns[i][j].findViewById(R.id.traceLog)).setText("");
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
		clipboardManager.setPrimaryClip(clipData);
	}

	// ========================================================================================= Activity

	long lastBackMS = 0;

	@Override
	public void onBackPressed() {
		if (lastBackMS == 0 || System.currentTimeMillis() - lastBackMS > 2000) {
			Toast toast = Toast.makeText(Play.this, lang(R.string.pressAgainToGoBack), Toast.LENGTH_SHORT);
			toast.setGravity(Gravity.RIGHT | Gravity.BOTTOM, 50, 50);
			toast.show();
			lastBackMS = System.currentTimeMillis();
		} else
			super.onBackPressed();
	}

	@Override
	protected void onResume() {
		super.onResume();

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		if (UIManager.Scale[0] == 0) {
			log("padding 크기값들이 잘못되었습니다.");
			requestRestart(Play.this);
		}
	}

	@Override
	protected void onDestroy() {
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

		(new Handler()).postDelayed(new Runnable() {
			@Override
			public void run() {
				for (int i = 0; i < 36; i++)
					Launchpad.circleBtnLED(i, 0);
				Launchpad.ReceiveTask.setEventListener(null);
				Launchpad.chainRefresh(-1);
			}
		}, 1000);

		if (loaded)
			UIManager.showAds(Play.this);
	}
}