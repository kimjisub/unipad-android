package com.kimjisub.launchpad.manager

class ChannelManager(x: Int, y: Int) {
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
		LED(4);
	}

	data class Item(
		var channel: Channel,
		var color: Int,
		var code: Int,
	) {
		override fun toString(): String {
			return "Item(channel=$channel, color=$color, code=$code)"
		}
	}


	init {
		btn = Array(x) { Array(y) { arrayOfNulls<Item?>(Channel.values().size) } }
		cir = Array(36) { arrayOfNulls<Item?>(Channel.values().size) }
		btnIgnoreList = BooleanArray(Channel.values().size)
		cirIgnoreList = BooleanArray(Channel.values().size)
	}


	fun get(x: Int, y: Int): Item? {
		var ret: Item? = null
		if (x != -1) {
			for (i in Channel.values().indices) {
				if (btnIgnoreList[i])
					continue
				if (btn[x][y][i] != null) {
					ret = btn[x][y][i]
					break
				}
			}
		} else {
			for (i in Channel.values().indices) {
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
		var color = color
		if (color == -1)
			color = LaunchpadColor.ARGB[code].toInt()
		if (x != -1)
			btn[x][y][channel.priority] = Item(channel, color, code)
		else
			cir[y][channel.priority] = Item(channel, color, code)
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