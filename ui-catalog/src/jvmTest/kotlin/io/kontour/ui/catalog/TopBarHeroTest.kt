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
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
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
 * Asked for as a hero top bar, collapsing as the Large one does; then as *"a
 * style, not some other flag … the image should be allowed to be taller"*; and
 * then, of a scrim that darkened the picture under the title, *"the image …
 * basically stops before the text. the text should be above the image, and the
 * bottom part of the image (where the text is) should fade towards the
 * background colour. the text is over that fade."* The picture here is made to
 * be awkward: a red band along its top, then black on the left and white on the
 * right, so a control or a title drawn straight on it is lost on one side or the
 * other whatever the theme.
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

    /** Where the open bar's foot is, a little above it: the picture's last rows. */
    private val foot = Open * Density - 4 * Density

    /** Beside the title on the picture's black half, clear of the back button and the words. */
    private val blackEdge = 6f * Density

    /**
     * The top of the title, open: where a high-contrast bar's solid band of its
     * own colour starts, on the picture's black half where nothing else is that
     * colour.
     */
    private fun titleTop(): Int {
        val solid = render(progress = 0f, contrast = ContrastLevel.High)
        val ground = scheme(false, ContrastLevel.High).background
        val below = (Band.value * Density).toInt()
        return (below until (Open * Density).toInt()).first { y -> close(solid.at(blackEdge, y.toFloat()), ground, 0.01f) }
    }

    /** Collapsed, it is a Small bar: the same pixels, picture, fade and discs gone. */
    @Test
    fun collapsedItIsASmallBar() {
        val small = render(progress = 0f, style = TopBarStyle.Small)
        val hero = render(progress = 1f)
        var differ = 0
        for (y in 0 until 56 * Density.toInt()) for (x in 0 until Width) if (small.getRGB(x, y) != hero.getRGB(x, y)) differ++
        assertTrue(differ == 0, "a collapsed hero bar differs from a Small bar in $differ pixels")
    }

    /**
     * Open, the picture is left alone until a little above the title, runs on
     * under it, and fades into the bar's own colour at the foot — where the page
     * begins. Before, a scrim darkened the part under the title into a dark band,
     * which read as the picture stopping short of the words.
     */
    @Test
    fun thePictureRunsUnderTheTitleAndFadesIntoTheBar() {
        val image = render(progress = 0f)
        val ground = scheme(false).background
        // High up on both halves, the picture's own black and white.
        assertTrue(close(image.at(blackEdge, 120f * Density), Color.Black), "the picture's upper part was touched, black side")
        assertTrue(close(image.at(Width - 20f, 120f * Density), Color.White), "the picture's upper part was touched, white side")
        // Level with the title's top, on the black half: the picture is still
        // there, partly faded — neither the picture's black nor the bar's colour.
        val underTitle = image.at(blackEdge, titleTop() + 2f).luminance()
        assertTrue(underTitle in 0.05f..0.9f, "level with the title's top the picture is gone or untouched: luminance $underTitle")
        // At the foot, on the black half: the bar's own colour.
        val atFoot = image.at(blackEdge, foot)
        assertTrue(close(atFoot, ground, 0.02f), "the picture does not fade into the bar's colour at its foot: $atFoot, not $ground")
    }

    /**
     * The title is in the bar's own ink over the fade: the pixels that change
     * when the title does are dark in a light scheme. Before, a scrim under it
     * turned it light.
     */
    @Test
    fun theTitleIsInTheBarsInk() {
        val titled = render(progress = 0f)
        val blank = render(progress = 0f, title = " ")
        val glyphs = mutableListOf<Float>()
        for (y in 0 until (Open * Density).toInt()) for (x in 0 until Width) {
            if (titled.getRGB(x, y) != blank.getRGB(x, y)) glyphs += Color(titled.getRGB(x, y)).luminance()
        }
        assertTrue(glyphs.size > 200, "the title drew almost nothing: ${glyphs.size} pixels")
        val median = glyphs.sorted()[glyphs.size / 2]
        assertTrue(median < 0.3f, "the title is not in the bar's dark ink over the fade: median luminance $median")
    }

    /**
     * Level with the top of the title, at both edges of the picture, the fade
     * gives the title's ink 3:1, which large type needs, in either theme.
     */
    @Test
    fun theTitleReadsOnAnyPicture() {
        val top = titleTop()
        val edge = 6f * Density
        for (dark in listOf(false, true)) {
            val image = render(progress = 0f, dark = dark)
            val ink = scheme(dark).content
            for (x in listOf(edge, Width - edge)) {
                val ground = image.at(x, top + 2f)
                assertTrue(
                    contrastRatio(ink, ground) >= 3f,
                    "level with the title's top (${if (dark) "dark" else "light"}, x=$x) the title is ${contrastRatio(ink, ground)}:1 on $ground",
                )
            }
        }
    }

    /** Where the subtitle is, near the foot, its muted ink has 4.5:1 on either half. */
    @Test
    fun theSubtitleReadsOnAnyPicture() {
        val subtitleRow = Open * Density - 20f * Density
        for (dark in listOf(false, true)) {
            val image = render(progress = 0f, dark = dark)
            val ink = scheme(dark).contentMuted
            for (x in listOf(blackEdge, Width - blackEdge)) {
                val ground = image.at(x, subtitleRow)
                assertTrue(
                    contrastRatio(ink, ground) >= 4.5f,
                    "beside the subtitle (${if (dark) "dark" else "light"}, x=$x) the ink is ${contrastRatio(ink, ground)}:1 on $ground",
                )
            }
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

    /** At high contrast there is nothing of the picture behind the title: the bar's colour, solid. */
    @Test
    fun atHighContrastTheTitleStandsOnSolidGround() {
        val image = render(progress = 0f, contrast = ContrastLevel.High)
        val ground = scheme(false, ContrastLevel.High).background
        // Level with the title, on the picture's black half, beside the words: a
        // fade that let any of the picture through would be grey.
        val titleRow = Open * Density - 40 * Density
        val sample = image.at(blackEdge, titleRow)
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
