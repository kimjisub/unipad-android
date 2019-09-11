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
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anjlab.android.iab.v3.TransactionDetails;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.kimjisub.design.PackViewSimple;
import com.kimjisub.design.dialog.FileExplorerDialog;
import com.kimjisub.design.panel.MainPackPanel;
import com.kimjisub.launchpad.BuildConfig;
import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.adapter.UnipackAdapter;
import com.kimjisub.launchpad.adapter.UnipackItem;
import com.kimjisub.launchpad.databinding.ActivityMainBinding;
import com.kimjisub.launchpad.db.ent.UnipackENT;
import com.kimjisub.launchpad.db.ent.UnipackOpenENT;
import com.kimjisub.launchpad.manager.BillingManager;
import com.kimjisub.launchpad.manager.PreferenceManager;
import com.kimjisub.launchpad.manager.ThemeResources;
import com.kimjisub.launchpad.manager.Unipack;
import com.kimjisub.launchpad.midi.MidiConnection;
import com.kimjisub.launchpad.midi.controller.MidiController;
import com.kimjisub.launchpad.network.Networks;
import com.kimjisub.manager.FileManager;
import com.kimjisub.manager.Log;

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

import static com.kimjisub.launchpad.manager.Constant.AUTOPLAY_AUTOMAPPING_DELAY_PRESET;

public class MainActivity extends BaseActivity {
	ActivityMainBinding b;

	BillingManager billingManager;

	// Firebase
	Networks.FirebaseManager firebase_storeCount;
	ValueAnimator VA_floatingAnimation;

	ArrayList<UnipackItem> list;
	UnipackAdapter adapter;
	int lastPlayIndex = -1;
	boolean updateProcessing = false;

	MidiController midiController;

