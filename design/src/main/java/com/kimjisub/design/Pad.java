package com.kimjisub.design;

import android.content.Context;
import android.content.res.TypedArray;
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
	
	
	public Pad(Context context) {
		super(context);
		initView(context);
	}
	
	public Pad(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
		getAttrs(attrs);
	}
	
	public Pad(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		initView(context);
		getAttrs(attrs, defStyle);
	}
	
	private void getAttrs(AttributeSet attrs) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PackView);
		setTypeArray(typedArray);
	}
	
	private void getAttrs(AttributeSet attrs, int defStyle) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PackView, defStyle, 0);
		setTypeArray(typedArray);
	}
	
	private void setTypeArray(TypedArray typedArray) {
		
		int color = typedArray.getResourceId(R.styleable.PackView_flagColor, R.drawable.border_play_blue);
		//setFlagColor(color);
		
		String title = typedArray.getString(R.styleable.PackView_title);
		//setTitle(title);
		
		String subTitle = typedArray.getString(R.styleable.PackView_subTitle);
		//setSubTitle(subTitle);
		
		Boolean LED = typedArray.getBoolean(R.styleable.PackView_LED, false);
		//setLED(LED);
		
		Boolean autoPlay = typedArray.getBoolean(R.styleable.PackView_AutoPlay, false);
		//setAutoPlay(autoPlay);
		
		String size = typedArray.getString(R.styleable.PackView_size);
		//setSize(size);
		
		String chain = typedArray.getString(R.styleable.PackView_chain);
		//setChain(chain);
		
		int capacity = typedArray.getInteger(R.styleable.PackView_capacity, 0);
		//setCapacity(capacity);
		
		Boolean optionVisibility = typedArray.getBoolean(R.styleable.PackView_optionVisibility, true);
		//setOptionVisibility(optionVisibility);
		
		typedArray.recycle();
	}
	
	public void setOnClickListener(OnClickListener listener){
		V_touchView.setOnClickListener(listener);
	}
	public void setOnTouchListener(OnTouchListener listener){
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
