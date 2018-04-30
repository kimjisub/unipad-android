package com.kimjisub.design;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kimjisub.design.manage.UIManager;

public class PackView extends RelativeLayout {
	
	Context context;
	
	RelativeLayout RL_root;
	RelativeLayout RL_detail;
	LinearLayout LL_leftView;
	RelativeLayout RL_playBtn;
	ImageView IV_playImg;
	TextView TV_playText;
	LinearLayout LL_btns;
	LinearLayout LL_extendBtns;
	LinearLayout LL_infos;
	LinearLayout LL_extendView;
	
	RelativeLayout RL_flag1;
	RelativeLayout RL_flag2;
	TextView TV_title;
	TextView TV_subTitle;
	TextView TV_option1;
	TextView TV_option2;
	
	boolean status = false;
	
	int PX_flag_default;
	int PX_flag_enable;
	int PX_info_default;
	int PX_info_enable;
	int PX_info_extend;
	
	private void initView(Context context) {
		this.context = context;
		
		String infService = Context.LAYOUT_INFLATER_SERVICE;
		LayoutInflater li = (LayoutInflater) getContext().getSystemService(infService);
		View v = li.inflate(R.layout.packview, this, false);
		addView(v);
		
		
		// set view
		RL_root = findViewById(R.id.root);
		RL_detail = findViewById(R.id.detail);
		LL_leftView = findViewById(R.id.leftView);
		RL_playBtn = findViewById(R.id.playBtn);
		IV_playImg = findViewById(R.id.playImg);
		TV_playText = findViewById(R.id.playText);
		LL_btns = findViewById(R.id.btns);
		LL_extendBtns = findViewById(R.id.extendBtns);
		LL_infos = findViewById(R.id.infos);
		LL_extendView = findViewById(R.id.extendView);
		
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
		PX_flag_default = UIManager.dpToPx(context, 10);
		PX_flag_enable = UIManager.dpToPx(context, 100);
		PX_info_default = UIManager.dpToPx(context, 40);
		PX_info_enable = UIManager.dpToPx(context, 75);
		PX_info_extend = UIManager.dpToPx(context, 300);
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
	
	public PackView setStatus(boolean bool) {
		status = bool;
		
		return this;
	}
	
	public boolean getStatus() {
		return status;
	}
	
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
			LinearLayout LL_info = (LinearLayout) View.inflate(context, R.layout.res_info, null);
			((TextView) LL_info.findViewById(R.id.title)).setText(titles[i]);
			((TextView) LL_info.findViewById(R.id.content)).setText(contents[i]);
			
			ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
			
			LL_infos.addView(LL_info, lp);
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
			
			LinearLayout LL_btn = (LinearLayout) View.inflate(context, R.layout.res_btn, null);
			((TextView) LL_btn.findViewById(R.id.btn)).setText(title);
			((GradientDrawable) LL_btn.findViewById(R.id.btn).getBackground()).setColor(color);
			
			LL_btn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					onFunctionBtnClick(I);
				}
			});
			
			LL_btns.addView(LL_btn, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
	
	public PackView setPlayImageShow(boolean bool) {
		IV_playImg.setVisibility(bool ? VISIBLE : GONE);
		
		return this;
	}
	
	public PackView setPlayText(String str) {
		TV_playText.setText(str);
		
		return this;
	}
	
	public PackView setExtendView(View v, int height, String[] btnTitles, int[] btnColors, final OnExtendEventListener listener) {
		LL_extendView.removeAllViews();
		LL_extendView.addView(v);
		
		PX_info_extend = height + PX_info_enable - UIManager.dpToPx(context, 7);
		
		LL_extendBtns.removeAllViews();
		
		for (int i = 0; i < btnTitles.length; i++) {
			final int I = i;
			
			String title = btnTitles[i];
			int color = btnColors[i];
			
			LinearLayout LL_btn = (LinearLayout) View.inflate(context, R.layout.res_btn, null);
			((TextView) LL_btn.findViewById(R.id.btn)).setText(title);
			((GradientDrawable) LL_btn.findViewById(R.id.btn).getBackground()).setColor(color);
			
			final PackView packView = this;
			LL_btn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					if (listener != null)
						listener.onExtendFunctionBtnClick(packView, I);
				}
			});
			
			LL_extendBtns.addView(LL_btn, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
		}
		
		return this;
	}
	
	//========================================================================================= Play
	
	private boolean isPlay = false;
	
	public void togglePlay(boolean bool) {
		if (isPlay != bool) {
			if (bool) {
				//animation
				LL_leftView.animate().x(PX_flag_enable).setDuration(500).start();
				
				//clickEvent
				RL_playBtn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onPlayClick();
					}
				});
			} else {
				//animation
				LL_leftView.animate().x(PX_flag_default).setDuration(500).start();
				
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
	
	private int levDetail = 0;
	
	public void toggleDetail(int lev) {
		if (levDetail != lev || lev == 2) {
			final int px_default = PX_info_default;
			final int px_enable = PX_info_enable;
			final int px_extend = PX_info_extend;
			
			int start = 0;
			int end = 0;
			if (lev == 0) {
				RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) RL_detail.getLayoutParams();
				start = params.height;
				end = px_default;
				animateExtendBtns(false);
			} else if (lev == 1) {
				RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) RL_detail.getLayoutParams();
				start = params.height;
				end = px_enable;
				animateExtendBtns(false);
			} else if (lev == 2) {
				RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) RL_detail.getLayoutParams();
				start = params.height;
				end = px_extend;
				animateExtendBtns(true);
			}
			
			Animation a = animateDetail(start, end);
			if (lev != 2)
				a.setAnimationListener(new Animation.AnimationListener() {
					@Override
					public void onAnimationStart(Animation animation) {
					
					}
					
					@Override
					public void onAnimationEnd(Animation animation) {
						LL_extendView.removeAllViews();
						LL_extendBtns.removeAllViews();
					}
					
					@Override
					public void onAnimationRepeat(Animation animation) {
					
					}
				});
			
			levDetail = lev;
		}
	}
	
	public Animation animateDetail(final int start, final int end) {
		final int change = end - start;
		
		Animation a = new Animation() {
			@Override
			protected void applyTransformation(float interpolatedTime, Transformation t) {
				RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) RL_detail.getLayoutParams();
				params.height = start + (int) (change * interpolatedTime);
				RL_detail.setLayoutParams(params);
			}
		};
		a.setDuration(500);
		RL_detail.startAnimation(a);
		return a;
	}
	
	public void animateExtendBtns(boolean bool) {
		if (LL_btns.getVisibility() == VISIBLE && !bool)
			return;
		if (LL_extendBtns.getVisibility() == VISIBLE && bool)
			return;
		Animation fade_in = AnimationUtils.loadAnimation(context, R.anim.btn_fade_in);
		fade_in.setInterpolator(AnimationUtils.loadInterpolator(context, android.R.anim.accelerate_decelerate_interpolator));
		
		Animation fade_out = AnimationUtils.loadAnimation(context, R.anim.btn_fade_out);
		fade_in.setInterpolator(AnimationUtils.loadInterpolator(context, android.R.anim.accelerate_decelerate_interpolator));
		
		if (bool) {
			LL_extendBtns.setVisibility(VISIBLE);
			LL_btns.setAnimation(fade_out);
			LL_extendBtns.setAnimation(fade_in);
			LL_btns.setVisibility(GONE);
		} else {
			LL_btns.setVisibility(VISIBLE);
			LL_extendBtns.setAnimation(fade_out);
			LL_btns.setAnimation(fade_in);
			LL_extendBtns.setVisibility(GONE);
		}
	}
	
	public void toggleDetail() {
		toggleDetail(levDetail == 0 ? 1 : 0);
	}
	
	public boolean isDetail() {
		return levDetail != 0;
	}
	
	public int levDetail() {
		return levDetail;
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
		if (onEventListener != null && isPlay()) onEventListener.onPlayClick(this);
	}
	
	void onFunctionBtnClick(int index) {
		if (onEventListener != null && isDetail())
			onEventListener.onFunctionBtnClick(this, index);
	}
	
	public interface OnExtendEventListener {
		void onExtendFunctionBtnClick(PackView v, int index);
	}
	
	
}