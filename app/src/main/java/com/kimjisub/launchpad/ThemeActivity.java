package com.kimjisub.launchpad;

import android.content.Intent;
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
import com.kimjisub.launchpad.utils.Log;
import com.kimjisub.launchpad.utils.PreferenceManager;
import com.kimjisub.launchpad.utils.ThemePack;

import java.util.ArrayList;

public class ThemeActivity extends BaseActivity {

	RecyclerView RV_list;
	TextView TV_apply;

	static ArrayList<ThemePack> L_theme;

	void initVar() {
		RV_list = findViewById(R.id.list);
		TV_apply = findViewById(R.id.apply);

		L_theme = ThemePack.getThemePackList(getApplicationContext());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_theme);
	}

	@Override
	public void onResume() {
		super.onResume();
		initVar();

		final CarouselLayoutManager layoutManager = new CarouselLayoutManager(CarouselLayoutManager.HORIZONTAL, false);
		layoutManager.setPostLayoutListener(new CarouselZoomPostLayoutListener());

		RV_list.setLayoutManager(layoutManager);
		RV_list.setHasFixedSize(true);
		RV_list.setAdapter(new Adapter());
		RV_list.addOnScrollListener(new CenterScrollListener());

		layoutManager.scrollToPosition(mGetTheme());

		TV_apply.setOnClickListener(v -> {
			mSetTheme(layoutManager.getCenterItemPosition());
			finish();
		});
	}

	public void mSetTheme(int i) {
		if (L_theme.size() != i)
			PreferenceManager.SelectedTheme.save(ThemeActivity.this, L_theme.get(i).package_name);
		else
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=com.kimjisub.launchpad.theme.")));
	}

	public int mGetTheme() {
		int ret = 0;
		String selectedThemePackageName = PreferenceManager.SelectedTheme.load(ThemeActivity.this);
		int i = 0;
		for (ThemePack themePack : L_theme) {
			Log.log(selectedThemePackageName + ", " + themePack.package_name);
			if (themePack.package_name.equals(selectedThemePackageName)) {
				ret = i;
				break;
			}
			i++;
		}
		return ret;
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

	private final class Adapter extends RecyclerView.Adapter<ViewHolder> {

		private int itemsCount = 0;

		Adapter() {
			itemsCount = L_theme.size() + 1;
		}

		@Override
		public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
			return new ViewHolder(View.inflate(parent.getContext(), R.layout.theme_list, null));
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			try {
				ThemePack theme = L_theme.get(position);
				holder.theme_icon.setBackground(theme.icon);
				holder.theme_version.setText(theme.version);
				holder.theme_author.setText(theme.author);
			} catch (Exception ignore) {
				holder.theme_icon.setBackground(drawable(R.drawable.theme_add));
				holder.theme_version.setText(lang(R.string.themeDownload));
			}
		}

		@Override
		public int getItemCount() {
			return itemsCount;
		}
	}
}
