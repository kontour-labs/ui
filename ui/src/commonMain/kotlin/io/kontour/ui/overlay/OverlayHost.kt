package io.kontour.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import io.kontour.ui.foundation.Scrim
import io.kontour.ui.theme.Theme

/**
 * Where an overlay sits in the stack.
 *
 * Ordering is by ordinal, so a [Menu] always draws above a [Sheet] regardless of
 * the order they were opened in. That is the point: without a declared ordering,
 * z-order becomes "whichever happened to be pushed last", which is correct right
 * up until a menu is opened from inside a sheet.
 */
enum class OverlayLayer {
    /** Bottom and side sheets. */
    Sheet,

    /** Dialogs and alerts. */
    Dialog,

    /** Dropdowns, context menus, popovers. */
    Menu,

    /** Tooltips and coach marks. */
    Tooltip,

    /** Toasts and snackbars. Above everything the user can interact with. */
    Toast,

    /** Blocking states the user cannot dismiss — a forced update. */
    Critical,
    ;

    /**
     * True when something in this layer means the user is busy, and an
     * [OverlayQueue] should hold off.
     *
     * [Tooltip] and [Toast] are the two that do not: a toast is a passing
     * confirmation the user is not required to deal with, and the tooltip layer
     * is where coach marks themselves render — a queue that counted its own
     * output as a reason to stop would show a coach mark, immediately suppress
     * itself, and hide it again on the next frame.
     */
    internal val suppressesQueue: Boolean
        get() = this != Tooltip && this != Toast
}

/** How much an overlay dims what is behind it. */
enum class ScrimStyle {
    /** No dimming, and pointer events pass through. Tooltips, toasts. */
    None,

    /** No dimming, but pointer events are blocked. Menus. */
    Transparent,

    /** Dimmed and blocking. Dialogs, modal sheets. */
    Dimmed,
}

/**
 * One overlay in the stack.
 *
 * @param key Identity. Pushing an entry whose key already exists replaces it
 *   rather than stacking a duplicate — which is what stops a double-tap from
 *   opening two identical dialogs.
 */
@Immutable
class OverlayEntry(
    val key: Any,
    val layer: OverlayLayer,
    val scrim: ScrimStyle = ScrimStyle.Dimmed,
    /**
     * What this entry does to the content behind it, beyond dimming it.
     *
     * Defaults to following the scrim: anything that dims also blurs, and
     * anything that does not, does not. A dropdown sits over content the user is
     * still reading and has no business softening it. See [BackdropStyle].
     */
    val backdrop: BackdropStyle =
        if (scrim == ScrimStyle.Dimmed) BackdropStyle.Blur else BackdropStyle.None,
    val dismissOnOutside: Boolean = true,
    /**
     * Whether scrolling the page behind this entry closes it.
     *
     * Follows [dismissOnOutside], which is already the library's way of saying
     * "this is a light thing the user can wave away" — a menu, a dropdown, a
     * tooltip. Those are anchored to something that is about to move, and a
     * scroll is the clearest possible statement that the user has finished with
     * them.
     *
     * It is also what lets the page scroll at all. See [Scrim]'s
     * `onScrollRequest`: a scrim is in front of the page for every kind of
     * pointer event or none, so an overlay that will not close on a scroll is
     * an overlay that stops the page scrolling.
     *
     * Turn it off for anything holding work in progress — a popover with a form
     * in it — and accept that the page is frozen while it is open, which for a
     * form is usually the right trade anyway.
     */
    val dismissOnScroll: Boolean = dismissOnOutside,
    val dismissOnBack: Boolean = true,
    val trapFocus: Boolean = true,
    val dismissLabel: String? = null,
    /**
     * True when this entry animates its own way out and calls `hide` itself.
     *
     * A sheet slides down under its own `SheetState`; the host's fade knows
     * nothing about that. With the host also removing the entry the moment it
     * was dismissed, the two raced: the sheet vanished mid-slide while the scrim
     * faded on the host's schedule, or the scrim outlived a sheet that had
     * already gone. Whoever owns the animation has to own the removal.
     */
    val managesOwnExit: Boolean = false,
    /**
     * How far in this entry is, 0..1, for its scrim to match.
     *
     * `null` — the usual case — means the host's own progress, which is what
     * drives the panel too, so the two are the same number by construction.
     *
     * An entry that animates itself has to say so. A modal sheet slides on its
     * `SheetState`'s spring, which the host knows nothing about: left to the
     * host's progress its scrim would fade over 150ms while the sheet was still
     * travelling, which is the "background disappears before the sheet does"
     * that reads as two separate events rather than one.
     */
    val visibility: (() -> Float)? = null,
    /**
     * Called after this entry has been dismissed.
     *
     * `onDismiss`, not `onDismissRequest`, and the difference is not cosmetic:
     * the host owns whether an entry is showing, so by the time this runs the
     * entry is already gone and there is nothing to decline. Every *component*
     * takes `onDismissRequest` instead, because there the caller owns `visible`
     * and is being asked rather than told.
     */
    val onDismiss: (() -> Unit)? = null,
    val content: @Composable () -> Unit,
)

