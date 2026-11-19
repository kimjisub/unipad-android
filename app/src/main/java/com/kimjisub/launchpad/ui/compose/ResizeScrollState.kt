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
		snapshotFlow { ScrollPosition(viewport, state.maxValue, state.value) }.collect { position ->
			if (position.viewport.height <= 0) return@collect
			if (position.viewport != previousViewport || position.max != previousMax) {
				state.scrollTo(requested)
			} else if (position.value != previousValue) {
				requested = position.value
			}
			previousViewport = position.viewport
			previousMax = position.max
			// The restored value may be clamped. Do not replace the requested position with it.
			previousValue = state.value
		}
	}
	return onSizeChanged { viewport = it }.verticalScroll(state)
}

private data class ScrollPosition(val viewport: IntSize, val max: Int, val value: Int)

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
		snapshotFlow {
			ListPosition(state.layoutInfo.viewportSize, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
		}.collect { position ->
			if (position.viewport.height <= 0) return@collect
			if (position.viewport != previousViewport) {
				state.scrollToItem(requestedIndex, requestedOffset)
			} else if (position.index != previousIndex || position.offset != previousOffset) {
				requestedIndex = position.index
				requestedOffset = position.offset
			}
			previousViewport = position.viewport
			previousIndex = state.firstVisibleItemIndex
			previousOffset = state.firstVisibleItemScrollOffset
		}
	}
	return state
}

private data class ListPosition(val viewport: IntSize, val index: Int, val offset: Int)
