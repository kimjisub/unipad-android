package com.kimjisub.launchpad;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.anjlab.android.iab.v3.TransactionDetails;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;
import com.kimjisub.launchpad.manage.BillingCertification;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.LaunchpadDriver;
import com.kimjisub.launchpad.manage.Log;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.SettingManager;
import com.kimjisub.launchpad.manage.Unipack;
import com.kimjisub.launchpad.manage.db.manager.DB_Unipack;
import com.kimjisub.launchpad.manage.db.manager.DB_UnipackOpen;
import com.kimjisub.launchpad.manage.db.vo.UnipackOpenVO;
import com.kimjisub.launchpad.manage.db.vo.UnipackVO;
import com.kimjisub.unipad.designkit.FileExplorer;
import com.kimjisub.unipad.designkit.PackViewSimple;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.List;

import static com.kimjisub.launchpad.manage.Constant.AUTOPLAY_AUTOMAPPING_DELAY_PRESET;

public class MainActivity extends BaseActivity {

	// DB
	DB_Unipack DB_unipack;
	DB_UnipackOpen DB_unipackOpen;

	// Intro
	BillingCertification billingCertification;
	RelativeLayout RL_intro;
	TextView TV_version;

	// MainActivity
	AdView AV_adview;
	RelativeLayout RL_rootView;
	SwipeRefreshLayout SRL_scrollView;
	ScrollView SV_scrollView;
	LinearLayout LL_list;
	FloatingActionMenu FAM_floatingMenu;
	FloatingActionButton FAB_reconnectLaunchpad;
	FloatingActionButton FAB_loadUniPack;
	FloatingActionButton FAB_store;
	FloatingActionButton FAB_setting;
	LinearLayout LL_scale;
	LinearLayout LL_paddingScale;
	LinearLayout LL_testView;
	ValueAnimator VA_floatingAnimation;
	RelativeLayout RL_panel_total;
	TextView TV_panel_total_version;
	TextView TV_panel_total_unipackCount;
	TextView TV_panel_total_unipackCapacity;
	TextView TV_panel_total_openCount;
	TextView TV_panel_total_padtouchCount;
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

	String UnipackRootPath;
	int lastPlayIndex = -1;
	ArrayList<PackItem> P_list;
	Networks.GetStoreCount getStoreCount = new Networks.GetStoreCount();

	boolean isDoneIntro = false;
	boolean updateComplete = true;

	void initVar(boolean onFirst) {
		// DB
		DB_unipack = new DB_Unipack(MainActivity.this);
		DB_unipackOpen = new DB_UnipackOpen(MainActivity.this);

		// Intro
		RL_intro = findViewById(R.id.intro);
		TV_version = findViewById(R.id.version);

		// MainActivity
		AV_adview = findViewById(R.id.adView);
		RL_rootView = findViewById(R.id.rootView);
		SRL_scrollView = findViewById(R.id.swipeRefreshLayout);
		SV_scrollView = findViewById(R.id.scrollView);
		LL_list = findViewById(R.id.list);
		FAM_floatingMenu = findViewById(R.id.floatingMenu);
		FAB_reconnectLaunchpad = findViewById(R.id.fab_reconnectLaunchpad);
		FAB_loadUniPack = findViewById(R.id.fab_loadUniPack);
		FAB_store = findViewById(R.id.fab_store);
		FAB_setting = findViewById(R.id.fab_setting);
		LL_scale = findViewById(R.id.scale);
		LL_paddingScale = findViewById(R.id.paddingScale);
		LL_testView = findViewById(R.id.testView);
		RL_panel_total = findViewById(R.id.panel_total);
		TV_panel_total_version = findViewById(R.id.panel_total_version);
		TV_panel_total_unipackCount = findViewById(R.id.panel_total_unipackCount);
		TV_panel_total_unipackCapacity = findViewById(R.id.panel_total_unipackCapacity);
		TV_panel_total_openCount = findViewById(R.id.panel_total_openCount);
		TV_panel_total_padtouchCount = findViewById(R.id.panel_total_padTouchCount);
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
		TV_panel_pack_title.setSelected(true);
		TV_panel_pack_subTitle.setSelected(true);
		TV_panel_pack_path.setSelected(true);

		// animation
		if (onFirst) {
			int color1 = color(R.color.red);
			int color2 = color(R.color.orange);
			VA_floatingAnimation = ObjectAnimator.ofObject(new ArgbEvaluator(), color2, color1);
			VA_floatingAnimation.setDuration(300);
			VA_floatingAnimation.setRepeatCount(Animation.INFINITE);
			VA_floatingAnimation.setRepeatMode(ValueAnimator.REVERSE);
			VA_floatingAnimation.addUpdateListener(valueAnimator -> {
				int color = (int) valueAnimator.getAnimatedValue();
				FAM_floatingMenu.setMenuButtonColorNormal(color);
				FAM_floatingMenu.setMenuButtonColorPressed(color);
				FAB_store.setColorNormal(color);
				FAB_store.setColorPressed(color);
			});
		}


		// var
		UnipackRootPath = SettingManager.IsUsingSDCard.getPath(MainActivity.this);
		if (onFirst) {
			P_list = new ArrayList<>();
		}
	}

