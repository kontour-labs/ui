package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Toasts leave oldest first, one at a time.
 *
 * Reported from the catalog: raise a handful of toasts, drag one away, and the rest
 * all disappear at exactly the same moment — however far apart they were raised.
 * Clearing one by hand buys the others time, and the purchase capped every clock at
 * a full lifetime, so every toast younger than about a second came out of it holding
 * the same number and ran out on the same frame.
 *
 * The stack is back to front in the order it was raised, so the order it leaves in
 * is the reverse of nothing: the one at the back goes first, and the next one only
 * after it has.
 */
@OptIn(ExperimentalTestApi::class)
class ToastOrderTest {

    @Test
    fun afterOneIsSwipedTheRestLeaveOldestFirstAndApart() = runComposeUiTest {
        val toasts = ToastHostState()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) { ToastHost(toasts) }
            }
        }
        mainClock.autoAdvance = false

        val ids = List(4) { index ->
            toasts.show("Toast $index", duration = 2_500.milliseconds).also { mainClock.advanceTimeBy(300) }
        }
        // The front one — the newest — swiped away by hand.
        toasts.dismiss(ids.last())

        val leftAt = mutableMapOf<Long, Long>()
        var now = 0L
        while (now < 12_000 && leftAt.size < ids.size) {
            mainClock.advanceTimeBy(16)
            now += 16
            // Leaving is either on its way out or already gone from the list: under
            // reduced motion the exit can finish inside the frame that started it.
            ids.forEach { id ->
                val toast = toasts.toasts.firstOrNull { it.id == id }
                if ((toast == null || !toast.presence.targetState) && id !in leftAt) leftAt[id] = now
            }
        }

        val rest = ids.dropLast(1)
        assertTrue(rest.all { it in leftAt }, "not every toast left: $leftAt")
        val order = rest.sortedBy { leftAt.getValue(it) }
        assertEquals(rest, order, "the toasts left in the order $order, not oldest first ($leftAt)")
        rest.zipWithNext().forEach { (older, newer) ->
            val gap = leftAt.getValue(newer) - leftAt.getValue(older)
            assertTrue(
                gap >= 200,
                "toast $newer left ${gap}ms after toast $older — together, rather than one after " +
                    "the other ($leftAt)",
            )
        }
    }
}
