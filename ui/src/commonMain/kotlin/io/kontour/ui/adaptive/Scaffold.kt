package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.clearFocusOnTap
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Theme

/** Which corner a [Scaffold]'s floating action button sits in. */
enum class FabPosition { Start, Centre, End }

/** What a [Scaffold] takes by default. */
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
 *     topBar = { TopBar { +"Favourites" } },
 *     floatingActionButton = { FloatingActionButton(icon = Tabler.Outline.Plus, contentDescription = "Add", onClick = ::add) },
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
 * [windowInsets] defaults to `safeDrawing`. The padding handed to the
 * content is the **larger** of the bar and the inset on each edge, not their
 * sum — a top bar already sits under the status bar and has padded itself for
 * it, so adding both insets the content twice. `ScaffoldGeometryTest` measures
 * a real scaffold for exactly this, because a double inset looks like a
 * slightly generous gap rather than a bug.
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
    containerColour: Color = Theme.colours.background,
    contentColour: Color = Theme.colours.content,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    /**
     * Whether pressing the screen somewhere nothing wanted takes focus off the
     * field that has it.
     *
     * On, because "I tapped away from the text box and the keyboard stayed up"
     * is the behaviour nobody asks for. `OverlayHost` has carried this rule for
     * a while and is bounded by its own box, so an app whose root is a
     * `Scaffold` and not a host had it nowhere — see
     * [io.kontour.ui.input.clearFocusOnTap], which is the rule itself and can go
     * on any root.
     *
     * Off for a screen that manages focus itself.
     */
    clearFocusOnTap: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val fabMargin = ScaffoldDefaults.FabMargin
    // **Nothing here is read in composition**, and that is the whole of what this
    // block is about. The insets and the bars' heights are read in measure and
    // handed to the content through one padding that does not change identity,
    // so a keyboard sliding up, or a large top bar collapsing as the list under
    // it scrolls, re-measures the content and recomposes nothing. Read here and
    // handed out as a fresh `PaddingValues`, both recomposed the whole screen on
    // every frame of them — and a top bar that collapses is one the top bar's own
    // KDoc puts in a scaffold. The slots are remembered for the same reason: a
    // new lambda handed to `subcompose` is a slot it composes again.
    val padding = remember { ScaffoldPadding() }
    val currentTop by rememberUpdatedState(topBar)
    val currentBottom by rememberUpdatedState(bottomBar)
    val currentFab by rememberUpdatedState(floatingActionButton)
    val currentContent by rememberUpdatedState(content)
    val topSlot: @Composable () -> Unit = remember { { currentTop?.invoke() } }
    val bottomSlot: @Composable () -> Unit = remember { { currentBottom?.invoke() } }
    val fabSlot: @Composable () -> Unit = remember { { currentFab?.invoke() } }
    val contentSlot: @Composable () -> Unit = remember(padding) { { currentContent(padding) } }

    Surface(
        modifier = modifier.fillMaxSize().clearFocusOnTap(clearFocusOnTap),
        containerColour = containerColour,
        contentColour = contentColour,
    ) {
        SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val insetTop = windowInsets.getTop(this)
            val insetBottom = windowInsets.getBottom(this)
            val insetLeft = windowInsets.getLeft(this, layoutDirection)
            val insetRight = windowInsets.getRight(this, layoutDirection)
            val ltr = layoutDirection == LayoutDirection.Ltr
            val insetStart = if (ltr) insetLeft else insetRight
            val insetEnd = if (ltr) insetRight else insetLeft

            val top = subcompose(ScaffoldSlot.Top, topSlot).map { it.measure(loose) }
            val bottom = subcompose(ScaffoldSlot.Bottom, bottomSlot).map { it.measure(loose) }
            val fab = subcompose(ScaffoldSlot.Fab, fabSlot).map { it.measure(loose) }

            val topHeight = top.maxOfOrNull { it.height } ?: 0
            val bottomHeight = bottom.maxOfOrNull { it.height } ?: 0

            padding.start = insetStart.toDp()
            // The larger, not the sum: a bar has already padded itself for the
            // inset it sits under.
            padding.top = maxOf(topHeight, insetTop).toDp()
            padding.end = insetEnd.toDp()
            padding.bottom = maxOf(bottomHeight, insetBottom).toDp()

            val body = subcompose(ScaffoldSlot.Content, contentSlot).map { it.measure(constraints) }

            layout(width, height) {
                // Content first, so the bars draw over it — which is what makes
                // a translucent bar possible at all.
                body.forEach { it.place(0, 0) }
                top.forEach { it.place(0, 0) }
                bottom.forEach { it.place(0, height - it.height) }

                fab.forEach { placeable ->
                    val marginPx = fabMargin.roundToPx()
                    val x = when (fabPosition) {
                        FabPosition.Start -> insetStart + marginPx
                        FabPosition.Centre -> (width - placeable.width) / 2
                        FabPosition.End -> width - placeable.width - insetEnd - marginPx
                    }
                    // Above the bottom bar if there is one, above the inset if
                    // there is not. A FAB behind the navigation bar is a FAB
                    // nobody can press.
                    val bottomClearance = maxOf(bottomHeight, insetBottom)
                    // Relative: `End` is the end of the line, which is the left in right-to-left.
                    placeable.placeRelative(x, height - placeable.height - bottomClearance - marginPx)
                }
            }
        }
    }
}

/**
 * The padding a [Scaffold] hands its content: one object for the scaffold's life,
 * whose sides are written in its measure. A list that takes it as
 * `contentPadding` reads them in its own measure, so a change re-measures it
 * without recomposing anything; content that reads them in composition is
 * recomposed when they change, a frame later.
 */
@Stable
private class ScaffoldPadding : PaddingValues {
    var start by mutableStateOf(0.dp)
    var top by mutableStateOf(0.dp)
    var end by mutableStateOf(0.dp)
    var bottom by mutableStateOf(0.dp)

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        if (layoutDirection == LayoutDirection.Ltr) start else end

    override fun calculateTopPadding(): Dp = top

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        if (layoutDirection == LayoutDirection.Ltr) end else start

    override fun calculateBottomPadding(): Dp = bottom

    override fun toString(): String = "ScaffoldPadding(start=$start, top=$top, end=$end, bottom=$bottom)"
}

private enum class ScaffoldSlot { Top, Bottom, Fab, Content }
