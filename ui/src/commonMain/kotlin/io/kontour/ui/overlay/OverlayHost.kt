package io.kontour.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.platform.WarnIfWindowPansForKeyboard
import io.kontour.ui.components.text.TextContextMenuHost
import io.kontour.ui.foundation.ScrimLayer
import io.kontour.ui.input.clearFocusOnTap
import io.kontour.ui.input.reportsPresses
import io.kontour.ui.motion.BackPan
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.EdgePanWidth
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.motion.RelaySystemBack
import io.kontour.ui.motion.rememberSwipeBackInput
import io.kontour.ui.motion.swipeBack
import io.kontour.ui.theme.Theme
import io.kontour.ui.dragdrop.DragHost
import io.kontour.ui.dragdrop.DragLayer
import io.kontour.ui.dragdrop.handleKey
import io.kontour.ui.dragdrop.LocalDragHost
import io.kontour.ui.dragdrop.LocalOverlayLayer

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
     * It is also what lets the page scroll at all. See [io.kontour.ui.foundation.Scrim]'s
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
) {
    /**
     * A rectangle, in root coordinates, where a press goes past this entry's
     * scrim to whatever is underneath it. Null, which is every entry but one,
     * and the scrim takes every press it covers.
     *
     * The one is a combobox's list, whose field the user is still typing in:
     * see `AnchoredDropdownMenuImpl`. Read in the scrim's measure pass, so a
     * rectangle that moves takes its way through with it.
     *
     * Set once, as the entry is built, and never after — which is what keeps
     * the class's `@Immutable` true. Not a constructor parameter, because the
     * constructor is public and the need is the library's own.
     */
    internal var passThrough: (() -> Rect?)? = null

    /**
     * Whether the user may dismiss this entry, asked at the moment they try —
     * or null, which is every entry but the library's own modal ones, to go by
     * [dismissOnOutside], [dismissOnBack] and [dismissOnScroll] as they were
     * built.
     *
     * Set, it answers for all three. The flags are fixed when the entry is
     * published, and a component publishes its entry when it is shown, so a
     * `dismissible` that changed while it was up changed nothing: a form sheet
     * opened with `dismissible = !form.dirty` could still be tapped away after
     * the reader had typed into it, and the typing went with it. The components
     * that take `dismissible` hand it over through this instead, read live.
     *
     * Set once, as the entry is built, like [passThrough], and for the same
     * reason not a constructor parameter.
     */
    internal var dismissibleNow: (() -> Boolean)? = null

    /** [dismissOnOutside], or [dismissibleNow] where there is one. */
    internal val outsideDismisses: Boolean get() = dismissibleNow?.invoke() ?: dismissOnOutside

    /** [dismissOnBack], or [dismissibleNow] where there is one. */
    internal val backDismisses: Boolean get() = dismissibleNow?.invoke() ?: dismissOnBack

    /** [dismissOnScroll], or [dismissibleNow] where there is one. */
    internal val scrollDismisses: Boolean get() = dismissibleNow?.invoke() ?: dismissOnScroll
}

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
     * Entries animating their own way out ([OverlayEntry.managesOwnExit]) that
     * have let go of the page: still drawn, no longer in its way. See [letGo].
     */
    private val lettingGo = mutableStateListOf<Any>()

    /**
     * Where the host itself sits in the window, so anchors captured in root
     * coordinates can be made host-local. Zero when the host is at the root,
     * which is the common case and the recommended one.
     */
    internal var originInRoot: Offset by mutableStateOf(Offset.Zero)

    /**
     * How far each of the host's edges is from the same edge of the root, in
     * pixels. All zero for a host that fills the window.
     *
     * Read by anchored overlays to take only the part of a window inset that
     * reaches into this host: a status bar is 47dp off the top of the window, and
     * nothing at all off the top of a card half-way down the page. See
     * `AnchoredOverlayLayout`.
     */
    internal var edgesFromRoot: AnchorInsets by mutableStateOf(AnchorInsets.None)

    /**
     * The bars along the foot of the window, by whoever reported them, as their
     * top and bottom edges in root pixels: what a bottom [ToastHost] keeps
     * clear of. A [io.kontour.ui.nav.NavBar] reports its own while it is on
     * screen, and takes it back when it goes. See [clearedByToasts].
     */
    internal val bottomBars = mutableStateMapOf<Any, BarEdges>()

    /**
     * How far in each entry is, by key: what the scrims and the backdrop are
     * drawn from.
     *
     * Lambdas rather than values, and for the same reason [io.kontour.ui.foundation.Scrim] takes one:
     * these change every frame, and one of the things reading them is a
     * `graphicsLayer` wrapped around the *whole application*. Reading a float
     * here in composition would recompose every screen in the app once per
     * frame of every dialog opening.
     *
     * Published by each entry rather than owned here, because the progress
     * animatable lives in `EntryHost` and a sheet supplies its own
     * [OverlayEntry.visibility] instead.
     *
     * **One per entry, not one for the stack.** This was a single slot that the
     * topmost dimmed entry took over, so a dialog opening over a sheet handed
     * the page from the sheet's fraction, at 1, to the dialog's, at 0: the page
     * came forward to full size in one frame and receded again only once the
     * dialog had gone. Every entry keeps its own now, and what is behind them
     * is drawn from all of them — see [furthestIn] and [scrimShare].
     */
    private val fractions = mutableStateMapOf<Any, () -> Float>()

    /** Publishes [fraction] as how far in the entry [key] is. */
    internal fun publishFraction(key: Any, fraction: () -> Float) {
        fractions[key] = fraction
    }

    /** Withdraws [fraction], if it is still the one published for [key]. */
    internal fun withdrawFraction(key: Any, fraction: () -> Float) {
        if (fractions[key] === fraction) fractions.remove(key)
    }

    /** How far in the entry [key] is, 0..1; 0 for one that has not published. */
    internal fun fractionOf(key: Any): Float = (fractions[key]?.invoke() ?: 0f).coerceIn(0f, 1f)

    /**
     * The furthest in of [keys]: how far an effect that any of them asks for
     * has gone.
     *
     * A maximum rather than a sum, so a second entry asking for what is
     * already in place — a dialog over a sheet, a sheet over a sheet — neither
     * doubles it nor, arriving from zero, takes it away.
     */
    internal fun furthestIn(keys: List<Any>): Float {
        var furthest = 0f
        for (key in keys) furthest = maxOf(furthest, fractionOf(key))
        return furthest
    }

    /** The stack, bottom to top. Sorted by layer, then by push order within a layer. */
    val visible: List<OverlayEntry>
        get() = entries.filter { it.key !in leaving && it.key !in lettingGo }.sortedBy { it.layer.ordinal }

    /** [visible], plus whatever is still animating out. What the host draws. */
    internal val rendered: List<OverlayEntry>
        get() = entries.sortedBy { it.layer.ordinal }

    /** True while the host is animating this entry out. */
    internal fun isLeaving(key: Any): Boolean = key in leaving

    /**
     * True once this entry is on its way out, whoever is animating it: it is
     * drawn, and nothing else about it — its scrim takes no taps, it holds no
     * focus, back goes past it.
     */
    internal fun isGoing(key: Any): Boolean = key in leaving || key in lettingGo

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
        lettingGo.remove(entry.key)
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

    /**
     * An entry that animates its own way out has started to: from here on it is
     * drawn and nothing more. Its scrim lets taps through to the page, the page
     * takes focus and is read again, and back goes past it — while it slides or
     * folds away on its own clock, until it calls [hide].
     *
     * Reported from a phone as the page taking "an extra split second" to answer
     * after a sheet had gone: the sheet held the page for the whole of its slide,
     * and then for the host's fade on top.
     */
    internal fun letGo(key: Any) {
        if (entries.any { it.key == key } && key !in leaving && key !in lettingGo) lettingGo.add(key)
    }

    /** Undoes [letGo]: the entry turned back before it had gone. */
    internal fun takeBack(key: Any) {
        lettingGo.remove(key)
    }

    /** Called by the host once [key] has finished animating out. */
    internal fun finishHiding(key: Any) {
        entries.removeAll { it.key == key }
        leaving.remove(key)
        lettingGo.remove(key)
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
    val canDismissOnBack: Boolean get() = visible.any { it.backDismisses }

    /**
     * Dismisses the topmost dismissible entry, and reports whether it did.
     *
     * This is what a back gesture calls. Returns false when nothing was
     * dismissed, so the caller can let the event fall through to navigation
     * rather than swallowing it — a back press that does nothing is worse than
     * one that leaves the screen.
     */
    fun dismissTop(): Boolean {
        val target = visible.lastOrNull { it.backDismisses } ?: return false
        target.onDismiss?.invoke()
        if (!target.managesOwnExit) hide(target.key)
        return true
    }

    /**
     * The entry a back gesture goes to, or null when it should go past the
     * overlays to the page.
     *
     * The topmost entry that dismisses on back, *or that dims*: a dimmed entry
     * says the page is unavailable, and a back gesture reaching past a dialog
     * that may not close — to pop the screen behind it — would be the worst of
     * both. Such an entry takes the gesture and refuses it. A toast or a plain
     * tooltip neither dims nor dismisses on back, so back goes past it.
     */
    internal val backTarget: OverlayEntry?
        get() = visible.lastOrNull { it.backDismisses || it.scrim == ScrimStyle.Dimmed }

    /** What a completed back gesture aimed at [entry] does: [dismissTop], for that entry. */
    internal fun dismissForBack(entry: OverlayEntry) {
        if (!entry.backDismisses || isGoing(entry.key)) return
        entry.onDismiss?.invoke()
        if (!entry.managesOwnExit) hide(entry.key)
    }

    internal fun dismissOutside(entry: OverlayEntry) {
        if (!entry.outsideDismisses) return
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
    /** The keys of every entry that dims, bottom to top. */
    dimmers: List<Any>,
    /** The keys of the entries above this one that blur what is behind them. */
    blurredBy: List<Any>,
    takesBack: Boolean,
    /** Told which entry the focus is in, so a modal is not taken out of its own. */
    opened: OpenedModals,
) {
    val motion = Theme.motion
    val leaving = state.isLeaving(entry.key)
    // On its way out by either route. The page is the reader's again from here,
    // not from when the last of the entry has been drawn.
    val going = state.isGoing(entry.key)
    val progress = remember { Animatable(0f) }

    // **The keyboard goes as the entry does**, not after it. A field in a
    // sheet or a dialog kept its focus, and so the keyboard, until the entry
    // had finished leaving and its content was gone: the length of a sheet's
    // slide down with the keyboard still up over it, reported from Android as
    // *"closing sheets with a keyboard open makes the keyboard take just like
    // half a second too long to close"*. UIKit lets go of the keyboard as a
    // dismissal starts. Only for focus in this entry: a sheet that leaves the
    // page usable leaves the page's own field its keyboard.
    val keyboard = LocalSoftwareKeyboardController.current
    val focused = remember { mutableStateOf(false) }
    LaunchedEffect(going) {
        if (going && focused.value) keyboard?.hide()
    }

    // Back, for this entry alone. Its dispatcher is enabled only while it is
    // the entry back goes to, and its handler is registered before its content
    // is composed, so a stack inside a sheet answers first while it has
    // somewhere to go back to. See `OverlayBack.kt`.
    val backOwner = rememberChildBackDispatcher(enabled = takesBack && !going)
    val backStyle = LocalBackStyle.current
    val (back, backHandler) = rememberOverlayBack(
        // Back closes an overlay under predictive back, and only one that may be
        // dismissed. Under swipe back — iOS — only what iOS swipes away: a side
        // sheet, by a gesture moving towards its own edge, or by a key. A
        // bottom sheet is dragged down there, and a dialog answered.
        closes = { back, edge ->
            entry.backDismisses && when (backStyle) {
                BackStyle.Predictive -> true
                BackStyle.Swipe -> back.swipeAwayEdge.let { away -> away != null && (edge == null || edge == away) }
            }
        },
        onCommit = { state.dismissForBack(entry) },
    )
    RegisterBackHandler(backOwner, backHandler)

    // A gesture runs the entry's own appearance backwards — the scale and fade
    // it arrived with, or its slide — so it leaves by the animation it already
    // has, and the scrim and the receding page, which read the same number,
    // follow with it. Let go, and `leaving` carries the same exit on from the
    // pose the hand left; abandon it, and the entry's own arrival brings it
    // back. An entry that animates its own exit — a sheet — reads the gesture
    // itself.
    if (!entry.managesOwnExit) {
        LaunchedEffect(back) {
            snapshotFlow { if (back.following) back.progress else null }
                .collect { gesture -> if (gesture != null) progress.snapTo(1f - gesture) }
        }
        LaunchedEffect(back.cancels) {
            if (back.cancels > 0 && !state.isLeaving(entry.key)) {
                progress.animateTo(1f, motion.springOrTween(motion.springSnappy))
            }
        }
    }

    LaunchedEffect(leaving) {
        // An entry that animates itself out calls `hide` when it has finished:
        // there is nothing left to fade. Fading it anyway kept its scrim over
        // the page for another 150ms after the sheet had gone.
        if (leaving && entry.managesOwnExit) {
            state.finishHiding(entry.key)
            return@LaunchedEffect
        }
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
    // Read where it is used, in measure, so a panel can hold its placement for the
    // length of its exit without recomposing to find out it is leaving.
    val leavingReader: () -> Boolean = remember(state, entry.key) { { state.isLeaving(entry.key) } }

    DisposableEffect(state, entry.key, fraction) {
        state.publishFraction(entry.key, fraction)
        onDispose { state.withdrawFraction(entry.key, fraction) }
    }

    // This entry's share of the dimming: all of it on its own, and less as
    // something that dims arrives above it, so what is under the two is never
    // darker than one scrim makes it — see [scrimShare]. Read in draw, as the
    // fraction is.
    val dimIndex = dimmers.indexOf(entry.key)
    val scrimAlpha = Theme.colours.scrim.alpha
    val scrimFraction: () -> Float = remember(state, dimmers, dimIndex, scrimAlpha, fraction) {
        when {
            dimIndex < 0 -> fraction
            // Alone, the share is the fraction itself, and the lookup is not
            // worth doing on every frame of every sheet.
            dimmers.size == 1 -> fraction
            else -> { { scrimShare(dimmers.map { state.fractionOf(it) }, scrimAlpha, dimIndex) } }
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
        //
        // Read here rather than off the entry inside the lambda, so a sheet or a
        // dialog whose `dismissible` changes while it is up — see
        // [OverlayEntry.dismissibleNow] — gives up or takes back its scrim's tap
        // and the dismiss action a screen reader is offered with it.
        val outside = entry.outsideDismisses
        val dismiss: (() -> Unit)? = remember(entry, state, outside, going) {
            if (outside && !going) {
                { state.dismissOutside(entry) }
            } else {
                // No dismissal for something already dismissed, or for
                // something that may not be dismissed at all.
                null
            }
        }
        // Remembered for the same reason `dismiss` is: `Scrim` keys a gesture
        // coroutine on it, and `EntryHost` recomposes every frame an overlay is
        // animating.
        val onScroll = entry.scrollDismisses
        val scrolled: (() -> Unit)? = remember(entry, state, onScroll, going) {
            if (onScroll && !going) {
                { state.dismissOutside(entry) }
            } else {
                null
            }
        }
        // The entry's way through, in the host's coordinates rather than the
        // root's, which is what the scrim is laid out in. Remembered for the
        // same reason as the two above.
        val hole: (() -> Rect?)? = remember(entry, state) {
            entry.passThrough?.let { inRoot -> { inRoot()?.translate(-state.originInRoot) } }
        }
        ScrimLayer(
            // The same number the panel is drawn with, so the dimming and the
            // thing it dims are one movement — see `Scrim` — and, with
            // something dimming above it, its share of that: see [scrimShare].
            fraction = scrimFraction,
            onDismissRequest = dismiss,
            dismissLabel = entry.dismissLabel,
            colour = if (dimIndex >= 0) Theme.colours.scrim else Color.Transparent,
            onScrollRequest = scrolled,
            // Once it is going, a tap is the page's: what the scrim was keeping
            // it from is already on its way off the screen. Material's scrim
            // does the same.
            blocks = !going,
            hole = hole,
        )
    }

    Box(
        Modifier
            .semantics {
                isTraversalGroup = true
                traversalIndex = (index + 1).toFloat()
            }
            // Softened with the page by whatever above it blurs what is behind
            // it: a dialog over a sheet blurs the sheet too, not only the strip
            // of page beside it. See [blurredBehind].
            .then(if (blurredBy.isNotEmpty()) Modifier.blurredBehind(state, blurredBy) else Modifier)
            .onFocusChanged { focus ->
                focused.value = focus.hasFocus
                if (focus.hasFocus) opened.focusIn = entry.key else if (opened.focusIn == entry.key) opened.focusIn = null
            }
            // A field in the overlay asking to be seen never moves the window. See [endsBringIntoView].
            .endsBringIntoView()
    ) {
        CompositionLocalProvider(
            LocalOverlayProgress provides reader,
            LocalOverlayLeaving provides leavingReader,
            LocalOverlayBack provides back,
            LocalOverlayLayer provides index + 1,
        ) {
            if (backOwner != null) {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides backOwner) {
                    Recaught(entry.content)
                }
            } else {
                Recaught(entry.content)
            }
        }
    }
}

/**
 * [content], composed once more without skipping in the frame after it first
 * is, so a change its caller made while it was being added is not lost.
 *
 * An overlay's content is the caller's lambda, drawn here at the root. When the
 * caller's values change, Compose updates that lambda in place and tells the
 * places that invoked it to recompose; this is one of them. But the caller is
 * usually in a subcomposition of its own — a `Scaffold`'s body, a lazy item —
 * which recomposes after the root in a frame. In the frame an overlay first
 * appears, then, the root has drawn its content from the lambda as it was, and
 * has not yet applied it, when the caller updates the lambda; Compose finds the
 * place it should tell is not in the composition yet, and forgets it. The
 * content stays as it opened until something inside it changes for itself.
 *
 * Reported from an admin as a rollback dialog stuck on "Comparing…", its button
 * disabled, about one time in three: whenever the comparison came back in the
 * frame the dialog appeared. A change to [LocalRecaught] a frame later makes
 * everything under it recompose without skipping, once, from the lambdas as
 * they are; after that the root holds it and is told of changes as it should
 * be. Here, so it holds for every overlay, and around both of `EntryHost`'s
 * calls, so content moved from one to the other is caught again too.
 */
@Composable
private fun Recaught(content: @Composable () -> Unit) {
    var caught by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        caught = true
    }
    CompositionLocalProvider(LocalRecaught provides caught, content = content)
}

/**
 * Read by nothing: its change is what makes [Recaught]'s content recompose
 * without skipping, which a static local's does to everything it provides to.
 */
private val LocalRecaught = staticCompositionLocalOf { false }

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
 * Whether this overlay is on its way out. A lambda, read where it matters — an
 * anchored panel reads it in measure to leave from the side it was on. See
 * `AnchoredOverlayLayout`.
 */
internal val LocalOverlayLeaving = compositionLocalOf<() -> Boolean> { { false } }

/**
 * The topmost entry in [stack] that dims: the one the page is shut behind, for
 * a screen reader. See `OverlayHost`.
 */
internal fun topDimmedEntry(stack: List<OverlayEntry>): OverlayEntry? =
    stack.lastOrNull { it.scrim == ScrimStyle.Dimmed }

/**
 * How much of a full scrim the dimmed entry at [index] draws, given how far in
 * each dimmed entry is, bottom to top, and the scrim colour's own [alpha].
 *
 * Every dimmed entry gets a scrim, directly under itself, because each has to
 * dim what is under *it*: a dialog over a sheet darkens the sheet as well as
 * the page. Drawn at full strength they would composite, and two dialogs over a
 * sheet would sit on near-black. So each is drawn at the share that brings
 * everything under it to what **one** scrim does, and no darker.
 *
 * Worked from the top down. What is under entry `k` is darkened by its own
 * scrim and every one above it, and should come to the darker of its own full
 * scrim and what is above already does:
 *
 * ```
 * dark(k)  = max(alpha * fraction(k), dark(k + 1))
 * share(k) = (dark(k) - dark(k + 1)) / (1 - dark(k + 1))
 * ```
 *
 * The second line is solving `1 - (1 - share)(1 - above) = dark` for `share`,
 * which is how two layers of one colour composite. So a sheet under a dialog
 * arriving from nothing keeps the page exactly as dark as it was on every
 * frame, handing the dimming over to the dialog's scrim as it comes in rather
 * than fading its own out on a clock of its own. This replaced "only the
 * topmost dims", which was right at rest and wrong in between: the sheet's scrim
 * went clear over 220ms while the dialog's came in on the dialog's spring, and
 * on the way out the page went light and then dark again.
 *
 * @return A fraction of the full scrim, 0..1, for `ScrimLayer` to multiply
 *   the colour's alpha by.
 */
internal fun scrimShare(fractions: List<Float>, alpha: Float, index: Int): Float {
    if (alpha <= 0f || index !in fractions.indices) return 0f
    var above = 0f
    for (k in fractions.lastIndex downTo index) {
        val dark = maxOf(alpha * fractions[k].coerceIn(0f, 1f), above)
        if (k == index) {
            val share = if (above >= 1f) 0f else (dark - above) / (1f - above)
            return (share / alpha).coerceIn(0f, 1f)
        }
        above = dark
    }
    return 0f
}

/**
 * The host, for something that only *helps* one if it is there — a bar telling
 * toasts where it is — and works the same without. [LocalOverlayHost] fails
 * loudly instead, which is right for anything that renders into it.
 */
internal val LocalOverlayHostOrNull = staticCompositionLocalOf<OverlayHostState?> { null }

/** A bar's top and bottom edges, in root pixels. */
@Immutable
internal data class BarEdges(val top: Float, val bottom: Float)

/**
 * Tells the [OverlayHost] this is a bar along the foot of the window, which a
 * bottom toast stack stands clear of: *"can we make it so toasts clear the
 * navbar (if applicable)?"* A navigation bar is a component, not an inset, so
 * nothing in the window's insets says where it is; the bar does, every time
 * it is placed, and takes it back when it leaves.
 *
 * Its own place in the window, measured, rather than its height: a floating
 * bar stands above the gesture bar, a docked one runs under it, and a bar
 * sliding away with its page moves with every frame. Only a bar near the foot
 * counts — see [ToastHost] — so one drawn mid-page, in a showcase, does not
 * lift the toasts.
 */
@Composable
internal fun Modifier.clearedByToasts(): Modifier {
    val host = LocalOverlayHostOrNull.current ?: return this
    val key = remember { Any() }
    DisposableEffect(host, key) { onDispose { host.bottomBars.remove(key) } }
    return onGloballyPositioned { placed ->
        val top = placed.positionInRoot().y
        host.bottomBars[key] = BarEdges(top, top + placed.size.height)
    }
}

/** Remembers an [OverlayHostState], the stack an [OverlayHost] draws. */
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
    /**
     * Whether a press that nothing else handles takes focus off whatever has
     * it — which in practice means a text field, its caret and its keyboard.
     *
     * On by default because the alternative is what was reported: a field kept
     * its focus ring and its keyboard wherever else you pressed, on every
     * platform, because Compose hands focus to a field and nothing ever asks
     * for it back. Switch it off for a screen that is meant to hold the
     * keyboard up — a chat composer, a search-as-you-type page — where dropping
     * focus on a stray press is the wrong answer.
     */
    clearFocusOnTap: Boolean = true,
    content: @Composable () -> Unit,
) {
    val stack = state.rendered
    // Only what is actually still here traps focus — an overlay fading out must
    // hand focus back before it finishes, not after.
    val trapping = state.visible.any { it.trapFocus }

    // Computed here rather than beside the loop below, because the content
    // sibling needs them too — it is the node the backdrop is applied to.
    //
    // **Every entry that dims, not the topmost.** The page is behind all of
    // them, and each keeps doing to it what it asked to for as long as it is
    // in: a dialog over a sheet adds its blur to the sheet's recede rather than
    // replacing it. Taking the backdrop from the topmost alone brought the page
    // forward to full size the moment a dialog opened over a sheet, and pushed
    // it back again when the dialog had gone. A backdrop still needs a dim —
    // see [OverlayEntry.backdrop].
    val dimmers = stack.filter { it.scrim == ScrimStyle.Dimmed }
    val dimmerKeys = dimmers.map { it.key }
    val receding = dimmers.filter { it.backdrop.scales }.map { it.key }
    val blurring = dimmers.filter { it.backdrop.blurs }.map { it.key }
    // What the page is shut behind for a reader: the topmost that dims, but
    // only while it is still here. One on its way out gives the page back to a screen reader as
    // it gives it back to a finger — see `OverlayHostState.letGo`.
    val shutBy = topDimmedEntry(state.visible)

    // **A modal opening takes the focus off whatever had it**, so a text field
    // under it lets go, and on a phone its keyboard goes with it. Asked for in
    // those words: "when a dialog or something pops up, any text fields lose
    // focus (and therefore, the ime will hide)". The field kept its focus and
    // its caret behind the dim, and its keyboard stayed up over the dialog.
    //
    // Only when a *new* modal comes to the top: one that dims, so a menu or a
    // combobox's list — opened from a field, which goes on typing — leaves the
    // focus where it is, and one that was not already open below, so closing
    // a dialog over a sheet does not take the focus off the sheet again. And
    // never from inside the modal itself: one that has put the focus in its
    // own field by the time it counts as on top — an expanded search, which
    // is only on top once its morph is under way — keeps it.
    //
    // A `SideEffect`, which runs as this composition is applied: before any
    // effect launched by the modal's own content, so a modal that asks for the
    // focus in itself — a command palette's field — has it given to it after
    // the page's is taken away, not before.
    val focusManager = LocalFocusManager.current
    val opened = remember { OpenedModals() }
    SideEffect {
        val top = shutBy?.key
        if (top != null && top != opened.top && top !in opened.dimmed && opened.focusIn != top) {
            focusManager.clearFocus(force = true)
        }
        opened.top = top
        opened.dimmed = state.visible.filter { it.scrim == ScrimStyle.Dimmed }.mapTo(HashSet()) { it.key }
    }

    // A window that pans for the keyboard lifts a sheet twice, and only the app
    // can change that. Said once, in a debuggable Android app.
    WarnIfWindowPansForKeyboard()

    // Back goes to the page only while no overlay takes it. The page's
    // dispatcher is disabled rather than its handlers outranked, so it does not
    // matter what the page registers or when: a navigation stack composed after
    // a dialog opened still cannot pop from behind it.
    //
    // On iOS the outermost host builds that tree on a dispatcher of its own
    // rather than under the platform's, and tracks the swipe itself: see
    // `libraryTracksBackSwipe`. On Android it builds one too, and relays the
    // system's gesture into it, so a released gesture finishes at the finger's
    // speed: see `SystemBackRelay`. Elsewhere the tree hangs off the
    // platform's, as Escape and the browser's back arrive there.
    val platformBack = LocalNavigationEventDispatcherOwner.current
    val tracksSwipe = LocalTakesBackSwipe.current
    val relays = LocalRelaysSystemBack.current && !tracksSwipe && platformBack != null
    val ownBack = if (tracksSwipe || relays) rememberOwnBackDispatcher() else null
    val backRoot = ownBack ?: platformBack
    if (relays && ownBack != null) {
        RelaySystemBack(platform = platformBack.navigationEventDispatcher, into = ownBack.navigationEventDispatcher)
    }
    val backTarget = state.backTarget
    val pageBack = rememberChildBackDispatcher(enabled = backTarget == null, parent = backRoot)

    // What is being dragged in here, drawn over everything — the overlays as
    // well, so a card can be carried out of a sheet. The page behind a dimmed
    // modal takes no drops, as it takes no taps.
    val drags = remember { DragHost() }
    val blockedBelow = shutBy?.let { top -> stack.indexOfFirst { it.key == top.key } + 1 } ?: 0
    SideEffect {
        drags.originOf = { state.originInRoot }
        drags.blockedBelow = blockedBelow
    }

    CompositionLocalProvider(
        LocalOverlayHost provides state,
        LocalOverlayHostOrNull provides state,
        LocalDragHost provides drags,
        LocalTakesBackSwipe provides false,
        LocalRelaysSystemBack provides false,
        *(if (ownBack != null) arrayOf(LocalNavigationEventDispatcherOwner provides ownBack) else emptyArray()),
    ) {
        val direction = LocalLayoutDirection.current
        Box(
            modifier
                .fillMaxSize()
                // A drag's keys, before anything focused sees them: the arrows
                // move a carried thing, Enter puts it down, Escape calls it off.
                .onPreviewKeyEvent { drags.handleKey(it, direction) }
                .trackHostOrigin(state)
                .clearFocusOnTap(clearFocusOnTap)
                // Every press, wherever it lands, to what lets go at one
                // elsewhere: a swipe row left open, a chart's selection.
                .reportsPresses()
                .then(if (ownBack != null && tracksSwipe) Modifier.edgeSwipeBack(ownBack.navigationEventDispatcher) else Modifier),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // **Outside the content's layer and inside the host**, in
                    // that order, and both halves matter.
                    //
                    // Outside, because the band and the ring must not be scaled
                    // or blurred along with what they frame — that is why this
                    // is before `overlayBackdrop` in the chain rather than after.
                    //
                    // Inside the host, because the band is drawn *over* what it
                    // wraps now, and what this wraps is the app. It sat on the
                    // host for one round, where `drawContent` is the app **and
                    // the overlay stack**, so the band painted black across the
                    // sheet it was supposed to be framing.
                    .backdropGround(state, receding, blurring)
                    .overlayBackdrop(state, receding, blurring)
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = 0f
                    }
                    // Focus cannot enter content that is behind a modal overlay.
                    .focusProperties { canFocus = !trapping }
                    // Neither can a screen reader, which is the same sentence
                    // in the one traversal order the line above does not cover.
                    //
                    // A modal dims the page, takes the focus and eats the taps
                    // aimed past it — all facts about sighted, pointer or
                    // keyboard use. The semantics tree was left alone, so a
                    // button under an open sheet's scrim kept its click action
                    // and its enabled flag and stayed announceable and
                    // activatable: a control the app had just decided nobody
                    // should reach. See `ModalInertnessTest`, and note how it
                    // was found — a catalog test that reports dead controls
                    // named it the moment anything left a modal open at rest.
                    //
                    // **Keyed off the dimming, not off `trapping`.** The first
                    // cut used `trapping` and took the page away from a
                    // dropdown, because a menu traps focus — for the keyboard
                    // reason two lines up — while being explicitly not modal:
                    // `ScrimStyle.Transparent` is documented as "no dimming, but
                    // pointer events are blocked", the page underneath keeps
                    // scrolling, and `BackdropStyle`'s own KDoc says a dropdown
                    // "sits over content the user is still reading". Dimming is
                    // the library's existing statement that the page is not
                    // available, and it is what a sighted user is being shown;
                    // this makes the assistive tree say the same thing.
                    // `OverlayScrollTest` is what caught the wider version.
                    //
                    // `clearAndSetSemantics` rather than `hideFromAccessibility`:
                    // the latter flags a node and leaves it in the tree for
                    // platform services to honour, which is a promise about
                    // other people's code. Measured, the flag left the button
                    // findable. Clearing states the fact — there is no such
                    // control right now — in the one vocabulary every reader of
                    // this tree shares.
                    .then(if (shutBy != null) Modifier.clearAndSetSemantics {} else Modifier)
            ) {
                // Every text box below here gets the library's right-click
                // menu. The host installs it rather than the field, because it
                // opens into this host: `LocalOverlayHost` throws when there is
                // no host, so a field that reached for one would break every app
                // that draws a field on its own.
                TextContextMenuHost {
                    if (pageBack != null) {
                        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides pageBack) {
                            content()
                        }
                    } else {
                        content()
                    }
                }
            }

            // Each scrim-requesting entry gets its own scrim directly beneath
            // it, so an outside tap always dismisses the thing it is under.
            // Each dimming one draws its share, so the stack is never darker
            // than one scrim — see [scrimShare].
            stack.forEachIndexed { index, entry ->
                // Keyed by identity, not by slot. Without this, removing an
                // entry that is not the top one shifts every entry above it
                // down a slot, and each inherits the removed entry's remembered
                // state — its progress, its back handler, its scrim's gesture
                // detector — from the entry that used to sit there.
                key(entry.key) {
                    EntryHost(
                        state = state,
                        entry = entry,
                        index = index,
                        dimmers = dimmerKeys,
                        // What above this one blurs: everything behind it, so
                        // this as well as the page.
                        blurredBy = stack.subList(index + 1, stack.size)
                            .filter { it.scrim == ScrimStyle.Dimmed && it.backdrop.blurs }
                            .map { it.key },
                        takesBack = entry === backTarget,
                        opened = opened,
                    )
                }
            }

            // Last, so above every entry.
            DragLayer(drags)
        }
    }
}

