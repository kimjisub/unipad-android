package com.kimjisub.launchpad;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.kimjisub.design.Item;
import com.kimjisub.launchpad.manage.FileManager;
import com.kimjisub.launchpad.manage.Networks;
import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.Tools;
import com.kimjisub.launchpad.manage.UIManager;
import com.kimjisub.launchpad.manage.Unipack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.kimjisub.launchpad.manage.Tools.lang;
import static com.kimjisub.launchpad.manage.Tools.log;


public class Main extends BaseActivity {
	LinearLayout LL_List;
	String ProjectFolderURL;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);


		LL_List = (LinearLayout) findViewById(R.id.list);
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
								.setMessage(lang(Main.this, com.kimjisub.design.R.string.doYouWantToDeleteProject))
								.setPositiveButton(lang(Main.this, com.kimjisub.design.R.string.cancel), null)
								.setNegativeButton(lang(Main.this, com.kimjisub.design.R.string.delete), new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										FileManager.deleteFolder(URL[i]);
										update();
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
		TV_path = (TextView) LL_explorer.findViewById(R.id.path);
		LV_list = (ListView) LL_explorer.findViewById(R.id.list);

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
					if (file.canRead()) {

						loadUnipack(file.getPath());

					} else if (file.canRead()) {
						UIManager.showDialog(Main.this, file.getName(), lang(Main.this, R.string.isNotAnUniPack));
					} else {
						UIManager.showDialog(Main.this, file.getName(), lang(Main.this, R.string.cantReadFile));
					}

				}
			}
		});
		getDir(fileExplorerPath);


		dialog.setView(LL_explorer);
		dialog.show();
	}

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
					Unipack project = new Unipack(projectPath, true);

					if (project.ErrorDetail == null) {
						publishProgress(lang(Main.this, R.string.analyzeComplete),
							lang(Main.this, R.string.title) + " : " + project.title + "\n" +
								lang(Main.this, R.string.producerName) + " : " + project.producerName + "\n" +
								lang(Main.this, R.string.scale) + " : " + project.buttonX + " x " + project.buttonY + "\n" +
								lang(Main.this, R.string.chainCount) + " : " + project.chain + "\n" +
								lang(Main.this, R.string.capacity) + " : " + FileManager.byteToMB(FileManager.getFolderSize(projectPath)) + " MB" + " MB");
					} else if (project.CriticalError) {
						publishProgress(lang(Main.this, R.string.analyzeFailed), project.ErrorDetail);
						FileManager.deleteFolder(projectPath);
					} else {
						publishProgress(lang(Main.this, R.string.warning), project.ErrorDetail);
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
									startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/fbStore/apps/details?id=" + getPackageName())));
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