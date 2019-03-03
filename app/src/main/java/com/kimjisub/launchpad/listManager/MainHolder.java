package com.kimjisub.launchpad.listManager;

import android.support.v7.widget.RecyclerView;

import com.kimjisub.unipad.designkit.PackViewSimple;

public class MainHolder extends RecyclerView.ViewHolder {
	PackViewSimple packViewSimple;
	int position = -1;

	public MainHolder(PackViewSimple view) {
		super(view);
		packViewSimple = view;
	}
}