/**
 * The overlay stack.
 *
 * Obtain it from [LocalOverlayHost]; `KontourTheme` does not install one, because
 * an overlay host has to render somewhere specific in the layout. Install it
 * yourself at the root of your app with [OverlayHost].
 */
@Stable
class OverlayHostState {
    private val entries = mutableStateListOf<OverlayEntry>()

    /**
     * Entries that have been hidden but are still on screen, animating out.
     *
     * They are gone as far as everything *else* is concerned — [isEmpty],
     * [isBusy], [canDismissOnBack] and [dismissTop] all ignore them, so a sheet
     * on its way out neither blocks the back gesture nor keeps a queue waiting.
     * Only the host still renders them, and only until their animation finishes.
     */
    private val leaving = mutableStateListOf<Any>()

    /**
     * Where the host itself sits in the window, so anchors captured in root
     * coordinates can be made host-local. Zero when the host is at the root,
     * which is the common case and the recommended one.
     */
    internal var originInRoot: Offset by mutableStateOf(Offset.Zero)

    /**
     * How far the entry that owns the backdrop is in, or null when none does.
     *
     * A lambda rather than a value, and for the same reason [Scrim] takes one:
     * this changes every frame, and the thing reading it is a `graphicsLayer`
     * wrapped around the *whole application*. Reading a float here in
     * composition would recompose every screen in the app once per frame of
     * every dialog opening.
     *
     * Published by the entry rather than owned here, because the progress
     * animatable lives in `EntryHost` and a sheet supplies its own
     * [OverlayEntry.visibility] instead. Cleared by whoever set it.
     */
    internal var backdropFraction: (() -> Float)? by mutableStateOf(null)

    /** The stack, bottom to top. Sorted by layer, then by push order within a layer. */
    val visible: List<OverlayEntry>
        get() = entries.filter { it.key !in leaving }.sortedBy { it.layer.ordinal }

    /** [visible], plus whatever is still animating out. What the host draws. */
    internal val rendered: List<OverlayEntry>
        get() = entries.sortedBy { it.layer.ordinal }

    /** True while this entry is on its way out rather than on its way in. */
    internal fun isLeaving(key: Any): Boolean = key in leaving

    /** True when nothing at all is showing. */
    val isEmpty: Boolean get() = visible.isEmpty()

    /**
     * True when the user is engaged with something modal — a sheet, dialog,
     * menu or blocking state.
     *
     * What an [OverlayQueue] watches, rather than [isEmpty]: a toast showing is
     * not a reason to postpone onboarding, and the queue's own coach mark
     * certainly is not. See [OverlayLayer.suppressesQueue].
     */
    val isBusy: Boolean get() = visible.any { it.layer.suppressesQueue }

    /** Shows [entry], replacing any existing entry with the same key. */
    fun show(entry: OverlayEntry) {
        // Re-showing something that is mid-exit turns it straight back around
        // rather than stacking a second copy behind the one leaving.
        leaving.remove(entry.key)
        val existing = entries.indexOfFirst { it.key == entry.key }
        if (existing >= 0) {
            entries[existing] = entry
        } else {
            entries.add(entry)
        }
    }

    /**
     * Hides the entry with this key. No-op if it is not showing.
     *
     * Marks it rather than removing it: the host animates it out and calls
     * [finishHiding] when there is nothing left to look at. Removing here is
     * what used to make a dialog vanish instead of fading — its own
     * `AnimatedVisibility(exit = …)` never got a frame to run in.
     */
    fun hide(key: Any) {
        if (entries.any { it.key == key } && key !in leaving) leaving.add(key)
    }

    /** Called by the host once [key] has finished animating out. */
    internal fun finishHiding(key: Any) {
        entries.removeAll { it.key == key }
        leaving.remove(key)
    }

    /** True when an entry with this key is showing. */
    fun isShowing(key: Any): Boolean = visible.any { it.key == key }

    /**
     * True when a back gesture would dismiss something here.
     *
     * What a back handler enables itself on. Not the same as `!isEmpty`: a toast
     * is showing but is not dismissible, and a back press that silently did
     * nothing because a toast happened to be up is worse than one that leaves
     * the screen.
     */
    val canDismissOnBack: Boolean get() = visible.any { it.dismissOnBack }

