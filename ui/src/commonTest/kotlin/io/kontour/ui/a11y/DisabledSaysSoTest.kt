package io.kontour.ui.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.ThumbnailStrip
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.selection.ColourPicker
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A disabled control says it is disabled, and offers a screen reader nothing a
 * hand cannot do: contributing.md's second rule, "stays in the accessibility
 * tree marked disabled", and its third, "withhold the action too".
 *
 * Found in the November 2026 audit, in components outside the contract suite:
 * a disabled colour picker's tracks still took `setProgress`, a disabled
 * carousel still turned through its custom actions, and a disabled page
 * indicator's dots and thumbnail strip's thumbnails withheld their `onClick`
 * without saying why.
 */
@OptIn(ExperimentalTestApi::class)
class DisabledSaysSoTest {

    private fun androidx.compose.ui.test.ComposeUiTest.nodes(matcher: SemanticsMatcher): List<SemanticsNode> =
        onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()

    private val adjustable = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun aDisabledColourPickersTracksCannotBeMoved() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                ColourPicker(colour = Color.Red, onColourChange = {}, enabled = false, showAlphaSlider = true)
            }
        }
        val tracks = nodes(adjustable)
        assertTrue(tracks.isNotEmpty(), "no tracks found")
        for (track in tracks) {
            assertTrue(SemanticsActions.SetProgress !in track.config, "a disabled track still takes setProgress")
            assertTrue(SemanticsProperties.Disabled in track.config, "a disabled track does not say so")
        }
    }

    @Test
    fun aDisabledCarouselCannotBeTurned() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                val state = rememberCarouselState { 3 }
                Carousel(state = state, contentDescription = "Photos", enabled = false, modifier = Modifier.size(300.dp, 200.dp)) {
                    Box(Modifier.size(300.dp, 200.dp))
                }
            }
        }
        val carousel = nodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Photos"))).single()
        assertTrue(SemanticsActions.CustomActions !in carousel.config, "a disabled carousel still offers previous and next")
        assertTrue(SemanticsProperties.Disabled in carousel.config, "a disabled carousel does not say so")
    }

    @Test
    fun aDisabledPageIndicatorsDotsSaySo() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                val state = rememberCarouselState { 3 }
                PageIndicator(state = state, enabled = false, onPageClick = {})
            }
        }
        val dots = nodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton))
        assertTrue(dots.size == 3, "expected three dots, found ${dots.size}")
        for (dot in dots) {
            assertTrue(SemanticsActions.OnClick !in dot.config, "a disabled dot can still be pressed")
            assertTrue(SemanticsProperties.Disabled in dot.config, "a disabled dot does not say so")
        }
    }

    @Test
    fun aDisabledThumbnailStripsThumbnailsSaySo() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                val state = rememberCarouselState { 3 }
                ThumbnailStrip(state = state, enabled = false, modifier = Modifier.size(400.dp, 80.dp)) {
                    Box(Modifier.size(60.dp))
                }
            }
        }
        val thumbs = nodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton))
        assertTrue(thumbs.isNotEmpty(), "no thumbnails found")
        for (thumb in thumbs) {
            assertTrue(SemanticsActions.OnClick !in thumb.config, "a disabled thumbnail can still be pressed")
            assertTrue(SemanticsProperties.Disabled in thumb.config, "a disabled thumbnail does not say so")
        }
    }
}
