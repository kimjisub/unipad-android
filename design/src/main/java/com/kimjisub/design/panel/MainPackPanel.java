package com.kimjisub.design.panel;

import android.content.Context;
import android.content.res.TypedArray;
import android.databinding.DataBindingUtil;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;

import com.kimjisub.design.R;
import com.kimjisub.design.databinding.PanelMainPackBinding;

public class MainPackPanel extends RelativeLayout {

	public PanelMainPackBinding b;

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
			if (onEventListener != null)
				onEventListener.onStarClick(v);
		});
		b.bookmark.setOnClickListener(v -> {
			if (onEventListener != null)
				onEventListener.onBookmarkClick(v);
		});
		b.edit.setOnClickListener(v -> {
			if (onEventListener != null)
				onEventListener.onEditClick(v);
		});
		b.storage.setOnClickListener(v -> {
			if (onEventListener != null)
				onEventListener.onStorageClick(v);
		});
		b.youtube.setOnClickListener(v -> {
			if (onEventListener != null)
				onEventListener.onYoutubeClick(v);
		});
		b.website.setOnClickListener(v -> {
			if (onEventListener != null)
				onEventListener.onWebsiteClick(v);
		});
		b.func.setOnClickListener(v -> {
			if (onEventListener != null)
				onEventListener.onFuncClick(v);
		});
		b.delete.setOnClickListener(v -> {
			if (onEventListener != null)
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

	public void setStar(boolean star) {
		b.star.setImageResource(star ? R.drawable.ic_star_24dp : R.drawable.ic_star_border_24dp);
	}

	public void setBookmark(boolean bookmark) {
		b.star.setImageResource(bookmark ? R.drawable.ic_bookmark_24dp : R.drawable.ic_bookmark_border_24dp);
	}

	public void setStorage(boolean external) {
		b.star.setImageResource(external ? R.drawable.ic_public_24dp : R.drawable.ic_lock_24dp);
		b.star.setClickable(true);
	}

	public void setStorageMoving() {
		b.star.setImageResource(R.drawable.ic_copy_24dp);
		b.star.setClickable(false);
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
