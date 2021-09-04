package com.kimjisub.launchpad.activity

import android.os.Bundle
import com.kimjisub.launchpad.databinding.ActivityStoreBinding

class StoreActivity : BaseActivity() {
	private lateinit var b: ActivityStoreBinding
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivityStoreBinding.inflate(layoutInflater)
		setContentView(b.root)
	}
}