package com.kimjisub.launchpad.adapter;

import com.kimjisub.design.PackViewSimple;
import com.kimjisub.launchpad.manager.Unipack;
import com.kimjisub.launchpad.network.fb.fbStore;

public class StoreItem {
	public com.kimjisub.launchpad.network.fb.fbStore fbStore;
	public boolean isDownloaded;
	public boolean isDownloading;

	public PackViewSimple packViewSimple;
	public boolean isToggle = false;

	public StoreItem(fbStore fbStore, boolean isDownloaded) {
		this.fbStore = fbStore;
		this.isDownloaded = isDownloaded;
	}
}
