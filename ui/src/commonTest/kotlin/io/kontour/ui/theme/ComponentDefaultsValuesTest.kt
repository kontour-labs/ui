package io.kontour.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pins every value [ComponentDefaults] holds, as a literal, written by hand.
 *
 * The sweep that created that object moved fifty-eight constants out of
 * forty-odd `*Defaults` objects. Each one had to become a default on a new data
 * class without being mis-transcribed, and the failure mode is a `24.dp` that
 * arrives as `20.dp` in a component nothing photographs.
 *
 * ### Why this is not `assertEquals(f(), f())`
 *
 * Stage 6 named the trap and it applies here too: asserting
 * `ComponentDefaults() == kontourComponentDefaults()` would prove nothing,
 * because both sides come from the same declaration. The independently written
 * side has to be *written*, which is what the fifty-eight literals below are.
 * They were transcribed from the `*Defaults` objects as they stood before the
 * sweep, not from the new class.
 *
 * That costs a legitimate retune having to be made twice, which is the
 * golden-in-code trade `HighContrastWideningTest` already takes for the same
 * reason. It is worth it here for the same reason too: these numbers are not
 * supposed to move, and a second place saying so is the feature.
 *
 * ### What this does not catch
 *
 * A **symmetric swap between two fields with the same default**. Fifteen values
 * are shared by two or more fields — `8.dp` by four of them — so wiring
 * `buttonGapMedium` where `buttonGapLarge` belongs and vice versa passes every
 * assertion here. Two other checks cover the rest of that ground and neither
 * closes this one:
 *
 *  - `check-components.py` requires every field to be read, so an *asymmetric*
 *    swap shows up as one field read twice and another read never.
 *  - The default theme's screenshot goldens must not move, which catches any
 *    value that changed — but a symmetric swap changes no value.
 *
 * `ComponentDefaultsReachTest` in `:ui-catalog` renders the one pair a real
 * typo would plausibly produce: two adjacent lines of the same declaration
 * holding the same number.
 */
class ComponentDefaultsValuesTest {

    private val d = ComponentDefaults()

    @Test
    fun casingIsOffUntilAThemeAsksForIt() {
        // The library ships no product in it, so it has no opinion on casing —
        // the same stance `brand` takes on colour. GTurbo turns it on.
        assertFalse(d.uppercaseLabels, "the default theme does not set labels in capitals")
    }

    @Test
    fun buttonMetricsAreWhatTheyWere() {
        assertEquals(10.dp, d.buttonPaddingXSmall)
        assertEquals(14.dp, d.buttonPaddingSmall)
        assertEquals(20.dp, d.buttonPaddingMedium)
        assertEquals(24.dp, d.buttonPaddingLarge)
        assertEquals(32.dp, d.buttonPaddingXLarge)
        assertEquals(4.dp, d.buttonGapXSmall)
        assertEquals(6.dp, d.buttonGapSmall)
        assertEquals(8.dp, d.buttonGapMedium)
        assertEquals(8.dp, d.buttonGapLarge)
        assertEquals(10.dp, d.buttonGapXLarge)
        assertEquals(0.93f, d.pressScaleSmall)
        assertEquals(0.95f, d.pressScaleMedium)
    }

    @Test
    fun controlMetricsAreWhatTheyWere() {
        assertEquals(6.dp, d.segmentedTrackPadding)
        assertEquals(6.dp, d.toolbarPadding)
    }

    @Test
    fun rowAndListMetricsAreWhatTheyWere() {
        assertEquals(48.dp, d.listItemMinHeight)
        assertEquals(64.dp, d.listItemTwoLineMinHeight)
        assertEquals(2.dp, d.listItemSpacing)
        assertEquals(6.dp, d.scrollbarThickness)
        assertEquals(10.dp, d.scrollbarHoveredThickness)
        assertEquals(88.dp, d.swipeActionWidth)
    }

    @Test
    fun displayMetricsAreWhatTheyWere() {
        assertEquals(8.dp, d.pageIndicatorDotSize)
        assertEquals(20.dp, d.pageIndicatorActiveWidth)
        assertEquals(6.dp, d.pageIndicatorGap)
        assertEquals(108.dp, d.keyValueLabelWidth)
        assertEquals(96.dp, d.keyValueMinValueWidth)
        assertEquals(48.dp, d.marqueeGap)
    }

    @Test
    fun navigationMetricsAreWhatTheyWere() {
        assertEquals(56.dp, d.navIndicatorWidth)
        assertEquals(32.dp, d.navIndicatorHeight)
        assertEquals(28.dp, d.navGlyphHeight)
        assertEquals(48.dp, d.navInlineGlyph)
        assertEquals(40.dp, d.navCircleSize)
        assertEquals(1.08f, d.navSelectedIconScale)
        assertEquals(12.dp, d.navBarItemGap)
        assertEquals(88.dp, d.navRailCollapsedWidth)
        assertEquals(280.dp, d.navRailExpandedWidth)
        assertEquals(280.dp, d.navDrawerWidth)
        assertEquals(24.dp, d.navDrawerNestIndent)
        assertEquals(56.dp, d.topBarHeight)
        assertEquals(112.dp, d.topBarLargeHeight)
        assertEquals(48.dp, d.tabBarHeight)
    }

