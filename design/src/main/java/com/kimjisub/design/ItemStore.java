package com.kimjisub.design;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Created by rlawl on 2017-09-12.
 */

public class ItemStore extends RelativeLayout {

	Context context;

	RelativeLayout RL_root;

	TextView TV_title;
	TextView TV_subTitle;
	TextView TV_LED;
	TextView TV_autoPlay;
	TextView TV_downloadCount;
	RelativeLayout RL_flag;

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

	private void initView(Context context) {
		this.context = context;

		String infService = Context.LAYOUT_INFLATER_SERVICE;
		LayoutInflater li = (LayoutInflater) getContext().getSystemService(infService);
		View v = li.inflate(R.layout.item_store, this, false);
		addView(v);

		RL_root = (RelativeLayout) findViewById(R.id.root);

		RL_flag = (RelativeLayout) findViewById(R.id.play);
		TV_title = (TextView) findViewById(R.id.title);
		TV_subTitle = (TextView) findViewById(R.id.subTitle);
		TV_LED = (TextView) findViewById(R.id.LED);
		TV_autoPlay = (TextView) findViewById(R.id.autoPlay);
		TV_downloadCount = (TextView) findViewById(R.id.downloadCount);


		findViewById(R.id.leftView).setX(UIManager.dpToPx(context, 10));
	}

	private void getAttrs(AttributeSet attrs) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.ItemStore);
		setTypeArray(typedArray);
	}

	private void getAttrs(AttributeSet attrs, int defStyle) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.ItemStore, defStyle, 0);
		setTypeArray(typedArray);
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

		String downloadCount = typedArray.getString(R.styleable.ItemStore_downloadCount);
		setDownloadCount(downloadCount);

		Boolean optionVisibility = typedArray.getBoolean(R.styleable.ItemStore_optionVisibility, true);
		setOptionVisibility(optionVisibility);

		typedArray.recycle();
	}

	public ItemStore setFlagColor(int res) {
		RL_flag.setBackgroundResource(res);
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

	public ItemStore setDownloadCount(String str) {
		TV_downloadCount.setText(str);
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


	//========================================================================================= Play

	private boolean isPlay = false;

	public void togglePlay(boolean bool) {
		if (isPlay != bool) {
			if (bool) {
				//animation
				findViewById(R.id.leftView).animate().x(UIManager.dpToPx(context, 100)).setDuration(500).start();
				findViewById(R.id.play).animate().alpha(0).setDuration(500).start();

				//clickEvent
				findViewById(R.id.playbtn).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onPlayClick();
					}
				});
			} else {
				//animation
				findViewById(R.id.leftView).animate().x(UIManager.dpToPx(context, 10)).setDuration(500).start();
				findViewById(R.id.play).animate().alpha(1).setDuration(500).start();

				//clickEvent
				findViewById(R.id.playbtn).setOnClickListener(null);
				findViewById(R.id.playbtn).setClickable(false);
			}
			isPlay = bool;
		}
	}

	public void togglePlay() {
		togglePlay(!isPlay);
	}

	public boolean isPlay() {
		return isPlay;
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
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) findViewById(R.id.info).getLayoutParams();
						params.topMargin = px + (int) (px2 * interpolatedTime);
						findViewById(R.id.info).setLayoutParams(params);
					}
				};
				a.setDuration(500);
				findViewById(R.id.info).startAnimation(a);

				//clickEvent
				findViewById(R.id.delete).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onDeleteClick();
					}
				});
			} else {
				//animation
				Animation a = new Animation() {
					@Override
					protected void applyTransformation(float interpolatedTime, Transformation t) {
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) findViewById(R.id.info).getLayoutParams();
						params.topMargin = px + px2 + (int) (-px2 * interpolatedTime);
						findViewById(R.id.info).setLayoutParams(params);
					}
				};
				a.setDuration(500);
				findViewById(R.id.info).startAnimation(a);

				//clickEvent
				findViewById(R.id.delete).setOnClickListener(null);
				findViewById(R.id.delete).setClickable(false);
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


	private OnDeleteClickListener onDeleteClickListener = null;

	public interface OnDeleteClickListener {
		void onDeleteClick();
	}

	public ItemStore setOnDeleteClickListener(OnDeleteClickListener listener) {
		this.onDeleteClickListener = listener;
		return this;
	}

	void onDeleteClick() {
		if (onDeleteClickListener != null) onDeleteClickListener.onDeleteClick();
	}


	public ItemStore setOnViewClickListener(View.OnClickListener listener) {
		RL_root.setOnClickListener(listener);
		return this;
	}

	public ItemStore setOnViewLongClickListener(View.OnLongClickListener listener) {
		RL_root.setOnLongClickListener(listener);
		return this;
	}

}