    /**
     * Dismisses the topmost dismissible entry, and reports whether it did.
     *
     * This is what a back gesture calls. Returns false when nothing was
     * dismissed, so the caller can let the event fall through to navigation
     * rather than swallowing it — a back press that does nothing is worse than
     * one that leaves the screen.
     */
    fun dismissTop(): Boolean {
        val target = visible.lastOrNull { it.dismissOnBack } ?: return false
        target.onDismiss?.invoke()
        if (!target.managesOwnExit) hide(target.key)
        return true
    }

    internal fun dismissOutside(entry: OverlayEntry) {
        if (!entry.dismissOnOutside) return
        entry.onDismiss?.invoke()
        // An entry that animates itself out calls `hide` when it is done. Doing
        // it here as well is what made a sheet disappear mid-slide.
        if (!entry.managesOwnExit) hide(entry.key)
    }
}

/**
 * One overlay, from the frame it is pushed to the frame it is finally gone.
 *
 * The host owns the appear-and-disappear progress rather than each panel owning
 * its own, which is what makes an exit animation possible at all: a panel that
 * set its own `appeared` flag on first composition could only ever run 0 → 1,
 * and every panel in the library did exactly that. `Dialog` even declared an
 * `exit` transition — unreachable code, because the subtree was torn out before
 * `AnimatedVisibility` could observe `visible = false`.
 */
@Composable
private fun EntryHost(
    state: OverlayHostState,
    entry: OverlayEntry,
    index: Int,
    dimmed: Boolean,
) {
    val motion = Theme.motion
    val leaving = state.isLeaving(entry.key)
    val progress = remember { Animatable(0f) }

    LaunchedEffect(leaving) {
        progress.animateTo(
            targetValue = if (leaving) 0f else 1f,
            // Out faster than in. An overlay arriving wants to be noticed; one
            // leaving is already dealt with, and waiting on it is a delay.
            animationSpec = if (leaving) {
                motion.tweenExit()
            } else {
                motion.springOrTween(motion.springSnappy)
            },
        )
        if (leaving) state.finishHiding(entry.key)
    }

    // One lambda for the scrim and the backdrop, remembered rather than built
    // inline: a fresh lambda each time would re-key everything downstream of it.
    val fraction: () -> Float = remember(entry) { entry.visibility ?: { progress.value } }

    // And one for the panel, which is a *different* number: `fraction` may be an
    // entry's own visibility — a sheet's, derived from where it has been dragged
    // to — while the panel's scale and fade always follow the host's own
    // appearance. Both are lambdas for the same reason, and it is the reason
    // this line exists at all: see [LocalOverlayProgress].
    val reader: () -> Float = remember { { progress.value } }

    DisposableEffect(state, dimmed, fraction) {
        if (dimmed) state.backdropFraction = fraction
        onDispose {
            // Only if it is still ours. A second dialog opening over this one
            // takes the backdrop over, and this entry's disposal must not then
            // clear the newer entry's fraction and un-blur the screen.
            if (state.backdropFraction === fraction) state.backdropFraction = null
        }
    }

    if (entry.scrim != ScrimStyle.None) {
        // Remembered, because `Scrim` uses it as its `pointerInput` key.
        //
        // Written inline, this was a fresh lambda on every composition of
        // `EntryHost` — and `EntryHost` recomposes on every frame an overlay is
        // animating, because it reads `progress.value` to publish it below. So
        // the scrim's gesture coroutine was cancelled and relaunched a dozen
        // times per open. Mostly that is waste; the part that is not is that a
        // tap landing in the wrong frame meets a detector that has just been
        // torn down.
        val dismiss: (() -> Unit)? = remember(entry, state, entry.dismissOnOutside, leaving) {
            if (entry.dismissOnOutside && !leaving) {
                { state.dismissOutside(entry) }
            } else {
                // Still consumes taps on the way out; it just no longer offers a
                // dismissal for something already dismissed.
                null
            }
        }
        // Remembered for the same reason `dismiss` is: `Scrim` keys a gesture
        // coroutine on it, and `EntryHost` recomposes every frame an overlay is
        // animating.
        val scrolled: (() -> Unit)? = remember(entry, state, entry.dismissOnScroll, leaving) {
            if (entry.dismissOnScroll && !leaving) {
                { state.dismissOutside(entry) }
            } else {
                null
            }
        }
        Scrim(
            // The same number the panel is drawn with, so the dimming and the
            // thing it dims are one movement — see `Scrim`.
            fraction = fraction,
            onDismissRequest = dismiss,
            dismissLabel = entry.dismissLabel,
            color = if (dimmed) Theme.colors.scrim else Color.Transparent,
            onScrollRequest = scrolled,
        )
    }

    Box(
        Modifier.semantics {
            isTraversalGroup = true
            traversalIndex = (index + 1).toFloat()
        }
    ) {
        CompositionLocalProvider(LocalOverlayProgress provides reader) {
            entry.content()
        }
    }
}

