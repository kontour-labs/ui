package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.GraphicsEnvironment

/**
 * AWT's default screen device, defensively.
 *
 * Two ways this returns nothing useful and both are ordinary rather than
 * exceptional: `getLocalGraphicsEnvironment()` throws under `java.awt.headless`,
 * which is how the screenshot suite runs, and `DisplayMode.getRefreshRate()`
 * returns `REFRESH_RATE_UNKNOWN`, which is zero, on drivers that will not say.
 * [FallbackHz] covers both.
 */
@Composable
internal actual fun platformDisplayHz(): Int = remember {
    runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .displayMode
            .refreshRate
    }.getOrNull()?.takeIf { it > 0 } ?: FallbackHz
}
