package com.kimjisub.launchpad.adapter;

import android.support.v7.widget.RecyclerView;

import com.kimjisub.design.PackViewSimple;

public class StoreHolder extends RecyclerView.ViewHolder {
	PackViewSimple packViewSimple;
	int position = -1;

	public StoreHolder(PackViewSimple view) {
		super(view);
		packViewSimple = view;
	}
}
