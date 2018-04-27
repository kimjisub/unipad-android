package com.kimjisub.design;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kimjisub.design.manage.UIManager;

import java.text.DecimalFormat;

import static com.kimjisub.design.manage.Tools.lang;

/**
 * Created by rlawl on 2017-09-12.
 */

public class ItemStore extends RelativeLayout {

	Context context;

	RelativeLayout RL_root;
	RelativeLayout RL_info;
	LinearLayout LL_leftView;

	RelativeLayout RL_flag1;
	RelativeLayout RL_flag2;
	TextView TV_progress;
	TextView TV_title;
	TextView TV_subTitle;
	TextView TV_LED;
	TextView TV_autoPlay;
	TextView TV_downloadCount;

	public ItemStore(Context context) {
		super(context);
		initView(context);
	}

	public ItemStore(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
		getAttrs(attrs);

	}

	public ItemStore(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		initView(context);
		getAttrs(attrs, defStyle);

	}

	private void getAttrs(AttributeSet attrs) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.ItemStore);
		setTypeArray(typedArray);
	}

	private void getAttrs(AttributeSet attrs, int defStyle) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.ItemStore, defStyle, 0);
		setTypeArray(typedArray);
	}

	public static ItemStore errItem(Context context, OnViewClickListener listener){
		return new ItemStore(context)
			.setFlagColor(R.drawable.border_play_red)
			.setTitle(lang(context, R.string.errOccur))
			.setSubTitle(lang(context, R.string.UnableToAccessServer))
			.setOptionVisibility(false)
			.setOnViewClickListener(listener);
	}

	private void initView(Context context) {
		this.context = context;

		String infService = Context.LAYOUT_INFLATER_SERVICE;
		LayoutInflater li = (LayoutInflater) getContext().getSystemService(infService);
		View v = li.inflate(R.layout.item_store, this, false);
		addView(v);

		RL_root =  findViewById(R.id.root);
		RL_info =  findViewById(R.id.detail);
		LL_leftView = findViewById(R.id.leftView);

		RL_flag1 =  findViewById(R.id.play1);
		RL_flag2 =  findViewById(R.id.play2);
		TV_progress = findViewById(R.id.progress);
		TV_title = findViewById(R.id.title);
		TV_subTitle = findViewById(R.id.subTitle);
		TV_LED = findViewById(R.id.LED);
		TV_autoPlay = findViewById(R.id.autoPlay);
		TV_downloadCount = findViewById(R.id.downloadCount);


		findViewById(R.id.leftView).setX(UIManager.dpToPx(context, 10));
	}

	private void setTypeArray(TypedArray typedArray) {

		int color = typedArray.getResourceId(R.styleable.ItemStore_flagColor, R.drawable.border_play_blue);
		setFlagColor(color);

		String title = typedArray.getString(R.styleable.ItemStore_title);
		setTitle(title);

		String subTitle = typedArray.getString(R.styleable.ItemStore_subTitle);
		setSubTitle(subTitle);

		Boolean LED = typedArray.getBoolean(R.styleable.ItemStore_LED, false);
		setLED(LED);

		Boolean autoPlay = typedArray.getBoolean(R.styleable.ItemStore_AutoPlay, false);
		setAutoPlay(autoPlay);

		int downloadCount = typedArray.getInteger(R.styleable.ItemStore_downloadCount, 0);
		updateDownloadCount(downloadCount);

		Boolean optionVisibility = typedArray.getBoolean(R.styleable.ItemStore_optionVisibility, true);
		setOptionVisibility(optionVisibility);

		typedArray.recycle();
	}


	//========================================================================================= Set Function

	public ItemStore setFlagColor(int res) {
		RL_flag2.setBackgroundResource(res);
		return this;
	}

	public ItemStore changeFlagColor(int res){
		RL_flag1.setBackground(RL_flag2.getBackground());
		RL_flag1.setAlpha(1);
		RL_flag2.setBackground(getResources().getDrawable(res));
		RL_flag1.animate().alpha(0).setDuration(500).start();
		return this;
	}

	public ItemStore changeFlagOpen(boolean bool){
		if(bool)
			LL_leftView.animate().x(UIManager.dpToPx(context, 100)).setDuration(500).start();
		else
			LL_leftView.animate().x(UIManager.dpToPx(context, 10)).setDuration(500).start();
		return this;
	}

	public ItemStore setProgress(String str){
		TV_progress.setText(str);
		return this;
	}

	public ItemStore setTitle(String str) {
		TV_title.setText(str);
		return this;
	}

	public ItemStore setSubTitle(String str) {
		TV_subTitle.setText(str);
		return this;
	}

	public ItemStore setLED(boolean bool) {
		if (bool)
			TV_LED.setTextColor(getResources().getColor(R.color.green));
		else
			TV_LED.setTextColor(getResources().getColor(R.color.pink));
		return this;
	}

	public ItemStore setAutoPlay(boolean bool) {
		if (bool)
			TV_autoPlay.setTextColor(getResources().getColor(R.color.green));
		else
			TV_autoPlay.setTextColor(getResources().getColor(R.color.pink));
		return this;
	}

	public ItemStore updateDownloadCount(int num) {
		TV_downloadCount.setText((new DecimalFormat("#,##0")).format(num));
		TV_downloadCount.setAlpha(0);
		TV_downloadCount.animate().alpha(1).setDuration(500).start();
		return this;
	}

	public ItemStore setOptionVisibility(boolean bool) {
		if (bool) {
			TV_LED.setVisibility(VISIBLE);
			TV_autoPlay.setVisibility(VISIBLE);
		}else {
			TV_LED.setVisibility(INVISIBLE);
			TV_autoPlay.setVisibility(INVISIBLE);
		}
		return this;
	}

	//========================================================================================= Info

	private boolean isInfo = false;

	public void toggleInfo(boolean bool) {
		if (isInfo != bool) {
			final int px = UIManager.dpToPx(context, 30);
			final int px2 = UIManager.dpToPx(context, 35);
			if (bool) {
				//animation
				Animation a = new Animation() {
					@Override
					protected void applyTransformation(float interpolatedTime, Transformation t) {
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) findViewById(R.id.detail).getLayoutParams();
						params.topMargin = px + (int) (px2 * interpolatedTime);
						findViewById(R.id.detail).setLayoutParams(params);
					}
				};
				a.setDuration(500);
				findViewById(R.id.detail).startAnimation(a);
			} else {
				//animation
				Animation a = new Animation() {
					@Override
					protected void applyTransformation(float interpolatedTime, Transformation t) {
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) findViewById(R.id.detail).getLayoutParams();
						params.topMargin = px + px2 + (int) (-px2 * interpolatedTime);
						findViewById(R.id.detail).setLayoutParams(params);
					}
				};
				a.setDuration(500);
				findViewById(R.id.detail).startAnimation(a);
			}

			isInfo = bool;
		}
	}

	public void toggleInfo() {
		toggleInfo(!isInfo);
	}

	public boolean isInfo() {
		return isInfo;
	}


	//========================================================================================= Listener


	private OnPlayClickListener onPlayClickListener = null;

	public interface OnPlayClickListener {
		void onPlayClick();
	}

	public ItemStore setOnPlayClickListener(OnPlayClickListener listener) {
		this.onPlayClickListener = listener;
		return this;
	}

	void onPlayClick() {
		if (onPlayClickListener != null) onPlayClickListener.onPlayClick();
	}


	//=========================================================================================

	private OnViewClickListener onViewClickListener = null;

	public interface OnViewClickListener {
		void onViewClick(ItemStore v);
	}

	public ItemStore setOnViewClickListener(OnViewClickListener listener) {
		RL_root.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onViewClick();
			}
		});
		onViewClickListener = listener;
		return this;
	}

	void onViewClick() {
		if (onViewClickListener != null) onViewClickListener.onViewClick(this);
	}


	//=========================================================================================

	private OnViewLongClickListener onViewLongClickListener = null;

	public interface OnViewLongClickListener  {
		void onViewLongClick(ItemStore v);
	}

	public ItemStore setOnViewLongClickListener(OnViewLongClickListener listener) {
		RL_root.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				onViewLongClick();
				return true;
			}
		});
		onViewLongClickListener = listener;
		return this;
	}

	void onViewLongClick() {
		if (onViewLongClickListener != null) onViewLongClickListener.onViewLongClick(this);
	}
}