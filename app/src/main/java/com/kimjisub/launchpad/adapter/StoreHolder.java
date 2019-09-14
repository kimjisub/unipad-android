package com.kimjisub.launchpad.adapter;

import androidx.recyclerview.widget.RecyclerView;

import com.kimjisub.design.PackView;

public class StoreHolder extends RecyclerView.ViewHolder {
	PackView packView;
	int position = -1;

	public StoreHolder(PackView view) {
		super(view);
		packView = view;
	}
}
