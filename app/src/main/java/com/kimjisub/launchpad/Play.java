package com.kimjisub.launchpad;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
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

import java.util.ArrayList;

/**
 * Created by rlawl on 2016-02-03.
 * ReCreated by rlawl on 2016-04-23.
 */

public class Play extends BaseActivity {
	정보.Theme.Resources theme;
	
	Vibrator vibrator;
	
	정보.uni 프로젝트;
	boolean 로딩성공 = false;
	
	LED쓰레드 LED쓰레드;
	자동재생쓰레드 자동재생쓰레드;
	
	SoundPool 소리;
	int[][][] 정지아이디;
	
	
	int 체인 = 0;
	long 이전이밴트ms;
	String 로그내용 = "";
	ArrayList<Integer>[][][] 순서기록표;
	int[] 다음순서;
	
	boolean 누른키표시;
	boolean LED효과;
	boolean 순서기록;
	boolean 녹음;
	
	RelativeLayout[][] RL_버튼들;
	ImageView[] IV_체인들;
	
	long delay = 1;
	
	
	void 소리요소밀기(int c, int x, int y) {
		try {
			정보.uni.소리요소 tmp = 프로젝트.소리[c][x][y].get(0);
			프로젝트.소리[c][x][y].remove(0);
			프로젝트.소리[c][x][y].add(tmp);
		} catch (NullPointerException ee) {
			//ee.printStackTrace();
		} catch (IndexOutOfBoundsException ee) {
			ee.printStackTrace();
			//Toast.makeText(Play.this, "발견은 되었지만 알 수 없는 오류입니다\n개발자에게 어떤 팩인지 문의주시면 해결해보겠습니다!", Toast.LENGTH_SHORT).show();
		}
	}
	
	void 소리요소밀기(int c, int x, int y, int 번호) {
		try {
			ArrayList<정보.uni.소리요소> e = 프로젝트.소리[c][x][y];
			if (프로젝트.소리[c][x][y].get(0).번호 != 번호)
				while (true) {
					정보.uni.소리요소 tmp = e.get(0);
					e.remove(0);
					e.add(tmp);
					if (e.get(0).번호 == 번호 % e.size())
						break;
				}
		} catch (NullPointerException ee) {
			//ee.printStackTrace();
		} catch (IndexOutOfBoundsException ee) {
			ee.printStackTrace();
		}
	}
	
	정보.uni.소리요소 소리요소가져오기(int c, int x, int y) {
		try {
			return 프로젝트.소리[c][x][y].get(0);
		} catch (NullPointerException e) {
			return new 정보.uni.소리요소();
		} catch (IndexOutOfBoundsException ee) {
			ee.printStackTrace();
			return new 정보.uni.소리요소();
		}
	}
	
	void LED이벤트밀기(int c, int x, int y) {
		try {
			정보.uni.LED이벤트 e = 프로젝트.LED[c][x][y].get(0);
			프로젝트.LED[c][x][y].remove(0);
			프로젝트.LED[c][x][y].add(e);
		} catch (NullPointerException ignored) {
		} catch (IndexOutOfBoundsException ee) {
			ee.printStackTrace();
		}
	}
	
	void LED이벤트밀기(int c, int x, int y, int 번호) {
		try {
			ArrayList<정보.uni.LED이벤트> e = 프로젝트.LED[c][x][y];
			if (e.get(0).번호 != 번호)
				while (true) {
					정보.uni.LED이벤트 tmp = e.get(0);
					e.remove(0);
					e.add(tmp);
					if (e.get(0).번호 == 번호 % e.size())
						break;
				}
		} catch (NullPointerException ignored) {
		} catch (IndexOutOfBoundsException ee) {
			ee.printStackTrace();
		}
	}
	
	정보.uni.LED이벤트 LED이벤트가져오기(int c, int x, int y) {
		try {
			return 프로젝트.LED[c][x][y].get(0);
		} catch (NullPointerException ee) {
			return null;
		} catch (IndexOutOfBoundsException ee) {
			return null;
		}
	}
	
	static class 색관리 {
		static final int 연습모드 = 0;
		static final int 누른키 = 1;
		static final int LED = 2;
		
		static 요소[][][] 요소 = null;
		
		static class 요소 {
			int x;
			int y;
			int chanel;
			int color;
			int code;
			
			public 요소(int x, int y, int chanel, int color, int code) {
				this.x = x;
				this.y = y;
				this.chanel = chanel;
				this.color = color;
				this.code = code;
			}
		}
		
		static 요소 get(int x, int y) {
			요소 ret = null;
			for (int i = 0; i < 3; i++) {
				if (요소[x][y][i] != null) {
					ret = 요소[x][y][i];
					break;
				}
			}
			return ret;
		}
		
		static 요소 add(int x, int y, int chanel, int color_, int code_) {
			요소[x][y][chanel] = new 요소(x, y, chanel, color_, code_);
			return get(x, y);
		}
		
		static 요소 remove(int x, int y, int chanel) {
			요소[x][y][chanel] = null;
			return get(x, y);
		}
		
	}
	
	void 색설정(int x, int y, 색관리.요소 요소) {
		색설정UI(x, y, 요소);
		색설정Launchpad(x, y, 요소);
	}
	
	void 색설정UI(int x, int y, 색관리.요소 요소) {
		if (요소 != null) {
			if (요소.chanel == 색관리.누른키)
				RL_버튼들[x][y].findViewById(R.id.LED).setBackground(theme.btn_);
			else
				RL_버튼들[x][y].findViewById(R.id.LED).setBackgroundColor(요소.color);
		} else {
			RL_버튼들[x][y].findViewById(R.id.LED).setBackgroundColor(0);
		}
	}
	
	void 색설정Launchpad(int x, int y, 색관리.요소 요소) {
		if (요소 != null)
			Launchpad.런치패드패드LED(x, y, 요소.code);
		else
			Launchpad.런치패드패드LED(x, y, 0);
		
	}
	
