package com.kimjisub.launchpad;

import android.os.Bundle;
import android.os.Handler;

import com.kimjisub.launchpad.api.unipad.UniPadApi;
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO;
import com.kimjisub.launchpad.databinding.ActivityImportpackBinding;
import com.kimjisub.launchpad.manager.FileManager;
import com.kimjisub.launchpad.manager.Log;
import com.kimjisub.launchpad.manager.Unipack;
import com.kimjisub.launchpad.network.UnishareDownloader;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ImportPackByUrlActivity extends BaseActivity {
	ActivityImportpackBinding b;

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
					UnishareVO unishare = response.body();
					setStatus(Status.prepare, code + "\n" + unishare.title + "\n" + unishare.producer);
					log("title: " + unishare.title);
					log("producerName: " + unishare.producer);

					new UnishareDownloader(unishare, F_UniPackRootExt, new UnishareDownloader.UnishareDownloadListener() {
						long fileSize;

						@Override
						public void onDownloadStart() {
							log("Download start");
						}

						@Override
						public void onGetFileSize(long fileSize) {
							this.fileSize = fileSize > 0 ? fileSize : unishare.fileSize;
							log("fileSize : " + this.fileSize);
						}

						@Override
						public void onDownloading(long downloadedSize) {
							setStatus(Status.downloading, (int) ((float) downloadedSize / fileSize * 100) + "%\n" + FileManager.byteToMB(downloadedSize) + " / " + FileManager.byteToMB(fileSize) + "MB");
						}

						@Override
						public void onDownloadEnd() {
							log("Download End");
						}

						@Override
						public void onAnalyzeStart() {
							log("Analyzing Start");
							setStatus(Status.analyzing, code + "\n" + unishare.title + "\n" + unishare.producer);
						}

						@Override
						public void onAnalyzeSuccess(Unipack unipack) {
							log("Analyzing Success");
							setStatus(Status.success, unipack.getInfoText(ImportPackByUrlActivity.this));
						}

						@Override
						public void onAnalyzeFail(Unipack unipack) {
							log("Analyzing Fail");
							Log.err(unipack.ErrorDetail);
							setStatus(Status.failed, unipack.ErrorDetail);
						}

						@Override
						public void onAnalyzeEnd() {
							log("Analyzing End");
							delayFinish();
						}

						@Override
						public void onException(Throwable e) {
							e.printStackTrace();
							setStatus(Status.failed, e.toString());
							log("Exception: " + e.getMessage());
						}
					});
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

	enum Status {prepare, downloading, analyzing, success, notFound, failed}

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
		runOnUiThread(() -> new Handler().postDelayed(() -> restartApp(this), 3000));
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}


}