/**
 * The modals that were open at the last composition — which was on top, and
 * every one that dims — and the entry the focus is in, if it is in one.
 */
private class OpenedModals {
    var top: Any? = null
    var dimmed: Set<Any> = emptySet()
    var focusIn: Any? = null
}

/**
 * The back swipe from the leading edge, and Escape, for a host that has taken
 * back over from the platform.
 *
 * UIKit's edge pan, in the library's hands: a pan that starts within
 * [EdgePanWidth] of the leading edge and heads for the trailing one. It looks
 * before anything under it does, as UIKit's does — a carousel or a row of swipe
 * actions against the edge does not get to keep it, since the edge decides as
 * early as they do — and only while something would answer back, so at the
 * root of an app the edge is the content's. A control that took the press
 * itself, a slider, keeps it.
 */
@Composable
private fun Modifier.edgeSwipeBack(dispatcher: NavigationEventDispatcher): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pan = remember(rtl) {
        BackPan(
            rightwards = !rtl,
            edge = if (rtl) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT,
            pass = PointerEventPass.Initial,
            startsAt = { x, window, density -> (if (rtl) window - x else x) < EdgePanWidth.value * density },
            early = true,
        )
    }
    val swipe = rememberSwipeBackInput(dispatcher)
    val keys = remember { KeyBackInput() }
    DisposableEffect(dispatcher, keys) {
        dispatcher.addInput(keys)
        onDispose { dispatcher.removeInput(keys) }
    }
    return this
        .swipeBack(pan, swipe)
        // After everything focused inside has had the key and left it.
        .onKeyEvent { event ->
            event.key == Key.Escape && event.type == KeyEventType.KeyDown && keys.press()
        }
}

