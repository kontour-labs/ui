package io.kontour.ui.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.kontour.ui.theme.KontourTheme
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The landing page, light and dark.
 *
 * It is the only page in the gallery whose content is prose, so it is the only
 * one where a token change shows up as *text* going wrong — a body colour that
 * stopped clearing contrast on a card, a heading that lost its hierarchy. The
 * specimen pages would not catch either.
 */
class AboutScreenshotTest {

    @AfterTest
    fun assertGoldensMatched() = Screenshot.assertAllMatched()

    @Test
    fun rendersLight() = render(dark = false)

    @Test
    fun rendersDark() = render(dark = true)

    private fun render(dark: Boolean) {
        Screenshot.render(
            name = "about-${if (dark) "dark" else "light"}",
            width = 1600,
            height = 2900,
        ) {
            KontourTheme(darkTheme = dark, reduceMotion = true) {
                AboutShowcase(Modifier.fillMaxSize())
            }
        }
    }
}