	// =============================================================================================

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initVar(true);
		if (BillingCertification.isShowAds())
			initAdmob();

		startIntro();
	}

	void startIntro() {
		billingCertification = new BillingCertification(MainActivity.this, new BillingCertification.BillingEventListener() {
			@Override
			public void onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details) {
			}

			@Override
			public void onPurchaseHistoryRestored() {
			}

			@Override
			public void onBillingError(int errorCode, @Nullable Throwable error) {
			}

			@Override
			public void onBillingInitialized() {
			}

			@Override
			public void onRefresh() {
				if (BillingCertification.isPurchaseRemoveAds() || BillingCertification.isPurchaseProTools()) {
					TV_version.setTextColor(color(R.color.orange));
				}

				if (BillingCertification.isShowAds()) {
					if (checkAdsCooltime()) {
						updateAdsCooltime();
						showAdmob();
					}
					AdRequest adRequest = new AdRequest.Builder().build();
					AV_adview.loadAd(adRequest);
				} else
					AV_adview.setVisibility(View.GONE);
			}
		});

		TV_version.setText(BuildConfig.VERSION_NAME);
		TV_panel_total_version.setText(BuildConfig.VERSION_NAME);

		TedPermission.with(this)
				.setPermissionListener(new PermissionListener() {
					@Override
					public void onPermissionGranted() {


						new Handler().postDelayed(() -> {
							RL_intro.setVisibility(View.GONE);
							startMain();
						}, 3000);
					}

					@Override
					public void onPermissionDenied(List<String> deniedPermissions) {
						finish();
					}
				})
				.setRationaleMessage(R.string.permissionRequire)
				.setDeniedMessage(R.string.permissionDenied)
				.setPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
				.check();
	}

	void startMain() {
		rescanScale(LL_scale, LL_paddingScale);

		SRL_scrollView.setOnRefreshListener(this::update);

		FAB_reconnectLaunchpad.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LaunchpadActivity.class)));

		FAB_loadUniPack.setOnClickListener(v -> new FileExplorer(MainActivity.this, SettingManager.FileExplorerPath.load(MainActivity.this))
				.setOnEventListener(new FileExplorer.OnEventListener() {
					@Override
					public void onFileSelected(String filePath) {
						loadUnipack(filePath);
					}

					@Override
					public void onPathChanged(String folderPath) {
						SettingManager.FileExplorerPath.save(MainActivity.this, folderPath);
					}
				})
				.show());

		FAB_store.setOnClickListener(v -> startActivityForResult(new Intent(MainActivity.this, FBStoreActivity.class), 0));

		FAB_store.setOnLongClickListener(view -> {
			//startActivityForResult(new Intent(MainActivity.this, FSStoreActivity.class), 0);
			return false;
		});

		FAB_setting.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingActivity.class)));

		FAM_floatingMenu.setOnMenuToggleListener(new FloatingActionMenu.OnMenuToggleListener() {
			Handler handler = new Handler();

			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					FAM_floatingMenu.close(true);
				}
			};

			@Override
			public void onMenuToggle(boolean opened) {
				if (opened)
					handler.postDelayed(runnable, 5000);
				else
					handler.removeCallbacks(runnable);
			}
		});

		IV_panel_pack_star.setOnClickListener(v -> {
			int playIndex = getPlayIndex();
			if (playIndex != -1) {
				PackItem item = P_list.get(playIndex);
				UnipackVO unipackVO = DB_unipack.getByPath(item.path);
				unipackVO.pin = !unipackVO.pin;
				DB_unipack.update(item.path, unipackVO);

				updatePanelPackOption();
			}
		});
		IV_panel_pack_bookmark.setOnClickListener(v -> {
			int playIndex = getPlayIndex();
			if (playIndex != -1) {
				PackItem item = P_list.get(playIndex);
				UnipackVO unipackVO = DB_unipack.getByPath(item.path);
				unipackVO.bookmark = !unipackVO.bookmark;
				DB_unipack.update(item.path, unipackVO);

				updatePanelPackOption();
			}
		});
		IV_panel_pack_edit.setOnClickListener(v ->{

		});
		IV_panel_pack_youtube.setOnClickListener(v -> {
			int playIndex = getPlayIndex();
			if (playIndex != -1) {
				PackItem item = P_list.get(playIndex);
				String website = "https://www.youtube.com/results?search_query=UniPad+" + item.unipack.title;
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(website)));
			}
		});
		IV_panel_pack_website.setOnClickListener(v -> {
			int playIndex = getPlayIndex();
			if (playIndex != -1) {
				PackItem item = P_list.get(playIndex);
				String website = item.unipack.website;
				if (website != null)
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(website)));
			}
		});
		IV_panel_pack_func.setOnClickListener(v -> {
			int playIndex = getPlayIndex();
			if (playIndex != -1) {
				PackItem item = P_list.get(playIndex);
				new AlertDialog.Builder(MainActivity.this)
						.setTitle(lang(R.string.warning))
						.setMessage(lang(R.string.doYouWantToRemapProject))
						.setPositiveButton(lang(R.string.accept), (dialog, which) -> {
							autoMapping(item.unipack);
						}).setNegativeButton(lang(R.string.cancel), null)
						.show();

			}
		});
		IV_panel_pack_delete.setOnClickListener(v -> {
			int playIndex = getPlayIndex();
			if (playIndex != -1) {
				PackItem item = P_list.get(playIndex);

				new AlertDialog.Builder(MainActivity.this)
						.setTitle(lang(R.string.warning))
						.setMessage(lang(R.string.doYouWantToDeleteProject))
						.setPositiveButton(lang(R.string.accept), (dialog, which) -> {
							deleteUnipack(item.unipack);
						}).setNegativeButton(lang(R.string.cancel), null)
						.show();
			}
		});

		isDoneIntro = true;

		checkThings();
		update();
		setDriver();
	}

	void checkThings() {
		versionCheck();
		newPackCheck();
	}

	void update() {
		lastPlayIndex = -1;
		if (!updateComplete)
			return;

		SRL_scrollView.setRefreshing(true);
		updateComplete = false;

		LL_list.removeAllViews();
		P_list.clear();

		togglePlay(null);
		updatePanel();

		new Thread(() -> {
			try {
				File projectFolder = new File(UnipackRootPath);

				if (projectFolder.isDirectory()) {

					File[] projectFiles = FileManager.sortByTime(projectFolder.listFiles());

					for (File project : projectFiles)
						addItemByFile(project);

					if (P_list.size() == 0)
						runOnUiThread(this::addErrorItem);

				} else {
					projectFolder.mkdir();
					runOnUiThread(this::addErrorItem);
				}

				File nomedia = new File(UnipackRootPath + "/.nomedia");
				if (!nomedia.isFile()) {
					try {
						(new FileWriter(nomedia)).close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} finally {
				runOnUiThread(() -> SRL_scrollView.setRefreshing(false));
				updateComplete = true;
			}
		}).start();
	}

	void addItemByFile(File file) {
		if (!file.isDirectory()) return;

		String path = UnipackRootPath + "/" + file.getName();
		Unipack unipack = new Unipack(path, false);
		UnipackVO unipackVO = DB_unipack.getOrCreateByPath(path);
		int flagColor;

		String title = unipack.title;
		String subTitle = unipack.producerName;

		if (unipack.CriticalError) {
			flagColor = color(R.color.red);
			title = lang(R.string.errOccur);
			subTitle = unipack.path;
		} else
			flagColor = color(R.color.skyblue);

		if (unipackVO.bookmark)
			flagColor = color(R.color.orange);


		final PackViewSimple packViewSimple = new PackViewSimple(MainActivity.this)
				.setFlagColor(flagColor)
				.setTitle(title)
				.setSubTitle(subTitle)
				.setOption1(lang(R.string.LED_), unipack.isKeyLED)
				.setOption2(lang(R.string.autoPlay_), unipack.isAutoPlay)
				.setOnEventListener(new PackViewSimple.OnEventListener() {
					@Override
					public void onViewClick(PackViewSimple v) {
						togglePlay(path);
					}

					@Override
					public void onViewLongClick(PackViewSimple v) {
					}

					@Override
					public void onPlayClick(PackViewSimple v) {
						rescanScale(LL_scale, LL_paddingScale);
						LaunchpadActivity.removeDriverListener(MainActivity.this);

						DB_unipackOpen.add(new UnipackOpenVO(path, new Date()));

						Intent intent = new Intent(MainActivity.this, PlayActivity.class);
						intent.putExtra("getPath", path);
						startActivity(intent);
					}
				});

		PackItem packItem = new PackItem(packViewSimple, unipack, path, flagColor);
		P_list.add(packItem);

		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		int left = dpToPx(16);
		int top = 0;
		int right = dpToPx(16);
		int bottom = dpToPx(10);
		lp.setMargins(left, top, right, bottom);
		runOnUiThread(() -> {
			LL_list.addView(packItem.packViewSimple, lp);
			Animation a = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_in);
			a.setInterpolator(AnimationUtils.loadInterpolator(MainActivity.this, android.R.anim.accelerate_decelerate_interpolator));
			packItem.packViewSimple.setAnimation(a);
			TV_panel_total_unipackCount.setText(P_list.size() + "");
		});


		//TODO 정렬
	}

	void addErrorItem() {
		String title = lang(R.string.unipackNotFound);
		String subTitle = lang(R.string.clickToAddUnipack);

		PackViewSimple packViewSimple = PackViewSimple.errItem(MainActivity.this, title, subTitle, new PackViewSimple.OnEventListener() {
			@Override
			public void onViewClick(PackViewSimple v) {
				startActivity(new Intent(MainActivity.this, FBStoreActivity.class));
			}

			@Override
			public void onViewLongClick(PackViewSimple v) {
			}

			@Override
			public void onPlayClick(PackViewSimple v) {
			}
		});


		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		int left = dpToPx(16);
		int top = 0;
		int right = dpToPx(16);
		int bottom = dpToPx(10);
		lp.setMargins(left, top, right, bottom);
		LL_list.addView(packViewSimple, lp);
	}

	// ============================================================================================= UniPack Work

	void deleteUnipack(final Unipack unipack) {
		FileManager.deleteFolder(unipack.path);
		update();
	}

	@SuppressLint("StaticFieldLeak")
	void autoMapping(Unipack uni) {
		final Unipack unipack = new Unipack(uni.path, true);


		if (unipack.isAutoPlay && unipack.autoPlayTable != null) {
			(new AsyncTask<String, String, String>() {

				ProgressDialog progressDialog;

				ArrayList<Unipack.AutoPlay> autoplay1;
				ArrayList<Unipack.AutoPlay> autoplay2;
				ArrayList<Unipack.AutoPlay> autoplay3;

				@Override
				protected void onPreExecute() {
					autoplay1 = new ArrayList<>();
					for (Unipack.AutoPlay e : unipack.autoPlayTable) {
						switch (e.func) {
							case Unipack.AutoPlay.ON:
								autoplay1.add(e);
								break;
							case Unipack.AutoPlay.OFF:
								break;
							case Unipack.AutoPlay.CHAIN:
								autoplay1.add(e);
								break;
							case Unipack.AutoPlay.DELAY:
								autoplay1.add(e);
								break;
						}
					}

					autoplay2 = new ArrayList<>();
					Unipack.AutoPlay prevDelay = new Unipack.AutoPlay(0, 0);
					for (Unipack.AutoPlay e : autoplay1) {
						switch (e.func) {
							case Unipack.AutoPlay.ON:
								if (prevDelay != null) {
									autoplay2.add(prevDelay);
									prevDelay = null;
								}
								autoplay2.add(e);
								break;
							case Unipack.AutoPlay.CHAIN:
								autoplay2.add(e);
								break;
							case Unipack.AutoPlay.DELAY:
								if (prevDelay != null)
									prevDelay.d += e.d;
								else
									prevDelay = e;
								break;
						}
					}

					progressDialog = new ProgressDialog(MainActivity.this);
					progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
					progressDialog.setTitle(lang(R.string.analyzing));
					progressDialog.setMessage(lang(R.string.wait));
					progressDialog.setCancelable(false);
					progressDialog.setMax(autoplay2.size());
					progressDialog.show();
					super.onPreExecute();
				}

				@Override
				protected String doInBackground(String... params) {

					autoplay3 = new ArrayList<>();
					int nextDuration = 1000;
					MediaPlayer mplayer = new MediaPlayer();
					for (Unipack.AutoPlay e : autoplay2) {
						try {
							switch (e.func) {
								case Unipack.AutoPlay.ON:
									int num = e.num % unipack.soundTable[e.currChain][e.x][e.y].size();
									nextDuration = FileManager.wavDuration(mplayer, unipack.soundTable[e.currChain][e.x][e.y].get(num).path);
									autoplay3.add(e);
									break;
								case Unipack.AutoPlay.CHAIN:
									autoplay3.add(e);
									break;
								case Unipack.AutoPlay.DELAY:
									e.d = nextDuration + AUTOPLAY_AUTOMAPPING_DELAY_PRESET;
									autoplay3.add(e);
									break;
							}
						} catch (Exception ee) {
							ee.printStackTrace();
						}
						publishProgress();
					}
					mplayer.release();

					StringBuilder stringBuilder = new StringBuilder();
					for (Unipack.AutoPlay e : autoplay3) {
						switch (e.func) {
							case Unipack.AutoPlay.ON:
								//int num = e.num % unipack.soundTable[e.currChain][e.x][e.y].size();
								stringBuilder.append("t ").append(e.x + 1).append(" ").append(e.y + 1).append("\n");
								break;
							case Unipack.AutoPlay.CHAIN:
								stringBuilder.append("c ").append(e.c + 1).append("\n");
								break;
							case Unipack.AutoPlay.DELAY:
								stringBuilder.append("d ").append(e.d).append("\n");
								break;
						}
					}
					try {
						File filePre = new File(unipack.path, "autoPlay");
						@SuppressLint("SimpleDateFormat") File fileNow = new File(unipack.path, "autoPlay_" + new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss").format(new Date(System.currentTimeMillis())));
						filePre.renameTo(fileNow);

						BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(unipack.path + "/autoPlay")));
						writer.write(stringBuilder.toString());
						writer.close();
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException ee) {
						ee.printStackTrace();
					}

					return null;
				}

				@Override
				protected void onProgressUpdate(String... progress) {
					if (progressDialog.isShowing())
						progressDialog.incrementProgressBy(1);
				}

				@Override
				protected void onPostExecute(String result) {
					super.onPostExecute(result);

					try {
						if (progressDialog != null && progressDialog.isShowing())
							progressDialog.dismiss();
						new AlertDialog.Builder(MainActivity.this)
								.setTitle(lang(R.string.success))
								.setMessage(lang(R.string.remapDone))
								.setPositiveButton(lang(R.string.accept), null)
								.show();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).execute();


		} else {
			new AlertDialog.Builder(MainActivity.this)
					.setTitle(lang(R.string.failed))
					.setMessage(lang(R.string.remapFail))
					.setPositiveButton(lang(R.string.accept), null)
					.show();
		}
	}

	@SuppressLint("StaticFieldLeak")
	void loadUnipack(final String UnipackZipPath) {

		(new AsyncTask<String, String, String>() {

			ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);

			String msg1;
			String msg2;

			@Override
			protected void onPreExecute() {

				progressDialog.setTitle(lang(R.string.analyzing));
				progressDialog.setMessage(lang(R.string.wait));
				progressDialog.setCancelable(false);
				progressDialog.show();
				super.onPreExecute();
			}

			@Override
			protected String doInBackground(String... params) {


				File file = new File(UnipackZipPath);
				String name = file.getName();
				String name_ = name.substring(0, name.lastIndexOf("."));

				String UnipackPath = FileManager.makeNextPath(UnipackRootPath, name_, "/");

				try {
					FileManager.unZipFile(UnipackZipPath, UnipackPath);
					Unipack unipack = new Unipack(UnipackPath, true);

					if (unipack.ErrorDetail == null) {
						msg1 = lang(R.string.analyzeComplete);
						msg2 = unipack.getInfoText(MainActivity.this);
					} else if (unipack.CriticalError) {
						msg1 = lang(R.string.analyzeFailed);
						msg2 = unipack.ErrorDetail;
						FileManager.deleteFolder(UnipackPath);
					} else {
						msg1 = lang(R.string.warning);
						msg2 = unipack.ErrorDetail;
					}

				} catch (IOException e) {
					msg1 = lang(R.string.analyzeFailed);
					msg2 = e.toString();
					FileManager.deleteFolder(UnipackPath);
				}

				return null;
			}

			@Override
			protected void onProgressUpdate(String... progress) {
			}

			@Override
			protected void onPostExecute(String result) {
				update();
				showDialog(msg1, msg2);
				progressDialog.dismiss();
				super.onPostExecute(result);
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	// ============================================================================================= List Manage

	class PackItem {
		PackViewSimple packViewSimple;
		Unipack unipack;
		String path;
		int flagColors;

		public PackItem(PackViewSimple packViewSimple, Unipack unipack, String path, int flagColors) {
			this.packViewSimple = packViewSimple;
			this.unipack = unipack;
			this.path = path;
			this.flagColors = flagColors;
		}
	}

	void togglePlay(int i) {
		togglePlay(P_list.get(i).path);
	}

	@SuppressLint("SetTextI18n")
	void togglePlay(String path) {
		try {
			int i = 0;
			for (PackItem packItem : P_list) {
				if (packItem.path.equals(path)) {
					packItem.packViewSimple.togglePlay(color(R.color.red), packItem.flagColors);
					lastPlayIndex = i;
				} else
					packItem.packViewSimple.togglePlay(false, color(R.color.red), packItem.flagColors);

				i++;
			}
			showSelectLPUI();

			updatePanel();

		} catch (ConcurrentModificationException e) {
			e.printStackTrace();
		}
	}

	int getPlayIndex() {
		int index = -1;

		int i = 0;
		for (PackItem packItem : P_list) {
			if (packItem.packViewSimple.isPlay()) {
				index = i;
				break;
			}
			i++;
		}

		return index;
	}

	// ============================================================================================= panel

	void updatePanel() {
		Log.test("updatePanel");
		int playIndex = getPlayIndex();
		Animation animation = AnimationUtils.loadAnimation(MainActivity.this, playIndex != -1 ? R.anim.panel_in : R.anim.panel_out);
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
			updatePanelMain();
		else
			updatePanelPack();

		int visibility = RL_panel_pack.getVisibility();
		if ((visibility == View.VISIBLE && playIndex == -1)
				|| (visibility == View.INVISIBLE && playIndex != -1))
			RL_panel_pack.startAnimation(animation);
	}

	void updatePanelMain() {
		Log.test("main");
		TV_panel_total_unipackCount.setText(P_list.size() + "");
		if (TV_panel_total_unipackCapacity.getText().toString().length() == 0)
			TV_panel_total_unipackCapacity.setText(FileManager.byteToMB(FileManager.getFolderSize(UnipackRootPath)) + " MB");
		TV_panel_total_openCount.setText(DB_unipackOpen.getAllCount() + "");
		TV_panel_total_padtouchCount.setText(lang(R.string.measuring));
	}

	void updatePanelPack() {
		Log.test("pack");
		PackItem item = P_list.get(getPlayIndex());
		PackViewSimple packViewSimple = item.packViewSimple;
		Unipack unipack = item.unipack;
		UnipackVO unipackVO = DB_unipack.getByPath(item.path);

		IV_panel_pack_star.setImageResource(unipackVO.pin ? R.drawable.ic_star_24dp : R.drawable.ic_star_border_24dp);
		IV_panel_pack_bookmark.setImageResource(unipackVO.bookmark ? R.drawable.ic_bookmark_24dp : R.drawable.ic_bookmark_border_24dp);
		TV_panel_pack_title.setText(unipack.title);
		TV_panel_pack_subTitle.setText(unipack.producerName);
		TV_panel_pack_path.setText(item.path);
		TV_panel_pack_scale.setText(unipack.buttonX + " × " + unipack.buttonY);
		TV_panel_pack_chainCount.setText(unipack.chain + "");
		TV_panel_pack_soundCount.setText(lang(R.string.measuring));
		TV_panel_pack_ledCount.setText(lang(R.string.measuring));
		TV_panel_pack_fileSize.setText(lang(R.string.measuring));
		TV_panel_pack_openCount.setText(DB_unipackOpen.getCountByPath(item.path) + "");
		TV_panel_pack_padTouchCount.setText(lang(R.string.measuring));
		IV_panel_pack_website.setVisibility(unipack.website != null ? View.VISIBLE : View.INVISIBLE);

		new Thread(() -> {
			String fileSize = FileManager.byteToMB(FileManager.getFolderSize(unipack.path)) + " MB";
			runOnUiThread(() -> TV_panel_pack_fileSize.setText(fileSize));

			Unipack unipackDetail = new Unipack(item.path, true);
			item.unipack = unipackDetail;

			runOnUiThread(() -> {
				packViewSimple
						.setTitle(unipackDetail.title)
						.setSubTitle(unipackDetail.producerName)
						.setOption1(lang(R.string.LED_), unipackDetail.isKeyLED)
						.setOption2(lang(R.string.autoPlay_), unipackDetail.isAutoPlay);

				TV_panel_pack_title.setText(unipackDetail.title);
				TV_panel_pack_subTitle.setText(unipackDetail.producerName);
				TV_panel_pack_path.setText(item.path);
				TV_panel_pack_scale.setText(unipackDetail.buttonX + " × " + unipack.buttonY);
				TV_panel_pack_chainCount.setText(unipackDetail.chain + "");
				TV_panel_pack_soundCount.setText(unipackDetail.soundTableCount + "");
				TV_panel_pack_ledCount.setText(unipackDetail.ledTableCount + "");
				IV_panel_pack_website.setVisibility(unipackDetail.website != null ? View.VISIBLE : View.INVISIBLE);

			});
		}).start();
	}

	void updatePanelPackOption() {
		PackItem item = P_list.get(getPlayIndex());
		PackViewSimple packViewSimple = item.packViewSimple;
		Unipack unipack = item.unipack;
		UnipackVO unipackVO = DB_unipack.getByPath(item.path);

		int flagColor;
		if (unipack.CriticalError)
			flagColor = color(R.color.red);
		else
			flagColor = color(R.color.skyblue);

		if (unipackVO.bookmark)
			flagColor = color(R.color.orange);

		item.flagColors = flagColor;

		IV_panel_pack_star.setImageResource(unipackVO.pin ? R.drawable.ic_star_24dp : R.drawable.ic_star_border_24dp);
		IV_panel_pack_bookmark.setImageResource(unipackVO.bookmark ? R.drawable.ic_bookmark_24dp : R.drawable.ic_bookmark_border_24dp);
	}

	// ============================================================================================= Launchpad

	void setDriver() {
		LaunchpadActivity.setDriverListener(MainActivity.this,
				new LaunchpadDriver.DriverRef.OnConnectionEventListener() {
					@Override
					public void onConnected() {
						Log.driverCycle("MainActivity onConnected()");
						updateLP();
					}

					@Override
					public void onDisconnected() {
						Log.driverCycle("MainActivity onDisconnected()");
					}
				}, new LaunchpadDriver.DriverRef.OnGetSignalListener() {
					@Override
					public void onPadTouch(int x, int y, boolean upDown, int velo) {
						if (!((x == 3 || x == 4) && (y == 3 || y == 4))) {
							if (upDown)
								LaunchpadActivity.driver.sendPadLED(x, y, new int[]{40, 61}[(int) (Math.random() * 2)]);
							else
								LaunchpadActivity.driver.sendPadLED(x, y, 0);
						}
					}

					@Override
					public void onFunctionkeyTouch(int f, boolean upDown) {
						if (f == 0 && upDown) {
							if (havePrev()) {
								togglePlay(lastPlayIndex - 1);
								SV_scrollView.smoothScrollTo(0, P_list.get(lastPlayIndex).packViewSimple.getTop() + (-Scale_Height / 2) + (P_list.get(lastPlayIndex).packViewSimple.getHeight() / 2));
							} else
								showSelectLPUI();
						} else if (f == 1 && upDown) {
							if (haveNext()) {
								togglePlay(lastPlayIndex + 1);
								SV_scrollView.smoothScrollTo(0, P_list.get(lastPlayIndex).packViewSimple.getTop() + (-Scale_Height / 2) + (P_list.get(lastPlayIndex).packViewSimple.getHeight() / 2));
							} else
								showSelectLPUI();
						} else if (f == 2 && upDown) {
							if (haveNow())
								P_list.get(lastPlayIndex).packViewSimple.onPlayClick();
						}
					}

					@Override
					public void onChainTouch(int c, boolean upDown) {
					}

					@Override
					public void onUnknownEvent(int cmd, int sig, int note, int velo) {
						if (cmd == 7 && sig == 46 && note == 0 && velo == -9)
							updateLP();
					}
				});
	}

	void updateLP() {
		showWatermark();
		showSelectLPUI();
	}

	boolean haveNow() {
		return 0 <= lastPlayIndex && lastPlayIndex <= P_list.size() - 1;
	}

	boolean haveNext() {
		return lastPlayIndex < P_list.size() - 1;
	}

	boolean havePrev() {
		return 0 < lastPlayIndex;
	}

	void showSelectLPUI() {
		if (havePrev())
			LaunchpadActivity.driver.sendFunctionkeyLED(0, 63);
		else
			LaunchpadActivity.driver.sendFunctionkeyLED(0, 5);

		if (haveNow())
			LaunchpadActivity.driver.sendFunctionkeyLED(2, 61);
		else
			LaunchpadActivity.driver.sendFunctionkeyLED(2, 0);

		if (haveNext())
			LaunchpadActivity.driver.sendFunctionkeyLED(1, 63);
		else
			LaunchpadActivity.driver.sendFunctionkeyLED(1, 5);
	}

	void showWatermark() {
		LaunchpadActivity.driver.sendPadLED(3, 3, 61);
		LaunchpadActivity.driver.sendPadLED(3, 4, 40);
		LaunchpadActivity.driver.sendPadLED(4, 3, 40);
		LaunchpadActivity.driver.sendPadLED(4, 4, 61);
	}

	// ============================================================================================= Check

	void versionCheck() {
		if (!BuildConfig.VERSION_NAME.contains("b")) {
			new Networks.CheckVersion().setOnChangeListener(version -> {
				try {
					String currVersion = BuildConfig.VERSION_NAME;
					if (version != null && !currVersion.equals(version)) {
						Snackbar.make(RL_rootView, lang(R.string.newVersionFound) + "\n" + currVersion + " → " + version, Snackbar.LENGTH_SHORT)
								.setAction(R.string.update, v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName()))))
								.show();
					}
				} catch (Exception ignore) {
				}
			}).run();
		}
	}

	void newPackCheck() {
		getStoreCount.setOnChangeListener(count -> {
			if (SettingManager.PrevStoreCount.load(MainActivity.this) == count)
				runOnUiThread(() -> blink(false));
			else
				runOnUiThread(() -> blink(true));
		}).run();
	}

	void blink(final boolean bool) {
		if (bool)
			VA_floatingAnimation.start();
		else
			VA_floatingAnimation.end();
	}

	// ============================================================================================= Activity

	@Override
	public void onBackPressed() {
		if (!isDoneIntro)
			super.onBackPressed();
		else {
			if (getPlayIndex() != -1)
				togglePlay(null);
			else
				super.onBackPressed();
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		switch (requestCode) {
			case 0:
				checkThings();
				update();
				break;
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		initVar(false);
		if (!isDoneIntro)
			;
		else {
			setDriver();
			checkThings();
			updatePanel();
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		if (!isDoneIntro)
			;
		else {
			getStoreCount.setOnChangeListener(null);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		LaunchpadActivity.removeDriverListener(MainActivity.this);
	}
}