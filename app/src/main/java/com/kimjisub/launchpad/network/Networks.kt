package com.kimjisub.launchpad.network

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object Networks {

	class FirebaseManager(key: String) {
		private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
		private val myRef: DatabaseReference = database.getReference(key)
		private var childEventListener: ChildEventListener? = null
		private var valueEventListener: ValueEventListener? = null
		fun setEventListener(childEventListener: ChildEventListener): FirebaseManager {
			this.childEventListener = childEventListener
			return this
		}

		fun setEventListener(valueEventListener: ValueEventListener): FirebaseManager {
			this.valueEventListener = valueEventListener
			return this
		}

		fun attachEventListener(bool: Boolean): FirebaseManager {
			childEventListener?.let {
				if (bool) myRef.addChildEventListener(it) else myRef.removeEventListener(it)
			}
			valueEventListener?.let {
				if (bool) myRef.addValueEventListener(it) else myRef.removeEventListener(it)
			}
			return this
		}

	}
}
