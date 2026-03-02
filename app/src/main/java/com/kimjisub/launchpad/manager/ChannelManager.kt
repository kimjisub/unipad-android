package com.kimjisub.launchpad.manager

class ChannelManager(x: Int, y: Int) {
	companion object {
		private const val CIRCULAR_BUTTON_COUNT = 36
	}

	private var btn: Array<Array<Array<Item?>>>
	private var cir: Array<Array<Item?>>
	private var btnIgnoreList: BooleanArray
	private var cirIgnoreList: BooleanArray


	enum class Channel(val priority: Int) {
		UI(0),
		UI_UNIPAD(1),
		GUIDE(2),
		PRESSED(3),
		CHAIN(3),
		LED(4)
	}

	data class Item(
		var channel: Channel,
		var color: Int,
		var code: Int,
	)


	init {
		btn = Array(x) { Array(y) { arrayOfNulls<Item>(Channel.entries.size) } }
		cir = Array(CIRCULAR_BUTTON_COUNT) { arrayOfNulls<Item>(Channel.entries.size) }
		btnIgnoreList = BooleanArray(Channel.entries.size)
		cirIgnoreList = BooleanArray(Channel.entries.size)
	}


	fun get(x: Int, y: Int): Item? {
		var ret: Item? = null
		if (x != -1) {
			for (i in Channel.entries.indices) {
				if (btnIgnoreList[i])
					continue
				if (btn[x][y][i] != null) {
					ret = btn[x][y][i]
					break
				}
			}
		} else {
			for (i in Channel.entries.indices) {
				if (cirIgnoreList[i])
					continue
				if (cir[y][i] != null) {
					ret = cir[y][i]
					break
				}
			}
		}
		return ret
	}

	fun add(x: Int, y: Int, channel: Channel, color: Int, code: Int) {
		val resolvedColor = if (color == -1) LaunchpadColor.ARGB[code].toInt() else color
		if (x != -1)
			btn[x][y][channel.priority] = Item(channel, resolvedColor, code)
		else
			cir[y][channel.priority] = Item(channel, resolvedColor, code)
	}

	fun remove(x: Int, y: Int, channel: Channel) {
		if (x != -1)
			btn[x][y][channel.priority] = null
		else
			cir[y][channel.priority] = null
	}

	fun setBtnIgnore(channel: Channel, ignore: Boolean) {
		btnIgnoreList[channel.priority] = ignore
	}

	fun setCirIgnore(channel: Channel, ignore: Boolean) {
		cirIgnoreList[channel.priority] = ignore
	}
}