    @Test
    fun overlayAndSurfaceMetricsAreWhatTheyWere() {
        assertEquals(4.dp, d.menuGap)
        assertEquals(280.dp, d.tooltipMaxWidth)
        assertEquals(560.dp, d.commandPaletteWidth)
        assertEquals(360.dp, d.commandPaletteMaxHeight)
        assertEquals(96.dp, d.commandPaletteTopInset)
        assertEquals(8.dp, d.coachmarkSpotlightPadding)
        assertEquals(24.dp, d.backdropBlurRadius)
        assertEquals(0.94f, d.backdropScaleBack)
        assertEquals(0.58f, d.glassAlpha)
        assertEquals(0.94f, d.glassOpaqueAlpha)
        assertEquals(14.dp, d.glassBlurRadius)
        assertEquals(640.dp, d.sheetMaxWidth)
        assertEquals(480.dp, d.sideSheetWidth)
        assertEquals(12.dp, d.toastPeek)
        assertEquals(420.dp, d.toastMaxWidth)
        assertEquals(72.dp, d.toastPillWidth)
        assertEquals(36.dp, d.toastPillHeight)
    }

    /**
     * Every field is pinned above.
     *
     * Without this a field added to [ComponentDefaults] and forgotten here is
     * simply unchecked, and the test that was written to stop a value drifting
     * quietly stops covering it just as quietly. `equals` over the whole object
     * is what makes the coverage total: a new field changes the constructed
     * value, and this fails until it is listed.
     */
    @Test
    fun nothingHasBeenAddedThatThisTestDoesNotPin() {
        assertEquals(
            ComponentDefaults(),
            ComponentDefaults(
                uppercaseLabels = false,
                buttonPaddingXSmall = 10.dp,
                buttonPaddingSmall = 14.dp,
                buttonPaddingMedium = 20.dp,
                buttonPaddingLarge = 24.dp,
                buttonPaddingXLarge = 32.dp,
                buttonGapXSmall = 4.dp,
                buttonGapSmall = 6.dp,
                buttonGapMedium = 8.dp,
                buttonGapLarge = 8.dp,
                buttonGapXLarge = 10.dp,
                pressScaleSmall = 0.93f,
                pressScaleMedium = 0.95f,
                segmentedTrackPadding = 6.dp,
                toolbarPadding = 6.dp,
                listItemMinHeight = 48.dp,
                listItemTwoLineMinHeight = 64.dp,
                listItemSpacing = 2.dp,
                scrollbarThickness = 6.dp,
                scrollbarHoveredThickness = 10.dp,
                swipeActionWidth = 88.dp,
                pageIndicatorDotSize = 8.dp,
                pageIndicatorActiveWidth = 20.dp,
                pageIndicatorGap = 6.dp,
                keyValueLabelWidth = 108.dp,
                keyValueMinValueWidth = 96.dp,
                marqueeGap = 48.dp,
                navIndicatorWidth = 56.dp,
                navIndicatorHeight = 32.dp,
                navGlyphHeight = 28.dp,
                navInlineGlyph = 48.dp,
                navCircleSize = 40.dp,
                navSelectedIconScale = 1.08f,
                navBarItemGap = 12.dp,
                navRailCollapsedWidth = 88.dp,
                navRailExpandedWidth = 280.dp,
                navDrawerWidth = 280.dp,
                navDrawerNestIndent = 24.dp,
                topBarHeight = 56.dp,
                topBarLargeHeight = 112.dp,
                tabBarHeight = 48.dp,
                menuGap = 4.dp,
                tooltipMaxWidth = 280.dp,
                commandPaletteWidth = 560.dp,
                commandPaletteMaxHeight = 360.dp,
                commandPaletteTopInset = 96.dp,
                coachmarkSpotlightPadding = 8.dp,
                backdropBlurRadius = 24.dp,
                backdropScaleBack = 0.94f,
                glassAlpha = 0.58f,
                glassOpaqueAlpha = 0.94f,
                glassBlurRadius = 14.dp,
                sheetMaxWidth = 640.dp,
                sideSheetWidth = 480.dp,
                toastPeek = 12.dp,
                toastMaxWidth = 420.dp,
                toastPillWidth = 72.dp,
                toastPillHeight = 36.dp,
            ),
            "a field was added to ComponentDefaults without being pinned here. " +
                "Add it to whichever test above covers its section, and to this " +
                "constructor call — the point of listing all fifty-eight is that " +
                "a new one cannot arrive unchecked.",
        )
    }
}
