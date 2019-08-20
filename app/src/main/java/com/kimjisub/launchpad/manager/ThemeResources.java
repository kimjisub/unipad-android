package com.kimjisub.launchpad.manager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.kimjisub.launchpad.BuildConfig;
import com.kimjisub.launchpad.R;

public class ThemeResources {
	String packageName;

	Resources customRes;
	Resources defaultRes;

	public Drawable icon;
	public String name, description, author;
	public String version;

	public Drawable playbg, custom_logo;
	public Drawable btn, btn_;
	public Drawable chainled, chain, chain_, chain__;
	public Drawable phantom, phantom_;
	public Drawable xml_prev, xml_play, xml_pause, xml_next;
	public int checkbox, trace_log, option_window, option_window_checkbox, option_window_btn, option_window_btn_text;
	public boolean isChainLED = true;

	public ThemeResources(Context context, String packageName, boolean fullLoad) throws Exception {
		this.packageName = packageName;

		customRes = context.getPackageManager().getResourcesForApplication(packageName);
		defaultRes = context.getResources();

		////////////////////////////////////////////////////////////////////////////////////////////////

		icon = getCustomDrawable("theme_ic", R.drawable.theme_ic);

		version = context.getPackageManager().getPackageInfo(packageName, 0).versionName;

		name = getCustomString("theme_name", R.string.theme_name);
		description = getCustomString("theme_description", R.string.theme_description);
		author = getCustomString("theme_author", R.string.theme_author);

		if (!fullLoad)
			return;

		////////////////////////////////////////////////////////////////////////////////////////////////

		// Drawable
		playbg = getCustomDrawable("playbg", R.drawable.playbg);
		try {
			custom_logo = getCustomDrawable("custom_logo");
		} catch (Exception ignore) {
			custom_logo = null;
		}
		btn = getCustomDrawable("btn", R.drawable.btn);
		btn_ = getCustomDrawable("btn_", R.drawable.btn_);
		try {
			chainled = getCustomDrawable("chainled");
		} catch (Exception e) {
			isChainLED = false;
			chain = getCustomDrawable("chain");
			chain_ = getCustomDrawable("chain_");
			chain__ = getCustomDrawable("chain__");
		}
		phantom = getCustomDrawable("phantom");
		try {
			phantom_ = getCustomDrawable("phantom_");
		} catch (Exception ignore) {
		}
		xml_prev = getCustomDrawable("xml_prev", R.drawable.xml_prev);
		xml_play = getCustomDrawable("xml_play", R.drawable.xml_play);
		xml_pause = getCustomDrawable("xml_pause", R.drawable.xml_pause);
		xml_next = getCustomDrawable("xml_next", R.drawable.xml_next);


		// Color
		checkbox = getCustomColor("checkbox", R.color.checkbox);
		trace_log = getCustomColor("trace_log", R.color.trace_log);
		option_window = getCustomColor("option_window", R.color.option_window);
		option_window_checkbox = getCustomColor("option_window_checkbox", R.color.option_window_checkbox);
		option_window_btn = getCustomColor("option_window_btn", R.color.option_window_btn);
		option_window_btn_text = getCustomColor("option_window_btn_text", R.color.option_window_btn_text);
	}

	public ThemeResources(Context context, boolean fullLoad) throws Exception {
		defaultRes = context.getResources();

		////////////////////////////////////////////////////////////////////////////////////////////////

		icon = defaultRes.getDrawable(R.drawable.theme_ic);

		version = BuildConfig.VERSION_NAME;

		name = defaultRes.getString(R.string.theme_name);
		description = defaultRes.getString(R.string.theme_description);
		author = defaultRes.getString(R.string.theme_author);

		if (!fullLoad)
			return;

		////////////////////////////////////////////////////////////////////////////////////////////////

		// Drawable
		playbg = defaultRes.getDrawable(R.drawable.playbg);
		custom_logo = defaultRes.getDrawable(R.drawable.custom_logo);
		btn = defaultRes.getDrawable(R.drawable.btn);
		btn_ = defaultRes.getDrawable(R.drawable.btn_);
		chainled = defaultRes.getDrawable(R.drawable.chainled);
		phantom = defaultRes.getDrawable(R.drawable.phantom);
		phantom_ = defaultRes.getDrawable(R.drawable.phantom_);
		xml_prev = defaultRes.getDrawable(R.drawable.xml_prev);
		xml_play = defaultRes.getDrawable(R.drawable.xml_play);
		xml_pause = defaultRes.getDrawable(R.drawable.xml_pause);
		xml_next = defaultRes.getDrawable(R.drawable.xml_next);

		// Color
		checkbox = defaultRes.getColor(R.color.checkbox);
		trace_log = defaultRes.getColor(R.color.trace_log);
		option_window = defaultRes.getColor(R.color.option_window);
		option_window_checkbox = defaultRes.getColor(R.color.option_window_checkbox);
		option_window_btn = defaultRes.getColor(R.color.option_window_btn);
		option_window_btn_text = defaultRes.getColor(R.color.option_window_btn_text);
	}

	private Drawable getCustomDrawable(String customId) throws Exception {
		int resId = getResourceId("drawable", customId);
		return customRes.getDrawable(resId);
	}

	private int getCustomColor(String customId) throws Exception {
		int resId = getResourceId("color", customId);
		return customRes.getColor(resId);
	}

	private String getCustomString(String customId) throws Exception {
		int resId = getResourceId("string", customId);
		return customRes.getString(resId);
	}

	private Drawable getCustomDrawable(String customId, int defaultId) {
		try {
			return getCustomDrawable(customId);
		} catch (Exception e) {
			return defaultRes.getDrawable(defaultId);
		}
	}

	private int getCustomColor(String customId, int defaultId) {
		try {
			return getCustomColor(customId);
		} catch (Exception e) {
			return defaultRes.getColor(defaultId);
		}
	}

	private String getCustomString(String customId, int defaultId) {
		try {
			return getCustomString(customId);
		} catch (Exception e) {
			return defaultRes.getString(defaultId);
		}
	}

	private int getResourceId(String type, String customId) throws Exception {
		return customRes.getIdentifier(packageName + ":" + type + "/" + customId, null, null);
	}
}