	boolean 스킨초기화(int num) {
		String packageName = 정보.설정.selectedTheme.불러오기(Play.this);
		if (num >= 2)
			return false;
		try {
			정보.Theme mTheme = new 정보.Theme(Play.this, packageName);
			//mTheme.init();
			mTheme.loadThemeResources();
			theme = mTheme.resources;
			return true;
		} catch (OutOfMemoryError e) {
			e.printStackTrace();
			재시작(this);
			Toast.makeText(Play.this, 언어(R.string.skinMemoryErr) + "\n" + packageName, Toast.LENGTH_LONG).show();
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(Play.this, 언어(R.string.skinErr) + "\n" + packageName, Toast.LENGTH_LONG).show();
			정보.설정.selectedTheme.저장하기(Play.this, getPackageName());
			return 스킨초기화(num + 1);
		}
	}
	
	void 스킨설정() {
		((ImageView) findViewById(R.id.background)).setImageDrawable(theme.playbg);
		for (int i = 0; i < 프로젝트.가로축; i++)
			for (int j = 0; j < 프로젝트.세로축; j++)
				RL_버튼들[i][j].findViewById(R.id.background).setBackground(theme.btn);
		
		if (프로젝트.가로축 < 16 && 프로젝트.세로축 < 16) {
			for (int i = 0; i < 프로젝트.가로축; i++)
				for (int j = 0; j < 프로젝트.세로축; j++)
					RL_버튼들[i][j].findViewById(R.id.phantom).setBackground(theme.phantom);
			
			if (프로젝트.가로축 % 2 == 0 && 프로젝트.세로축 % 2 == 0 && 프로젝트.정사각형버튼 && theme.phantom_ != null) {
				int x = 프로젝트.가로축 / 2 - 1;
				int y = 프로젝트.세로축 / 2 - 1;
				
				RL_버튼들[x][y].findViewById(R.id.phantom).setBackground(theme.phantom_);
				
				RL_버튼들[x + 1][y].findViewById(R.id.phantom).setBackground(theme.phantom_);
				RL_버튼들[x + 1][y].findViewById(R.id.phantom).setRotation(270);
				
				RL_버튼들[x][y + 1].findViewById(R.id.phantom).setBackground(theme.phantom_);
				RL_버튼들[x][y + 1].findViewById(R.id.phantom).setRotation(90);
				
				RL_버튼들[x + 1][y + 1].findViewById(R.id.phantom).setBackground(theme.phantom_);
				RL_버튼들[x + 1][y + 1].findViewById(R.id.phantom).setRotation(180);
			}
		}
		
		체인초기화();
		
		findViewById(R.id.prev).setBackground(theme.xml_prev);
		findViewById(R.id.play).setBackground(theme.xml_play);
		findViewById(R.id.next).setBackground(theme.xml_next);
		
		((CheckBox) findViewById(R.id.누른키표시)).setTextColor(theme.text1);
		((CheckBox) findViewById(R.id.LED효과)).setTextColor(theme.text1);
		((CheckBox) findViewById(R.id.자동재생)).setTextColor(theme.text1);
		((CheckBox) findViewById(R.id.순서기록)).setTextColor(theme.text1);
		((CheckBox) findViewById(R.id.녹음)).setTextColor(theme.text1);
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			((CheckBox) findViewById(R.id.누른키표시)).setButtonTintList(ColorStateList.valueOf(theme.text1));
			((CheckBox) findViewById(R.id.자동재생)).setButtonTintList(ColorStateList.valueOf(theme.text1));
			((CheckBox) findViewById(R.id.순서기록)).setButtonTintList(ColorStateList.valueOf(theme.text1));
			((CheckBox) findViewById(R.id.녹음)).setButtonTintList(ColorStateList.valueOf(theme.text1));
			((CheckBox) findViewById(R.id.LED효과)).setButtonTintList(ColorStateList.valueOf(theme.text1));
		}
		
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		
		String URL = getIntent().getStringExtra("URL");
		화면.log("PlayActivity onCreate()\nURL : " + URL);
		프로젝트 = new 정보.uni(URL, true);
		
