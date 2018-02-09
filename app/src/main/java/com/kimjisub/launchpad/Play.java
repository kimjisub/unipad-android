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

/**
 * Created by rlawl ON 2016-02-03.
 * ReCreated by rlawl ON 2016-04-23.
 */

public class Play extends BaseActivity {

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
	
	ThemePack.Resources theme;

	Unipack unipack;
	boolean loaded = false;

	LEDTask ledTask;
	AutoPlayTask autoPlayTask;

	SoundPool soundPool;
	int[][][] stopID;


	int chain = 0;

	long rec_prevEventMS;
	String rec_log = "";

	ArrayList[][][] trace_table;
	int[] trace_nextNum;

	boolean pressedPadShow;
	boolean LEDEvent;
	boolean traceLog;
	boolean record;

	RelativeLayout[][] RL_btns;
	ImageView[] IV_chains;

	final long DELAY = 1;

	void soundItemPush(int c, int x, int y) {
		//log("soundItemPush (" + c + ", " + x + ", " + y + ")");
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
		//log("soundItemPush (" + c + ", " + x + ", " + y + ", " + num + ")");
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
		//log("soundItem_get (" + c + ", " + x + ", " + y + ")");
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

	void LEDItem_push(int c, int x, int y) {
		//log("LEDItem_push (" + c + ", " + x + ", " + y + ")");
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
		//log("LEDItem_push (" + c + ", " + x + ", " + y + ", " + num + ")");
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
		//log("LEDItem_get (" + c + ", " + x + ", " + y + ")");
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

		static Item add(int x, int y, int chanel, int color_, int code_) {
			Items[x][y][chanel] = new Item(x, y, chanel, color_, code_);
			return get(x, y);
		}

		static Item remove(int x, int y, int chanel) {
			Items[x][y][chanel] = null;
			return get(x, y);
		}

	}

	void setColor(int x, int y, ColorManager.Item Item) {
		setColorUI(x, y, Item);
		setColorLaunchpad(x, y, Item);
	}

	void setColorUI(int x, int y, ColorManager.Item Item) {
		if (Item != null) {
			if (Item.chanel == ColorManager.PRESSED)
				RL_btns[x][y].findViewById(R.id.LED).setBackground(theme.btn_);
			else
				RL_btns[x][y].findViewById(R.id.LED).setBackgroundColor(Item.color);
		} else {
			RL_btns[x][y].findViewById(R.id.LED).setBackgroundColor(0);
		}
	}

	void setColorLaunchpad(int x, int y, ColorManager.Item Item) {
		if (Item != null)
			Launchpad.btnLED(x, y, Item.code);
		else
			Launchpad.btnLED(x, y, 0);

	}

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
			IV_chains = new ImageView[unipack.chain];
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


			pressedPadShow = CB_누른키표시.isChecked();
			LEDEvent = CB_LED효과.isChecked();
			record = CB_녹음.isChecked();

			stopID = new int[unipack.chain][unipack.buttonX][unipack.buttonY];
			trace_table = new ArrayList[unipack.chain][unipack.buttonX][unipack.buttonY];
			trace_nextNum = new int[unipack.chain];

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
				pressedPadShow = isChecked;
			}
		});
		CB_LED효과.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (unipack.isKeyLED) {
					LEDEvent = isChecked;
					if (!LEDEvent)
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
					removeGuide();
				}
			}
		});
		CB_순서기록.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				traceLog = isChecked;
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
				record = isChecked;
				if (record) {
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
				final RelativeLayout RL_chain = (RelativeLayout) View.inflate(this, R.layout.chain, null);
				RL_chain.setLayoutParams(new RelativeLayout.LayoutParams(buttonX, buttonY));

				final int finalI = i;
				RL_chain.findViewById(R.id.버튼).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						chainChange(finalI);
					}
				});

				IV_chains[i] = RL_chain.findViewById(R.id.버튼);
				LL_chains.addView(RL_chain);
			}
		}
		chainChange(chain);

		Launchpad.ReceiveTask.setGetSignalListener(new Launchpad.ReceiveTask.getSignalListener() {
			@Override
			public void getSignal(int command, int note, int velocity) {


				if (Launchpad.device == Launchpad.midiDevice.S) {
					if (command == 9 && velocity != 0) {
						int x = note / 16 + 1;
						int y = note % 16 + 1;
						if (y >= 1 && y <= 8) {
							padTouch(x - 1, y - 1, true);
						} else if (y == 9) {
							if (unipack.chain > x - 1)
								chainChange(x - 1);
						}
					} else if (command == 9 && velocity == 0) {
						int x = note / 16 + 1;
						int y = note % 16 + 1;
						if (y >= 1 && y <= 8) {
							padTouch(x - 1, y - 1, false);
						}
					} else if (command == 11) {
					}
				} else if (Launchpad.device == Launchpad.midiDevice.MK2) {
					if (command == 9 && velocity != 0) {
						int x = 9 - (note / 10);
						int y = note % 10;
						if (y >= 1 && y <= 8) {
							padTouch(x - 1, y - 1, true);
						} else if (y == 9) {
							if (unipack.chain > x - 1)
								chainChange(x - 1);
						}
					} else if (command == 9 && velocity == 0) {
						int x = 9 - (note / 10);
						int y = note % 10;
						if (y >= 1 && y <= 8) {
							padTouch(x - 1, y - 1, false);
						}
					} else if (command == 11) {
					}
				} else if (Launchpad.device == Launchpad.midiDevice.Pro) {
					if (command == 9 && velocity != 0) {
						int x = 9 - (note / 10);
						int y = note % 10;
						if (y >= 1 && y <= 8) {
							padTouch(x - 1, y - 1, true);
						}
					} else if (command == 9 && velocity == 0) {
						int x = 9 - (note / 10);
						int y = note % 10;
						if (y >= 1 && y <= 8) {
							padTouch(x - 1, y - 1, false);
						}
					} else if (command == 11 && velocity != 0) {
						int x = 9 - (note / 10);
						int y = note % 10;
						if (y == 9) {
							if (unipack.chain > x - 1)
								chainChange(x - 1);
						}
					}
				} else if (Launchpad.device == Launchpad.midiDevice.Piano) {

					int x;
					int y;

					if (command == 9 && velocity != 0) {
						if (note >= 36 && note <= 67) {
							x = (67 - note) / 4 + 1;
							y = 4 - (67 - note) % 4;
							padTouch(x - 1, y - 1, true);
						} else if (note >= 68 && note <= 99) {
							x = (99 - note) / 4 + 1;
							y = 8 - (99 - note) % 4;
							padTouch(x - 1, y - 1, true);
						}

					} else if (velocity == 0) {
						if (note >= 36 && note <= 67) {
							x = (67 - note) / 4 + 1;
							y = 4 - (67 - note) % 4;
							padTouch(x - 1, y - 1, false);
						} else if (note >= 68 && note <= 99) {
							x = (99 - note) / 4 + 1;
							y = 8 - (99 - note) % 4;
							padTouch(x - 1, y - 1, false);
						}
					}
				}

			}
		});

		skin_set();

		Launchpad.setConnectListener(new Launchpad.connectListener() {
			@Override
			public void connect() {
				(new Handler()).postDelayed(new Runnable() {
					@Override
					public void run() {
						chainChange(chain);
					}
				}, 3000);
			}
		});

	}

	@SuppressLint("StaticFieldLeak")
	class LEDTask extends AsyncTask<String, Integer, String> {

		boolean isPlaying = true;
		ArrayList<LEDTask.LEDEvent> LEDEvents;
		LEDTask.LED[][] LED = new LED[unipack.buttonX][unipack.buttonY];

		LEDTask() {
			LEDEvents = new ArrayList<>();
		}

		class LEDEvent {
			boolean noError = false;

			ArrayList<Unipack.LED.Syntax> syntaxs;
			int index = 0;
			long delay = -1;

			int x;
			int y;
			boolean isPlaying = true;
			boolean isShutdown = false;
			int loop;
			int loopProgress = 0;

			LEDEvent(int x, int y) {
				this.x = x;
				this.y = y;

				Unipack.LED e = LEDItem_get(chain, x, y);
				LEDItem_push(chain, x, y);
				if (e != null) {
					syntaxs = e.syntaxs;
					loop = e.loop;
					noError = true;
				}
			}

			boolean equal(int x, int y) {
				return (this.x == x) && (this.y == y);
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

			boolean equal(int x, int y) {
				return (this.buttonX == x) && (this.buttonY == y);
			}
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

					//try {
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
											setColorLaunchpad(x, y, ColorManager.add(x, y, ColorManager.LED, color, velo));
											publishProgress(x, y);
											LED[x][y] = new LED(e.x, e.y, x, y, color, velo);

											break;
										case Unipack.LED.Syntax.OFF:
											if (LED[x][y] != null && LED[x][y].equal(e.x, e.y)) {
												setColorLaunchpad(x, y, ColorManager.remove(x, y, ColorManager.LED));
												publishProgress(x, y);
												LED[x][y] = null;
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
						for (int i_ = 0; i_ < unipack.buttonX; i_++) {
							for (int j_ = 0; j_ < unipack.buttonY; j_++) {
								if (LED[i_][j_] != null && LED[i_][j_].equal(e.x, e.y)) {
									setColorLaunchpad(i_, j_, ColorManager.remove(i_, j_, ColorManager.LED));
									publishProgress(i_, j_);
									LED[i_][j_] = null;
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
				setColorUI(p[0], p[1], ColorManager.get(p[0], p[1]));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void onPostExecute(String result) {
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
						log("beforeStartPlaying");
						beforeStartPlaying = false;
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

					if (guideItems.size() != 0 && guideItems.get(0).currChain != chain) {
						if (beforeChain == -1 || beforeChain != chain) {
							beforeChain = chain;
							afterMatchChain = true;
							publishProgress(8);
							publishProgress(5, guideItems.get(0).currChain);
						}
					} else {
						if (afterMatchChain) {
							publishProgress(9);
							afterMatchChain = false;
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
				guidePad(progress[1], progress[2], true);
			} else if (progress[0] == 5) {
				guideChain(progress[1], true);
			} else if (progress[0] == 6) {
				guidePad(progress[1], progress[2], false);
			} else if (progress[0] == 7) {
				guideChain(progress[1], false);
			} else if (progress[0] == 8) {
				removeGuide();
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

	void padTouch(int x, int y, boolean upDown) {
		//log("padTouch (" + x + ", " + y + ", " + upDown + ")");
		try {
			if (upDown) {
				soundPool.stop(stopID[chain][x][y]);
				Unipack.Sound e = soundItem_get(chain, x, y);
				stopID[chain][x][y] = soundPool.play(e.id, 1.0F, 1.0F, 0, e.loop, 1.0F);

				soundItemPush(chain, x, y);

				if (record) {
					long currTime = System.currentTimeMillis();
					rec_addLog("d " + (currTime - rec_prevEventMS));
					rec_addLog("t " + (x + 1) + " " + (y + 1));
					rec_prevEventMS = currTime;
				}
				if (traceLog)
					traceLog_log(x, y);

				if (pressedPadShow)
					setColor(x, y, ColorManager.add(x, y, ColorManager.PRESSED, LaunchpadColor.ARGB[119] + 0xFF000000, 119));

				if (LEDEvent)
					ledTask.addEvent(x, y);

				checkGuide(x, y);
			} else {
				if (soundItem_get(chain, x, y).loop == -1)
					soundPool.stop(stopID[chain][x][y]);

				if (pressedPadShow)
					setColor(x, y, ColorManager.remove(x, y, ColorManager.PRESSED));

				if (LEDEvent) {
					LEDTask.LEDEvent e = ledTask.searchEvent(x, y);

					if (e != null && e.loop == 0) {
						ledTask.eventShutdown(x, y);
					}
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

			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++) {
					soundItemPush(chain, i, j, 0);
					LEDItem_push(chain, i, j, 0);
				}

			if (record) {
				long currTime = System.currentTimeMillis();
				rec_addLog("d " + (currTime - rec_prevEventMS));
				rec_addLog("chain " + (chain + 1));
				rec_prevEventMS = currTime;
			}
			traceLog_show();
			//checkGuide(num);
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}
	}

	void guidePad(int x, int y, boolean onOff) {
		//log("guidePad (" + x + ", " + y + ", " + onOff + ")");
		if (onOff)
			setColor(x, y, ColorManager.add(x, y, ColorManager.GUIDE, LaunchpadColor.ARGB[63] + 0xFF000000, 63));
		else
			setColor(x, y, ColorManager.remove(x, y, ColorManager.GUIDE));
	}

	void guideChain(int c, boolean onOff) {
		//log("guideChain (" + c + ", " + onOff + ")");
		if (onOff) {
			IV_chains[c].setBackground(theme.chain__);
			Launchpad.chainLED(c, 63);
		} else
			chainInit();
	}


	void removeGuide() {
		log("removeGuide");
		try {
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++)
					setColor(i, j, ColorManager.remove(i, j, ColorManager.GUIDE));
			chainInit(false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void checkGuide(int x, int y) {
		//log("checkGuide (" + x + ", " + y + ")");
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

/*	void checkGuide(int c) {
		log("checkGuide (" + c + ")");
		if (autoPlayTask != null && autoPlayTask.loop && !autoPlayTask.isPlaying) {
			ArrayList<Unipack.AutoPlay> guideItems = autoPlayTask.guideItems;
			if (guideItems != null) {
				for (Unipack.AutoPlay autoPlay : autoPlayTask.guideItems) {
					int func = autoPlay.func;
					int currChain = autoPlay.currChain;
					int x = autoPlay.x;
					int y = autoPlay.y;
					int c_ = autoPlay.c;
					int d = autoPlay.d;

					if (func == Unipack.AutoPlay.CHAIN && c_ == c_) {
						autoPlayTask.achieve++;
						break;
					}
				}
			}
		}
	}*/


	void LEDInit() {
		log("LEDInit");
		if (unipack.isKeyLED) {
			try {
				for (int i = 0; i < unipack.buttonX; i++) {
					for (int j = 0; j < unipack.buttonY; j++) {

						if (ledTask.isEventExist(i, j))
							ledTask.eventShutdown(i, j);

						setColor(i, j, ColorManager.remove(i, j, ColorManager.LED));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	void padInit() {
		log("padInit");
		for (int i = 0; i < unipack.buttonX; i++)
			for (int j = 0; j < unipack.buttonY; j++)
				padTouch(i, j, false);
	}

	void chainInit() {
		chainInit(true);
	}

	void chainInit(boolean chainGuide) {
		log("chainInit");
		if (chainGuide)
			if (autoPlayTask != null)
				autoPlayTask.beforeChain = -1;
		if (unipack.chain > 1) {
			for (int i = 0; i < unipack.chain; i++) {
				if (i == chain)
					IV_chains[i].setBackground(theme.chain_);
				else
					IV_chains[i].setBackground(theme.chain);
			}
			Launchpad.chainRefresh(chain);
		}
	}

	void traceLog_show() {
		//log("traceLog_show");
		for (int i = 0; i < unipack.buttonX; i++) {
			for (int j = 0; j < unipack.buttonY; j++) {
				((TextView) RL_btns[i][j].findViewById(R.id.traceLog)).setText("");
				for (int k = 0; k < trace_table[chain][i][j].size(); k++)
					((TextView) RL_btns[i][j].findViewById(R.id.traceLog)).append(trace_table[chain][i][j].get(k) + " ");
			}
		}
	}

	void traceLog_log(int x, int y) {
		//log("traceLog_log (" + x + ", " + y + ")");
		trace_table[chain][x][y].add(trace_nextNum[chain]++);
		((TextView) RL_btns[x][y].findViewById(R.id.traceLog)).setText("");
		for (int i = 0; i < trace_table[chain][x][y].size(); i++)
			((TextView) RL_btns[x][y].findViewById(R.id.traceLog)).append(trace_table[chain][x][y].get(i) + " ");
	}

	void traceLog_init() {
		log("traceLog_init");
		for (int i = 0; i < unipack.chain; i++) {
			for (int j = 0; j < unipack.buttonX; j++)
				for (int k = 0; k < unipack.buttonY; k++)
					if (trace_table[i][j][k] == null)
						trace_table[i][j][k] = new ArrayList<>();
					else
						trace_table[i][j][k].clear();
			trace_nextNum[i] = 1;
		}
		try {
			for (int i = 0; i < unipack.buttonX; i++)
				for (int j = 0; j < unipack.buttonY; j++)
					((TextView) RL_btns[i][j].findViewById(R.id.traceLog)).setText("");
		} catch (NullPointerException e) {
		}
	}

	void rec_addLog(String msg) {
		rec_log += "\n" + msg;
	}

	void putClipboard(String msg) {
		ClipboardManager clipboardManager = (ClipboardManager) Play.this.getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clipData = ClipData.newPlainText("LABEL", msg);
		clipboardManager.setPrimaryClip(clipData);
	}

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

		Launchpad.ReceiveTask.setGetSignalListener(null);
		Launchpad.setConnectListener(null);
		Launchpad.chainRefresh(-1);
		LEDInit();
		padInit();

		if (loaded)
			UIManager.showAds(Play.this);
	}
}