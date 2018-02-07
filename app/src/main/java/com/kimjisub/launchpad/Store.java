package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.kimjisub.design.ItemStore;
import com.kimjisub.launchpad.fb.fbStore;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.UIManager;
import com.kimjisub.launchpad.manage.Unipack;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import static com.kimjisub.launchpad.manage.Tools.lang;
import static com.kimjisub.launchpad.manage.Tools.log;
import static com.kimjisub.launchpad.manage.Tools.logErr;

/**
 * Created by rlawl ON 2016-03-04.
 * ReCreated by rlawl ON 2016-04-23.
 */

public class Store extends BaseActivity {
	LinearLayout LL_list;

	String UnipackRootURL = SaveSetting.IsUsingSDCard.URL;

	int downloadCount = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_store);

		LL_list = findViewById(R.id.list);

		initUI();

	}

	ArrayList<ItemStore> IS_items;
	ArrayList<fbStore> DStoreDatas;

	void initUI() {
		LL_list.removeAllViews();
		IS_items = new ArrayList<>();
		DStoreDatas = new ArrayList<>();

		LL_list.addView(ItemStore.errItem(Store.this, null));

		final String[] downloadedProjectList;

		File folder = new File(UnipackRootURL);

		if (folder.isDirectory()) {
			downloadedProjectList = new String[folder.listFiles().length];
			File[] files = folder.listFiles();
			for (int i = 0; i < files.length; i++)
				downloadedProjectList[i] = files[i].getName();
		} else {
			downloadedProjectList = new String[0];
			folder.mkdir();
		}

		new Networks.GetStoreList().setOnAddListener(new Networks.GetStoreList.onDataListener() {
			@Override
			public void onAdd(final fbStore d) {
				try {
					if (DStoreDatas.size() == 0)
						LL_list.removeAllViews();
					DStoreDatas.add(d);
					d.index = DStoreDatas.size() - 1;

					final ItemStore itemStore = new ItemStore(Store.this)
						.setTitle(d.title)
						.setSubTitle(d.producerName)
						.setLED(d.isLED)
						.setAutoPlay(d.isAutoPlay)
						.updateDownloadCount(d.downloadCount)
						.setOnViewLongClickListener(new ItemStore.OnViewLongClickListener() {
							@Override
							public void onViewLongClick(ItemStore v) {
								v.toggleInfo();
							}
						});

					boolean isDownloaded = false;
					for (String downloadedProject : downloadedProjectList) {
						if (d.code.equals(downloadedProject)) {
							isDownloaded = true;
							break;
						}
					}

					if (isDownloaded)
						itemStore.setFlagColor(R.drawable.border_play_green);
					else
						itemStore.setOnViewClickListener(new ItemStore.OnViewClickListener() {
							@Override
							public void onViewClick(ItemStore v) {
								itemClicked(v, d.index);
							}
						});


					IS_items.add(itemStore);
					LL_list.addView(itemStore, 0);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onChange(final fbStore d) {
				try {

					int i;
					for (i = 0; i < DStoreDatas.size(); i++) {
						fbStore item = DStoreDatas.get(i);
						if (item.code.equals(d.code))
							break;
					}

					ItemStore itemStore = IS_items.get(i);
					itemStore.setTitle(d.title)
						.setSubTitle(d.producerName)
						.setLED(d.isLED)
						.setAutoPlay(d.isAutoPlay)
						.updateDownloadCount(d.downloadCount);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).run();
	}

	@SuppressLint("StaticFieldLeak")
	void itemClicked(final ItemStore v, final int i) {
		log("itemClicked(" + i + ")");

		v.changeFlagOpen(true);
		v.changeFlagColor(R.drawable.border_play_gray);
		v.setOnViewClickListener(null);
		v.setProgress("0%");

		(new AsyncTask<String, Long, String>() {

			int fileSize;
			String UnipackZipURL;
			String UnipackURL;

			String code;
			String title;
			String producerName;
			boolean isAutoPlay;
			boolean isLED;
			int downloadCount;
			String URL;

			@Override
			protected void onPreExecute() {
				Store.this.downloadCount++;

				code = DStoreDatas.get(i).code;
				title = DStoreDatas.get(i).title;
				producerName = DStoreDatas.get(i).producerName;
				isAutoPlay = DStoreDatas.get(i).isAutoPlay;
				isLED = DStoreDatas.get(i).isLED;
				downloadCount = DStoreDatas.get(i).downloadCount;
				URL = DStoreDatas.get(i).URL;

				for (int i = 1; ; i++) {
					if (i == 1)
						UnipackZipURL = UnipackRootURL + "/" + code + ".zip";
					else
						UnipackZipURL = UnipackRootURL + "/" + code + " (" + i + ").zip";

					if (!new File(UnipackZipURL).exists())
						break;
				}
				UnipackURL = UnipackRootURL + "/" + code + "/";

				super.onPreExecute();
			}

			@Override
			protected String doInBackground(String[] params) {

				Networks.sendGet("http://unipad.kr:8081/?code=" + code);
				try {

					URL url = new URL(URL);
					HttpURLConnection conexion = (HttpURLConnection) url.openConnection();
					conexion.setConnectTimeout(5000);
					conexion.setReadTimeout(5000);

					fileSize = conexion.getContentLength();
					log(URL);
					log("fileSize : " + fileSize);
					fileSize = fileSize == -1 ? 104857600 : fileSize;

					InputStream input = new BufferedInputStream(url.openStream());
					OutputStream output = new FileOutputStream(UnipackZipURL);

					byte data[] = new byte[1024];

					long total = 0;

					int count;
					while ((count = input.read(data)) != -1) {
						total += count;
						publishProgress(0L, total);
						output.write(data, 0, count);
					}

					output.flush();
					output.close();
					input.close();
					publishProgress(1L);

					try {
						FileManager.unZipFile(UnipackZipURL, UnipackURL);
						Unipack unipack = new Unipack(UnipackURL, true);
						if (unipack.CriticalError) {
							logErr(unipack.ErrorDetail);
							publishProgress(-1L);
							FileManager.deleteFolder(UnipackURL);
						} else
							publishProgress(2L);

					} catch (Exception e) {
						publishProgress(-1L);
						FileManager.deleteFolder(UnipackURL);
						e.printStackTrace();
					}
					FileManager.deleteFolder(UnipackZipURL);


				} catch (Exception e) {
					publishProgress(-1L);
					e.printStackTrace();
				}
				Store.this.downloadCount--;

				return null;
			}

			@Override
			protected void onProgressUpdate(Long... progress) {
				if (progress[0] == 0) {//다운중
					v.setProgress((int) ((float) progress[1] / fileSize * 100) + "%");
				} else if (progress[0] == 1) {//분석중
					v.setProgress(lang(Store.this, R.string.analyzing));
					v.changeFlagColor(R.drawable.border_play_orange);
				} else if (progress[0] == -1) {//실패
					v.setProgress(lang(Store.this, R.string.failed));
					v.changeFlagColor(R.drawable.border_play_red);
				} else if (progress[0] == 2) {//완료
					v.setProgress("");
					v.changeFlagColor(R.drawable.border_play_green);
					v.changeFlagOpen(false);
				}
			}

			@Override
			protected void onPostExecute(String unused) {

			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	@Override
	protected void onResume() {
		super.onResume();
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

		if (UIManager.Scale[0] == 0) {
			log("padding 크기값들이 잘못되었습니다.");
			restartApp(Store.this);
		}
	}


	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				if (downloadCount > 0) {
					Toast.makeText(Store.this, lang(Store.this, R.string.canNotQuitWhileDownloading), Toast.LENGTH_SHORT).show();
					return true;
				} else
					finish();
				break;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		finishActivity(this);
	}
}
