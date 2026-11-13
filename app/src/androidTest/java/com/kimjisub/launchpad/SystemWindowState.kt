package com.kimjisub.launchpad

import androidx.test.uiautomator.UiDevice

/**
 * Reads the system's own state from `dumpsys`: which app's "isn't responding" dialog is on screen,
 * and whether the home app has finished redrawing after a configuration change. Waits that depend
 * on the system rather than on the app under test use these, so a failure can name the stalled
 * system app instead of looking like a failure of the app.
 */
internal object SystemWindowState {
    private val WINDOW_BLOCK = Regex("""\n(?=\s*Window #\d+ )""")
    private val NOT_RESPONDING_TITLE = Regex("""Window\{\w+ u\d+ Application Not Responding: ([^}\s]+)\}""")
    private val SYSTEM_OWNED = Regex("""\spackage=android(?:\s|$)""", RegexOption.MULTILINE)
    private val ACTIVITY_BLOCK = Regex("""\n(?=\s*\* )""")
    private val REPORTED_ASSETS = Regex("""mGlobalConfig=\{[^\n]*? as\.(\d+) """)
    private val CURRENT_ASSETS = Regex("""mGlobalConfiguration=\{[^\n]*? as\.(\d+) """)

    /**
     * The processes whose "isn't responding" dialog is visible. The system server owns that dialog
     * (package `android`) whichever app stalled.
     */
    fun notResponding(windows: String): List<String> =
        windows.split(WINDOW_BLOCK).mapNotNull { block ->
            val process = NOT_RESPONDING_TITLE.find(block.lineSequence().first())?.groupValues?.get(1)
            process?.takeIf { SYSTEM_OWNED.containsMatchIn(block) && "isVisible=true" in block }
        }.distinct()

    /** A sentence to append to a failure message; empty when no such dialog is visible. */
    fun describeNotResponding(device: UiDevice): String {
        val processes = runCatching { notResponding(device.executeShellCommand("dumpsys window windows")) }
            .getOrElse { return " (Could not read the system's windows: $it)" }
        return if (processes.isEmpty()) ""
        else " The system's \"isn't responding\" dialog is on screen for ${processes.joinToString()}."
    }

    /** The home activity as `package/.Activity` or `package/full.Activity`, or null if none resolves. */
    fun homeActivity(device: UiDevice): String? =
        device.executeShellCommand(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        ).lines().map(String::trim).lastOrNull { '/' in it && ' ' !in it }

    data class Home(val inFront: Boolean, val settled: Boolean, val detail: String)

    /**
     * Whether [home] is the focused app and, if so, whether it has settled: it has been sent the
     * system's current resources (an overlay change raises their sequence number, `as.N`), it is
     * resumed with no relaunch pending, all its windows are drawn, its own window holds focus, and
     * no "isn't responding" dialog is visible. [activities] is `dumpsys activity activities`, which
     * also names the focused app and window; [windows] is `dumpsys window windows`.
     */
    fun home(home: String, activities: String, windows: String): Home {
        val (pkg, cls) = home.split('/', limit = 2)
        val shortName = "$pkg/${cls.removePrefix(pkg)}"
        val fullName = "$pkg/${if (cls.startsWith('.')) pkg + cls else cls}"
        // An activity record prints as `{… u0 pkg/.Activity t8}`; Android 12 and 13 close the name
        // with a brace as well (`{… u0 pkg/.Activity} t8}`).
        fun names(text: String) = listOf(shortName, fullName).any { " $it " in text || " $it}" in text }
        val focusedApp = activities.lineSequence().firstOrNull { "mFocusedApp=" in it }?.trim().orEmpty()
        val inFront = names(focusedApp)
        if (!inFront) return Home(inFront = false, settled = false, detail = focusedApp.ifEmpty { "no focused app" })
        val record = activities.split(ACTIVITY_BLOCK).firstOrNull { block ->
            val title = block.lineSequence().first()
            title.trimStart().startsWith("* Hist") && names(title)
        }.orEmpty()
        val reportedAssets = REPORTED_ASSETS.find(record)?.groupValues?.get(1)
        val currentAssets = CURRENT_ASSETS.find(windows)?.groupValues?.get(1)
        val state = Regex("""^\s*state=(\w+)""", RegexOption.MULTILINE).find(record)?.groupValues?.get(1)
        val relaunching = "mPendingRelaunchCount=" in record
        val allDrawn = "allDrawn=true" in record
        val focus = activities.lineSequence().firstOrNull { "mCurrentFocus=" in it }?.trim().orEmpty()
        val focused = focus.endsWith("$fullName}")
        val window = windows.split(WINDOW_BLOCK).firstOrNull { it.lineSequence().first().trimEnd().endsWith("$fullName}:") }.orEmpty()
        val drawState = Regex("""mDrawState=(\w+)""").find(window)?.groupValues?.get(1)
        val notResponding = notResponding(windows)
        val settled = reportedAssets != null && reportedAssets == currentAssets && state == "RESUMED" && !relaunching && allDrawn && focused && drawState == "HAS_DRAWN" && notResponding.isEmpty()
        return Home(
            inFront = true,
            settled = settled,
            detail = "assets=$reportedAssets/$currentAssets state=$state relaunching=$relaunching allDrawn=$allDrawn drawState=$drawState $focus" +
                if (notResponding.isEmpty()) "" else " notResponding=$notResponding",
        )
    }
}
