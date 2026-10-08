package io.kontour.ui.components.datetime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A date picker's days can be coloured and annotated.
 *
 * Asked for as *"the ability to colour and annotate dates (with things like
 * small numbers or text or stuff) in the date picker components (including the
 * range picker)"*: `containerColourFor` fills a day with a colour of the app's,
 * and `annotationFor` puts a few characters under its number, which a screen
 * reader hears after the date.
 */
@OptIn(ExperimentalTestApi::class)
class DayAnnotationTest {

    private val day = LocalDate(2026, 6, 10)

    @Test
    fun anAnnotationIsDrawnUnderItsDayAndReadAfterTheDate() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                DatePicker(
                    value = null,
                    onValueChange = {},
                    annotationFor = { if (it == day) DayAnnotation("$42", contentDescription = "42 dollars") else null },
                    navigation = rememberCalendarNavigationState(day),
                )
            }
        }
        waitForIdle()
        // Drawn once, on the 10th, and not a node of its own: the day carries it.
        onAllNodesWithText("$42", useUnmergedTree = true).fetchSemanticsNodes().let {
            assertEquals(1, it.size, "the annotation was drawn ${it.size} times")
        }
        val tenth = onNodeWithText("10").fetchSemanticsNode()
        val said = tenth.config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(
            said != null && said.contains("42 dollars") && said.indexOf("42 dollars") > said.indexOf("2026"),
            "the 10th announces '$said' — the annotation's description should follow the date",
        )
    }

    @Test
    fun annotatedWeeksAreTallerSoTheNumberKeepsItsPlace() {
        fun height(annotated: Boolean): Int {
            var h = 0
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                        KontourTheme(darkTheme = false, reduceMotion = true) {
                            Box(Modifier.testTag(Tag).width(350.dp)) {
                                DatePicker(
                                    value = null,
                                    onValueChange = {},
                                    annotationFor = if (annotated) ({ null }) else null,
                                )
                            }
                        }
                    }
                }
                waitForIdle()
                h = onNodeWithTag(Tag).fetchSemanticsNode().size.height
            }
            return h
        }
        val plain = height(annotated = false)
        val annotated = height(annotated = true)
        // Six weeks, each a line of `labelSmall` taller: well over a line in all.
        assertTrue(
            annotated - plain >= 6 * 12,
            "annotations made the picker $plain → ${annotated}px tall; six weeks should each gain a line",
        )
    }

    @Test
    fun aDaysOwnColourFillsItAndAChosenDayIsDrawnOverIt() {
        val holiday = Color(0xFF2E7D32)
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    KontourTheme(darkTheme = false, reduceMotion = true) {
                        Box(Modifier.testTag(Tag).width(350.dp).background(Color.White)) {
                            DateRangePicker(
                                start = LocalDate(2026, 6, 16),
                                end = LocalDate(2026, 6, 18),
                                onRangeChange = { _, _ -> },
                                containerColourFor = { if (it == day || it == LocalDate(2026, 6, 17)) holiday else null },
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val pixels = onNodeWithTag(Tag).captureToImage().toPixelMap()
            var green = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (pixels[x, y] == holiday) green++
            }
            // The 10th's circle, and not the 17th, which is inside the range
            // and drawn as the range: a circle of a few hundred pixels at most,
            // not two.
            assertTrue(green > 200, "the 10th's own colour was not drawn ($green pixels of it)")
            assertTrue(green < 2000, "$green pixels of the day colour: the range's middle day kept its own colour over the band")
        }
    }

    private companion object {
        const val Tag = "picker"
    }
}
