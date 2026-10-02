package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Share
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.nav.HeroTopBar
import io.kontour.ui.nav.TopBar
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.kontourColourScheme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A large bar over a picture, collapsing into a small bar.
 *
 * Asked for as a hero top bar, collapsing as the Large one does. The picture
 * here is made to be awkward: a red band along its top, then black on the left
 * and white on the right, so a control or a title drawn straight on it is lost
 * on one side or the other whatever the theme.
 */
class HeroTopBarTest {

    private companion object {
        const val Density = 2f
        const val Width = 400
        const val Height = 700
        val Red = Color(0xFFFF0000)
        val Band = 20.dp
    }

    private var back = Rect.Zero
    private var share = Rect.Zero

    @Composable
    private fun Picture() {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(Band).background(Red))
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxSize().background(Color.Black))
                Box(Modifier.weight(1f).fillMaxSize().background(Color.White))
            }
        }
    }

    private fun render(
        progress: Float,
        dark: Boolean = false,
        contrast: ContrastLevel = ContrastLevel.Standard,
        reduceMotion: Boolean = false,
        rtl: Boolean = false,
        inset: Int = 0,
        hero: Boolean = true,
    ): BufferedImage = Scene(width = Width, height = Height, density = Density, reduceMotion = reduceMotion, darkTheme = dark) {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = reduceMotion) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                    val actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                        IconButton(
                            icon = Tabler.Outline.Share,
                            contentDescription = "Share",
                            onClick = {},
                            modifier = Modifier.reportBounds { share = it },
                        )
                    }
                    if (hero) {
                        HeroTopBar(
                            image = { Picture() },
                            navigation = {
                                IconButton(
                                    icon = io.kontour.ui.foundation.SystemIcons.ChevronBack,
                                    contentDescription = "Back",
                                    onClick = {},
                                    modifier = Modifier.reportBounds { back = it },
                                )
                            },
                            actions = actions,
                            collapseProgress = progress,
                            windowInsets = WindowInsets(top = inset.dp),
                        ) { +"Kings Park" }
                    } else {
                        TopBar(
                            navigation = {
                                IconButton(
                                    icon = io.kontour.ui.foundation.SystemIcons.ChevronBack,
                                    contentDescription = "Back",
                                    onClick = {},
                                    modifier = Modifier.reportBounds { back = it },
                                )
                            },
                            actions = actions,
                            windowInsets = WindowInsets(top = inset.dp),
                        ) { +"Kings Park" }
                    }
                }
            }
        }
    }.use { it.frames(20) }

    private fun BufferedImage.at(x: Float, y: Float) = Color(getRGB(x.toInt(), y.toInt()))

    private fun close(a: Color, b: Color, slack: Float = 0.02f) =
        abs(a.red - b.red) < slack && abs(a.green - b.green) < slack && abs(a.blue - b.blue) < slack

    /** Collapsed, it is a Small bar: the same pixels, picture and discs gone. */
    @Test
    fun collapsedItIsASmallBar() {
        val small = render(progress = 0f, hero = false)
        val hero = render(progress = 1f)
        var differ = 0
        for (y in 0 until 56 * Density.toInt()) for (x in 0 until Width) if (small.getRGB(x, y) != hero.getRGB(x, y)) differ++
        assertTrue(differ == 0, "a collapsed hero bar differs from a Small bar in $differ pixels")
    }

    /** Open, the picture's top is left alone, and the title stands on the bar's colour. */
    @Test
    fun onlyThePartUnderTheTitleFades() {
        val image = render(progress = 0f)
        val ground = kontourColourScheme(false, ContrastLevel.Standard).background
        // The middle of the picture's height, on the black side clear of the
        // controls, is the picture's own black.
        assertTrue(close(image.at(40f * Density, 120f * Density), Color.Black), "the picture's upper part was faded")
        // Just above the bar's foot, beside the title, it is the bar's colour.
        val foot = 280f * Density - 4 * Density
        assertTrue(
            close(image.at(Width - 20f, foot), ground, 0.06f) && close(image.at(20f, foot), ground, 0.06f),
            "under the title the picture shows through: ${image.at(20f, foot)} on the black side, ${image.at(Width - 20f, foot)} on the white",
        )
    }

    /**
     * Level with the top of the title, beside it, the ground gives the title's
     * ink 3:1 on either half of the picture, in either theme. The title's top
     * is where the solid band starts at high contrast.
     */
    @Test
    fun theTitleReadsOnAnyPicture() {
        val solid = render(progress = 0f, contrast = ContrastLevel.High)
        val hcGround = kontourColourScheme(false, ContrastLevel.High).background
        val edge = 6f * Density
        val titleTop = (0 until 280 * Density.toInt()).first { y -> close(solid.at(edge, y.toFloat()), hcGround, 0.005f) }
        for (dark in listOf(false, true)) {
            val image = render(progress = 0f, dark = dark)
            val ink = kontourColourScheme(dark, ContrastLevel.Standard).content
            for (x in listOf(edge, Width - edge)) {
                val ground = image.at(x, titleTop + 2f)
                assertTrue(
                    contrastRatio(ink, ground) >= 3f,
                    "level with the title's top (${if (dark) "dark" else "light"}, x=$x) the title is ${contrastRatio(ink, ground)}:1 on $ground",
                )
            }
        }
    }

    /** Each control sits on a disc of the bar's colour over the picture, and its glyph reads on it. */
    @Test
    fun theControlsReadOnAnyPicture() {
        for (dark in listOf(false, true)) {
            val image = render(progress = 0f, dark = dark)
            val ground = kontourColourScheme(dark, ContrastLevel.Standard).background
            val ink = kontourColourScheme(dark, ContrastLevel.Standard).content
            for ((name, bounds) in listOf("back" to back, "share" to share)) {
                // Inside the disc, clear of the glyph: the bar's colour.
                val disc = image.at(bounds.center.x, bounds.top + 3 * Density)
                assertTrue(close(disc, ground, 0.03f), "the $name button has no disc of the bar's colour behind it (${if (dark) "dark" else "light"}): $disc")
                assertTrue(contrastRatio(ink, disc) >= 3f, "the $name glyph is ${contrastRatio(ink, disc)}:1 on its disc")
            }
        }
    }

    /** At high contrast there is nothing of the picture behind the title: the band is solid. */
    @Test
    fun atHighContrastTheTitleStandsOnSolidGround() {
        val image = render(progress = 0f, contrast = ContrastLevel.High)
        val ground = kontourColourScheme(false, ContrastLevel.High).background
        // Level with the title, on the black side, past where the words end.
        val titleRow = 280f * Density - 40 * Density
        val sample = image.at(Width / 2f - 10, titleRow)
        assertTrue(close(sample, ground, 0.005f), "at high contrast the title's band is $sample, not the solid $ground")
    }

    /** Collapsing, the picture rises behind the bar; under reduced motion it stays and fades. */
    @Test
    fun underReducedMotionThePictureDoesNotRise() {
        fun redRows(image: BufferedImage): Int =
            (0 until 60).count { y -> close(image.at(Width / 2f, y.toFloat()), Red, 0.1f) }
        val moving = redRows(render(progress = 0.4f))
        val still = redRows(render(progress = 0.4f, reduceMotion = true))
        assertEquals((Band.value * Density).toInt(), still, "under reduced motion the picture's red band moved: $still rows")
        assertTrue(moving < still, "with motion the picture did not rise as the bar collapsed: $moving rows of red, $still at rest")
    }

    /** The picture reaches the top of the window under a status bar; the controls keep below it. */
    @Test
    fun thePictureRunsUnderTheStatusBar() {
        val image = render(progress = 0f, inset = 40)
        assertTrue(close(image.at(Width / 2f, 2f), Red, 0.1f), "the picture stops short of the window's top")
        assertTrue(back.top >= 40 * Density, "the back button is under the status bar, at ${back.top}px")
    }

    @Test
    fun rightToLeftTheBackButtonIsOnTheRight() {
        render(progress = 0f, rtl = true)
        assertTrue(back.center.x > Width / 2f, "right to left, the back button is at ${back.center.x}px of $Width")
        assertTrue(share.center.x < Width / 2f, "right to left, the action is at ${share.center.x}px of $Width")
    }
}
