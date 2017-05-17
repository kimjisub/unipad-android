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

import java.util.ArrayList;
import java.util.List;

public class Theme extends BaseActivity {
	
	static ArrayList<정보.Theme> themeList;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_theme);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		getThemeList();
		
		final CarouselLayoutManager layoutManager = new CarouselLayoutManager(CarouselLayoutManager.HORIZONTAL, false);
		layoutManager.setPostLayoutListener(new CarouselZoomPostLayoutListener());
		
		final RecyclerView recyclerView = (RecyclerView) findViewById(R.id.list);
		recyclerView.setLayoutManager(layoutManager);
		recyclerView.setHasFixedSize(true);
		recyclerView.setAdapter(new Adapter());
		recyclerView.addOnScrollListener(new CenterScrollListener());
		
		layoutManager.scrollToPosition(mGetTheme());
		
		findViewById(R.id.apply).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mSetTheme(layoutManager.getCenterItemPosition());
				재시작(Theme.this);
			}
		});
	}
	
	public void mSetTheme(int i) {
		if (themeList.size() == i) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=com.kimjisub.launchpad.theme.")));
		} else {
			정보.Theme theme = themeList.get(i);
			정보.설정.selectedTheme.저장하기(Theme.this, theme.package_name);
		}
	}
	
	public int mGetTheme() {
		int ret = 0;
		String packageName = 정보.설정.selectedTheme.불러오기(Theme.this);
		for (int i = 0; i < themeList.size(); i++) {
			정보.Theme theme = themeList.get(i);
			화면.log(packageName + ", " + theme.package_name);
			if (theme.package_name.equals(packageName)) {
				ret = i;
				break;
			}
		}
		return ret;
	}
	
	private final class Adapter extends RecyclerView.Adapter<ViewHolder> {
		
		private int itemsCount = 0;
		
		public Adapter() {
			itemsCount = themeList.size() + 1;
		}
		
		@Override
		public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
			return new ViewHolder(View.inflate(parent.getContext(), R.layout.theme_list, null));
		}
		
		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			try {
				정보.Theme theme = themeList.get(position);
				holder.theme_icon.setBackground(theme.icon);
				holder.theme_version.setText(theme.version);
				holder.theme_author.setText(theme.author);
			} catch (Exception ignore) {
				holder.theme_icon.setBackground(getResources().getDrawable(R.drawable.theme_add));
				holder.theme_version.setText(언어(R.string.themeDownload));
			}
		}
		
		@Override
		public int getItemCount() {
			return itemsCount;
		}
	}
	
	public final static class ViewHolder extends RecyclerView.ViewHolder {
		ImageView theme_icon;
		TextView theme_version;
		TextView theme_author;
		
		public ViewHolder(View itemView) {
			super(itemView);
			theme_icon = (ImageView) itemView.findViewById(R.id.theme_icon);
			theme_version = (TextView) itemView.findViewById(R.id.theme_version);
			theme_author = (TextView) itemView.findViewById(R.id.theme_author);
		}
	}
	
	public void getThemeList() {
		themeList = new ArrayList<>();
		
		List<ApplicationInfo> packages = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
		
		addTheme(getPackageName());
		
		for (ApplicationInfo applicationInfo : packages) {
			String packageName = applicationInfo.packageName;
			if (packageName.startsWith("com.kimjisub.launchpad.theme."))
				addTheme(packageName);
		}
	}
	
	void addTheme(String packageName) {
		정보.Theme theme = new 정보.Theme(Theme.this, packageName);
		try {
			theme.init();
			themeList.add(theme);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onBackPressed() {
		재시작(Theme.this);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		finishActivity(this);
	}
	
	String 언어(int id) {
		return getResources().getString(id);
	}
}
