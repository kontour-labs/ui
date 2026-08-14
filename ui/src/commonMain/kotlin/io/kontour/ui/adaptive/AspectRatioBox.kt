package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Holds a fixed aspect ratio while its width is decided by the layout.
 *
 * ```kotlin
 * AspectRatioBox(16f / 9f) { RouteMap() }
 * ```
 *
 * Thin, but it exists because the alternative — `Modifier.aspectRatio` applied
 * at each call site — reliably gets applied to the wrong node. On the *content*
 * it constrains the content and the container still collapses; on the
 * container after `fillMaxWidth` it works; before it, it does not. Wrapping the
 * order once beats rediscovering it.
 *
 * The main use is a media slot whose content has not loaded yet. Reserving the
 * space up front is what stops the page reflowing under the user's finger when
 * the image arrives.
 */
@Composable
fun AspectRatioBox(
    ratio: Float,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    matchHeightConstraintsFirst: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio, matchHeightConstraintsFirst),
        contentAlignment = contentAlignment,
        content = content,
    )
}
