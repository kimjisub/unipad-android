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

import com.kimjisub.launchpad.manage.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.kimjisub.launchpad.manage.Tools.*;


public class Main extends BaseActivity {
	LinearLayout LL_List;
	String ProjectFolderURL;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		
		LL_List = (LinearLayout) findViewById(R.id.목록);
		ProjectFolderURL = SaveSetting.IsUsingSDCard.URL;
		
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
		
	}
	
	RelativeLayout[] RL_list;
	boolean[] statusPlay;
	boolean[] statusInfo;
	String[] URL;
	Unipack unipacks[];
	
	void update() {
		LL_List.removeAllViews();
		
		File 프로젝트폴더 = new File(ProjectFolderURL);
		
		if (프로젝트폴더.isDirectory()) {
			
			File[] 프로젝트폴더리스트 = FileManager.sortByTime(프로젝트폴더.listFiles());
			int num = 프로젝트폴더리스트.length;
			
			RL_list = new RelativeLayout[num];
			statusPlay = new boolean[num];
			statusInfo = new boolean[num];
			URL = new String[num];
			unipacks = new Unipack[num];
			
			int 파일개수 = 0;
			for (int i = 0; i < num; i++) {
				File 프로젝트폴더_ = 프로젝트폴더리스트[i];
				if (프로젝트폴더_.isFile())
					continue;
				파일개수++;
				
				URL[i] = ProjectFolderURL + "/" + 프로젝트폴더_.getName();
				unipacks[i] = new Unipack(URL[i], false);
				
				RelativeLayout 항목 = (RelativeLayout) View.inflate(Main.this, R.layout.list_item, null);
				
				((TextView) 항목.findViewById(R.id.제목)).setText(unipacks[i].제목);
				((TextView) 항목.findViewById(R.id.제작자)).setText(unipacks[i].제작자);
				
				((TextView) 항목.findViewById(R.id.size)).setText(unipacks[i].가로축 + " x " + unipacks[i].세로축);
				((TextView) 항목.findViewById(R.id.chain)).setText(unipacks[i].체인 + "");
				((TextView) 항목.findViewById(R.id.capacity)).setText(String.format("%.2f", (float) FileManager.getFolderSize(URL[i]) / 1024L / 1024L) + " MB");
				
				if (unipacks[i].keyLED여부)
					((TextView) 항목.findViewById(R.id.LED)).setTextColor(getResources().getColor(R.color.green));
				if (unipacks[i].autoPlay여부)
					((TextView) 항목.findViewById(R.id.자동재생)).setTextColor(getResources().getColor(R.color.green));
				
				항목.findViewById(R.id.제목제작자).setX(UIManager.dpToPx(getApplicationContext(), 10));
				
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
				LL_List.addView(항목);
			}
			
			if (파일개수 == 0) {
				View v = View.inflate(Main.this, R.layout.list_item_not_exist, null);
				v.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(Main.this, Store.class));
					}
				});
				v.findViewById(R.id.제목제작자).setX(UIManager.dpToPx(getApplicationContext(), 10));
				LL_List.addView(v);
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
			v.findViewById(R.id.제목제작자).setX(UIManager.dpToPx(getApplicationContext(), 10));
			LL_List.addView(v);
		}
		
		File nomedia = new File(ProjectFolderURL + "/.nomedia");
		if (!nomedia.isFile()) {
			try {
				(new FileWriter(nomedia)).close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		log(ProjectFolderURL);
	}
	
	void toglePlay(final int i) {
		RelativeLayout 항목 = RL_list[i];
		if (!statusPlay[i]) {
			//animation
			항목.findViewById(R.id.제목제작자).animate().x(UIManager.dpToPx(getApplicationContext(), 100)).setDuration(500).start();
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
			항목.findViewById(R.id.제목제작자).animate().x(UIManager.dpToPx(getApplicationContext(), 10)).setDuration(500).start();
			항목.findViewById(R.id.play).animate().alpha(1).setDuration(500).start();
			
			//clickEvent
			항목.findViewById(R.id.playbtn).setOnClickListener(null);
			항목.findViewById(R.id.playbtn).setClickable(false);
		}
		
		statusPlay[i] = !statusPlay[i];
	}
	
	void togleinfo(final int i) {
		final RelativeLayout 항목 = RL_list[i];
		final int px = UIManager.dpToPx(getApplicationContext(), 30);
		final int px2 = UIManager.dpToPx(getApplicationContext(), 35);
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
						.setTitle(unipacks[i].제목)
						.setMessage(lang(Main.this, R.string.doYouWantToDeleteProject))
						.setPositiveButton(lang(Main.this, R.string.cancel), null)
						.setNegativeButton(lang(Main.this, R.string.delete), new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								FileManager.deleteFolder(URL[i]);
								update();
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
		
		String 경로 =SaveSetting.FileExplorerPath.load(Main.this);
		
		
		LV_리스트.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final File file = new File(mPath.get(position));
				if (file.isDirectory()) {
					if (file.canRead())
						getDir(mPath.get(position));
					else
						UIManager.showDialog(Main.this, file.getName(), lang(Main.this, R.string.cantReadFolder));
				} else {
					if (file.canRead()) {
						(new AsyncTask<String, String, String>() {
							
							ProgressDialog 로딩 = new ProgressDialog(Main.this);
							
							@Override
							protected void onPreExecute() {
								
								파일탐색기.dismiss();
								로딩.setTitle(lang(Main.this, R.string.analyzing));
								로딩.setMessage(lang(Main.this, R.string.wait));
								로딩.setCancelable(false);
								로딩.show();
								super.onPreExecute();
							}
							
							@Override
							protected String doInBackground(String... params) {
								
								String 경로 = ProjectFolderURL + "/" + FileManager.randomString(10) + "/";
								
								try {
									FileManager.unZipFile(file.getPath(), 경로);
									Unipack project = new Unipack(경로, true);
									
									if (project.에러내용 == null) {
										publishProgress(lang(Main.this, R.string.analyzeComplete),
											lang(Main.this, R.string.title) + " : " + project.제목 + "\n" +
												lang(Main.this, R.string.producerName) + " : " + project.제작자 + "\n" +
												lang(Main.this, R.string.scale) + " : " + project.가로축 + " x " + project.세로축 + "\n" +
												lang(Main.this, R.string.chainCount) + " : " + project.체인 + "\n" +
												lang(Main.this, R.string.capacity) + " : " + String.format("%.2f", (float) FileManager.getFolderSize(경로) / 1024L / 1024L) + " MB");
									} else if (project.치명적인에러) {
										publishProgress(lang(Main.this, R.string.analyzeFailed), project.에러내용);
										FileManager.deleteFolder(경로);
									} else {
										publishProgress(lang(Main.this, R.string.warning), project.에러내용);
									}
									
								} catch (IOException e) {
									publishProgress(lang(Main.this, R.string.analyzeFailed), e.toString());
									FileManager.deleteFolder(경로);
								}
								
								return null;
							}
							
							@Override
							protected void onProgressUpdate(String... progress) {
								UIManager.showDialog(Main.this, progress[0], progress[1]);
							}
							
							@Override
							protected void onPostExecute(String result) {
								로딩.dismiss();
								update();
								super.onPostExecute(result);
							}
						}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
						
						
					} else if (file.canRead()) {
						UIManager.showDialog(Main.this, file.getName(), lang(Main.this, R.string.isNotAnUniPack));
					} else {
						UIManager.showDialog(Main.this, file.getName(), lang(Main.this, R.string.cantReadFile));
					}
					
				}
			}
		});
		getDir(경로);
		
		
		파일탐색기.setView(LL_파일탐색기);
		파일탐색기.show();
	}
	
	void getDir(String dirPath) {
		SaveSetting.FileExplorerPath.save(Main.this, dirPath);
		TV_경로.setText(dirPath);
		
		mItem = new ArrayList<>();
		mPath = new ArrayList<>();
		File f = new File(dirPath);
		File[] files = FileManager.sortByName(f.listFiles());
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
	
	
	
	
	
	
	
	
	
	
	void updateCheck() {
		new Networks.CheckVersion(getPackageName()).setOnEndListener(new Networks.CheckVersion.onEndListener() {
			@Override
			public void onEnd(String verson) {
				try {
					String currVerson = BuildConfig.VERSION_NAME;
					if (verson != null && !currVerson.equals(verson)) {
						new AlertDialog.Builder(Main.this)
							.setTitle(lang(Main.this, R.string.newVersionFound))
							.setMessage(lang(Main.this, R.string.currentVersion) + " : " + currVerson + "\n" +
								lang(Main.this, R.string.newVersion) + " : " + verson)
							.setPositiveButton(lang(Main.this, R.string.update), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/fbStore/apps/details?id=" + getPackageName())));
									dialog.dismiss();
								}
							})
							.setNegativeButton(lang(Main.this, R.string.ignore), new DialogInterface.OnClickListener() {
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
		}).run();
	}
	
	void noticeCheck() {
		new Networks.CheckNotice(lang(Main.this, R.string.language)).setOnEndListener(new Networks.CheckNotice.onEndListener() {
			@Override
			public void onEnd(final String title, final String content) {
				if (title != null && content != null) {
					String 이전공지사항 = SaveSetting.PrevNotice.load(Main.this);
					
					if (!이전공지사항.equals(content)) {
						
						TextView 내용 = new TextView(Main.this);
						내용.setText(Html.fromHtml(content));
						int px1 = UIManager.dpToPx(Main.this, 25);
						int px2 = UIManager.dpToPx(Main.this, 15);
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
							.setTitle(title)
							.setPositiveButton(lang(Main.this, R.string.accept), null)
							.setNegativeButton(lang(Main.this, R.string.doNotSee), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									SaveSetting.PrevNotice.save(Main.this, content);
								}
							})
							.setCancelable(false)
							.setView(리니어)
							.show();
						
					}
				}
			}
		}).run();
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
		
		if (UIManager.Scale[0] == 0) {
			Tools.log("padding 크기값들이 잘못되었습니다.");
			restartApp(Main.this);
		}
		
		update();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		finishActivity(this);
	}
}