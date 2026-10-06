package io.kontour.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import io.kontour.ui.theme.Motion
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import io.kontour.ui.theme.toneColours
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.geometry.lerp as lerpRect
import androidx.compose.ui.graphics.lerp as lerpColour

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
 * How a [CoachmarkTour] goes from one step to the next.
 *
 * Asked for in these words: *"please animate between the marks as they change.
 * my vision is to have 2 options: one for just fading one out and fading the new
 * one in, and another for the mark shrinking into ball, zipping to it's new
 * position, and expanding back into a new mark"*. Either way the page is dimmed
 * once from the first step to the last; only the hole and the mark change.
 *
 * Under reduced motion every tour fades: the ball's trip across the screen is
 * the movement the preference asks to be spared.
 */
enum class CoachmarkTransition {
    /**
     * The mark showing fades out where it is, its control going dark with it,
     * and the next one fades in at its own.
     */
    Fade,

    /**
     * The mark shrinks into a ball at the side it points from, the ball zips to
     * the next control, and grows into the next mark there. The hole closes
     * behind the one and opens round the other.
     */
    Morph,
}

/**
 * An ordered walk through several steps — [Modifier.coachmarkStep]s on the
 * controls they explain, and [CoachmarkStep]s that point at nothing — on one
 * screen or across the app.
 *
 * ```kotlin
 * val tour = rememberCoachmarkTour("welcome", "plan", "saved") { step ->
 *     // Take the reader to the screen each step is on.
 *     when (step) {
 *         "plan" -> tab = Tab.Plan
 *         "saved" -> tab = Tab.Saved
 *     }
 * }
 *
 * CoachmarkStep(tour, "welcome", title = "Welcome aboard", message = "A minute's tour of the app.")
 *
 * IconButton(
 *     onClick = ::save,
 *     icon = Tabler.Outline.Bookmark,
 *     contentDescription = "Save this trip",
 *     modifier = Modifier.coachmarkStep(
 *         tour = tour,
 *         id = "saved",
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
 * ### Across the app
 *
 * A step is shown once its control is on screen, so a tour can go wherever the
 * app does. [rememberCoachmarkTour]'s `navigate` is told each step as the tour
 * reaches it — forwards, back, or at the start — and takes the reader to the
 * screen it is on; the dim stays up in between, and the mark goes to the step
 * when its screen has arrived. Remember the tour above the screens it visits,
 * where it outlives every one of them. A step whose control does not turn up —
 * scrolled out of a list, or on a screen `navigate` did not go to — is passed
 * over, in the direction the reader was going, after a moment's wait.
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

    /** Whether a step is showing, or on its way. */
    val isRunning: Boolean get() = index in order.indices

    /** The step now showing, or null. */
    val current: String? get() = order.getOrNull(index)

    /** Human-readable position, for a "2 of 4" in the bubble. */
    val position: Int get() = index + 1

    /** How many steps the tour has. */
    val count: Int get() = order.size

    /** Whether there is a step before this one to go back to. */
    val hasPrevious: Boolean get() = index > 0

    /** Whether there is a step after this one, rather than the end. */
    val hasNext: Boolean get() = index < order.lastIndex

    /** Starts the tour at its first step, finished or not. */
    fun start() {
        if (order.isEmpty()) return
        isFinished = false
        heading = 1
        index = 0
    }

    /** On to the next step, or the end of the tour from the last one. */
    fun next() {
        heading = 1
        if (hasNext) index++ else finish()
    }

    /** Back a step; nothing on the first. */
    fun previous() {
        if (!hasPrevious) return
        heading = -1
        index--
    }

    /** Ends the tour, whether it was walked through or skipped. */
    fun finish() {
        index = NotRunning
        isFinished = true
        takeDown()
    }

    /** How the tour goes from one step to the next; see [rememberCoachmarkTour]. */
    internal var transition: CoachmarkTransition by mutableStateOf(CoachmarkTransition.Morph)

    /** The way the reader last went: 1 forwards, -1 back. Which way a missing step is passed over. */
    internal var heading: Int = 1

    /** Every step on screen now, by id: what the spotlight shows when the tour reaches it. */
    internal val steps = mutableStateMapOf<String, TourStep>()

    /**
     * The tour's one spotlight, the overlay every step shows itself in.
     *
     * One for the tour rather than one per step: a step shown in an entry of its
     * own faded in over the last one fading out, two dims at once, and the
     * page behind went twice as dark between every pair of steps and came back
     * — reported as *"the background gets twice as dim when animating between
     * two coachmarks"*. Shown under one key, the next step replaces the last in
     * place, and the dim stays one dim from the first step to the last — and
     * now across a change of screen, which takes the steps that put it up with
     * it: the entry is the tour's, and only the tour takes it down.
     */
    internal val spotlight: Any = Any()

    /** The host the spotlight is up in, while it is. */
    internal var shownIn: OverlayHostState? = null

    /** Takes the spotlight down, if it is up. */
    internal fun takeDown() {
        shownIn?.hide(spotlight)
        shownIn = null
    }

    /**
     * Moves on from [id], which did not turn up in time: the way the reader was
     * going, or forwards from the first step.
     */
    internal fun passOver(id: String) {
        if (current != id) return
        if (heading < 0 && hasPrevious) index-- else next()
    }

    private companion object {
        const val NotRunning = -1
    }
}

