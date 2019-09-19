package com.kimjisub.launchpad.network

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object Networks {
	fun sendGet(str: String?): String {
		val html = StringBuilder()
		try {
			val url = URL(str)
			val conn = url.openConnection() as HttpURLConnection
			conn.connectTimeout = 10000
			conn.useCaches = false
			if (conn.responseCode == HttpURLConnection.HTTP_OK) {
				val br = BufferedReader(
					InputStreamReader(conn.inputStream)
				)
				while (true) {
					val line = br.readLine() ?: break
					html.append(line)
					html.append('\n')
				}
				br.close()
			}
			conn.disconnect()
		} catch (e: Exception) {
			e.printStackTrace()
		}
		return html.toString()
	}

	class FirebaseManager(key: String) {
		private var database: FirebaseDatabase = FirebaseDatabase.getInstance()
		private var myRef: DatabaseReference
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
			if (childEventListener != null) if (bool) myRef.addChildEventListener(childEventListener!!) else myRef.removeEventListener(
				childEventListener!!
			)
			if (valueEventListener != null) if (bool) myRef.addValueEventListener(valueEventListener!!) else myRef.removeEventListener(
				valueEventListener!!
			)
			return this
		}

		init {
			myRef = database.getReference(key)
		}
	}
}