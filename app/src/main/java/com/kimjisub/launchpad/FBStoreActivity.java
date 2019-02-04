package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import com.kimjisub.launchpad.fb.fbStore;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Log;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.SettingManager;
import com.kimjisub.launchpad.manage.Unipack;
import com.kimjisub.unipad.designkit.PackViewSimple;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class FBStoreActivity extends BaseActivity {

	LinearLayout LL_list;

	String UnipackRootURL;

	Networks.GetStoreCount getStoreCount = new Networks.GetStoreCount();
	ArrayList<PackViewSimple> PV_items;
	ArrayList<fbStore> DStoreDatas;
	ArrayList<Pack> P_packs;

	void initVar() {
		LL_list = findViewById(R.id.list);

		UnipackRootURL = SettingManager.IsUsingSDCard.URL(FBStoreActivity.this);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_store);
		initVar();

		update();
		getStoreCount.run();
	}

	void update() {
		P_packs = new ArrayList<>();
		LL_list.removeAllViews();
		togglePlay(null);

		addErrorItem();

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

		new Networks.GetStoreList().setDataListener(new Networks.GetStoreList.onDataListener() {
			@Override
			public void onAdd(final fbStore d) {
				try {
					if (DStoreDatas.size() == 0)
						LL_list.removeAllViews();
					DStoreDatas.add(d);
					d.index = DStoreDatas.size() - 1;


					boolean _isDownloaded = false;
					for (String downloadedProject : downloadedProjectList) {
						if (d.code.equals(downloadedProject)) {
							_isDownloaded = true;
							break;
						}
					}
					final boolean isDownloaded = _isDownloaded;

					final PackViewSimple packViewSimple = new PackViewSimple(FBStoreActivity.this)
							.setFlagColor(isDownloaded ? color(R.color.green) : color(R.color.red))
							.setTitle(d.title)
							.setSubTitle(d.producerName)
							.setOption1(lang(R.string.LED_), d.isLED)
							.setOption2(lang(R.string.autoPlay_), d.isAutoPlay)
							.setPlayImageShow(false)
							.setOnEventListener(new PackViewSimple.OnEventListener() {
								@Override
								public void onViewClick(PackViewSimple v) {
									v.togglePlay();
								}

								@Override
								public void onViewLongClick(PackViewSimple v) {
								}

								@Override
								public void onPlayClick(PackViewSimple v) {
									if (v.getStatus())
										itemClicked(v, d.index);
								}
							})
							.setStatus(!isDownloaded);


					final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					int left = dpToPx(16);
					int top = 0;
					int right = dpToPx(16);
					int bottom = dpToPx(10);
					lp.setMargins(left, top, right, bottom);
					PV_items.add(packViewSimple);
					LL_list.addView(packViewSimple, 0, lp);
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

					PackViewSimple packViewSimple = PV_items.get(i);
					packViewSimple.setTitle(d.title)
							.setSubTitle(d.producerName)
							.setOption1(lang(R.string.LED_), d.isLED)
							.setOption2(lang(R.string.autoPlay_), d.isAutoPlay);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).run();
	}

	void addErrorItem() {
		String title = lang(R.string.errOccur);
		String subTitle = lang(R.string.UnableToAccessServer);

		PackViewSimple packViewSimple = PackViewSimple.errItem(FBStoreActivity.this, title, subTitle, null);


		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		int left = dpToPx(16);
		int top = 0;
		int right = dpToPx(16);
		int bottom = dpToPx(10);
		lp.setMargins(left, top, right, bottom);
		LL_list.addView(packViewSimple, lp);
	}

	// ============================================================================================= Activity

	void togglePlay(String code) {
		try {
			int i = 0;
			for (Pack pack : P_packs) {
				fbStore fbStore = pack.fbStore;
				PackViewSimple packViewSimple = pack.packViewSimple;

				if (fbStore.code.equals(code))
					packViewSimple.togglePlay(true);
				else
					packViewSimple.togglePlay(false);

				i++;
			}

			int playIndex = getPlayIndex();
			Animation animation = AnimationUtils.loadAnimation(FBStoreActivity.this, playIndex != -1 ? R.anim.panel_in : R.anim.panel_out);

			animation.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
					RL_panel_pack.setVisibility(View.VISIBLE);
					RL_panel_pack.setAlpha(1);
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					RL_panel_pack.setVisibility(playIndex != -1 ? View.VISIBLE : View.INVISIBLE);
					RL_panel_pack.setAlpha(playIndex != -1 ? 1 : 0);
				}

				@Override
				public void onAnimationRepeat(Animation animation) {

				}
			});

			if(playIndex != -1){
				Pack pack = P_packs.get(playIndex);
				TV_panel_pack_title.setText(pack.unipack.title);
				TV_panel_pack_subTitle.setText(pack.unipack.producerName);
			}

			if (!(RL_panel_pack.getVisibility() == View.VISIBLE && playIndex != -1))
				RL_panel_pack.startAnimation(animation);

		} catch (ConcurrentModificationException e) {
			e.printStackTrace();
		}
	}

	int getPlayIndex() {
		int index = -1;

		int i = 0;
		for (Pack pack : P_packs) {
			if (pack.packViewSimple.isPlay()) {
				index = i;
				break;
			}
			i++;
		}

		return index;
	}

	void itemClicked(final PackViewSimple v, final int i) {
		Log.log("itemClicked(" + i + ")");

		v.togglePlay(true);
		v.updateFlagColor(color(R.color.gray1));
		v.setStatus(false);
		v.setPlayText("0%");

		startDownload();
	}

	@SuppressLint("StaticFieldLeak")
	void startDownload(Pack pack){

		fbStore fbStore = pack.fbStore;
		PackViewSimple packViewSimple = pack.packViewSimple;

		String code = fbStore.code;
		String title = fbStore.title;
		String producerName = fbStore.producerName;
		boolean isAutoPlay = fbStore.isAutoPlay;
		boolean isLED = fbStore.isLED;
		int downloadCount = fbStore.downloadCount;
		String URL = fbStore.URL;

		int fileSize;
		String UnipackZipURL = FileManager.makeNextUrl(UnipackRootURL, code, ".zip");
		String UnipackURL = UnipackRootURL + "/" + code + "/";


		(new AsyncTask<String, Long, String>() {



			@Override
			protected void onPreExecute() {
				FBStoreActivity.this.downloadCount++;

				super.onPreExecute();
			}

			@Override
			protected String doInBackground(String[] params) {

				Networks.sendGet("https://us-central1-unipad-e41ab.cloudfunctions.net/increaseDownloadCount/" + code);
				try {

					URL url = new URL(URL);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setConnectTimeout(5000);
					connection.setReadTimeout(5000);

					fileSize = connection.getContentLength();
					Log.log(URL);
					Log.log("fileSize : " + fileSize);
					fileSize = fileSize == -1 ? 104857600 : fileSize;

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
							publishProgress(0L, total);
							skip = 100;
						}
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
							Log.err(unipack.ErrorDetail);
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
				FBStoreActivity.this.downloadCount--;

				return null;
			}

			@Override
			protected void onProgressUpdate(Long... progress) {
				if (progress[0] == 0) {//다운중
					v.setPlayText((int) ((float) progress[1] / fileSize * 100) + "%\n" + FileManager.byteToMB(progress[1]) + " / " + FileManager.byteToMB(fileSize) + "MB");
				} else if (progress[0] == 1) {//분석중
					v.setPlayText(lang(R.string.analyzing));
					v.updateFlagColor(color(R.color.orange));
				} else if (progress[0] == -1) {//실패
					v.setPlayText(lang(R.string.failed));
					v.updateFlagColor(color(R.color.red));
					v.setStatus(true);
				} else if (progress[0] == 2) {//완료
					v.setPlayText("");
					v.updateFlagColor(color(R.color.green));
					v.togglePlay(false);
				}
			}

			@Override
			protected void onPostExecute(String unused) {

			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	@Override
	public void onResume() {
		super.onResume();
		initVar();

		getStoreCount.setOnChangeListener(data -> SettingManager.PrevStoreCount.save(FBStoreActivity.this, data));
	}

	@Override
	public void onPause() {
		super.onPause();

		getStoreCount.setOnChangeListener(null);
	}

	@Override
	public void onBackPressed() {
		if (downloadCount > 0)
			showToast(R.string.canNotQuitWhileDownloading);
		else
			super.onBackPressed();
	}

	class Pack {
		PackViewSimple packViewSimple;
		fbStore fbStore;
		int flagColors;
		boolean isDownloaded;
		boolean isDownloading;

		public Pack(PackViewSimple packViewSimple, com.kimjisub.launchpad.fb.fbStore fbStore) {
			this.packViewSimple = packViewSimple;
			this.fbStore = fbStore;
		}
	}
}
