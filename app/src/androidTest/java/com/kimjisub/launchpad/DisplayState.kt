package com.kimjisub.launchpad

import android.graphics.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/**
 * What the display check reads. It also fails when no node matches, so this says whether the node
 * still exists, then lists each ancestor's placement, window bounds and scroll, the host view and
 * every node under [panelTag].
 */
internal fun displayState(compose: ComposeTestRule, node: SemanticsNodeInteraction, panelTag: String): String {
    val clock = compose.mainClock.currentTime
    val semantics = runCatching { node.fetchSemanticsNode() }
    val panelRoot = runCatching { compose.onNode(hasTestTag(panelTag), useUnmergedTree = true).fetchSemanticsNode() }
    // Without a matching node, the panel's host view is the one the check would have read.
    val view = (semantics.getOrNull() ?: panelRoot.getOrNull())?.let { (it.root as? ViewRootForTest)?.view }
    val visible = Rect()
    var viewState = ""
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        viewState = "view=${view?.width}x${view?.height} shown=${view?.isShown} " +
            "visible=${view?.getGlobalVisibleRect(visible)} $visible attached=${view?.isAttachedToWindow}"
    }
    fun describe(n: SemanticsNode) =
        "  #${n.id} placed=${n.layoutInfo.isPlaced} attached=${n.layoutInfo.isAttached} " +
            "window=${n.boundsInWindow} size=${n.size} " +
            "scroll=${n.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                ?.let { "${it.value()}/${it.maxValue()}" }} tag=${n.config.getOrNull(SemanticsProperties.TestTag)} " +
            "text=${n.config.getOrNull(SemanticsProperties.Text)} " +
            "desc=${n.config.getOrNull(SemanticsProperties.ContentDescription)}"
    val chain = semantics.fold(
        { generateSequence(it) { n -> n.parent }.joinToString("\n", transform = ::describe) },
        { "  matching node not found now: ${it.message?.lineSequence()?.firstOrNull()}" },
    )
    val panel = panelRoot.fold(
        { root -> generateSequence(listOf(root)) { level -> level.flatMap { it.children }.ifEmpty { null } }
            .flatten().joinToString("\n", transform = ::describe) },
        { "  $panelTag not found: ${it.message?.lineSequence()?.firstOrNull()}" },
    )
    return "clockBefore=$clock clockAfter=${compose.mainClock.currentTime} $viewState\n" +
        "node and ancestors:\n$chain\n$panelTag:\n$panel"
}
