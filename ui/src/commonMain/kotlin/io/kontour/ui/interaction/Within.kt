package io.kontour.ui.interaction

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * [block]'s result if it finishes within [timeMillis], or null if the time runs out
 * first — `withTimeoutOrNull`, timed by `delay`.
 *
 * **Why not `withTimeoutOrNull` itself.** It is timed through
 * `Delay.invokeOnTimeout`, and a dispatcher that implements `delay` is free to leave
 * that one to a real-time default — which Compose's test clock does. So in a test
 * whose `delay`s run on the virtual clock, a `withTimeoutOrNull` beside them ran on
 * the wall: a toast held for 2.5 seconds of virtual time never expired, and a
 * counter's warning never ended, however far the clock was advanced. Two clocks in
 * one wait is a race even where both are real, so the timer here is a `delay` like
 * every other wait in the library.
 */
internal suspend fun <T> withinOrNull(timeMillis: Long, block: suspend CoroutineScope.() -> T): T? {
    if (timeMillis <= 0L) return null
    return coroutineScope {
        val work = async { block() }
        val timer = launch { delay(timeMillis) }
        select {
            work.onAwait { result ->
                timer.cancel()
                result
            }
            timer.onJoin {
                work.cancel()
                null
            }
        }
    }
}
