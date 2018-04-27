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

import com.kimjisub.design.manage.FileManager;
import com.kimjisub.design.manage.UIManager;

import static com.kimjisub.design.manage.Tools.lang;

/**
 * Created by rlawl on 2017-09-07
 */

public class _Item {/*extends RelativeLayout {

	Context context;

	RelativeLayout RL_root;
	RelativeLayout RL_detail;
	TextView TV_delete;
	TextView TV_edit;
	LinearLayout LL_leftView;
	RelativeLayout RL_playBtn;

	RelativeLayout RL_flag;
	TextView TV_title;
	TextView TV_subTitle;
	TextView TV_LED;
	TextView TV_autoPlay;
	TextView TV_size;
	TextView TV_chain;
	TextView TV_capacity;

	public Item(Context context) {
		super(context);
		initView(context);
	}

	public Item(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
		getAttrs(attrs);
	}

	public Item(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		initView(context);
		getAttrs(attrs, defStyle);
	}

	private void getAttrs(AttributeSet attrs) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.Item);
		setTypeArray(typedArray);
	}

	private void getAttrs(AttributeSet attrs, int defStyle) {
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.Item, defStyle, 0);
		setTypeArray(typedArray);
	}

	public static Item errItem(Context context, OnViewClickListener listener) {
		return new Item(context)
			.setFlagColor(R.drawable.border_play_red)
			.setTitle(lang(context, R.string.unipackNotFound))
			.setSubTitle(lang(context, R.string.clickToAddUnipack))
			.setOptionVisibility(false)
			.setOnViewClickListener(listener);
	}

	private void initView(Context context) {
		this.context = context;

		String infService = Context.LAYOUT_INFLATER_SERVICE;
		LayoutInflater li = (LayoutInflater) getContext().getSystemService(infService);
		View v = li.inflate(R.layout.item, this, false);
		addView(v);

		RL_root = findViewById(R.id.root);
		RL_detail = findViewById(R.id.info);
		TV_delete = findViewById(R.id.delete);
		TV_edit = findViewById(R.id.edit);
		LL_leftView = findViewById(R.id.leftView);
		RL_playBtn = findViewById(R.id.playBtn);

		RL_flag = findViewById(R.id.flag);
		TV_title = findViewById(R.id.title);
		TV_subTitle = findViewById(R.id.subTitle);
		TV_LED = findViewById(R.id.LED);
		TV_autoPlay = findViewById(R.id.autoPlay);
		TV_size = findViewById(R.id.size);
		TV_chain = findViewById(R.id.chain);
		TV_capacity = findViewById(R.id.capacity);


		LL_leftView.setX(UIManager.dpToPx(context, 10));
	}

	private void setTypeArray(TypedArray typedArray) {

		int color = typedArray.getResourceId(R.styleable.Item_flagColor, R.drawable.border_play_blue);
		setFlagColor(color);

		String title = typedArray.getString(R.styleable.Item_title);
		setTitle(title);

		String subTitle = typedArray.getString(R.styleable.Item_subTitle);
		setSubTitle(subTitle);

		Boolean LED = typedArray.getBoolean(R.styleable.Item_LED, false);
		setLED(LED);

		Boolean autoPlay = typedArray.getBoolean(R.styleable.Item_AutoPlay, false);
		setAutoPlay(autoPlay);

		String size = typedArray.getString(R.styleable.Item_size);
		setSize(size);

		String chain = typedArray.getString(R.styleable.Item_chain);
		setChain(chain);

		int capacity = typedArray.getInteger(R.styleable.Item_capacity, 0);
		setCapacity(capacity);

		Boolean optionVisibility = typedArray.getBoolean(R.styleable.Item_optionVisibility, true);
		setOptionVisibility(optionVisibility);

		typedArray.recycle();
	}


	//========================================================================================= Set Function

	public Item setFlagColor(int res) {
		RL_flag.setBackgroundResource(res);
		return this;
	}

	public Item setTitle(String str) {
		TV_title.setText(str);
		return this;
	}

	public Item setSubTitle(String str) {
		TV_subTitle.setText(str);
		return this;
	}

	public Item setLED(boolean bool) {
		if (bool)
			TV_LED.setTextColor(getResources().getColor(R.color.green));
		else
			TV_LED.setTextColor(getResources().getColor(R.color.pink));
		return this;
	}

	public Item setAutoPlay(boolean bool) {
		if (bool)
			TV_autoPlay.setTextColor(getResources().getColor(R.color.green));
		else
			TV_autoPlay.setTextColor(getResources().getColor(R.color.pink));
		return this;
	}

	public Item setSize(String str) {
		TV_size.setText(str);
		return this;
	}

	public Item setChain(String str) {
		TV_chain.setText(str);
		return this;
	}

	public Item setCapacity(long num) {
		TV_capacity.setText(FileManager.byteToMB(num) + " MB");
		return this;
	}

	public Item setOptionVisibility(boolean bool) {
		if (bool) {
			TV_LED.setVisibility(VISIBLE);
			TV_autoPlay.setVisibility(VISIBLE);
		} else {
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
				LL_leftView.animate().x(UIManager.dpToPx(context, 100)).setDuration(500).start();
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
				LL_leftView.animate().x(UIManager.dpToPx(context, 10)).setDuration(500).start();
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
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) RL_detail.getLayoutParams();
						params.topMargin = px + (int) (px2 * interpolatedTime);
						RL_detail.setLayoutParams(params);
					}
				};
				a.setDuration(500);
				RL_detail.startAnimation(a);

				//clickEvent
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
				});
			} else {
				//animation
				Animation a = new Animation() {
					@Override
					protected void applyTransformation(float interpolatedTime, Transformation t) {
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) RL_detail.getLayoutParams();
						params.topMargin = px + px2 + (int) (-px2 * interpolatedTime);
						RL_detail.setLayoutParams(params);
					}
				};
				a.setDuration(500);
				RL_detail.startAnimation(a);

				//clickEvent
				TV_delete.setOnClickListener(null);
				TV_delete.setClickable(false);
				TV_edit.setOnClickListener(null);
				TV_edit.setClickable(false);
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

	public Item setOnPlayClickListener(OnPlayClickListener listener) {
		this.onPlayClickListener = listener;
		return this;
	}

	void onPlayClick() {
		if (onPlayClickListener != null) onPlayClickListener.onPlayClick();
	}

	//=========================================================================================

	private OnDeleteClickListener onDeleteClickListener = null;

	public interface OnDeleteClickListener {
		void onDeleteClick();
	}

	public Item setOnDeleteClickListener(OnDeleteClickListener listener) {
		this.onDeleteClickListener = listener;
		return this;
	}

	void onDeleteClick() {
		if (onDeleteClickListener != null) onDeleteClickListener.onDeleteClick();
	}

	//=========================================================================================

	private OnEditClickListener onEditClickListener = null;

	public interface OnEditClickListener {
		void onEditClick();
	}

	public Item setOnEditClickListener(OnEditClickListener listener) {
		this.onEditClickListener = listener;
		return this;
	}

	void onEditClick() {
		if (onEditClickListener != null) onEditClickListener.onEditClick();
	}


	//=========================================================================================

	private OnViewClickListener onViewClickListener = null;

	public interface OnViewClickListener {
		void onViewClick(Item v);
	}

	public Item setOnViewClickListener(OnViewClickListener listener) {
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

	public interface OnViewLongClickListener {
		void onViewLongClick(Item v);
	}

	public Item setOnViewLongClickListener(OnViewLongClickListener listener) {
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
*/
}
