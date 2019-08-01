package com.kimjisub.design;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;

import com.kimjisub.design.databinding.PanelMainPackBinding;

import java.io.File;

public class MainPackPanel extends RelativeLayout {

	private PanelMainPackBinding b;

	public MainPackPanel(Context context) {
		super(context);
		init();
	}

	public MainPackPanel(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
		getAttrs(attrs);
	}

	public MainPackPanel(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
		getAttrs(attrs, defStyleAttr);
	}

	private void init() {
		b = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.panel_main_pack, this, true);

		b.title.setSelected(true);
		b.subTitle.setSelected(true);
		b.path.setSelected(true);


		b.star.setOnClickListener(v -> {
			if(onEventListener != null)
				onEventListener.onStarClick(v);
		});
		b.bookmark.setOnClickListener(v -> {
			if(onEventListener != null)
				onEventListener.onBookmarkClick(v);
		});
		b.edit.setOnClickListener(v -> {
			if(onEventListener != null)
				onEventListener.onEditClick(v);
		});
		b.storage.setOnClickListener(v -> {
			if(onEventListener != null)
				onEventListener.onStorageClick(v);
		});
		b.youtube.setOnClickListener(v -> {
			if(onEventListener != null)
				onEventListener.onYoutubeClick(v);
		});
		b.website.setOnClickListener(v -> {
			if(onEventListener != null)
				onEventListener.onWebsiteClick(v);
		});
		b.func.setOnClickListener(v -> {
			if(onEventListener != null)
				onEventListener.onFuncClick(v);
		});
		b.delete.setOnClickListener(v -> {
			if(onEventListener != null)
				onEventListener.onDeleteClick(v);
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

	public void setPath(String path) {
		b.path.setText(path);
	}

	public void setStar(boolean star) {
		b.star.
				b.subTitle.setText(subTitle);
	}

	public void setUnipackCount(String unipackCount) {
		b.unipackCount.setText(unipackCount);
	}

	public void setOpenCount(String openCount) {
		b.openCount.setText(openCount);
	}

	public void setPadTouchCount(String padTouchCount) {
		b.padTouchCount.setText(padTouchCount);
	}

	public void setThemeName(String padTouchCount) {
		b.padTouchCount.setText(padTouchCount);
	}

	public void setUnipackCapacity(String padTouchCount) {
		b.padTouchCount.setText(padTouchCount);
	}


	////////////////////////////////////////////////////////////////////////////////////////////////

	OnEventListener onEventListener;

	public interface OnEventListener {
		void onStarClick(View v);

		void onBookmarkClick(View v);

		void onEditClick(View v);

		void onStorageClick(View v);

		void onYoutubeClick(View v);

		void onWebsiteClick(View v);

		void onFuncClick(View v);

		void onDeleteClick(View v);
	}

	public void setOnEventListener(OnEventListener onEventListener) {
		this.onEventListener = onEventListener;
	}
}
