package com.kimjisub.launchpad.manager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.kimjisub.launchpad.R;

public class ThemeResources {
	String packageName;

	Resources customRes;
	Resources defaultRes;


	public Drawable playbg, custom_logo;
	public Drawable btn, btn_;
	public Drawable chainled, chain, chain_, chain__;
	public Drawable phantom, phantom_;
	public Drawable xml_prev, xml_play, xml_pause, xml_next;
	public int checkbox, trace_log, option_window, option_window_checkbox, option_window_btn, option_window_btn_text;
	public boolean isChainLED = true;

	public ThemeResources(Context context, String packageName) throws Exception {
		this.packageName = packageName;

		customRes = context.getPackageManager().getResourcesForApplication(packageName);
		defaultRes = context.getResources();


		// Drawable
		playbg = getCustomDrawable("playbg");
		try {
			custom_logo = getCustomDrawable("custom_logo");
		} catch (Exception ignore) {
			//custom_logo = defaultRes.getDrawable(R.drawable.custom_logo);
		}
		btn = getCustomDrawable("btn");
		btn_ = getCustomDrawable("btn_");
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
		xml_prev = getCustomDrawable("xml_prev");
		xml_play = getCustomDrawable("xml_play");
		xml_pause = getCustomDrawable("xml_pause");
		xml_next = getCustomDrawable("xml_next");


		// Color
		try {
			checkbox = getCustomColor("checkbox");
		} catch (Exception ignore) {
			checkbox = defaultRes.getColor(R.color.checkbox);
		}
		try {
			trace_log = getCustomColor("trace_log");
		} catch (Exception ignore) {
			trace_log = defaultRes.getColor(R.color.trace_log);
		}
		try {
			option_window = getCustomColor("option_window");
		} catch (Exception ignore) {
			option_window = defaultRes.getColor(R.color.option_window);
		}
		try {
			option_window_checkbox = getCustomColor("option_window_checkbox");
		} catch (Exception ignore) {
			option_window_checkbox = defaultRes.getColor(R.color.option_window_checkbox);
		}
		try {
			option_window_btn = getCustomColor("option_window_btn");
		} catch (Exception ignore) {
			option_window_btn = defaultRes.getColor(R.color.option_window_btn);
		}
		try {
			option_window_btn_text = getCustomColor("option_window_btn_text");
		} catch (Exception ignore) {
			option_window_btn_text = defaultRes.getColor(R.color.option_window_btn_text);
		}
	}

	public ThemeResources(Context context) throws Exception {
		defaultRes = context.getResources();

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

	private Drawable getCustomDrawable(String id) throws Exception {
		int resId = customRes.getIdentifier(packageName + ":drawable/" + id, null, null);
		return customRes.getDrawable(resId);
	}

	private int getCustomColor(String id) throws Exception {
		int resId = customRes.getIdentifier(packageName + ":color/" + id, null, null);
		return customRes.getColor(resId);
	}
}