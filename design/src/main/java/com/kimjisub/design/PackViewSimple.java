package com.kimjisub.design;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kimjisub.design.manage.UIManager;

public class PackViewSimple extends RelativeLayout {

	Context context;

	LinearLayout LL_touchView;
	LinearLayout LL_leftView;
	RelativeLayout RL_playBtn;
	ImageView IV_playImg;
	TextView TV_playText;

	RelativeLayout RL_flag;
	RelativeLayout RL_flagSize;
	int RL_flag_color;
	TextView TV_title;
	TextView TV_subTitle;
	TextView TV_option1;
	TextView TV_option2;

	int PX_flag_default;
	int PX_flag_enable;
	private boolean isToggle = false;
	private OnEventListener onEventListener = null;

	ValueAnimator flagAnimator;
	Animation toggleAnimator;

	public PackViewSimple(Context context) {
		super(context);
		initView(context);
	}

	public PackViewSimple(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
		getAttrs(attrs);
	}

	public PackViewSimple(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		initView(context);
		getAttrs(attrs, defStyle);
	}

	public static PackViewSimple errItem(Context context, String title, String subTitle, OnEventListener listener) {
		return new PackViewSimple(context)
				.setFlagColor(context.getResources().getColor(R.color.red))
				.setTitle(title)
				.setSubTitle(subTitle)
				//.setOptionVisibility(false)
				.setOnEventListener(listener);
	}

	private void initView(Context context) {
		this.context = context;

		String infService = Context.LAYOUT_INFLATER_SERVICE;
		LayoutInflater li = (LayoutInflater) getContext().getSystemService(infService);
		View v = li.inflate(R.layout.packviewsimple, this, false);
		addView(v);


		// set view
		LL_touchView = findViewById(R.id.touchView);
		LL_leftView = findViewById(R.id.leftView);
		RL_playBtn = findViewById(R.id.playBtn);
		IV_playImg = findViewById(R.id.playImg);
		TV_playText = findViewById(R.id.playText);

		RL_flag = findViewById(R.id.flag);
		RL_flagSize = findViewById(R.id.flagSize);
		TV_title = findViewById(R.id.title);
		TV_subTitle = findViewById(R.id.subTitle);
		TV_option1 = findViewById(R.id.option1);
		TV_option2 = findViewById(R.id.option2);

		// set vars
		PX_flag_default = UIManager.dpToPx(context, 10);
		PX_flag_enable = UIManager.dpToPx(context, 100);

		// set listener
		LL_touchView.setOnClickListener(v1 -> onViewClick());
		LL_touchView.setOnLongClickListener(v2 -> {
			onViewLongClick();
			return true;
		});
	}

