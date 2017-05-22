package com.kimjisub.launchpad.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * Created by kimjisub on 2017. 5. 18..
 */

public class InfoView extends LinearLayout {
	
	
	public InfoView(Context context) {
		super(context);
	}
	
	public InfoView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
		//getAttrs(attrs);
	}
	
	public InfoView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		init(context);
		//getAttrs(attrs, defStyle);
	}
	
	void init(Context context){
		
	}
	
	
}
