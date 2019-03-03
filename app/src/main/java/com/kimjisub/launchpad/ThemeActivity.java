package com.kimjisub.launchpad;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.widget.TextView;

import com.azoft.carousellayoutmanager.CarouselLayoutManager;
import com.azoft.carousellayoutmanager.CarouselZoomPostLayoutListener;
import com.azoft.carousellayoutmanager.CenterScrollListener;
import com.kimjisub.launchpad.listManager.ThemeAdapter;
import com.kimjisub.launchpad.listManager.ThemeItem;
import com.kimjisub.launchpad.utils.Log;
import com.kimjisub.launchpad.utils.PreferenceManager;

import java.util.ArrayList;

public class ThemeActivity extends BaseActivity {

	RecyclerView RV_list;
	TextView TV_apply;

	public ArrayList<ThemeItem> L_theme;

	void initVar() {
		RV_list = findViewById(R.id.list);
		TV_apply = findViewById(R.id.apply);

		L_theme = ThemeItem.getThemePackList(getApplicationContext());
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
		RV_list.setAdapter(new ThemeAdapter(ThemeActivity.this));
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
		for (ThemeItem themeItem : L_theme) {
			Log.log(selectedThemePackageName + ", " + themeItem.package_name);
			if (themeItem.package_name.equals(selectedThemePackageName)) {
				ret = i;
				break;
			}
			i++;
		}
		return ret;
	}
}
