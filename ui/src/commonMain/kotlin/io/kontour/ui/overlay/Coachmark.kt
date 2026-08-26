package io.kontour.ui.overlay

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/** Sizing for a [CoachmarkTour]'s spotlight. */
object CoachmarkDefaults {

    /** How far the lit area reaches past the control it is lighting. */
    val SpotlightPadding: Dp = 8.dp

    /**
     * The lit area's shape.
     *
     * `medium` rather than a radius of its own, so the hole around a control is
     * cut with the same corner the rest of the library draws — including the
     * smoothing, which a `CornerRadius` on a `RoundRect` cannot carry.
     */
    val SpotlightShape: CornerBasedShape
        @Composable get() = Theme.shapes.container
}

/**
 * An ordered walk through several [Modifier.coachmarkStep]s.
 *
 * ```
 * val tour = rememberCoachmarkTour("plan", "save", "alerts")
 *
 * IconButton(
 *     icon = Tabler.Outline.Bookmark,
 *     contentDescription = "Save this trip",
 *     onClick = ::save,
 *     modifier = Modifier.coachmarkStep(
 *         tour = tour,
 *         id = "save",
 *         title = "Save this trip",
 *         text = "Saved trips show up on the home screen.",
 *     ),
 * )
 * ```
 *
 * The tour holds only the **order**; each step's words live at the control they
 * describe, which is the only place they can be kept honest when that control
 * changes. Start it with [start] — from a "show me around" button, or once on
 * first run — and it walks itself.
 *
 * ### Not the same thing as [Modifier.coachMark]
 *
 * That one is a single tip, fired by an [OverlayQueue] when the app judges the
 * moment right, and it deliberately leaves the interface undimmed: the user is
 * being shown a thing *in* the screen and the screen has to stay legible around
 * it. A tour is the opposite bargain. The user asked for it, it is going to take
 * over for a few seconds, and dimming everything but the current control is what
 * makes "this one, here" unmistakable.
 *
 * | | `Modifier.coachMark` | `CoachmarkTour` |
 * |---|---|---|
 * | Who starts it | the app, through a queue | the user |
 * | How many | one | several, in order |
 * | The rest of the screen | untouched | dimmed, with a hole |
 * | Can be ignored | yes | it is modal |
 */
@Stable
class CoachmarkTour internal constructor(internal val order: List<String>) {

    /**
     * Which step is showing, or `-1` when the tour is not running.
     *
     * A nullable current step would be the obvious shape and would lose
     * something: a tour that has *finished* and one that has not *started* are
     * different states to an app that wants to offer it again, and both are
     * "nothing showing".
     */
    var index: Int by mutableStateOf(NotRunning)
        private set

    /** True once the last step has been passed, false before the tour is started. */
    var isFinished: Boolean by mutableStateOf(false)
        private set

    val isRunning: Boolean get() = index in order.indices

    /** The step now showing, or null. */
    val current: String? get() = order.getOrNull(index)

    /** Human-readable position, for a "2 of 4" in the bubble. */
    val position: Int get() = index + 1

    val count: Int get() = order.size

    val hasPrevious: Boolean get() = index > 0

    val hasNext: Boolean get() = index < order.lastIndex

    fun start() {
        if (order.isEmpty()) return
        isFinished = false
        index = 0
    }

    fun next() {
        if (hasNext) index++ else finish()
    }

    fun previous() {
        if (hasPrevious) index--
    }

    /** Ends the tour, whether it was walked through or skipped. */
    fun finish() {
        index = NotRunning
        isFinished = true
    }

    internal fun isShowing(id: String): Boolean = current == id

    private companion object {
        const val NotRunning = -1
    }
}

/**
 * A [CoachmarkTour] over these step ids, in this order.
 *
 * Ids rather than the steps themselves, so a step whose control is not on screen
 * simply shows nothing rather than pointing at a rectangle that is not there.
 */
@Composable
fun rememberCoachmarkTour(vararg steps: String): CoachmarkTour =
    remember(steps.toList()) { CoachmarkTour(steps.toList()) }

