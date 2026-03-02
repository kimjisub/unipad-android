package com.kimjisub.launchpad.di

import com.kimjisub.launchpad.db.AppDatabase
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import org.koin.dsl.module

val appModule = module {
	single {
		val db = AppDatabase.getInstance(get())
		UnipackRepository(db.unipackDAO())
	}

	single {
		PreferenceManager(get())
	}

	single {
		WorkspaceManager(get())
	}
}
