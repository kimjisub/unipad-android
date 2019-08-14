package com.kimjisub.launchpad.activity;

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
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.anjlab.android.iab.v3.TransactionDetails;
import com.github.clans.fab.FloatingActionMenu;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.kimjisub.design.dialog.FileExplorerDialog;
import com.kimjisub.design.Panel.MainPackPanel;
import com.kimjisub.design.PackViewSimple;
import com.kimjisub.launchpad.BuildConfig;
import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.adapter.MainAdapter;
import com.kimjisub.launchpad.adapter.MainItem;
import com.kimjisub.launchpad.adapter.ThemeItem;
import com.kimjisub.launchpad.databinding.ActivityMainBinding;
import com.kimjisub.launchpad.db.manager.DB_Unipack;
import com.kimjisub.launchpad.db.manager.DB_UnipackOpen;
import com.kimjisub.launchpad.db.vo.UnipackOpenVO;
import com.kimjisub.launchpad.db.vo.UnipackVO;
import com.kimjisub.launchpad.manager.BillingManager;
import com.kimjisub.launchpad.network.Networks;
import com.kimjisub.manager.FileManager;
import com.kimjisub.launchpad.manager.LaunchpadDriver;
import com.kimjisub.manager.Log;
import com.kimjisub.launchpad.manager.PreferenceManager;
import com.kimjisub.launchpad.manager.Unipack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Date;

import static com.kimjisub.launchpad.manager.Constant.*;

public class MainActivity extends BaseActivity {
	ActivityMainBinding b;

	BillingManager billingManager;

	// DB
	public DB_Unipack DB_unipack;
	DB_UnipackOpen DB_unipackOpen;

	ValueAnimator VA_floatingAnimation;

	int lastPlayIndex = -1;
	public ArrayList<MainItem> I_list;
	RecyclerView.Adapter RV_adapter;

	Networks.FirebaseManager firebase_storeCount;

	boolean updateComplete = true;

