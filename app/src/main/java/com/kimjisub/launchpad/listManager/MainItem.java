package com.kimjisub.launchpad.listManager;

import com.kimjisub.launchpad.utils.Unipack;
import com.kimjisub.unipad.designkit.PackViewSimple;

public class MainItem {
	public Unipack unipack;
	public String path;
	public int flagColor;
	public PackViewSimple packViewSimple;
	public boolean toggle = false;
	public boolean moving = false;

	public MainItem(Unipack unipack, String path, int flagColor) {
		this.unipack = unipack;
		this.path = path;
		this.flagColor = flagColor;
	}
}
