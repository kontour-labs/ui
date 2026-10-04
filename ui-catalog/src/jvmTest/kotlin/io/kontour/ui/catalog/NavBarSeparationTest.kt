package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.ui.foundation.SurfaceDefaults
import io.kontour.ui.nav.NavBar
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A floating nav bar stands off what scrolls under it.
 *
 * Asked for: *"add some more background separation for the navbar. it blends in
 * a lot with content behind it, especially when you're scrolling over list
 * items and stuff."* The bar is `surface`, and so are a list's rows: over them
 * it was a shadow apart and no more. Now the page fades out under it, from its
 * middle down, and its capsule has a rim.
 *
 * Read from the pixels, over rows of a colour nothing in the theme is, in the
 * light and in the dark.
 */
class NavBarSeparationTest {

    private companion object {
        const val Density = 2f
        val Rows = Color(0xFFE0402A)
    }

    private class Look(val image: BufferedImage, val capsule: Rect, val page: Color, val container: Color, val ink: Color)

    private fun look(dark: Boolean, rows: Boolean): Look {
        var capsule = Rect.Zero
        var page = Color.Unspecified
        var container = Color.Unspecified
        var ink = Color.Unspecified
        val image = Scene(width = 800, height = 600, density = Density, reduceMotion = true, darkTheme = dark) {
            page = Theme.colours.background
            container = Theme.colours.surface
            ink = SurfaceDefaults.contentColour(container)
            Box(Modifier.fillMaxSize().background(page), contentAlignment = Alignment.BottomCenter) {
                if (rows) {
                    Column(Modifier.fillMaxSize()) {
                        repeat(12) { Box(Modifier.fillMaxWidth().height(56.dp).background(Rows)) }
                    }
                }
                NavBar(modifier = Modifier.reportBounds { capsule = it }) {
                    item(true, {}, key = "a", icon = Tabler.Outline.MapPin) { +"Nearby" }
                    item(false, {}, key = "b", icon = Tabler.Outline.Bus) { +"Routes" }
                }
            }
        }.use { it.frames(6) }
        return Look(image, capsule, page, container, ink)
    }

    private fun red(image: BufferedImage, x: Int, y: Int) = image.getRGB(x, y) shr 16 and 0xFF
    private fun Color.red255() = (red * 255f).roundToInt()

    private fun fadesUnderTheBar(dark: Boolean) {
        val look = look(dark, rows = true)
        val middle = (look.capsule.top + look.capsule.height / 2).toInt()
        // Beside the capsule's rounded end, at its middle: the rows run behind
        // here, between the capsule and the window's edge.
        val beside = red(look.image, (4 * Density).toInt(), middle)
        // Within a few levels: the capsule's shadow reaches this far, faintly.
        assertTrue(
            abs(beside - look.page.red255()) <= 8,
            "${if (dark) "dark" else "light"}: beside the bar, the rows should have faded into the page " +
                "(${look.page.red255()}), and are $beside — the rows are ${Rows.red255()}",
        )
        // Well above it the rows are as they were: the fade is the bar's, not a wash.
        val above = red(look.image, look.image.width / 2, (look.capsule.top - 40 * Density).toInt())
        assertEquals(Rows.red255(), above, "${if (dark) "dark" else "light"}: 40dp above the bar the rows are untouched")
    }

    @Test
    fun inTheLightTheRowsFadeOutUnderTheBar() = fadesUnderTheBar(dark = false)

    @Test
    fun inTheDarkTheRowsFadeOutUnderTheBar() = fadesUnderTheBar(dark = true)

    /**
     * Over a plain page the capsule has an edge of its own: a hairline of its
     * ink, darker than it in the light and lighter in the dark. Its shadow alone
     * leaves its top edge, where the shadow hardly falls, within a few levels of
     * the bar.
     */
    private fun hasARim(dark: Boolean) {
        val look = look(dark, rows = false)
        val x = look.image.width / 2 + 60
        val middle = (look.capsule.top + look.capsule.height / 2).toInt()
        val bar = look.container.red255()
        val towardsInk = if (look.ink.red255() > bar) 1 else -1
        val column = (middle downTo (middle - look.capsule.height.toInt())).map { red(look.image, x, it) }
        val strongest = column.maxOf { (it - bar) * towardsInk }
        assertTrue(
            strongest >= 24,
            "${if (dark) "dark" else "light"}: the capsule's top edge should have a rim of its ink, at least 24 levels " +
                "from the bar ($bar) towards it; the most there is $strongest, in $column",
        )
    }

    @Test
    fun inTheLightTheCapsuleHasARim() = hasARim(dark = false)

    @Test
    fun inTheDarkTheCapsuleHasARim() = hasARim(dark = true)
}
