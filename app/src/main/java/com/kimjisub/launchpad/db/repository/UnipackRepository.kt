package com.kimjisub.launchpad.db.repository

import androidx.lifecycle.LiveData
import com.kimjisub.launchpad.db.dao.UnipackDao
import com.kimjisub.launchpad.db.ent.Unipack
import java.util.Date

class UnipackRepository(
	private val unipackDao: UnipackDao,
) {
	fun find(id: String): LiveData<Unipack> {
		return unipackDao.find(id)
	}

	suspend fun getOrCreate(id: String): LiveData<Unipack> {
		val unipack = unipackDao.find(id).value
		if (unipack == null) {
			unipackDao.insert(Unipack.create(id))
		}
		return unipackDao.find(id)
	}

	suspend fun toggleBookmark(id: String) {
		unipackDao.toggleBookmark(id)
	}

	fun totalOpenCount(): LiveData<Long> {
		return unipackDao.totalOpenCount()
	}

	fun openCount(id: String): LiveData<Long> {
		return unipackDao.openCount(id)
	}

	fun lastOpenedAt(id: String): LiveData<Date> {
		return unipackDao.lastOpenedAt(id)
	}

	suspend fun recordOpen(id: String) {
		unipackDao.addOpenCount(id)
		unipackDao.setLastOpenedAt(id, Date())
	}
}
