package com.kimjisub.launchpad.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.kimjisub.launchpad.R;

public class ThemeHolder extends RecyclerView.ViewHolder {
	ImageView theme_icon;
	TextView theme_version;
	TextView theme_author;

	public ThemeHolder(View itemView) {
		super(itemView);
		theme_icon = itemView.findViewById(R.id.theme_icon);
		theme_version = itemView.findViewById(R.id.theme_version);
		theme_author = itemView.findViewById(R.id.theme_author);
	}
}
