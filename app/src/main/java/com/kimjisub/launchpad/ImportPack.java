package com.kimjisub.launchpad;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import java.io.IOException;

/**
 * Created by rlawl on 2016-04-25.
 * Created by rlawl on 2016-04-25.
 */

public class ImportPack extends BaseActivity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_importpack);
		
		final String 프로젝트폴더URL = 정보.설정.유니팩저장경로.URL;
		final String 프로젝트경로 = getIntent().getData().getPath();
		
		
		final TextView TV_제목 = (TextView) findViewById(R.id.제목);
		final TextView TV_메시지 = (TextView) findViewById(R.id.메시지);
		
		
		TV_메시지.setText(프로젝트경로);
		
		(new AsyncTask<String, String, String>() {
			
			boolean 성공 = false;
			boolean 경고 = true;
			String 제목 = null;
			String 메시지 = null;
			
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
			}
			
			@Override
			protected String doInBackground(String... params) {
				
				String 경로 = 프로젝트폴더URL + "/" + 파일.랜덤문자(10) + "/";
				
				try {
					파일.unZipFile(프로젝트경로, 경로);
					정보.uni 프로젝트 = new 정보.uni(경로, true);
					
					if (프로젝트.에러내용 == null) {
						성공 = true;
						경고 = false;
						제목 = 언어(R.string.analyzeComplete);
						메시지 = 언어(R.string.title) + " : " + 프로젝트.제목 + "\n" +
							언어(R.string.producerName) + " : " + 프로젝트.제작자 + "\n" +
							언어(R.string.scale) + " : " + 프로젝트.가로축 + " x " + 프로젝트.세로축 + "\n" +
							언어(R.string.chainCount) + " : " + 프로젝트.체인 + "\n" +
							언어(R.string.capacity) + " : " + String.format("%.2f", (float) 파일.폴더크기(경로) / 1024L / 1024L) + " MB";
					} else if (프로젝트.치명적인에러) {
						제목 = 언어(R.string.analyzeFailed);
						메시지 = 프로젝트.에러내용;
						파일.폴더삭제(경로);
					} else {
						성공 = true;
						경고 = true;
						제목 = 언어(R.string.warning);
						메시지 = 프로젝트.에러내용;
					}
					
				} catch (IOException e) {
					제목 = 언어(R.string.analyzeFailed);
					메시지 = e.getMessage();
					파일.폴더삭제(경로);
				}
				
				return null;
			}
			
			@Override
			protected void onProgressUpdate(String... progress) {
			}
			
			@Override
			protected void onPostExecute(String result) {
				super.onPostExecute(result);
				TV_제목.setText(제목);
				TV_메시지.setText(메시지);
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						finish();
					}
				}, 3000);
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		
		
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		finishActivity(this);
		restartApp(this);
	}
	
	String 언어(int id) {
		return getResources().getString(id);
	}
}
