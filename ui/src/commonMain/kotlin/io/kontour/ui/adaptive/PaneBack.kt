package io.kontour.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.overlay.RegisterBackHandler

/**
 * Where a back gesture aimed at a single-pane detail has got to — read by the
 * pane's transition, which seeks towards the list while it is under way.
 */
@Stable
internal class PaneBackState {
    /** Whether a gesture is under way. False for a key or a button, which never start one. */
    var inProgress by mutableStateOf(false)
        internal set

    /** How far through the gesture, 0 to 1. */
    var progress by mutableFloatStateOf(0f)
        internal set

    /** Which edge the gesture started from — `NavigationEvent.EDGE_LEFT` and so on. */
    var swipeEdge by mutableIntStateOf(NavigationEvent.EDGE_NONE)
        internal set
}

private object PaneBackInfo : NavigationEventInfo()

private class PaneBackHandler(
    private val state: PaneBackState,
    private val onBack: () -> Unit,
) : NavigationEventHandler<NavigationEventInfo>(PaneBackInfo, false) {

    override fun onBackStarted(event: NavigationEvent) {
        state.swipeEdge = event.swipeEdge
        state.progress = event.progress
        state.inProgress = true
    }

    override fun onBackProgressed(event: NavigationEvent) {
        state.swipeEdge = event.swipeEdge
        state.progress = event.progress
    }

    override fun onBackCancelled() {
        state.inProgress = false
    }

    override fun onBackCompleted() {
        // The caller moves the focus; the transition carries on from wherever
        // the hand left it, or runs back if the caller declined.
        state.inProgress = false
        onBack()
    }
}

/**
 * Registers the single pane's back handler, enabled while [enabled], and
 * returns the gesture's state. Registered where it is composed, so a handler
 * inside the detail — a stack of its own — registers after it and wins.
 */
@Composable
internal fun rememberPaneBack(enabled: Boolean, onBack: () -> Unit): PaneBackState {
    val state = remember { PaneBackState() }
    val latest by rememberUpdatedState(onBack)
    val handler = remember(state) { PaneBackHandler(state) { latest() } }
    SideEffect { handler.isBackEnabled = enabled }
    RegisterBackHandler(LocalNavigationEventDispatcherOwner.current, handler)
    return state
}
