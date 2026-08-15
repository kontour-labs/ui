package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    val dismissOnOutside: Boolean = true,
    val dismissOnBack: Boolean = true,
    val trapFocus: Boolean = true,
    val dismissLabel: String? = null,
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
     * Where the host itself sits in the window, so anchors captured in root
     * coordinates can be made host-local. Zero when the host is at the root,
     * which is the common case and the recommended one.
     */
    internal var originInRoot: Offset by mutableStateOf(Offset.Zero)

    /** The stack, bottom to top. Sorted by layer, then by push order within a layer. */
    val visible: List<OverlayEntry>
        get() = entries.sortedBy { it.layer.ordinal }

    /** True when nothing at all is showing. */
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * True when the user is engaged with something modal — a sheet, dialog,
     * menu or blocking state.
     *
     * What an [OverlayQueue] watches, rather than [isEmpty]: a toast showing is
     * not a reason to postpone onboarding, and the queue's own coach mark
     * certainly is not. See [OverlayLayer.suppressesQueue].
     */
    val isBusy: Boolean get() = entries.any { it.layer.suppressesQueue }

    /** Shows [entry], replacing any existing entry with the same key. */
    fun show(entry: OverlayEntry) {
        val existing = entries.indexOfFirst { it.key == entry.key }
        if (existing >= 0) {
            entries[existing] = entry
        } else {
            entries.add(entry)
        }
    }

    /** Hides the entry with this key. No-op if it is not showing. */
    fun hide(key: Any) {
        entries.removeAll { it.key == key }
    }

    /** True when an entry with this key is showing. */
    fun isShowing(key: Any): Boolean = entries.any { it.key == key }

    /**
     * True when a back gesture would dismiss something here.
     *
     * What a back handler enables itself on. Not the same as `!isEmpty`: a toast
     * is showing but is not dismissible, and a back press that silently did
     * nothing because a toast happened to be up is worse than one that leaves
     * the screen.
     */
    val canDismissOnBack: Boolean get() = entries.any { it.dismissOnBack }

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
        hide(target.key)
        return true
    }

    internal fun dismissOutside(entry: OverlayEntry) {
        if (!entry.dismissOnOutside) return
        entry.onDismiss?.invoke()
        hide(entry.key)
    }
}

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
 * ### Why in-composition rather than platform windows
 *
 * Material renders each dialog into its own platform window. That is fine on
 * Android and awkward everywhere else: window ordering, animation and dismissal
 * all behave differently per platform, and a menu opened from inside a sheet has
 * to reason about two windows. Rendering the stack in-composition means ordering
 * is a sort on [OverlayLayer], dimming is decided once for the whole stack
 * rather than multiplying into opacity, and every target behaves identically.
 *
 * The cost is that overlays are clipped to this composable's bounds, so it wants
 * to be at the root, and that a platform `Popup` is still needed for the cases
 * that genuinely require a real window — Android IME interaction, chiefly. Those
 * remain available; they are just not the default.
 *
 * ### Focus and reading order
 *
 * The content beneath an overlay is removed from the accessibility traversal
 * order while a focus-trapping entry is showing, so a screen reader cannot walk
 * into content the user cannot see. Each overlay gets a `traversalIndex` above
 * the content for the same reason.
 */
@Composable
fun OverlayHost(
    modifier: Modifier = Modifier,
    state: OverlayHostState = rememberOverlayHostState(),
    content: @Composable () -> Unit,
) {
    val stack = state.visible
    val trapping = stack.any { it.trapFocus }

    CompositionLocalProvider(LocalOverlayHost provides state) {
        Box(modifier.fillMaxSize().trackHostOrigin(state)) {
            Box(
                Modifier
                    .fillMaxSize()
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
            val dimming = topDimmedEntry(stack)

            stack.forEachIndexed { index, entry ->
                if (entry.scrim != ScrimStyle.None) {
                    Scrim(
                        visible = true,
                        onDismiss = if (entry.dismissOnOutside) {
                            { state.dismissOutside(entry) }
                        } else {
                            null
                        },
                        dismissLabel = entry.dismissLabel,
                        color = if (entry === dimming) {
                            Theme.colors.scrim
                        } else {
                            Color.Transparent
                        },
                    )
                }

                Box(
                    Modifier.semantics {
                        isTraversalGroup = true
                        traversalIndex = (index + 1).toFloat()
                    }
                ) {
                    entry.content()
                }
            }
        }
    }
}