		setContentView(R.layout.activity_play);
		
		
		try {
			
			vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
			
			if (프로젝트.에러내용 != null) {
				new AlertDialog.Builder(Play.this)
					.setTitle(언어(프로젝트.치명적인에러 ? R.string.error : R.string.warning))
					.setMessage(프로젝트.에러내용)
					.setPositiveButton(언어(R.string.accept), null)
					.setCancelable(false)
					.show();
			}
			RL_버튼들 = new RelativeLayout[프로젝트.가로축][프로젝트.세로축];
			IV_체인들 = new ImageView[프로젝트.체인];
			색관리.요소 = new 색관리.요소[프로젝트.가로축][프로젝트.세로축][3];
			
			if (프로젝트.keyLED여부) {
				LED쓰레드 = new LED쓰레드();
				LED쓰레드.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
			
			if (프로젝트.정사각형버튼) {
				if (!프로젝트.keyLED여부)
					findViewById(R.id.LED효과).setVisibility(View.GONE);
				
				if (!프로젝트.autoPlay여부)
					findViewById(R.id.자동재생).setVisibility(View.GONE);
			} else {
				findViewById(R.id.루트뷰).setPadding(0, 0, 0, 0);
				
				findViewById(R.id.누른키표시).setVisibility(View.GONE);
				findViewById(R.id.LED효과).setVisibility(View.GONE);
				findViewById(R.id.자동재생).setVisibility(View.GONE);
				
				findViewById(R.id.순서기록).setVisibility(View.GONE);
				findViewById(R.id.녹음).setVisibility(View.GONE);
			}
			
			if (프로젝트.keyLED여부) {
				((CheckBox) findViewById(R.id.LED효과)).setChecked(true);
				((CheckBox) findViewById(R.id.누른키표시)).setChecked(false);
			}
			
			
			누른키표시 = ((CheckBox) findViewById(R.id.누른키표시)).isChecked();
			LED효과 = ((CheckBox) findViewById(R.id.LED효과)).isChecked();
			녹음 = ((CheckBox) findViewById(R.id.녹음)).isChecked();
			
			정지아이디 = new int[프로젝트.체인][프로젝트.가로축][프로젝트.세로축];
			순서기록표 = new ArrayList[프로젝트.체인][프로젝트.가로축][프로젝트.세로축];
			다음순서 = new int[프로젝트.체인];
			순서기록초기설정();
			
			if (스킨초기화(0)) {
				(new AsyncTask<String, String, String>() {
					
					ProgressDialog 로딩;
					
					@Override
					protected void onPreExecute() {
						
						로딩 = new ProgressDialog(Play.this);
						로딩.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
						로딩.setTitle(언어(R.string.loading));
						로딩.setMessage(언어(R.string.wait));
						로딩.setCancelable(false);
						
						int 노래개수 = 0;
						for (int i = 0; i < 프로젝트.체인; i++)
							for (int j = 0; j < 프로젝트.가로축; j++)
								for (int k = 0; k < 프로젝트.세로축; k++)
									if (프로젝트.소리[i][j][k] != null)
										노래개수 += 프로젝트.소리[i][j][k].size();
						
						소리 = new SoundPool(프로젝트.체인 * 프로젝트.가로축 * 프로젝트.세로축, AudioManager.STREAM_MUSIC, 0);
						//소리 = AudioPool.Create();
						
						
						로딩.setMax(노래개수);
						로딩.show();
						super.onPreExecute();
					}
					
					@Override
					protected String doInBackground(String... params) {
						
						try {
							
							for (int i = 0; i < 프로젝트.체인; i++) {
								for (int j = 0; j < 프로젝트.가로축; j++) {
									for (int k = 0; k < 프로젝트.세로축; k++) {
										ArrayList 요소 = 프로젝트.소리[i][j][k];
										if (요소 != null) {
											for (int l = 0; l < 요소.size(); l++) {
												정보.uni.소리요소 e = 프로젝트.소리[i][j][k].get(l);
												e.아이디 = 소리.load(e.노래경로, 1);
												//e.아이디 = 소리.load(e.노래경로);
												publishProgress();
											}
										}
									}
								}
							}
							로딩성공 = true;
						} catch (Exception e) {
							e.printStackTrace();
						}
						
						return null;
					}
					
					@Override
					protected void onProgressUpdate(String... progress) {
						로딩.incrementProgressBy(1);
					}
					
					@Override
					protected void onPostExecute(String result) {
						super.onPostExecute(result);
						
						if (로딩성공) {
							try {
								if (로딩 != null && 로딩.isShowing())
									로딩.dismiss();
							} catch (Exception e) {
								e.printStackTrace();
							}
							try {
								화면표시();
							} catch (ArithmeticException | NullPointerException e) {
								e.printStackTrace();
							}
						} else {
							Toast.makeText(Play.this, 언어(R.string.outOfCPU), Toast.LENGTH_LONG).show();
							finish();
						}
					}
				}).execute();
			}
			
			
		} catch (OutOfMemoryError ignore) {
			Toast.makeText(Play.this, 언어(R.string.outOfMemory), Toast.LENGTH_SHORT).show();
			finish();
		}
	}
	
