package com.kimjisub.launchpad.network

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.kimjisub.launchpad.network.Networks.FirebaseManager
import com.kimjisub.launchpad.network.fb.StoreVO
import com.kimjisub.launchpad.tool.Log

/** Activity-scoped subscription. Tests replace the feed, while exercising the real store screen. */
interface StoreCatalog {
	interface Listener {
		fun onAdded(pack: StoreVO, key: String)
		fun onChanged(pack: StoreVO)
		fun onCount(count: Long)
	}

	fun attach(listener: Listener)
	fun detach()
}

class FirebaseStoreCatalog internal constructor(
	storeFactory: () -> FirebaseManager,
	countFactory: () -> FirebaseManager,
) : StoreCatalog {
	constructor() : this({ FirebaseManager("store") }, { FirebaseManager("storeCount") })

	private val store by lazy(storeFactory)
	private val count by lazy(countFactory)

	override fun attach(listener: StoreCatalog.Listener) {
		store.setEventListener(object : ChildEventListener {
			override fun onChildAdded(snapshot: DataSnapshot, previousKey: String?) {
				try {
					val pack = snapshot.getValue(StoreVO::class.java) ?: return
					val key = snapshot.key ?: pack.code ?: return
					listener.onAdded(pack, key)
				} catch (e: RuntimeException) {
					Log.err("onChildAdded failed", e)
				}
			}

			override fun onChildChanged(snapshot: DataSnapshot, previousKey: String?) {
				try {
					val pack = snapshot.getValue(StoreVO::class.java) ?: return
					listener.onChanged(pack)
				} catch (e: RuntimeException) {
					Log.err("onChildChanged failed", e)
				}
			}

			override fun onChildRemoved(snapshot: DataSnapshot) {}
			override fun onChildMoved(snapshot: DataSnapshot, previousKey: String?) {}
			override fun onCancelled(error: DatabaseError) {}
		})
		count.setEventListener(object : ValueEventListener {
			override fun onDataChange(snapshot: DataSnapshot) {
				val value = snapshot.getValue(Long::class.java) ?: return
				listener.onCount(value)
			}

			override fun onCancelled(error: DatabaseError) {}
		})
		store.attachEventListener(true)
		count.attachEventListener(true)
	}

	override fun detach() {
		store.attachEventListener(false)
		count.attachEventListener(false)
	}
}
