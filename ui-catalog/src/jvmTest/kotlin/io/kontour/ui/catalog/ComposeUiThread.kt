package io.kontour.ui.catalog

import java.lang.reflect.InvocationTargetException
import javax.swing.SwingUtilities

/**
 * Runs [block] on the AWT event thread, the thread Compose on the desktop posts
 * its own deferred work to, and hands back what it returned or threw.
 *
 * **Every `ImageComposeScene` here is driven from inside this.** A scene is not
 * thread-safe, and it is not only the test that touches it: whenever a node's
 * bounds change outside a layout pass, Compose's `RectManager` posts a
 * `dispatchCallbacks()` to `MainUIDispatcher` — the AWT event thread on the
 * desktop — 16ms out, and that call compacts the scene's `RectList`. A test
 * rendering the next frame on its own thread is inserting and removing in the
 * same list at the same moment, and the entries it loses surface later as
 * `IllegalArgumentException: LayoutNode … not found in RectList`, when one of
 * them is removed.
 *
 * Which is how `MonthJumpTest` failed on CI: a turn of the month drum composed
 * a new month of focusable day cells — every focus target registers a rect
 * callback as it attaches — and the post landed in the next frame's layout.
 * The scene's composition was left half-applied, and what the test reported
 * was the pager's `IndexOutOfBoundsException` that followed it. A bare scene
 * that recreates four hundred nodes a frame, rendered on the test thread with a
 * random pause between frames, has failed the same way within ten frames —
 * not every time, it is a race — and rendered in here it ran 3,895 frames
 * without it. On one thread the post simply waits its turn.
 */
fun <T> onComposeUiThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var outcome: Result<T>? = null
    try {
        SwingUtilities.invokeAndWait { outcome = runCatching(block) }
    } catch (wrapped: InvocationTargetException) {
        // runCatching took everything the block could throw; this is the event
        // queue's own failure, which is worth seeing as itself.
        throw wrapped.cause ?: wrapped
    }
    return checkNotNull(outcome) { "the event thread did not run the block" }.getOrThrow()
}