/**
 * A [CoachmarkTour] over these step ids, in this order.
 *
 * Ids rather than the steps themselves, so the words stay at the controls, and
 * a step whose control is not on screen is waited for and then passed over
 * rather than pointing at a rectangle that is not there.
 *
 * @param transition How it goes from one step to the next.
 * @param navigate Takes the reader to the screen a step is on, told each step's
 *   id as the tour reaches it — at the start, forwards and back. Null for a tour
 *   that stays on one screen. Called where this is, so remember the tour above
 *   the screens it visits; a step already on screen needs nothing done.
 */
@Composable
fun rememberCoachmarkTour(
    vararg steps: String,
    transition: CoachmarkTransition = CoachmarkTransition.Morph,
    navigate: (suspend (step: String) -> Unit)? = null,
): CoachmarkTour {
    val tour = remember(steps.toList()) { CoachmarkTour(steps.toList()) }
    SideEffect { tour.transition = transition }
    val goTo by rememberUpdatedState(navigate)
    LaunchedEffect(tour) {
        snapshotFlow { tour.current }.collectLatest { step ->
            if (step != null) goTo?.invoke(step)
        }
    }
    // A tour that leaves composition takes its spotlight with it: there is
    // nothing left to walk it.
    DisposableEffect(tour) { onDispose { tour.takeDown() } }
    return tour
}

/**
 * Marks this control as one stop on [tour], and explains it when its turn comes.
 *
 * The modifier both **measures** the control — so the spotlight knows what to
 * cut a hole around — and **describes** the step. Keeping the two together is
 * what lets a tour be declared at the controls it is about rather than in a list
 * somewhere else that has to name them all again.
 *
 * @param id Matches one of the ids given to [rememberCoachmarkTour]. An id no
 *   step uses is passed over.
 * @param padding How far the lit area reaches past the control. A little, so the
 *   hole reads as being *about* the control rather than as a window near it.
 * @param side Which way the bubble prefers to sit. Flipped automatically when
 *   there is no room.
 * @param tone The bubble's colour: the accent by default, any [Tone] the step
 *   is better said in.
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
    tone: Tone = Tone.Accent,
    enabled: Boolean = true,
): Modifier {
    val step = remember { TourStep(modal = false) }
    SideEffect {
        step.id = id
        step.title = title
        step.message = message
        step.icon = icon
        step.side = side
        step.alignment = alignment
        step.padding = padding
        step.shape = shape
        step.tone = tone
    }
    TourStepEntry(tour, id, step, enabled)
    return this.anchorBounds { step.bounds = it }
}

/**
 * A step on [tour] that points at nothing: a card in the middle of the dimmed
 * screen, for a welcome, a word between two screens, or a last "that's it".
 *
 * Asked for as *"a coachmark that acts as a modal, not pointing to anything in
 * particular. this is good for like "welcome" text"*. Put it anywhere in the
 * screen the step belongs to — it draws nothing where it is — or above the
 * screens, for a step that belongs to none of them. A tour of this one step is
 * a modal of its own, with "Got it" to close it.
 *
 * @param id Matches one of the ids given to [rememberCoachmarkTour].
 * @param tone The card's colour: the accent by default.
 */