/**
 * How far this overlay is through appearing, or how far back through leaving.
 *
 * Read by every panel that scales and fades — see `Modifier.overlayAppearance`.
 * Defaults to 1 so a panel rendered outside a host, as the contract suite does,
 * is simply visible rather than invisible.
 *
 * ### A lambda, for the same reason the scrim takes one
 *
 * This used to be the `Float` itself, read in composition and handed down, which
 * changed the local's value on every frame of every overlay animation and so
 * recomposed every reader of it sixty times a second. `Scrim` already took a
 * lambda for exactly that reason and says so; the panel was simply left behind.
 * Carrying the read instead of the value moves it into the phase that produces
 * the frame — a `graphicsLayer` block, as `Scrim` uses `drawBehind` — and the
 * local then never changes at all while an overlay moves.
 *
 * **It is not why the backdrop looked like it arrived first.** That was the
 * theory this change was made on, and `DialogBackdropRateTest` disproved it: the
 * curves are identical to three decimal places before and after, on every
 * background it measures. Composition, layout and draw all happen inside one
 * frame, so a value written by the frame clock reaches the screen in that frame
 * whichever phase reads it. The real cause is arithmetic rather than timing, and
 * it is answered in `Modifier.overlayAppearance`. This change stands on the
 * recomposition cost alone, which is reason enough.
 */
internal val LocalOverlayProgress = compositionLocalOf<() -> Float> { { 1f } }

/**
 * The one entry in [stack] whose scrim is actually drawn dark.
 *
 * Every scrim-requesting entry gets a scrim, because each has to block input to
 * whatever is under *it* — a menu opened over a sheet must not let taps through
 * to the sheet. But only the topmost dimming entry draws colour. Dimming each of
 * them would composite: two dialogs over a sheet would darken the background
 * three times over, and the third overlay would sit on near-black.
 */
internal fun topDimmedEntry(stack: List<OverlayEntry>): OverlayEntry? =
    stack.lastOrNull { it.scrim == ScrimStyle.Dimmed }

@Composable
fun rememberOverlayHostState(): OverlayHostState = remember { OverlayHostState() }

/**
 * The host every overlay renders into.
 *
 * Fails loudly rather than defaulting, because an overlay silently rendering
 * nowhere is a bug that looks like a missing feature.
 */
val LocalOverlayHost = staticCompositionLocalOf<OverlayHostState> {
    error(
        "No OverlayHost found. Wrap your app content in OverlayHost { … } — " +
            "dialogs, sheets, menus and toasts all render into it."
    )
}

/**
 * Renders [content], with any active overlays stacked above it.
 *
 * ```
 * KontourTheme {
 *     OverlayHost {
 *         AppRoot()
 *     }
 * }
 * ```
 *
 * **Overlays are clipped to this composable's bounds**, so it belongs at the
 * root. Why the stack renders in-composition rather than in platform windows,
 * and how focus and reading order are handled, are in
 * `ui-docs/content/overlays.md`.
 */
@Composable
fun OverlayHost(
    modifier: Modifier = Modifier,
    state: OverlayHostState = rememberOverlayHostState(),
    content: @Composable () -> Unit,
) {
    val stack = state.rendered
    // Only what is actually still here traps focus — an overlay fading out must
    // hand focus back before it finishes, not after.
    val trapping = state.visible.any { it.trapFocus }

    // Computed here rather than beside the loop below, because the content
    // sibling needs it too — it is the node the backdrop is applied to.
    val dimming = topDimmedEntry(stack)
    val backdrop = dimming?.backdrop ?: BackdropStyle.None

    CompositionLocalProvider(LocalOverlayHost provides state) {
        Box(modifier.fillMaxSize().trackHostOrigin(state).backdropGround(state, backdrop)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .overlayBackdrop(state, backdrop)
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = 0f
                    }
                    // Focus cannot enter content that is behind a modal overlay.
                    .focusProperties { canFocus = !trapping }
            ) {
                content()
            }

            // Each scrim-requesting entry gets its own scrim directly beneath
            // it, so an outside tap always dismisses the thing it is under.
            // Only the topmost dimming one draws colour — see [topDimmedEntry].
            stack.forEachIndexed { index, entry ->
                // Keyed by identity, not by slot. Without this, removing an
                // entry that is not the top one shifts every entry above it
                // down a slot, and each inherits the removed entry's remembered
                // state — including the scrim's colour animatable. A surviving
                // transparent scrim would inherit one sitting at full dim and
                // spend 220ms animating down from it, which is a dim that
                // visibly outlives whatever caused it.
                key(entry.key) {
                    EntryHost(
                        state = state,
                        entry = entry,
                        index = index,
                        dimmed = entry === dimming,
                    )
                }
            }
        }
    }
}
