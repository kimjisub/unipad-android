package com.kimjisub.launchpad

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The items a screen reader steps through when the person swipes forward, and what it says for each,
 * read from the accessibility tree of the active window.
 *
 * Follows TalkBack's rules: an item is a visible node that can be acted on or that Compose marks as
 * screen-reader focusable, or a node with something to say that no such item already speaks for.
 * Its name is its content description, else its text, else what its unfocusable descendants say,
 * each followed by its state. The order is the tree order, rearranged by each node's traversal-before
 * link, which Compose sets to the next item in reading order.
 */
object ScreenReaderOrder {
    /** [tag] is the node's Compose test tag or view id, if it has one. */
    data class Stop(val name: String, val actionable: Boolean, val tag: String?, val bounds: Rect) {
        override fun toString() = "\"$name\"${if (actionable) " (action)" else ""} ${tag ?: ""} $bounds"
    }

    fun read(timeoutMs: Long = 5000L): List<Stop> {
        val nodes = items(activeRoot(timeoutMs))
        return ordered(nodes).map { node ->
            Stop(spokenName(node), isActionable(node), node.viewIdResourceName, Rect().also(node::getBoundsInScreen))
        }
    }

    private fun activeRoot(timeoutMs: Long): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            automation.rootInActiveWindow?.let { return it }
            check(SystemClock.uptimeMillis() < deadline) { "No active window to read" }
            SystemClock.sleep(100)
        }
    }

    private fun items(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo, speakingAncestor: Boolean) {
            if (!node.isVisibleToUser) return
            val item = if (isFocusable(node)) true else !speakingAncestor && hasOwnSpeech(node)
            if (item) found += node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { visit(it, speakingAncestor || isFocusable(node)) }
            }
        }
        visit(root, speakingAncestor = false)
        return found
    }

    /** Each traversal-before chain starts where the tree order first reaches it. */
    private fun ordered(nodes: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        val next = nodes.associateWith { node -> node.traversalBefore?.let { target -> nodes.find { it == target } } }
        val linked = next.values.filterNotNull().toSet()
        val result = mutableListOf<AccessibilityNodeInfo>()
        for (head in nodes.filter { it !in linked }) {
            var current: AccessibilityNodeInfo? = head
            while (current != null && current !in result) {
                result += current
                current = next[current]
            }
        }
        nodes.filter { it !in result }.forEach { result += it }
        return result
    }

    private fun isActionable(node: AccessibilityNodeInfo) =
        node.isClickable || node.isLongClickable || node.isFocusable ||
            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK || it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }

    private fun isFocusable(node: AccessibilityNodeInfo) = isActionable(node) || node.isScreenReaderFocusable

    private fun hasOwnSpeech(node: AccessibilityNodeInfo) =
        !node.contentDescription.isNullOrBlank() || !node.text.isNullOrBlank() || !node.stateDescription.isNullOrBlank()

    private fun spokenName(node: AccessibilityNodeInfo): String {
        val own = node.contentDescription?.takeIf { it.isNotBlank() } ?: node.text?.takeIf { it.isNotBlank() }
        val parts = mutableListOf<CharSequence>()
        if (own != null) parts += own else collectDescendantSpeech(node, parts)
        node.stateDescription?.takeIf { it.isNotBlank() }?.let { parts += it }
        return parts.joinToString(", ")
    }

    private fun collectDescendantSpeech(node: AccessibilityNodeInfo, into: MutableList<CharSequence>) {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (!child.isVisibleToUser || isFocusable(child)) continue
            val own = child.contentDescription?.takeIf { it.isNotBlank() } ?: child.text?.takeIf { it.isNotBlank() }
            if (own != null) into += own else collectDescendantSpeech(child, into)
            child.stateDescription?.takeIf { it.isNotBlank() }?.let { into += it }
        }
    }
}
