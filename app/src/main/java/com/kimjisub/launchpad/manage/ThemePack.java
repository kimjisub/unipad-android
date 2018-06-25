package com.kimjisub.launchpad.manage;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.kimjisub.launchpad.R;

public class ThemePack {
	public Context context;
	public String package_name = "";
	public Drawable icon;
	public String name = "";
	public String author = "";
	public String description = "";
	public String version = "";
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
	
	public void loadDefaultThemeResources() throws Exception{
		resources = new Resources();
	}
	
	public class Resources {
		public Drawable playbg, custom_logo;
		public Drawable btn, btn_;
		public Drawable chainled, chain, chain_, chain__;
		public Drawable phantom, phantom_;
		public Drawable xml_prev, xml_play, xml_pause, xml_next;
		public int checkbox, trace_log, option_window, option_window_checkbox, option_window_btn, option_window_btn_text;
		public boolean isChainLED = true;
		
		public Resources(android.content.res.Resources res) throws Exception {
			// Drawable
			this.playbg = res.getDrawable(res.getIdentifier(package_name + ":drawable/playbg", null, null));
			try {
				this.custom_logo = res.getDrawable(res.getIdentifier(package_name + ":drawable/custom_logo", null, null));
			} catch (Exception ignore) {
			}
			this.btn = res.getDrawable(res.getIdentifier(package_name + ":drawable/btn", null, null));
			this.btn_ = res.getDrawable(res.getIdentifier(package_name + ":drawable/btn_", null, null));
			try {
				this.chainled = res.getDrawable(res.getIdentifier(package_name + ":drawable/chainled", null, null));
			} catch (Exception e) {
				isChainLED = false;
				this.chain = res.getDrawable(res.getIdentifier(package_name + ":drawable/chain", null, null));
				this.chain_ = res.getDrawable(res.getIdentifier(package_name + ":drawable/chain_", null, null));
				this.chain__ = res.getDrawable(res.getIdentifier(package_name + ":drawable/chain__", null, null));
			}
			this.phantom = res.getDrawable(res.getIdentifier(package_name + ":drawable/phantom", null, null));
			try {
				this.phantom_ = res.getDrawable(res.getIdentifier(package_name + ":drawable/phantom_", null, null));
			} catch (Exception ignore) {
			}
			this.xml_prev = res.getDrawable(res.getIdentifier(package_name + ":drawable/xml_prev", null, null));
			this.xml_play = res.getDrawable(res.getIdentifier(package_name + ":drawable/xml_play", null, null));
			this.xml_pause = res.getDrawable(res.getIdentifier(package_name + ":drawable/xml_pause", null, null));
			this.xml_next = res.getDrawable(res.getIdentifier(package_name + ":drawable/xml_next", null, null));
			
			// Color
			try {
				this.checkbox = res.getColor(res.getIdentifier(package_name + ":color/checkbox", null, null));
			} catch (Exception ignore) {
				this.checkbox = context.getResources().getColor(R.color.checkbox);
			}
			try {
				this.trace_log = res.getColor(res.getIdentifier(package_name + ":color/trace_log", null, null));
			} catch (Exception ignore) {
				this.trace_log = context.getResources().getColor(R.color.trace_log);
			}
			try {
				this.option_window = res.getColor(res.getIdentifier(package_name + ":color/option_window", null, null));
			} catch (Exception ignore) {
				this.option_window = context.getResources().getColor(R.color.option_window);
			}
			try {
				this.option_window_checkbox = res.getColor(res.getIdentifier(package_name + ":color/option_window_checkbox", null, null));
			} catch (Exception ignore) {
				this.option_window_checkbox = context.getResources().getColor(R.color.option_window_checkbox);
			}
			try {
				this.option_window_btn = res.getColor(res.getIdentifier(package_name + ":color/option_window_btn", null, null));
			} catch (Exception ignore) {
				this.option_window_btn = context.getResources().getColor(R.color.option_window_btn);
			}
			try {
				this.option_window_btn_text = res.getColor(res.getIdentifier(package_name + ":color/option_window_btn_text", null, null));
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
	}
}
