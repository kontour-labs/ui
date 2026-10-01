package io.kontour.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.platform.libraryRelaysSystemBack
import io.kontour.ui.platform.libraryTracksBackSwipe

/**
 * Where a back gesture aimed at one overlay has got to.
 *
 * One per entry, owned by the host and read by whatever runs the entry's own
 * exit — the host's appearance progress for most entries, a sheet's offset for
 * a sheet — so a gesture moves an overlay only by the animation it already has.
 * Nothing here draws.
 */
@Stable
internal class OverlayBackState {
    /**
     * True while a gesture is being followed: under predictive back, aimed at an
     * entry that may be dismissed. Never true otherwise, so an entry that may
     * not close, or any entry under swipe back, does not move at all.
     */
    var following by mutableStateOf(false)
        internal set

    /** How far through the gesture being followed, 0 to 1. */
    var progress by mutableFloatStateOf(0f)
        internal set

    /**
     * Counts gestures that were followed and then abandoned. Each one asks the
     * entry back to rest, by its own enter or settle animation.
     */
    var cancels by mutableIntStateOf(0)
        internal set

    /**
     * Counts gestures that were followed and let go. Each one dismissed the
     * entry; an entry that animates its own exit and was not closed by its
     * caller goes back to rest on seeing it.
     */
    var commits by mutableIntStateOf(0)
        internal set

    /**
     * The edge whose gesture moves this entry towards the side it leaves by, for
     * an entry that is swiped away — a side sheet — or null.
     *
     * Set by the entry's own panel, which is the only thing that knows which
     * side it is on. Under swipe back, a gesture from this edge closes it, with
     * the finger moving the way the sheet goes: a sheet on the right takes a
     * swipe from the left edge. A plain field, read when a gesture starts.
     */
    var swipeAwayEdge: Int? = null

    /**
     * The entry's own handler, for a gesture its panel tracks itself: see
     * [rememberDirectBack]. Set once, by [rememberOverlayBack].
     */
    internal var handler: OverlayBackHandler? = null
}

/** The back gesture aimed at the overlay this is inside, or null outside one. */
internal val LocalOverlayBack = compositionLocalOf<OverlayBackState?> { null }

/** The info every overlay handler reports: nothing but that it is one. */
private object OverlayBackInfo : NavigationEventInfo()

/**
 * One entry's back handler.
 *
 * Enabled whenever its entry is the one back reaches, on every platform, so
 * back never goes past an open overlay to the page behind it — or, on Android,
 * out of the app. What it then does is [closes]'s answer:
 *
 * - **Yes** — predictive back, and an entry that may be dismissed: a gesture
 *   runs the entry's own exit backwards by its progress, letting go dismisses
 *   it, and a key or a button dismisses it at once.
 * - **No** — an entry that may not be dismissed, or any entry under swipe back:
 *   nothing moves and nothing closes. On iOS a sheet is dragged down and a
 *   dialog is answered; back is not a way out of either there, and inventing
 *   one would be a new thing to learn. A stack *inside* the entry still goes
 *   back, because its handler registers after this one.
 * - **The exception is a side sheet**, which iOS does swipe away: under swipe
 *   back a gesture moving towards its own edge — see
 *   [OverlayBackState.swipeAwayEdge] — follows and closes it, and so does a key.
 *   The sheet's own drag, under either style, reaches this handler directly,
 *   by [rememberDirectBack].
 *
 * A plain `NavigationEventHandler` rather than `NavigationBackHandler`, because
 * the host decides which entry may answer by enabling its *dispatcher*, and
 * the progress has to be readable where the entry's animation is driven.
 */
internal class OverlayBackHandler(
    private val back: OverlayBackState,
    /**
     * Whether back closes this entry now, for a gesture from the given edge, or
     * null for a key or a button; read at each event, never cached.
     */
    private val closes: (edge: Int?) -> Boolean,
    /** Called when back closes the entry. */
    private val onCommit: () -> Unit,
) : NavigationEventHandler<NavigationEventInfo>(OverlayBackInfo, true) {

    /** The edge of the gesture under way, or null between gestures. */
    private var edge: Int? = null

    override fun onBackStarted(event: NavigationEvent) = started(event)

    override fun onBackProgressed(event: NavigationEvent) = progressed(event)

    override fun onBackCancelled() = cancelled()

    override fun onBackCompleted() = completed()

    // The same four, callable from a [DirectBackHandler] as well as by the
    // dispatcher this is registered with.

    internal fun started(event: NavigationEvent) {
        edge = event.swipeEdge
        if (!closes(event.swipeEdge)) return
        back.progress = event.progress
        back.following = true
    }

    internal fun progressed(event: NavigationEvent) {
        if (back.following) back.progress = event.progress
    }

    internal fun cancelled() {
        edge = null
        if (!back.following) return
        back.following = false
        back.cancels++
    }

    internal fun completed() {
        val followed = back.following
        val from = edge
        back.following = false
        edge = null
        if (!closes(from)) return
        if (followed) back.commits++
        onCommit()
    }
}

