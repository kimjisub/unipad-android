package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Keep the requested position when a larger viewport temporarily clamps the actual scroll. */
@Composable
fun Modifier.resizeVerticalScroll(): Modifier {
	val state = rememberScrollState()
	var viewport by remember { mutableStateOf(IntSize.Zero) }
	var requested by rememberSaveable { mutableIntStateOf(state.value) }
	LaunchedEffect(state) {
		var previousViewport = IntSize.Zero
		var previousMax = state.maxValue
		var previousValue = state.value
		var restorePending = false
		var clampedByViewport = false
		snapshotFlow { ScrollPosition(viewport, state.maxValue, state.value, state.isScrollInProgress) }.collect { position ->
			if (position.viewport.height <= 0) return@collect
			val resized = position.viewport != previousViewport
			val rangeChanged = position.max != previousMax
			if (resized) {
				restorePending = true
				clampedByViewport = position.value < requested && position.value == position.max
				if (position.scrolling && !clampedByViewport) {
					// Movement during resizing belongs to the user, not to an old restore request.
					requested = position.value
					restorePending = false
				}
			} else if (position.value != previousValue &&
				(!rangeChanged || (!clampedByViewport && !restorePending) || position.value > previousValue)) {
				// Content-only clamping becomes the new position. Growing content must not jump back.
				requested = position.value
				clampedByViewport = false
				restorePending = false
			}
			var observedValue = position.value
			if (restorePending && !position.scrolling && !state.isScrollInProgress) {
				val restored = position.value == requested || tryRestoreScroll { state.scrollTo(requested) }
				if (restored) {
					restorePending = false
					clampedByViewport = state.value != requested
					observedValue = state.value
				}
			}
			previousViewport = position.viewport
			previousMax = position.max
			previousValue = observedValue
		}
	}
	return onSizeChanged { viewport = it }.verticalScroll(state)
}

private data class ScrollPosition(val viewport: IntSize, val max: Int, val value: Int, val scrolling: Boolean)

/** Retain the first visible row and its offset, even when all rows fit after resizing. */
@Composable
fun rememberResizeLazyListState(resetKey: Any? = null): LazyListState {
	val state = rememberLazyListState()
	var requestedIndex by rememberSaveable(resetKey) { mutableIntStateOf(0) }
	var requestedOffset by rememberSaveable(resetKey) { mutableIntStateOf(0) }
	LaunchedEffect(state, resetKey) {
		var previousViewport = IntSize.Zero
		var previousIndex = state.firstVisibleItemIndex
		var previousOffset = state.firstVisibleItemScrollOffset
		var restorePending = false
		var resetPending = resetKey != null
		snapshotFlow {
			ListPosition(state.layoutInfo.viewportSize, state.firstVisibleItemIndex,
				state.firstVisibleItemScrollOffset, state.isScrollInProgress, state.canScrollForward)
		}.collect { position ->
			if (position.viewport.height <= 0) return@collect
			val resized = position.viewport != previousViewport
			if (resized) restorePending = true
			if (!resetPending) {
				if (resized && position.scrolling) {
					val clamped = !position.canScrollForward &&
						(position.index < requestedIndex || position.index == requestedIndex && position.offset < requestedOffset)
					if (!clamped) {
						requestedIndex = position.index
						requestedOffset = position.offset
						restorePending = false
					}
				} else if (!resized && (position.index != previousIndex || position.offset != previousOffset)) {
					requestedIndex = position.index
					requestedOffset = position.offset
					restorePending = false
				}
			}
			var observedIndex = position.index
			var observedOffset = position.offset
			if (restorePending && !position.scrolling && !state.isScrollInProgress) {
				val restored = position.index == requestedIndex && position.offset == requestedOffset ||
					tryRestoreScroll { state.scrollToItem(requestedIndex, requestedOffset) }
				if (restored) {
					restorePending = false
					resetPending = false
					observedIndex = state.firstVisibleItemIndex
					observedOffset = state.firstVisibleItemScrollOffset
				}
			}
			previousViewport = position.viewport
			previousIndex = observedIndex
			previousOffset = observedOffset
		}
	}
	return state
}

private data class ListPosition(
	val viewport: IntSize,
	val index: Int,
	val offset: Int,
	val scrolling: Boolean,
	val canScrollForward: Boolean,
)

/** A competing gesture can cancel only the scroll mutation; keep observing until it ends. */
private suspend fun tryRestoreScroll(restore: suspend () -> Unit): Boolean = try {
	restore()
	true
} catch (_: CancellationException) {
	// Disposal of the owning effect must still cancel it normally.
	currentCoroutineContext().ensureActive()
	false
}
