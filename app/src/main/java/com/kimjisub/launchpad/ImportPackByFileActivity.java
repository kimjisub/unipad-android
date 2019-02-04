package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Log;
import com.kimjisub.launchpad.manage.SettingManager;
import com.kimjisub.launchpad.manage.Unipack;

import java.io.File;
import java.io.IOException;

public class ImportPackByFileActivity extends BaseActivity {

	TextView TV_title;
	TextView TV_message;
	TextView TV_info;

	String UnipackRootURL;
	String UnipackZipURL;
	String UnipackURL;
	@SuppressLint("StaticFieldLeak")
	AsyncTask<String, String, String> processTask = new AsyncTask<String, String, String>() {


		String title = null;
		String message = null;

		@Override
		protected void onPreExecute() {
			TV_title.setText(lang(R.string.analyzing));
			TV_message.setText(UnipackZipURL);
			TV_info.setText("URL : " + UnipackZipURL);
			super.onPreExecute();
		}

		@SuppressLint("DefaultLocale")
		@Override
		protected String doInBackground(String... params) {
			try {
				FileManager.unZipFile(UnipackZipURL, UnipackURL);
				Unipack unipack = new Unipack(UnipackURL, true);

				if (unipack.ErrorDetail == null) {
					title = lang(R.string.analyzeComplete);
					message = unipack.getInfoText(ImportPackByFileActivity.this);
				} else if (unipack.CriticalError) {
					title = lang(R.string.analyzeFailed);
					message = unipack.ErrorDetail;
					FileManager.deleteFolder(UnipackURL);
				} else {
					title = lang(R.string.warning);
					message = unipack.ErrorDetail;
				}

			} catch (IOException e) {
				title = lang(R.string.analyzeFailed);
				message = e.getMessage();
				FileManager.deleteFolder(UnipackURL);
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			TV_title.setText(title);
			TV_message.setText(message);
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					finish();
				}
			}, 3000);
		}
	};

	void initVar() {
		TV_title = findViewById(R.id.title);
		TV_message = findViewById(R.id.message);
		TV_info = findViewById(R.id.info);

		UnipackRootURL = SettingManager.IsUsingSDCard.URL(ImportPackByFileActivity.this);
		UnipackZipURL = getIntent().getData().getPath();
		File file = new File(UnipackZipURL);
		String name = file.getName();
		String name_ = name.substring(0, name.lastIndexOf("."));
		UnipackURL = FileManager.makeNextUrl(UnipackRootURL, name_, "/");

		log("UnipackZipURL: " + UnipackZipURL);
		log("UnipackURL: " + UnipackURL);
		setStatus(Status.prepare, UnipackZipURL);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_importpack);
		initVar();

		new UnzipTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		//processTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

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
		});
	}

	void log(String msg) {
		runOnUiThread(() -> TV_info.append(msg + "\n"));
	}

	void delayFinish() {
		log("delayFinish()");
		new Handler().postDelayed(() -> finish(), 3000);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		restartApp(this);
	}

	enum Status {prepare, downloading, analyzing, success, notFound, failed}

	class UnzipTask extends AsyncTask<String, String, String> {

		@Override
		protected void onPreExecute() {
			log("Unzip Task onPreExecute()");
			super.onPreExecute();
		}

		@Override
		protected String doInBackground(String[] params) {
			log("Unzip Task doInBackground()");

			try {
				try {
					FileManager.unZipFile(UnipackZipURL, UnipackURL);
					Unipack unipack = new Unipack(UnipackURL, true);
					if (unipack.CriticalError) {
						Log.err(unipack.ErrorDetail);
						setStatus(ImportPackByFileActivity.Status.success, unipack.ErrorDetail);
						FileManager.deleteFolder(UnipackURL);
					} else
						setStatus(ImportPackByFileActivity.Status.success, unipack.getInfoText(ImportPackByFileActivity.this));

					log("Analyzing End");
				} catch (Exception e) {
					e.printStackTrace();
					log("Analyzing Error");
					setStatus(ImportPackByFileActivity.Status.failed, e.toString());
					log("DeleteFolder: UnipackURL " + UnipackURL);
					FileManager.deleteFolder(UnipackURL);
				}

				log("DeleteFolder: UnipackZipURL " + UnipackZipURL);
				FileManager.deleteFolder(UnipackZipURL);

			} catch (Exception e) {
				e.printStackTrace();
				log("Download Task doInBackground() ERROR");
				setStatus(ImportPackByFileActivity.Status.failed, e.toString());
			}


			return null;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
		}

		@Override
		protected void onPostExecute(String unused) {
			log("Unzip Task onPostExecute()");
			delayFinish();
		}
	}
}
