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

	RelativeLayout RL_flag;
	TextView TV_title;
	TextView TV_subTitle;
	TextView TV_LED;
	TextView TV_autoPlay;
	TextView TV_size;
	TextView TV_chain;
	TextView TV_capacity;

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

		RL_flag = findViewById(R.id.flag);
		TV_title = findViewById(R.id.title);
		TV_subTitle = findViewById(R.id.subTitle);
		TV_LED = findViewById(R.id.LED);
		TV_autoPlay = findViewById(R.id.autoPlay);
		TV_size = findViewById(R.id.size);
		TV_chain = findViewById(R.id.chain);
		TV_capacity = findViewById(R.id.capacity);


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
		info_enable = UIManager.dpToPx(context, 100);
		info_extend = UIManager.dpToPx(context, 35);
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

	private void getAttrs(AttributeSet attrs) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PackView);
		setTypeArray(typedArray);
	}

	private void getAttrs(AttributeSet attrs, int defStyle) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PackView, defStyle, 0);
		setTypeArray(typedArray);
	}

	private void setTypeArray(TypedArray typedArray) {

		int color = typedArray.getResourceId(R.styleable.Item_flagColor, R.drawable.border_play_blue);
		//setFlagColor(color);

		String title = typedArray.getString(R.styleable.Item_title);
		//setTitle(title);

		String subTitle = typedArray.getString(R.styleable.Item_subTitle);
		//setSubTitle(subTitle);

		Boolean LED = typedArray.getBoolean(R.styleable.Item_LED, false);
		//setLED(LED);

		Boolean autoPlay = typedArray.getBoolean(R.styleable.Item_AutoPlay, false);
		//setAutoPlay(autoPlay);

		String size = typedArray.getString(R.styleable.Item_size);
		//setSize(size);

		String chain = typedArray.getString(R.styleable.Item_chain);
		//setChain(chain);

		int capacity = typedArray.getInteger(R.styleable.Item_capacity, 0);
		//setCapacity(capacity);

		Boolean optionVisibility = typedArray.getBoolean(R.styleable.Item_optionVisibility, true);
		//setOptionVisibility(optionVisibility);

		typedArray.recycle();
	}

	//========================================================================================= set / update / etc..

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

			ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);

			LL_btns.addView(LL_infoitem, lp);
		}

		return this;
	}

	//========================================================================================= Play

	private boolean isPlay = false;

	public void togglePlay(boolean bool) {
		if (isPlay != bool) {
			if (bool) {
				//animation
				LL_leftView.animate().x(flag_enable).setDuration(500).start();
				RL_flag.animate().alpha(0).setDuration(500).start();

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
				RL_flag.animate().alpha(1).setDuration(500).start();

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
		void onPlayClick(PackView v);

		void onFunctionBtnClick(PackView v, int index);

		void onViewClick(PackView v);

		void onViewLongClick(PackView v);
	}

	public PackView setOnEventListener(OnEventListener listener) {
		this.onEventListener = listener;
		return this;
	}

	void onPlayClick() {
		if (onEventListener != null) onEventListener.onPlayClick(this);
	}

	void onFunctionBtnClick(int index) {
		if (onEventListener != null) onEventListener.onFunctionBtnClick(this, index);
	}

	void onViewClick() {
		if (onEventListener != null) onEventListener.onViewClick(this);
	}

	void onViewLongClick() {
		if (onEventListener != null) onEventListener.onViewLongClick(this);
	}


}