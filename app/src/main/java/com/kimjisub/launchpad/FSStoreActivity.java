package com.kimjisub.launchpad;

import android.os.Bundle;

import com.kimjisub.launchpad.databinding.ActivityStoreBinding;

public class FSStoreActivity extends BaseActivity {

	ActivityStoreBinding b;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		b = setContentViewBind(R.layout.activity_store);
	}
}