	private void getAttrs(AttributeSet attrs) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PackView);
		setTypeArray(typedArray);
	}
	//============================================================================================== set / update / etc..

	private void getAttrs(AttributeSet attrs, int defStyle) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PackView, defStyle, 0);
		setTypeArray(typedArray);
	}

	private void setTypeArray(TypedArray typedArray) {

		/*int color = typedArray.getResourceId(R.styleable.PackView_flagColor, R.drawable.border_play_blue);
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
		//setOptionVisibility(optionVisibility);*/

		typedArray.recycle();
	}

	public PackViewSimple setTitle(String str) {
		TV_title.setText(str);
		return this;
	}

	public PackViewSimple setSubTitle(String str) {
		TV_subTitle.setText(str);
		return this;
	}

	public PackViewSimple setOption1(String msg, boolean bool) {
		int green = getResources().getColor(R.color.green);
		int pink = getResources().getColor(R.color.pink);

		TV_option1.setText(msg);
		TV_option1.setTextColor(bool ? green : pink);

		return this;
	}

	public PackViewSimple setOption2(String msg, boolean bool) {
		int green = getResources().getColor(R.color.green);
		int pink = getResources().getColor(R.color.pink);

		TV_option2.setText(msg);
		TV_option2.setTextColor(bool ? green : pink);

		return this;
	}

	public PackViewSimple setPlayImageShow(boolean bool) {
		IV_playImg.setVisibility(bool ? VISIBLE : GONE);

		return this;
	}

	public PackViewSimple setPlayText(String str) {
		TV_playText.setText(str);

		return this;
	}

	//============================================================================================== Flag

	public PackViewSimple animateFlagColor(int colorNext) {

		int colorPrev = RL_flag_color;
		flagAnimator = ObjectAnimator.ofObject(new ArgbEvaluator(), colorPrev, colorNext);
		flagAnimator.setDuration(500);
		flagAnimator.addUpdateListener(valueAnimator -> {
			int color = (int) valueAnimator.getAnimatedValue();

			GradientDrawable flagBackground = (GradientDrawable) getResources().getDrawable(R.drawable.border_all_round);
			flagBackground.setColor(color);
			RL_flag.setBackground(flagBackground);
		});
		flagAnimator.start();
		RL_flag_color = colorNext;

		return this;
	}

	public PackViewSimple setFlagColor(int color) {
		GradientDrawable flagBackground = (GradientDrawable) getResources().getDrawable(R.drawable.border_all_round);
		flagBackground.setColor(color);
		RL_flag.setBackground(flagBackground);
		RL_flag_color = color;

		return this;
	}

	//============================================================================================== Toggle

	public PackViewSimple animateToggle(final int target) {
		int start = RL_flagSize.getLayoutParams().width;

		final int change = target - start;
		toggleAnimator = new Animation() {
			@Override
			protected void applyTransformation(float interpolatedTime, Transformation t) {
				ViewGroup.LayoutParams params = RL_flagSize.getLayoutParams();
				params.width = start + (int) (change * interpolatedTime);
				RL_flagSize.setLayoutParams(params);
			}
		};
		toggleAnimator.setDuration(500);
		RL_flagSize.startAnimation(toggleAnimator);

		return this;
	}

	public PackViewSimple skipAnimateToggle(final int target) {
		ViewGroup.LayoutParams params = RL_flagSize.getLayoutParams();
		params.width = target;
		RL_flagSize.setLayoutParams(params);

		return this;
	}

	public PackViewSimple toggle(boolean bool) {
		if (isToggle != bool) {
			if (bool) {
				//animation
				animateToggle(PX_flag_enable);

				//clickEvent
				RL_playBtn.setOnClickListener(v -> onPlayClick());
			} else {
				//animation
				animateToggle(PX_flag_default);

				//clickEvent
				RL_playBtn.setOnClickListener(null);
				RL_playBtn.setClickable(false);
			}
			isToggle = bool;
		}

		return this;
	}

	public PackViewSimple toggle() {
		toggle(!isToggle);

		return this;
	}

	public PackViewSimple toggle(boolean bool, int onColor, int offColor) {
		toggle(bool);
		if (isToggle)
			animateFlagColor(onColor);
		else
			animateFlagColor(offColor);

		return this;
	}

	public PackViewSimple toggle(int onColor, int offColor) {
		toggle(!isToggle);
		if (isToggle)
			animateFlagColor(onColor);
		else
			animateFlagColor(offColor);

		return this;
	}

	public PackViewSimple setToggle(boolean bool) {
		if (bool) {
			//animation
			skipAnimateToggle(PX_flag_enable);

			//clickEvent
			RL_playBtn.setOnClickListener(v -> onPlayClick());
		} else {
			//animation
			skipAnimateToggle(PX_flag_default);

			//clickEvent
			RL_playBtn.setOnClickListener(null);
			RL_playBtn.setClickable(false);
		}
		isToggle = bool;

		return this;
	}

	public PackViewSimple setToggle() {
		setToggle(!isToggle);

		return this;
	}

	public PackViewSimple setToggle(boolean bool, int onColor, int offColor) {
		setToggle(bool);
		if (isToggle)
			setFlagColor(onColor);
		else
			setFlagColor(offColor);

		return this;
	}

	public PackViewSimple setToggle(int onColor, int offColor) {
		setToggle(!isToggle);
		if (isToggle)
			setFlagColor(onColor);
		else
			setFlagColor(offColor);

		return this;
	}

	public boolean isToggle() {
		return isToggle;
	}

	//==============================================================================================

	public PackViewSimple cancelAllAnimation() {
		if (flagAnimator != null) {
			flagAnimator.cancel();
			setFlagColor(RL_flag_color);
		}
		if (toggleAnimator != null) {
			toggleAnimator.cancel();
			setToggle(isToggle);
		}

		return this;
	}

	//============================================================================================== Listener


	public PackViewSimple setOnEventListener(OnEventListener listener) {
		this.onEventListener = listener;
		return this;
	}

	public void onViewClick() {
		if (onEventListener != null) onEventListener.onViewClick(this);
	}

	public void onViewLongClick() {
		if (onEventListener != null) onEventListener.onViewLongClick(this);
	}

	public void onPlayClick() {
		if (onEventListener != null && isToggle()) onEventListener.onPlayClick(this);
	}

	public interface OnEventListener {

		void onViewClick(PackViewSimple v);

		void onViewLongClick(PackViewSimple v);

		void onPlayClick(PackViewSimple v);
	}
}