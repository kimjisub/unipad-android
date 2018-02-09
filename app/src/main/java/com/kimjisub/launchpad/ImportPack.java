package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.Unipack;

import java.io.File;
import java.io.IOException;

public class ImportPack extends BaseActivity {

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

		UnipackRootURL = SaveSetting.IsUsingSDCard.URL;
		UnipackZipURL = getIntent().getData().getPath();
		File file = new File(UnipackZipURL);
		String name = file.getName();
		String name_ = name.substring(0, name.lastIndexOf("."));
		for (int i = 1; ; i++) {
			if (i == 1)
				UnipackURL = UnipackRootURL + "/" + name_ + "/";
			else
				UnipackURL = UnipackRootURL + "/" + name_ + " (" + i + ")/";
			if (!new File(UnipackURL).exists())
				break;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_importpack);
		initVar();

		processTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

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
					message = unipack.getInfoText(ImportPack.this);
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

	@Override
	protected void onDestroy() {
		super.onDestroy();
		restartApp(this);
	}
}
