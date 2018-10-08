package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Log;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.SettingManager;
import com.kimjisub.launchpad.manage.Unipack;
import com.kimjisub.launchpad.manage.network.MakeUrl;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ImportPackByUrl extends BaseActivity {
	
	TextView TV_title;
	TextView TV_message;
	TextView TV_info;
	
	String UnipackRootURL;
	String UnipackZipURL;
	String UnipackURL;
	
	void initVar() {
		TV_title = findViewById(R.id.title);
		TV_message = findViewById(R.id.message);
		TV_info = findViewById(R.id.info);
		
		UnipackRootURL = SettingManager.IsUsingSDCard.URL(ImportPackByUrl.this);
	}
	
	@SuppressLint("StaticFieldLeak")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_importpack);
		initVar();
		
		Networks.getUniPadApi().makeUrl_get(getIntent().getData().getQueryParameter("code")).enqueue(new Callback<MakeUrl>() {
			@Override
			public void onResponse(Call<MakeUrl> call, Response<MakeUrl> response) {
				if (response.isSuccessful()) {
					new DownloadTask(response.body()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				} else {
					switch (response.code()) {
						case 404:
							//not found
							break;
					}
				}
			}
			
			@Override
			public void onFailure(Call<MakeUrl> call, Throwable t) {
				//server error
			}
		});
	}
	
	enum Status {downloading, analyzing, success, notFound, failed}
	
	void setStatus(Status status, String msg) {
		switch (status) {
			case downloading:
				TV_title.setText(R.string.downloading);
				TV_message.setText(msg);
				break;
			case analyzing:
				TV_title.setText(R.string.analyzing);
				TV_message.setText(msg);
				break;
			case success:
				TV_title.setText(R.string.success);
				TV_message.setText(msg);
				break;
			case notFound:
				TV_title.setText(R.string.unipackNotFound);
				TV_message.setText(msg);
				break;
			case failed:
				TV_title.setText(R.string.failed);
				TV_message.setText(msg);
				break;
		}
	}
	
	class DownloadTask extends AsyncTask<String, String, String> {
		String code;
		String title;
		String author;
		String url;
		int fileSize;
		int downloadCount;
		
		public DownloadTask(MakeUrl makeUrl) {
			this.code = makeUrl.code;
			this.title = makeUrl.title;
			this.author = makeUrl.author;
			this.url = makeUrl.url;
			this.fileSize = makeUrl.fileSize;
			this.downloadCount = makeUrl.downloadCount;
		}
		
		@Override
		protected void onPreExecute() {
			TV_title.setText(lang(R.string.wait));
			TV_message.setText(code);
			
			super.onPreExecute();
		}
		
		@Override
		protected String doInBackground(String[] params) {
			
			UnipackZipURL = FileManager.makeNextUrl(UnipackRootURL, title, ".zip");
			UnipackURL = FileManager.makeNextUrl(UnipackRootURL, title, "/");
			
			try {
				
				java.net.URL downloadUrl = new URL(url);
				HttpURLConnection conexion = (HttpURLConnection) downloadUrl.openConnection();
				conexion.setConnectTimeout(5000);
				conexion.setReadTimeout(5000);
				
				int fileSize_ = conexion.getContentLength();
				Log.log(url);
				fileSize = fileSize_ == -1 ? fileSize : fileSize_;
				Log.log("fileSize : " + fileSize);
				
				InputStream input = new BufferedInputStream(downloadUrl.openStream());
				OutputStream output = new FileOutputStream(UnipackZipURL);
				
				byte data[] = new byte[1024];
				
				long total = 0;
				
				int count;
				int skip = 100;
				while ((count = input.read(data)) != -1) {
					total += count;
					skip--;
					if (skip == 0) {
						publishProgress("down", (int) ((float) total / fileSize * 100) + "%");
						skip = 100;
					}
					output.write(data, 0, count);
				}
				
				output.flush();
				output.close();
				input.close();
				publishProgress("anal", title);
				
				try {
					FileManager.unZipFile(UnipackZipURL, UnipackURL);
					Unipack unipack = new Unipack(UnipackURL, true);
					if (unipack.CriticalError) {
						Log.err(unipack.ErrorDetail);
						publishProgress("fail", unipack.ErrorDetail);
						FileManager.deleteFolder(UnipackURL);
					} else
						publishProgress("succ", unipack.title);
					
				} catch (Exception e) {
					publishProgress("fail", e.toString());
					FileManager.deleteFolder(UnipackURL);
					e.printStackTrace();
				}
				FileManager.deleteFolder(UnipackZipURL);
				
				
			} catch (Exception e) {
				publishProgress("fail", e.toString());
				e.printStackTrace();
			}
			
			
			return null;
		}
		
		@Override
		protected void onProgressUpdate(String... progress) {
			switch (progress[0]) {
				case "down":
					TV_title.setText(lang(R.string.downloading));
					TV_message.setText(progress[1]);
					break;
				case "anal":
					TV_title.setText(lang(R.string.analyzing));
					TV_message.setText(progress[1]);
					break;
				case "fail":
					TV_title.setText(lang(R.string.failed));
					TV_message.setText(progress[1]);
					break;
				case "succ":
					TV_title.setText(lang(R.string.success));
					TV_message.setText(progress[1]);
					break;
			}
		}
		
		@Override
		protected void onPostExecute(String unused) {
			new Handler().postDelayed(() -> finish(), 3000);
		}
	}
	
	
	@Override
	public void onResume() {
		super.onResume();
		initVar();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		restartApp(this);
	}
}
