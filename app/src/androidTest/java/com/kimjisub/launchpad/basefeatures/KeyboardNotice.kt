package com.kimjisub.launchpad.basefeatures

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** A keyboard window element, reduced to what recognising a keyboard notice needs. */
internal data class KeyboardElement(
    val bounds: Rect,
    val text: String = "",
    val description: String = "",
    val clickable: Boolean = false,
    val visible: Boolean = true,
    val viewId: String = "",
    val children: List<KeyboardElement> = emptyList(),
) {
    val label: String get() = text.ifEmpty { description }

    fun descendants(): Sequence<KeyboardElement> =
        sequenceOf(this) + children.asSequence().flatMap { it.descendants() }

    /** Elements from this one down to [target], or null when [target] is not inside it. */
    fun pathTo(target: KeyboardElement): List<KeyboardElement>? =
        if (this === target) listOf(this)
        else children.firstNotNullOfOrNull { it.pathTo(target) }?.let { listOf(this) + it }

    companion object {
        fun of(node: AccessibilityNodeInfo): KeyboardElement = KeyboardElement(
            bounds = Rect().also(node::getBoundsInScreen),
            text = node.text?.toString().orEmpty(),
            description = node.contentDescription?.toString().orEmpty(),
            clickable = node.isClickable,
            visible = node.isVisibleToUser,
            viewId = node.viewIdResourceName.orEmpty(),
            children = (0 until node.childCount).mapNotNull(node::getChild).map(::of),
        )
    }
}

/**
 * What Gboard shows above its suggestion row. After the system font size changes, Gboard shows
 * "Keyboard font size updated" with an OK button there until it is closed; the strip raises the
 * keyboard by more than a row of keys. Only that notice is recognised: anything else above the
 * suggestion row is reported as [Other] and left as it is.
 */
internal sealed interface KeyboardNotice {
    /** Nothing above the suggestion row, or no Gboard suggestion row to measure from. */
    data object None : KeyboardNotice

    /** The font size notice, closed by pressing [close]. */
    data class FontSize(val close: KeyboardElement) : KeyboardNotice

    /** The font size notice without exactly one close button beside its message. */
    data class FontSizeUnclosable(val labels: List<String>) : KeyboardNotice

    /** Something other than the font size notice above the suggestion row. */
    data class Other(val labels: List<String>) : KeyboardNotice

    companion object {
        const val GBOARD_HEADER = "com.google.android.inputmethod.latin:id/keyboard_header_view_holder"
        // Gboard's English message and the description of the notice's OK button.
        const val FONT_SIZE_MESSAGE = "Keyboard font size updated"
        const val CLOSE = "Close the banner."

        fun of(keyboard: KeyboardElement): KeyboardNotice {
            val header = keyboard.descendants().firstOrNull { it.viewId == GBOARD_HEADER } ?: return None
            val above = keyboard.descendants()
                .filter { it.visible && it.bounds.bottom <= header.bounds.top }
                .toList()
            val labels = above.filter { it.clickable || it.label.isNotEmpty() }.map { it.label }
            val message = above.firstOrNull { it.text == FONT_SIZE_MESSAGE }
                ?: return if (labels.isEmpty()) None else Other(labels)
            // The smallest element holding the message and a close button on the message's row is
            // the notice; a close button on another row belongs to something else.
            val close = keyboard.pathTo(message)!!.asReversed().asSequence()
                .map { element -> element.closeButtonsBeside(message) }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
            return close.singleOrNull()?.let(::FontSize) ?: FontSizeUnclosable(labels)
        }

        private fun KeyboardElement.closeButtonsBeside(message: KeyboardElement) = descendants()
            .filter { it.clickable && it.visible && it.description == CLOSE }
            .filter { it.bounds.top < message.bounds.bottom && message.bounds.top < it.bounds.bottom }
            .toList()
    }
}
