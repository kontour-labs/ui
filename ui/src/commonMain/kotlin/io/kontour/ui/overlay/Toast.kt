package io.kontour.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.freeDragOwning
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonColours
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.a11y.LocalTouchTargetOwnedByParent
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.adaptive.sheetEdges
import io.kontour.ui.adaptive.topEdges
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource
import kotlin.math.abs

/** What a toast is reporting. */
enum class ToastTone { Neutral, Success, Warning, Danger, Accent }

/** One live toast. */
@Stable
class Toast internal constructor(
    val id: Long,
    val message: String,
    val tone: ToastTone,
    val icon: ImageVector?,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val durationMillis: Long,
) {
    /**
     * Whether this toast is on screen, and whether it has finished arriving or
     * leaving.
     *
     * A toast removed from the list the instant it is dismissed cannot animate
     * out — the node is gone before the transition starts. So dismissing sets
     * `targetState = false` and the card removes the toast for real once the
     * transition reports itself idle. This is the shape `AnimatedVisibility`
     * documents for exactly this case, and it is why the flag lives on the model
     * rather than in the card.
     */
    internal val presence = MutableTransitionState(false).apply { targetState = true }
}

/**
 * Shows short confirmations of things the user just did.
 *
 * ```
 * val toasts = rememberToastHostState()
 * ToastHost(toasts)
 *
 * toasts.show("Added to favourites")
 * toasts.show("Couldn't save", tone = ToastTone.Danger, actionLabel = "Retry", onAction = ::retry)
 * ```
 *
 * A toast is for feedback on an *action*. For something about the state of the
 * screen the user is looking at, use a
 * [io.kontour.ui.components.display.Banner] — it stays put, and a toast that
 * carries important information will be missed by anyone who looked away.
 *
 * Never put the only copy of something important in a toast, and never put a
 * control in one that is not also available elsewhere: an action that vanishes
 * after four seconds is unusable for anyone who reads slowly.
 *
 * ### They stack, and each one runs its own clock
 *
 * This used to hold a queue and show its head, so a toast pinned for an answer
 * stopped every later one from being seen at all, and four rapid confirmations
 * took sixteen seconds to get through. Now up to [ToastDefaults.MaxVisible] are
 * on screen at once, newest in front, older ones scaled and offset behind — and
 * each carries its own timer, so a long one and a short one expire when they
 * were each told to.
 */
@Stable
class ToastHostState {
    private var nextId = 0L

    /** Oldest first, which is back to front on screen. */
    internal val toasts = mutableStateListOf<Toast>()

    /**
     * Queues a toast, and returns its id so it can be [dismiss]ed by name.
     *
     * @param durationMillis How long before it dismisses itself. Longer when
     *   there is an action, since the user has to read it *and* decide. **Zero
     *   means it stays** until [dismiss], [dismissCurrent] or [clear] — for a
     *   toast whose action is the point and which the user must actually answer.
     *   Reach for it rarely: a confirmation that will not go away is a banner
     *   that has been put in the wrong place, and
     *   [io.kontour.ui.components.display.Banner] is the component for that. A
     *   pinned toast no longer blocks the ones behind it, but it does hold a
     *   place in the stack.
     */
    fun show(
        message: String,
        tone: ToastTone = ToastTone.Neutral,
        icon: ImageVector? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMillis: Long =
            if (actionLabel != null) ToastDefaults.DurationWithAction else ToastDefaults.Duration,
    ): Long {
        val id = nextId++
        toasts.add(
            Toast(
                id = id,
                message = message,
                tone = tone,
                icon = icon,
                actionLabel = actionLabel,
                onAction = onAction,
                durationMillis = durationMillis,
            )
        )
        return id
    }

    /**
     * How many toasts the user has sent away by hand.
     *
     * Read by each toast's clock, which buys back time whenever this moves. See
     * [ToastDefaults.ClearedGrace]: clearing the ones on top is how a user
     * reaches one further down, and the reaching should not cost them the thing
     * they were reaching for.
     */
    internal var clears by mutableIntStateOf(0)
        private set

    /** Starts [id] on its way out. It leaves the list once it has animated away. */
    fun dismiss(id: Long) {
        clears++
        expire(id)
    }

    /**
     * The clock's own way out, which must not read as the user clearing one.
     *
     * The difference matters: a toast that runs out of time is the stack working
     * as intended, and a toast the user swiped away is the user working through
     * the stack. Only the second buys the others more time, and routing both
     * through [dismiss] would have every expiry extend every other toast — a
     * stack that never empties.
     */
    internal fun expire(id: Long) {
        toasts.firstOrNull { it.id == id }?.presence?.targetState = false
    }

    /**
     * Dismisses the one in front, which is the newest.
     *
     * It used to be the *oldest*, because there was only ever one showing and it
     * was the head of a queue. In a stack the front one is the one the user is
     * looking at, and that is the only reading of "current" that still means
     * anything.
     */
    fun dismissCurrent() {
        toasts.lastOrNull { it.presence.targetState }?.presence?.targetState = false
    }

    /** Clears everything. For navigating away from the context they belong to. */
    fun clear() {
        toasts.forEach { it.presence.targetState = false }
    }

    internal fun remove(toast: Toast) {
        toasts.remove(toast)
    }
}

@Composable
fun rememberToastHostState(): ToastHostState = remember { ToastHostState() }

