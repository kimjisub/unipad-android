package com.kimjisub.launchpad.adapter;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.manager.ThemeResources;

import java.util.ArrayList;
import java.util.List;

public class ThemeItem {
	public Context context;
	public String package_name;
	public Drawable icon;
	public String name;
	public String author;
	public String description;
	public String version;
	public ThemeResources resources;

	public ThemeItem(Context context, String package_name) throws Exception {
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
		resources = new ThemeResources(context, package_name);
	}

	public void loadDefaultThemeResources() throws Exception {
		resources = new ThemeResources(context);
	}


	public final static ArrayList<ThemeItem> getThemePackList(Context context) {
		ArrayList<ThemeItem> ret = new ArrayList<>();

		try {
			ret.add(new ThemeItem(context, context.getPackageName()));
		} catch (Exception e) {
			e.printStackTrace();
		}

		List<ApplicationInfo> packages = context.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
		for (ApplicationInfo applicationInfo : packages) {
			String packageName = applicationInfo.packageName;
			if (packageName.startsWith("com.kimjisub.launchpad.theme.")) {
				try {
					ret.add(new ThemeItem(context, packageName));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return ret;
	}
}
