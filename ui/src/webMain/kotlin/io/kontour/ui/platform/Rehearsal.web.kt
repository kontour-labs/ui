package io.kontour.ui.platform

/**
 * On a phone's or a tablet's browser, where the first sheet opened stopped for
 * 100ms and more, and drawing it at the start cost one frame of 80–100ms.
 *
 * Not on a desktop's. There a Mac's Chrome compiles through Metal, and drawing
 * every overlay up front cost a first visit one frame of 843–968ms as the page
 * appeared, in November 2026, for overlays most visits to a page never open.
 * Its programs are compiled when they are first drawn instead, as they always
 * were, and kept by the browser for the next visit.
 */
internal actual val platformRehearsesOverlays: Boolean by lazy { textTouchesGoToTheBrowser }
