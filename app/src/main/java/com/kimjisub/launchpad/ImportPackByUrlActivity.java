package com.kimjisub.launchpad;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;

import com.kimjisub.launchpad.api.unipad.UniPadApi;
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO;
import com.kimjisub.launchpad.databinding.ActivityImportpackBinding;
import com.kimjisub.launchpad.manager.FileManager;
import com.kimjisub.launchpad.manager.Log;
import com.kimjisub.launchpad.manager.NotificationManager;
import com.kimjisub.launchpad.manager.Unipack;
import com.kimjisub.launchpad.network.UnishareDownloader;

import java.io.File;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ImportPackByUrlActivity extends BaseActivity {
	ActivityImportpackBinding b;

	String code;

	NotificationCompat.Builder notificationBuilder;
	int id;
	long fileSize;
	long downloadedSize;
	String identifyCode = "";
	String errorMsg = "";
	UnishareVO unishare;
	Unipack unipack;
	Throwable throwable;
	int prevPercent = 0;

	void initVar() {
		code = getIntent().getData().getQueryParameter("code");
		log("code: " + code);

		id = (int) (Math.random() * Integer.MAX_VALUE);

		notificationBuilder = new NotificationCompat.Builder(ImportPackByUrlActivity.this)
				.setAutoCancel(true)
				.setSmallIcon(R.mipmap.ic_launcher);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			notificationBuilder.setChannelId(NotificationManager.Channel.DOWNLOAD);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		b = setContentViewBind(R.layout.activity_importpack);
		initVar();

		identifyCode = "#" + code;
		setStatus(Status.prepare);

		UniPadApi.getService().unishare_get(code).enqueue(new Callback<UnishareVO>() {
			@Override
			public void onResponse(Call<UnishareVO> call, Response<UnishareVO> response) {
				if (response.isSuccessful()) {
					unishare = response.body();
					log("title: " + unishare.title);
					log("producerName: " + unishare.producer);
					identifyCode = unishare.title + " #" + unishare._id;
					setStatus(Status.getInfo);

					new UnishareDownloader(unishare, F_UniPackRootExt, new UnishareDownloader.UnishareDownloadListener() {
						@Override
						public void onDownloadStart() {
							log("Download start");
							setStatus(Status.downloadStart);
						}

						@Override
						public void onGetFileSize(long fileSize_) {
							fileSize = fileSize_ > 0 ? fileSize_ : unishare.fileSize;
							log("fileSize: " + fileSize_ + " → " + fileSize);
						}

						@Override
						public void onDownloading(long downloadedSize_) {
							downloadedSize = downloadedSize_;

							setStatus(Status.downloading);
						}

						@Override
						public void onDownloadEnd(File zip) {
							log("Download End");
						}

						@Override
						public void onAnalyzeStart() {
							log("Analyzing Start");
							setStatus(Status.analyzing);
						}

						@Override
						public void onAnalyzeSuccess(Unipack unipack_) {
							log("Analyzing Success");
							unipack = unipack_;
							setStatus(Status.success);
						}

						@Override
						public void onAnalyzeFail(Unipack unipack) {
							log("Analyzing Fail");
							Log.err(unipack.ErrorDetail);
							errorMsg = unipack.ErrorDetail;
							setStatus(Status.failed);
						}

						@Override
						public void onAnalyzeEnd(File folder) {
							log("Analyzing End");
							delayFinish();
						}

						@Override
						public void onException(Throwable throwable_) {
							throwable = throwable_;
							throwable.printStackTrace();
							setStatus(Status.exception);
							log("Exception: " + throwable.getMessage());
						}
					});
				} else {
					switch (response.code()) {
						case 404:
							log("404 Not Found");
							setStatus(Status.notFound);//, "Not Found");
							break;
					}
				}
			}

			@Override
			public void onFailure(Call<UnishareVO> call, Throwable t) {
				log("server error");
				setStatus(Status.failed);//, "server error\n" + t.getMessage());
			}
		});
	}


	enum Status {
		prepare(0, R.string.wait_a_sec),
		getInfo(1, R.string.wait_a_sec),
		downloadStart(2, R.string.downloadWaiting),
		downloading(3, R.string.downloading),
		analyzing(4, R.string.analyzing),
		success(5, R.string.success, false),
		failed(6, R.string.failed, false),
		exception(7, R.string.exceptionOccurred, false),
		notFound(8, R.string.unipackNotFound, false);

		int value;
		int titleStringId;
		boolean ongoing = true;

		Status(int value, int titleStringId) {
			this.value = value;
			this.titleStringId = titleStringId;
		}

		Status(int value, int titleStringId, boolean ongoing) {
			this.value = value;
			this.titleStringId = titleStringId;
			this.ongoing = ongoing;
		}
	}


	void setStatus(Status status) {
		runOnUiThread(() -> {
			switch (status) {
				case prepare:
					b.title.setText(status.titleStringId);
					b.message.setText(identifyCode);
					notificationBuilder.setContentTitle(identifyCode);
					notificationBuilder.setContentText(lang(status.titleStringId));
					break;
				case getInfo:
					b.title.setText(status.titleStringId);
					b.message.setText(identifyCode);
					notificationBuilder.setContentTitle(identifyCode);
					notificationBuilder.setContentText(lang(status.titleStringId));
					break;
				case downloadStart:
					b.title.setText(status.titleStringId);
					b.message.setText("#" + code + "\n" + unishare.title + "\n" + unishare.producer);
					notificationBuilder.setContentTitle(identifyCode);
					notificationBuilder.setContentText(lang(status.titleStringId));
					notificationBuilder.setProgress(100, 0, true);
					break;
				case downloading:
					int percent = (int) ((float) downloadedSize / fileSize * 100);
					String downloadedSizeMB = FileManager.byteToMB(downloadedSize);
					String fileSizeMB = FileManager.byteToMB(fileSize);

					if (prevPercent == percent) return;
					prevPercent = percent;

					b.title.setText(status.titleStringId);
					b.message.setText(percent + "%\n" + downloadedSizeMB + " / " + fileSizeMB + "MB");
					notificationBuilder.setContentTitle(identifyCode);
					notificationBuilder.setContentText(percent + "%\n" + downloadedSizeMB + " / " + fileSizeMB + "MB");
					notificationBuilder.setProgress(100, percent, false);
					break;
				case analyzing:
					b.title.setText(status.titleStringId);
					b.message.setText("#" + code + "\n" + unishare.title + "\n" + unishare.producer);
					notificationBuilder.setContentTitle(identifyCode);
					notificationBuilder.setContentText(lang(status.titleStringId));
					notificationBuilder.setProgress(100, 0, true);
					break;
				case success:
					b.title.setText(status.titleStringId);
					b.message.setText(unipack.getInfoText(ImportPackByUrlActivity.this));
					notificationBuilder.setContentTitle(identifyCode);
					notificationBuilder.setContentText(lang(status.titleStringId));
					notificationBuilder.setProgress(0, 0, false);
					break;
				case failed:
					b.title.setText(status.titleStringId);
					b.message.setText(errorMsg);
					notificationBuilder.setContentTitle(identifyCode);
					notificationBuilder.setContentText(lang(status.titleStringId));
					notificationBuilder.setProgress(0, 0, false);
					break;
				case exception:
					b.title.setText(status.titleStringId);
					b.message.setText(throwable.getMessage());
					notificationBuilder.setContentTitle(identifyCode);
					notificationBuilder.setContentText(lang(status.titleStringId));
					notificationBuilder.setProgress(0, 0, false);
					break;
				case notFound:
					b.title.setText(status.titleStringId);
					b.message.setText("#" + code + "\n" + unishare.title + "\n" + unishare.producer);
					notificationBuilder.setContentTitle(identifyCode);
					notificationBuilder.setContentText(lang(status.titleStringId));
					notificationBuilder.setProgress(0, 0, false);
					break;
			}


			notificationBuilder.setOngoing(status.ongoing);

			NotificationManager.getManager(ImportPackByUrlActivity.this).notify(id, notificationBuilder.build());
		});
	}

	void log(String msg) {
		runOnUiThread(() -> b.info.append(msg + "\n"));
	}

	void delayFinish() {
		log("delayFinish()");
		runOnUiThread(() -> new Handler().postDelayed(() -> {
			finish();
			overridePendingTransition(R.anim.activity_in, R.anim.activity_out);
		}, 3000));
		//restartApp(this);
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
