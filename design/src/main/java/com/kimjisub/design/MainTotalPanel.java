package com.kimjisub.design;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;

import com.kimjisub.design.databinding.PanelMainTotalBinding;

public class MainTotalPanel extends RelativeLayout {

	private PanelMainTotalBinding b;

	public MainTotalPanel(Context context) {
		super(context);
	}

	public MainTotalPanel(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public MainTotalPanel(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
		getAttrs(attrs);
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	public MainTotalPanel(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
		getAttrs(attrs, defStyleAttr);
	}

	private void init() {
		b = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.panel_main_total, null, true);
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

	///////////////////////////////////////////////////////////////////////////

	public void setVersion(String version) {
		b.version.setText(version);
	}

	public void setPremium(Boolean premium) {
		b.version.setTextColor(getResources().getColor(premium ? R.color.orange : R.color.text));
	}

	@SuppressLint("SetTextI18n")
	public void setUnipackCount(String unipackCount) {
		b.unipackCount.setText(unipackCount);
	}

	public void setOpenCount(String openCount){
		b.openCount.setText(openCount);
	}

	public void setPadTouchCount(String padTouchCount){
		b.padTouchCount.setText(padTouchCount);
	}

	public void setThemeName(String padTouchCount){
		b.padTouchCount.setText(padTouchCount);
	}

	public void setUnipackCapacity(String padTouchCount){
		b.padTouchCount.setText(padTouchCount);
	}
}