@Composable
fun CoachmarkStep(
    tour: CoachmarkTour,
    id: String,
    title: String,
    message: String,
    icon: ImageVector? = null,
    tone: Tone = Tone.Accent,
    enabled: Boolean = true,
) {
    val step = remember { TourStep(modal = true) }
    SideEffect {
        step.id = id
        step.title = title
        step.message = message
        step.icon = icon
        step.tone = tone
    }
    TourStepEntry(tour, id, step, enabled)
}

/**
 * What a step says, and where it is: written by the step from its composition,
 * read by the spotlight.
 */
@Stable
internal class TourStep(val modal: Boolean) {
    var id: String by mutableStateOf("")
    var title: String by mutableStateOf("")
    var message: String by mutableStateOf("")
    var icon: ImageVector? by mutableStateOf(null)
    var side: OverlaySide by mutableStateOf(OverlaySide.Bottom)
    var alignment: OverlayAlignment by mutableStateOf(OverlayAlignment.Centre)
    var padding: Dp by mutableStateOf(0.dp)
    var shape: CornerBasedShape? by mutableStateOf(null)
    var tone: Tone by mutableStateOf(Tone.Accent)

    /** The control, in root pixels; null until it is placed, and for a step that points at nothing. */
    var bounds: Rect? by mutableStateOf(null)

    /** Whether the spotlight can show it: a modal at once, a control once it is placed. */
    val isReady: Boolean get() = modal || bounds != null

    /** The bubble, in host pixels, as it was last placed. Not state: written in layout, read in draw. */
    var bubble: Rect? = null

    /** The lit area, in host pixels, as it was last drawn. Not state, for the same reason. */
    var hole: Rect? = null
}

/**
 * Puts [step] on [tour] for as long as it is composed, and the tour's spotlight
 * up in this host when the tour is running and nobody else has.
 */
@Composable
private fun TourStepEntry(tour: CoachmarkTour, id: String, step: TourStep, enabled: Boolean) {
    val host = LocalOverlayHost.current
    val dismissLabel = Theme.strings.gotIt

    DisposableEffect(tour, id, enabled) {
        if (enabled) tour.steps[id] = step
        onDispose { if (tour.steps[id] === step) tour.steps.remove(id) }
    }

    LaunchedEffect(tour, host) {
        snapshotFlow { tour.isRunning }.collect { running ->
            if (!running || tour.shownIn != null) return@collect
            tour.shownIn = host
            host.show(
                OverlayEntry(
                    key = tour.spotlight,
                    layer = OverlayLayer.Tooltip,
                    // The spotlight draws its own: the host's is a full-window
                    // rectangle and the whole point here is the part that is not
                    // covered.
                    scrim = ScrimStyle.None,
                    dismissLabel = dismissLabel,
                    onDismiss = { tour.finish() },
                    // A tour is modal — it is the one coach mark the user cannot
                    // work around, because they asked for it.
                    trapFocus = true,
                    content = { TourSpotlight(tour) },
                )
            )
        }
    }
}

/**
 * Where the spotlight is between two steps: the one going and the one coming,
 * and how far each has got.
 */
@Stable
private class TourScene {
    /** The step the spotlight is on, or arriving at. */
    var showing: TourStep? by mutableStateOf(null)

    /** The step it is leaving, while it is. */
    var leaving: TourStep? by mutableStateOf(null)

    /** How far [leaving] has gone: 1 gone. */
    val out = Animatable(1f)

    /** How far [showing] has come: 1 arrived. */
    val arrival = Animatable(1f)

    /** How far the ball is between [zipFrom] and [showing]: 1 there. Morph only. */
    val zip = Animatable(1f)

    /** Where the ball set off from, and in what colour. Not state: read in draw. */
    var zipFrom: Offset? = null
    var zipFromColour: Color = Color.Unspecified

    /** Where the ball was last drawn, and in what colour, for a walk that turns it round mid-trip. */
    var ballAt: Offset? = null
    var ballColour: Color = Color.Unspecified

    /** Where a ball turned round mid-trip waits, rather than at the step it was going to. */
    var parked: Offset? = null

    /** Whether a step has been shown since the spotlight went up. */
    var started = false
}

