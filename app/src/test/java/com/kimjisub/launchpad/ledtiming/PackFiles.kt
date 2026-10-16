package com.kimjisub.launchpad.ledtiming

import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import java.io.File

/** Writes a one-chain pack with [keyLed] files (named "chain x y loop") and an [autoPlay] script. */
fun writePack(root: File, buttons: Int, keyLed: Map<String, String>, autoPlay: String): File {
	root.mkdirs()
	File(root, "info").writeText("title=LED timing\nproducerName=Test\nbuttonX=$buttons\nbuttonY=$buttons\nchain=1\n")
	File(root, "keySound").writeText("1 1 1 a.wav\n")
	File(root, "sounds").mkdir()
	File(root, "sounds/a.wav").writeText("")
	File(root, "keyLed").mkdir()
	for ((name, text) in keyLed) File(root, "keyLed/$name").writeText(text)
	File(root, "autoPlay").writeText(autoPlay)
	return root
}

fun loadPack(root: File): UniPack = UniPackFolder(root).load().loadDetail()
