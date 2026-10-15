package com.kimjisub.launchpad

import java.io.File

/**
 * A chain of folders under [parent] whose innermost folder lies beyond the kernel's path length
 * limit, so a recursive delete by path (the app's included) cannot list or remove it and the
 * chain stays. Unlike permission bits, which Android 10's emulated storage ignores, this holds on
 * every storage.
 *
 * Each level is added by renaming the chain built so far into a new short folder, so no single
 * call names a path over the limit; [remove] takes it apart the same way.
 */
class UnreachableFolder(parent: File) {
    private val top = File(parent, "unreachable")
    private val spare = File(parent, "unreachable_next")

    /** True when the chain was built and its innermost folder cannot be reached by path. */
    fun create(): Boolean {
        if (!top.mkdir()) return false
        var depth = 0
        while (top.absolutePath.length + depth * (LEVEL.length + 1) <= PATH_MAX) {
            if (!spare.mkdir() || !top.renameTo(File(spare, LEVEL)) || !spare.renameTo(top)) return false
            depth++
        }
        return !File(top.absolutePath + "/$LEVEL".repeat(depth)).exists()
    }

    /** True when nothing of the chain is left. */
    fun remove(): Boolean {
        while (File(top, LEVEL).renameTo(spare)) {
            top.delete()
            spare.renameTo(top)
        }
        top.delete()
        return !top.exists() && !spare.exists()
    }

    private companion object {
        /** Linux PATH_MAX, terminating NUL included. */
        const val PATH_MAX = 4096
        val LEVEL = "d".repeat(200)
    }
}
