package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.kimjisub.launchpad.networks.UniPadApiBuilder;
import com.kimjisub.launchpad.utils.FileManager;
import com.kimjisub.launchpad.utils.Log;
import com.kimjisub.launchpad.networks.Networks;
import com.kimjisub.launchpad.utils.SettingManager;
import com.kimjisub.launchpad.utils.Unipack;
import com.kimjisub.launchpad.networks.dto.MakeUrlDTO;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ImportPackByUrlActivity extends BaseActivity {

	TextView TV_title;
	TextView TV_message;
	TextView TV_info;

	String UnipackRootPath;
	String UnipackZipPath;
	String UnipackPath;

	String code;

	void initVar() {
		TV_title = findViewById(R.id.title);
		TV_message = findViewById(R.id.message);
		TV_info = findViewById(R.id.info);

		UnipackRootPath = SettingManager.IsUsingSDCard.getPath(ImportPackByUrlActivity.this);
		//UnipackZipPath
		//UnipackPath

		code = getIntent().getData().getQueryParameter("code");
		log("code: " + code);
		setStatus(Status.prepare, code);
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_importpack);
		initVar();


		UniPadApiBuilder.getService().makeUrl_get(code).enqueue(new Callback<MakeUrlDTO>() {
			@Override
			public void onResponse(Call<MakeUrlDTO> call, Response<MakeUrlDTO> response) {
				if (response.isSuccessful()) {
					MakeUrlDTO makeUrlDTO = response.body();
					setStatus(Status.prepare, code + "\n" + makeUrlDTO.title + "\n" + makeUrlDTO.producerName);
					log("title: " + makeUrlDTO.title);
					log("producerName: " + makeUrlDTO.producerName);
					UnipackZipPath = FileManager.makeNextPath(UnipackRootPath, makeUrlDTO.title + " #" + code, ".zip");
					UnipackPath = FileManager.makeNextPath(UnipackRootPath, makeUrlDTO.title + " #" + code, "/");
					new DownloadTask(response.body()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					addCount(code);
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
			public void onFailure(Call<MakeUrlDTO> call, Throwable t) {
				log("server error");
				setStatus(Status.failed, "server error\n" + t.getMessage());
			}
		});
	}

	void addCount(String code) {
		UniPadApiBuilder.getService().makeUrl_addCount(code).enqueue(new Callback<ResponseBody>() {
			@Override
			public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
			}

			@Override
			public void onFailure(Call<ResponseBody> call, Throwable t) {
			}
		});
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
		new Handler().postDelayed(() -> restartApp(this), 3000);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}


	enum Status {prepare, downloading, analyzing, success, notFound, failed}

	class DownloadTask extends AsyncTask<String, String, String> {
		String code;
		String title;
		String producerName;
		String url;
		int fileSize;
		int downloadCount;

		public DownloadTask(MakeUrlDTO makeUrlDTO) {
			this.code = makeUrlDTO.code;
			this.title = makeUrlDTO.title;
			this.producerName = makeUrlDTO.producerName;
			this.url = makeUrlDTO.url;
			this.fileSize = makeUrlDTO.fileSize;
			this.downloadCount = makeUrlDTO.downloadCount;
		}

		@Override
		protected void onPreExecute() {
			log("Download Task onPreExecute()");
			super.onPreExecute();
		}

		@Override
		protected String doInBackground(String[] params) {
			log("Download Task doInBackground()");

			try {

				java.net.URL downloadUrl = new URL(url);
				HttpURLConnection conexion = (HttpURLConnection) downloadUrl.openConnection();
				conexion.setConnectTimeout(5000);
				conexion.setReadTimeout(5000);

				int fileSize_ = conexion.getContentLength();
				fileSize = fileSize_ == -1 ? fileSize : fileSize_;
				log("fileSize : " + fileSize);

				InputStream input = new BufferedInputStream(downloadUrl.openStream());
				OutputStream output = new FileOutputStream(UnipackZipPath);

				byte data[] = new byte[1024];
				long total = 0;
				int count;
				int progress = 0;
				log("Download start");
				while ((count = input.read(data)) != -1) {
					total += count;
					progress++;
					if (progress % 100 == 0) {

						setStatus(ImportPackByUrlActivity.Status.downloading, (int) ((float) total / fileSize * 100) + "%\n" + FileManager.byteToMB(total) + " / " + FileManager.byteToMB(fileSize) + "MB");
					}
					output.write(data, 0, count);
				}
				log("Download End");

				output.flush();
				output.close();
				input.close();

				log("Analyzing Start");
				setStatus(ImportPackByUrlActivity.Status.analyzing, code + "\n" + title + "\n" + producerName);

				try {
					FileManager.unZipFile(UnipackZipPath, UnipackPath);
					Unipack unipack = new Unipack(UnipackPath, true);
					if (unipack.CriticalError) {
						Log.err(unipack.ErrorDetail);
						setStatus(ImportPackByUrlActivity.Status.failed, unipack.ErrorDetail);
						FileManager.deleteFolder(UnipackPath);
					} else
						setStatus(ImportPackByUrlActivity.Status.success, unipack.getInfoText(ImportPackByUrlActivity.this));

					log("Analyzing End");
				} catch (Exception e) {
					e.printStackTrace();
					log("Analyzing Error");
					setStatus(ImportPackByUrlActivity.Status.failed, e.toString());
					log("DeleteFolder: UnipackPath " + UnipackPath);
					FileManager.deleteFolder(UnipackPath);
				}

				log("DeleteFolder: UnipackZipPath " + UnipackZipPath);
				FileManager.deleteFolder(UnipackZipPath);

			} catch (Exception e) {
				e.printStackTrace();
				log("Download Task doInBackground() ERROR");
				setStatus(ImportPackByUrlActivity.Status.failed, e.toString());
			}


			return null;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
		}

		@Override
		protected void onPostExecute(String unused) {
			log("Download Task onPostExecute()");
			delayFinish();
		}
	}
}