/**
 * Marks this control as one stop on [tour], and explains it when its turn comes.
 *
 * The modifier both **measures** the control — so the spotlight knows what to
 * cut a hole around — and **renders** the step. Keeping the two together is what
 * lets a tour be declared at the controls it is about rather than in a list
 * somewhere else that has to name them all again.
 *
 * @param id Matches one of the ids given to [rememberCoachmarkTour]. An id no
 *   step uses simply never shows.
 * @param padding How far the lit area reaches past the control. A little, so the
 *   hole reads as being *about* the control rather than as a window near it.
 * @param side Which way the bubble prefers to sit. Flipped automatically when
 *   there is no room.
 */
@Composable
fun Modifier.coachmarkStep(
    tour: CoachmarkTour,
    id: String,
    title: String,
    text: String,
    icon: ImageVector? = null,
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Center,
    padding: Dp = CoachmarkDefaults.SpotlightPadding,
    shape: CornerBasedShape = CoachmarkDefaults.SpotlightShape,
    enabled: Boolean = true,
): Modifier {
    var bounds by remember { mutableStateOf<Rect?>(null) }

    CoachmarkSpotlight(
        visible = enabled && tour.isShowing(id),
        anchor = bounds,
        title = title,
        text = text,
        icon = icon,
        side = side,
        alignment = alignment,
        padding = padding,
        shape = shape,
        tour = tour,
    )

    return this.anchorBounds { bounds = it }
}

/**
 * The dim, the hole in it, and the bubble.
 *
 * Rendered as a single overlay entry at [OverlayLayer.Tooltip] with the host's
 * own scrim switched off, because the host draws a plain rectangle and this one
 * has a hole in it.
 */
@Composable
private fun CoachmarkSpotlight(
    visible: Boolean,
    anchor: Rect?,
    title: String,
    text: String,
    icon: ImageVector?,
    side: OverlaySide,
    alignment: OverlayAlignment,
    padding: Dp,
    shape: CornerBasedShape,
    tour: CoachmarkTour,
) {
    val host = LocalOverlayHost.current
    val colors = Theme.colors
    val key = remember { Any() }
    val dismissLabel = Theme.strings.gotIt
    val latestAnchor by rememberUpdatedState(anchor)
    val latestTour by rememberUpdatedState(tour)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(visible, anchor != null, title, text, side, alignment) {
        if (!visible || anchor == null) {
            host.hide(key)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Tooltip,
                // The entry draws its own, below. The host's is a full-window
                // rectangle and the whole point here is the part that is not
                // covered.
                scrim = ScrimStyle.None,
                dismissLabel = dismissLabel,
                onDismiss = { latestTour.finish() },
                // A tour is modal — it is the one coach mark the user cannot
                // work around, because they asked for it.
                trapFocus = true,
                content = {
                    Spotlight(
                        anchorInRoot = { latestAnchor },
                        padding = padding,
                        shape = shape,
                        onDismissRequest = { latestTour.finish() },
                    )
                    AnchoredOverlayLayout(
                        anchorInRoot = { latestAnchor },
                        fromScale = 0.85f,
                        side = side,
                        alignment = alignment,
                        // Clear of the lit area rather than of the control, so
                        // the bubble does not sit on top of the thing it is
                        // pointing at.
                        gap = padding + Theme.spacing.xxs,
                        margin = MenuDefaults.ScreenMargin,
                        arrow = ArrowSpec(color = colors.accent.solid),
                    ) {
                        CoachmarkBubble(
                            title = title,
                            text = text,
                            icon = icon,
                            tour = latestTour,
                        )
                    }
                },
            )
        )
    }
}

