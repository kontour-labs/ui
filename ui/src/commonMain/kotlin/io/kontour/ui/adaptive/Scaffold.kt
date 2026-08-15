package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Theme

/** Which corner a [Scaffold]'s floating action button sits in. */
enum class FabPosition { Start, Center, End }

object ScaffoldDefaults {
    /** Gap between the FAB and the edges, or the bottom bar. */
    val FabMargin: Dp = 16.dp
}

/**
 * A screen's frame: a top bar, a bottom bar, a floating action button, and the
 * content between them.
 *
 * ```kotlin
 * Scaffold(
 *     topBar = { TopBar() {
                      +"Favourites"
                  } },
 *     floatingActionButton = { FloatingActionButton(Tabler.Outline.Plus, "Add", ::add) },
 * ) { padding ->
 *     LazyColumn(contentPadding = padding) { … }
 * }
 * ```
 *
 * The content is given the **whole** area and handed the padding it should inset
 * by, rather than being squeezed into the gap between the bars. That is what lets
 * a list scroll *under* a translucent bar rather than stopping at its edge — and
 * it is why the padding is a parameter rather than something applied for you: a
 * `LazyColumn` wants it as `contentPadding`, a `Column` wants it as `padding`,
 * and applying it to the wrong one clips the scroll.
 *
 * ### Insets
 *
 * [contentWindowInsets] defaults to `safeDrawing`, covering the status bar, the
 * navigation bar, the display cutout and the keyboard. The padding handed to the
 * content is the **larger** of the bar and the inset on each edge, not their sum
 * — a top bar already sits under the status bar and has padded itself for it, so
 * adding both would inset the content twice.
 *
 * Anything drawn edge to edge behind the bars is the app's business: this hands
 * out measurements, it does not paint.
 */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    fabPosition: FabPosition = FabPosition.End,
    containerColor: Color = Theme.colors.background,
    contentColor: Color = Theme.colors.content,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val insets = contentWindowInsets.asPaddingValues()
    val insetTop = insets.calculateTopPadding()
    val insetBottom = insets.calculateBottomPadding()
    val insetStart = insets.calculateStartPadding(direction)
    val insetEnd = insets.calculateEndPadding(direction)
    val fabMargin = ScaffoldDefaults.FabMargin

    Surface(modifier = modifier.fillMaxSize(), color = containerColor, contentColor = contentColor) {
        SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val loose = constraints.copy(minWidth = 0, minHeight = 0)

            val top = subcompose(ScaffoldSlot.Top) { topBar?.invoke() }.map { it.measure(loose) }
            val bottom = subcompose(ScaffoldSlot.Bottom) { bottomBar?.invoke() }
                .map { it.measure(loose) }
            val fab = subcompose(ScaffoldSlot.Fab) { floatingActionButton?.invoke() }
                .map { it.measure(loose) }

            val topHeight = top.maxOfOrNull { it.height } ?: 0
            val bottomHeight = bottom.maxOfOrNull { it.height } ?: 0

            val padding = PaddingValues(
                start = insetStart,
                // The larger, not the sum: a bar has already padded itself for
                // the inset it sits under.
                top = maxOf(topHeight.toDp(), insetTop),
                end = insetEnd,
                bottom = maxOf(bottomHeight.toDp(), insetBottom),
            )

            val body = subcompose(ScaffoldSlot.Content) { content(padding) }
                .map { it.measure(constraints) }

            layout(width, height) {
                // Content first, so the bars draw over it — which is what makes
                // a translucent bar possible at all.
                body.forEach { it.place(0, 0) }
                top.forEach { it.place(0, 0) }
                bottom.forEach { it.place(0, height - it.height) }

                fab.forEach { placeable ->
                    val marginPx = fabMargin.roundToPx()
                    val x = when (fabPosition) {
                        FabPosition.Start -> insetStart.roundToPx() + marginPx
                        FabPosition.Center -> (width - placeable.width) / 2
                        FabPosition.End -> width - placeable.width -
                            insetEnd.roundToPx() - marginPx
                    }
                    // Above the bottom bar if there is one, above the inset if
                    // there is not. A FAB behind the navigation bar is a FAB
                    // nobody can press.
                    val bottomClearance = maxOf(bottomHeight, insetBottom.roundToPx())
                    placeable.place(x, height - placeable.height - bottomClearance - marginPx)
                }
            }
        }
    }
}

private enum class ScaffoldSlot { Top, Bottom, Fab, Content }
