package com.kimjisub.launchpad.db.repository

import androidx.lifecycle.LiveData
import com.kimjisub.launchpad.db.dao.UnipackDao
import com.kimjisub.launchpad.db.ent.Unipack
import java.util.*

class UnipackRepository(
	private val unipackDao: UnipackDao,
) {
	@Suppress("RedundantSuspendModifier")
	fun find(id: String): LiveData<Unipack> {
		return unipackDao.find(id)
	}


	@Suppress("RedundantSuspendModifier")
	suspend fun getOrCreate(id: String): LiveData<Unipack> {
		return unipackDao.getOrCreate(id)
	}

	suspend fun toggleBookmark(id: String) {
		unipackDao.toggleBookmark(id)
	}

	@Suppress("RedundantSuspendModifier")
	fun openCount(): LiveData<Long> {
		return unipackDao.openCount()
	}

	@Suppress("RedundantSuspendModifier")
	fun openCount(id: String): LiveData<Long> {
		return unipackDao.openCount(id)
	}


	@Suppress("RedundantSuspendModifier")
	fun getLastOpenedDate(id: String): LiveData<Date> {
		return unipackDao.lastOpenedAt(id)
	}

	@Suppress("RedundantSuspendModifier")
	suspend fun recordOpen(id: String) {
		unipackDao.addOpenCount(id)
		unipackDao.setLastOpenedAt(id, Date())
	}
}