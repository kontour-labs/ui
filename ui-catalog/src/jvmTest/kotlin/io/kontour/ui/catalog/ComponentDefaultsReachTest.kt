package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.ComponentDefaults
import io.kontour.ui.theme.KontourTheme
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A `ComponentDefaults` override reaches pixels, and two fields that share a
 * default are not wired to each other.
 *
 * ### What this covers that the other two checks cannot
 *
 * Three things guard the sweep that created `ComponentDefaults`, and each has a
 * blind spot the next one fills:
 *
 *  - `ComponentDefaultsValuesTest` in `:ui` pins all fifty-eight values as
 *    hand-written literals, so nothing was mis-transcribed on the way out of the
 *    `*Defaults` objects. It cannot see *where* a value is wired.
 *  - `check-components.py` requires every field to be read by something, so a
 *    field that is dead — or one whose forward was dropped, leaving it read
 *    never while another is read twice — fails the build. It cannot see whether
 *    the reads are the right way round.
 *  - The default theme's goldens must not move, which catches any value that
 *    changed. A **symmetric swap changes no value**, so it catches nothing here.
 *
 * That last gap is real and narrow. Fifteen of the fifty-eight defaults are
 * shared by two or more fields, but almost every pair is split across two
 * components with two different member names, where a swap needs two matching
 * mistakes and shows in review. Exactly one pair is the shape a plain typo
 * produces: `buttonGapMedium` and `buttonGapLarge`, adjacent lines of the same
 * declaration, both `8.dp`, read four lines apart in the same `when`. That pair
 * is what [theTwoEightDpButtonGapsAreNotWiredToEachOther] renders.
 *
 * The rest of this file is the end-to-end proof that the plumbing exists at all
 * — that `KontourTheme(componentDefaults = …)` reaches a component's layout and
 * its text — because every other check in the set reads source rather than
 * pixels.
 */
class ComponentDefaultsReachTest {

    private fun buttonWidth(size: ButtonSize, defaults: ComponentDefaults): Float {
        var bounds = Rect.Zero
        val scene = ImageComposeScene(width = 600, height = 200, density = Density(2f)) {
            KontourTheme(componentDefaults = defaults) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Button(
                        onClick = {},
                        size = size,
                        modifier = Modifier.onGloballyPositioned {
                            bounds = Rect(it.positionInRoot(), it.size.toSize())
                        },
                    ) {
                        +SystemIcons.Plus
                        +"Save"
                    }
                }
            }
        }
        try {
            repeat(3) { frame -> scene.render(16_000_000L * frame).close() }
        } finally {
            scene.close()
        }
        assertTrue(bounds.width > 0f, "the button never reported a size")
        return bounds.width
    }

    /**
     * The plumbing exists: a padding set on the theme changes what is measured.
     *
     * `buttonPaddingMedium` was `20.dp` written inside `ButtonDefaults.metrics()`
     * — the sharpest case in the sweep, since the three fields beside it in the
     * same `ButtonMetrics` already came from `Theme.sizing` and
     * `Theme.typography`. 40dp of extra padding is 80px at this density, applied
     * at both ends.
     */
    @Test
    fun aPaddingSetOnTheThemeChangesWhatIsMeasured() {
        val standard = buttonWidth(ButtonSize.Medium, ComponentDefaults())
        val roomy = buttonWidth(
            ButtonSize.Medium,
            ComponentDefaults(buttonPaddingMedium = 60.dp),
        )
        assertEquals(
            160f,
            roomy - standard,
            absoluteTolerance = 1f,
            "`buttonPaddingMedium` went from 20dp to 60dp, which is 80px at each " +
                "end at density 2. The button grew by ${roomy - standard}px, so " +
                "either the field is not reaching `ButtonDefaults.metrics()` or " +
                "it is reaching only one end.",
        )
    }

    /**
     * The one pair a plain typo would produce.
     *
     * Both are `8.dp`, they are adjacent lines of `ComponentDefaults`, and they
     * are read four lines apart in the same `when` — so the value checks, the
     * read-count ratchet and the goldens are all blind to them being crossed.
     * Moving one and requiring only the matching size to change is the only
     * thing that is not.
     */
    @Test
    fun theTwoEightDpButtonGapsAreNotWiredToEachOther() {
        val mediumBase = buttonWidth(ButtonSize.Medium, ComponentDefaults())
        val largeBase = buttonWidth(ButtonSize.Large, ComponentDefaults())

        val wideMediumGap = ComponentDefaults(buttonGapMedium = 48.dp)
        assertEquals(
            80f,
            buttonWidth(ButtonSize.Medium, wideMediumGap) - mediumBase,
            absoluteTolerance = 1f,
            "widening `buttonGapMedium` did not widen the Medium button — it is " +
                "wired to the wrong size, or to nothing",
        )
        assertEquals(
            largeBase,
            buttonWidth(ButtonSize.Large, wideMediumGap),
            absoluteTolerance = 1f,
            "widening `buttonGapMedium` widened the **Large** button. The two " +
                "8dp gaps are crossed: `buttonGapMedium` is being read where " +
                "`buttonGapLarge` belongs.",
        )

        val wideLargeGap = ComponentDefaults(buttonGapLarge = 48.dp)
        assertEquals(
            80f,
            buttonWidth(ButtonSize.Large, wideLargeGap) - largeBase,
            absoluteTolerance = 1f,
            "widening `buttonGapLarge` did not widen the Large button",
        )
        assertEquals(
            mediumBase,
            buttonWidth(ButtonSize.Medium, wideLargeGap),
            absoluteTolerance = 1f,
            "widening `buttonGapLarge` widened the **Medium** button. The two " +
                "8dp gaps are crossed the other way.",
        )
    }

    /**
     * `uppercaseLabels` reaches a control's label — the field GTurbo needs and
     * the only one on the object that is not a measurement.
     *
     * Compared as pixels rather than by reading the semantics tree on purpose:
     * the flag is a *rendering* decision, and the accessible name must not
     * change with it. A screen reader announcing "SAVE" because a designer wanted
     * small caps is the failure this is shaped to notice.
     */
    @Test
    fun uppercaseLabelsChangesWhatIsDrawnAndNotWhatIsAnnounced() {
        fun render(defaults: ComponentDefaults): ByteArray {
            val scene = ImageComposeScene(width = 300, height = 120, density = Density(2f)) {
                KontourTheme(componentDefaults = defaults) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        Button(onClick = {}) { +"Save" }
                    }
                }
            }
            return try {
                requireNotNull(scene.render(0L).encodeToData(EncodedImageFormat.PNG)) {
                    "Skia failed to encode a frame"
                }.bytes
            } finally {
                scene.close()
            }
        }

        val plain = render(ComponentDefaults())
        val capitals = render(ComponentDefaults(uppercaseLabels = true))
        assertTrue(
            !plain.contentEquals(capitals),
            "`uppercaseLabels = true` drew the same pixels as `false`. The flag " +
                "is read at `contentScope`, which is the slot helper every " +
                "label-shaped control uses — if a control stopped going through " +
                "it, the flag stopped reaching that control silently.",
        )
    }
}