	void initVar(boolean onFirst) {
		// DB
		DB_unipack = new DB_Unipack(MainActivity.this);
		DB_unipackOpen = new DB_UnipackOpen(MainActivity.this);

		// View
		b.panelTotal.setVersion(BuildConfig.VERSION_NAME);

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
				b.floatingMenu.setMenuButtonColorNormal(color);
				b.floatingMenu.setMenuButtonColorPressed(color);
				b.fabStore.setColorNormal(color);
				b.fabStore.setColorPressed(color);
			});
		}

		// var
		if (onFirst) {
			I_list = new ArrayList<>();
			RV_adapter = new MainAdapter(MainActivity.this);

			b.recyclerView.setHasFixedSize(false);
			b.recyclerView.setAdapter(RV_adapter);
			b.recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));

			firebase_storeCount = new Networks.FirebaseManager("storeCount");
			firebase_storeCount.setEventListener(new ValueEventListener() {
				@Override
				public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
					Long data = dataSnapshot.getValue(Long.class);
					Long prev = PreferenceManager.PrevStoreCount.load(MainActivity.this);
					runOnUiThread(() -> blink(!data.equals(prev)));
				}

				@Override
				public void onCancelled(@NonNull DatabaseError databaseError) {
				}
			});
		}
	}

	// =============================================================================================

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		b = setContentViewBind(R.layout.activity_main);
		initVar(true);

		loadAdmob();
		billingManager = new BillingManager(MainActivity.this, new BillingManager.BillingEventListener() {
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
				b.panelTotal.setPremium(billingManager.isPurchaseRemoveAds() || billingManager.isPurchaseProTools());

				if (billingManager.isShowAds()) {
					if (checkAdsCooltime()) {
						updateAdsCooltime();
						showAdmob();
					}
					/*todo ad
					AdRequest adRequest = new AdRequest.Builder().build();
					b.adView.loadAd(adRequest);*/
				} else ;
					/*todo ad
					b.adView.setVisibility(View.GONE);*/
			}
		});
		startMain();
		updatePanel(true);
	}

	@SuppressLint("StaticFieldLeak")
	void startMain() {
		rescanScale(b.scale, b.paddingScale);

		b.swipeRefreshLayout.setOnRefreshListener(this::update);

		b.fabReconnectLaunchpad.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LaunchpadActivity.class)));

		b.fabLoadUniPack.setOnClickListener(v -> new FileExplorerDialog(MainActivity.this, PreferenceManager.FileExplorerPath.load(MainActivity.this))
				.setOnEventListener(new FileExplorerDialog.OnEventListener() {
					@Override
					public void onFileSelected(String filePath) {
						loadUnipack(new File(filePath));
					}

					@Override
					public void onPathChanged(String folderPath) {
						PreferenceManager.FileExplorerPath.save(MainActivity.this, folderPath);
					}
				})
				.show());

		b.fabStore.setOnClickListener(v -> startActivityForResult(new Intent(MainActivity.this, FBStoreActivity.class), 0));

		b.fabStore.setOnLongClickListener(view -> {
			//startActivityForResult(new Intent(MainActivity.this, FSStoreActivity.class), 0);
			return false;
		});

		b.fabSetting.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingActivity.class)));

		b.floatingMenu.setOnMenuToggleListener(new FloatingActionMenu.OnMenuToggleListener() {
			Handler handler = new Handler();

			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					b.floatingMenu.close(true);
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

		b.panelPack.setOnEventListener(new MainPackPanel.OnEventListener() {
			@Override
			public void onStarClick(View v) {
				MainItem item = getCurrPlay();
				if (item != null) {
					UnipackVO unipackVO = DB_unipack.getByPath(item.unipack.F_project.getName());
					unipackVO.pin = !unipackVO.pin;
					DB_unipack.update(item.unipack.F_project.getName(), unipackVO);

					updatePanelPackOption();
				}
			}

			@Override
			public void onBookmarkClick(View v) {
				MainItem item = getCurrPlay();
				if (item != null) {
					UnipackVO unipackVO = DB_unipack.getByPath(item.unipack.F_project.getName());
					unipackVO.bookmark = !unipackVO.bookmark;
					DB_unipack.update(item.unipack.F_project.getName(), unipackVO);

					updatePanelPackOption();
				}
			}

			@Override
			public void onEditClick(View v) {

			}

			@Override
			public void onStorageClick(View v) {
				MainItem item = getCurrPlay();
				if (item != null) {
					item.isMoving = true;
					File source = new File(item.path);
					boolean isInternal = FileManager.isInternalFile(MainActivity.this, source);
					File target = new File(isInternal ? F_UniPackRootExt : F_UniPackRootInt, source.getName());

					(new AsyncTask<String, String, String>() {
						@Override
						protected void onPreExecute() {
							super.onPreExecute();
							b.panelPack.setStorageMoving();
						}

						@Override
						protected String doInBackground(String... params) {
							FileManager.moveDirectory(source, target);
							return null;
						}

						@Override
						protected void onProgressUpdate(String... strings) {
						}

						@Override
						protected void onPostExecute(String result) {
							super.onPostExecute(result);
							update();
						}

					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

					updatePanelPackOption();
				}
			}

			@Override
			public void onYoutubeClick(View v) {
				MainItem item = getCurrPlay();
				if (item != null) {
					String website = "https://www.youtube.com/results?search_query=UniPad+" + item.unipack.title;
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(website)));
				}
			}

			@Override
			public void onWebsiteClick(View v) {
				MainItem item = getCurrPlay();
				if (item != null) {
					String website = item.unipack.website;
					if (website != null)
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(website)));
				}
			}

			@Override
			public void onFuncClick(View v) {
				MainItem item = getCurrPlay();
				if (item != null)
					new AlertDialog.Builder(MainActivity.this)
							.setTitle(lang(R.string.warning))
							.setMessage(lang(R.string.doYouWantToRemapProject))
							.setPositiveButton(lang(R.string.accept), (dialog, which) -> {
								autoMapping(item.unipack);
							}).setNegativeButton(lang(R.string.cancel), null)
							.show();
			}

			@Override
			public void onDeleteClick(View v) {
				MainItem item = getCurrPlay();
				if (item != null)

					new AlertDialog.Builder(MainActivity.this)
							.setTitle(lang(R.string.warning))
							.setMessage(lang(R.string.doYouWantToDeleteProject))
							.setPositiveButton(lang(R.string.accept), (dialog, which) -> {
								deleteUnipack(item.unipack);
							}).setNegativeButton(lang(R.string.cancel), null)
							.show();
			}
		});


		checkThings();
		update(false);
		setDriver();
	}

	void checkThings() {
		versionCheck();
	}

	void update() {
		update(true);
	}

	@SuppressLint("StaticFieldLeak")
	void update(boolean animateNew) {
		lastPlayIndex = -1;
		if (!updateComplete)
			return;

		b.swipeRefreshLayout.setRefreshing(true);
		updateComplete = false;

		togglePlay(null);
		updatePanel(true);

		(new AsyncTask<String, String, String>() {

			ArrayList<MainItem> I_curr = new ArrayList<>();
			ArrayList<MainItem> I_added = new ArrayList<>();
			ArrayList<MainItem> I_removed = new ArrayList<>(I_list);

			@Override
			protected void onPreExecute() {
				super.onPreExecute();
			}

			@Override
			protected String doInBackground(String... params) {
				try {

					for (File file : getUniPackDirList()) {

						if (!file.isDirectory()) continue;

						String path = file.getPath();
						Unipack unipack = new Unipack(file, false);
						MainItem packItem = new MainItem(unipack, path, animateNew);

						I_curr.add(packItem);
					}

					for (MainItem item : I_curr) {
						int index = -1;
						int i = 0;
						for (MainItem item2 : I_removed) {
							if (item2.path.equals(item.path)) {
								index = i;
								break;
							}
							i++;
						}

						if (index != -1)
							I_removed.remove(index);
						else
							I_added.add(0, item);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}

			@Override
			protected void onProgressUpdate(String... strings) {
			}

			@Override
			protected void onPostExecute(String result) {
				super.onPostExecute(result);

				for (MainItem F_added : I_added) {

					int i = 0;
					long targetTime = FileManager.getInnerFileLastModified(F_added.unipack.F_project);
					for (MainItem item : I_list) {
						long testTime = FileManager.getInnerFileLastModified(item.unipack.F_project);
						if (targetTime > testTime)
							break;
						i++;
					}
					I_list.add(i, F_added);
					RV_adapter.notifyItemInserted(i);
					b.panelTotal.setUnipackCount(I_list.size() + "");
				}

				for (MainItem F_removed : I_removed) {
					int i = 0;
					for (MainItem item : I_list) {
						if (item.path.equals(F_removed.path)) {
							int I = i;
							I_list.remove(I);
							RV_adapter.notifyItemRemoved(I);
							b.panelTotal.setUnipackCount(I_list.size() + "");
							break;
						}
						i++;
					}
				}

				if (I_added.size() > 0) b.recyclerView.smoothScrollToPosition(0);

				addErrorItem(I_list.size() == 0);

				b.swipeRefreshLayout.setRefreshing(false);
				updateComplete = true;
			}

		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	void addErrorItem(Boolean bool) {
		if (bool) {
			b.errItem
					.setTitle(lang(R.string.unipackNotFound))
					.setSubTitle(lang(R.string.clickToAddUnipack))
					.setFlagColor(color(R.color.red))
					.setOnEventListener(new PackViewSimple.OnEventListener() {
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
		}

		b.errItem.setVisibility(bool ? View.VISIBLE : View.GONE);
	}

	// ============================================================================================= UniPack Work

	void deleteUnipack(final Unipack unipack) {
		FileManager.deleteDirectory(unipack.F_project);
		update();
	}

	@SuppressLint("StaticFieldLeak")
	void autoMapping(Unipack uni) {
		try {
			final Unipack unipack = new Unipack(uni.F_project, true);

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
						progressDialog.setMessage(lang(R.string.wait_a_sec));
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
										nextDuration = FileManager.wavDuration(mplayer, unipack.soundTable[e.currChain][e.x][e.y].get(num).file.getPath());
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
							File filePre = new File(unipack.F_project, "autoPlay");
							@SuppressLint("SimpleDateFormat") File fileNow = new File(unipack.F_project, "autoPlay_" + new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss").format(new Date(System.currentTimeMillis())));
							filePre.renameTo(fileNow);

							BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(unipack.F_autoPlay)));
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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressLint("StaticFieldLeak")
	void loadUnipack(final File F_UniPackZip) {

		(new AsyncTask<String, String, String>() {

			ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);

			String msg1;
			String msg2;

			@Override
			protected void onPreExecute() {

				progressDialog.setTitle(lang(R.string.analyzing));
				progressDialog.setMessage(lang(R.string.wait_a_sec));
				progressDialog.setCancelable(false);
				progressDialog.show();
				super.onPreExecute();
			}

			@Override
			protected String doInBackground(String... params) {
				String name = F_UniPackZip.getName();
				String name_ = name.substring(0, name.lastIndexOf("."));

				File F_UniPack = FileManager.makeNextPath(F_UniPackRootExt, name_, "/");

				try {
					FileManager.unZipFile(F_UniPackZip.getPath(), F_UniPack.getPath());
					Unipack unipack = new Unipack(F_UniPack, true);

					if (unipack.ErrorDetail == null) {
						msg1 = lang(R.string.analyzeComplete);
						msg2 = unipack.getInfoText(MainActivity.this);
					} else if (unipack.CriticalError) {
						msg1 = lang(R.string.analyzeFailed);
						msg2 = unipack.ErrorDetail;
						FileManager.deleteDirectory(F_UniPack);
					} else {
						msg1 = lang(R.string.warning);
						msg2 = unipack.ErrorDetail;
					}

				} catch (Exception e) {
					msg1 = lang(R.string.analyzeFailed);
					msg2 = e.toString();
					FileManager.deleteDirectory(F_UniPack);
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


	void togglePlay(int i) {
		togglePlay(I_list.get(i));
	}

	@SuppressLint("SetTextI18n")
	public void togglePlay(MainItem item) {
		try {
			int i = 0;
			for (MainItem mainItem : I_list) {
				PackViewSimple packViewSimple = mainItem.packViewSimple;

				if (item != null && mainItem.path.equals(item.path)) {
					mainItem.isToggle = !mainItem.isToggle;
					lastPlayIndex = i;
				} else
					mainItem.isToggle = false;

				if (packViewSimple != null)
					packViewSimple.toggle(mainItem.isToggle, color(R.color.red), mainItem.flagColor);

				i++;
			}
			showSelectLPUI();

			updatePanel(false);

		} catch (ConcurrentModificationException e) {
			e.printStackTrace();
		}
	}

	public void pressPlay(MainItem item) {
		rescanScale(b.scale, b.paddingScale);
		LaunchpadActivity.removeDriverListener(MainActivity.this);

		DB_unipackOpen.add(new UnipackOpenVO(item.unipack.F_project.getName(), new Date()));

		Intent intent = new Intent(MainActivity.this, PlayActivity.class);
		intent.putExtra("path", item.path);
		startActivity(intent);
	}

	int getPlayIndex() {
		int index = -1;

		int i = 0;
		for (MainItem mainItem : I_list) {
			if (mainItem.isToggle) {
				index = i;
				break;
			}
			i++;
		}

		return index;
	}

	MainItem getCurrPlay() {
		MainItem ret = null;

		int playIndex = getPlayIndex();
		if (playIndex != -1)
			ret = I_list.get(playIndex);

		return ret;
	}

	// ============================================================================================= panel

	void updatePanel(boolean hardWork) {
		int playIndex = getPlayIndex();
		Animation animation = AnimationUtils.loadAnimation(MainActivity.this, playIndex != -1 ? R.anim.panel_in : R.anim.panel_out);
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
			updatePanelMain(hardWork);
		else
			updatePanelPack(hardWork);

		int visibility = b.panelPack.getVisibility();
		if ((visibility == View.VISIBLE && playIndex == -1)
				|| (visibility == View.INVISIBLE && playIndex != -1))
			b.panelPack.startAnimation(animation);
	}

	@SuppressLint("StaticFieldLeak")
	void updatePanelMain(boolean hardWork) {
		b.panelTotal.setUnipackCount(I_list.size() + "");
		b.panelTotal.setOpenCount(DB_unipackOpen.getAllCount() + "");
		b.panelTotal.setPadTouchCount(lang(R.string.measuring));
		try {
			String name = new ThemeItem(MainActivity.this, PreferenceManager.SelectedTheme.load(MainActivity.this)).name;
			b.panelTotal.setThemeName(name);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (hardWork)
			(new AsyncTask<String, String, String>() {
				@Override
				protected String doInBackground(String... params) {
					String fileSize = FileManager.byteToMB(FileManager.getFolderSize(F_UniPackRootExt)) + " MB";
					publishProgress(fileSize);
					return null;
				}

				@Override
				protected void onProgressUpdate(String... strings) {
					b.panelTotal.setUnipackCapacity(strings[0]);
				}
			}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	@SuppressLint("StaticFieldLeak")
	void updatePanelPack(boolean hardWork) {
		MainItem item = I_list.get(getPlayIndex());
		Unipack unipack = item.unipack;
		UnipackVO unipackVO = DB_unipack.getByPath(item.unipack.F_project.getName());

		b.panelPack.setStar(unipackVO.pin);
		b.panelPack.setBookmark(unipackVO.bookmark);
		b.panelPack.setStorage(!FileManager.isInternalFile(MainActivity.this, unipack.F_project));
		b.panelPack.b.title.setText(unipack.title);
		b.panelPack.b.subTitle.setText(unipack.producerName);
		b.panelPack.b.path.setText(item.path);
		b.panelPack.b.scale.setText(unipack.buttonX + " × " + unipack.buttonY);
		b.panelPack.b.chainCount.setText(unipack.chain + "");
		b.panelPack.b.soundCount.setText(lang(R.string.measuring));
		b.panelPack.b.ledCount.setText(lang(R.string.measuring));
		b.panelPack.b.fileSize.setText(lang(R.string.measuring));
		b.panelPack.b.openCount.setText(DB_unipackOpen.getCountByPath(item.unipack.F_project.getName()) + "");
		b.panelPack.b.padTouchCount.setText(lang(R.string.measuring));
		b.panelPack.b.website.setVisibility(unipack.website != null ? View.VISIBLE : View.INVISIBLE);

		(new AsyncTask<String, String, String>() {
			Handler handler = new Handler();

			@Override
			protected String doInBackground(String... params) {
				String fileSize = FileManager.byteToMB(FileManager.getFolderSize(unipack.F_project)) + " MB";
				handler.post(() -> {
					if (b.panelPack.b.path.getText().toString().equals(item.path))
						b.panelPack.b.fileSize.setText(fileSize);
				});

				try {
					Unipack unipackDetail = new Unipack(item.unipack.F_project, true);
					item.unipack = unipackDetail;
					publishProgress(fileSize);
					handler.post(() -> {
						if (b.panelPack.b.path.getText().toString().equals(item.path)) {
							b.panelPack.b.soundCount.setText(unipackDetail.soundTableCount + "");
							b.panelPack.b.ledCount.setText(unipackDetail.ledTableCount + "");
						}
					});
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}

		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	void updatePanelPackOption() {
		MainItem item = I_list.get(getPlayIndex());
		Unipack unipack = item.unipack;
		UnipackVO unipackVO = DB_unipack.getByPath(item.unipack.F_project.getName());

		int flagColor;
		if (unipack.CriticalError)
			flagColor = color(R.color.red);
		else
			flagColor = color(R.color.skyblue);

		if (unipackVO.bookmark)
			flagColor = color(R.color.orange);

		item.flagColor = flagColor;

		b.panelPack.setStar(unipackVO.pin);
		b.panelPack.setBookmark(unipackVO.bookmark);
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
								b.recyclerView.smoothScrollToPosition(lastPlayIndex);
								//b.recyclerView.smoothScrollToPosition(0, I_list.get(lastPlayIndex).packViewSimple.getTop() + (-Scale_Height / 2) + (I_list.get(lastPlayIndex).packViewSimple.getHeight() / 2));
							} else
								showSelectLPUI();
						} else if (f == 1 && upDown) {
							if (haveNext()) {
								togglePlay(lastPlayIndex + 1);
								b.recyclerView.smoothScrollToPosition(lastPlayIndex);
							} else
								showSelectLPUI();
						} else if (f == 2 && upDown) {
							if (haveNow())
								I_list.get(lastPlayIndex).packViewSimple.onPlayClick();
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
		return 0 <= lastPlayIndex && lastPlayIndex <= I_list.size() - 1;
	}

	boolean haveNext() {
		return lastPlayIndex < I_list.size() - 1;
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
						Snackbar.make(b.getRoot(), lang(R.string.newVersionFound) + "\n" + currVersion + " → " + version, Snackbar.LENGTH_SHORT)
								.setAction(R.string.update, v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName()))))
								.show();
					}
				} catch (Exception ignore) {
				}
			}).run();
		}
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
		if (getPlayIndex() != -1)
			togglePlay(null);
		else
			super.onBackPressed();
	}

	@Override
	public void onResume() {
		super.onResume();

		initVar(false);
		setDriver();
		checkThings();

		new Handler().postDelayed(() -> update(), 1000);


		firebase_storeCount.attachEventListener(true);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		switch (requestCode) {
			case 0:
				checkThings();
				break;
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		firebase_storeCount.attachEventListener(false);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		LaunchpadActivity.removeDriverListener(MainActivity.this);
	}
}