/**
 * Which edge a stack of toasts is anchored to.
 *
 * [Bottom] by default, which is where they have always been. [Top] is what a
 * screen wants when the bottom is spoken for — a navigation bar, a sheet, a
 * persistent player — or simply when the thumb rests there and a toast under it
 * is a toast nobody reads.
 *
 * An enum rather than an `Alignment`, because the position decides two things
 * and an `Alignment` only carries one of them. The stack used to infer its
 * direction by casting the alignment to `BiasAlignment` and reading the vertical
 * bias, defaulting to "bottom" for anything else — so a custom `Alignment`
 * silently meant bottom, and `Alignment.Center` did too. Worse, the insets could
 * not follow: they were fixed at
 * [WindowInsets.sheetEdges][io.kontour.ui.adaptive.sheetEdges], which has no top
 * side at all, so a top-anchored toast drew underneath the status bar and the
 * display cutout.
 */
enum class ToastPosition {
    Top,
    Bottom,
    ;

    /** Where the stack sits in its host. */
    internal val alignment: Alignment
        get() = if (this == Top) Alignment.TopCenter else Alignment.BottomCenter

    /**
     * True when the stack recedes *away* from the viewer's edge — which is up
     * for a bottom stack and down for a top one. Drives the peek offsets, the
     * enter and exit slide, and which way a toast is swiped away.
     */
    internal val towardEdge: Boolean get() = this == Bottom
}

object ToastDefaults {
    /**
     * How many toasts are on screen at once.
     *
     * Four: one card and three pills behind it. It was three, and the ones
     * behind used to be near enough the same size as each other that a fourth
     * would have read as a thicker edge rather than as another toast. They taper
     * now — see [DepthScale] — so the fourth is plainly a fourth, and it costs
     * less height than the third one used to because they also [Peek] less.
     */
    const val MaxVisible: Int = 4

    /**
     * How far each toast behind the front one peeks out.
     *
     * This was 16dp, and the note here argued for it: a toast is a *pill*, and
     * at 10dp the one behind added eighteen pixels of rounded top to a shape
     * that was already round, so the stack read as one toast with a thick edge
     * rather than as two. That was true while every pill was the same width,
     * because the gap was the *only* thing separating them and it had to do all
     * the work on its own.
     *
     * The pills step in at the sides now, so the silhouette says where one ends
     * and the next begins and the gap does not have to. Twelve, which fits three
     * behind the card in slightly less height than two used to take.
     */
    val Peek: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.toastPeek

    /**
     * How wide a toast waiting behind the front one is drawn.
     *
     * A fixed number, and that is the whole of why this works. A pill carries no
     * message, so it has no width of its own to want and nothing to re-wrap when
     * it is given one — which is what let the shared width and its ratchet go.
     *
     * Wide enough to read as a toast rather than as a chip: at 48dp the thing
     * peeking out above the card looked like a badge that had come off it.
     */
    val PillWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.toastPillWidth

    /**
     * How tall it is drawn.
     *
     * Mostly hidden — [Peek] of it shows above the card in front — so this is
     * about what the visible band's corners look like rather than about the
     * shape as a whole. A one-line toast's own height, so a pill promoted to the
     * front does not also have to grow taller.
     */
    val PillHeight: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.toastPillHeight

    /**
     * How tall a toast's row of controls is, whatever the platform.
     *
     * 48dp, the target a finger wants, reserved by the toast rather than by
     * each control inside it — see the note beside `LocalTouchTargetOwnedByParent`
     * in `ToastSurface`. Fixed rather than read from `Theme.sizing.minTouchTarget`
     * on purpose: a card that is one size on desktop and another on a phone for
     * the same sentence is the thing being fixed, so the number cannot be the
     * one that differs between them.
     */
    val ControlRowHeight: Dp = 48.dp

    /**
     * How big the *first* pill behind the card is drawn.
     *
     * The size every pill used to be, near enough — the old rule was
     * `1 - 0.07 * depth`, so the first one landed here and the second one three
     * dp behind it. Kept as the ceiling deliberately: the report is that the
     * ones behind should get gradually smaller and that the size they are today
     * is the *largest* of them, so nothing here grows.
     */
    const val FirstPillScale: Float = 0.93f

    /**
     * How much smaller each pill is than the one in front of it.
     *
     * Twelve per cent, and it was five. At five a stack of pills was a single
     * lumpy silhouette: 134px, then 124px, with 32px of overlap between them, so
     * the step at the sides was five pixels on each side and read as a wobble in
     * one shape rather than as two shapes. At twelve the widths are 134, 117 and
     * 99 — a taper you can count.
     *
     * This is what does the separating now, which is why [Peek] could come down
     * rather than up to make room for a fourth toast.
     */
    const val DepthScale: Float = 0.12f

    /**
     * How far a pill behind the card shrinks as it leaves.
     *
     * A pill does not slide out the way the front card does. The front card
     * leaves toward the edge it came from, which is a legible direction because
     * it is the only thing there; a pill doing the same slides *under* the cards
     * in front of it and is simply not there on the next frame. Reported exactly
     * that way — a secondary toast should shrink and fade rather than vanish.
     *
     * Two thirds. It has to be a bigger step than the [DepthScale] twelve per
     * cent that separates one pill from the next: a gentler shrink than that
     * reads as the stack re-tapering around a toast rather than as the toast
     * leaving.
     */
    const val PillExitScale: Float = 0.66f

    /**
     * How much of a waiting toast's own content colour outlines it.
     *
     * Enough to see where one pill ends and the next begins, faint enough that
     * it reads as an edge catching the light rather than as a drawn border —
     * these are the toasts nobody is being asked to look at. See the note beside
     * it in `ToastSurface` for why this is a rim and not the shadow the report
     * asked for.
     */
    const val PillRim: Float = 0.22f

    /** How much of the screen's width a toast may take, at most. */
    val MaxWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.toastMaxWidth

