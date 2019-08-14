package com.kimjisub.design.panel;

import android.content.Context;
import android.content.res.TypedArray;
import android.databinding.DataBindingUtil;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;

import com.kimjisub.design.R;
import com.kimjisub.design.databinding.PanelStoreTotalBinding;

public class StoreTotalPanel extends RelativeLayout {

	private PanelStoreTotalBinding b;

	public StoreTotalPanel(Context context) {
		super(context);
		init();
	}

	public StoreTotalPanel(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
		getAttrs(attrs);
	}

	public StoreTotalPanel(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
		getAttrs(attrs, defStyleAttr);
	}

	private void init() {
		b = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.panel_store_total, this, true);
	}

	private void getAttrs(AttributeSet attrs) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.MainTotalPanel);
		setTypeArray(typedArray);
	}

	private void getAttrs(AttributeSet attrs, int defStyle) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.MainTotalPanel, defStyle, 0);
		setTypeArray(typedArray);
	}

	private void setTypeArray(TypedArray typedArray) {

		typedArray.recycle();
	}

	////////////////////////////////////////////////////////////////////////////////////////////////

	public void setVersion(String version) {
		b.version.setText(version);
	}

	public void setCustomLogo(Drawable drawable) {
		b.customLogo.setImageDrawable(drawable);
	}

	public void setPremium(Boolean premium) {
		b.version.setTextColor(getResources().getColor(premium ? R.color.orange : R.color.text));
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
}