/**
 * The dim, the hole in it, the mark, and the way between steps.
 *
 * Rendered as the tour's single overlay entry, [CoachmarkTour.spotlight], at
 * [OverlayLayer.Tooltip] with the host's own scrim switched off, because the
 * host draws a plain rectangle and this one has a hole in it.
 */
@Composable
private fun TourSpotlight(tour: CoachmarkTour) {
    val motion = Theme.motion
    val morph = tour.transition == CoachmarkTransition.Morph && !motion.reduceMotion
    val scene = remember { TourScene() }
    val colours = solidTones()

    LaunchedEffect(tour, morph) {
        snapshotFlow { tour.current }.collectLatest { id ->
            if (id != null) scene.walkTo(id, tour, morph, motion, colours)
        }
    }

    Box(Modifier.fillMaxSize()) {
        TourDim(scene, morph, colours, onDismissRequest = tour::finish)
        scene.leaving?.let { TourMark(it, tour, scene, morph, leaving = true) }
        scene.showing?.let { TourMark(it, tour, scene, morph, leaving = false) }
    }
}

/**
 * Takes the spotlight to step [id]: the step showing out, a wait for [id] to be
 * on screen, and [id] in.
 *
 * Called afresh whenever the tour moves, cancelling a walk in flight; what that
 * walk had got to is where this one sets off from, so Next pressed twice turns
 * the mark round rather than cutting it.
 */
private suspend fun TourScene.walkTo(
    id: String,
    tour: CoachmarkTour,
    morph: Boolean,
    motion: Motion,
    colours: SolidTones,
) {
    val now = showing
    if (now != null && now.id == id && tour.steps[id] === now) {
        // Turned back to the step that was arriving: on with its arrival.
        arrival.animateTo(1f, motion.tweenDefault())
        return
    }
    if (now != null) {
        // What had come in goes out from as far as it got. A ball turned round
        // on its way waits where it was rather than at the step it never reached.
        if (zip.value < 1f) parked = ballAt
        leaving = now
        showing = null
        out.snapTo(1f - arrival.value)
    }
    if (leaving != null && out.value < 1f) out.animateTo(1f, motion.tweenExit())
    if (!morph) leaving = null

    val step = awaitStep(tour, id)
    if (step == null) {
        tour.passOver(id)
        return
    }

    val first = !started && leaving == null && parked == null
    started = true
    arrival.snapTo(if (first) 1f else 0f)
    showing = step
    if (first) return

    val from = leaving
    if (morph && from != null) {
        // Placed before it can be gone to: the bubble's first layout, unseen.
        withFrameNanos { }
        withFrameNanos { }
        zipFrom = parked ?: tipOf(from) ?: ballAt
        zipFromColour = if (parked != null) ballColour else colours.of(from.tone).container
        parked = null
        zip.snapTo(0f)
        leaving = null
        zip.animateTo(1f, motion.springOrTween(motion.springSnappy))
    } else {
        leaving = null
        zip.snapTo(1f)
    }
    arrival.animateTo(1f, if (morph) motion.springOrTween(motion.springSnappy) else motion.tweenDefault())
}

/**
 * Step [id], once it is on screen, or null if it has not turned up within
 * [StepGraceMillis]. Counted on the frame clock, as the walk's animations are.
 */
private suspend fun awaitStep(tour: CoachmarkTour, id: String): TourStep? {
    val from = withFrameNanos { it }
    while (true) {
        tour.steps[id]?.takeIf { it.isReady }?.let { return it }
        val now = withFrameNanos { it }
        if (now - from > StepGraceMillis * 1_000_000L) return null
    }
}

/**
 * The dim with its holes, and the ball between two marks.
 *
 * ### Path operations, not a blend mode
 *
 * The obvious way to punch a hole is to draw the dim, then draw the hole in
 * `BlendMode.Clear` — which needs the whole thing in an offscreen buffer, since
 * clearing against the window itself would erase the app. Two holes, one going
 * and one coming, are regions of a path: the window less both at the dim's full
 * strength, and each hole's own part at the strength it has faded back to.
 */