    /**
     * Extra air between the message and the action, on top of the row's own gap.
     *
     * The row already spaces its children by `xs`, which is the gap between an
     * icon and the words it belongs to. An action is not part of the sentence,
     * and at that distance it read as though it were. This doubles it.
     *
     * Doubling and not more, because the gap comes out of the message: a toast
     * is capped at [MaxWidth], and at 12dp the catalog's own "Couldn't reach the
     * timetable service" went from one line to two. The action has a ground
     * under it now, which is most of what tells it apart; the gap only has to
     * finish the job.
     */
    val ActionGap: Dp = 8.dp

    /**
     * How much of the toast's content colour sits under its action's label.
     *
     * Enough to be a shape you could press, faint enough not to compete with
     * the message — the button is the second thing on a toast, not the first.
     */
    const val ActionGround: Float = 0.16f

    /** A disabled action, in the toast's own content colour. */
    const val DisabledContent: Float = 0.38f

    /**
     * How long a toast stays before dismissing itself.
     *
     * Two and a half seconds — long enough to read a confirmation, short enough
     * not to sit there once it has been read. It was four, which is a long time
     * to look at "Saved" and was reported as such.
     *
     * These were inline literals on [ToastHostState.show]'s signature, the one
     * pair of tunables in this file that were not here.
     */
    const val Duration: Long = 2_500

    /**
     * How long a toast with an action stays.
     *
     * Twice the plain one, because an action has to be read, decided on *and*
     * reached — and a control that vanishes as the finger arrives is worse than
     * one that lingers.
     */
    const val DurationWithAction: Long = 5_000

    /**
     * How much of its own duration a toast is topped up to when it reaches the
     * front.
     *
     * A toast behind the front one is counting the whole time — that is
     * deliberate and there is a test for it — but it is also not being read: the
     * card in front of it is. So a toast with two hundred milliseconds left when
     * the one in front of it goes is promoted and then immediately expires,
     * which the reporter saw as a toast that flashed rather than one that
     * arrived.
     *
     * A **floor**, not a restart, and it was a deliberate choice between the
     * two: a restart makes a stack of four take four full durations to clear,
     * which is the queue behaviour this host was rewritten to stop being. A
     * toast promoted with plenty of time left is untouched.
     *
     * Three fifths of the toast's *own* duration rather than a flat number of
     * milliseconds, so that a toast carrying an action keeps the ratio
     * [DurationWithAction] exists for — an action has to be read, decided on and
     * reached, and a flat floor would hand it the same second and a half as a
     * bare "Saved".
     */
    const val PromotedFloor: Float = 0.6f

    /**
     * How far a toast has to be dragged before it goes.
     *
     * A third of its own height, measured as a straight-line distance rather
     * than along an axis — the gesture accepts any direction but one, so a
     * diagonal flick has to count for what it is. Short enough that a flick is
     * enough, long enough that a scroll started on top of one does not throw it
     * away.
     */
    const val SwipeAway: Float = 0.33f

    /**
     * How far a toast can be pulled *away* from the edge it dismisses toward.
     *
     * Just over a third of its own height, and it is a limit rather than a
     * ratio: each pixel of pull moves the card less than the last, so it eases
     * up to this and stops. It used to be a flat 33% of every delta, which is a
     * slower drag rather than a bounded one — pull far enough and the card left
     * the screen the wrong way.
     *
     * Enough that the card acknowledges the finger, little enough that it is
     * plainly refusing. Zero, which is what this used to be, is
     * indistinguishable from a control that has hung.
     *
     * Only the one refused quarter is banded. Sideways is a way *out* now, so it
     * tracks the finger one for one — a direction that dismisses must not feel
     * like a direction that is being declined.
     */
    const val RubberBand: Float = 0.35f

    /**
     * How much of its own duration a toast wins back each time the user clears
     * one by hand.
     *
     * Reported from the far end of a deep stack: *"I tried to access one that
     * was a heap of a way down, and it disappeared as I was clearing the ones on
     * top."* Every toast counts down wherever it sits, so working through the
     * ones in front spends the time of the one behind them — the act of reaching
     * for it is what takes it away.
     *
     * Two fifths, and **capped at a full lifetime**: clearing tops the remainder
     * back up, and can never carry a toast past the duration it was shown with.
     * That keeps a burst bounded — a user who clears ten toasts does not leave
     * the last one on screen for a minute — while making the common case, two or
     * three swipes to reach the fourth card, cost nothing at all.
     */
    const val ClearedGrace: Float = 0.4f
}

/**
 * Renders whatever [state] has queued. Install once, near the root.
 *
 * Toasts sit in [OverlayLayer.Toast] with [ScrimStyle.None], so they never dim
 * or block what is underneath — the user must be able to keep working while one
 * is showing.
 *
 * @param maxVisible How many are drawn. Extras stay in the state and take their
 *   turn as the ones in front expire; their timers run either way, which is what
 *   stops a backlog from outliving its usefulness. One that runs out while it is
 *   still waiting for room simply never appears — there is nothing to animate
 *   away, and a confirmation of something the user did ten seconds ago is not
 *   worth showing late.
 * @param showClose Puts a close control on every toast. Off by default, because
 *   a toast that dismisses itself in four seconds does not need one — turn it on
 *   where toasts are pinned, or where they carry an action worth reading twice.
 */
