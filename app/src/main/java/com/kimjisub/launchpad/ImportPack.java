package com.kimjisub.launchpad;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Unipack;

import java.io.IOException;

import static com.kimjisub.launchpad.manage.Tools.*;

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
		
		final String folderURL = SaveSetting.IsUsingSDCard.URL;
		final String projectURL = getIntent().getData().getPath();
		
		
		final TextView TV_제목 = (TextView) findViewById(R.id.제목);
		final TextView TV_메시지 = (TextView) findViewById(R.id.메시지);
		
		
		TV_메시지.setText(projectURL);
		
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
				
				String 경로 = folderURL + "/" + FileManager.randomString(10) + "/";
				
				try {
					FileManager.unZipFile(projectURL, 경로);
					Unipack 프로젝트 = new Unipack(경로, true);
					
					if (프로젝트.에러내용 == null) {
						성공 = true;
						경고 = false;
						제목 = lang(ImportPack.this, R.string.analyzeComplete);
						메시지 = lang(ImportPack.this, R.string.title) + " : " + 프로젝트.제목 + "\n" +
							lang(ImportPack.this, R.string.producerName) + " : " + 프로젝트.제작자 + "\n" +
							lang(ImportPack.this, R.string.scale) + " : " + 프로젝트.가로축 + " x " + 프로젝트.세로축 + "\n" +
							lang(ImportPack.this, R.string.chainCount) + " : " + 프로젝트.체인 + "\n" +
							lang(ImportPack.this, R.string.capacity) + " : " + String.format("%.2f", (float) FileManager.getFolderSize(경로) / 1024L / 1024L) + " MB";
					} else if (프로젝트.치명적인에러) {
						제목 = lang(ImportPack.this, R.string.analyzeFailed);
						메시지 = 프로젝트.에러내용;
						FileManager.deleteFolder(경로);
					} else {
						성공 = true;
						경고 = true;
						제목 = lang(ImportPack.this, R.string.warning);
						메시지 = 프로젝트.에러내용;
					}
					
				} catch (IOException e) {
					제목 = lang(ImportPack.this, R.string.analyzeFailed);
					메시지 = e.getMessage();
					FileManager.deleteFolder(경로);
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
}
