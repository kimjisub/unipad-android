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

public class StorePackPanel extends RelativeLayout {

	PanelStorePackBinding b;

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
		b.subTitle.setSelected(true);
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

	public void setSubTitle(String subTitle) {
		b.subTitle.setText(subTitle);
	}

	public void setDownloadCount(String downloadCount) {
		b.downloadCount.setText(downloadCount);
	}

	public void updateTitle(String title) {
		if (!b.title.getText().equals(title)) {
			b.title.setAlpha(0);
			setTitle(title);
			b.title.animate().alpha(1).setDuration(500).start();
		}
	}

	public void updateSubTitle(String subTitle) {
		if (!b.subTitle.getText().equals(subTitle)) {
			b.subTitle.setAlpha(0);
			setSubTitle(subTitle);
			b.subTitle.animate().alphaBy(0).alpha(1).setDuration(500).start();
		}
	}

	public void updateDownloadCount(String downloadCount) {
		if (!b.downloadCount.getText().equals(downloadCount)) {
			b.downloadCount.setAlpha(0);
			setDownloadCount(downloadCount);
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
