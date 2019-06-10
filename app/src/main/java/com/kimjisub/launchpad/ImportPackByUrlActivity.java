package com.kimjisub.launchpad;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;

import com.kimjisub.launchpad.databinding.ActivityImportpackBinding;
import com.kimjisub.launchpad.manager.FileManager;
import com.kimjisub.launchpad.manager.Log;
import com.kimjisub.launchpad.manager.Unipack;
import com.kimjisub.launchpad.networks.api.UniPadApi;
import com.kimjisub.launchpad.networks.api.vo.UnishareVO;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ImportPackByUrlActivity extends BaseActivity {
	ActivityImportpackBinding b;

	File F_UniPackZip;
	File F_UniPack;

	String code;

	void initVar() {
		code = getIntent().getData().getQueryParameter("code");
		log("code: " + code);
		setStatus(Status.prepare, code);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		b = setContentViewBind(R.layout.activity_importpack);
		initVar();


		UniPadApi.getService().unishare_get(code).enqueue(new Callback<UnishareVO>() {
			@Override
			public void onResponse(Call<UnishareVO> call, Response<UnishareVO> response) {
				if (response.isSuccessful()) {
					UnishareVO unishareVO = response.body();
					setStatus(Status.prepare, code + "\n" + unishareVO.title + "\n" + unishareVO.producer);
					log("title: " + unishareVO.title);
					log("producerName: " + unishareVO.producer);
					F_UniPackZip = FileManager.makeNextPath(F_UniPackRootExt, unishareVO.title + " #" + code, ".zip");
					F_UniPack = FileManager.makeNextPath(F_UniPackRootExt, unishareVO.title + " #" + code, "/");
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
			public void onFailure(Call<UnishareVO> call, Throwable t) {
				log("server error");
				setStatus(Status.failed, "server error\n" + t.getMessage());
			}
		});
	}

	void setStatus(Status status, String msg) {
		runOnUiThread(() -> {
			switch (status) {
				case prepare:
					b.title.setText(R.string.wait);
					b.message.setText(msg);
					break;
				case downloading:
					b.title.setText(R.string.downloading);
					b.message.setText(msg);
					break;
				case analyzing:
					b.title.setText(R.string.analyzing);
					b.message.setText(msg);
					break;
				case success:
					b.title.setText(R.string.success);
					b.message.setText(msg);
					break;
				case notFound:
					b.title.setText(R.string.unipackNotFound);
					b.message.setText(msg);
					break;
				case failed:
					b.title.setText(R.string.failed);
					b.message.setText(msg);
					break;
			}
		});
	}

	void log(String msg) {
		runOnUiThread(() -> b.info.append(msg + "\n"));
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
		UnishareVO u;

		public DownloadTask(UnishareVO u) {
			this.u = u;
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

				java.net.URL downloadUrl = new URL("https://api.unipad.kr/unishare/"+u._id+"/download");
				HttpURLConnection conexion = (HttpURLConnection) downloadUrl.openConnection();
				conexion.setConnectTimeout(5000);
				conexion.setReadTimeout(5000);

				int fileSize_ = conexion.getContentLength();
				u.fileSize = fileSize_ == -1 ? u.fileSize : fileSize_;
				log("fileSize : " + u.fileSize);

				InputStream input = new BufferedInputStream(downloadUrl.openStream());
				OutputStream output = new FileOutputStream(F_UniPackZip);

				byte data[] = new byte[1024];
				long total = 0;
				int count;
				int progress = 0;
				log("Download start");
				while ((count = input.read(data)) != -1) {
					total += count;
					progress++;
					if (progress % 100 == 0) {

						setStatus(ImportPackByUrlActivity.Status.downloading, (int) ((float) total / u.fileSize * 100) + "%\n" + FileManager.byteToMB(total) + " / " + FileManager.byteToMB(u.fileSize) + "MB");
					}
					output.write(data, 0, count);
				}
				log("Download End");

				output.flush();
				output.close();
				input.close();

				log("Analyzing Start");
				setStatus(ImportPackByUrlActivity.Status.analyzing, code + "\n" + u.title + "\n" + u.producer);

				try {
					FileManager.unZipFile(F_UniPackZip.getPath(), F_UniPack.getPath());
					Unipack unipack = new Unipack(F_UniPack, true);
					if (unipack.CriticalError) {
						Log.err(unipack.ErrorDetail);
						setStatus(ImportPackByUrlActivity.Status.failed, unipack.ErrorDetail);
						FileManager.deleteDirectory(F_UniPack);
					} else
						setStatus(ImportPackByUrlActivity.Status.success, unipack.getInfoText(ImportPackByUrlActivity.this));

					log("Analyzing End");
				} catch (Exception e) {
					e.printStackTrace();
					log("Analyzing Error");
					setStatus(ImportPackByUrlActivity.Status.failed, e.toString());
					log("DeleteFolder: UnipackPath " + F_UniPack.getPath());
					FileManager.deleteDirectory(F_UniPack);
				}

				log("DeleteFolder: UnipackZipPath " + F_UniPackZip.getPath());
				FileManager.deleteDirectory(F_UniPackZip);

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
