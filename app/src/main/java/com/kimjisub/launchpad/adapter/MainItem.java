package com.kimjisub.launchpad.adapter;

import com.kimjisub.launchpad.manager.Unipack;
import com.kimjisub.design.PackViewSimple;

public class MainItem {
	public Unipack unipack;
	public String path;
	public int flagColor;
	public PackViewSimple packViewSimple;
	public boolean isToggle = false;
	public boolean isMoving = false;
	public boolean isNew = false;

	public MainItem(Unipack unipack, String path, boolean isNew) {
		this.unipack = unipack;
		this.path = path;
		this.isNew = isNew;
	}
}
