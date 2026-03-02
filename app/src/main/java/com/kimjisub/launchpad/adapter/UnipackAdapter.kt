package com.kimjisub.launchpad.adapter

import androidx.lifecycle.LiveData
import com.kimjisub.launchpad.db.ent.Unipack
import com.kimjisub.launchpad.unipack.UniPack

data class UniPackItem(
	var unipack: UniPack,
	val unipackENT: LiveData<Unipack>,
)