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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Share
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.theme.ColourScheme
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
 * A large bar over a picture, collapsing into a small bar: `TopBarStyle.Hero`.
 *
 * Asked for as a hero top bar, collapsing as the Large one does, and then as
 * *"a style, not some other flag … the image should be allowed to be taller, and
 * the text … should still be above the image. there should just be some faded
 * 'darkening' underneath the text so it stands out."* The picture here is made
 * to be awkward: a red band along its top, then black on the left and white on
 * the right, so a control or a title drawn straight on it is lost on one side or
 * the other whatever the theme.
 */
class TopBarHeroTest {

    private companion object {
        const val Density = 2f
        const val Width = 400
        const val Height = 900
        val Red = Color(0xFFFF0000)
        val Band = 20.dp

        /** `TopBarDefaults.HeroHeight`. */
        const val Open = 360f
    }

    private var back = Rect.Zero
    private var share = Rect.Zero
    private var bar = Rect.Zero

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
        style: TopBarStyle = TopBarStyle.Hero,
        picture: Boolean = true,
        expandedHeight: Dp = Dp.Unspecified,
        title: String = "Kings Park",
    ): BufferedImage = Scene(width = Width, height = Height, density = Density, reduceMotion = reduceMotion, darkTheme = dark) {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = reduceMotion) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                    TopBar(
                        modifier = Modifier.reportBounds { bar = it },
                        style = style,
                        navigation = {
                            IconButton(
                                icon = io.kontour.ui.foundation.SystemIcons.ChevronBack,
                                contentDescription = "Back",
                                onClick = {},
                                modifier = Modifier.reportBounds { back = it },
                            )
                        },
                        actions = {
                            IconButton(
                                icon = Tabler.Outline.Share,
                                contentDescription = "Share",
                                onClick = {},
                                modifier = Modifier.reportBounds { share = it },
                            )
                        },
                        image = if (picture) ({ Picture() }) else null,
                        expandedHeight = expandedHeight,
                        collapseProgress = progress,
                        windowInsets = WindowInsets(top = inset.dp),
                    ) {
                        +title
                        supporting { +"Fraser Avenue" }
                    }
                }
            }
        }
    }.use { it.frames(20) }

    private fun BufferedImage.at(x: Float, y: Float) = Color(getRGB(x.toInt(), y.toInt()))

    private fun close(a: Color, b: Color, slack: Float = 0.02f) =
        abs(a.red - b.red) < slack && abs(a.green - b.green) < slack && abs(a.blue - b.blue) < slack

    private fun scheme(dark: Boolean, contrast: ContrastLevel = ContrastLevel.Standard) = kontourColourScheme(dark, contrast)

    /** The scrim's colour, made opaque: what the bar darkens the picture with. */
    private fun ColourScheme.scrimBase() = scrim.copy(alpha = 1f)

    /** The ink a hero's title takes over its picture, as the bar picks it. */
    private fun ColourScheme.onPicture() = contentColourFor(
        background = scrimBase(),
        light = if (isDark) content else onPrimary,
        dark = if (isDark) onPrimary else content,
    )

    /** Where the open bar's foot is, a little above it: the picture's last rows. */
    private val foot = Open * Density - 4 * Density

    /**
     * The top of the title, open: where a high-contrast bar's solid band starts,
     * on the picture's white half where nothing else is that colour.
     */
    private fun titleTop(): Int {
        val solid = render(progress = 0f, contrast = ContrastLevel.High)
        val base = scheme(false, ContrastLevel.High).scrimBase()
        val edge = Width - 6f * Density
        return (0 until (Open * Density).toInt()).first { y -> close(solid.at(edge, y.toFloat()), base, 0.01f) }
    }

    /** Collapsed, it is a Small bar: the same pixels, picture, scrim and discs gone. */
    @Test
    fun collapsedItIsASmallBar() {
        val small = render(progress = 0f, style = TopBarStyle.Small)
        val hero = render(progress = 1f)
        var differ = 0
        for (y in 0 until 56 * Density.toInt()) for (x in 0 until Width) if (small.getRGB(x, y) != hero.getRGB(x, y)) differ++
        assertTrue(differ == 0, "a collapsed hero bar differs from a Small bar in $differ pixels")
    }

    /**
     * Open, the picture is left alone except under the title, where it is
     * darkened. Before, the foot faded into the bar's own colour — a band of
     * near-white across the bottom of a light-theme picture.
     */
    @Test
    fun onlyThePartUnderTheTitleDarkens() {
        val image = render(progress = 0f)
        val ground = scheme(false).background
        // High up on both halves, the picture's own black and white.
        assertTrue(close(image.at(40f * Density, 120f * Density), Color.Black), "the picture's upper part was touched, black side")
        assertTrue(close(image.at(Width - 20f, 120f * Density), Color.White), "the picture's upper part was touched, white side")
        // Just above the foot, on the white half: darkened, and not the bar's colour.
        val shaded = image.at(Width - 20f, foot)
        assertTrue(shaded.luminance() <= 0.35f, "the white under the title was not darkened: $shaded")
        assertTrue(!close(shaded, ground, 0.1f), "the foot is the bar's colour, as the old fade made it: $shaded")
    }

    /**
     * The title is in light ink over the picture: the pixels that change when
     * the title does are bright. Before, it kept the bar's dark ink in light.
     */
    @Test
    fun theTitleIsLightOverThePicture() {
        val titled = render(progress = 0f)
        val blank = render(progress = 0f, title = " ")
        val glyphs = mutableListOf<Float>()
        for (y in 0 until (Open * Density).toInt()) for (x in 0 until Width) {
            if (titled.getRGB(x, y) != blank.getRGB(x, y)) glyphs += Color(titled.getRGB(x, y)).luminance()
        }
        assertTrue(glyphs.size > 200, "the title drew almost nothing: ${glyphs.size} pixels")
        val median = glyphs.sorted()[glyphs.size / 2]
        assertTrue(median > 0.7f, "the title's ink is not light over the picture: median luminance $median")
    }

    /**
     * Level with the top of the title, at both edges of the picture, the scrim
     * gives the title's light ink 3:1, which large type needs, in either theme.
     */
    @Test
    fun theTitleReadsOnAnyPicture() {
        val top = titleTop()
        val edge = 6f * Density
        for (dark in listOf(false, true)) {
            val image = render(progress = 0f, dark = dark)
            val ink = scheme(dark).onPicture()
            for (x in listOf(edge, Width - edge)) {
                val ground = image.at(x, top + 2f)
                assertTrue(
                    contrastRatio(ink, ground) >= 3f,
                    "level with the title's top (${if (dark) "dark" else "light"}, x=$x) the title is ${contrastRatio(ink, ground)}:1 on $ground",
                )
            }
        }
    }

    /** Where the subtitle is, near the foot, its ink has 4.5:1 on the white half. */
    @Test
    fun theSubtitleReadsOnAnyPicture() {
        val edge = Width - 6f * Density
        val subtitleRow = Open * Density - 20f * Density
        for (dark in listOf(false, true)) {
            val image = render(progress = 0f, dark = dark)
            val ink = scheme(dark).onPicture()
            val ground = image.at(edge, subtitleRow)
            assertTrue(
                contrastRatio(ink, ground) >= 4.5f,
                "beside the subtitle (${if (dark) "dark" else "light"}) the ink is ${contrastRatio(ink, ground)}:1 on $ground",
            )
        }
    }

    /** Each control sits on a disc of the bar's colour over the picture, and its glyph reads on it. */
    @Test
    fun theControlsReadOnAnyPicture() {
        for (dark in listOf(false, true)) {
            val image = render(progress = 0f, dark = dark)
            val ground = scheme(dark).background
            val ink = scheme(dark).content
            for ((name, bounds) in listOf("back" to back, "share" to share)) {
                // Inside the disc, clear of the glyph: the bar's colour.
                val disc = image.at(bounds.center.x, bounds.top + 3 * Density)
                assertTrue(close(disc, ground, 0.03f), "the $name button has no disc of the bar's colour behind it (${if (dark) "dark" else "light"}): $disc")
                assertTrue(contrastRatio(ink, disc) >= 3f, "the $name glyph is ${contrastRatio(ink, disc)}:1 on its disc")
            }
        }
    }

    /** At high contrast there is nothing of the picture behind the title: the scrim is solid. */
    @Test
    fun atHighContrastTheTitleStandsOnSolidGround() {
        val image = render(progress = 0f, contrast = ContrastLevel.High)
        val base = scheme(false, ContrastLevel.High).scrimBase()
        // Level with the title, on the picture's white half, past where the
        // words end: a scrim that let any of the picture through would be grey.
        val titleRow = Open * Density - 40 * Density
        val sample = image.at(Width - 6f * Density, titleRow)
        assertTrue(close(sample, base, 0.005f), "at high contrast the title's band is $sample, not the solid $base")
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

    /**
     * Without its picture a Hero bar is a Large bar at the hero's height, so a
     * page whose picture is sometimes missing does not jump.
     */
    @Test
    fun withoutAnImageItIsALargeBarAtTheHeroHeight() {
        render(progress = 0f, picture = false)
        assertTrue(abs(bar.height - Open * Density) <= 1f, "a Hero bar without a picture is ${bar.height / Density}dp, not $Open")
    }

    /** `expandedHeight` on a Large bar is the least it opens to. */
    @Test
    fun expandedHeightIsAFloorForLarge() {
        render(progress = 0f, style = TopBarStyle.Large, expandedHeight = 200.dp)
        assertTrue(abs(bar.height - 200f * Density) <= 1f, "a Large bar asked to open to 200dp is ${bar.height / Density}dp")
    }
}
