package com.kimjisub.design;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class Pad extends RelativeLayout {

	Context context;

	ImageView IV_background;
	ImageView IV_LED;
	ImageView IV_phantom;
	TextView TV_traceLog;
	View V_touchView;

	public Pad(Context context) {
		super(context);
		initView(context);
	}


	public Pad(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
	}

	public Pad(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		initView(context);
	}

	private void initView(Context context) {
		this.context = context;

		String infService = Context.LAYOUT_INFLATER_SERVICE;
		LayoutInflater li = (LayoutInflater) getContext().getSystemService(infService);
		View v = li.inflate(R.layout.pad, this, false);
		addView(v);


		// set view
		IV_background = findViewById(R.id.background);
		IV_LED = findViewById(R.id.LED);
		IV_phantom = findViewById(R.id.phantom);
		TV_traceLog = findViewById(R.id.traceLog);
		V_touchView = findViewById(R.id.touchView);
	}

	public void setOnClickListener(OnClickListener listener) {
		V_touchView.setOnClickListener(listener);
	}

	public void setOnTouchListener(OnTouchListener listener) {
		V_touchView.setOnTouchListener(listener);
	}
	//========================================================================================= Background

	public Pad setBackgroundImageDrawable(Drawable drawable) {
		IV_background.setImageDrawable(drawable);
		return this;
	}

	//========================================================================================= LED


	public Pad setLedBackground(Drawable drawable) {
		IV_LED.setBackground(drawable);
		return this;
	}

	public Pad setLedBackgroundColor(int color) {
		IV_LED.setBackgroundColor(color);
		return this;
	}

	//========================================================================================= Phantom

	public Pad setPhantomImageDrawable(Drawable drawable) {
		IV_phantom.setImageDrawable(drawable);
		return this;
	}

	public Pad setPhantomRotation(float rotation) {
		IV_phantom.setRotation(rotation);
		return this;
	}

	//========================================================================================= TraceLog

	public Pad setTraceLogText(String string) {
		TV_traceLog.setText(string);
		return this;
	}

	public Pad appendTraceLog(String string) {
		TV_traceLog.append(string);
		return this;
	}

	public Pad setTraceLogTextColor(int color) {
		TV_traceLog.setTextColor(color);
		return this;
	}
}
