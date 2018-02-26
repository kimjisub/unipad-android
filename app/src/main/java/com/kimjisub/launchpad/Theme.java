package com.kimjisub.launchpad;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.azoft.carousellayoutmanager.CarouselLayoutManager;
import com.azoft.carousellayoutmanager.CarouselZoomPostLayoutListener;
import com.azoft.carousellayoutmanager.CenterScrollListener;
import com.kimjisub.launchpad.manage.SaveSetting;
import com.kimjisub.launchpad.manage.ThemePack;

import java.util.ArrayList;
import java.util.List;

import static com.kimjisub.launchpad.manage.Tools.log;

public class Theme extends BaseActivity {

	RecyclerView RV_list;
	TextView TV_apply;

	static ArrayList<ThemePack> themeList;


	void initVar() {
		RV_list = findViewById(R.id.list);
		TV_apply = findViewById(R.id.apply);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_theme);
		initVar();
	}

	@Override
	protected void onResume() {
		super.onResume();
		initVar();
		getThemeList();

		final CarouselLayoutManager layoutManager = new CarouselLayoutManager(CarouselLayoutManager.HORIZONTAL, false);
		layoutManager.setPostLayoutListener(new CarouselZoomPostLayoutListener());

		RV_list.setLayoutManager(layoutManager);
		RV_list.setHasFixedSize(true);
		RV_list.setAdapter(new Adapter());
		RV_list.addOnScrollListener(new CenterScrollListener());

		layoutManager.scrollToPosition(mGetTheme());

		TV_apply.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mSetTheme(layoutManager.getCenterItemPosition());
				requestRestart(Theme.this);
			}
		});
	}

	public void mSetTheme(int i) {
		if (themeList.size() == i) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=com.kimjisub.launchpad.theme.")));
		} else {
			ThemePack theme = themeList.get(i);
			SaveSetting.SelectedTheme.save(Theme.this, theme.package_name);
		}
	}

	public int mGetTheme() {
		int ret = 0;
		String packageName = SaveSetting.SelectedTheme.load(Theme.this);
		for (int i = 0; i < themeList.size(); i++) {
			ThemePack theme = themeList.get(i);
			log(packageName + ", " + theme.package_name);
			if (theme.package_name.equals(packageName)) {
				ret = i;
				break;
			}
		}
		return ret;
	}

	private final class Adapter extends RecyclerView.Adapter<ViewHolder> {

		private int itemsCount = 0;

		Adapter() {
			itemsCount = themeList.size() + 1;
		}

		@Override
		public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
			return new ViewHolder(View.inflate(parent.getContext(), R.layout.theme_list, null));
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			try {
				ThemePack theme = themeList.get(position);
				holder.theme_icon.setBackground(theme.icon);
				holder.theme_version.setText(theme.version);
				holder.theme_author.setText(theme.author);
			} catch (Exception ignore) {
				holder.theme_icon.setBackground(getResources().getDrawable(R.drawable.theme_add));
				holder.theme_version.setText(lang(R.string.themeDownload));
			}
		}

		@Override
		public int getItemCount() {
			return itemsCount;
		}
	}

	final static class ViewHolder extends RecyclerView.ViewHolder {
		ImageView theme_icon;
		TextView theme_version;
		TextView theme_author;

		ViewHolder(View itemView) {
			super(itemView);
			theme_icon = itemView.findViewById(R.id.theme_icon);
			theme_version = itemView.findViewById(R.id.theme_version);
			theme_author = itemView.findViewById(R.id.theme_author);
		}
	}

	public void getThemeList() {
		themeList = new ArrayList<>();

		List<ApplicationInfo> packages = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);

		addThemeInList(getPackageName());

		for (ApplicationInfo applicationInfo : packages) {
			String packageName = applicationInfo.packageName;
			if (packageName.startsWith("com.kimjisub.launchpad.theme."))
				addThemeInList(packageName);
		}
	}

	void addThemeInList(String packageName) {
		ThemePack theme = new ThemePack(Theme.this, packageName);
		try {
			theme.init();
			themeList.add(theme);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onBackPressed() {
		requestRestart(Theme.this);
	}
}
