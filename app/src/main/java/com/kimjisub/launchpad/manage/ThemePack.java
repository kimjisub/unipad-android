package com.kimjisub.launchpad.manage;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.kimjisub.launchpad.R;

public class ThemePack {
	public Context context;
	public String package_name;
	public Drawable icon;
	public String name;
	public String author;
	public String description;
	public String version;
	public Resources resources;
	
	public ThemePack(Context context, String package_name) throws Exception {
		this.context = context;
		this.package_name = package_name;
		
		version = context.getPackageManager().getPackageInfo(package_name, 0).versionName;
		
		android.content.res.Resources res = context.getPackageManager().getResourcesForApplication(package_name);
		icon = res.getDrawable(res.getIdentifier(package_name + ":drawable/theme_ic", null, null));
		name = res.getString(res.getIdentifier(package_name + ":string/theme_name", null, null));
		description = res.getString(res.getIdentifier(package_name + ":string/theme_description", null, null));
		author = res.getString(res.getIdentifier(package_name + ":string/theme_author", null, null));
	}
	
	public void loadThemeResources() throws Exception {
		resources = new Resources(context.getPackageManager().getResourcesForApplication(package_name));
	}
	
	public void loadDefaultThemeResources() throws Exception {
		resources = new Resources();
	}
	
	public class Resources {
		android.content.res.Resources res;
		
		public Drawable playbg, custom_logo;
		public Drawable btn, btn_;
		public Drawable chainled, chain, chain_, chain__;
		public Drawable phantom, phantom_;
		public Drawable xml_prev, xml_play, xml_pause, xml_next;
		public int checkbox, trace_log, option_window, option_window_checkbox, option_window_btn, option_window_btn_text;
		public boolean isChainLED = true;
		
		public Resources(android.content.res.Resources res) throws Exception {
			this.res = res;
			
			
			// Drawable
			this.playbg = getDrawable("playbg");
			try {
				this.custom_logo = getDrawable("custom_logo");
			} catch (Exception ignore) {
			}
			this.btn = getDrawable("btn");
			this.btn_ = getDrawable("btn_");
			try {
				this.chainled = getDrawable("chainled");
			} catch (Exception e) {
				isChainLED = false;
				this.chain = getDrawable("chain");
				this.chain_ = getDrawable("chain_");
				this.chain__ = getDrawable("chain__");
			}
			this.phantom = getDrawable("phantom");
			try {
				this.phantom_ = getDrawable("phantom_");
			} catch (Exception ignore) {
			}
			this.xml_prev = getDrawable("xml_prev");
			this.xml_play = getDrawable("xml_play");
			this.xml_pause = getDrawable("xml_pause");
			this.xml_next = getDrawable("xml_next");
			
			
			// Color
			try {
				this.checkbox = getColor("checkbox");
			} catch (Exception ignore) {
				this.checkbox = context.getResources().getColor(R.color.checkbox);
			}
			try {
				this.trace_log = getColor("trace_log");
			} catch (Exception ignore) {
				this.trace_log = context.getResources().getColor(R.color.trace_log);
			}
			try {
				this.option_window = getColor("option_window");
			} catch (Exception ignore) {
				this.option_window = context.getResources().getColor(R.color.option_window);
			}
			try {
				this.option_window_checkbox = getColor("option_window_checkbox");
			} catch (Exception ignore) {
				this.option_window_checkbox = context.getResources().getColor(R.color.option_window_checkbox);
			}
			try {
				this.option_window_btn = getColor("option_window_btn");
			} catch (Exception ignore) {
				this.option_window_btn = context.getResources().getColor(R.color.option_window_btn);
			}
			try {
				this.option_window_btn_text = getColor("option_window_btn_text");
			} catch (Exception ignore) {
				this.option_window_btn_text = context.getResources().getColor(R.color.option_window_btn_text);
			}
		}
		
		public Resources() throws Exception {
			// Drawable
			this.playbg = context.getResources().getDrawable(R.drawable.playbg);
			this.custom_logo = context.getResources().getDrawable(R.drawable.custom_logo);
			this.btn = context.getResources().getDrawable(R.drawable.btn);
			this.btn_ = context.getResources().getDrawable(R.drawable.btn_);
			this.chainled = context.getResources().getDrawable(R.drawable.chainled);
			this.phantom = context.getResources().getDrawable(R.drawable.phantom);
			this.phantom_ = context.getResources().getDrawable(R.drawable.phantom_);
			this.xml_prev = context.getResources().getDrawable(R.drawable.xml_prev);
			this.xml_play = context.getResources().getDrawable(R.drawable.xml_play);
			this.xml_pause = context.getResources().getDrawable(R.drawable.xml_pause);
			this.xml_next = context.getResources().getDrawable(R.drawable.xml_next);
			
			// Color
			this.checkbox = context.getResources().getColor(R.color.checkbox);
			this.trace_log = context.getResources().getColor(R.color.trace_log);
			this.option_window = context.getResources().getColor(R.color.option_window);
			this.option_window_checkbox = context.getResources().getColor(R.color.option_window_checkbox);
			this.option_window_btn = context.getResources().getColor(R.color.option_window_btn);
			this.option_window_btn_text = context.getResources().getColor(R.color.option_window_btn_text);
		}
		
		private Drawable getDrawable(String id) throws Exception {
			return res.getDrawable(res.getIdentifier(package_name + ":drawable/" + id, null, null));
		}
		
		private int getColor(String id) throws Exception {
			return res.getColor(res.getIdentifier(package_name + ":color/" + id, null, null));
		}
	}
}