@Composable
private fun TourDim(
    scene: TourScene,
    morph: Boolean,
    colours: SolidTones,
    onDismissRequest: () -> Unit,
) {
    val host = LocalOverlayHost.current
    val scrim = Theme.colours.scrim
    val fraction = LocalOverlayProgress.current
    val density = LocalDensity.current
    val ball = with(density) { BallRadius.toPx() }
    val paths = remember { DimPaths() }

    Box(
        Modifier
            .fillMaxSize()
            // Everything, including the lit control. A tap on the control being
            // explained is the likeliest accident here, and letting it through
            // would run an action the user was only being shown.
            .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
            .drawBehind {
                val strength = scrim.alpha * fraction().coerceIn(0f, 1f)
                val going = scene.leaving
                val coming = scene.showing
                val out = scene.out.value
                val arrival = scene.arrival.value

                // Each step's hole, followed as its control moves, and how
                // present it is: in a fade, how lit; in a morph, how open.
                val goingHole = going?.let { holeOf(it, host.originInRoot) }
                val comingHole = coming?.let { holeOf(it, host.originInRoot) }
                val goingShown = if (morph) 1f else 1f - out
                val comingShown = if (morph) 1f else arrival
                val goingOpen = if (morph) 1f - eased(out) else 1f
                val comingOpen = if (morph) eased(arrival) else 1f

                paths.dim(
                    scope = this,
                    colour = scrim.copy(alpha = strength),
                    going = goingHole?.let { (rect, step) -> HoleDraw(rect.scaledBy(goingOpen), step, goingShown) },
                    coming = comingHole?.let { (rect, step) -> HoleDraw(rect.scaledBy(comingOpen), step, comingShown) },
                )

                if (morph) drawBall(scene, ball, colours, strength / scrim.alpha.coerceAtLeast(0.001f))
            }
    )
}

/** A hole to cut: where, in what shape, and how lit (1 fully). */
private class HoleDraw(val rect: Rect, val step: TourStep, val lit: Float)

/** The paths the dim is cut from, kept from frame to frame. */
private class DimPaths {
    val window = Path()
    val a = Path()
    val b = Path()
    val union = Path()
    val rest = Path()
    val part = Path()

    fun dim(scope: DrawScope, colour: Color, going: HoleDraw?, coming: HoleDraw?) = with(scope) {
        val holes = listOfNotNull(going, coming).filter { it.rect.width > 0.5f && it.rect.height > 0.5f }
        if (holes.isEmpty()) {
            drawRect(colour)
            return@with
        }
        window.reset()
        window.addRect(Rect(Offset.Zero, size))
        val first = holes[0]
        a.reset()
        a.addHole(first, this)
        if (holes.size == 1) {
            rest.op(window, a, PathOperation.Difference)
            drawPath(rest, colour)
            if (first.lit < 1f) drawPath(a, colour.copy(alpha = colour.alpha * (1f - first.lit)))
            return@with
        }
        val second = holes[1]
        b.reset()
        b.addHole(second, this)
        union.op(a, b, PathOperation.Union)
        rest.op(window, union, PathOperation.Difference)
        drawPath(rest, colour)
        if (first.lit < 1f) {
            part.op(a, b, PathOperation.Difference)
            drawPath(part, colour.copy(alpha = colour.alpha * (1f - first.lit)))
        }
        if (second.lit < 1f) {
            part.op(b, a, PathOperation.Difference)
            drawPath(part, colour.copy(alpha = colour.alpha * (1f - second.lit)))
        }
        val both = maxOf(first.lit, second.lit)
        if (both < 1f) {
            part.op(a, b, PathOperation.Intersect)
            drawPath(part, colour.copy(alpha = colour.alpha * (1f - both)))
        }
    }

    private fun Path.addHole(hole: HoleDraw, scope: DrawScope) = with(scope) {
        val lit = hole.rect
        val shape = hole.step.shape
        if (shape == null) {
            addRect(lit)
            return@with
        }
        when (val outline = shape.createOutline(lit.size, layoutDirection, this)) {
            is Outline.Rounded -> addRoundRect(outline.roundRect.shiftedBy(lit.topLeft))
            is Outline.Generic -> addPath(outline.path, lit.topLeft)
            is Outline.Rectangle -> addRect(outline.rect.translate(lit.topLeft))
        }
    }
}

/**
 * A step's lit area in host pixels, followed as its control moves — a list
 * scrolling under it — and held where it was once the control has gone, so a
 * step leaving with its screen closes where it was rather than vanishing.
 */
