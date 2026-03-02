package com.kimjisub.launchpad.manager

import android.graphics.drawable.Drawable

interface IThemeResources {
	val icon: Drawable
	val name: String
	val author: String
	val version: String

	val playbg: Drawable?
	val customLogo: Drawable?
	val btn: Drawable?
	val btnPressed: Drawable?
	val chainled: Drawable?
	val chain: Drawable?
	val chainSelected: Drawable?
	val chainGuide: Drawable?
	val phantom: Drawable?
	val phantomVariant: Drawable?
	val checkbox: Int?
	val traceLog: Int?
	val optionWindow: Int?
	val optionWindowCheckbox: Int?
	val isChainLed: Boolean
}