@Composable
fun ToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier,
    position: ToastPosition = ToastPosition.Bottom,
    maxVisible: Int = ToastDefaults.MaxVisible,
    showClose: Boolean = false,
    closeLabel: String = Theme.strings.dismiss,
    /**
     * What a toast keeps clear of. The gesture bar, the cutout and the keyboard —
     * a confirmation of what the user just typed, hidden behind the keyboard they
     * typed it with, is the one place it is guaranteed not to be read.
     *
     * It does **not** account for a navigation bar: that is a component, not an
     * inset, and a screen with one should pass a
     * `WindowInsets(bottom = barHeight)` union of its own.
     *
     * Follows [position] by default, which is the whole reason that parameter is
     * an enum: a top-anchored stack needs the status bar and the cutout, and
     * `sheetEdges` has no top side, so it used to draw under both.
     */
    windowInsets: WindowInsets =
        if (position == ToastPosition.Top) WindowInsets.topEdges else WindowInsets.sheetEdges,
) {
    val host = LocalOverlayHost.current
    val key = remember { Any() }
    val occupied = state.toasts.isNotEmpty()

    // Everything the entry's content reads has to be read *live*: the entry is
    // pushed once, when the stack goes from empty to occupied, and composed by
    // the host from then on.
    val latest by rememberUpdatedState(
        ToastHostConfig(modifier, position, maxVisible, showClose, closeLabel, windowInsets)
    )

    LaunchedEffect(occupied) {
        if (!occupied) {
            host.hide(key)
            return@LaunchedEffect
        }
        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Toast,
                scrim = ScrimStyle.None,
                // Back should dismiss the screen, not a transient confirmation.
                dismissOnBack = false,
                dismissOnOutside = false,
                trapFocus = false,
                content = { ToastStack(state, latest) },
            )
        )
    }
}

/**
 * Where a card actually sits, given how far it has been pulled.
 *
 * One pixel per pixel toward the edge it dismisses to. The other way it is a
 * rubber band: `limit * (1 - 1 / (pull / limit + 1))`, which is half the limit
 * at one limit of pull, three quarters at three, and never quite arrives. That
 * "never quite" is the whole difference between a band and a wall — a card that
 * stops dead still reads as broken, however short the distance was.
 *
 * The first two attempts damped each delta as it arrived, which cannot work: the
 * damping is a function of where the card already is, so it converges within two
 * or three events and is flat from there. Measured over a 240px pull in 20px
 * steps, a flat third of every delta gave `[10, 10, 10, …]` and a linear ramp
 * gave `[2, 19, 19, 19, …]`. This gives a curve that is still moving at the end.
 */
/**
 * Whether a drag is aimed back into the screen, which is the one that returns.
 *
 * Decided with the reporter: a toast goes away when swiped toward the edge it is
 * anchored to **or** sideways, in either direction, with 45 degrees of slack
 * around each. Those three cones meet, and what is left over is a single
 * quarter — the one pointing away from the edge, back at the content.
 *
 * So the rule is not three tests. It is one: **every direction dismisses except
 * the quarter aimed back in.** Push a bottom toast up, or a top one down, and it
 * resists and returns; send it anywhere else and it goes.
 *
 * Being inside a 45-degree cone around "away" is exactly `awayward > |x|`, which
 * is why there is no angle in the arithmetic. The boundary is inclusive on the
 * dismissing side: a drag at precisely 45 degrees goes.
 */
private fun refusesToLeave(travel: Offset, towardEdge: Boolean): Boolean {
    val awayward = if (towardEdge) -travel.y else travel.y
    return awayward > abs(travel.x)
}

/**
 * Where a card sits, given how far it has been pulled, on both axes.
 *
 * Sideways is one pixel per pixel — it is a way out, so it must not feel
 * refused. The vertical half is [rubberBand], which is already 1:1 toward the
 * edge and a band away from it.
 */
private fun toastTravel(pull: Offset, limit: Float, towardEdge: Boolean): Offset =
    Offset(pull.x, rubberBand(pull.y, limit, towardEdge))

private fun rubberBand(pull: Float, limit: Float, towardEdge: Boolean): Float {
    if (limit <= 0f) return pull
    val wrongWay = if (towardEdge) -pull else pull
    if (wrongWay <= 0f) return pull
    val eased = limit * (1f - 1f / (wrongWay / limit + 1f))
    return if (towardEdge) -eased else eased
}

/** What [ToastHost] was called with, so the overlay entry can read it fresh. */
private data class ToastHostConfig(
    val modifier: Modifier,
    val position: ToastPosition,
    val maxVisible: Int,
    val showClose: Boolean,
    val closeLabel: String,
    val windowInsets: WindowInsets,
)

