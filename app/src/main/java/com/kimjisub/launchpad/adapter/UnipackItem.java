package com.kimjisub.launchpad.adapter;

import com.kimjisub.design.PackViewSimple;
import com.kimjisub.launchpad.manager.Unipack;

public class UnipackItem {
	public Unipack unipack;
	public String path;
	public boolean bookmark;
	public boolean isNew;


	public PackViewSimple packViewSimple;
	public int flagColor;
	public boolean isToggle = false;
	public boolean isMoving = false;

	public UnipackItem(Unipack unipack, String path, boolean bookmark, boolean isNew) {
		this.unipack = unipack;
		this.path = path;
		this.bookmark = bookmark;
		this.isNew = isNew;
	}
}
