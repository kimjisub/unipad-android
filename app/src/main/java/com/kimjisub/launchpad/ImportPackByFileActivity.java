package com.kimjisub.launchpad;

import android.os.Bundle;

public class ImportPackByFileActivity extends BaseActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_importpack);
	}
	/*TextView TV_title;
	TextView TV_message;
	TextView TV_info;

	File F_UniPackRoot;
	File F_UniPackZip;
	File F_UniPackFile;
	@SuppressLint("StaticFieldLeak")
	AsyncTask<String, String, String> processTask = new AsyncTask<String, String, String>() {

		String title = null;
		String message = null;

		@Override
		protected void onPreExecute() {
			TV_title.setText(lang(R.string.analyzing));
			TV_message.setText(F_UniPackZip.getPath());
			TV_info.setText("getPath : " + UnipackZipPath);
			super.onPreExecute();
		}

		@SuppressLint("DefaultLocale")
		@Override
		protected String doInBackground(String... params) {
			try {
				FileManager.unZipFile(UnipackZipPath, UnipackPath);
				File UniPackFile = new File(UnipackPath);
				Unipack unipack = new Unipack(UniPackFile, true);

				if (unipack.ErrorDetail == null) {
					title = lang(R.string.analyzeComplete);
					message = unipack.getInfoText(ImportPackByFileActivity.this);
				} else if (unipack.CriticalError) {
					title = lang(R.string.analyzeFailed);
					message = unipack.ErrorDetail;
					FileManager.deleteDirectory(UniPackFile);
				} else {
					title = lang(R.string.warning);
					message = unipack.ErrorDetail;
				}

			} catch (IOException e) {
				title = lang(R.string.analyzeFailed);
				message = e.getMessage();
				FileManager.deleteDirectory(new File(UnipackPath));
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
			new Handler().postDelayed(() -> finish(), 3000);
		}
	};

	void initVar() {
		TV_title = findViewById(R.id.title);
		TV_message = findViewById(R.id.message);
		TV_info = findViewById(R.id.info);

		F_UniPackRoot = PreferenceManager.IsUsingSDCard.getPath(ImportPackByFileActivity.this);
		UnipackZipPath = getIntent().getData().getPath();
		File file = new File(UnipackZipPath);
		String name = file.getName();
		String name_ = name.substring(0, name.lastIndexOf("."));
		UnipackPath = FileManager.makeNextPath(F_UniPackRoot, name_, "/");
		UniPackFile = new File(UnipackPath);

		log("UnipackZipPath: " + UnipackZipPath);
		log("UnipackPath: " + UnipackPath);
		setStatus(Status.prepare, UnipackZipPath);
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
					FileManager.unZipFile(UnipackZipPath, UnipackPath);
					Unipack unipack = new Unipack(UniPackFile, true);
					if (unipack.CriticalError) {
						Log.err(unipack.ErrorDetail);
						setStatus(ImportPackByFileActivity.Status.success, unipack.ErrorDetail);
						FileManager.deleteDirectory(UniPackFile);
					} else
						setStatus(ImportPackByFileActivity.Status.success, unipack.getInfoText(ImportPackByFileActivity.this));

					log("Analyzing End");
				} catch (Exception e) {
					e.printStackTrace();
					log("Analyzing Error");
					setStatus(ImportPackByFileActivity.Status.failed, e.toString());
					log("DeleteFolder: UnipackPath " + UnipackPath);
					FileManager.deleteDirectory(UniPackFile);
				}

				log("DeleteFolder: UnipackZipPath " + UnipackZipPath);
				FileManager.deleteDirectory(new File(UnipackZipPath));

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
	}*/
}
