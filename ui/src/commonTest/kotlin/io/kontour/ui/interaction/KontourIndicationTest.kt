package io.kontour.ui.interaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A control keeps its press and hover feedback through anything that changes
 * under it mid-gesture.
 *
 * ### The dead control
 *
 * The indication counted: a press added one and a release took one away, and the
 * control drew pressed while the count was above nought. A count is only right
 * for a node that saw every interaction from the start, and the node is replaced
 * whenever the factory stops being equal — which it did on a change of input
 * modality, a theme or a contrast tier. The new node saw a release with no press
 * before it, counted to minus one, and every press after that brought it back
 * only to nought. The control never drew pressed again, for as long as it lived.
 *
 * The everyday trigger was the first touch after a mouse: the touch turns the
 * modality over on its way down, so the control under the finger was replaced
 * between its own press and release.
 *
 * Grey under the wash, so a light wash and a dark one both show against it.
 */
@OptIn(ExperimentalTestApi::class)
class KontourIndicationTest {

    private val ground = Color(0xFF808080)

    @Composable
    private fun Target(source: MutableInteractionSource) {
        Box(
            Modifier
                .testTag("target")
                .size(40.dp)
                .background(ground)
                .clickable(interactionSource = source, indication = kontourIndication(), onClick = {}),
        )
    }

    private fun ComposeUiTest.centre(): Color {
        val pixels = onNodeWithTag("target").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun ComposeUiTest.emit(source: MutableInteractionSource, interaction: Interaction) {
        runOnIdle { source.tryEmit(interaction) }
        waitForIdle()
    }

    @Test
    fun aPressThatOutlivesAChangeOfModalityLeavesTheControlPressable() = runComposeUiTest {
        val source = MutableInteractionSource()
        var modality by mutableStateOf(InputModality.Mouse)
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                CompositionLocalProvider(LocalInputModality provides modality) { Target(source) }
            }
        }
        waitForIdle()
        val resting = centre()

        val first = PressInteraction.Press(Offset(20f, 20f))
        emit(source, first)
        assertNotEquals(resting, centre(), "a press drew no wash at all, so this measured nothing")

        // The finger's own press turned the modality to touch on its way down.
        modality = InputModality.Touch
        waitForIdle()
        emit(source, PressInteraction.Release(first))
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(resting, centre(), "the release did not take the wash away")

        emit(source, PressInteraction.Press(Offset(20f, 20f)))
        assertNotEquals(
            resting,
            centre(),
            "a press after a release that straddled a change of modality drew nothing. " +
                "The node that answered the release had not seen its press, counted " +
                "below nought, and every press since has only brought it back to nought.",
        )
    }

    @Test
    fun aHoverThatOutlivesAChangeOfThemeLeavesTheControlHoverable() = runComposeUiTest {
        val source = MutableInteractionSource()
        var dark by mutableStateOf(false)
        setContent {
            KontourTheme(darkTheme = dark, reduceMotion = true) {
                CompositionLocalProvider(LocalInputModality provides InputModality.Mouse) { Target(source) }
            }
        }
        waitForIdle()

        val first = HoverInteraction.Enter()
        emit(source, first)
        dark = true
        waitForIdle()
        emit(source, HoverInteraction.Exit(first))
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val resting = centre()

        emit(source, HoverInteraction.Enter())
        assertNotEquals(
            resting,
            centre(),
            "a pointer back over a control whose last hover straddled a theme change " +
                "drew no hover. The node that answered the exit had not seen the enter.",
        )
    }

    /**
     * Changing input does not recompose every control.
     *
     * The modality was read in composition to build the factory, so moving the
     * hand from the mouse to the screen recomposed every control on screen and
     * replaced every indication node in it — the second of which is the dead
     * control above. The node reads it itself now, and composition never hears
     * of it.
     */
    @Test
    fun aChangeOfModalityDoesNotRecomposeAControl() = runComposeUiTest {
        val source = MutableInteractionSource()
        val probe = Compositions()
        var modality by mutableStateOf(InputModality.Touch)
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                CompositionLocalProvider(LocalInputModality provides modality) { Probe(probe, source) }
            }
        }
        waitForIdle()
        val resting = probe.count
        check(resting > 0) { "the probe never composed, so this measured nothing" }

        for (next in listOf(InputModality.Mouse, InputModality.Touch, InputModality.Mouse)) {
            modality = next
            waitForIdle()
        }
        assertEquals(
            resting,
            probe.count,
            "a control with nothing but a clickable and the library's indication " +
                "recomposed ${probe.count - resting} time(s) across three changes of input",
        )
    }
}

/** A counter the compiler can skip past: see `WindowSizeRecompositionTest`. */
@Stable
private class Compositions {
    var count = 0
}

@Composable
private fun Probe(into: Compositions, source: MutableInteractionSource) {
    into.count++
    Box(Modifier.size(40.dp).clickable(interactionSource = source, indication = kontourIndication(), onClick = {}))
}
