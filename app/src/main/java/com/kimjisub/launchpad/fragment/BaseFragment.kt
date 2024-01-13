package com.kimjisub.launchpad.fragment

import androidx.fragment.app.Fragment
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import org.koin.android.ext.android.inject

open class BaseFragment : Fragment() {
	val p: PreferenceManager by inject()
	val ws: WorkspaceManager by inject()
	// val unipackRepo: UnipackRepository by inject()

	// val ads by lazy { AdmobManager(this) }
	// val bm by lazy { BillingModule(this, lifecycleScope, this) }


}