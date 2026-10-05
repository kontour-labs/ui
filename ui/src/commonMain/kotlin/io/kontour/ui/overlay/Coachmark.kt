package io.kontour.ui.overlay

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.lerp
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
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonDefaults
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/** Sizing for a [CoachmarkTour]'s spotlight. */
object CoachmarkDefaults {

    /** How far the lit area reaches past the control it is lighting. */
    val SpotlightPadding: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.coachmarkSpotlightPadding

    /**
     * The lit area's shape.
     *
     * `medium` rather than a radius of its own, so the hole around a control is
     * cut with the same corner the rest of the library draws — including the
     * smoothing, which a `CornerRadius` on a `RoundRect` cannot carry.
     */
    val SpotlightShape: CornerBasedShape
        @Composable @ReadOnlyComposable get() = Theme.shapes.container
}

/**
 * An ordered walk through several [Modifier.coachmarkStep]s.
 *
 * ```
 * val tour = rememberCoachmarkTour("plan", "save", "alerts")
 *
 * IconButton(
 *     onClick = ::save,
 *     icon = Tabler.Outline.Bookmark,
 *     contentDescription = "Save this trip",
 *     modifier = Modifier.coachmarkStep(
 *         tour = tour,
 *         id = "save",
 *         title = "Save this trip",
 *         message = "Saved trips show up on the home screen.",
 *     ),
 * )
 * ```
 *
 * The tour holds only the **order**; each step's words live at the control they
 * describe, which is the only place they can be kept honest when that control
 * changes. Start it with [start] — from a "show me around" button, or once on
 * first run — and it walks itself.
 *
 * ### Not the same thing as [Modifier.coachmark]
 *
 * That one is a single tip, fired by an [OverlayQueue] when the app judges the
 * moment right, and it deliberately leaves the interface undimmed: the user is
 * being shown a thing *in* the screen and the screen has to stay legible around
 * it. A tour is the opposite bargain. The user asked for it, it is going to take
 * over for a few seconds, and dimming everything but the current control is what
 * makes "this one, here" unmistakable.
 *
 * | | `Modifier.coachmark` | `CoachmarkTour` |
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

    /**
     * The tour's one spotlight, the overlay every step shows itself in.
     *
     * One for the tour rather than one per step: a step shown in an entry of its
     * own faded in over the last one fading out, two dims at once, and the
     * page behind went twice as dark between every pair of steps and came back
     * — reported as *"the background gets twice as dim when animating between
     * two coachmarks"*. Shown under one key, the next step replaces the last in
     * place, and the dim stays one dim from the first step to the last.
     */
    internal val spotlight: Any = Any()

    /** Which step's composition last showed [spotlight], so a step that has gone only takes it down if nobody has taken it over. */
    internal var spotlightShownBy: Any? = null

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
    message: String,
    icon: ImageVector? = null,
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Centre,
    padding: Dp = CoachmarkDefaults.SpotlightPadding,
    shape: CornerBasedShape = CoachmarkDefaults.SpotlightShape,
    enabled: Boolean = true,
): Modifier {
    var bounds by remember { mutableStateOf<Rect?>(null) }

    CoachmarkSpotlight(
        visible = enabled && tour.isShowing(id),
        anchor = { bounds },
        title = title,
        message = message,
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
 * Rendered as the tour's single overlay entry, [CoachmarkTour.spotlight], at
 * [OverlayLayer.Tooltip] with the host's own scrim switched off, because the
 * host draws a plain rectangle and this one has a hole in it. The step showing
 * puts its own content in it; the next step replaces that content rather than
 * adding an entry, and the hole and the bubble travel from the one control to
 * the other.
 */
@Composable
private fun CoachmarkSpotlight(
    visible: Boolean,
    anchor: () -> Rect?,
    title: String,
    message: String,
    icon: ImageVector?,
    side: OverlaySide,
    alignment: OverlayAlignment,
    padding: Dp,
    shape: CornerBasedShape,
    tour: CoachmarkTour,
) {
    val host = LocalOverlayHost.current
    val colours = Theme.colours
    // This step's own identity, against which the tour's entry is claimed.
    val step = remember { Any() }
    val key = tour.spotlight
    val dismissLabel = Theme.strings.gotIt
    // Read here rather than by the caller — see `TooltipOverlay`.
    val anchorNow = anchor()
    val latestAnchor by rememberUpdatedState(anchorNow)
    val latestTour by rememberUpdatedState(tour)

    // Taken down only by the step that put it up. Another step showing now
    // has taken it over, and a step that hid it then would take the next
    // one's spotlight down with its own.
    DisposableEffect(tour) {
        onDispose {
            if (tour.spotlightShownBy === step) {
                tour.spotlightShownBy = null
                host.hide(key)
            }
        }
    }

    LaunchedEffect(visible, anchorNow != null, title, message, side, alignment) {
        if (!visible || anchorNow == null) {
            // A frame's grace, for the step after this one to show itself in
            // the same entry. Hidden at once, it began to fade out and was
            // then turned round. Nobody took it over — the next step's
            // control is not on screen, or the tour has ended — and it goes.
            withFrameNanos { }
            if (tour.spotlightShownBy === step) {
                tour.spotlightShownBy = null
                host.hide(key)
            }
            return@LaunchedEffect
        }

        tour.spotlightShownBy = step
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
                    // From the last step's control to this one's, for the
                    // hole and the bubble alike, rather than a cut.
                    val travelling = rememberTravellingAnchor(step) { latestAnchor }
                    Spotlight(
                        anchorInRoot = travelling,
                        padding = padding,
                        shape = shape,
                        onDismissRequest = { latestTour.finish() },
                    )
                    AnchoredOverlayLayout(
                        anchorInRoot = travelling,
                        fromScale = 0.85f,
                        side = side,
                        alignment = alignment,
                        // Clear of the lit area rather than of the control, so
                        // the bubble does not sit on top of the thing it is
                        // pointing at.
                        gap = padding + Theme.spacing.xxs,
                        margin = MenuDefaults.ScreenMargin,
                        arrow = ArrowSpec(colour = colours.accent.solid),
                    ) {
                        CoachmarkBubble(
                            title = title,
                            message = message,
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
 * Where the spotlight is: [anchor], or on its way there from wherever it was
 * when [step] last changed.
 *
 * Read in draw and in measure, as the anchor itself is, so a control that moves
 * while the spotlight travels — a list scrolling under it — is still where it
 * lands. Under reduced motion it does not travel: it is at the new control the
 * moment the step changes.
 */
@Composable
private fun rememberTravellingAnchor(step: Any, anchor: () -> Rect?): () -> Rect? {
    val motion = Theme.motion
    val latest by rememberUpdatedState(anchor)
    val travel = remember { Animatable(1f) }
    val trip = remember { TravellingFrom() }
    val read = remember {
        {
            val to = latest()
            val from = trip.from
            val t = travel.value
            val at = if (from == null || to == null || t >= 1f) to else lerp(from, to, t)
            trip.last = at
            at
        }
    }
    LaunchedEffect(step) {
        // The first step arrives with the entry itself, and has nowhere to come from.
        if (trip.last == null || motion.reduceMotion) {
            trip.from = null
            travel.snapTo(1f)
            return@LaunchedEffect
        }
        trip.from = trip.last
        travel.snapTo(0f)
        travel.animateTo(1f, motion.springOrTween(motion.springDefault))
    }
    return read
}

/** Where the spotlight set off from, and where it was last drawn. Not state: read and written in draw. */
private class TravellingFrom {
    var from: Rect? = null
    var last: Rect? = null
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
    val scrim = Theme.colours.scrim
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
                drawPath(path, scrim.copy(alpha = scrim.alpha * fraction().coerceIn(0f, 1f)))
            }
    )
}

/** The words, and the way forward. */
@Composable
private fun CoachmarkBubble(
    title: String,
    message: String,
    icon: ImageVector?,
    tour: CoachmarkTour,
) {
    val colours = Theme.colours
    val strings = Theme.strings

    Surface(
        modifier = Modifier
            .widthIn(max = BubbleMaxWidth)
            .semantics(mergeDescendants = true) {
                contentDescription = strings.sentences(listOf(title, message))
            },
        shape = Theme.shapes.container,
        containerColour = colours.accent.solid,
        contentColour = colours.accent.onSolid,
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
            Text(message, style = Theme.typography.bodySmall)

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
                        text = strings.tourPosition(Theme.formats.quantity(tour.position), Theme.formats.quantity(tour.count)),
                        style = Theme.typography.labelSmall,
                        // Faded beside the message at the standard tier (5.4:1); ink at the
                        // enhanced one, which asks 7.
                        colour = if (highContrast()) colours.accent.onSolid else colours.accent.onSolid.copy(alpha = StepCountAlpha),
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
                // The way on, filled in the bubble's ink so it reads as the
                // button it is beside Back: as two ghost buttons on the accent
                // the pair read as words, and the last step's was asked for
                // as if it were not there — *"there needs to be a "done" and
                // "back" button in the final item's coachmark"*. Done on the
                // last step of a tour, which is what finishing one is; "Got
                // it" is a lone mark's, acknowledging rather than ending.
                Button(
                    onClick = tour::next,
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Small,
                    colours = ButtonDefaults.colours(
                        ButtonVariant.Primary,
                        container = colours.accent.onSolid,
                        content = colours.accent.solid,
                        border = null,
                    ),
                ) {
                    +when {
                        tour.hasNext -> strings.next
                        tour.count > 1 -> strings.done
                        else -> strings.gotIt
                    }
                }
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
