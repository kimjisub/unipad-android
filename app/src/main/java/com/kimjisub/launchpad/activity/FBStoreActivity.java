package com.kimjisub.launchpad.activity;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.kimjisub.design.PackViewSimple;
import com.kimjisub.launchpad.BuildConfig;
import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.adapter.StoreAdapter;
import com.kimjisub.launchpad.adapter.StoreItem;
import com.kimjisub.launchpad.databinding.ActivityStoreBinding;
import com.kimjisub.launchpad.manager.PreferenceManager;
import com.kimjisub.launchpad.manager.Unipack;
import com.kimjisub.launchpad.network.Networks;
import com.kimjisub.launchpad.network.fb.fbStore;
import com.kimjisub.manager.FileManager;
import com.kimjisub.manager.Log;

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
	ActivityStoreBinding b;

	Networks.FirebaseManager firebase_store;
	Networks.FirebaseManager firebase_storeCount;

	ArrayList<StoreItem> list;
	StoreAdapter adapter;
	File[] F_UniPackList;

	void initVar(boolean onFirst) {
		if (onFirst) {
			firebase_store = new Networks.FirebaseManager("store");
			firebase_storeCount = new Networks.FirebaseManager("storeCount");
			list = new ArrayList<>();
			adapter = new StoreAdapter(FBStoreActivity.this, list, new StoreAdapter.EventListener() {

				@Override
				public void onViewClick(StoreItem item, PackViewSimple v) {
					togglePlay(item);
				}

				@Override
				public void onViewLongClick(StoreItem item, PackViewSimple v) {

				}

				@Override
				public void onPlayClick(StoreItem item, PackViewSimple v) {
					if (!item.isDownloaded && !item.isDownloading)
						startDownload(getPackItemByCode(item.fbStore.code));
				}
			});

			DividerItemDecoration divider = new DividerItemDecoration(FBStoreActivity.this, DividerItemDecoration.VERTICAL);
			divider.setDrawable(getResources().getDrawable(R.drawable.border_divider));
			b.recyclerView.addItemDecoration(divider);
			b.recyclerView.setHasFixedSize(false);
			b.recyclerView.setLayoutManager(new LinearLayoutManager(FBStoreActivity.this));
			//b.recyclerView.setItemAnimator(null);
			b.recyclerView.setAdapter(adapter);
		}
		F_UniPackList = getUniPackDirList();
	}

	// =============================================================================================

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		b = setContentViewBind(R.layout.activity_store);
		initVar(true);

		firebase_store.setEventListener(new ChildEventListener() {
			@Override
			public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
				Log.test("onChildAdded: " + s);

				try {
					fbStore d = dataSnapshot.getValue(fbStore.class);

					boolean isDownloaded = false;
					for (File dir : F_UniPackList) {
						if (d.code.equals(dir.getName())) {
							isDownloaded = true;
							break;
						}
					}


					list.add(0, new StoreItem(d, isDownloaded));
					adapter.notifyItemInserted(0);
					b.errItem.setVisibility(list.size() == 0 ? View.VISIBLE : View.GONE);


					updatePanelMain();
				} catch (Exception e) {
					e.printStackTrace();
				}
				Log.test("onChildAdded: ");
			}

			@Override
			public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
				Log.test("onChildChanged: " + s);

				try {
					fbStore d = dataSnapshot.getValue(fbStore.class);
					StoreItem item = getPackItemByCode(d.code);
					item.fbStore = d;
					adapter.notifyItemChanged(list.indexOf(item), "update");

					int selectedIndex = getSelectedIndex();
					if (selectedIndex != -1) {
						String changeCode = item.fbStore.code;
						String selectedCode = list.get(selectedIndex).fbStore.code;
						if (changeCode.equals(selectedCode))
							updatePanelPack(item);
					}


				} catch (Exception e) {
					e.printStackTrace();
				}
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
				PreferenceManager.PrevStoreCount.save(FBStoreActivity.this, data);
			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {

			}
		});
	}

	// ============================================================================================= List Manage

	void togglePlay(StoreItem target) {
		try {
			for (StoreItem item : list) {
				PackViewSimple packViewSimple = item.packViewSimple;

				if (target != null && item.fbStore.code.equals(target.fbStore.code))
					item.isToggle = !item.isToggle;
				else
					item.isToggle = false;

				if (packViewSimple != null)
					packViewSimple.toggle(item.isToggle);
			}

			updatePanel();

		} catch (ConcurrentModificationException e) {
			e.printStackTrace();
		}
	}

	int getSelectedIndex() {
		int index = -1;

		int i = 0;
		for (StoreItem item : list) {
			if (item.isToggle) {
				index = i;
				break;
			}
			i++;
		}

		return index;
	}

	StoreItem getPackItemByCode(String code) {
		StoreItem ret = null;
		for (StoreItem item : list)
			if (item.fbStore.code.equals(code)) {
				ret = item;
				break;
			}
		return ret;
	}

	int getDownloadingCount() {
		int count = 0;
		for (StoreItem item : list) {
			if (item.isDownloading)
				count++;
		}

		return count;
	}

	int getDownloadedCount() {
		int count = 0;
		for (StoreItem item : list) {
			if (item.isDownloaded)
				count++;
		}

		return count;
	}

	@SuppressLint("StaticFieldLeak")
	void startDownload(StoreItem item) {
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

		packViewSimple.animateFlagColor(color(R.color.gray1));
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

					byte[] data = new byte[1024];

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
					packViewSimple.animateFlagColor(color(R.color.orange));
				} else if (progress[0] == -1) {//실패
					packViewSimple.setPlayText(lang(R.string.failed));
					packViewSimple.animateFlagColor(color(R.color.red));
				} else if (progress[0] == 2) {//완료
					packViewSimple.setPlayText(lang(R.string.downloaded));
					packViewSimple.animateFlagColor(color(R.color.green));
					item.isDownloaded = true;
					updatePanel();
				}
			}

			@Override
			protected void onPostExecute(String unused) {

			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	// ============================================================================================= panel

	void updatePanel() {
		Log.test("panel");
		int playIndex = getSelectedIndex();
		Animation animation = AnimationUtils.loadAnimation(FBStoreActivity.this, playIndex != -1 ? R.anim.panel_in : R.anim.panel_out);
		animation.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {

				b.panelPack.setVisibility(View.VISIBLE);
				b.panelPack.setAlpha(1);
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				b.panelPack.setVisibility(playIndex != -1 ? View.VISIBLE : View.INVISIBLE);
				b.panelPack.setAlpha(playIndex != -1 ? 1 : 0);
			}

			@Override
			public void onAnimationRepeat(Animation animation) {

			}
		});

		if (playIndex == -1)
			updatePanelMain();
		else
			updatePanelPack(list.get(playIndex));

		int visibility = b.panelPack.getVisibility();
		if ((visibility == View.VISIBLE && playIndex == -1)
				|| (visibility == View.INVISIBLE && playIndex != -1))
			b.panelPack.startAnimation(animation);
	}

	void updatePanelMain() {
		Log.test("panel main");
		b.panelTotal.b.customLogo.setImageResource(R.drawable.custom_logo);
		b.panelTotal.b.version.setText(BuildConfig.VERSION_NAME);
		b.panelTotal.b.storeCount.setText(list.size() + "");
		b.panelTotal.b.downloadedCount.setText(getDownloadedCount() + "");
	}

	void updatePanelPack(StoreItem item) {
		Log.test("panel pack");
		fbStore fbStore = item.fbStore;

		b.panelPack.updateTitle(fbStore.title);
		b.panelPack.updateSubTitle(fbStore.producerName);
		b.panelPack.updateDownloadCount(fbStore.downloadCount + "");
	}

	// ============================================================================================= Activity

	@Override
	public void onBackPressed() {
		if (getSelectedIndex() != -1)
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

		list.clear();
		adapter.notifyDataSetChanged();
		b.errItem.setVisibility(list.size() == 0 ? View.VISIBLE : View.GONE);

		togglePlay(null);
		updatePanel();

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
