package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.kimjisub.design.PackView;
import com.kimjisub.launchpad.fb.fbStore;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.Unipack;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;

import static com.kimjisub.launchpad.manage.Tools.log;
import static com.kimjisub.launchpad.manage.Tools.logErr;
import static com.kimjisub.launchpad.manage.UIManager.dpToPx;

public class Store extends BaseActivity {
	LinearLayout LL_list;
	
	String UnipackRootURL;
	
	int downloadCount = 0;
	
	Networks.GetStoreCount getStoreCount = new Networks.GetStoreCount();
	
	void initVar() {
		LL_list = findViewById(R.id.list);
		
		UnipackRootURL = SaveSetting.IsUsingSDCard.URL(Store.this);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_store);
		initVar();
		
		initUI();
		getStoreCount.run();
	}
	
	ArrayList<PackView> PV_items;
	ArrayList<fbStore> DStoreDatas;
	
	void initUI() {
		LL_list.removeAllViews();
		PV_items = new ArrayList<>();
		DStoreDatas = new ArrayList<>();
		
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
					
					
					@SuppressLint("ResourceType") String[] infoTitles = new String[]{
						getResources().getString(R.string.downloadCount)
					};
					String[] infoContents = new String[]{
						(new DecimalFormat("#,##0")).format(d.downloadCount)
					};
					@SuppressLint("ResourceType") String[] btnTitles = new String[]{
					};
					int[] btnColors = new int[]{
					};
					
					final PackView packView = new PackView(Store.this)
						.setFlagColor(isDownloaded ? getResources().getColor(R.color.green) : getResources().getColor(R.color.red))
						.setTitle(d.title)
						.setSubTitle(d.producerName)
						.setInfos(infoTitles, infoContents)
						.setBtns(btnTitles, btnColors)
						.setOptions(lang(R.string.LED_), lang(R.string.autoPlay_))
						.setOptionBools(d.isLED, d.isAutoPlay)
						.setPlayImageShow(false)
						.setOnEventListener(new PackView.OnEventListener() {
							@Override
							public void onViewClick(PackView v) {
								if (v.getStatus())
									itemClicked(v, d.index);
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
								switch (index) {
									case 0:
										//deleteUnipack(unipack);
										//v.toggleDetail(2);
										break;
									case 1:
										//editUnipack(unipack);
										break;
								}
							}
						})
						.setStatus(!isDownloaded);
					
					
					final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					int left = dpToPx(Store.this, 16);
					int top = 0;
					int right = dpToPx(Store.this, 16);
					int bottom = dpToPx(Store.this, 10);
					lp.setMargins(left, top, right, bottom);
					PV_items.add(packView);
					LL_list.addView(packView, 0, lp);
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
					
					PackView itemStore = PV_items.get(i);
					itemStore.setTitle(d.title)
						.setSubTitle(d.producerName)
						.setOptionBools(d.isLED, d.isAutoPlay)
						.updateInfo(0, (new DecimalFormat("#,##0")).format(d.downloadCount));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).run();
	}
	
	void addErrorItem() {
		String title = lang(Store.this, R.string.errOccur);
		String subTitle = lang(Store.this, R.string.UnableToAccessServer);
		
		PackView packView = PackView.errItem(Store.this, title, subTitle, null);
		
		
		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		int left = dpToPx(Store.this, 16);
		int top = 0;
		int right = dpToPx(Store.this, 16);
		int bottom = dpToPx(Store.this, 10);
		lp.setMargins(left, top, right, bottom);
		LL_list.addView(packView, lp);
	}
	
	@SuppressLint("StaticFieldLeak")
	void itemClicked(final PackView v, final int i) {
		log("itemClicked(" + i + ")");
		
		v.togglePlay(true);
		v.updateFlagColor(getResources().getColor(R.color.dark5));
		v.setStatus(false);
		v.setPlayText("0%");
		
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
					v.setPlayText((int) ((float) progress[1] / fileSize * 100) + "%");
				} else if (progress[0] == 1) {//분석중
					v.setPlayText(lang(R.string.analyzing));
					v.updateFlagColor(getResources().getColor(R.color.orange));
				} else if (progress[0] == -1) {//실패
					v.setPlayText(lang(R.string.failed));
					v.updateFlagColor(getResources().getColor(R.color.red));
					v.setStatus(true);
				} else if (progress[0] == 2) {//완료
					v.setPlayText("");
					v.updateFlagColor(getResources().getColor(R.color.green));
					v.togglePlay(false);
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
		initVar();
		
		getStoreCount.setOnChangeListener(new Networks.GetStoreCount.onChangeListener() {
			@Override
			public void onChange(long data) {
				SaveSetting.PrevStoreCount.save(Store.this, data);
			}
		});
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		getStoreCount.setOnChangeListener(null);
	}
	
	@Override
	public void onBackPressed() {
		if (downloadCount > 0)
			Toast.makeText(Store.this, lang(R.string.canNotQuitWhileDownloading), Toast.LENGTH_SHORT).show();
		else
			super.onBackPressed();
	}
}
