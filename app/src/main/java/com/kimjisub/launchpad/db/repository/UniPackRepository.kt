package com.kimjisub.launchpad.db.repository

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.kimjisub.launchpad.db.dao.UniPackDao
import com.kimjisub.launchpad.db.dao.UniPackOpenDao
import com.kimjisub.launchpad.db.ent.UniPackOpenENT
import com.kimjisub.launchpad.db.ent.Unipack
import java.util.*

class UniPackRepository(
	private val uniPackDAO: UniPackDao,
	private val uniPackOpenDAO: UniPackOpenDao,
) {
	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun find(id: String): LiveData<Unipack> {
		return uniPackDAO.find(id)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun insert(unipack: Unipack) {
		uniPackDAO.insert(unipack)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun update(unipack: Unipack) {
		return uniPackDAO.update(unipack)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun getOrCreate(id: String): LiveData<Unipack> {
		return uniPackDAO.getOrCreate(id)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun recordOpen(id: String) {
		uniPackOpenDAO.insert(UniPackOpenENT(id, Date()))
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun openCount(): LiveData<Int> {
		return uniPackOpenDAO.count
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun openCount(id: String): LiveData<Int> {
		return uniPackOpenDAO.getCount(id)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun openCountSync(id: String): Int {
		return uniPackOpenDAO.getCountSync(id)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun getLastOpenedDate(id: String): LiveData<UniPackOpenENT> {
		return uniPackOpenDAO.getLastOpenedDate(id)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun getLastOpenedDateSync(id: String): UniPackOpenENT? {
		return uniPackOpenDAO.getLastOpenedDateSync(id)
	}

}