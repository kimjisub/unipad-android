package com.kimjisub.launchpad.adapter;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.activity.ThemeActivity;

public class ThemeAdapter extends RecyclerView.Adapter<ThemeHolder> {

	ThemeActivity context;

	public ThemeAdapter(ThemeActivity context) {
		this.context = context;
	}

	@Override
	public ThemeHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
		return new ThemeHolder(View.inflate(parent.getContext(), R.layout.theme_item, null));
	}

	@Override
	public void onBindViewHolder(ThemeHolder holder, int position) {
		try {
			ThemeItem item = context.L_theme.get(position);
			holder.theme_icon.setBackground(item.icon);
			holder.theme_version.setText(item.version);
			holder.theme_author.setText(item.author);
		} catch (Exception ignore) {
			holder.theme_icon.setBackground(context.drawable(R.drawable.theme_add));
			holder.theme_version.setText(context.lang(R.string.themeDownload));
		}
	}

	@Override
	public int getItemCount() {
		return context.L_theme.size() + 1;
	}
}