	void 화면표시() {
		//스킨초기화(0);
		
		int 가로;
		int 세로;
		
		if (프로젝트.정사각형버튼) {
			가로 = 세로 = Math.min(화면.패딩높이 / 프로젝트.가로축, 화면.패딩너비 / 프로젝트.세로축);
			
		} else {
			가로 = 화면.너비 / 프로젝트.세로축;
			세로 = 화면.높이 / 프로젝트.가로축;
		}
		
		
		((CheckBox) findViewById(R.id.누른키표시)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				터치초기화();
				누른키표시 = isChecked;
			}
		});
		((CheckBox) findViewById(R.id.LED효과)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (프로젝트.keyLED여부) {
					LED효과 = isChecked;
					if (!LED효과)
						LED초기화();
				}
			}
		});
		((CheckBox) findViewById(R.id.자동재생)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					자동재생쓰레드 = new 자동재생쓰레드();
					try {
						자동재생쓰레드.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					} catch (Exception e) {
						buttonView.setChecked(false);
						e.printStackTrace();
					}
				} else {
					자동재생쓰레드.loop = false;
					터치초기화();
					LED초기화();
					연습모드표시제거();
				}
			}
		});
		((CheckBox) findViewById(R.id.순서기록)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				순서기록 = isChecked;
			}
		});
		((CheckBox) findViewById(R.id.순서기록)).setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				
				순서기록초기화();
				Toast.makeText(Play.this, 언어(R.string.recOrderClear), Toast.LENGTH_SHORT).show();
				return false;
			}
		});
		((CheckBox) findViewById(R.id.녹음)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				녹음 = isChecked;
				if (녹음) {
					이전이밴트ms = System.currentTimeMillis();
					로그내용 = "c " + (체인 + 1);
				} else {
					클립보드에넣기(로그내용);
					Toast.makeText(Play.this, 언어(R.string.copied), Toast.LENGTH_SHORT).show();
					로그내용 = "";
				}
			}
		});
		findViewById(R.id.prev).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				자동재생_앞으로();
			}
		});
		findViewById(R.id.play).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (자동재생쓰레드.재생중)
					자동재생_정지();
				else
					자동재생_재생();
			}
		});
		findViewById(R.id.next).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				자동재생_뒤로();
			}
		});
		
		LinearLayout LL_패드 = (LinearLayout) findViewById(R.id.패드);
		LinearLayout LL_체인 = (LinearLayout) findViewById(R.id.체인);
		LL_패드.removeAllViews();
		LL_체인.removeAllViews();
		
		
		for (int i = 0; i < 프로젝트.가로축; i++) {
			LinearLayout row = new LinearLayout(this);
			row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
			
			for (int j = 0; j < 프로젝트.세로축; j++) {
				final RelativeLayout 버튼 = (RelativeLayout) View.inflate(this, R.layout.button, null);
				버튼.setLayoutParams(new LinearLayout.LayoutParams(가로, 세로));
				
				
				final int finalI = i;
				final int finalJ = j;
				/*버튼.setOnHoverListener(new View.OnHoverListener() {
					@Override
					public boolean onHover(View v, MotionEvent event) {
						switch (event.getAction()) {
							case MotionEvent.ACTION_HOVER_ENTER:
								패드터치(finalI, finalJ, true);
								break;
							case MotionEvent.ACTION_HOVER_MOVE:
								패드터치(finalI, finalJ, true);
								break;
							case MotionEvent.ACTION_HOVER_EXIT:
								패드터치(finalI, finalJ, false);
								break;
						}
						return false;
					}
				});*/
				버튼.setOnTouchListener(new View.OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {
						//화면.log(event.getAction() + "");
						switch (event.getAction()) {
							case MotionEvent.ACTION_DOWN:
								패드터치(finalI, finalJ, true);
								break;
							case MotionEvent.ACTION_UP:
								패드터치(finalI, finalJ, false);
								break;
						}
						return false;
					}
				});
				RL_버튼들[i][j] = 버튼;
				row.addView(버튼);
			}
			LL_패드.addView(row);
		}
		
		if (프로젝트.체인 > 1) {
			for (int i = 0; i < 프로젝트.체인; i++) {
				final RelativeLayout 버튼 = (RelativeLayout) View.inflate(this, R.layout.chain, null);
				버튼.setLayoutParams(new RelativeLayout.LayoutParams(가로, 세로));
				
				final int finalI = i;
				버튼.findViewById(R.id.버튼).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						체인변경(finalI);
					}
				});
				
				IV_체인들[i] = (ImageView) 버튼.findViewById(R.id.버튼);
				LL_체인.addView(버튼);
			}
		}
		체인변경(체인);
		
		Launchpad.데이터수신.setGetSignalListener(new Launchpad.데이터수신.getSignalListener() {
			@Override
			public void getSignal(int command, int note, int velocity) {
				
				
				if (Launchpad.런치패드기종 == Launchpad.S) {
					if (command == 9 && velocity != 0) {
						int x = note / 16 + 1;
						int y = note % 16 + 1;
						if (y >= 1 && y <= 8) {
							패드터치(x - 1, y - 1, true);
						} else if (y == 9) {
							if (프로젝트.체인 > x - 1)
								체인변경(x - 1);
						}
					} else if (command == 9 && velocity == 0) {
						int x = note / 16 + 1;
						int y = note % 16 + 1;
						if (y >= 1 && y <= 8) {
							패드터치(x - 1, y - 1, false);
						}
					} else if (command == 11) {
					}
				} else if (Launchpad.런치패드기종 == Launchpad.MK2) {
					if (command == 9 && velocity != 0) {
						int x = 9 - (note / 10);
						int y = note % 10;
						if (y >= 1 && y <= 8) {
							패드터치(x - 1, y - 1, true);
						} else if (y == 9) {
							if (프로젝트.체인 > x - 1)
								체인변경(x - 1);
						}
					} else if (command == 9 && velocity == 0) {
						int x = 9 - (note / 10);
						int y = note % 10;
						if (y >= 1 && y <= 8) {
							패드터치(x - 1, y - 1, false);
						}
					} else if (command == 11) {
					}
				} else if (Launchpad.런치패드기종 == Launchpad.Pro) {
					if (command == 9 && velocity != 0) {
						int x = 9 - (note / 10);
						int y = note % 10;
						if (y >= 1 && y <= 8) {
							패드터치(x - 1, y - 1, true);
						}
					} else if (command == 9 && velocity == 0) {
						int x = 9 - (note / 10);
						int y = note % 10;
						if (y >= 1 && y <= 8) {
							패드터치(x - 1, y - 1, false);
						}
					} else if (command == 11 && velocity != 0) {
						int x = 9 - (note / 10);
						int y = note % 10;
						if (y == 9) {
							if (프로젝트.체인 > x - 1)
								체인변경(x - 1);
						}
					}
				} else if (Launchpad.런치패드기종 == Launchpad.Piano) {
					
					int x;
					int y;
					
					if (command == 9 && velocity != 0) {
						if (note >= 36 && note <= 67) {
							x = (67 - note) / 4 + 1;
							y = 4 - (67 - note) % 4;
							패드터치(x - 1, y - 1, true);
						} else if (note >= 68 && note <= 99) {
							x = (99 - note) / 4 + 1;
							y = 8 - (99 - note) % 4;
							패드터치(x - 1, y - 1, true);
						}
						
					} else if (velocity == 0) {
						if (note >= 36 && note <= 67) {
							x = (67 - note) / 4 + 1;
							y = 4 - (67 - note) % 4;
							패드터치(x - 1, y - 1, false);
						} else if (note >= 68 && note <= 99) {
							x = (99 - note) / 4 + 1;
							y = 8 - (99 - note) % 4;
							패드터치(x - 1, y - 1, false);
						}
					}
				}
				
			}
		});
		
		스킨설정();
		
		Launchpad.setConnectListener(new Launchpad.connectListener() {
			@Override
			public void connect() {
				(new Handler()).postDelayed(new Runnable() {
					@Override
					public void run() {
						체인변경(체인);
					}
				}, 3000);
			}
		});
		
	}
	
	class LED쓰레드 extends AsyncTask<String, Integer, String> {
		
		ArrayList<LED이벤트> 이벤트목록;
		boolean 작동중 = true;
		LED[][] LED = new LED[프로젝트.가로축][프로젝트.세로축];
		
		public LED쓰레드() {
			이벤트목록 = new ArrayList<>();
		}
		
		class LED이벤트 {
			boolean 정상적인이벤트 = false;
			
			ArrayList<정보.uni.LED이벤트.요소> LED이벤트목록;
			int 실행할이벤트 = 0;
			long 딜레이 = -1;
			
			int x;
			int y;
			boolean 실행중 = true;
			boolean 강제종료 = false;
			int 반복횟수;
			int 반복한횟수 = 0;
			
			public LED이벤트(int x, int y) {
				this.x = x;
				this.y = y;
				
				정보.uni.LED이벤트 LED이벤트 = LED이벤트가져오기(체인, x, y);
				LED이벤트밀기(체인, x, y);
				if (LED이벤트 != null) {
					LED이벤트목록 = LED이벤트.LED;
					반복횟수 = LED이벤트.반복;
					정상적인이벤트 = true;
				}
			}
			
			boolean equal(int x, int y) {
				return (this.x == x) && (this.y == y);
			}
		}
		
		LED이벤트 이벤트검색(int x, int y) {
			LED이벤트 res = null;
			try {
				for (int i = 0; i < 이벤트목록.size(); i++) {
					LED이벤트 e = 이벤트목록.get(i);
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
		
		boolean 이벤트존재(int x, int y) {
			return 이벤트검색(x, y) != null;
		}
		
		void 이벤트추가(int x, int y) {
			if (LED쓰레드.이벤트존재(x, y)) {
				LED이벤트 e = LED쓰레드.이벤트검색(x, y);
				e.강제종료 = true;
			}
			LED이벤트 e = new LED이벤트(x, y);
			if (e.정상적인이벤트)
				이벤트목록.add(e);
			
			//화면.log("LED 추가 (" + x + ", " + y + ")");
		}
		
		void 이벤트강제종료(int x, int y) {
			이벤트검색(x, y).강제종료 = true;
		}
		
		class LED {
			int 변경요소x;
			int 변경요소y;
			int x;
			int y;
			int 색;
			int 벨로시티;
			
			public LED(int 변경요소x, int 변경요소y, int x, int y, int 색, int 벨로시티) {
				this.변경요소x = 변경요소x;
				this.변경요소y = 변경요소y;
				this.x = x;
				this.y = y;
				this.색 = 색;
				this.벨로시티 = 벨로시티;
			}
			
			public LED(int 변경요소x, int 변경요소y, int x, int y, int 색) {
				this.변경요소x = 변경요소x;
				this.변경요소y = 변경요소y;
				this.x = x;
				this.y = y;
				this.색 = 색;
				this.벨로시티 = -1;
			}
			
			boolean equal(int x, int y) {
				return (this.변경요소x == x) && (this.변경요소y == y);
			}
		}
		
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}
		
		@Override
		protected String doInBackground(String... params) {
			
			while (작동중) {
				long 현재시간 = System.currentTimeMillis();
				
				for (int i = 0; i < 이벤트목록.size(); i++) {
					LED이벤트 e = 이벤트목록.get(i);
					
					//try {
					if (e != null && e.실행중 && !e.강제종료) {
						if (e.딜레이 == -1)
							e.딜레이 = 현재시간;
						
						for (; ; e.실행할이벤트++) {
							
							if (e.실행할이벤트 >= e.LED이벤트목록.size()) {
								e.반복한횟수++;
								e.실행할이벤트 = 0;
							}
							
							if (e.반복횟수 != 0 && e.반복횟수 <= e.반복한횟수) {
								e.실행중 = false;
								break;
							}
							
							if (e.딜레이 <= 현재시간) {
								
								
								정보.uni.LED이벤트.요소 LED이벤트 = e.LED이벤트목록.get(e.실행할이벤트);
								
								int 기능 = LED이벤트.기능;
								int x = LED이벤트.x;
								int y = LED이벤트.y;
								int color = LED이벤트.color;
								int velo = LED이벤트.velo;
								int delay = LED이벤트.delay;
								
								
								try {
									switch (기능) {
										case 정보.uni.LED이벤트.요소.켜기:
											색설정Launchpad(x, y, 색관리.add(x, y, 색관리.LED, color, velo));
											publishProgress(1, x, y, color, velo);
											LED[x][y] = new LED(e.x, e.y, x, y, color, velo);
											
											break;
										case 정보.uni.LED이벤트.요소.끄기:
											if (LED[x][y] != null && LED[x][y].equal(e.x, e.y)) {
												색설정Launchpad(x, y, 색관리.remove(x, y, 색관리.LED));
												publishProgress(2, x, y);
												LED[x][y] = null;
											}
											
											break;
										case 정보.uni.LED이벤트.요소.딜레이:
											e.딜레이 += delay;
											break;
									}
								} catch (ArrayIndexOutOfBoundsException ee) {
									ee.printStackTrace();
								}
								
							} else
								break;
						}
						
						
					} else if (e == null) {
						이벤트목록.remove(i);
						화면.log("LED 오류 e == null");
					} else if (e.강제종료) {
						for (int i_ = 0; i_ < 프로젝트.가로축; i_++) {
							for (int j_ = 0; j_ < 프로젝트.세로축; j_++) {
								if (LED[i_][j_] != null && LED[i_][j_].equal(e.x, e.y)) {
									색설정Launchpad(i_, j_, 색관리.remove(i_, j_, 색관리.LED));
									publishProgress(2, i_, j_);
									LED[i_][j_] = null;
								}
							}
						}
						이벤트목록.remove(i);
					} else if (!e.실행중) {
						이벤트목록.remove(i);
					}
				}
				
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			
			return null;
		}
		
		@Override
		protected void onProgressUpdate(Integer... p) {
			try {
				색설정(p[1], p[2], 색관리.get(p[1], p[2]));
				/*switch (p[0]) {
					case 1:
						RL_버튼들[p[1]][p[2]].findViewById(R.id.LED).setBackgroundColor(p[3]);
						break;
					case 2:
						RL_버튼들[p[1]][p[2]].findViewById(R.id.LED).setBackgroundColor(0);
						break;
				}*/
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		@Override
		protected void onPostExecute(String result) {
		}
	}
	
	class 자동재생쓰레드 extends AsyncTask<String, Integer, String> {
		
		boolean loop = true;
		boolean 재생중 = false;
		int 진행도 = 0;
		
		
		ArrayList<정보.uni.자동재생요소> 연습할요소 = new ArrayList<>();
		int 달성 = 0;
		
		public 자동재생쓰레드() {
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			if (프로젝트.정사각형버튼)
				findViewById(R.id.자동재생제어뷰).setVisibility(View.VISIBLE);
			((ProgressBar) findViewById(R.id.진행도)).setMax(프로젝트.자동재생.size());
			((ProgressBar) findViewById(R.id.진행도)).setProgress(0);
			자동재생_재생();
		}
		
		@Override
		protected String doInBackground(String... params) {
			
			long 딜레이 = 0;
			long 처음시간 = System.currentTimeMillis();
			
			while (진행도 < 프로젝트.자동재생.size() && loop) {
				long 지금시간 = System.currentTimeMillis();
				
				if (재생중) {
					if (딜레이 <= 지금시간 - 처음시간) {
						정보.uni.자동재생요소 요소 = 프로젝트.자동재생.get(진행도);
						
						
						int 기능 = 요소.기능;
						int 체인기록 = 요소.체인기록;
						int 번호 = 요소.번호;
						int x = 요소.x;
						int y = 요소.y;
						int c = 요소.c;
						int d = 요소.d;
						
						switch (기능) {
							case 정보.uni.자동재생요소.켜기:
								if (체인 != 체인기록)
									publishProgress(3, 체인기록);
								소리요소밀기(체인기록, x, y, 번호);
								LED이벤트밀기(체인기록, x, y, 번호);
								publishProgress(1, x, y);
								break;
							case 정보.uni.자동재생요소.끄기:
								if (체인 != 체인기록)
									publishProgress(3, 체인기록);
								publishProgress(2, x, y);
								break;
							case 정보.uni.자동재생요소.체인:
								publishProgress(3, c);
								break;
							case 정보.uni.자동재생요소.딜레이:
								딜레이 += d;
								break;
						}
						진행도++;
					}
					
				} else {
					if (딜레이 <= 지금시간 - 처음시간)
						딜레이 = 지금시간 - 처음시간;
					
					if (달성 >= 연습할요소.size() || 달성 == -1) {
						달성 = 0;
						
						
						for (int i = 0; i < 연습할요소.size(); i++) {
							정보.uni.자동재생요소 요소 = 연습할요소.get(i);
							int 기능 = 요소.기능;
							int 체인기록 = 요소.체인기록;
							int 번호 = 요소.번호;
							int x = 요소.x;
							int y = 요소.y;
							int c = 요소.c;
							int d = 요소.d;
							
							switch (기능) {
								case 정보.uni.자동재생요소.켜기:
									publishProgress(6, x, y);
									break;
								case 정보.uni.자동재생요소.체인:
									publishProgress(7, c);
									break;
							}
						}
						
						연습할요소.clear();
						
						int 누적딜레이 = 0;
						boolean 등록됨 = false;
						
						for (; 진행도 < 프로젝트.자동재생.size() && (누적딜레이 <= 20 || !등록됨); 진행도++) {
							정보.uni.자동재생요소 요소 = 프로젝트.자동재생.get(진행도);
							
							int 기능 = 요소.기능;
							int 체인기록 = 요소.체인기록;
							int 번호 = 요소.번호;
							int x = 요소.x;
							int y = 요소.y;
							int c = 요소.c;
							int d = 요소.d;
							
							
							if (기능 == 정보.uni.자동재생요소.켜기 || 기능 == 정보.uni.자동재생요소.체인 || 기능 == 정보.uni.자동재생요소.딜레이) {
								
								switch (기능) {
									case 정보.uni.자동재생요소.켜기:
										소리요소밀기(체인기록, x, y, 번호);
										LED이벤트밀기(체인기록, x, y, 번호);
										publishProgress(4, x, y);
										등록됨 = true;
										break;
									case 정보.uni.자동재생요소.체인:
										publishProgress(5, c);
										등록됨 = true;
										break;
									case 정보.uni.자동재생요소.딜레이:
										if (등록됨)
											누적딜레이 += d;
										break;
								}
								if (기능 == 정보.uni.자동재생요소.켜기 || 기능 == 정보.uni.자동재생요소.체인)
									연습할요소.add(요소);
							}
						}
					}
				}
				
				
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			return null;
		}
		
		@Override
		protected void onProgressUpdate(Integer... progress) {
			if (progress[0] == 1) {
				패드터치(progress[1], progress[2], true);
			} else if (progress[0] == 2) {
				패드터치(progress[1], progress[2], false);
			} else if (progress[0] == 3) {
				체인변경(progress[1]);
			} else if (progress[0] == 4) {
				패드표시(progress[1], progress[2], true);
			} else if (progress[0] == 5) {
				체인표시(progress[1], true);
			} else if (progress[0] == 6) {
				패드표시(progress[1], progress[2], false);
			} else if (progress[0] == 7) {
				체인표시(progress[1], false);
			}
			((ProgressBar) findViewById(R.id.진행도)).setProgress(진행도);
		}
		
		@Override
		protected void onPostExecute(String result) {
			((CheckBox) findViewById(R.id.자동재생)).setChecked(false);
			if (프로젝트.keyLED여부) {
				((CheckBox) findViewById(R.id.LED효과)).setChecked(true);
				((CheckBox) findViewById(R.id.누른키표시)).setChecked(false);
			} else {
				((CheckBox) findViewById(R.id.누른키표시)).setChecked(true);
			}
			findViewById(R.id.자동재생제어뷰).setVisibility(View.GONE);
		}
		
	}
	
	void 자동재생_재생() {
		터치초기화();
		LED초기화();
		연습모드표시제거();
		
		자동재생쓰레드.재생중 = true;
		findViewById(R.id.play).setBackground(theme.xml_pause);
		
		if (프로젝트.keyLED여부) {
			((CheckBox) findViewById(R.id.LED효과)).setChecked(true);
			((CheckBox) findViewById(R.id.누른키표시)).setChecked(false);
		} else {
			((CheckBox) findViewById(R.id.누른키표시)).setChecked(true);
		}
	}
	
	void 자동재생_정지() {
		자동재생쓰레드.재생중 = false;
		
		터치초기화();
		LED초기화();
		
		findViewById(R.id.play).setBackground(theme.xml_play);
		
		
		자동재생쓰레드.달성 = -1;
		
		((CheckBox) findViewById(R.id.누른키표시)).setChecked(false);
		((CheckBox) findViewById(R.id.LED효과)).setChecked(false);
	}
	
	void 자동재생_앞으로() {
		터치초기화();
		LED초기화();
		int 진행도 = 자동재생쓰레드.진행도 - 40;
		if (진행도 < 0) 진행도 = 0;
		자동재생쓰레드.진행도 = 진행도;
		if (!자동재생쓰레드.재생중)
			자동재생_재생();
		((ProgressBar) findViewById(R.id.진행도)).setProgress(자동재생쓰레드.진행도);
	}
	
	void 자동재생_뒤로() {
		터치초기화();
		LED초기화();
		자동재생쓰레드.진행도 += 40;
		if (!자동재생쓰레드.재생중)
			자동재생_재생();
		((ProgressBar) findViewById(R.id.진행도)).setProgress(자동재생쓰레드.진행도);
	}
	
	void 연습모드표시제거() {
		//화면.log("연습모드표시제거");
		try {
			for (int i = 0; i < 프로젝트.가로축; i++) {
				for (int j = 0; j < 프로젝트.세로축; j++) {
					
					색설정(i, j, 색관리.remove(i, j, 색관리.연습모드));
				}
			}
			체인초기화();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	void 연습모드달성체크(int x, int y) {
		
		if (자동재생쓰레드 != null && 자동재생쓰레드.loop && !자동재생쓰레드.재생중) {
			
			ArrayList<정보.uni.자동재생요소> 연습할요소 = 자동재생쓰레드.연습할요소;
			if (연습할요소 != null) {
				for (정보.uni.자동재생요소 요소 : 연습할요소) {
					int 기능_ = 요소.기능;
					int 체인기록_ = 요소.체인기록;
					int x_ = 요소.x;
					int y_ = 요소.y;
					int c_ = 요소.c;
					int d_ = 요소.d;
					
					if (기능_ == 정보.uni.자동재생요소.켜기 && x == x_ && y == y_ && 체인기록_ == 체인) {
						자동재생쓰레드.달성++;
						break;
					}
				}
			}
		}
	}
	
	void 연습모드달성체크(int c) {
		if (자동재생쓰레드 != null && 자동재생쓰레드.loop && !자동재생쓰레드.재생중) {
			ArrayList<정보.uni.자동재생요소> 연습할요소 = 자동재생쓰레드.연습할요소;
			if (연습할요소 != null) {
				for (정보.uni.자동재생요소 요소 : 자동재생쓰레드.연습할요소) {
					int 기능_ = 요소.기능;
					int 체인기록_ = 요소.체인기록;
					int x_ = 요소.x;
					int y_ = 요소.y;
					int c_ = 요소.c;
					int d_ = 요소.d;
					
					if (기능_ == 정보.uni.자동재생요소.체인 && c == c_) {
						자동재생쓰레드.달성++;
						break;
					}
				}
			}
		}
	}
	
	void 패드터치(int x, int y, boolean 누르기때기) {
		//화면.log("패드터치 (" + x + ", " + y + ", " + 누르기때기 + ")");
		try {
			if (누르기때기) {
				//vibrator.vibrate(10);
				
				소리.stop(정지아이디[체인][x][y]);
				정보.uni.소리요소 e = 소리요소가져오기(체인, x, y);
				정지아이디[체인][x][y] = 소리.play(e.아이디, 1.0F, 1.0F, 0, e.반복, 1.0F);//ID, leftVolum, rightVolum, 우선순위, 루프, 재생속도
				
				소리요소밀기(체인, x, y);
				
				if (녹음) {
					long 현재ms = System.currentTimeMillis();
					로그("d " + (현재ms - 이전이밴트ms));
					로그("o " + (x + 1) + " " + (y + 1));
					이전이밴트ms = 현재ms;
				}
				if (순서기록) {
					순서기록하기(x, y);
				}
				
				if (누른키표시)
					색설정(x, y, 색관리.add(x, y, 색관리.누른키, Launchpad.색코드[119] + 0xFF000000, 119));
				
				if (LED효과) {
					LED쓰레드.이벤트추가(x, y);
				}
				
				연습모드달성체크(x, y);
			} else {
				if (소리요소가져오기(체인, x, y).반복 == -1)
					소리.stop(정지아이디[체인][x][y]);
				
				if (녹음) {
					long 현재ms = System.currentTimeMillis();
					로그("d " + (현재ms - 이전이밴트ms));
					로그("f " + (x + 1) + " " + (y + 1));
					이전이밴트ms = 현재ms;
				}
				
				if (누른키표시)
					색설정(x, y, 색관리.remove(x, y, 색관리.누른키));
				
				if (LED효과) {
					LED쓰레드.LED이벤트 이벤트 = LED쓰레드.이벤트검색(x, y);
					
					if (이벤트 != null && 이벤트.반복횟수 == 0) {
						LED쓰레드.이벤트강제종료(x, y);
					}
				}
			}
		} catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
			e.printStackTrace();
		}
	}
	
	void 체인변경(int 체인번호) {
		화면.log("체인변경 (" + 체인번호 + ")");
		try {
			if (프로젝트.체인 > 1 && 체인번호 >= 0 && 체인번호 < 프로젝트.체인) {
				체인 = 체인번호;
				체인초기화();
			}
			
			for (int i = 0; i < 프로젝트.가로축; i++)
				for (int j = 0; j < 프로젝트.세로축; j++) {
					소리요소밀기(체인, i, j, 0);
					LED이벤트밀기(체인, i, j, 0);
				}
			
			if (녹음) {
				long 현재ms = System.currentTimeMillis();
				로그("d " + (현재ms - 이전이밴트ms));
				로그("chain " + (체인 + 1));
				이전이밴트ms = 현재ms;
			}
			순서기록표시();
			연습모드달성체크(체인번호);
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}
	}
	
	void 패드표시(int x, int y, boolean 켜기끄기) {
		//화면.log("패드표시 (" + x + ", " + y + ", " + 켜기끄기 + ")");
		if (켜기끄기)
			색설정(x, y, 색관리.add(x, y, 색관리.연습모드, Launchpad.색코드[63] + 0xFF000000, 63));
		else
			색설정(x, y, 색관리.remove(x, y, 색관리.연습모드));
	}
	
	void 체인표시(int c, boolean 켜기끄기) {
		//화면.log("체인표시 (" + c + ", " + 켜기끄기 + ")");
		if (켜기끄기) {
			IV_체인들[c].setBackground(theme.chain__);
			Launchpad.런치패드체인LED(c, 63);
		} else {
			체인초기화();
		}
	}
	
	void LED초기화() {
		화면.log("LED초기화");
		if (프로젝트.keyLED여부) {
			try {
				for (int i = 0; i < 프로젝트.가로축; i++) {
					for (int j = 0; j < 프로젝트.세로축; j++) {
						
						if (LED쓰레드.이벤트존재(i, j))
							LED쓰레드.이벤트강제종료(i, j);
						
						색설정(i, j, 색관리.remove(i, j, 색관리.LED));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	void 터치초기화() {
		화면.log("터치초기화");
		for (int i = 0; i < 프로젝트.가로축; i++)
			for (int j = 0; j < 프로젝트.세로축; j++)
				패드터치(i, j, false);
	}
	
	void 체인초기화() {
		화면.log("체인초기화");
		if (프로젝트.체인 > 1) {
			for (int i = 0; i < 프로젝트.체인; i++) {
				if (i == 체인)
					IV_체인들[i].setBackground(theme.chain_);
				else
					IV_체인들[i].setBackground(theme.chain);
			}
			Launchpad.런치패드체인초기화(체인);
		}
	}
	
	void 순서기록표시() {
		//화면.log("순서기록표시");
		for (int i = 0; i < 프로젝트.가로축; i++) {
			for (int j = 0; j < 프로젝트.세로축; j++) {
				((TextView) RL_버튼들[i][j].findViewById(R.id.순서)).setText("");
				for (int k = 0; k < 순서기록표[체인][i][j].size(); k++)
					((TextView) RL_버튼들[i][j].findViewById(R.id.순서)).append(순서기록표[체인][i][j].get(k) + " ");
			}
		}
	}
	
	void 순서기록표시(int x, int y) {
		//화면.log("순서기록표시 (" + x + ", " + y + ")");
		((TextView) RL_버튼들[x][y].findViewById(R.id.순서)).setText("");
		for (int i = 0; i < 순서기록표[체인][x][y].size(); i++)
			((TextView) RL_버튼들[x][y].findViewById(R.id.순서)).append(순서기록표[체인][x][y].get(i) + " ");
	}
	
	void 순서기록하기(int x, int y) {
		//화면.log("순서기록하기 (" + x + ", " + y + ")");
		순서기록표[체인][x][y].add(다음순서[체인]++);
		순서기록표시(x, y);
	}
	
	void 순서기록지우기() {
		//화면.log("순서기록지우기");
		for (int i = 0; i < 프로젝트.가로축; i++) {
			for (int j = 0; j < 프로젝트.세로축; j++) {
				((TextView) RL_버튼들[i][j].findViewById(R.id.순서)).setText("");
			}
		}
	}
	
	void 순서기록초기화() {
		//화면.log("순서기록초기화");
		for (int i = 0; i < 프로젝트.체인; i++) {
			for (int j = 0; j < 프로젝트.가로축; j++)
				for (int k = 0; k < 프로젝트.세로축; k++)
					순서기록표[i][j][k].clear();
			다음순서[i] = 1;
		}
		순서기록지우기();
	}
	
	void 순서기록초기설정() {
		//화면.log("순서기록초기설정");
		for (int i = 0; i < 프로젝트.체인; i++) {
			for (int j = 0; j < 프로젝트.가로축; j++)
				for (int k = 0; k < 프로젝트.세로축; k++)
					순서기록표[i][j][k] = new ArrayList<>();
			다음순서[i] = 1;
		}
	}
	
	void 로그(String 로그) {
		로그내용 += "\n" + 로그;
	}
	
	void 클립보드에넣기(String 내용) {
		ClipboardManager 클립보드 = (ClipboardManager) Play.this.getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData 클립 = ClipData.newPlainText("LABEL", 내용);
		클립보드.setPrimaryClip(클립);
	}
	
	long 백키 = 0;
	
	@Override
	public void onBackPressed() {
		if (백키 == 0 || System.currentTimeMillis() - 백키 > 2000) {
			Toast 토스트 = Toast.makeText(Play.this, 언어(R.string.pressAgainToGoBack), Toast.LENGTH_SHORT);
			토스트.setGravity(Gravity.RIGHT | Gravity.BOTTOM, 50, 50);
			토스트.show();
			백키 = System.currentTimeMillis();
		} else
			super.onBackPressed();
	}
	
	@Override
	protected void onResume() {
		//화면.log("onResume");
		super.onResume();
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
		wl.acquire();
		
		if (화면.너비 == 0 || 화면.높이 == 0 || 화면.패딩너비 == 0 || 화면.패딩높이 == 0) {
			화면.log("padding 크기값들이 잘못되었습니다.");
			restartApp(Play.this);
		}
	}
	
	@Override
	protected void onDestroy() {
		//화면.log("onDestroy");
		super.onDestroy();
		if (자동재생쓰레드 != null)
			자동재생쓰레드.loop = false;
		if (LED쓰레드 != null)
			LED쓰레드.작동중 = false;
		if (소리 != null) {
			for (int i = 0; i < 프로젝트.체인; i++)
				for (int j = 0; j < 프로젝트.가로축; j++)
					for (int k = 0; k < 프로젝트.세로축; k++)
						if (프로젝트.소리[i][j][k] != null) {
							try {
								소리.unload(소리요소가져오기(i, j, k).아이디);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
			소리.release();
			소리 = null;
		}
		
		Launchpad.데이터수신.setGetSignalListener(null);
		Launchpad.setConnectListener(null);
		Launchpad.런치패드체인초기화(-1);
		LED초기화();
		터치초기화();
		
		if (로딩성공)
			화면.광고(Play.this);
		
		finishActivity(this);
	}
	
	String 언어(int id) {
		return getResources().getString(id);
	}
}