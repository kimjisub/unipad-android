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
		
		String code = getIntent().getData().getQueryParameter("code");
		log("code: " + code);
		setStatus(Status.prepare, code);
		Networks.getUniPadApi().makeUrl_get(code).enqueue(new Callback<MakeUrl>() {
			@Override
			public void onResponse(Call<MakeUrl> call, Response<MakeUrl> response) {
				if (response.isSuccessful()) {
					MakeUrl makeUrl = response.body();
					log("title: " + makeUrl.title);
					log("author: " + makeUrl.author);
					new DownloadTask(response.body()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				} else {
					switch (response.code()) {
						case 404:
							log("404 Not Found");
							setStatus(Status.notFound, "Not Found");
							break;
					}
				}
			}
			
			@Override
			public void onFailure(Call<MakeUrl> call, Throwable t) {
				log("server error");
				setStatus(Status.failed, "server error\n" + t.getMessage());
			}
		});
	}
	
	enum Status {prepare, downloading, analyzing, success, notFound, failed}
	
	void setStatus(Status status, String msg) {
		runOnUiThread(() -> {
			switch (status) {
				case prepare:
					TV_title.setText(R.string.wait);
					TV_message.setText(msg);
					break;
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
					delayFinish();
					break;
				case notFound:
					TV_title.setText(R.string.unipackNotFound);
					TV_message.setText(msg);
					delayFinish();
					break;
				case failed:
					TV_title.setText(R.string.failed);
					TV_message.setText(msg);
					delayFinish();
					break;
			}
		});
	}
	
	void log(String msg) {
		runOnUiThread(() -> TV_info.append(msg + "\n"));
	}
	
	void delayFinish() {
		log("delayFinish()");
		new Handler().postDelayed(() -> finish(), 3000);
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
			log("Download Task onPreExecute()");
			super.onPreExecute();
		}
		
		@Override
		protected String doInBackground(String[] params) {
			log("Download Task doInBackground()");
			
			UnipackZipURL = FileManager.makeNextUrl(UnipackRootURL, title + " #" + code, ".zip");
			UnipackURL = FileManager.makeNextUrl(UnipackRootURL, title + " #" + code, "/");
			
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
				int progress = 0;
				log("Download start");
				while ((count = input.read(data)) != -1) {
					total += count;
					progress++;
					if (progress % 100 == 0) {
						
						setStatus(ImportPackByUrl.Status.downloading, (int)((float) total / fileSize * 100) + "%\n" + FileManager.byteToMB(total) + " / " + FileManager.byteToMB(fileSize) + "MB");
					}
					output.write(data, 0, count);
				}
				log("Download End");
				
				output.flush();
				output.close();
				input.close();
				
				log("Analyzing Start");
				setStatus(ImportPackByUrl.Status.analyzing, title);
				
				try {
					FileManager.unZipFile(UnipackZipURL, UnipackURL);
					Unipack unipack = new Unipack(UnipackURL, true);
					if (unipack.CriticalError) {
						Log.err(unipack.ErrorDetail);
						setStatus(ImportPackByUrl.Status.success, unipack.ErrorDetail);
						FileManager.deleteFolder(UnipackURL);
					} else
						setStatus(ImportPackByUrl.Status.success, unipack.getInfoText(ImportPackByUrl.this));
					
					log("Analyzing End");
				} catch (Exception e) {
					e.printStackTrace();
					log("Analyzing Error");
					setStatus(ImportPackByUrl.Status.failed, e.toString());
					log("DeleteFolder: UnipackURL " + UnipackURL);
					FileManager.deleteFolder(UnipackURL);
				}
				
				log("DeleteFolder: UnipackZipURL " + UnipackZipURL);
				FileManager.deleteFolder(UnipackZipURL);
				
			} catch (Exception e) {
				e.printStackTrace();
				log("Download Task doInBackground() ERROR");
				setStatus(ImportPackByUrl.Status.failed, e.toString());
			}
			
			
			return null;
		}
		
		@Override
		protected void onProgressUpdate(String... progress) {
		}
		
		@Override
		protected void onPostExecute(String unused) {
			log("Download Task onPostExecute()");
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
		gotoMainAndUpdateList(this);
	}
}
