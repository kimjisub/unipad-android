package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Unipack;

import java.io.IOException;

import static com.kimjisub.launchpad.manage.Tools.*;

public class ImportPack extends BaseActivity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_importpack);
		
		processTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	@SuppressLint("StaticFieldLeak")
	AsyncTask<String, String, String> processTask = new AsyncTask<String, String, String>() {
		String folderURL;
		String projectURL;
		TextView TV_title;
		TextView TV_message;

		String title = null;
		String message = null;

		@Override
		protected void onPreExecute() {
			folderURL = SaveSetting.IsUsingSDCard.URL;
			projectURL = getIntent().getData().getPath();


			TV_title = findViewById(R.id.title);
			TV_message = findViewById(R.id.message);


			TV_message.setText(projectURL);
			super.onPreExecute();
		}

		@SuppressLint("DefaultLocale")
		@Override
		protected String doInBackground(String... params) {

			String fileURL = folderURL + "/" + FileManager.randomString(10) + "/";

			try {
				FileManager.unZipFile(projectURL, fileURL);
				Unipack unipack = new Unipack(fileURL, true);

				if (unipack.ErrorDetail == null) {
					title = lang(ImportPack.this, R.string.analyzeComplete);
					message = Unipack.getInfoText(ImportPack.this, unipack, projectURL);
				} else if (unipack.CriticalError) {
					title = lang(ImportPack.this, R.string.analyzeFailed);
					message = unipack.ErrorDetail;
					FileManager.deleteFolder(fileURL);
				} else {
					title = lang(ImportPack.this, R.string.warning);
					message = unipack.ErrorDetail;
				}

			} catch (IOException e) {
				title = lang(ImportPack.this, R.string.analyzeFailed);
				message = e.getMessage();
				FileManager.deleteFolder(fileURL);
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
		finishActivity(this);
		restartApp(this);
	}
}