/**
 * The dim, with a hole cut in it.
 *
 * ### An even-odd path, not a blend mode
 *
 * The obvious way to punch a hole is to draw the dim, then draw the hole in
 * `BlendMode.Clear` — which needs the whole thing in an offscreen buffer, since
 * clearing against the window itself would erase the app. A path with two
 * contours and [PathFillType.EvenOdd] gets the same shape in one `drawPath`,
 * with no buffer to allocate every frame and no blend mode to be unsupported on
 * a backend. The outer contour is the window, the inner one is the control, and
 * even-odd means the region inside both is outside the fill.
 */
@Composable
private fun Spotlight(
    anchorInRoot: () -> Rect?,
    padding: Dp,
    shape: CornerBasedShape,
    onDismissRequest: () -> Unit,
) {
    val host = LocalOverlayHost.current
    val scrim = Theme.colors.scrim
    val fraction = LocalOverlayProgress.current
    val path = remember { Path() }

    Box(
        Modifier
            .fillMaxSize()
            // Everything, including the lit control. A tap on the control being
            // explained is the likeliest accident here, and letting it through
            // would run an action the user was only being shown.
            .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
            .drawBehind {
                val hole = anchorInRoot()?.translate(-host.originInRoot) ?: return@drawBehind
                val lit = hole.inflate(padding.toPx())

                path.reset()
                path.fillType = PathFillType.EvenOdd
                path.addRect(Rect(Offset.Zero, size))
                when (val outline = shape.createOutline(lit.size, layoutDirection, this)) {
                    is Outline.Rounded -> path.addRoundRect(outline.roundRect.shiftedBy(lit.topLeft))
                    is Outline.Generic -> path.addPath(outline.path, lit.topLeft)
                    is Outline.Rectangle -> path.addRect(outline.rect.translate(lit.topLeft))
                }
                drawPath(path, scrim.copy(alpha = scrim.alpha * fraction.coerceIn(0f, 1f)))
            }
    )
}

/** The words, and the way forward. */
@Composable
private fun CoachmarkBubble(
    title: String,
    text: String,
    icon: ImageVector?,
    tour: CoachmarkTour,
) {
    val colors = Theme.colors
    val strings = Theme.strings

    Surface(
        modifier = Modifier
            .widthIn(max = BubbleMaxWidth)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $text"
            },
        shape = Theme.shapes.container,
        color = colors.accent.solid,
        contentColor = colors.accent.onSolid,
        shadow = Theme.elevation.overlay,
    ) {
        Column(
            modifier = Modifier.padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, size = Theme.sizing.iconMedium)
                }
                Text(title, style = Theme.typography.titleSmall)
            }
            Text(text, style = Theme.typography.bodySmall)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Theme.spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Where you are, on the left. A tour with no count is one the
                // user cannot decide whether to sit through.
                if (tour.count > 1) {
                    Text(
                        text = "${tour.position} / ${tour.count}",
                        style = Theme.typography.labelSmall,
                        color = colors.accent.onSolid.copy(alpha = StepCountAlpha),
                    )
                }
                Box(Modifier.weight(1f))
                if (tour.hasPrevious) {
                    Button(
                        onClick = tour::previous,
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Small,
                    ) { +strings.back }
                }
                Button(
                    onClick = tour::next,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Small,
                ) { +(if (tour.hasNext) strings.next else strings.gotIt) }
            }
        }
    }
}

private val BubbleMaxWidth: Dp = 320.dp

/** Present, not shouting: the count is orientation, not an instruction. */
private const val StepCountAlpha = 0.7f

/**
 * The same rounded rectangle, moved to [offset].
 *
 * [RoundRect] carries its own corner radii and has no translate of its own, so
 * moving one means rebuilding it. Only the origin moves; every radius is kept.
 */
private fun RoundRect.shiftedBy(offset: Offset): RoundRect = RoundRect(
    left = left + offset.x,
    top = top + offset.y,
    right = right + offset.x,
    bottom = bottom + offset.y,
    topLeftCornerRadius = topLeftCornerRadius,
    topRightCornerRadius = topRightCornerRadius,
    bottomRightCornerRadius = bottomRightCornerRadius,
    bottomLeftCornerRadius = bottomLeftCornerRadius,
)
