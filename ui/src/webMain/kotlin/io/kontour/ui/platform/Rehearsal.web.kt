package io.kontour.ui.platform

import kotlin.coroutines.resume
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On a phone's or a tablet's browser, where the first sheet opened stopped for
 * 100ms and more, and drawing it at the start cost one frame of 80–100ms.
 *
 * Not on a desktop's. There a Mac's Chrome compiles through Metal, and drawing
 * every overlay up front cost a first visit one frame of 843–968ms as the page
 * appeared, in November 2026, for overlays most visits to a page never open:
 * see [platformRehearsesWhenIdle] for what a desktop does instead.
 */
internal actual val platformRehearsesOverlays: Boolean by lazy { textTouchesGoToTheBrowser }

/**
 * A desktop's browser, a piece at a time while it is idle. On a first visit, in
 * a Mac's Chrome with its compiled shaders cleared, the docs site stopped for
 * 54–484ms at a time as it was read, compiling each kind of drawing the first
 * time it came up, while the six seconds after it loaded went unused.
 */
internal actual val platformRehearsesWhenIdle: Boolean by lazy { !textTouchesGoToTheBrowser }

/**
 * When the browser has nothing to do and the reader has not touched the page for
 * a moment: a piece of the rehearsal can stop the page for 700ms in a Mac's
 * Chrome, the one shader behind a large shadow's blur alone, and a reader
 * scrolling in that moment would feel it.
 */
internal actual suspend fun awaitBrowserIdle() {
    while (true) {
        suspendCancellableCoroutine { done -> whenIdle { if (done.isActive) done.resume(Unit) } }
        val still = msSinceInput()
        if (still >= ReaderStill) return
        delay((ReaderStill - still).toLong() + 1)
    }
}

/** How long the reader has to have left the page alone, in milliseconds. */
private const val ReaderStill = 1500.0

/** `requestIdleCallback` where there is one, which Safari does not have, and a short timer where not. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun whenIdle(callback: () -> Unit): Unit = js(
    "{ if (window.requestIdleCallback) window.requestIdleCallback(function () { setTimeout(callback, 0); }, { timeout: 3000 }); else setTimeout(function () { callback(); }, 500); }"
)

/** Milliseconds since the last pointer, wheel, touch or key event anywhere on the page, its listeners added once. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun msSinceInput(): Double = js(
    """(function () {
        if (!window.__kontourLastInput) {
            window.__kontourLastInput = performance.now();
            ['pointerdown', 'pointermove', 'wheel', 'keydown', 'touchstart'].forEach(function (type) {
                window.addEventListener(type, function () { window.__kontourLastInput = performance.now(); }, { capture: true, passive: true });
            });
        }
        return performance.now() - window.__kontourLastInput;
    })()"""
)
