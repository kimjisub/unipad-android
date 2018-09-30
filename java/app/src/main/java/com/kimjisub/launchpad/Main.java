package com.kimjisub.launchpad;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;
import com.kimjisub.design.PackView;
import com.kimjisub.launchpad.manage.Billing;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.LaunchpadDriver;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.SettingManager;
import com.kimjisub.launchpad.manage.Unipack;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static com.kimjisub.launchpad.manage.Constant.AUTOPLAY_AUTOMAPPING_DELAY_PRESET;

public class Main extends BaseActivity {
	
	// Intro
	RelativeLayout RL_intro;
	TextView TV_version;
	
	Billing billing;
	
	// Main
	ScrollView SV_scrollView;
	LinearLayout LL_list;
	FloatingActionMenu FAM_floatingMenu;
	FloatingActionButton FAB_refreshList;
	FloatingActionButton FAB_reconnectLaunchpad;
	FloatingActionButton FAB_loadUniPack;
	FloatingActionButton FAB_store;
	FloatingActionButton FAB_setting;
	LinearLayout LL_scale;
	LinearLayout LL_paddingScale;
	LinearLayout LL_testView;
	ValueAnimator VA_floatingAnimation;
	
	boolean isDoneIntro = false;
	boolean isShowWatermark = true;
	boolean updateComplete = true;
	
	String UnipackRootURL;
	Networks.GetStoreCount getStoreCount = new Networks.GetStoreCount();
	
	// initVar
	
	void initVar(boolean onFirst) {
		// Intro
		RL_intro = findViewById(R.id.intro);
		TV_version = findViewById(R.id.version);
		
		// Main
		SV_scrollView = findViewById(R.id.scrollView);
		LL_list = findViewById(R.id.list);
		FAM_floatingMenu = findViewById(R.id.floatingMenu);
		FAB_refreshList = findViewById(R.id.fab_refreshList);
		FAB_reconnectLaunchpad = findViewById(R.id.fab_reconnectLaunchpad);
		FAB_loadUniPack = findViewById(R.id.fab_loadUniPack);
		FAB_store = findViewById(R.id.fab_store);
		FAB_setting = findViewById(R.id.fab_setting);
		LL_scale = findViewById(R.id.scale);
		LL_paddingScale = findViewById(R.id.paddingScale);
		LL_testView = findViewById(R.id.testView);
		
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
		UnipackRootURL = SettingManager.IsUsingSDCard.URL(Main.this);
	}
	
	// =========================================================================================
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initVar(true);
		initAds();
		