/** Hands whatever reaches it straight to [target]. */
private class DirectBackHandler(
    private val target: OverlayBackHandler,
) : NavigationEventHandler<NavigationEventInfo>(OverlayBackInfo, true) {
    override fun onBackStarted(event: NavigationEvent) = target.started(event)

    override fun onBackProgressed(event: NavigationEvent) = target.progressed(event)

    override fun onBackCancelled() = target.cancelled()

    override fun onBackCompleted() = target.completed()
}

/**
 * A back dispatcher that reaches [back]'s entry and nothing else, for a gesture
 * the entry's own panel tracks — a side sheet dragged towards its edge — or
 * null outside an overlay.
 *
 * A root of its own, not a child of the entry's: through the entry's
 * dispatcher, a drag on a sheet would reach whatever inside it registered
 * later — a stack with somewhere to go back to — and pop a page instead of
 * moving the sheet the finger is on. The platform's gestures still go the
 * usual way, through the entry's dispatcher, so a stack inside a sheet still
 * goes back first to those.
 */
@Composable
internal fun rememberDirectBack(back: OverlayBackState?): NavigationEventDispatcher? {
    val handler = back?.handler ?: return null
    val dispatcher = remember(handler) {
        NavigationEventDispatcher().also { it.addHandler(DirectBackHandler(handler)) }
    }
    DisposableEffect(dispatcher) {
        onDispose { dispatcher.dispose() }
    }
    return dispatcher
}

/**
 * A dispatcher under whatever owns back above this point, whose [enabled] is
 * applied in the same frame it changes.
 *
 * `rememberNavigationEventDispatcherOwner` exists and is not used, because it
 * applies its flag in a `LaunchedEffect` — a frame late, which is a frame in
 * which a back press opened over an overlay can still reach the page beneath.
 * And it throws when nothing above provides an owner, which a screenshot
 * harness or a preview does not; this returns null there instead, and the
 * overlays simply do not answer back.
 */
@Composable
internal fun rememberChildBackDispatcher(
    enabled: Boolean,
    parent: NavigationEventDispatcherOwner? = LocalNavigationEventDispatcherOwner.current,
): NavigationEventDispatcherOwner? {
    val owner = remember(parent) {
        parent?.let { above ->
            val dispatcher = NavigationEventDispatcher(above.navigationEventDispatcher)
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
            }
        }
    }
    SideEffect { owner?.navigationEventDispatcher?.isEnabled = enabled }
    DisposableEffect(owner) {
        onDispose { owner?.navigationEventDispatcher?.dispose() }
    }
    return owner
}

/**
 * Registers [handler] on [owner] for as long as this is composed, before
 * anything composed after it — which is what lets a stack inside a sheet win
 * over the sheet itself while it has somewhere to go back to.
 */
@Composable
internal fun RegisterBackHandler(owner: NavigationEventDispatcherOwner?, handler: NavigationEventHandler<*>) {
    DisposableEffect(owner, handler) {
        owner?.navigationEventDispatcher?.addHandler(handler)
        onDispose { handler.remove() }
    }
}

/** The per-entry back state and its handler, remembered for one entry. */
@Composable
internal fun rememberOverlayBack(
    closes: (back: OverlayBackState, edge: Int?) -> Boolean,
    onCommit: () -> Unit,
): Pair<OverlayBackState, OverlayBackHandler> {
    val back = remember { OverlayBackState() }
    val latestCloses by rememberUpdatedState(closes)
    val latestCommit by rememberUpdatedState(onCommit)
    val handler = remember(back) {
        OverlayBackHandler(back, { edge -> latestCloses(back, edge) }) { latestCommit() }.also { back.handler = it }
    }
    return back to handler
}

/**
 * Whether an [OverlayHost] here takes the back swipe over from the platform.
 *
 * [libraryTracksBackSwipe] — iOS — at the top, and false beneath the host that
 * did, so one app has one tree and one edge pan however many hosts it nests.
 * Tests provide true to exercise it on the desktop.
 */
internal val LocalTakesBackSwipe = staticCompositionLocalOf { libraryTracksBackSwipe }

/**
 * Whether an [OverlayHost] here relays the platform's back gesture through a
 * tree of its own: [libraryRelaysSystemBack] — Android — at the top, and false
 * beneath the host that does. See `SystemBackRelay`. Tests provide true to
 * exercise it on the desktop.
 */
internal val LocalRelaysSystemBack = staticCompositionLocalOf { libraryRelaysSystemBack }

/**
 * A back dispatcher of the host's own, a root rather than a child of the
 * platform's, so nothing registered under it arms the platform's edge pan.
 */
@Composable
internal fun rememberOwnBackDispatcher(): NavigationEventDispatcherOwner {
    val owner = remember {
        val dispatcher = NavigationEventDispatcher()
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.navigationEventDispatcher.dispose() }
    }
    return owner
}

/**
 * Escape as back, for a host that has taken back over from the platform, which
 * no longer hears it. Only when something would answer, so an Escape nothing
 * wants still reaches whatever is outside.
 */
internal class KeyBackInput : NavigationEventInput() {
    private var armed = false

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        armed = hasEnabledHandlers
    }

    override fun onRemoved() {
        armed = false
    }

    /** Goes back, if anything would; says whether it did. */
    fun press(): Boolean {
        if (!armed) return false
        dispatchOnBackCompleted()
        return true
    }
}
