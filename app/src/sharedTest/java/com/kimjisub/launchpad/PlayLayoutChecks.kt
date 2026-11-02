package com.kimjisub.launchpad

import kotlin.math.abs

/** Screen coordinates shared by the device observation and its device-free rule checks. */
internal data class PlayLayoutRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centreX: Float get() = (left.toFloat() + right) / 2f
}

internal fun padCentreProblem(
    window: PlayLayoutRect,
    safe: PlayLayoutRect,
    content: PlayLayoutRect,
    pads: PlayLayoutRect,
    density: Float,
    toleranceDp: Float,
): String? {
    // Each content edge may consume its system inset or draw through it. Extra app padding
    // and translations cannot redefine the reference centre, even when they are symmetric.
    val edges = listOf(
        Triple(content.left, window.left, safe.left), Triple(content.top, window.top, safe.top),
        Triple(content.right, window.right, safe.right), Triple(content.bottom, window.bottom, safe.bottom),
    )
    if (content.left >= content.right || content.top >= content.bottom ||
        content.left < window.left || content.top < window.top ||
        content.right > window.right || content.bottom > window.bottom ||
        edges.any { (edge, windowEdge, safeEdge) -> edge != windowEdge && edge != safeEdge }
    ) {
        val insetDp = listOf(content.left - window.left, content.top - window.top,
            window.right - content.right, window.bottom - content.bottom).map { it / density }
        return "content $content differs from window $window by $insetDp dp (left/top/right/bottom), " +
            "beyond system bar/cutout boundaries $safe"
    }
    val offContentDp = abs(pads.centreX - content.centreX) / density
    val offWindowDp = abs(pads.centreX - window.centreX) / density
    return if (offContentDp <= toleranceDp) null else
        "pad grid is ${offContentDp}dp off the content centre line (${offWindowDp}dp off the window centre line)"
}

/** Only the explicit unknown-command response permits treating an orientation as unverified. */
internal fun orientationOverrideSupported(response: String): Boolean {
    val result = response.trim()
    val unsupported = result == "Unknown command: set-ignore-orientation-request"
    check(result.isEmpty() || unsupported) { "Could not enable orientation override: $result" }
    return !unsupported
}
