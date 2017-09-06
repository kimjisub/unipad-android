package com.kimjisub.launchpad;

import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.kimjisub.launchpad.fb.fbStore;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.UIManager;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.Unipack;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;

import static com.kimjisub.launchpad.manage.Tools.lang;
import static com.kimjisub.launchpad.manage.Tools.log;

/**
 * Created by rlawl ON 2016-03-04.
 * ReCreated by rlawl ON 2016-04-23.
 */

public class Store extends BaseActivity {
	LinearLayout LL_list;
	
	String projectFolderURL;
	
	int downloadCount = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_store);
		
		projectFolderURL = SaveSetting.IsUsingSDCard.URL;
		
		LL_list = (LinearLayout) findViewById(R.id.목록);
		
		initUI();
		
	}
	
	ArrayList<RelativeLayout> RL_list;
	ArrayList<Boolean> statusPlay;
	ArrayList<Boolean> statusInfo;
	ArrayList<fbStore> DStoreDatas;
	
	void initUI() {
		LL_list.removeAllViews();
		RL_list = new ArrayList<>();
		statusPlay = new ArrayList<>();
		statusInfo = new ArrayList<>();
		DStoreDatas = new ArrayList<>();
		
		View v = View.inflate(Store.this, R.layout.store_list_error, null);
		v.findViewById(R.id.제목제작자).setX(UIManager.dpToPx(getApplicationContext(), 10));
		LL_list.addView(v);
		
		final String[] 프로젝트목록;
		
		java.io.File 프로젝트폴더 = new java.io.File(projectFolderURL);
		
		if (프로젝트폴더.isDirectory()) {
			프로젝트목록 = new String[프로젝트폴더.listFiles().length];
			java.io.File[] 프로젝트폴더리스트 = 프로젝트폴더.listFiles();
			for (int i = 0; i < 프로젝트폴더리스트.length; i++)
				프로젝트목록[i] = 프로젝트폴더리스트[i].getName();
			
		} else {
			프로젝트목록 = new String[0];
			프로젝트폴더.mkdir();
		}
		
		new Networks.GetStoreList().setOnAddListener(new Networks.GetStoreList.onDataListener() {
			@Override
			public void onAdd(fbStore data) {
				try {
					if (DStoreDatas.size() == 0)
						LL_list.removeAllViews();
					statusPlay.add(false);
					statusInfo.add(false);
					DStoreDatas.add(data);
					data.i = DStoreDatas.size() - 1;
					
					final int i = data.i;
					String code = data.code;
					String title = data.title;
					String producerName = data.producerName;
					boolean isAutoPlay = data.isAutoPlay;
					boolean isLED = data.isLED;
					int downloadCount = data.downloadCount;
					String URL = data.URL;
					
					final RelativeLayout 항목 = (RelativeLayout) View.inflate(Store.this, R.layout.store_list_item, null);
					((TextView) 항목.findViewById(R.id.제목)).setText(title);
					((TextView) 항목.findViewById(R.id.제작자)).setText(producerName);
					if (isLED)
						((TextView) 항목.findViewById(R.id.LED)).setTextColor(getResources().getColor(R.color.green));
					else
						((TextView) 항목.findViewById(R.id.LED)).setTextColor(getResources().getColor(R.color.pink));
					if (isAutoPlay)
						((TextView) 항목.findViewById(R.id.자동재생)).setTextColor(getResources().getColor(R.color.green));
					else
						((TextView) 항목.findViewById(R.id.자동재생)).setTextColor(getResources().getColor(R.color.pink));
					((TextView) 항목.findViewById(R.id.downloadCount)).setText((new DecimalFormat("#,##0")).format(downloadCount));
					
					항목.findViewById(R.id.제목제작자).setX(UIManager.dpToPx(getApplicationContext(), 10));
					
					boolean 이미다운됨 = false;
					for (int j = 0; j < 프로젝트목록.length; j++) {
						if (code.equals(프로젝트목록[j])) {
							이미다운됨 = true;
							break;
						}
					}
					
					if (이미다운됨) {
						항목.findViewById(R.id.play1).setBackground(getResources().getDrawable(R.drawable.border_play_green));
					} else {
						항목.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								//clearInfo();
								onPlay(i);
							}
						});
					}
					항목.setOnLongClickListener(new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View v) {
							//clearInfo(i);
							toggleinfo(i);
							return true;
						}
					});
					
					RL_list.add(항목);
					LL_list.addView(항목);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			@Override
			public void onChange(fbStore data) {
				try {
					int i_;
					for (i_ = 0; i_ < DStoreDatas.size(); i_++) {
						fbStore tmp = DStoreDatas.get(i_);
						if (tmp.code.equals(data.code))
							break;
					}
					final int i = i_;
					String code = data.code;
					String title = data.title;
					String producerName = data.producerName;
					boolean isAutoPlay = data.isAutoPlay;
					boolean isLED = data.isLED;
					int downloadCount = data.downloadCount;
					String URL = data.URL;
					
					final RelativeLayout 항목 = RL_list.get(i);
					((TextView) 항목.findViewById(R.id.제목)).setText(title);
					((TextView) 항목.findViewById(R.id.제작자)).setText(producerName);
					if (isLED)
						((TextView) 항목.findViewById(R.id.LED)).setTextColor(getResources().getColor(R.color.green));
					else
						((TextView) 항목.findViewById(R.id.LED)).setTextColor(getResources().getColor(R.color.pink));
					if (isAutoPlay)
						((TextView) 항목.findViewById(R.id.자동재생)).setTextColor(getResources().getColor(R.color.green));
					else
						((TextView) 항목.findViewById(R.id.자동재생)).setTextColor(getResources().getColor(R.color.pink));
					((TextView) 항목.findViewById(R.id.downloadCount)).setText((new DecimalFormat("#,##0")).format(downloadCount));
					
					항목.findViewById(R.id.downloadCount).setAlpha(0);
					항목.findViewById(R.id.downloadCount).animate().alpha(1).setDuration(500).start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).run();
	}
	
	void onPlay(final int i) {
		Log.d("com.kimjisub.log", "onPlay(" + i + ")");
		final RelativeLayout 항목 = RL_list.get(i);
		if (!statusPlay.get(i)) {
			//animation
			항목.findViewById(R.id.제목제작자).animate().x(UIManager.dpToPx(getApplicationContext(), 100)).setDuration(500).start();
			항목.findViewById(R.id.play1).animate().alpha(0).setDuration(500).start();
			
			//clickEvent
			항목.setOnClickListener(null);
			
			final TextView 프로그레스 = (TextView) 항목.findViewById(R.id.progress);
			(new AsyncTask<String, Long, String>() {
				
				int 파일크기;
				String 다운로드경로;
				
				String code;
				String title;
				String producerName;
				boolean isAutoPlay;
				boolean isLED;
				int downloadCount;
				String URL;
				
				@Override
				protected void onPreExecute() {
					Store.this.downloadCount++;
					프로그레스.setText(0 + "%");
					
					code = DStoreDatas.get(i).code;
					title = DStoreDatas.get(i).title;
					producerName = DStoreDatas.get(i).producerName;
					isAutoPlay = DStoreDatas.get(i).isAutoPlay;
					isLED = DStoreDatas.get(i).isLED;
					downloadCount = DStoreDatas.get(i).downloadCount;
					URL = DStoreDatas.get(i).URL;
					
					super.onPreExecute();
				}
				
				@Override
				protected String doInBackground(String[] params) {
					
					Networks.sendGet("http://unipad.kr:8081/?code=" + code);
					try {
						
						URL url = new URL(URL);
						HttpURLConnection conexion = (HttpURLConnection) url.openConnection();
						conexion.setConnectTimeout(5000);
						conexion.setReadTimeout(5000);
						
						파일크기 = conexion.getContentLength();
						Log.d("com.kimjisub.log", URL);
						Log.d("com.kimjisub.log", "파일크기 : " + 파일크기);
						파일크기 = 파일크기 == -1 ? 104857600 : 파일크기;
						
						다운로드경로 = SaveSetting.IsUsingSDCard.URL + "/" + FileManager.randomString(10) + ".uni.zip";
						
						InputStream input = new BufferedInputStream(url.openStream());
						OutputStream output = new FileOutputStream(다운로드경로);
						
						byte data[] = new byte[1024];
						
						long total = 0;
						
						int count;
						while ((count = input.read(data)) != -1) {
							total += count;
							publishProgress(0L, total);
							output.write(data, 0, count);
						}
						
						output.flush();
						output.close();
						input.close();
						publishProgress(1L);
						String 경로 = projectFolderURL + "/" + code + "/";
						
						try {
							FileManager.unZipFile(다운로드경로, 경로);
							Unipack 프로젝트 = new Unipack(경로, true);
							if (프로젝트.CriticalError) {
								publishProgress(-1L);
								FileManager.deleteFolder(경로);
							} else
								publishProgress(2L);
							
						} catch (Exception e) {
							publishProgress(-1L);
							FileManager.deleteFolder(경로);
							e.printStackTrace();
						}
						FileManager.deleteFolder(다운로드경로);
						
						
					} catch (Exception e) {
						publishProgress(-1L);
						e.printStackTrace();
					}
					Store.this.downloadCount--;
					
					return null;
				}
				
				@Override
				protected void onProgressUpdate(Long... progress) {
					if (progress[0] == 0) {//다운중
						프로그레스.setText((int) ((float) progress[1] / 파일크기 * 100) + "%");
					} else if (progress[0] == 1) {//분석중
						프로그레스.setText(lang(Store.this, R.string.analyzing));
						항목.findViewById(R.id.play1).setBackground(항목.findViewById(R.id.play2).getBackground());
						항목.findViewById(R.id.play1).setAlpha(1);
						항목.findViewById(R.id.play2).setBackground(getResources().getDrawable(R.drawable.border_play_orange));
						항목.findViewById(R.id.play1).animate().alpha(0).setDuration(500).start();
					} else if (progress[0] == -1) {//실패
						프로그레스.setText(lang(Store.this, R.string.failed));
						항목.findViewById(R.id.play1).setBackground(항목.findViewById(R.id.play2).getBackground());
						항목.findViewById(R.id.play1).setAlpha(1);
						항목.findViewById(R.id.play2).setBackground(getResources().getDrawable(R.drawable.border_play_red));
						항목.findViewById(R.id.play1).animate().alpha(0).setDuration(500).start();
					} else if (progress[0] == 2) {//완료
						프로그레스.setText("");
						항목.findViewById(R.id.play1).setBackground(항목.findViewById(R.id.play2).getBackground());
						항목.findViewById(R.id.play1).setAlpha(1);
						항목.findViewById(R.id.play2).setBackground(getResources().getDrawable(R.drawable.border_play_green));
						항목.findViewById(R.id.제목제작자).animate().x(UIManager.dpToPx(getApplicationContext(), 10)).setDuration(500).start();
						항목.findViewById(R.id.play1).animate().alpha(0).setDuration(500).start();
					}
				}
				
				@Override
				protected void onPostExecute(String unused) {
					
				}
			}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		} else {
			//animation
			항목.findViewById(R.id.제목제작자).animate().x(UIManager.dpToPx(getApplicationContext(), 10)).setDuration(500).start();
			항목.findViewById(R.id.play1).animate().alpha(1).setDuration(500).start();
			
			//clickEvent
			항목.findViewById(R.id.playbtn).setOnClickListener(null);
			항목.findViewById(R.id.playbtn).setClickable(false);
		}
		
		statusPlay.set(i, !statusPlay.get(i));
	}
	
	void toggleinfo(final int i) {
		final RelativeLayout 항목 = RL_list.get(i);
		final int px = UIManager.dpToPx(getApplicationContext(), 30);
		final int px2 = UIManager.dpToPx(getApplicationContext(), 35);
		if (!statusInfo.get(i)) {
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
		}
		
		statusInfo.set(i, !statusInfo.get(i));
	}
	
	/*void clearInfo() {
		for (int i = 0; i < statusInfo.size(); i++)
			if (statusInfo.get(i))
				toggleInfo(i);
	}
	
	void clearInfo(int e) {
		for (int i = 0; i < statusInfo.size(); i++)
			if (statusInfo.get(i) && i != e)
				toggleInfo(i);
	}*/
	
	@Override
	protected void onResume() {
		super.onResume();
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		if (UIManager.Scale[0] == 0) {
			log("padding 크기값들이 잘못되었습니다.");
			restartApp(Store.this);
		}
	}
	
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				if (downloadCount > 0) {
					Toast.makeText(Store.this, lang(Store.this, R.string.canNotQuitWhileDownloading), Toast.LENGTH_SHORT).show();
					return true;
				} else
					finish();
				break;
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		finishActivity(this);
	}
}
