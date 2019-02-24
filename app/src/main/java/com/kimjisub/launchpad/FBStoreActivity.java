package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.kimjisub.launchpad.networks.Networks;
import com.kimjisub.launchpad.networks.fb.fbStore;
import com.kimjisub.launchpad.utils.FileManager;
import com.kimjisub.launchpad.utils.Log;
import com.kimjisub.launchpad.utils.SettingManager;
import com.kimjisub.launchpad.utils.Unipack;
import com.kimjisub.unipad.designkit.PackViewSimple;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;

public class FBStoreActivity extends BaseActivity {

	LinearLayout LL_list;
	RelativeLayout RL_panel_total;
	TextView TV_panel_total_version;
	TextView TV_panel_total_unipackStoreCount;
	TextView TV_panel_total_unipackDownloadedCount;
	RelativeLayout RL_panel_pack;
	ImageView IV_panel_pack_star;
	ImageView IV_panel_pack_bookmark;
	ImageView IV_panel_pack_edit;
	TextView TV_panel_pack_title;
	TextView TV_panel_pack_subTitle;
	TextView TV_panel_pack_path;
	TextView TV_panel_pack_scale;
	TextView TV_panel_pack_chainCount;
	TextView TV_panel_pack_soundCount;
	TextView TV_panel_pack_ledCount;
	TextView TV_panel_pack_fileSize;
	TextView TV_panel_pack_openCount;
	TextView TV_panel_pack_padTouchCount;
	ImageView IV_panel_pack_youtube;
	ImageView IV_panel_pack_website;
	ImageView IV_panel_pack_func;
	ImageView IV_panel_pack_delete;

	Networks.FirebaseManager firebase_store;
	Networks.FirebaseManager firebase_storeCount;
	ArrayList<PackItem> P_list;
	File[] F_UniPackList;

	void initVar(boolean onFirst) {
		LL_list = findViewById(R.id.list);
		RL_panel_total = findViewById(R.id.panel_total);
		TV_panel_total_version = findViewById(R.id.panel_total_version);
		TV_panel_total_unipackStoreCount = findViewById(R.id.panel_total_unipackStoreCount);
		TV_panel_total_unipackDownloadedCount = findViewById(R.id.panel_total_unipackDownloadedCount);
		RL_panel_pack = findViewById(R.id.panel_pack);
		IV_panel_pack_star = findViewById(R.id.panel_pack_star);
		IV_panel_pack_bookmark = findViewById(R.id.panel_pack_bookmark);
		IV_panel_pack_edit = findViewById(R.id.panel_pack_edit);
		TV_panel_pack_title = findViewById(R.id.panel_pack_title);
		TV_panel_pack_subTitle = findViewById(R.id.panel_pack_subTitle);
		TV_panel_pack_path = findViewById(R.id.panel_pack_path);
		TV_panel_pack_scale = findViewById(R.id.panel_pack_scale);
		TV_panel_pack_chainCount = findViewById(R.id.panel_pack_chainCount);
		TV_panel_pack_soundCount = findViewById(R.id.panel_pack_soundCount);
		TV_panel_pack_ledCount = findViewById(R.id.panel_pack_ledCount);
		TV_panel_pack_fileSize = findViewById(R.id.panel_pack_fileSize);
		TV_panel_pack_openCount = findViewById(R.id.panel_pack_openCount);
		TV_panel_pack_padTouchCount = findViewById(R.id.panel_pack_padTouchCount);
		IV_panel_pack_youtube = findViewById(R.id.panel_pack_youtube);
		IV_panel_pack_website = findViewById(R.id.panel_pack_website);
		IV_panel_pack_func = findViewById(R.id.panel_pack_func);
		IV_panel_pack_delete = findViewById(R.id.panel_pack_delete);
		TV_panel_total_version.setText(BuildConfig.VERSION_NAME);
		TV_panel_pack_title.setSelected(true);
		TV_panel_pack_subTitle.setSelected(true);
		TV_panel_pack_path.setSelected(true);

		if (onFirst) {
			firebase_store = new Networks.FirebaseManager("store");
			firebase_storeCount = new Networks.FirebaseManager("storeCount");
			P_list = new ArrayList<>();
		}
		F_UniPackList = getUniPackDirList();
	}

