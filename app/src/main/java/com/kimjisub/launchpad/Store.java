package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.kimjisub.launchpad.fb.FsStore;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Log;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.SettingManager;
import com.kimjisub.launchpad.manage.Unipack;
import com.kimjisub.unipad.designkit.PackView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Store extends BaseActivity {
	
	// UI
	LinearLayout LL_list;
	
	// Vars
	String UnipackRootURL;
	File folder;
	int downloadCount = 0;
	String[] downloadedProjectList;
	Map<String, PackItem> MAP_packItem;
	ArrayList<String> AL_packList;
	
	// FireBase
	FirebaseFirestore db;
	Networks.GetStoreCount getStoreCount = new Networks.GetStoreCount();
	
	class PackItem {
		PackView packView;
		FsStore fsStore;
		
		public PackItem(PackView packView, FsStore fsStore) {
			this.packView = packView;
			this.fsStore = fsStore;
		}
		
	}
	
	void initVar() {
		// UI
		LL_list = findViewById(R.id.list);
		
		// Vars
		UnipackRootURL = SettingManager.IsUsingSDCard.URL(Store.this);
		folder = new File(UnipackRootURL);
		MAP_packItem = new HashMap<>();
		AL_packList = new ArrayList<>();
		
		if (folder.isDirectory()) {
			downloadedProjectList = new String[folder.listFiles().length];
			File[] files = folder.listFiles();
			for (int i = 0; i < files.length; i++)
				downloadedProjectList[i] = files[i].getName();
		} else {
			downloadedProjectList = new String[0];
			folder.mkdir();
		}
	}
	
	// =========================================================================================
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_store);
		initVar();
		
		initList();
		getStoreCount.run();
	}
	
	
	void initList() {
		LL_list.removeAllViews();
		
		addErrorItem();
		
		db = FirebaseFirestore.getInstance();
		
		db.collection("Unipack-Store")
			.addSnapshotListener((value, e) -> {
				if (e != null) {
					Log.firebase("Listen failed." + e);
					return;
				}
				
				for (DocumentChange dc : value.getDocumentChanges()) {
					QueryDocumentSnapshot document = dc.getDocument();
					
					try {
						String key = document.getId();
						
						FsStore item = new FsStore(document);
						
						
						switch (dc.getType()) {
							
							case ADDED:
								added(key, item);
								Log.firebase("New: " + key);
								break;
							case MODIFIED:
								modified(key, item);
								Log.firebase("Modified: " + key);
								break;
							case REMOVED:
								removed(key);
								Log.firebase("Removed: " + key);
								break;
						}
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
				
			});
	}
	
	void added(String key, FsStore d) throws Exception {
		if (MAP_packItem.size() == 0)
			LL_list.removeAllViews();
		
		
		boolean _isDownloaded = false;
		for (String downloadedProject : downloadedProjectList) {
			if (d.code.equals(downloadedProject)) {
				_isDownloaded = true;
				break;
			}
		}
		final boolean isDownloaded = _isDownloaded;
		
		final PackView packView = new PackView(Store.this)
			.setFlagColor(isDownloaded ? color(R.color.green) : color(R.color.red))
			.setTitle(d.title)
			.setSubTitle(d.producerName)
			.addInfo(lang(R.string.downloadCount), (new DecimalFormat("#,##0")).format(d.downloadCount))
			.setOption1(lang(R.string.LED_), d.isLED)
			.setOption2(lang(R.string.autoPlay_), d.isAutoPlay)
			.setPlayImageShow(false)
			.setOnEventListener(new PackView.OnEventListener() {
				@Override
				public void onViewClick(PackView v) {
					if (v.getStatus())
						itemClicked(v, key);
				}
				
				@Override
				public void onViewLongClick(PackView v) {
					v.toggleDetail();
				}
				
				@Override
				public void onPlayClick(PackView v) {
				}
				
				@Override
				public void onFunctionBtnClick(PackView v, int index) {
				}
			})
			.setStatus(!isDownloaded);
		
		
		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		int left = dpToPx(16);
		int top = 0;
		int right = dpToPx(16);
		int bottom = dpToPx(10);
		lp.setMargins(left, top, right, bottom);
		
		
		MAP_packItem.put(key, new PackItem(packView, d));
		LL_list.addView(packView, 0, lp);
	}
	
	void modified(String key, FsStore d) throws Exception {
		PackItem packItem = MAP_packItem.get(key);
		
		
		packItem.packView.setTitle(d.title)
			.setSubTitle(d.producerName)
			.setOption1(lang(R.string.LED_), d.isLED)
			.setOption2(lang(R.string.autoPlay_), d.isAutoPlay)
			.updateInfo(0, (new DecimalFormat("#,##0")).format(d.downloadCount));
		
		packItem.fsStore = d;
		
		
		MAP_packItem.put(key, packItem);
	}
	
	void removed(String key) throws Exception {
		MAP_packItem.remove(key);
	}
	
	
	void addErrorItem() {
		String title = lang(R.string.errOccur);
		String subTitle = lang(R.string.UnableToAccessServer);
		
		PackView packView = PackView.errItem(Store.this, title, subTitle, null);
		
		
		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		int left = dpToPx(16);
		int top = 0;
		int right = dpToPx(16);
		int bottom = dpToPx(10);
		lp.setMargins(left, top, right, bottom);
		LL_list.addView(packView, lp);
	}
	
	// ========================================================================================= Activity
	
	@SuppressLint("StaticFieldLeak")
	void itemClicked(final PackView v, final String key) {
		Log.log("itemClicked(" + key + ")");
		
		v.togglePlay(true);
		v.updateFlagColor(color(R.color.gray1));
		v.setStatus(false);
		v.setPlayText("0%");
		
		(new AsyncTask<String, Long, String>() {
			
			int fileSize;
			String UnipackZipURL;
			String UnipackURL;
			
			String code;
			String url;
			
			@Override
			protected void onPreExecute() {
				Store.this.downloadCount++;
				
				url = MAP_packItem.get(key).fsStore.url;
				
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
				
				Networks.sendGet("https://us-central1-unipad-e41ab.cloudfunctions.net/increaseDownloadCount/" + code);
				try {
					
					URL url = new URL(this.url);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setConnectTimeout(5000);
					connection.setReadTimeout(5000);
					
					fileSize = connection.getContentLength();
					Log.log(this.url);
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
				Store.this.downloadCount--;
				
				return null;
			}
			
			@Override
			protected void onProgressUpdate(Long... progress) {
				if (progress[0] == 0) {//다운중
					v.setPlayText((int) ((float) progress[1] / fileSize * 100) + "%");
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
		
		getStoreCount.setOnChangeListener(data -> SettingManager.PrevStoreCount.save(Store.this, data));
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
}
