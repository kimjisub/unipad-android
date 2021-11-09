package com.kimjisub.launchpad.db.repository

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.kimjisub.launchpad.db.dao.UniPackDAO
import com.kimjisub.launchpad.db.dao.UniPackOpenDAO
import com.kimjisub.launchpad.db.ent.UniPackENT
import com.kimjisub.launchpad.db.ent.UniPackOpenENT
import java.util.*

class UniPackRepository (private val uniPackDAO:UniPackDAO, private val uniPackOpenDAO: UniPackOpenDAO){
	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun find(id: String): LiveData<UniPackENT>{
		return uniPackDAO.find(id)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun insert(unipack: UniPackENT){
		uniPackDAO.insert(unipack)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun update(unipack: UniPackENT){
		return uniPackDAO.update(unipack)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun getOrCreate(id: String): LiveData<UniPackENT>{
		return uniPackDAO.getOrCreate(id)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun recordOpen(id: String){
		uniPackOpenDAO.insert(UniPackOpenENT(id, Date()))
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun openCount(): LiveData<Int>{
		return uniPackOpenDAO.count
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun openCount(id: String): LiveData<Int>{
		return uniPackOpenDAO.getCount(id)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun openCountSync(id: String): Int{
		return uniPackOpenDAO.getCountSync(id)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun getLastOpenedDate(id: String): LiveData<UniPackOpenENT>{
		return uniPackOpenDAO.getLastOpenedDate(id)
	}

	@Suppress("RedundantSuspendModifier")
	@WorkerThread
	fun getLastOpenedDateSync(id: String): UniPackOpenENT?{
		return uniPackOpenDAO.getLastOpenedDateSync(id)
	}

}