		startIntro();
	}
	
	void startIntro() {
		billing = new Billing(this).setOnEventListener(new Billing.OnEventListener() {
			@Override
			public void onServiceDisconnected(Billing v) {
			}
			
			@Override
			public void onServiceConnected(Billing v) {
				if (Billing.isPremium)
					TV_version.setTextColor(color(R.color.orange));
			}
			
			@Override
			public void onPurchaseDone(Billing v, JSONObject jo) {
			}
		}).start();
		
		try {
			TV_version.setText(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
		
		TedPermission.with(this)
			.setPermissionListener(new PermissionListener() {
				@Override
				public void onPermissionGranted() {
					showAds();
					new Handler().postDelayed(() -> {
						RL_intro.setVisibility(View.GONE);
						startMain();
					}, 3000);
				}
				
				@Override
				public void onPermissionDenied(ArrayList<String> deniedPermissions) {
					finish();
				}
			})
			.setRationaleMessage(lang(R.string.permissionRequire))
			.setDeniedMessage(lang(R.string.permissionDenied))
			.setPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
			.check();
	}
	
	void startMain() {
		rescanScale(LL_scale, LL_paddingScale);
		
		FAB_refreshList.setOnClickListener(v -> update());
		
		FAB_reconnectLaunchpad.setOnClickListener(v -> startActivity(new Intent(Main.this, Launchpad.class)));
		
		FAB_loadUniPack.setOnClickListener(v -> {
			new FileExplorer(Main.this, SettingManager.FileExplorerPath.load(Main.this))
				.setOnEventListener(new FileExplorer.OnEventListener() {
					@Override
					public void onFileSelected(String fileURL) {
						Toast.makeText(Main.this, "onFileSelected: "+ fileURL, Toast.LENGTH_SHORT).show();
						loadUnipack(fileURL);
					}
					
					@Override
					public void onURLChanged(String folderURL) {
						Toast.makeText(Main.this, "onURLChanged: "+ folderURL, Toast.LENGTH_SHORT).show();
						SettingManager.FileExplorerPath.save(Main.this, folderURL);
					}
				})
				.show();
		});
		
		FAB_store.setOnClickListener(v -> startActivityForResult(new Intent(Main.this, FBStore.class), 0));
		
		FAB_store.setOnLongClickListener(view -> {
			startActivityForResult(new Intent(Main.this, Store.class), 0);
			return false;
		});
		
		FAB_setting.setOnClickListener(v -> startActivity(new Intent(Main.this, Setting.class)));
		
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
		
		isDoneIntro = true;
		
		checkThings();
		update();
		updateDriver();
	}
	
	int projectsCount = 0;
	PackView[] PV_items;
	int[] flagColors;
	String[] URLs;
	Unipack[] unipacks;
	
	void checkThings(){
		versionCheck();
		newPackCheck();
	}
	
	void update() {
		playIndex = -1;
		if (!updateComplete)
			return;
		
		updateComplete = false;
		
		LL_list.removeAllViews();
		
		new Thread(() -> {
			try {
				File projectFolder = new File(UnipackRootURL);
				
				if (projectFolder.isDirectory()) {
					
					File[] projectFiles = FileManager.sortByTime(projectFolder.listFiles());
					File[] projects = new File[projectFiles.length];
					
					
					projectsCount = 0;
					for (File project : projectFiles) {
						if (!project.isDirectory()) continue;
						projects[projectsCount] = project;
						projectsCount++;
					}
					
					PV_items = new PackView[projectsCount];
					flagColors = new int[projectsCount];
					URLs = new String[projectsCount];
					unipacks = new Unipack[projectsCount];
					
					for (int i = 0; i < projectsCount; i++) {
						final int I = i;
						File project = projects[I];
						
						final String url = UnipackRootURL + "/" + project.getName();
						final Unipack unipack = new Unipack(url, false);
						int flagColor;
						String title = unipack.title;
						String producerName = unipack.producerName;
						
						
						if (unipack.ErrorDetail == null) {
							flagColor = color(R.color.skyblue);
						} else if (unipack.CriticalError) {
							flagColor = color(R.color.red);
							title = lang(R.string.errOccur);
							producerName = unipack.URL;
						} else {
							flagColor = color(R.color.orange);
						}
						
						final PackView packView = new PackView(Main.this)
							.setFlagColor(flagColor)
							.setTitle(title)
							.setSubTitle(producerName)
							.addInfo(lang(R.string.scale), unipack.buttonX + " x " + unipack.buttonY)
							.addInfo(lang(R.string.chainCount), unipack.chain + "")
							.addInfo(lang(R.string.capacity), FileManager.byteToMB(FileManager.getFolderSize(url)) + " MB")
							.addBtn(lang(R.string.delete), color(R.color.red))
							.addBtn(lang(R.string.edit), color(R.color.orange))
							.setOption1(lang(R.string.LED_), unipack.isKeyLED)
							.setOption2(lang(R.string.autoPlay_), unipack.isAutoPlay)
							.setOnEventListener(new PackView.OnEventListener() {
								@Override
								public void onViewClick(PackView v) {
									togglePlay(I);
									toggleDetail(-1);
								}
								
								@Override
								public void onViewLongClick(PackView v) {
									togglePlay(-1);
									toggleDetail(I);
								}
								
								@Override
								public void onPlayClick(PackView v) {
									rescanScale(LL_scale, LL_paddingScale);
									
									Intent intent = new Intent(Main.this, Play.class);
									intent.putExtra("URL", url);
									startActivity(intent);
								}
								
								@Override
								public void onFunctionBtnClick(final PackView v, int index) {
									switch (index) {
										case 0:
											deleteUnipack(v, unipack);
											break;
										case 1:
											editUnipack(v, unipack);
											break;
										case 2:
											startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(unipack.websiteURL)));
											break;
									}
								}
							});
						
						if(unipack.websiteURL != null)
							packView.addBtn(lang(R.string.website), color(R.color.skyblue));
						
						runOnUiThread(() -> {
							final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
							int left = dpToPx(16);
							int top = 0;
							int right = dpToPx(16);
							int bottom = dpToPx(10);
							lp.setMargins(left, top, right, bottom);
							LL_list.addView(packView, lp);
							Animation a = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.btn_fade_in);
							a.setInterpolator(AnimationUtils.loadInterpolator(Main.this, android.R.anim.accelerate_decelerate_interpolator));
							packView.setAnimation(a);
						});
						
						PV_items[I] = packView;
						flagColors[I] = flagColor;
						URLs[I] = url;
						unipacks[I] = unipack;
						
					}
					
					if (projectsCount == 0) {
						runOnUiThread(this::addErrorItem);
					}
					
				} else {
					projectFolder.mkdir();
					
					runOnUiThread(this::addErrorItem);
				}
				
				File nomedia = new File(UnipackRootURL + "/.nomedia");
				if (!nomedia.isFile()) {
					try {
						(new FileWriter(nomedia)).close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} finally {
				updateComplete = true;
			}
		}).start();
	}
	
	void addErrorItem() {
		String title = lang(R.string.unipackNotFound);
		String subTitle = lang(R.string.clickToAddUnipack);
		
		PackView packView = PackView.errItem(Main.this, title, subTitle, new PackView.OnEventListener() {
			@Override
			public void onViewClick(PackView v) {
				startActivity(new Intent(Main.this, FBStore.class));
			}
			
			@Override
			public void onViewLongClick(PackView v) {
			}
			
			@Override
			public void onPlayClick(PackView v) {
			}
			
			@Override
			public void onFunctionBtnClick(PackView v, int index) {
			}
		});
		
		
		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		int left = dpToPx(16);
		int top = 0;
		int right = dpToPx(16);
		int bottom = dpToPx(10);
		lp.setMargins(left, top, right, bottom);
		LL_list.addView(packView, lp);
	}
	
	void deleteUnipack(final PackView v, final Unipack unipack) {
		final RelativeLayout RL_delete = (RelativeLayout) View.inflate(Main.this, R.layout.extend_delete, null);
		((TextView) RL_delete.findViewById(R.id.path)).setText(unipack.URL);
		
		LL_testView.removeAllViews();
		LL_testView.addView(RL_delete);
		LL_testView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				LL_testView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				
				int height = LL_testView.getHeight();
				LL_testView.removeAllViews();
				
				@SuppressLint("ResourceType") String[] btnTitles = new String[]{
					lang(R.string.delete),
					lang(R.string.cancel)
				};
				int[] btnColors = new int[]{
					color(R.color.red),
					color(R.color.gray2)
				};
				
				v.setExtendView(RL_delete, height, btnTitles, btnColors, (v1, index) -> {
					switch (index) {
						case 0:
							FileManager.deleteFolder(unipack.URL);
							update();
							break;
						case 1:
							v1.toggleDetail(1);
							break;
					}
				}).toggleDetail(2);
			}
		});
	}
	
	void editUnipack(final PackView v, final Unipack unipack) {
		
		final RelativeLayout RL_delete = (RelativeLayout) View.inflate(Main.this, R.layout.extend_automapping, null);
		((TextView) RL_delete.findViewById(R.id.path)).setText(unipack.URL);
		
		LL_testView.removeAllViews();
		LL_testView.addView(RL_delete);
		LL_testView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				LL_testView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				
				int height = LL_testView.getHeight();
				LL_testView.removeAllViews();
				
				@SuppressLint("ResourceType") String[] btnTitles = new String[]{
					lang(R.string.edit),
					lang(R.string.cancel)
				};
				int[] btnColors = new int[]{
					color(R.color.orange),
					color(R.color.gray2)
				};
				
				v.setExtendView(RL_delete, height, btnTitles, btnColors, (v1, index) -> {
					switch (index) {
						case 0:
							autoMapping(v1, unipack);
							break;
						case 1:
							v1.toggleDetail(1);
							break;
					}
				}).toggleDetail(2);
			}
		});
	}
	
	@SuppressLint("StaticFieldLeak")
	void autoMapping(final PackView v, Unipack uni) {
		final Unipack unipack = new Unipack(uni.URL, true);
		
		
		if (unipack.isAutoPlay && unipack.autoPlay != null) {
			(new AsyncTask<String, String, String>() {
				
				ProgressDialog progressDialog;
				
				ArrayList<Unipack.AutoPlay> autoplay1;
				ArrayList<Unipack.AutoPlay> autoplay2;
				ArrayList<Unipack.AutoPlay> autoplay3;
				
				@Override
				protected void onPreExecute() {
					autoplay1 = new ArrayList<>();
					for (Unipack.AutoPlay e : unipack.autoPlay) {
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
					
					progressDialog = new ProgressDialog(Main.this);
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
									int num = e.num % unipack.sound[e.currChain][e.x][e.y].size();
									nextDuration = FileManager.wavDuration(mplayer, unipack.sound[e.currChain][e.x][e.y].get(num).URL);
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
								//int num = e.num % unipack.sound[e.currChain][e.x][e.y].size();
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
						File filePre = new File(unipack.URL, "autoPlay");
						@SuppressLint("SimpleDateFormat") File fileNow = new File(unipack.URL, "autoPlay_" + new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss").format(new Date(System.currentTimeMillis())));
						filePre.renameTo(fileNow);
						
						BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(unipack.URL + "/autoPlay")));
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
						new AlertDialog.Builder(Main.this)
							.setTitle(lang(R.string.success))
							.setMessage(lang(R.string.remapDone))
							.setPositiveButton(lang(R.string.accept), (dialogInterface, i) -> v.toggleDetail(0))
							.show();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).execute();
			
			
		} else {
			new AlertDialog.Builder(Main.this)
				.setTitle(lang(R.string.failed))
				.setMessage(lang(R.string.remapFail))
				.setPositiveButton(lang(R.string.accept), null)
				.show();
		}
	}
	
	@SuppressLint("StaticFieldLeak")
	void loadUnipack(final String UnipackZipURL) {
		
		(new AsyncTask<String, String, String>() {
			
			ProgressDialog progressDialog = new ProgressDialog(Main.this);
			
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
				
				
				File file = new File(UnipackZipURL);
				String name = file.getName();
				String name_ = name.substring(0, name.lastIndexOf("."));
				
				String UnipackURL;
				for (int i = 1; ; i++) {
					if (i == 1)
						UnipackURL = UnipackRootURL + "/" + name_ + "/";
					else
						UnipackURL = UnipackRootURL + "/" + name_ + " (" + i + ")/";
					
					if (!new File(UnipackURL).exists())
						break;
				}
				
				try {
					FileManager.unZipFile(UnipackZipURL, UnipackURL);
					Unipack unipack = new Unipack(UnipackURL, true);
					
					if (unipack.ErrorDetail == null) {
						msg1 = lang(R.string.analyzeComplete);
						msg2 = unipack.getInfoText(Main.this);
					} else if (unipack.CriticalError) {
						msg1 = lang(R.string.analyzeFailed);
						msg2 = unipack.ErrorDetail;
						FileManager.deleteFolder(UnipackURL);
					} else {
						msg1 = lang(R.string.warning);
						msg2 = unipack.ErrorDetail;
					}
					
				} catch (IOException e) {
					msg1 = lang(R.string.analyzeFailed);
					msg2 = e.toString();
					FileManager.deleteFolder(UnipackURL);
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
	
	// ========================================================================================= toggle Play, Detail
	
	int playIndex = -1;
	
	void togglePlay(int n) {
		playIndex = n;
		for (int i = 0; i < PV_items.length; i++) {
			PackView packView = PV_items[i];
			if (packView != null) {
				if (n != i)
					packView.togglePlay(false, color(R.color.red), flagColors[i]);
				else
					packView.togglePlay(color(R.color.red), flagColors[i]);
			}
		}
		showSelectLPUI();
	}
	
	void toggleDetail(int n) {
		for (int i = 0; i < PV_items.length; i++) {
			PackView packView = PV_items[i];
			if (packView != null) {
				if (n != i)
					packView.toggleDetail(0);
				else
					packView.toggleDetail();
			}
		}
	}
	
	// ========================================================================================= Launchpad
	
	void updateDriver() {
		Launchpad.setDriverListener(Main.this,
			new LaunchpadDriver.DriverRef.OnConnectionEventListener() {
				@Override
				public void onConnected() {
					onConnected_();
					(new Handler()).postDelayed(this::onConnected_, 3000);
					
				}
				
				void onConnected_() {
					showWatermark();
					showSelectLPUI();
				}
				
				@Override
				public void onDisconnected() {
				}
			}, new LaunchpadDriver.DriverRef.OnGetSignalListener() {
				@Override
				public void onPadTouch(int x, int y, boolean upDown, int velo) {
					if (upDown)
						Launchpad.driver.sendPadLED(x, y, new int[]{40, 61}[(int) (Math.random() * 2)]);
					else
						Launchpad.driver.sendPadLED(x, y, 0);
				}
				
				@Override
				public void onFunctionkeyTouch(int f, boolean upDown) {
					if (f == 0 && upDown) {
						if (havePrev()) {
							togglePlay(playIndex - 1);
							SV_scrollView.smoothScrollTo(0, PV_items[playIndex].getTop() + (-Scale_Height / 2) + (PV_items[playIndex].getHeight() / 2));
						} else
							showSelectLPUI();
					} else if (f == 1 && upDown) {
						if (haveNext()) {
							togglePlay(playIndex + 1);
							SV_scrollView.smoothScrollTo(0, PV_items[playIndex].getTop() + (-Scale_Height / 2) + (PV_items[playIndex].getHeight() / 2));
						} else
							showSelectLPUI();
					} else if (f == 2 && upDown) {
						if (haveNow())
							PV_items[playIndex].onPlayClick();
					} else if (4 <= f && f <= 7 && upDown)
						toggleWatermark();
				}
				
				@Override
				public void onChainTouch(int c, boolean upDown) {
				}
				
				@Override
				public void onUnknownEvent(int cmd, int sig, int note, int velo) {
				
				}
			});
	}
	
	boolean haveNow() {
		return PV_items != null && 0 <= playIndex && playIndex <= projectsCount - 1;
	}
	
	boolean haveNext() {
		return PV_items != null && playIndex < projectsCount - 1;
	}
	
	boolean havePrev() {
		return PV_items != null && 0 < playIndex;
	}
	
	void showSelectLPUI() {
		if (PV_items != null) {
			if (havePrev())
				Launchpad.driver.sendFunctionkeyLED(0, 63);
			else
				Launchpad.driver.sendFunctionkeyLED(0, 52);
			
			if (haveNow())
				Launchpad.driver.sendFunctionkeyLED(2, 61);
			else
				Launchpad.driver.sendFunctionkeyLED(2, 0);
			
			if (haveNext())
				Launchpad.driver.sendFunctionkeyLED(1, 63);
			else
				Launchpad.driver.sendFunctionkeyLED(1, 52);
		}
	}
	
	// ========================================================================================= Watermark
	
	void toggleWatermark() {
		isShowWatermark = !isShowWatermark;
		showWatermark();
	}
	
	void showWatermark() {
		if (isShowWatermark) {
			Launchpad.driver.sendFunctionkeyLED(4, 61);
			Launchpad.driver.sendFunctionkeyLED(5, 40);
			Launchpad.driver.sendFunctionkeyLED(6, 61);
			Launchpad.driver.sendFunctionkeyLED(7, 40);
		} else {
			Launchpad.driver.sendFunctionkeyLED(4, 0);
			Launchpad.driver.sendFunctionkeyLED(5, 0);
			Launchpad.driver.sendFunctionkeyLED(6, 0);
			Launchpad.driver.sendFunctionkeyLED(7, 0);
		}
	}
	
	// ========================================================================================= Check
	
	void versionCheck() {
		new Networks.CheckVersion().setOnChangeListener(version -> {
			try {
				String currVersion = BuildConfig.VERSION_NAME;
				if (version != null && !currVersion.equals(version)) {
					new AlertDialog.Builder(Main.this)
						.setTitle(lang(R.string.newVersionFound))
						.setMessage(lang(R.string.currentVersion) + " : " + currVersion + "\n" +
							lang(R.string.newVersion) + " : " + version)
						.setPositiveButton(lang(R.string.update), (dialog, which) -> {
							startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
							dialog.dismiss();
						})
						.setNegativeButton(lang(R.string.ignore), (dialog, which) -> dialog.dismiss())
						.show();
				}
			} catch (Exception ignore) {
			}
		}).run();
	}
	
	void newPackCheck() {
		getStoreCount.setOnChangeListener(count -> {
			if (SettingManager.PrevStoreCount.load(Main.this) == count)
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
	
	// ========================================================================================= Activity
	
	@Override
	public void onBackPressed() {
		if (!isDoneIntro)
			;
		else {
			if (PV_items != null) {
				boolean clear = true;
				for (PackView item : PV_items) {
					if (item != null) {
						if (item.isPlay() || item.isDetail()) {
							togglePlay(-1);
							toggleDetail(-1);
							
							clear = false;
							break;
						}
					}
				}
				
				if (clear)
					super.onBackPressed();
			} else
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
		else
			updateDriver();
		
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (!isDoneIntro)
			;
		else {
			getStoreCount.setOnChangeListener(null);
			Launchpad.driver.sendClearLED();
			Launchpad.removeDriverListener(Main.this);
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		billing.onDestroy();
		
		
		Launchpad.driver.sendClearLED();
		Launchpad.removeDriverListener(Main.this);
	}
}