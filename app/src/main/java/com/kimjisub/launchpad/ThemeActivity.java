package com.kimjisub.launchpad;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.azoft.carousellayoutmanager.CarouselLayoutManager;
import com.azoft.carousellayoutmanager.CarouselZoomPostLayoutListener;
import com.azoft.carousellayoutmanager.CenterScrollListener;
import com.kimjisub.launchpad.databinding.ActivityThemeBinding;
import com.kimjisub.launchpad.adapter.ThemeAdapter;
import com.kimjisub.launchpad.adapter.ThemeItem;
import com.kimjisub.launchpad.manager.Log;
import com.kimjisub.launchpad.manager.PreferenceManager;

import java.util.ArrayList;

public class ThemeActivity extends BaseActivity {
	ActivityThemeBinding b;

	public ArrayList<ThemeItem> L_theme;

	void initVar() {
		L_theme = ThemeItem.getThemePackList(getApplicationContext());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		b = setContentViewBind(R.layout.activity_theme);
	}

	@Override
	public void onResume() {
		super.onResume();
		initVar();

		final CarouselLayoutManager layoutManager = new CarouselLayoutManager(CarouselLayoutManager.HORIZONTAL, false);
		layoutManager.setPostLayoutListener(new CarouselZoomPostLayoutListener());

		b.list.setLayoutManager(layoutManager);
		b.list.setHasFixedSize(true);
		b.list.setAdapter(new ThemeAdapter(ThemeActivity.this));
		b.list.addOnScrollListener(new CenterScrollListener());

		layoutManager.scrollToPosition(mGetTheme());

		b.apply.setOnClickListener(v -> {
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
