package com.kimjisub.design.panel;

import android.content.Context;
import android.content.res.TypedArray;
import androidx.databinding.DataBindingUtil;

import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;

import com.kimjisub.design.R;
import com.kimjisub.design.databinding.PanelStoreTotalBinding;

public class StoreTotalPanel extends RelativeLayout {

	public PanelStoreTotalBinding b;

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
}