private fun DrawScope.holeOf(step: TourStep, origin: Offset): Pair<Rect, TourStep>? {
    if (step.modal) return null
    val now = step.bounds?.translate(-origin)?.inflate(step.padding.toPx())
    if (now != null) step.hole = now
    return step.hole?.let { it to step }
}

/**
 * The ball, in a morph: the leaving mark shrinking into it, its trip, and the
 * next mark growing out of it. Drawn under the marks, which fade over it at the
 * two ends where it is the mark's own shape.
 */
private fun DrawScope.drawBall(scene: TourScene, radius: Float, colours: SolidTones, fade: Float) {
    val going = scene.leaving
    val coming = scene.showing
    val (rect, colour) = when {
        going != null && coming == null -> {
            val tone = colours.of(going.tone).container
            val bubble = going.bubble ?: return
            val at = scene.parked ?: tipOf(going) ?: return
            val t = eased(scene.out.value)
            if (scene.parked != null) ballRect(at, radius) to scene.ballColour
            else lerpRect(bubble, ballRect(at, radius), t) to tone
        }
        coming != null && scene.zip.value < 1f -> {
            val from = scene.zipFrom ?: return
            val to = tipOf(coming) ?: return
            val t = scene.zip.value
            val at = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
            ballRect(at, radius) to lerpColour(scene.zipFromColour, colours.of(coming.tone).container, t.coerceIn(0f, 1f))
        }
        coming != null && scene.arrival.value < 1f -> {
            val to = tipOf(coming) ?: return
            val bubble = coming.bubble ?: return
            lerpRect(ballRect(to, radius), bubble, eased(scene.arrival.value)) to colours.of(coming.tone).container
        }
        else -> return
    }
    scene.ballAt = rect.center
    scene.ballColour = colour
    val corner = minOf(radius, rect.width / 2f, rect.height / 2f).coerceAtLeast(0f)
    drawRoundRect(
        color = colour.copy(alpha = colour.alpha * fade.coerceIn(0f, 1f)),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(corner),
    )
}

/**
 * Where a step's ball sits: just inside its bubble, on the side nearest its
 * control — where the arrow is — and in the middle of a card that points at
 * nothing.
 */
private fun tipOf(step: TourStep): Offset? {
    val bubble = step.bubble ?: return null
    val hole = step.hole
    if (step.modal || hole == null) return bubble.center
    val inset = minOf(bubble.width, bubble.height) / 2f
    val x = hole.center.x.coerceIn(bubble.left + inset.coerceAtMost(bubble.width / 2f), bubble.right - inset.coerceAtMost(bubble.width / 2f))
    val y = hole.center.y.coerceIn(bubble.top, bubble.bottom)
    return Offset(x, y)
}

private fun ballRect(centre: Offset, radius: Float) =
    Rect(centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius)

private fun Rect.scaledBy(scale: Float): Rect {
    if (scale >= 1f) return this
    val s = scale.coerceAtLeast(0f)
    return Rect(
        center.x - width * s / 2f,
        center.y - height * s / 2f,
        center.x + width * s / 2f,
        center.y + height * s / 2f,
    )
}

private fun eased(t: Float): Float = FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))

/**
 * One step's mark: a bubble beside its control, or a card in the middle for a
 * step that points at nothing, at the strength the walk has it at.
 */
