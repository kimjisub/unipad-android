package com.kimjisub.launchpad.tool

/**
 * Launchpad previous/current/next over the list the user can see.
 *
 * [anchor] is the key of the last picked item. When it is not in [keys] (never picked, or
 * filtered out) the cursor sits before the first item, so "next" picks the first visible one.
 */
class ListCursor<K>(private val keys: List<K>, anchor: K?) {

	val index: Int = if (anchor == null) -1 else keys.indexOf(anchor)

	val hasCurrent: Boolean get() = index in keys.indices
	val hasPrev: Boolean get() = index > 0
	val hasNext: Boolean get() = index < keys.lastIndex

	fun current(): K? = if (hasCurrent) keys[index] else null
	fun prev(): K? = if (hasPrev) keys[index - 1] else null
	fun next(): K? = if (hasNext) keys[index + 1] else null
}
