package io.kontour.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * Going back from the page this is read in, or null when back would not leave
 * it. Provided by the page hosts: `:ui-nav3`'s page transitions and pane scenes,
 * and a one-pane [io.kontour.ui.adaptive.ListDetailPaneScaffold]'s detail.
 *
 * Read it through [pageBack].
 */
val LocalPageBack: ProvidableCompositionLocal<(() -> Unit)?> = compositionLocalOf { null }

/**
 * Going back from this page, for a back button, or null when back would not
 * leave it — at the root of a stack, or in a list beside its open detail.
 *
 * ```kotlin
 * TopBar(onBack = pageBack()) { +"Perth Busport" }
 * ```
 *
 * **Not the back stack's size.** A page that shows its back button while
 * `backStack.size > 1` shows it on the *root* page during a back gesture: the
 * stack still holds both pages until the gesture finishes, and the root page is
 * already on screen under the one leaving. The page knows which it is, and that
 * is what this reads.
 *
 * It goes back the way the system's back does, through the same handlers, so
 * a page that asks before it discards changes is asked here too, and the page
 * leaves with its pop animation.
 */
@Composable
@ReadOnlyComposable
fun pageBack(): (() -> Unit)? = LocalPageBack.current

/**
 * A page host's [LocalPageBack]: back through the back dispatcher this is
 * composed under, or null while [enabled] is false.
 *
 * Dispatched rather than called, so whatever would take the system's back
 * takes this first — a handler the page registered, a pane inside it — and
 * where nothing would take it, nothing happens, as with the system's. A page
 * leaving is still composed for its exit, and a tap on its back button then
 * must not take the page under it away too. [fallback] is for a host with no
 * back dispatcher above it at all: a preview, a test.
 *
 * ```kotlin
 * CompositionLocalProvider(LocalPageBack provides rememberPageBack(canGoBack, onBack)) { page() }
 * ```
 */
@Composable
fun rememberPageBack(enabled: Boolean, fallback: () -> Unit): (() -> Unit)? {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val latestFallback by rememberUpdatedState(fallback)
    val input = remember { PageBackInput() }
    if (dispatcher != null) {
        DisposableEffect(dispatcher, input) {
            dispatcher.addInput(input)
            onDispose { dispatcher.removeInput(input) }
        }
    }
    val dispatched = dispatcher != null
    val back = remember(input, dispatched) { { if (dispatched) input.complete() else latestFallback() } }
    return if (enabled) back else null
}

/** Back with no gesture, from a button: completed at once, if anything would take it. */
private class PageBackInput : NavigationEventInput() {
    private var added = false
    private var armed = false

    override fun onAdded(dispatcher: NavigationEventDispatcher) {
        added = true
    }

    override fun onRemoved() {
        added = false
        armed = false
    }

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        armed = hasEnabledHandlers
    }

    fun complete() {
        if (added && armed) dispatchOnBackCompleted()
    }
}
