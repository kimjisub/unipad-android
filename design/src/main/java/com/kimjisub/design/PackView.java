package com.kimjisub.design;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kimjisub.design.manage.UIManager;

public class PackView extends RelativeLayout {
	
	Context context;
	
	RelativeLayout RL_root;
	RelativeLayout RL_info;
	TextView TV_delete;
	TextView TV_edit;
	LinearLayout LL_leftView;
	RelativeLayout RL_playBtn;
	
	LinearLayout LL_btns;
	LinearLayout LL_infos;
	
	RelativeLayout RL_flag1;
	RelativeLayout RL_flag2;
	TextView TV_title;
	TextView TV_subTitle;
	TextView TV_option1;
	TextView TV_option2;
	
	int flag_default;
	int flag_enable;
	int info_default;
	int info_enable;
	int info_extend;
	
	private void initView(Context context) {
		this.context = context;
		
		String infService = Context.LAYOUT_INFLATER_SERVICE;
		LayoutInflater li = (LayoutInflater) getContext().getSystemService(infService);
		View v = li.inflate(R.layout.packview, this, false);
		addView(v);
		
		
		// set view
		RL_root = findViewById(R.id.root);
		RL_info = findViewById(R.id.info);
		TV_delete = findViewById(R.id.delete);
		TV_edit = findViewById(R.id.edit);
		LL_leftView = findViewById(R.id.leftView);
		RL_playBtn = findViewById(R.id.playBtn);
		
		LL_btns = findViewById(R.id.btns);
		LL_infos = findViewById(R.id.infos);
		
		RL_flag1 = findViewById(R.id.flag1);
		RL_flag2 = findViewById(R.id.flag2);
		TV_title = findViewById(R.id.title);
		TV_subTitle = findViewById(R.id.subTitle);
		TV_option1 = findViewById(R.id.option1);
		TV_option2 = findViewById(R.id.option2);
		
		
		// set preset
		LL_leftView.setX(UIManager.dpToPx(context, 10));
		
		
		// set listener
		RL_root.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onViewClick();
			}
		});
		RL_root.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				onViewLongClick();
				return true;
			}
		});
		
		
		// set vars
		flag_default = UIManager.dpToPx(context, 10);
		flag_enable = UIManager.dpToPx(context, 100);
		info_default = UIManager.dpToPx(context, 40);
		info_enable = UIManager.dpToPx(context, 35);
		info_extend = UIManager.dpToPx(context, 100);
	}
	
	public PackView(Context context) {
		super(context);
		initView(context);
	}
	
	public PackView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
		getAttrs(attrs);
	}
	
	public PackView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		initView(context);
		getAttrs(attrs, defStyle);
	}
	
	public static PackView errItem(Context context, String title, String subTitle, OnEventListener listener) {
		return new PackView(context)
			.setFlagColor(context.getResources().getColor(R.color.red))
			.setTitle(title)
			.setSubTitle(subTitle)
			//.setOptionVisibility(false)
			.setOnEventListener(listener);
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
	
	//========================================================================================= set / update / etc..
	
	public PackView setFlagColor(int color) {
		GradientDrawable flag1Background = (GradientDrawable) getResources().getDrawable(R.drawable.border_all_round);
		flag1Background.setColor(color);
		RL_flag1.setBackground(flag1Background);
		
		return this;
	}
	
	public PackView updateFlagColor(int color) {
		GradientDrawable flag1Background = (GradientDrawable) getResources().getDrawable(R.drawable.border_all_round);
		flag1Background.setColor(color);
		GradientDrawable flag2Background = (GradientDrawable) RL_flag1.getBackground();
		
		RL_flag1.setBackground(flag1Background);
		RL_flag2.setBackground(flag2Background);
		
		RL_flag2.setAlpha(1);
		RL_flag2.animate().alpha(0).setDuration(500).start();
		
		return this;
	}
	
	public PackView setTitle(String str) {
		TV_title.setText(str);
		return this;
	}
	
	public PackView setSubTitle(String str) {
		TV_subTitle.setText(str);
		return this;
	}
	
	public PackView setInfos(String[] titles, String[] contents) {
		
		LL_infos.removeAllViews();
		
		for (int i = 0; i < titles.length; i++) {
			LinearLayout LL_infoitem = (LinearLayout) View.inflate(context, R.layout.res_info, null);
			((TextView) LL_infoitem.findViewById(R.id.title)).setText(titles[i]);
			((TextView) LL_infoitem.findViewById(R.id.content)).setText(contents[i]);
			
			ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
			
			LL_infos.addView(LL_infoitem, lp);
		}
		
		return this;
	}
	
	public PackView updateInfo(int index, String contents) {
		
		LinearLayout linearLayout = (LinearLayout) LL_infos.getChildAt(index);
		TextView textView = linearLayout.findViewById(R.id.content);
		
		textView.setText(contents);
		textView.setAlpha(0);
		textView.animate().alpha(1).setDuration(500).start();
		
		return this;
	}
	
	public PackView setBtns(String[] titles, int[] colors) {
		
		LL_btns.removeAllViews();
		
		for (int i = 0; i < titles.length; i++) {
			final int I = i;
			
			String title = titles[i];
			int color = colors[i];
			
			LinearLayout LL_infoitem = (LinearLayout) View.inflate(context, R.layout.res_btn, null);
			((TextView) LL_infoitem.findViewById(R.id.btn)).setText(title);
			((GradientDrawable) LL_infoitem.findViewById(R.id.btn).getBackground()).setColor(color);
			
			LL_infoitem.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					onFunctionBtnClick(I);
				}
			});
			
			LL_btns.addView(LL_infoitem, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
		}
		
		return this;
	}
	
	public PackView setOptions(String msg1, String msg2) {
		TV_option1.setText(msg1);
		TV_option2.setText(msg2);
		
		return this;
	}
	
	public PackView setOptionColors(int color1, int color2) {
		TV_option1.setTextColor(color1);
		TV_option2.setTextColor(color2);
		
		return this;
	}
	
	public PackView setOptionBools(boolean bool1, boolean bool2) {
		int green = getResources().getColor(R.color.green);
		int pink = getResources().getColor(R.color.pink);
		
		setOptionColors(bool1 ? green : pink, bool2 ? green : pink);
		
		return this;
	}
	
	//========================================================================================= Play
	
	private boolean isPlay = false;
	
	public void togglePlay(boolean bool) {
		if (isPlay != bool) {
			if (bool) {
				//animation
				LL_leftView.animate().x(flag_enable).setDuration(500).start();
				
				//clickEvent
				RL_playBtn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onPlayClick();
					}
				});
			} else {
				//animation
				LL_leftView.animate().x(flag_default).setDuration(500).start();
				
				//clickEvent
				RL_playBtn.setOnClickListener(null);
				RL_playBtn.setClickable(false);
			}
			isPlay = bool;
		}
	}
	
	public void togglePlay() {
		togglePlay(!isPlay);
	}
	
	public void togglePlay(boolean bool, int onColor, int offColor) {
		togglePlay(bool);
		if (isPlay)
			updateFlagColor(onColor);
		else
			updateFlagColor(offColor);
		
	}
	
	public void togglePlay(int onColor, int offColor) {
		togglePlay(!isPlay);
		if (isPlay)
			updateFlagColor(onColor);
		else
			updateFlagColor(offColor);
		
	}
	
	public boolean isPlay() {
		return isPlay;
	}
	
	//========================================================================================= Info
	
	private boolean isDetail = false;
	
	public void toggleDetail(boolean bool) {
		if (isDetail != bool) {
			final int px = info_default;
			final int px2 = info_enable;
			if (bool) {
				//animation
				Animation a = new Animation() {
					@Override
					protected void applyTransformation(float interpolatedTime, Transformation t) {
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) RL_info.getLayoutParams();
						params.height = px + (int) (px2 * interpolatedTime);
						RL_info.setLayoutParams(params);
					}
				};
				a.setDuration(500);
				RL_info.startAnimation(a);
				
				//clickEvent
				/*
				TV_delete.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onDeleteClick();
					}
				});
				TV_edit.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onEditClick();
					}
				});*/
			} else {
				//animation
				Animation a = new Animation() {
					@Override
					protected void applyTransformation(float interpolatedTime, Transformation t) {
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) RL_info.getLayoutParams();
						params.height = px + px2 + (int) (-px2 * interpolatedTime);
						RL_info.setLayoutParams(params);
					}
				};
				a.setDuration(500);
				RL_info.startAnimation(a);
				
				//clickEvent
				//TV_delete.setOnClickListener(null);
				//TV_delete.setClickable(false);
				//TV_edit.setOnClickListener(null);
				//TV_edit.setClickable(false);
			}
			
			isDetail = bool;
		}
	}
	
	public void toggleDetail() {
		toggleDetail(!isDetail);
	}
	
	public boolean isDetail() {
		return isDetail;
	}
	
	
	//========================================================================================= Listener
	
	
	private OnEventListener onEventListener = null;
	
	public interface OnEventListener {
		
		void onViewClick(PackView v);
		
		void onViewLongClick(PackView v);
		
		void onPlayClick(PackView v);
		
		void onFunctionBtnClick(PackView v, int index);
	}
	
	public PackView setOnEventListener(OnEventListener listener) {
		this.onEventListener = listener;
		return this;
	}
	
	void onViewClick() {
		if (onEventListener != null) onEventListener.onViewClick(this);
	}
	
	void onViewLongClick() {
		if (onEventListener != null) onEventListener.onViewLongClick(this);
	}
	
	void onPlayClick() {
		if (onEventListener != null && isPlay) onEventListener.onPlayClick(this);
	}
	
	void onFunctionBtnClick(int index) {
		if (onEventListener != null && isDetail) onEventListener.onFunctionBtnClick(this, index);
	}
	
	
}