@Composable
private fun TourMark(step: TourStep, tour: CoachmarkTour, scene: TourScene, morph: Boolean, leaving: Boolean) {
    val host = LocalOverlayHost.current
    val colours = solidTone(step.tone)
    val alpha: () -> Float = {
        if (leaving) {
            val out = scene.out.value
            if (morph) 1f - (out / MorphHandover).coerceIn(0f, 1f) else 1f - out
        } else {
            val arrival = scene.arrival.value
            if (morph) {
                if (scene.zip.value < 1f) 0f else ((arrival - (1f - MorphHandover)) / MorphHandover).coerceIn(0f, 1f)
            } else {
                arrival
            }
        }
    }
    val faded = Modifier
        .graphicsLayer { this.alpha = alpha() }
        .then(
            // A mark on its way out is a picture of one: nothing to press, and
            // nothing for a screen reader to find beside the one arriving.
            if (leaving) {
                Modifier
                    .clearAndSetSemantics { }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
            } else {
                Modifier
            }
        )
    val placed = Modifier.onGloballyPositioned { step.bubble = it.boundsInRoot().translate(-host.originInRoot) }
    val index = tour.order.indexOf(step.id)

    if (step.modal) {
        Box(
            Modifier.fillMaxSize().padding(MenuDefaults.ScreenMargin).then(faded),
            contentAlignment = Alignment.Center,
        ) {
            CoachmarkBubble(
                step = step,
                index = index,
                tour = tour,
                colours = colours,
                modifier = Modifier
                    .overlayAppearance(LocalOverlayProgress.current, fromScale = 0.9f)
                    .then(placed),
            )
        }
    } else {
        AnchoredOverlayLayout(
            anchorInRoot = { step.bounds },
            fromScale = 0.85f,
            side = step.side,
            alignment = step.alignment,
            // Clear of the lit area rather than of the control, so the bubble
            // does not sit on top of the thing it is pointing at.
            gap = step.padding + Theme.spacing.xxs,
            margin = MenuDefaults.ScreenMargin,
            arrow = ArrowSpec(colour = colours.container),
            modifier = faded,
        ) {
            CoachmarkBubble(step = step, index = index, tour = tour, colours = colours, modifier = placed)
        }
    }
}

/** The words, and the way forward. */
@Composable
private fun CoachmarkBubble(
    step: TourStep,
    index: Int,
    tour: CoachmarkTour,
    colours: SolidTone,
    modifier: Modifier = Modifier,
) {
    val strings = Theme.strings
    val title = step.title
    val message = step.message
    val count = tour.count
    // Its own place in the tour, not the tour's: a mark fading out says where
    // it was, not where the tour has gone.
    val hasPrevious = index > 0
    val hasNext = index < count - 1

    Surface(
        modifier = modifier
            .widthIn(max = BubbleMaxWidth)
            .semantics(mergeDescendants = true) {
                contentDescription = strings.sentences(listOf(title, message))
            },
        shape = Theme.shapes.container,
        containerColour = colours.container,
        contentColour = colours.content,
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
                step.icon?.let { Icon(it, contentDescription = null, size = Theme.sizing.iconMedium) }
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
                if (count > 1) {
                    Text(
                        text = strings.tourPosition(Theme.formats.quantity(index + 1), Theme.formats.quantity(count)),
                        style = Theme.typography.labelSmall,
                        // Faded beside the message at the standard tier (5.4:1); ink at the
                        // enhanced one, which asks 7.
                        colour = if (highContrast()) colours.content else colours.content.copy(alpha = StepCountAlpha),
                    )
                }
                Box(Modifier.weight(1f))
                if (hasPrevious) {
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
                        container = colours.content,
                        content = colours.container,
                        border = null,
                    ),
                ) {
                    +when {
                        hasNext -> strings.next
                        count > 1 -> strings.done
                        else -> strings.gotIt
                    }
                }
            }
        }
    }
}

/** A tone's solid pair: what a coach mark is filled with, and what is written on it. */
@Immutable
internal class SolidTone(val container: Color, val content: Color)

/** Every tone's [SolidTone], for drawing code that cannot read the theme. */
@Immutable
internal class SolidTones(private val tones: Map<Tone, SolidTone>) {
    fun of(tone: Tone): SolidTone = tones.getValue(tone)
}

/**
 * The solid pair a coach mark in [tone] is drawn in: the tone's own, and the
 * inverse surface for [Tone.Neutral], as a toast is.
 */
@Composable
@ReadOnlyComposable
internal fun solidTone(tone: Tone): SolidTone =
    toneColours(tone).let { SolidTone(it.solid, it.onSolid) }

@Composable
@ReadOnlyComposable
private fun solidTones(): SolidTones = SolidTones(Tone.entries.associateWith { solidTone(it) })

private val BubbleMaxWidth: Dp = 320.dp

/** The ball a morph shrinks a mark into: about a fingertip's dot. */
private val BallRadius: Dp = 10.dp

/**
 * The share of a morph's shrink the mark's words take to fade, and of its
 * growth the next mark's take to come in: the shape changes, and the words are
 * there only while it is a mark's.
 */
private const val MorphHandover = 0.3f

/**
 * How long a step's control has to turn up — the screen it is on arriving after
 * `navigate` — before the tour passes it over.
 */
private const val StepGraceMillis = 1500L

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
