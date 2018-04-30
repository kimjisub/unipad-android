package com.kimjisub.launchpad.manage;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class ThemePack {
	public Context context;
	public String package_name = "";
	public Drawable icon;
	public String name = "";
	public String author = "";
	public String description = "";
	public String version = "";
	public Resources resources;

	public ThemePack(Context context, String package_name) {
		this.context = context;
		this.package_name = package_name;
	}

	public void init() throws Exception {
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

	public class Resources {
		public Drawable playbg, btn, btn_, chainled, chain, chain_, chain__, phantom, phantom_, xml_prev, xml_play, xml_pause, xml_next;
		public int setting_btn, trace_log;
		public boolean isChainLED = true;

		public Resources(android.content.res.Resources res) throws Exception {
			this.playbg = res.getDrawable(res.getIdentifier(package_name + ":drawable/playbg", null, null));
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
			try {
				this.setting_btn = res.getColor(res.getIdentifier(package_name + ":color/setting_btn", null, null));
			} catch (Exception ignore) {
				this.setting_btn = res.getColor(res.getIdentifier(package_name + ":color/text1", null, null));
			}
			try {
				this.trace_log = res.getColor(res.getIdentifier(package_name + ":color/trace_log", null, null));
			} catch (Exception ignore) {
				this.trace_log = res.getColor(res.getIdentifier(package_name + ":color/text1", null, null));
			}
		}
	}
}