	// =============================================================================================

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_store);
		initVar(true);

		firebase_store.setEventListener(new ChildEventListener() {
			@Override
			public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
				try {
					fbStore d = dataSnapshot.getValue(fbStore.class);
					if (P_list.size() == 0)
						LL_list.removeAllViews();
					d.index = P_list.size();


					boolean _isDownloaded = false;
					for (File dir : F_UniPackList) {
						if (d.code.equals(dir.getName())) {
							_isDownloaded = true;
							break;
						}
					}
					final boolean isDownloaded = _isDownloaded;

					final PackViewSimple packViewSimple = new PackViewSimple(FBStoreActivity.this)
							.setFlagColor(color(isDownloaded ? R.color.green : R.color.red))
							.setTitle(d.title)
							.setSubTitle(d.producerName)
							.setOption1(lang(R.string.LED_), d.isLED)
							.setOption2(lang(R.string.autoPlay_), d.isAutoPlay)
							.setPlayImageShow(false)
							.setPlayText(lang(isDownloaded ? R.string.downloaded : R.string.download))
							.setOnEventListener(new PackViewSimple.OnEventListener() {
								@Override
								public void onViewClick(PackViewSimple v) {
									togglePlay(d.code);
								}

								@Override
								public void onViewLongClick(PackViewSimple v) {
								}

								@Override
								public void onPlayClick(PackViewSimple v) {
									PackItem item = getPackItemByCode(d.code);
									if (!item.isDownloaded && !item.isDownloading)
										startDownload(getPackItemByCode(d.code));
								}
							});


					final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					int left = dpToPx(16);
					int top = 0;
					int right = dpToPx(16);
					int bottom = dpToPx(10);
					lp.setMargins(left, top, right, bottom);

					P_list.add(new PackItem(packViewSimple, d, isDownloaded, false));
					LL_list.addView(packViewSimple, 0, lp);

					updatePanelMain(false);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
				try {
					fbStore d = dataSnapshot.getValue(fbStore.class);
					PackItem item = getPackItemByCode(d.code);
					fbStore fbStore = item.fbStore;
					PackViewSimple packViewSimple = item.packViewSimple;

					if (fbStore.code.equals(d.code)) {
						packViewSimple.setTitle(d.title)
								.setSubTitle(d.producerName)
								.setOption1(lang(R.string.LED_), d.isLED)
								.setOption2(lang(R.string.autoPlay_), d.isAutoPlay);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				updatePanel(false);
			}

			@Override
			public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
			}

			@Override
			public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {
			}
		});

		firebase_storeCount.setEventListener(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
				Long data = dataSnapshot.getValue(Long.class);
				SettingManager.PrevStoreCount.save(FBStoreActivity.this, data);
			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {

			}
		});
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

	// ============================================================================================= List Manage

	class PackItem {
		PackViewSimple packViewSimple;
		fbStore fbStore;
		boolean isDownloaded;
		boolean isDownloading;

		public PackItem(PackViewSimple packViewSimple, fbStore fbStore, boolean isDownloaded, boolean isDownloading) {
			this.packViewSimple = packViewSimple;
			this.fbStore = fbStore;
			this.isDownloaded = isDownloaded;
			this.isDownloading = isDownloading;
		}
	}

	void togglePlay(String code) {
		try {
			for (PackItem item : P_list) {
				fbStore fbStore = item.fbStore;
				PackViewSimple packViewSimple = item.packViewSimple;

				if (fbStore.code.equals(code))
					packViewSimple.togglePlay();
				else
					packViewSimple.togglePlay(false);
			}

			updatePanel(false);

		} catch (ConcurrentModificationException e) {
			e.printStackTrace();
		}
	}

	int getPlayIndex() {
		int index = -1;

		int i = 0;
		for (PackItem item : P_list) {
			if (item.packViewSimple.isPlay()) {
				index = i;
				break;
			}
			i++;
		}

		return index;
	}

	PackItem getPackItemByCode(String code) {
		PackItem ret = null;
		for (PackItem item : P_list)
			if (item.fbStore.code.equals(code)) {
				ret = item;
				break;
			}
		return ret;
	}

	int getDownloadingCount() {
		int count = 0;
		for (PackItem item : P_list) {
			if (item.isDownloading)
				count++;
		}

		return count;
	}

	int getDownloadedCount() {
		int count = 0;
		for (PackItem item : P_list) {
			if (item.isDownloaded)
				count++;
		}

		return count;
	}

	@SuppressLint("StaticFieldLeak")
	void startDownload(PackItem item) {
		fbStore fbStore = item.fbStore;
		PackViewSimple packViewSimple = item.packViewSimple;

		String code = fbStore.code;
		String title = fbStore.title;
		String producerName = fbStore.producerName;
		boolean isAutoPlay = fbStore.isAutoPlay;
		boolean isLED = fbStore.isLED;
		int downloadCount = fbStore.downloadCount;
		String URL = fbStore.URL;

		File F_UniPackZip = FileManager.makeNextPath(F_UniPackRootExt, code, ".zip");
		File F_UniPack = new File(F_UniPackRootExt, code);

		packViewSimple.updateFlagColor(color(R.color.gray1));
		packViewSimple.setPlayText("0%");

		(new AsyncTask<String, Long, String>() {


			@Override
			protected void onPreExecute() {
				item.isDownloading = true;

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

					int fileSize = connection.getContentLength();
					Log.log(URL);
					Log.log("fileSize : " + fileSize);
					fileSize = fileSize == -1 ? 104857600 : fileSize;

					InputStream input = new BufferedInputStream(url.openStream());
					OutputStream output = new FileOutputStream(F_UniPackZip);

					byte data[] = new byte[1024];

					long total = 0;

					int count;
					int skip = 100;
					while ((count = input.read(data)) != -1) {
						total += count;
						skip--;
						if (skip == 0) {
							publishProgress(0L, total, (long) fileSize);
							skip = 100;
						}
						output.write(data, 0, count);
					}

					output.flush();
					output.close();
					input.close();
					publishProgress(1L);

					try {
						FileManager.unZipFile(F_UniPackZip.getPath(), F_UniPack.getPath());
						Unipack unipack = new Unipack(F_UniPack, true);
						if (unipack.CriticalError) {
							Log.err(unipack.ErrorDetail);
							publishProgress(-1L);
							FileManager.deleteDirectory(F_UniPack);
						} else
							publishProgress(2L);

					} catch (Exception e) {
						publishProgress(-1L);
						FileManager.deleteDirectory(F_UniPack);
						e.printStackTrace();
					}
					FileManager.deleteDirectory(F_UniPackZip);


				} catch (Exception e) {
					publishProgress(-1L);
					e.printStackTrace();
				}

				item.isDownloading = false;

				return null;
			}

			@Override
			protected void onProgressUpdate(Long... progress) {
				if (progress[0] == 0) {//다운중
					packViewSimple.setPlayText((int) ((float) progress[1] / progress[2] * 100) + "%\n" + FileManager.byteToMB(progress[1]) + " / " + FileManager.byteToMB(progress[2]) + "MB");
				} else if (progress[0] == 1) {//분석중
					packViewSimple.setPlayText(lang(R.string.analyzing));
					packViewSimple.updateFlagColor(color(R.color.orange));
				} else if (progress[0] == -1) {//실패
					packViewSimple.setPlayText(lang(R.string.failed));
					packViewSimple.updateFlagColor(color(R.color.red));
				} else if (progress[0] == 2) {//완료
					packViewSimple.setPlayText(lang(R.string.downloaded));
					packViewSimple.updateFlagColor(color(R.color.green));
					item.isDownloaded = true;
					updatePanel(false);
				}
			}

			@Override
			protected void onPostExecute(String unused) {

			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	// ============================================================================================= panel

	void updatePanel(boolean hardWork) {
		Log.test("updatePanel");
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

		if (playIndex == -1)
			updatePanelMain(hardWork);
		else
			updatePanelPack(hardWork);

		int visibility = RL_panel_pack.getVisibility();
		if ((visibility == View.VISIBLE && playIndex == -1)
				|| (visibility == View.INVISIBLE && playIndex != -1))
			RL_panel_pack.startAnimation(animation);
	}

	void updatePanelMain(boolean hardWork) {
		Log.test("main");
		TV_panel_total_unipackStoreCount.setText(P_list.size() + "");
		TV_panel_total_unipackDownloadedCount.setText(getDownloadedCount() + "");
	}

	void updatePanelPack(boolean hardWork) {
		Log.test("pack");
		PackItem item = P_list.get(getPlayIndex());
		PackViewSimple packViewSimple = item.packViewSimple;
		fbStore fbStore = item.fbStore;
		TV_panel_pack_title.setText(fbStore.title);
		TV_panel_pack_subTitle.setText(fbStore.producerName);
	}

	// ============================================================================================= Activity

	@Override
	public void onBackPressed() {
		if (getPlayIndex() != -1)
			togglePlay(null);
		else {
			if (getDownloadingCount() > 0)
				showToast(R.string.canNotQuitWhileDownloading);
			else
				super.onBackPressed();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		initVar(false);

		LL_list.removeAllViews();
		P_list.clear();
		addErrorItem();

		togglePlay(null);
		updatePanel(true);

		firebase_store.attachEventListener(true);
		firebase_storeCount.attachEventListener(true);
	}

	@Override
	public void onPause() {
		super.onPause();

		firebase_store.attachEventListener(false);
		firebase_storeCount.attachEventListener(false);
	}
}
