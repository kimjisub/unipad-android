package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.Unipack;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.kimjisub.launchpad.manage.Tools.log;
import static com.kimjisub.launchpad.manage.Tools.logErr;

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

		SaveSetting.IsUsingSDCard.load(ImportPackByUrl.this);
		UnipackRootURL = SaveSetting.IsUsingSDCard.URL;
}

	@SuppressLint("StaticFieldLeak")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_importpack);
		initVar();

		(new AsyncTask<String, String, String>() {
			String code;
			String title;
			String author;
			String URL;
			int fileSize;
			int downloadCount;

			@Override
			protected void onPreExecute() {
				Uri url = getIntent().getData();
				code = url.getQueryParameter("code");

				TV_title.setText(lang(R.string.wait));
				TV_message.setText(code);

				super.onPreExecute();
			}

			@Override
			protected String doInBackground(String[] params) {

				try {
					JSONObject jsonObject = new JSONObject(Networks.sendGet("http://unipad.kr/directUrl/api/get/" + code));

					code = jsonObject.getString("code");
					title = jsonObject.getString("title");
					author = jsonObject.getString("author");
					URL = jsonObject.getString("url");
					fileSize = jsonObject.getInt("fileSize");
					downloadCount = jsonObject.getInt("downloadCount");



					for (int i = 1; ; i++) {
						if (i == 1)
							UnipackZipURL = UnipackRootURL + "/" + title + ".zip";
						else
							UnipackZipURL = UnipackRootURL + "/" + title + " (" + i + ").zip";

						if (!new File(UnipackZipURL).exists())
							break;
					}
					for (int i = 1; ; i++) {
						if (i == 1)
							UnipackURL = UnipackRootURL + "/" + title + "/";
						else
							UnipackURL = UnipackRootURL + "/" + title + " (" + i + ")/";

						if (!new File(UnipackURL).exists())
							break;
					}


					try {

						java.net.URL url = new URL(URL);
						HttpURLConnection conexion = (HttpURLConnection) url.openConnection();
						conexion.setConnectTimeout(5000);
						conexion.setReadTimeout(5000);

						int fileSize_ = conexion.getContentLength();
						log(URL);
						fileSize = fileSize_ == -1 ? fileSize : fileSize_;
						log("fileSize : " + fileSize);

						InputStream input = new BufferedInputStream(url.openStream());
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
								logErr(unipack.ErrorDetail);
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
				} catch (JSONException e) {
					publishProgress("fail", "Not Exist");
					e.printStackTrace();
				}



				return null;
			}

			@Override
			protected void onProgressUpdate(String... progress) {
				switch (progress[0]){
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
		restartApp(this);
	}
}
