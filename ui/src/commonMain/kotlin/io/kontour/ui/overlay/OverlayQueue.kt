package io.kontour.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * One thing that wants the user's attention, and the conditions under which it
 * may have it.
 *
 * @param priority Higher wins. A forced update outranks onboarding, which
 *   outranks a review prompt.
 * @param prerequisites Ids that must already have been shown and dismissed.
 *   "Do not ask for a review before the user has finished onboarding."
 * @param minSessions Suppress until the app has been opened this many times. The
 *   guard against asking a first-time user to rate an app they have not used.
 * @param repeatable When false — the default — the request is retired once
 *   dismissed and never returns. Onboarding is not repeatable; a "you are
 *   offline" prompt is.
 */
@Immutable
data class OverlayRequest(
    val id: String,
    val priority: Int,
    val prerequisites: Set<String> = emptySet(),
    val minSessions: Int = 0,
    val repeatable: Boolean = false,
)

/**
 * Decides which of several competing overlays to show, one at a time.
 *
 * **This is not [OverlayHostState], and the difference matters.** The host is a
 * *stack*: things that legitimately coexist, like a menu open over a sheet. The
 * queue is *mutual exclusion*: things that must never coexist, because showing
 * two of them at once is nonsense.
 *
 * The app has a set of exactly that kind — on launch it may want to show a
 * forced update, onboarding, a legal-update notice, a what's-new sheet, a review
 * prompt or a paywall. Never two. The Android app encodes this today as an
 * `ActiveOverlay` enum and a `when` in `GlobalOverlays.kt`; this generalises it
 * so adding a seventh does not mean editing a conditional.
 *
 * ```
 * val queue = rememberOverlayQueue(sessionCount = prefs.launches)
 *
 * queue.request(OverlayRequest("force-update", priority = 100)) { updateRequired }
 * queue.request(OverlayRequest("onboarding", priority = 90)) { !prefs.onboarded }
 * queue.request(
 *     OverlayRequest("review", priority = 10, prerequisites = setOf("onboarding"), minSessions = 5),
 * ) { !prefs.reviewed }
 *
 * when (queue.current?.id) {
 *     "force-update" -> ForceUpdateSheet(onDismiss = { queue.dismiss("force-update") })
 *     "onboarding" -> OnboardingSheet(onDismiss = { queue.dismiss("onboarding") })
 *     …
 * }
 * ```
 *
 * The same shape serves coach marks, which is why [io.kontour.ui.overlay.Tooltip]
 * uses it rather than carrying its own scheduler — `TooltipManager` in the
 * Android app is this class with different field names.
 */
@Stable
class OverlayQueue internal constructor(private val sessionCount: Int) {

    private val requests = mutableStateMapOf<String, OverlayRequest>()
    private val conditions = mutableStateMapOf<String, () -> Boolean>()
    private val dismissed = mutableStateMapOf<String, Unit>()

    /** Set to suspend the queue entirely — while any [OverlayHost] entry is showing. */
    var suppressed: Boolean by mutableStateOf(false)
        internal set

    /**
     * Registers [request], shown when [condition] holds.
     *
     * Safe to call on every composition: re-registering the same id replaces its
     * condition rather than duplicating it.
     */
    fun request(request: OverlayRequest, condition: () -> Boolean) {
        requests[request.id] = request
        conditions[request.id] = condition
    }

    /** Removes a request entirely, whether or not it has been shown. */
    fun withdraw(id: String) {
        requests.remove(id)
        conditions.remove(id)
    }

    /**
     * The request that should be showing, or null.
     *
     * The highest-priority request whose condition holds, whose prerequisites
     * have all been dismissed, and which has met its session threshold.
     */
    val current: OverlayRequest?
        get() {
            if (suppressed) return null
            return requests.values
                .filter { it.isEligible() }
                .maxByOrNull { it.priority }
        }

    private fun OverlayRequest.isEligible(): Boolean {
        if (!repeatable && dismissed.containsKey(id)) return false
        if (sessionCount < minSessions) return false
        // A prerequisite that has not been *dismissed* is not satisfied — merely
        // having been shown is not enough, or a review prompt could appear over
        // the onboarding it was meant to follow.
        if (prerequisites.any { !dismissed.containsKey(it) }) return false
        return conditions[id]?.invoke() == true
    }

    /**
     * Marks a request as done, revealing whatever is next.
     *
     * Call this whichever way the overlay was closed — confirmed, cancelled or
     * swiped away. The queue does not care why; it cares that the user is
     * finished with it.
     */
    fun dismiss(id: String) {
        dismissed[id] = Unit
    }

    /** Forgets that [id] was dismissed, so it can be shown again. */
    fun reset(id: String) {
        dismissed.remove(id)
    }

    /** True when [id] has been dismissed this session. Also satisfies prerequisites. */
    fun wasDismissed(id: String): Boolean = dismissed.containsKey(id)
}

/**
 * Creates an [OverlayQueue] and keeps it in sync with the overlay stack.
 *
 * While a sheet, dialog, menu or blocking state is showing, the queue is
 * suppressed — so a coach mark cannot appear over a dialog, and onboarding
 * cannot fire while the user is mid-way through a sheet. That is the
 * generalisation of the Android app's `tooltipBlocker`.
 *
 * It watches [OverlayHostState.isBusy] rather than `isEmpty`, which matters more
 * than it sounds: coach marks render into the tooltip layer *through this
 * queue*, so a queue that stopped for anything at all would suppress itself the
 * instant it showed something and flicker it straight back off.
 *
 * @param sessionCount How many times the app has been opened. Drives
 *   [OverlayRequest.minSessions]; pass 0 if you do not track it, and the
 *   threshold becomes a no-op rather than blocking everything.
 */
@Composable
fun rememberOverlayQueue(sessionCount: Int = 0): OverlayQueue {
    val host = LocalOverlayHost.current
    val queue = remember(sessionCount) { OverlayQueue(sessionCount) }
    queue.suppressed = host.isBusy
    return queue
}

/**
 * The queue for the current subtree.
 *
 * Optional — most components do not need it. Provide it explicitly where coach
 * marks are in play.
 */
val LocalOverlayQueue = staticCompositionLocalOf<OverlayQueue?> { null }
