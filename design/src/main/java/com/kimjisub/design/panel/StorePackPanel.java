package com.kimjisub.design.panel;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.databinding.DataBindingUtil;

import com.kimjisub.design.R;
import com.kimjisub.design.databinding.PanelStorePackBinding;

import java.text.DecimalFormat;

public class StorePackPanel extends RelativeLayout {

	PanelStorePackBinding b;

	DecimalFormat numberFormatter = new DecimalFormat("###,###");

	public StorePackPanel(Context context) {
		super(context);
		init();
	}

	public StorePackPanel(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
		getAttrs(attrs);
	}

	public StorePackPanel(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
		getAttrs(attrs, defStyleAttr);
	}

	private void init() {
		b = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.panel_store_pack, this, true);

		b.title.setSelected(true);
		b.subtitle.setSelected(true);
		b.path.setSelected(true);

		b.youtube.setOnClickListener(v -> {
			if (onEventListener != null)
				onEventListener.onYoutubeClick(v);
		});
		b.website.setOnClickListener(v -> {
			if (onEventListener != null)
				onEventListener.onWebsiteClick(v);
		});
	}

	private void getAttrs(AttributeSet attrs) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.MainPackPanel);
		setTypeArray(typedArray);
	}

	private void getAttrs(AttributeSet attrs, int defStyle) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.MainPackPanel, defStyle, 0);
		setTypeArray(typedArray);
	}

	private void setTypeArray(TypedArray typedArray) {

		typedArray.recycle();
	}

	////////////////////////////////////////////////////////////////////////////////////////////////

	public void setTitle(String title) {
		b.title.setText(title);
	}

	public void setSubtitle(String subtitle) {
		b.subtitle.setText(subtitle);
	}

	public void setDownloadCount(String downloadCount) {
		b.downloadCount.setText(downloadCount);
	}

	public void setDownloadCount(long downloadCount) {
		String downloadCountFormatted = numberFormatter.format(downloadCount);

		setDownloadCount(downloadCountFormatted);
	}

	public void updateTitle(String title) {
		if (!b.title.getText().equals(title)) {
			b.title.setAlpha(0);
			setTitle(title);
			b.title.animate().alpha(1).setDuration(500).start();
		}
	}

	public void updateSubtitle(String subtitle) {
		if (!b.subtitle.getText().equals(subtitle)) {
			b.subtitle.setAlpha(0);
			setSubtitle(subtitle);
			b.subtitle.animate().alphaBy(0).alpha(1).setDuration(500).start();
		}
	}

	public void updateDownloadCount(long downloadCount) {
		String downloadCountFormatted = numberFormatter.format(downloadCount);

		if (!b.downloadCount.getText().equals(downloadCountFormatted)) {
			b.downloadCount.setAlpha(0);
			setDownloadCount(downloadCountFormatted);
			b.downloadCount.animate().alphaBy(0).alpha(1).setDuration(500).start();
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////

	OnEventListener onEventListener;

	public interface OnEventListener {

		void onYoutubeClick(View v);

		void onWebsiteClick(View v);
	}

	public void setOnEventListener(OnEventListener onEventListener) {
		this.onEventListener = onEventListener;
	}
}
