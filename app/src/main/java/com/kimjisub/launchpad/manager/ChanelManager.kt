package com.kimjisub.launchpad.manager

class ChanelManager(x: Int, y: Int) {
	private var btn: Array<Array<Array<Item?>>>
	private var cir: Array<Array<Item?>>
	private var btnIgnoreList: BooleanArray
	private var cirIgnoreList: BooleanArray


	enum class Chanel(val priority: Int) {
		UI(0),
		UI_UNIPAD(1),
		GUIDE(2),
		PRESSED(3),
		CHAIN(3),
		LED(4);

		companion object {
			const val size = 5
		}
	}

	data class Item(
			var chanel: Chanel,
			var color: Int,
			var code: Int)


	init {
		btn = Array(x) { Array(y) { arrayOfNulls<Item?>(Chanel.size) } }
		cir = Array(36) { arrayOfNulls<Item?>(Chanel.size) }
		btnIgnoreList = BooleanArray(Chanel.size)
		cirIgnoreList = BooleanArray(Chanel.size)
	}


	fun get(x: Int, y: Int): Item? {
		var ret: Item? = null
		if (x != -1) {
			for (i in 0 until Chanel.size) {
				if (btnIgnoreList[i])
					continue
				if (btn[x][y][i] != null) {
					ret = btn[x][y][i]
					break
				}
			}
		} else {
			for (i in 0 until Chanel.size) {
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

	fun add(x: Int, y: Int, chanel: Chanel, color: Int, code: Int) {
		var color = color
		if (color == -1)
			color = LaunchpadColor.ARGB[code]
		if (x != -1)
			btn[x][y][chanel.priority] = Item(chanel, color, code)
		else
			cir[y][chanel.priority] = Item(chanel, color, code)
	}

	fun remove(x: Int, y: Int, chanel: Chanel) {
		if (x != -1)
			btn[x][y][chanel.priority] = null
		else
			cir[y][chanel.priority] = null
	}

	fun setBtnIgnore(chanel: Chanel, ignore: Boolean) {
		btnIgnoreList[chanel.priority] = ignore
	}

	fun setCirIgnore(chanel: Chanel, ignore: Boolean) {
		cirIgnoreList[chanel.priority] = ignore
	}
}