	void initVar(boolean onFirst) {
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
			list = new ArrayList<>();
			adapter = new UnipackAdapter(MainActivity.this, list, new UnipackAdapter.EventListener() {

				@Override
				public void onViewClick(UnipackItem item, PackViewSimple v) {
					if (!item.isMoving)
						togglePlay(item);
				}

				@Override
				public void onViewLongClick(UnipackItem item, PackViewSimple v) {
				}

				@Override
				public void onPlayClick(UnipackItem item, PackViewSimple v) {
					if (!item.isMoving)
						pressPlay(item);
				}
			});
			adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
				@Override
				public void onItemRangeInserted(int positionStart, int itemCount) {
					super.onItemRangeInserted(positionStart, itemCount);
					b.errItem.setVisibility(list.size() == 0 ? View.VISIBLE : View.GONE);
				}

				@Override
				public void onItemRangeRemoved(int positionStart, int itemCount) {
					super.onItemRangeRemoved(positionStart, itemCount);
					b.errItem.setVisibility(list.size() == 0 ? View.VISIBLE : View.GONE);
				}
			});
			b.errItem.setVisibility(list.size() == 0 ? View.VISIBLE : View.GONE);

			DividerItemDecoration divider = new DividerItemDecoration(MainActivity.this, DividerItemDecoration.VERTICAL);
			divider.setDrawable(getResources().getDrawable(R.drawable.border_divider));
			b.recyclerView.addItemDecoration(divider);
			b.recyclerView.setHasFixedSize(false);
			b.recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
			b.recyclerView.setAdapter(adapter);

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

		midiController = new MidiController() {
			@Override
			public void onAttach() {
				Log.driverCycle("MainActivity onConnected()");
				updateLP();
			}

			@Override
			public void onDetach() {
				Log.driverCycle("MainActivity onDisconnected()");
			}

			@Override
			public void onPadTouch(int x, int y, boolean upDown, int velo) {
				if (!((x == 3 || x == 4) && (y == 3 || y == 4))) {
					if (upDown)
						MidiConnection.driver.sendPadLED(x, y, new int[]{40, 61}[(int) (Math.random() * 2)]);
					else
						MidiConnection.driver.sendPadLED(x, y, 0);
				}
			}

			@Override
			public void onFunctionkeyTouch(int f, boolean upDown) {
				if (f == 0 && upDown) {
					if (havePrev()) {
						togglePlay(lastPlayIndex - 1);
						b.recyclerView.smoothScrollToPosition(lastPlayIndex);
						//b.recyclerView.smoothScrollToPosition(0, list.find(lastPlayIndex).packViewSimple.getTop() + (-Scale_Height / 2) + (list.find(lastPlayIndex).packViewSimple.getHeight() / 2));
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
						list.get(lastPlayIndex).packViewSimple.onPlayClick();
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
		};
	}

	// =============================================================================================

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		b = setContentViewBind(R.layout.activity_main);
		initVar(true);
		initPannel();

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
				UnipackItem item = getSelected();
				if (item != null) {
					new Thread(() -> {
						UnipackENT unipackENT = db.unipackDAO().find(item.unipack.F_project.getName());
						unipackENT.pin = !unipackENT.pin;
						db.unipackDAO().update(unipackENT);
					}).start();
				}
			}

			@Override
			public void onBookmarkClick(View v) {
				UnipackItem item = getSelected();
				if (item != null) {
					new Thread(() -> {
						UnipackENT unipackENT = db.unipackDAO().find(item.unipack.F_project.getName());
						unipackENT.bookmark = !unipackENT.bookmark;
						db.unipackDAO().update(unipackENT);
					}).start();
				}
			}

			@Override
			public void onEditClick(View v) {

			}

			@Override
			public void onStorageClick(View v) {
				UnipackItem item = getSelected();
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
						protected void onPostExecute(String result) {
							super.onPostExecute(result);
							update();
						}

					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				}
			}

			@Override
			public void onYoutubeClick(View v) {
				UnipackItem item = getSelected();
				if (item != null) {
					String website = "https://www.youtube.com/results?search_query=UniPad+" + item.unipack.title;
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(website)));
				}
			}

			@Override
			public void onWebsiteClick(View v) {
				UnipackItem item = getSelected();
				if (item != null) {
					String website = item.unipack.website;
					if (website != null)
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(website)));
				}
			}

			@Override
			public void onFuncClick(View v) {
				UnipackItem item = getSelected();
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
				UnipackItem item = getSelected();
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

		b.errItem.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, FBStoreActivity.class)));


		checkThings();
		update(false);
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
		if (updateProcessing)
			return;

		b.swipeRefreshLayout.setRefreshing(true);
		updateProcessing = true;

		togglePlay(null);
		updatePanel(true);

		(new AsyncTask<String, String, String>() {

			ArrayList<UnipackItem> I_curr = new ArrayList<>();
			ArrayList<UnipackItem> I_added = new ArrayList<>();
			ArrayList<UnipackItem> I_removed = new ArrayList<>(list);

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
						UnipackENT unipackENT = db.unipackDAO().getOrCreate(unipack.F_project.getName());
						UnipackItem packItem = new UnipackItem(unipack, path, unipackENT.bookmark, animateNew);

						I_curr.add(packItem);
					}

					for (UnipackItem item : I_curr) {
						int index = -1;
						int i = 0;
						for (UnipackItem item2 : I_removed) {
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
			protected void onPostExecute(String result) {
				super.onPostExecute(result);

				for (UnipackItem F_added : I_added) {

					int i = 0;
					long targetTime = FileManager.getInnerFileLastModified(F_added.unipack.F_project);
					for (UnipackItem item : list) {
						long testTime = FileManager.getInnerFileLastModified(item.unipack.F_project);
						if (targetTime > testTime)
							break;
						i++;
					}
					list.add(i, F_added);
					adapter.notifyItemInserted(i);
					b.panelTotal.b.unipackCount.setText(list.size() + "");
				}

				for (UnipackItem F_removed : I_removed) {
					int i = 0;
					for (UnipackItem item : list) {
						if (item.path.equals(F_removed.path)) {
							int I = i;
							list.remove(I);
							adapter.notifyItemRemoved(I);
							b.panelTotal.b.unipackCount.setText(list.size() + "");
							break;
						}
						i++;
					}
				}

				if (I_added.size() > 0) b.recyclerView.smoothScrollToPosition(0);

				b.swipeRefreshLayout.setRefreshing(false);
				updateProcessing = false;
			}

		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
		togglePlay(list.get(i));
	}

	@SuppressLint("SetTextI18n")
	public void togglePlay(UnipackItem target) {
		try {
			int i = 0;
			for (UnipackItem item : list) {
				PackViewSimple packViewSimple = item.packViewSimple;

				if (target != null && item.path.equals(target.path)) {
					item.isToggle = !item.isToggle;
					lastPlayIndex = i;
				} else
					item.isToggle = false;

				if (packViewSimple != null)
					packViewSimple.toggle(item.isToggle, color(R.color.red), item.flagColor);

				i++;
			}
			showSelectLPUI();

			updatePanel(false);

		} catch (ConcurrentModificationException e) {
			e.printStackTrace();
		}
	}

	public void pressPlay(UnipackItem item) {
		rescanScale(b.scale, b.paddingScale);

		new Thread(() -> {
			db.unipackOpenDAO().insert(new UnipackOpenENT(item.unipack.F_project.getName(), new Date()));
		}).start();


		Intent intent = new Intent(MainActivity.this, PlayActivity.class);
		intent.putExtra("path", item.path);
		startActivity(intent);
		MidiConnection.removeController(midiController);
	}

	int getSelectedIndex() {
		int index = -1;

		int i = 0;
		for (UnipackItem item : list) {
			if (item.isToggle) {
				index = i;
				break;
			}
			i++;
		}

		return index;
	}

	UnipackItem getSelected() {
		UnipackItem ret = null;

		int playIndex = getSelectedIndex();
		if (playIndex != -1)
			ret = list.get(playIndex);

		return ret;
	}

	// ============================================================================================= panel

	@SuppressLint("SetTextI18n")
	void initPannel() {
		b.panelTotal.b.customLogo.setImageResource(R.drawable.custom_logo);
		b.panelTotal.b.version.setText(BuildConfig.VERSION_NAME);


	}


	void updatePanel(boolean hardWork) {
		int selectedIndex = getSelectedIndex();
		Animation animation = AnimationUtils.loadAnimation(MainActivity.this, selectedIndex != -1 ? R.anim.panel_in : R.anim.panel_out);
		animation.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
				b.panelPack.setVisibility(View.VISIBLE);
				b.panelPack.setAlpha(1);
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				b.panelPack.setVisibility(selectedIndex != -1 ? View.VISIBLE : View.INVISIBLE);
				b.panelPack.setAlpha(selectedIndex != -1 ? 1 : 0);
			}

			@Override
			public void onAnimationRepeat(Animation animation) {

			}
		});

		if (selectedIndex == -1)
			updatePanelMain(hardWork);
		else
			updatePanelPack(list.get(selectedIndex));

		int visibility = b.panelPack.getVisibility();
		if ((visibility == View.VISIBLE && selectedIndex == -1)
				|| (visibility == View.INVISIBLE && selectedIndex != -1))
			b.panelPack.startAnimation(animation);
	}

	@SuppressLint("StaticFieldLeak")
	void updatePanelMain(boolean hardWork) {
		b.panelTotal.b.unipackCount.setText(list.size() + "");

		b.panelTotal.b.padTouchCount.setText(lang(R.string.measuring));


		String packageName = PreferenceManager.SelectedTheme.load(MainActivity.this);
		try {
			ThemeResources resources = new ThemeResources(MainActivity.this, packageName, false);
			b.panelTotal.b.selectedTheme.setText(resources.name);
		} catch (Exception e) {
			b.panelTotal.b.selectedTheme.setText(R.string.theme_name);
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
					b.panelTotal.b.unipackCapacity.setText(strings[0]);
				}
			}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	@SuppressLint("StaticFieldLeak")
	void updatePanelPack(UnipackItem item) {
		Unipack unipack = item.unipack;
		new Thread(() -> {
			UnipackENT unipackENT = db.unipackDAO().find(item.unipack.F_project.getName());

			int flagColor;
			if (unipack.CriticalError)
				flagColor = color(R.color.red);
			else
				flagColor = color(R.color.skyblue);

			if (unipackENT.bookmark)
				flagColor = color(R.color.orange);

			item.flagColor = flagColor;

			b.panelPack.setStar(unipackENT.pin);
			b.panelPack.setBookmark(unipackENT.bookmark);
		}).start();

		db.unipackOpenDAO().getCount(item.unipack.F_project.getName()).observe(this, integer -> {
			b.panelPack.b.openCount.setText(integer.toString());
		});


		b.panelPack.setStorage(!FileManager.isInternalFile(MainActivity.this, unipack.F_project));
		b.panelPack.b.title.setText(unipack.title);
		b.panelPack.b.subTitle.setText(unipack.producerName);
		b.panelPack.b.path.setText(item.path);
		b.panelPack.b.scale.setText(unipack.buttonX + " × " + unipack.buttonY);
		b.panelPack.b.chainCount.setText(unipack.chain + "");
		b.panelPack.b.soundCount.setText(lang(R.string.measuring));
		b.panelPack.b.ledCount.setText(lang(R.string.measuring));
		b.panelPack.b.fileSize.setText(lang(R.string.measuring));
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

	// ============================================================================================= Controller

	void updateLP() {
		showWatermark();
		showSelectLPUI();
	}

	boolean haveNow() {
		return 0 <= lastPlayIndex && lastPlayIndex <= list.size() - 1;
	}

	boolean haveNext() {
		return lastPlayIndex < list.size() - 1;
	}

	boolean havePrev() {
		return 0 < lastPlayIndex;
	}

	void showSelectLPUI() {
		if (havePrev())
			MidiConnection.driver.sendFunctionkeyLED(0, 63);
		else
			MidiConnection.driver.sendFunctionkeyLED(0, 5);

		if (haveNow())
			MidiConnection.driver.sendFunctionkeyLED(2, 61);
		else
			MidiConnection.driver.sendFunctionkeyLED(2, 0);

		if (haveNext())
			MidiConnection.driver.sendFunctionkeyLED(1, 63);
		else
			MidiConnection.driver.sendFunctionkeyLED(1, 5);
	}

	void showWatermark() {
		MidiConnection.driver.sendPadLED(3, 3, 61);
		MidiConnection.driver.sendPadLED(3, 4, 40);
		MidiConnection.driver.sendPadLED(4, 3, 40);
		MidiConnection.driver.sendPadLED(4, 4, 61);
	}

	// ============================================================================================= Activity

	@Override
	public void onBackPressed() {
		if (getSelectedIndex() != -1)
			togglePlay(null);
		else
			super.onBackPressed();
	}

	@Override
	public void onResume() {
		super.onResume();

		initVar(false);

		checkThings();

		new Handler().postDelayed(() -> update(), 1000);


		MidiConnection.setController(midiController);
		firebase_storeCount.attachEventListener(true);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch (requestCode) {
			case 0:
				checkThings();
				break;
		}
	}

	@Override
	public void onPause() {
		super.onPause();


		MidiConnection.setController(midiController);
		firebase_storeCount.attachEventListener(false);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		MidiConnection.removeController(midiController);
	}
}