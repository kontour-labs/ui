package io.kontour.ui.catalog

import androidx.compose.runtime.Composable

/**
 * Sixty, because a browser does not expose the display's refresh rate at all.
 *
 * There is no `screen.refreshRate`, and the only way to find out is to time
 * `requestAnimationFrame` — which is the inference [platformDisplayHz]'s
 * docstring refuses, for the reason it gives there. So the web readout judges
 * against sixty and says so on its face: the third line of [FrameReadout] shows
 * the number the budget came from, and on the web it will always read 60.
 */
@Composable
internal actual fun platformDisplayHz(): Int = FallbackHz