@Composable
private fun ToastStack(state: ToastHostState, config: ToastHostConfig) {
    // The stack grows away from the edge it is anchored to: a bottom-anchored
    // stack recedes upward, a top-anchored one downward.
    val towardEdge = config.position.towardEdge
    val visible = state.toasts.takeLast(config.maxVisible)
    val density = LocalDensity.current

    // No shared size any more, and the ratchet that kept one is gone with it.
    //
    // It held every card at the stack's widest and tallest — `maxOf(…)` fed by
    // each card's own measurement, applied as a `widthIn(min = …)`. The argument
    // was that one silhouette is what makes a stack read as a stack, and it was
    // self-sustaining by construction: `onSizeChanged` wrapped the min-width
    // node, so a card that had been stretched reported its stretched size back
    // in and the maximum could never fall.
    //
    // What it produced was the reported defect. A `Surface` aligns its content
    // to the start and does not propagate its minimum constraints, so a
    // stretched card does not re-flow — the message stays where it is and the
    // extra width is dead space on the right. "Saved" behind a longer toast grew
    // a blank half. The height half was worse and went unreported: one toast
    // carrying an action raised the floor for every plain toast behind it, for
    // as long as the stack lived.
    //
    // Only the front toast is a card now, and the ones behind it are plain
    // pills, so there is no second card for a width to be shared with.
    //
    // One number does survive, and it is not a ratchet. The cards are
    // bottom-aligned and then lifted by `Peek` each, so where a pill's top edge
    // lands depends on how much *shorter* it is than the card in front — and a
    // 36dp pill behind a 56dp card is hidden by it completely. That is the same
    // fault the height ratchet existed to prevent, restated for pills, and I
    // reproduced it exactly on the way here: the first version of this drew no
    // pill at all.
    //
    // So the pills are placed against the front card's height. The *front*
    // card's, measured now — not the tallest any card has ever been. It falls
    // when the front toast is replaced by a shorter one, which is the whole
    // difference between this and what it replaces.
    var frontHeightPx by remember { mutableIntStateOf(0) }
    val frontHeight = with(density) { frontHeightPx.toDp() }

    // How far the front card has been *pulled*, owned here so the pills behind it
    // travel with it. Reported as the stack coming apart under a finger: only
    // the card moved, and the pills it is supposed to be the front of stayed
    // where they were.
    //
    // Raw, and mapped to a displacement below. Damping each delta as it arrives
    // cannot make a rubber band — it makes a wall, because the damping depends
    // on where the card already is and so converges to a fixed point within two
    // or three events. Measured: a flat third of every delta stopped at 10px and
    // a linear ramp stopped at 19, both by the second frame of a 240px pull.
    // The band has to be a function of the *whole* pull.
    // Held as the state rather than read out of it, and handed down that way.
    //
    // `Modifier.draggable` calls its delta handler once per *pointer event*, and
    // a finger emits faster than the compositor recomposes. This used to be a
    // `Float` parameter on `ToastCard` — a value captured at composition — with
    // the handler computing `pull + delta` from it, so every delta inside one
    // frame started from the same stale base and the last one overwrote the
    // rest. You kept one delta per frame instead of their sum, and a 180px drag
    // moved the card 42px. `SliderDragTest`'s KDoc has the same bug in the same
    // API, and `Switch` avoids it the same way: read the accumulator live.
    // The stack's own scope. The settle below runs on it rather than on the
    // dragged node's, which exists only while its card is at `depth == 0`.
    val stackScope = rememberCoroutineScope()
    val pull = remember { mutableStateOf(Offset.Zero) }
    val swipe = toastTravel(
        pull = pull.value,
        limit = frontHeightPx * ToastDefaults.RubberBand,
        towardEdge = towardEdge,
    )

    // Back to nothing whenever the card in front changes.
    //
    // Every card reads `swipe` into its `translationY`, and nothing else ever
    // put this back: a toast swiped away left the whole stack behind it sitting
    // permanently displaced, until the stack emptied completely and the overlay
    // entry went with it. It also covers the case where a toast arrives while
    // the spring below is still running — the settle is on the dragged node's
    // own scope, and that node exists only while its card is at `depth == 0`.
    val frontId = visible.lastOrNull()?.id
    LaunchedEffect(frontId) { pull.value = Offset.Zero }

    // Every toast runs its clock, including the ones with no room to be drawn.
    //
    // This used to live in `ToastCard`, and a card is only composed for the
    // front `maxVisible` toasts — so a toast queued behind them was not counting
    // at all, and began its full duration over from the beginning once there was
    // room for it. `ToastHost`'s KDoc has always claimed the opposite, and the
    // report is the difference between the two: a burst of confirmations took
    // two rounds of the clock to clear rather than one, which is the jank.
    //
    // A toast that expires while it is off screen is *removed* rather than
    // dismissed. Dismissal is a request to animate away, and there is no card
    // composed to run that animation or to take it off the list when it ends —
    // so a dismissed-but-unremoved toast would sit in the stack for ever,
    // holding a place that nothing can see.
    //
    // The clock is not a flat `delay`, and the difference is `PromotedFloor`. A
    // toast counts down wherever it is in the stack, but it is only being *read*
    // at the front — so one that arrives there with almost nothing left is
    // promoted and gone in the same breath, which is what was reported. Reaching
    // the front tops the remainder up to a floor.
    //
    // Written as "wait for the front/behind flag to change, or for the time to
    // run out", which is the whole state machine. It has to be symmetric: a
    // toast can be demoted as well as promoted — showing a new one puts the
    // current front card behind it — and a toast that goes front, behind, front
    // is the ordinary case in a burst, not an exotic one.
    //
    // `TimeSource.Monotonic` is the right clock here precisely because `delay`
    // is: this host expires on wall time, not on frame time, and the tests in
    // `ToastStackTest` are written against that.
    state.toasts.forEach { toast ->
        key(toast.id) {
            val onScreen by rememberUpdatedState(visible.any { it === toast })
            val atFront by rememberUpdatedState(visible.lastOrNull() === toast)
            LaunchedEffect(toast.id) {
                if (toast.durationMillis <= 0) return@LaunchedEffect
                val floor = (toast.durationMillis * ToastDefaults.PromotedFloor).toLong()
                val grace = (toast.durationMillis * ToastDefaults.ClearedGrace).toLong()
                var left = toast.durationMillis
                var front = atFront
                var clears = state.clears
                while (left > 0) {
                    val mark = TimeSource.Monotonic.markNow()
                    // Either thing that can buy this toast time, waited for
                    // together: it reaches the front, or the user sends another
                    // one away by hand.
                    val moved = withTimeoutOrNull(left) {
                        snapshotFlow { atFront to state.clears }
                            .first { (nowFront, nowClears) ->
                                nowFront != front || nowClears != clears
                            }
                    }
                    left -= mark.elapsedNow().inWholeMilliseconds
                    if (moved == null) break
                    val (nowFront, nowClears) = moved
                    if (nowClears != clears) {
                        clears = nowClears
                        // Topped up toward a full lifetime, never past one.
                        left = minOf(left + grace, toast.durationMillis)
                    }
                    if (nowFront != front) {
                        front = nowFront
                        if (front) left = maxOf(left, floor)
                    }
                }
                // `expire`, not `dismiss`: running out of time is the stack
                // working, and must not read as the user clearing one.
                if (onScreen) state.expire(toast.id) else state.remove(toast)
            }
        }
    }

    Box(
        Modifier.fillMaxSize().windowInsetsPadding(config.windowInsets),
        contentAlignment = config.position.alignment,
    ) {
        visible.forEachIndexed { index, toast ->
            key(toast.id) {
                ToastCard(
                    toast = toast,
                    state = state,
                    // Zero is the front. The list is oldest first, so the last
                    // one in it is the one on top.
                    depth = visible.lastIndex - index,
                    towardEdge = towardEdge,
                    showClose = config.showClose,
                    closeLabel = config.closeLabel,
                    frontHeight = frontHeight,
                    onFrontMeasured = { frontHeightPx = it },
                    swipe = swipe,
                    pull = pull,
                    scope = stackScope,
                    modifier = config.modifier,
                )
            }
        }
    }
}

