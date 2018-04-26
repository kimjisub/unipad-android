package com.kimjisub.design;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
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
	
	RelativeLayout RL_flag;
	TextView TV_title;
	TextView TV_subTitle;
	TextView TV_LED;
	TextView TV_autoPlay;
	TextView TV_size;
	TextView TV_chain;
	TextView TV_capacity;

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

	private void initView(Context context) {
		this.context = context;

		String infService = Context.LAYOUT_INFLATER_SERVICE;
		LayoutInflater li = (LayoutInflater) getContext().getSystemService(infService);
		View v = li.inflate(R.layout.packview, this, false);
		addView(v);

		RL_root = findViewById(R.id.root);
		RL_info = findViewById(R.id.info);
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