package com.kimjisub.launchpad;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.clans.fab.FloatingActionMenu;
import com.kimjisub.design.Item;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.Tools;
import com.kimjisub.launchpad.manage.UIManager;
import com.kimjisub.launchpad.manage.Unipack;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.kimjisub.launchpad.manage.Tools.lang;
import static com.kimjisub.launchpad.manage.Tools.log;


public class Main extends BaseActivity {
	LinearLayout LL_List;
	FloatingActionMenu floatingActionMenu;
	String ProjectFolderURL;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);


		LL_List = findViewById(R.id.list);
		floatingActionMenu = findViewById(R.id.floatingMenu);
		ProjectFolderURL = SaveSetting.IsUsingSDCard.URL;

		updateCheck();
		noticeCheck();

		findViewById(R.id.fab_loadUniPack).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				unipackExplorer();
			}
		});

		findViewById(R.id.fab_store).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(Main.this, Store.class));
			}
		});

		findViewById(R.id.fab_setting).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(Main.this, Setting.class));
			}
		});


		floatingActionMenu.setOnMenuToggleListener(new FloatingActionMenu.OnMenuToggleListener() {
			Handler handler = new Handler();

			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					floatingActionMenu.close(true);
				}
			};

			@Override
			public void onMenuToggle(boolean opened) {
				if (opened) {
					handler.postDelayed(runnable, 3000);
				} else {
					handler.removeCallbacks(runnable);
				}
			}
		});


	}

	Item[] IT_items;
	String[] URL;
	Unipack[] unipacks;

	void update() {
		LL_List.removeAllViews();

		File projectFolder = new File(ProjectFolderURL);

		if (projectFolder.isDirectory()) {

			File[] projects = FileManager.sortByTime(projectFolder.listFiles());
			int num = projects.length;

			IT_items = new Item[num];
			URL = new String[num];
			unipacks = new Unipack[num];

			int count = 0;
			for (int i_ = 0; i_ < num; i_++) {
				final int i = i_;
				File project = projects[i];
				if (project.isFile()) continue;
				count++;

				URL[i] = ProjectFolderURL + "/" + project.getName();
				unipacks[i] = new Unipack(URL[i], false);

				Item IT_item = new Item(Main.this)
					.setTitle(unipacks[i].title)
					.setSubTitle(unipacks[i].producerName)
					.setSize(unipacks[i].buttonX + " x " + unipacks[i].buttonY)
					.setChain(unipacks[i].chain + "")
					.setCapacity(FileManager.getFolderSize(URL[i]))
					.setLED(unipacks[i].isKeyLED)
					.setAutoPlay(unipacks[i].isAutoPlay)
					.setOnPlayClickListener(new Item.OnPlayClickListener() {
						@Override
						public void onPlayClick() {
							Intent intent = new Intent(Main.this, Play.class);
							intent.putExtra("URL", URL[i]);
							startActivity(intent);
						}
					})
					.setOnDeleteClickListener(new Item.OnDeleteClickListener() {
						@Override
						public void onDeleteClick() {
							new AlertDialog.Builder(Main.this)
								.setTitle(unipacks[i].title)
								.setMessage(lang(Main.this, R.string.doYouWantToDeleteProject) + "\n" + URL[i])
								.setPositiveButton(lang(Main.this, R.string.cancel), null)
								.setNegativeButton(lang(Main.this, R.string.delete), new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										FileManager.deleteFolder(URL[i]);
										update();
									}
								})
								.show();
						}
					})
					.setOnEditClickListener(new Item.OnEditClickListener() {
						@Override
						public void onEditClick() {
							new AlertDialog.Builder(Main.this)
								.setTitle(unipacks[i].title)
								.setMessage(lang(Main.this, R.string.doYouWantToRemapProject) + "\n" + URL[i])
								.setPositiveButton(lang(Main.this, R.string.cancel), null)
								.setNegativeButton(lang(Main.this, R.string.accept), new DialogInterface.OnClickListener() {
									@SuppressLint("StaticFieldLeak")
									@Override
									public void onClick(DialogInterface dialog, int which) {


										final Unipack unipack = new Unipack(URL[i], true);


										if (unipack.isAutoPlay) {
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
													progressDialog.setTitle(lang(Main.this, R.string.analyzing));
													progressDialog.setMessage(lang(Main.this, R.string.wait));
													progressDialog.setCancelable(false);
													progressDialog.setMax(autoplay2.size());
													progressDialog.show();
													super.onPreExecute();
												}

												@Override
												protected String doInBackground(String... params) {

													autoplay3 = new ArrayList<>();
													int nextDuration = 0;
													MediaPlayer mplayer = new MediaPlayer();
													for (Unipack.AutoPlay e : autoplay2) {
														switch (e.func) {
															case Unipack.AutoPlay.ON:
																int num = e.num % unipack.sound[e.currChain][e.x][e.y].size();
																int duration = FileManager.wavDuration(mplayer, unipack.sound[e.currChain][e.x][e.y].get(num).URL);
																nextDuration = duration;
																autoplay3.add(e);
																break;
															case Unipack.AutoPlay.CHAIN:
																autoplay3.add(e);
																break;
															case Unipack.AutoPlay.DELAY:
																e.d = nextDuration-1;
																autoplay3.add(e);
																break;
														}
														publishProgress();
													}
													mplayer.release();

													StringBuilder stringBuilder = new StringBuilder();
													for (Unipack.AutoPlay e : autoplay3) {
														switch (e.func) {
															case Unipack.AutoPlay.ON:
																int num = e.num % unipack.sound[e.currChain][e.x][e.y].size();
																Tools.log("t " + (e.x + 1) + " " + (e.y + 1) + " (" + (e.currChain + 1) + " " + (e.x + 1) + " " + (e.y + 1) + " " + num + ") " + new File(unipack.sound[e.currChain][e.x][e.y].get(num).URL).getName());
																stringBuilder.append("t " + (e.x+1) + " " + (e.y+1)+"\n");
																break;
															case Unipack.AutoPlay.CHAIN:
																Tools.log("c " + (e.c + 1));
																stringBuilder.append("c " + (e.c + 1)+"\n");
																break;
															case Unipack.AutoPlay.DELAY:
																Tools.log("d " + e.d);
																stringBuilder.append("d " + e.d+"\n");
																break;
														}
													}
													try {
														File filePre=new File(URL[i], "autoPlay");
														File fileNow = new File(URL[i], "autoPlay_"+new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss").format(new Date(System.currentTimeMillis())));
														filePre.renameTo(fileNow);

														BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(URL[i] + "/autoPlay")));
														writer.write(stringBuilder.toString());
														writer.close();
													} catch (FileNotFoundException e) {
														e.printStackTrace();
													}catch (IOException ee){
														ee.printStackTrace();
													}

													return null;
												}

												@Override
												protected void onProgressUpdate(String... progress) {
													progressDialog.incrementProgressBy(1);
												}

												@Override
												protected void onPostExecute(String result) {
													super.onPostExecute(result);

													try {
														if (progressDialog != null && progressDialog.isShowing())
															progressDialog.dismiss();
														new AlertDialog.Builder(Main.this)
															.setTitle(lang(Main.this, R.string.success))
															.setMessage(lang(Main.this, R.string.remapDone))
															.setPositiveButton(lang(Main.this, R.string.accept), null)
															.show();
													} catch (Exception e) {
														e.printStackTrace();
													}
												}
											}).execute();


										} else {
											new AlertDialog.Builder(Main.this)
												.setTitle(lang(Main.this, R.string.failed))
												.setMessage(lang(Main.this, R.string.remapFail))
												.setPositiveButton(lang(Main.this, R.string.accept), null)
												.show();
										}
									}
								})
								.show();
						}
					})
					.setOnViewClickListener(new Item.OnViewClickListener() {
						@Override
						public void onViewClick(Item v) {
							togglePlay(i);
							toggleInfo(-1);
						}
					})
					.setOnViewLongClickListener(new Item.OnViewLongClickListener() {
						@Override
						public void onViewLongClick(Item v) {
							togglePlay(-1);
							toggleInfo(i);
						}
					});

				IT_items[i] = IT_item;
				LL_List.addView(IT_item);
			}

			if (count == 0) {
				LL_List.addView(Item.errItem(Main.this, new Item.OnViewClickListener() {
					@Override
					public void onViewClick(Item v) {
						startActivity(new Intent(Main.this, Store.class));
					}
				}));
			}

		} else {
			projectFolder.mkdir();

			LL_List.addView(Item.errItem(Main.this, new Item.OnViewClickListener() {
				@Override
				public void onViewClick(Item v) {
					startActivity(new Intent(Main.this, Store.class));
				}
			}));
		}

		File nomedia = new File(ProjectFolderURL + "/.nomedia");
		if (!nomedia.isFile()) {
			try {
				(new FileWriter(nomedia)).close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		log(ProjectFolderURL);
	}

	void togglePlay(int n) {
		for (int i = 0; i < IT_items.length; i++) {
			Item item = IT_items[i];
			if (item != null) {
				if (n != i)
					item.togglePlay(false);
				else
					item.togglePlay();
			}
		}
	}

	void toggleInfo(int n) {
		for (int i = 0; i < IT_items.length; i++) {
			Item item = IT_items[i];
			if (item != null) {
				if (n != i)
					item.toggleInfo(false);
				else
					item.toggleInfo();
			}
		}
	}


	List<String> mItem;
	List<String> mPath;
	TextView TV_path;
	ListView LV_list;

	void unipackExplorer() {
		final AlertDialog dialog = (new AlertDialog.Builder(Main.this)).create();
		LinearLayout LL_explorer = (LinearLayout) View.inflate(Main.this, R.layout.file_explorer, null);
		TV_path = LL_explorer.findViewById(R.id.path);
		LV_list = LL_explorer.findViewById(R.id.list);

		String fileExplorerPath = SaveSetting.FileExplorerPath.load(Main.this);


		LV_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final File file = new File(mPath.get(position));
				if (file.isDirectory()) {
					if (file.canRead())
						getDir(mPath.get(position));
					else
						UIManager.showDialog(Main.this, file.getName(), lang(Main.this, R.string.cantReadFolder));
				} else {
					if (file.canRead())
						loadUnipack(file.getPath());
					else
						UIManager.showDialog(Main.this, file.getName(), lang(Main.this, R.string.cantReadFile));


				}
			}
		});
		getDir(fileExplorerPath);


		dialog.setView(LL_explorer);
		dialog.show();
	}

	void getDir(String dirPath) {
		SaveSetting.FileExplorerPath.save(Main.this, dirPath);
		TV_path.setText(dirPath);

		mItem = new ArrayList<>();
		mPath = new ArrayList<>();
		File f = new File(dirPath);
		File[] files = FileManager.sortByName(f.listFiles());
		if (!dirPath.equals("/")) {
			mItem.add("../");
			mPath.add(f.getParent());
		}
		for (File file : files) {
			String name = file.getName();
			if (name.indexOf('.') != 0) {
				if (file.isDirectory()) {
					mPath.add(file.getPath());
					mItem.add(name + "/");
				} else if (name.lastIndexOf(".zip") == name.length() - 4 || name.lastIndexOf(".uni") == name.length() - 4) {
					mPath.add(file.getPath());
					mItem.add(file.getName());
				}
			}
		}
		ArrayAdapter<String> fileList = new ArrayAdapter<>(Main.this, android.R.layout.simple_list_item_1, mItem);
		LV_list.setAdapter(fileList);
	}

	@SuppressLint("StaticFieldLeak")
	void loadUnipack(final String zipPath) {

		(new AsyncTask<String, String, String>() {

			ProgressDialog progressDialog = new ProgressDialog(Main.this);

			@Override
			protected void onPreExecute() {

				progressDialog.setTitle(lang(Main.this, R.string.analyzing));
				progressDialog.setMessage(lang(Main.this, R.string.wait));
				progressDialog.setCancelable(false);
				progressDialog.show();
				super.onPreExecute();
			}

			@Override
			protected String doInBackground(String... params) {

				String projectPath = ProjectFolderURL + "/" + FileManager.randomString(10) + "/";

				try {
					FileManager.unZipFile(zipPath, projectPath);
					Unipack unipack = new Unipack(projectPath, true);

					if (unipack.ErrorDetail == null) {
						publishProgress(lang(Main.this, R.string.analyzeComplete), Unipack.getInfoText(Main.this, unipack, projectPath));
					} else if (unipack.CriticalError) {
						publishProgress(lang(Main.this, R.string.analyzeFailed), unipack.ErrorDetail);
						FileManager.deleteFolder(projectPath);
					} else {
						publishProgress(lang(Main.this, R.string.warning), unipack.ErrorDetail);
					}

				} catch (IOException e) {
					publishProgress(lang(Main.this, R.string.analyzeFailed), e.toString());
					FileManager.deleteFolder(projectPath);
				}

				return null;
			}

			@Override
			protected void onProgressUpdate(String... progress) {
				UIManager.showDialog(Main.this, progress[0], progress[1]);
			}

			@Override
			protected void onPostExecute(String result) {
				progressDialog.dismiss();
				update();
				super.onPostExecute(result);
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}


	void updateCheck() {
		new Networks.CheckVersion(getPackageName()).setOnEndListener(new Networks.CheckVersion.onEndListener() {
			@Override
			public void onEnd(String verson) {
				try {
					String currVerson = BuildConfig.VERSION_NAME;
					if (verson != null && !currVerson.equals(verson)) {
						new AlertDialog.Builder(Main.this)
							.setTitle(lang(Main.this, R.string.newVersionFound))
							.setMessage(lang(Main.this, R.string.currentVersion) + " : " + currVerson + "\n" +
								lang(Main.this, R.string.newVersion) + " : " + verson)
							.setPositiveButton(lang(Main.this, R.string.update), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
									dialog.dismiss();
								}
							})
							.setNegativeButton(lang(Main.this, R.string.ignore), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									dialog.dismiss();
								}
							})
							.show();
					}
				} catch (Exception ignore) {
				}
			}
		}).run();
	}

	void noticeCheck() {
		new Networks.CheckNotice(lang(Main.this, R.string.language)).setOnEndListener(new Networks.CheckNotice.onEndListener() {
			@Override
			public void onEnd(final String title, final String content) {
				int px1 = UIManager.dpToPx(Main.this, 25);
				int px2 = UIManager.dpToPx(Main.this, 15);


				if (title != null && content != null) {
					String prevNotice = SaveSetting.PrevNotice.load(Main.this);

					if (!prevNotice.equals(content)) {

						TextView textView = new TextView(Main.this);
						textView.setText(Html.fromHtml(content));
						textView.setPadding(px1, px2, px1, 0);
						textView.setTextColor(0xFF000000);
						textView.setLinkTextColor(0xffffaf00);
						textView.setHighlightColor(0xffffaf00);
						textView.setTextSize(16);
						textView.setClickable(true);
						textView.setMovementMethod(LinkMovementMethod.getInstance());

						LinearLayout linearLayout = new LinearLayout(Main.this);
						linearLayout.addView(textView);

						new AlertDialog.Builder(Main.this)
							.setTitle(title)
							.setPositiveButton(lang(Main.this, R.string.accept), null)
							.setNegativeButton(lang(Main.this, R.string.doNotSee), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									SaveSetting.PrevNotice.save(Main.this, content);
								}
							})
							.setCancelable(false)
							.setView(linearLayout)
							.show();

					}
				}
			}
		}).run();
	}

	@Override
	public void onBackPressed() {
		boolean clear = true;
		for (Item item : IT_items) {

			if (item != null) {

				if (item.isPlay() || item.isInfo())
					clear = false;

				item.togglePlay(false);
				item.toggleInfo(false);
			}
		}

		if (clear)
			super.onBackPressed();
	}

	@Override
	protected void onResume() {
		super.onResume();
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

		if (UIManager.Scale[0] == 0) {
			Tools.log("padding 크기값들이 잘못되었습니다.");
			restartApp(Main.this);
		}

		update();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		finishActivity(this);
	}
}