@Composable
private fun ToastCard(
    toast: Toast,
    state: ToastHostState,
    depth: Int,
    towardEdge: Boolean,
    showClose: Boolean,
    closeLabel: String,
    /** How tall the card in front is — see `ToastStack`. Zero until it reports. */
    frontHeight: Dp,
    onFrontMeasured: (Int) -> Unit,
    /**
     * How far the front card has been dragged, in pixels.
     *
     * Shared by the whole stack rather than owned by the card being dragged, and
     * **every** card reads it. Reported as the pills sitting still while the
     * card in front of them moved: a stack is one object, and half of it staying
     * behind while the other half follows a finger says it is not.
     */
    swipe: Offset,
    /**
     * The same drag before the rubber band is applied — see `rubberBand`.
     *
     * The state itself, not its value. The drag handler has to *read* this at
     * the moment each pointer event arrives; given a `Float` it would accumulate
     * onto whatever the last composition happened to see. See `ToastStack`.
     */
    pull: MutableState<Offset>,
    /** The stack's scope — see the settle in the gesture below. */
    scope: CoroutineScope,
    modifier: Modifier,
) {
    val motion = Theme.motion

    // The clock is not here. It is in `ToastStack`, which composes it for every
    // toast rather than for the ones that fit on screen — see the comment there.

    // Gone for real once it has finished leaving, which is what lets it leave at
    // all — see `Toast.presence`.
    //
    // The `targetState` guard is load-bearing. A freshly built
    // `MutableTransitionState` reports `isIdle == true` until something starts
    // its transition, and its `currentState` is still `false` at that point — so
    // a card that only asked "idle and not showing?" removed itself on its own
    // first frame, racing `AnimatedVisibility` for which ran first. One toast
    // survived and the one behind it vanished, which is a stack of one.
    LaunchedEffect(toast.presence.targetState, toast.presence.isIdle, toast.presence.currentState) {
        if (toast.presence.targetState) return@LaunchedEffect
        if (toast.presence.isIdle && !toast.presence.currentState) state.remove(toast)
    }

    /** This card's own measured height, for the swipe threshold and the band. */
    var height by remember { mutableFloatStateOf(0f) }

    // Measured from the front card's edge rather than from the pill's own, so a
    // pill shorter than the card still clears it by `Peek`.
    val towards = if (towardEdge) -1 else 1
    val depthOffset by animateDpAsState(
        targetValue = if (depth == 0) {
            0.dp
        } else {
            (ToastDefaults.Peek * depth + (frontHeight - ToastDefaults.PillHeight)) * towards
        },
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "toastDepthOffset",
    )
    val depthScale by animateFloatAsState(
        // The front one is full size; the first pill behind it is the biggest a
        // pill gets, and they taper from there.
        targetValue = if (depth == 0) {
            1f
        } else {
            ToastDefaults.FirstPillScale - ToastDefaults.DepthScale * (depth - 1)
        },
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "toastDepthScale",
    )


    AnimatedVisibility(
        visibleState = toast.presence,
        enter = slideInVertically(motion.tweenDefault()) { if (towardEdge) it / 2 else -it / 2 } +
            fadeIn(motion.tweenFast()) +
            scaleIn(motion.tweenFast(), initialScale = 0.94f),
        // The front card leaves toward the edge it arrived from. A pill behind
        // it cannot: sliding that way takes it *under* the cards in front, so it
        // is simply absent on the next frame — reported as a secondary toast
        // that vanishes. It shrinks and fades in place instead, which is a
        // departure you can see happening in the only direction a pill has
        // room to move.
        //
        // The fade belongs here and nowhere else. The resting `graphicsLayer`
        // below deliberately sets no alpha for depth, because fading the ones
        // behind made them translucent rather than distant and the front card
        // showed through them. A leaving pill has no such problem: it is on its
        // way out and nothing is meant to line up behind it.
        //
        // `tweenExit` on both, and that is what makes the pill's shrink visible
        // at all. `tweenFast` carries `Motion.standard`, a hard ease-*out* that
        // covers most of its distance in the first two frames — fine for an
        // arrival and wrong for a departure, which is what `Motion.tweenExit`
        // exists to say. Measured on a four-deep stack: the pill's exit moved
        // the top of the stack on **two** frames of a nine-frame tween and then
        // sat still, which reads as a jump because it is one.
        exit = if (depth == 0) {
            slideOutVertically(motion.tweenExit()) { if (towardEdge) it / 2 else -it / 2 } +
                fadeOut(motion.tweenExit()) +
                scaleOut(motion.tweenExit(), targetScale = 0.94f)
        } else {
            fadeOut(motion.tweenExit()) +
                scaleOut(motion.tweenExit(), targetScale = ToastDefaults.PillExitScale)
        },
    ) {
        ToastSurface(
            toast = toast,
            depth = depth,
            showClose = showClose && depth == 0,
            closeLabel = closeLabel,
            modifier = modifier
                // The gesture is the outermost thing, above the padding — and
                // that is the point. Below it the swipe area was exactly the
                // visible pill and the 16dp ring around it was dead space, on a
                // target that is small to begin with. Up here the ring is live,
                // which is 16dp of slop on every side.
                //
                // Only the front one is draggable: the others are behind it and
                // cannot be reached.
                .then(
                    if (depth == 0) {
                        Modifier.freeDragOwning(
                            enabled = true,
                            interactionSource = null,
                            scope = scope,
                            // A toast is tappable: it carries a close control
                            // and can carry an action. Claiming the down would
                            // eat them. `Movement` leaves a press that never
                            // travels entirely alone and takes the first pixel
                            // that does.
                            claimsOn = DragClaim.Movement,
                            onStart = { },
                            onDelta = { delta ->
                                // A plain accumulator, read and written live.
                                // Where the card actually goes is `toastTravel`,
                                // one level up, because a band is a function of
                                // the whole pull and not of one delta at a time.
                                pull.value += delta
                            },
                            onEnd = {
                                // Read from the accumulator, not from `swipe`.
                                //
                                // `swipe` is this composable's *parameter* — the
                                // banded displacement as of the last time it
                                // recomposed — and the release arrives on a
                                // pointer event, not on a frame. Deciding from
                                // it asks "how far had the card travelled as of
                                // the last frame", which is at best one frame
                                // stale and, when the events since then were
                                // never drawn, zero. Measured: a 120px swipe
                                // straight at the edge read as 0px and refused
                                // to dismiss.
                                val travelled = pull.value
                                val far = height * ToastDefaults.SwipeAway
                                if (
                                    !refusesToLeave(travelled, towardEdge) &&
                                    travelled.getDistance() >= far
                                ) {
                                    state.dismiss(toast.id)
                                } else {
                                    // Sprung, not snapped. Letting go below the
                                    // threshold used to put the card back in a
                                    // single frame, which looks like a glitch
                                    // rather than like a control returning.
                                    //
                                    // On the stack's scope rather than the
                                    // gesture's: this node exists only while its
                                    // card is at `depth == 0`, so a toast
                                    // arriving mid-spring would otherwise
                                    // detach it and freeze the card part-way.
                                    val from = pull.value
                                    scope.launch {
                                        animate(
                                            initialValue = 0f,
                                            targetValue = 1f,
                                            animationSpec = motion.springOrTween(motion.springSnappy),
                                        ) { fraction, _ ->
                                            pull.value = from * (1f - fraction)
                                        }
                                    }
                                }
                            },
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(Theme.spacing.md)
                // Measured in the layout phase, not read out of the draw phase:
                // writing state from inside `graphicsLayer` is a write during
                // draw, and the recomposition it schedules can loop.
                .onSizeChanged {
                    height = it.height.toFloat()
                    if (depth == 0) onFrontMeasured(it.height)
                }
                // Inside the padding, so the depth scale shrinks the *card* and
                // not the card plus its slop — the stack's geometry is tuned to
                // `Peek` against the card, and scaling a bigger box moves every
                // number in it.
                .graphicsLayer {
                    translationX = swipe.x
                    translationY = depthOffset.toPx() + swipe.y
                    scaleX = depthScale
                    scaleY = depthScale
                    // No alpha. Fading the ones behind made them *translucent*
                    // rather than distant: the card in front showed through the
                    // card behind it, and the text of all three overlapped into
                    // a smear. Offset and scale already say "behind", and they
                    // say it without letting anything show through.
                }
                ,
            onAction = {
                toast.onAction?.invoke()
                state.dismiss(toast.id)
            },
            onClose = { state.dismiss(toast.id) },
        )
    }
}

@Composable
private fun ToastSurface(
    toast: Toast,
    showClose: Boolean,
    closeLabel: String,
    /** Zero is the front one. Anything behind it is drawn as a bare pill. */
    depth: Int,
    modifier: Modifier,
    onAction: () -> Unit,
    onClose: () -> Unit,
) {
    val motion = Theme.motion
    val colours = Theme.colours
    val container = when (toast.tone) {
        ToastTone.Neutral -> colours.surfaceInverse
        ToastTone.Success -> colours.success.solid
        ToastTone.Warning -> colours.warning.solid
        ToastTone.Danger -> colours.danger.solid
        ToastTone.Accent -> colours.accent.solid
    }
    val content = when (toast.tone) {
        ToastTone.Neutral -> colours.onSurfaceInverse
        ToastTone.Success -> colours.success.onSolid
        ToastTone.Warning -> colours.warning.onSolid
        ToastTone.Danger -> colours.danger.onSolid
        ToastTone.Accent -> colours.accent.onSolid
    }

    Surface(
        modifier = modifier
            .widthIn(max = ToastDefaults.MaxWidth)
            .semantics {
                // Assertive for failures the user needs to know about now;
                // polite for confirmations they can hear when convenient.
                liveRegion = if (toast.tone == ToastTone.Danger) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            },
        shape = Theme.shapes.capsule,
        colour = container,
        contentColour = content,
        // A rim on the ones behind, and this is the "shadows for separation" in
        // the report done the only way that works.
        //
        // Every toast in the stack is the same colour, and the shadow is black:
        // `Theme.elevation.high` puts black at 10% over a `surfaceInverse` pill
        // that is already almost black, which is nothing at all. The offsets
        // point downward too, so the card's shadow falls away from the pill
        // above it and the pill's own falls under the card, where the card is
        // drawn over it. There is no arrangement of these shadows that draws a
        // line between two stacked pills in a light theme.
        //
        // A hairline in the toast's *content* colour does, and does it in both
        // themes without being told which one it is in: near-white on a dark
        // pill, near-dark on a light one. It is opaque, so nothing shows through
        // the way the old alpha fade did.
        border = if (depth == 0) {
            null
        } else {
            BorderStroke(Theme.sizing.borderWidth, content.copy(alpha = ToastDefaults.PillRim))
        },
        shadow = Theme.elevation.high,
    ) {
        // A card in front; behind it, a plain capsule of the same colour and
        // nothing else at all.
        //
        // The cards behind used to be full-size cards with their contents faded
        // to nothing — which is why they had to share the front one's width, and
        // why a stretched card showed a blank half. A pill has no content to
        // fade and no message to re-wrap, so the trap that governed this whole
        // component's layout does not apply to it: `AnimatedContent` may animate
        // freely between the two sizes in both directions.
        //
        // Nothing is readable behind the front toast now, which is the point.
        // Dismiss the one in front to see the next.
        AnimatedContent(
            targetState = depth == 0,
            transitionSpec = {
                (fadeIn(motion.springOrTween(motion.springDefault)) togetherWith
                    fadeOut(motion.tweenFast()))
                    // Unclipped, so the message is laid out at the size it is
                    // going to be rather than re-wrapped at every intermediate
                    // width on the way there. The same `SizeTransform` a text
                    // field's error uses, for the same reason.
                    .using(SizeTransform(clip = false))
            },
            label = "toastPresentation",
        ) { front ->
        if (!front) {
            Box(Modifier.size(ToastDefaults.PillWidth, ToastDefaults.PillHeight))
            return@AnimatedContent
        }
        // The toast reserves its controls' target itself, once, rather than
        // letting the platform's minimum push the card around.
        //
        // `Button` is built correctly — `minimumTouchTarget` sits above its
        // `height`, so its *visuals* stay 28dp on every platform. What grew was
        // the layout node: expanded to `minTouchTarget` on a phone, it is a
        // child of this row, the row sizes the surface, and the whole card grew
        // with it. Measured, tapping for the hit area rather than reading the
        // modifier chain, on "Trip saved to favourites." with an action and a
        // close:
        //
        // |            | card       | close target |
        // |------------|------------|--------------|
        // | 24dp (mouse) | 274 x 52dp | 58 x 46dp  |
        // | 48dp (finger)| 295 x 72dp | 47 x 47dp  |
        //
        // The same sentence, 21dp wider and 20dp taller on a phone, which is
        // the 27% more ink `TouchTargetOrderingTest` was reporting.
        //
        // So the row takes the duty on — the same bargain `Toolbar` and
        // `ButtonGroup` make — and guarantees the target by being that tall
        // itself, on every platform. Both cases now measure 274 x 56dp with a
        // 58 x 46dp close, so the card is one size everywhere and **no target
        // got smaller**: the close is wider on a phone than it was, because a
        // button that reserves its own 48dp box inside a row this size is
        // crowding it rather than growing it. The reservation is a decision the
        // component makes once, not one the platform makes for it.
        //
        // Only when there is something to press. A toast with nothing but a
        // message reserves nothing, because there is nothing to hit.
        val hasControls = toast.actionLabel != null || showClose
        val reserved = if (hasControls) {
            Modifier.defaultMinSize(minHeight = ToastDefaults.ControlRowHeight)
        } else {
            Modifier
        }
        CompositionLocalProvider(LocalTouchTargetOwnedByParent provides hasControls) {
        Row(
            modifier = reserved.padding(
                start = Theme.spacing.md,
                end = if (hasControls) Theme.spacing.xs else Theme.spacing.md,
                top = Theme.spacing.xs,
                bottom = Theme.spacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (toast.icon != null) {
                Icon(toast.icon, contentDescription = null, size = Theme.sizing.iconSmall)
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(toast.message, style = Theme.typography.bodySmall)
            }
            if (toast.actionLabel != null) {
                // Two things were making "Retry" read as the end of the
                // sentence rather than as a button, and both had to go.
                //
                // It sat eight pixels from the message, which is the gap
                // between an icon and the words it belongs to — so it looked
                // like it belonged to them. And a ghost button has no ground
                // until you hover it, which on a solid toast leaves the label
                // in exactly the colour and weight of the text beside it.
                Spacer(Modifier.width(ToastDefaults.ActionGap))
                Button(
                    onClick = onAction,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.XSmall,
                    // Derived from the toast's own content colour rather than
                    // taken from the palette: a toast is a solid tone, and an
                    // accent-coloured button on a red one is two brands
                    // arguing. A wash of the colour the text is already in
                    // gives it a ground at every tone without introducing a
                    // second hue.
                    colours = ButtonColours(
                        container = content.copy(alpha = ToastDefaults.ActionGround),
                        content = content,
                        border = null,
                        disabledContainer = Color.Transparent,
                        disabledContent = content.copy(alpha = ToastDefaults.DisabledContent),
                        disabledBorder = null,
                    ),
                ) { +toast.actionLabel }
            }
            if (showClose) {
                IconButton(
                    icon = SystemIcons.Close,
                    contentDescription = closeLabel,
                    onClick = onClose,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.XSmall,
                )
            }
        }
        }
        }
    }
}
