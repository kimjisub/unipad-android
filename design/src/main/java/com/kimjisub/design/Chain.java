package com.kimjisub.design;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

public class Chain extends RelativeLayout {
	
	Context context;
	
	ImageView IV_background;
	ImageView IV_LED;
	ImageView IV_phantom;
	View V_touchView;
	
	private void initView(Context context) {
		this.context = context;
		
		String infService = Context.LAYOUT_INFLATER_SERVICE;
		LayoutInflater li = (LayoutInflater) getContext().getSystemService(infService);
		View v = li.inflate(R.layout.chain, this, false);
		addView(v);
		
		
		// set view
		IV_background = findViewById(R.id.background);
		IV_LED = findViewById(R.id.LED);
		IV_phantom = findViewById(R.id.phantom);
		V_touchView = findViewById(R.id.touchView);
	}
	
	
	public Chain(Context context) {
		super(context);
		initView(context);
	}
	
	public Chain(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
	}
	
	public Chain(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		initView(context);
	}
	
	public void setOnClickListener(OnClickListener listener) {
		V_touchView.setOnClickListener(listener);
	}
	
	public void setOnTouchListener(OnTouchListener listener) {
		V_touchView.setOnTouchListener(listener);
	}
	//========================================================================================= Background
	
	public Chain setBackgroundImageDrawable(Drawable drawable) {
		IV_background.setImageDrawable(drawable);
		return this;
	}
	
	//========================================================================================= LED
	
	
	public Chain setLedBackground(Drawable drawable) {
		IV_LED.setBackground(drawable);
		return this;
	}
	
	public Chain setLedBackgroundColor(int color) {
		IV_LED.setBackgroundColor(color);
		return this;
	}
	
	public Chain setLedVisibility(int visibility) {
		IV_LED.setVisibility(visibility);
		return this;
	}
	
	//========================================================================================= Phantom
	
	public Chain setPhantomImageDrawable(Drawable drawable) {
		IV_phantom.setImageDrawable(drawable);
		return this;
	}
	
	public Chain setPhantomRotation(float rotation) {
		IV_phantom.setRotation(rotation);
		return this;
	}
}
