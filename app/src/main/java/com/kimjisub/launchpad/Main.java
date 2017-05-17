package com.kimjisub.launchpad;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main extends BaseActivity {
	LinearLayout LL_목록;
	String 프로젝트폴더URL;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		
		LL_목록 = (LinearLayout) findViewById(R.id.목록);
		프로젝트폴더URL = 정보.설정.유니팩저장경로.URL;
		
		updateCheck();
		noticeCheck();
		
		findViewById(R.id.uni불러오기).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				uni불러오기();
			}
		});
		
		findViewById(R.id.스토어).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(Main.this, Store.class));
			}
		});
		
		findViewById(R.id.설정).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(Main.this, Setting.class));
			}
		});
		
		업데이트();
		
	}
	
	void updateCheck() {
		new 통신.업로드된버전(getPackageName()).setOnEndListener(new 통신.업로드된버전.onEndListener() {
			@Override
			public void onEnd(String 결과) {
				try {
					String 버전이름 = BuildConfig.VERSION_NAME;
					if (결과 != null && !버전이름.equals(결과)) {
						new AlertDialog.Builder(Main.this)
							.setTitle(언어(R.string.newVersionFound))
							.setMessage(언어(R.string.currentVersion) + " : " + 버전이름 + "\n" +
								언어(R.string.newVersion) + " : " + 결과)
							.setPositiveButton(언어(R.string.update), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
									dialog.dismiss();
								}
							})
							.setNegativeButton(언어(R.string.ignore), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									dialog.dismiss();
								}
							})
							.show();
					}
				} catch (Exception ignore) {
				}
			}
		}).실행();
	}
	
	void noticeCheck() {
		new 통신.공지사항(언어(R.string.language)).setOnEndListener(new 통신.공지사항.onEndListener() {
			@Override
			public void onEnd(final String 제목, final String 글) {
				if (제목 != null && 글 != null) {
					String 이전공지사항 = 정보.설정.이전공지사항.불러오기(Main.this);
					
					if (!이전공지사항.equals(글)) {
						
						TextView 내용 = new TextView(Main.this);
						화면.log(글);
						내용.setText(Html.fromHtml(글));
						int px1 = 화면.dpToPx(Main.this, 25);
						int px2 = 화면.dpToPx(Main.this, 15);
						내용.setPadding(px1, px2, px1, 0);
						내용.setTextColor(0xFF000000);
						내용.setLinkTextColor(0xffffaf00);
						내용.setHighlightColor(0xffffaf00);
						내용.setTextSize(16);
						내용.setClickable(true);
						내용.setMovementMethod(LinkMovementMethod.getInstance());
						
						LinearLayout 리니어 = new LinearLayout(Main.this);
						리니어.addView(내용);
						
						new AlertDialog.Builder(Main.this)
							.setTitle(제목)
							.setPositiveButton(언어(R.string.accept), null)
							.setNegativeButton(언어(R.string.doNotSee), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									정보.설정.이전공지사항.저장하기(Main.this, 글);
								}
							})
							.setCancelable(false)
							.setView(리니어)
							.show();
						
					}
				}
			}
		}).execute();
	}
	
	RelativeLayout[] RL_list;
	boolean[] statusPlay;
	boolean[] statusInfo;
	String[] URL;
	정보.uni uni[];
	
	void 업데이트() {
		LL_목록.removeAllViews();
		
		File 프로젝트폴더 = new File(프로젝트폴더URL);
		
		if (프로젝트폴더.isDirectory()) {
			
			File[] 프로젝트폴더리스트 = 파일.시간별정렬(프로젝트폴더.listFiles());
			int num = 프로젝트폴더리스트.length;
			
			RL_list = new RelativeLayout[num];
			statusPlay = new boolean[num];
			statusInfo = new boolean[num];
			URL = new String[num];
			uni = new 정보.uni[num];
			
			int 파일개수 = 0;
			for (int i = 0; i < num; i++) {
				File 프로젝트폴더_ = 프로젝트폴더리스트[i];
				if (프로젝트폴더_.isFile())
					continue;
				파일개수++;
				
				URL[i] = 프로젝트폴더URL + "/" + 프로젝트폴더_.getName();
				uni[i] = new 정보.uni(URL[i], false);
				
				RelativeLayout 항목 = (RelativeLayout) View.inflate(Main.this, R.layout.list_item, null);
				
				((TextView) 항목.findViewById(R.id.제목)).setText(uni[i].제목);
				((TextView) 항목.findViewById(R.id.제작자)).setText(uni[i].제작자);
				
				((TextView) 항목.findViewById(R.id.size)).setText(uni[i].가로축 + " x " + uni[i].세로축);
				((TextView) 항목.findViewById(R.id.chain)).setText(uni[i].체인 + "");
				((TextView) 항목.findViewById(R.id.capacity)).setText(String.format("%.2f", (float) 파일.폴더크기(URL[i]) / 1024L / 1024L) + " MB");
				
				if (uni[i].keyLED여부)
					((TextView) 항목.findViewById(R.id.LED)).setTextColor(getResources().getColor(R.color.green));
				if (uni[i].autoPlay여부)
					((TextView) 항목.findViewById(R.id.자동재생)).setTextColor(getResources().getColor(R.color.green));
				
				항목.findViewById(R.id.제목제작자).setX(화면.dpToPx(getApplicationContext(), 10));
				
				final int finalI = i;
				항목.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						clearPlay(finalI);
						clearInfo();
						toglePlay(finalI);
					}
				});
				항목.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						clearPlay();
						clearInfo(finalI);
						togleinfo(finalI);
						return true;
					}
				});
				
				
				RL_list[i] = 항목;
				LL_목록.addView(항목);
			}
			
			if (파일개수 == 0) {
				View v = View.inflate(Main.this, R.layout.list_item_not_exist, null);
				v.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(Main.this, Store.class));
					}
				});
				v.findViewById(R.id.제목제작자).setX(화면.dpToPx(getApplicationContext(), 10));
				LL_목록.addView(v);
			}
			
		} else {
			프로젝트폴더.mkdir();
			View v = View.inflate(Main.this, R.layout.list_item_not_exist, null);
			v.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(Main.this, Store.class));
				}
			});
			v.findViewById(R.id.제목제작자).setX(화면.dpToPx(getApplicationContext(), 10));
			LL_목록.addView(v);
		}
		
		File nomedia = new File(프로젝트폴더URL + "/.nomedia");
		if (!nomedia.isFile()) {
			try {
				(new FileWriter(nomedia)).close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		화면.log(프로젝트폴더URL);
	}
	
	void toglePlay(final int i) {
		RelativeLayout 항목 = RL_list[i];
		if (!statusPlay[i]) {
			//animation
			항목.findViewById(R.id.제목제작자).animate().x(화면.dpToPx(getApplicationContext(), 100)).setDuration(500).start();
			항목.findViewById(R.id.play).animate().alpha(0).setDuration(500).start();
			
			//clickEvent
			항목.findViewById(R.id.playbtn).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent 인텐트 = new Intent(Main.this, Play.class);
					인텐트.putExtra("URL", URL[i]);
					startActivity(인텐트);
				}
			});
		} else {
			//animation
			항목.findViewById(R.id.제목제작자).animate().x(화면.dpToPx(getApplicationContext(), 10)).setDuration(500).start();
			항목.findViewById(R.id.play).animate().alpha(1).setDuration(500).start();
			
			//clickEvent
			항목.findViewById(R.id.playbtn).setOnClickListener(null);
			항목.findViewById(R.id.playbtn).setClickable(false);
		}
		
		statusPlay[i] = !statusPlay[i];
	}
	
	void togleinfo(final int i) {
		final RelativeLayout 항목 = RL_list[i];
		final int px = 화면.dpToPx(getApplicationContext(), 30);
		final int px2 = 화면.dpToPx(getApplicationContext(), 35);
		if (!statusInfo[i]) {
			//animation
			Animation a = new Animation() {
				@Override
				protected void applyTransformation(float interpolatedTime, Transformation t) {
					RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) 항목.findViewById(R.id.info).getLayoutParams();
					params.topMargin = px + (int) (px2 * interpolatedTime);
					항목.findViewById(R.id.info).setLayoutParams(params);
				}
			};
			a.setDuration(500);
			항목.findViewById(R.id.info).startAnimation(a);
			
			//clickEvent
			항목.findViewById(R.id.delete).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					new AlertDialog.Builder(Main.this)
						.setTitle(uni[i].제목)
						.setMessage(언어(R.string.doYouWantToDeleteProject))
						.setPositiveButton(언어(R.string.cancel), null)
						.setNegativeButton(언어(R.string.delete), new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								파일.폴더삭제(URL[i]);
								업데이트();
							}
						})
						.show();
				}
			});
		} else {
			//animation
			Animation a = new Animation() {
				@Override
				protected void applyTransformation(float interpolatedTime, Transformation t) {
					RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) 항목.findViewById(R.id.info).getLayoutParams();
					params.topMargin = px + px2 + (int) (-px2 * interpolatedTime);
					항목.findViewById(R.id.info).setLayoutParams(params);
				}
			};
			a.setDuration(500);
			항목.findViewById(R.id.info).startAnimation(a);
			
			//clickEvent
			항목.findViewById(R.id.delete).setOnClickListener(null);
			항목.findViewById(R.id.delete).setClickable(false);
		}
		
		statusInfo[i] = !statusInfo[i];
	}
	
	void clearPlay() {
		for (int i = 0; i < statusPlay.length; i++)
			if (statusPlay[i])
				toglePlay(i);
	}
	
	void clearPlay(int e) {
		for (int i = 0; i < statusPlay.length; i++)
			if (statusPlay[i] && i != e)
				toglePlay(i);
	}
	
	void clearInfo() {
		for (int i = 0; i < statusInfo.length; i++)
			if (statusInfo[i])
				togleinfo(i);
	}
	
	void clearInfo(int e) {
		for (int i = 0; i < statusInfo.length; i++)
			if (statusInfo[i] && i != e)
				togleinfo(i);
	}
	
	
	List<String> mItem;
	List<String> mPath;
	TextView TV_경로;
	ListView LV_리스트;
	
	
	void uni불러오기() {
		final AlertDialog 파일탐색기 = (new AlertDialog.Builder(Main.this)).create();
		LinearLayout LL_파일탐색기 = (LinearLayout) View.inflate(Main.this, R.layout.file_explore, null);
		TV_경로 = (TextView) LL_파일탐색기.findViewById(R.id.경로);
		LV_리스트 = (ListView) LL_파일탐색기.findViewById(R.id.리스트);
		
		String 경로 = 정보.설정.유니팩불러오기경로.불러오기(Main.this);
		
		
		LV_리스트.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final File file = new File(mPath.get(position));
				if (file.isDirectory()) {
					if (file.canRead())
						getDir(mPath.get(position));
					else
						다이얼로그(file.getName(), 언어(R.string.cantReadFolder));
				} else {
					if (file.canRead()) {
						(new AsyncTask<String, String, String>() {
							
							ProgressDialog 로딩 = new ProgressDialog(Main.this);
							
							@Override
							protected void onPreExecute() {
								
								파일탐색기.dismiss();
								로딩.setTitle(언어(R.string.analyzing));
								로딩.setMessage(언어(R.string.wait));
								로딩.setCancelable(false);
								로딩.show();
								super.onPreExecute();
							}
							
							@Override
							protected String doInBackground(String... params) {
								
								String 경로 = 프로젝트폴더URL + "/" + 파일.랜덤문자(10) + "/";
								
								try {
									파일.unZipFile(file.getPath(), 경로);
									정보.uni 프로젝트 = new 정보.uni(경로, true);
									
									if (프로젝트.에러내용 == null) {
										publishProgress(언어(R.string.analyzeComplete),
											언어(R.string.title) + " : " + 프로젝트.제목 + "\n" +
												언어(R.string.producerName) + " : " + 프로젝트.제작자 + "\n" +
												언어(R.string.scale) + " : " + 프로젝트.가로축 + " x " + 프로젝트.세로축 + "\n" +
												언어(R.string.chainCount) + " : " + 프로젝트.체인 + "\n" +
												언어(R.string.capacity) + " : " + String.format("%.2f", (float) 파일.폴더크기(경로) / 1024L / 1024L) + " MB");
									} else if (프로젝트.치명적인에러) {
										publishProgress(언어(R.string.analyzeFailed), 프로젝트.에러내용);
										파일.폴더삭제(경로);
									} else {
										publishProgress(언어(R.string.warning), 프로젝트.에러내용);
									}
									
								} catch (IOException e) {
									publishProgress(언어(R.string.analyzeFailed), e.toString());
									파일.폴더삭제(경로);
								}
								
								return null;
							}
							
							@Override
							protected void onProgressUpdate(String... progress) {
								다이얼로그(progress[0], progress[1]);
							}
							
							@Override
							protected void onPostExecute(String result) {
								로딩.dismiss();
								업데이트();
								super.onPostExecute(result);
							}
						}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
						
						
					} else if (file.canRead()) {
						다이얼로그(file.getName(), 언어(R.string.isNotAnUniPack));
					} else {
						다이얼로그(file.getName(), 언어(R.string.cantReadFile));
					}
					
				}
			}
		});
		getDir(경로);
		
		
		파일탐색기.setView(LL_파일탐색기);
		파일탐색기.show();
	}
	
	void getDir(String dirPath) {
		정보.설정.유니팩불러오기경로.저장하기(Main.this, dirPath);
		TV_경로.setText(dirPath);
		
		mItem = new ArrayList<>();
		mPath = new ArrayList<>();
		File f = new File(dirPath);
		File[] files = 파일.이름별정렬(f.listFiles());
		if (!dirPath.equals("/")) {
			mItem.add("../");
			mPath.add(f.getParent());
		}
		for (File file : files) {
			String name = file.getName();
			if (name.indexOf('.') != 0) {
				if (file.isDirectory()) {
					mPath.add(file.getPath());
					mItem.add(name + "/");
				} else if (name.lastIndexOf(".zip") == name.length() - 4 || name.lastIndexOf(".uni") == name.length() - 4) {
					mPath.add(file.getPath());
					mItem.add(file.getName());
				}
			}
		}
		ArrayAdapter<String> fileList = new ArrayAdapter<>(Main.this, android.R.layout.simple_list_item_1, mItem);
		LV_리스트.setAdapter(fileList);
	}
	
	void 다이얼로그(String 제목, String 내용) {
		new AlertDialog.Builder(Main.this)
			.setTitle(제목)
			.setMessage(내용)
			.setPositiveButton(언어(R.string.accept), null)
			.show();
	}
	
	@Override
	public void onBackPressed() {
		boolean clear = true;
		for (int i = 0; i < statusPlay.length; i++)
			if (statusPlay[i]) {
				toglePlay(i);
				clear = false;
			}
		
		for (int i = 0; i < statusInfo.length; i++)
			if (statusInfo[i]) {
				togleinfo(i);
				clear = false;
			}
		
		
		if (clear)
			super.onBackPressed();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		if (화면.너비 == 0 || 화면.높이 == 0 || 화면.패딩너비 == 0 || 화면.패딩높이 == 0) {
			화면.log("padding 크기값들이 잘못되었습니다.");
			restartApp(Main.this);
		}
		
		업데이트();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		finishActivity(this);
	}
	
	String 언어(int id) {
		return getResources().getString(id);
	}
}