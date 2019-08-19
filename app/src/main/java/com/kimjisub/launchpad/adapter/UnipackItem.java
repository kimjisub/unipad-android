package com.kimjisub.launchpad.adapter;

import com.kimjisub.design.PackViewSimple;
import com.kimjisub.launchpad.manager.Unipack;

public class UnipackItem {
	public Unipack unipack;
	public String path;
	public int flagColor;
	public PackViewSimple packViewSimple;
	public boolean bookmark= false;
	public boolean isToggle = false;
	public boolean isMoving = false;
	public boolean isNew = false;

	public UnipackItem(Unipack unipack, String path, boolean bookmark, boolean isNew) {
		this.unipack = unipack;
		this.path = path;
		this.bookmark = bookmark;
		this.isNew = isNew;
	}
}
