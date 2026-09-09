package io.kontour.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The geometry a brand adjusts, for the components that have some of their own.
 *
 * Colour and type in this library always come from the theme. Geometry did not:
 * it sat as hardcoded `val`s in forty-odd `*Defaults` objects, each overridable
 * at one call site and none of them app-wide. A design that wants tighter
 * buttons everywhere had to pass `contentPadding` at every call or give up, and
 * `ButtonDefaults.metrics()` is the sharpest version — three of its five fields
 * read [Sizing] and [Typography] and two were `10.dp` and `4.dp` written into
 * the function body.
 *
 * ### What earns a field here
 *
 * A constant belongs on this object when **a different design system would
 * plausibly change it** and **no existing token family can carry it**. Most
 * apparent per-component constants fail one of those:
 *
 *  - A menu's minimum width, a rating's five stars, the seven columns of a
 *    calendar and every fraction in `Toast`'s drag are *facts about the
 *    component*. Moving them here would offer a caller a dial that only breaks
 *    things.
 *  - A control's corner is [Shapes.control], its border weight is
 *    [Sizing.borderWidth], its spring is [Motion] and its screen margin is
 *    [Spacing]. Anything an existing family carries belongs in that family, not
 *    duplicated here.
 *
 * A third way a constant fails is that it cannot be *reached*.
 * `SliderDefaults.ThumbAspect` is a plausible brand dial and stayed where it is:
 * a top-level `SliderThumbReach` derives from it, and four gesture and draw
 * lambdas across `Slider` and `RangeSlider` read that at pointer time rather
 * than in composition. Threading a theme value through them is a rewrite of the
 * slider's hit geometry, which is a different change. Better a constant that
 * says so than a field wired through half of them.
 *
 * Two more failed a fourth way, and it is worth recording because the obvious
 * reading of them is wrong. `ListItem`'s 48dp minimum row height and the
 * nav item's 48dp inline glyph look like Android's touch target hardcoded past
 * [Sizing.minTouchTarget] — which varies by platform. They are not:
 * `minTouchTarget` is **24dp on the JVM**, so pointing them at it would halve
 * every desktop list row. They are density, they coincide with 48, and they are
 * fields here rather than a fix.
 *
 * ### Nothing here is unwired
 *
 * A wide object of defaulted fields is the shape of API that ships dead: at its
 * default a field that is read nowhere is indistinguishable from one that is.
 * `ComponentDefaultsReachTest` sets each field on its own to a sentinel, renders
 * the component that should read it, and requires the render to *change*. A
 * field that cannot move a pixel does not belong on this object.
 *
 * @property uppercaseLabels Whether the label of a control is set in capitals.
 *
 *   Casing is not something a [androidx.compose.ui.text.TextStyle] can carry, so
 *   there is nowhere else for this to live. It is applied where the library
 *   turns a caller's `String` into text on a *control* — the seven row-shaped
 *   slots (`Button`, the extended FAB, `Tag`, the three `Chip` variants and a
 *   `TabBar` tab) and a field's label — and nowhere else. Dialog titles, banner
 *   messages, list rows and menu items go through a different slot helper and
 *   are prose, not labels.
 *
 *   A caller who writes `Text("Save")` inside a slot instead of `+"Save"` opts
 *   out, exactly as they opt out of the slot's `maxLines` and its text style.
 *   That is the documented shape of the `+` vocabulary rather than a hole in
 *   this flag.
 */
@Immutable
data class ComponentDefaults(
    // --- Casing ---
    val uppercaseLabels: Boolean = false,

    // --- Buttons ---
    // Ten of these were inside `ButtonDefaults.metrics()`, beside three fields
    // that already read the theme. A brand's button density is the single most
    // asked-for adjustment in any design system.
    val buttonPaddingXSmall: Dp = 10.dp,
    val buttonPaddingSmall: Dp = 14.dp,
    val buttonPaddingMedium: Dp = 20.dp,
    val buttonPaddingLarge: Dp = 24.dp,
    val buttonPaddingXLarge: Dp = 32.dp,
    val buttonGapXSmall: Dp = 4.dp,
    val buttonGapSmall: Dp = 6.dp,
    val buttonGapMedium: Dp = 8.dp,
    val buttonGapLarge: Dp = 8.dp,
    val buttonGapXLarge: Dp = 10.dp,
    /** How far a small control shrinks on press. 28 and 36dp can take 7%. */
    val pressScaleSmall: Float = 0.93f,
    /** The same for 44dp. Large and XLarge use `DefaultPressScale`. */
    val pressScaleMedium: Float = 0.95f,

    // --- Controls ---
    val segmentedTrackPadding: Dp = 6.dp,
    val toolbarPadding: Dp = 6.dp,

    // --- Rows and lists ---
    val listItemMinHeight: Dp = 48.dp,
    val listItemTwoLineMinHeight: Dp = 64.dp,
    /** Between a row's label and its supporting line. */
    val listItemSpacing: Dp = 2.dp,
    val scrollbarThickness: Dp = 6.dp,
    val scrollbarHoveredThickness: Dp = 10.dp,
    val swipeActionWidth: Dp = 88.dp,

    // --- Display ---
    val pageIndicatorDotSize: Dp = 8.dp,
    val pageIndicatorActiveWidth: Dp = 20.dp,
    val pageIndicatorGap: Dp = 6.dp,
    val keyValueLabelWidth: Dp = 108.dp,
    val keyValueMinValueWidth: Dp = 96.dp,
    /** The blank between the end of a marquee's text and its repeat. */
    val marqueeGap: Dp = 48.dp,

    // --- Navigation ---
    val navIndicatorWidth: Dp = 56.dp,
    val navIndicatorHeight: Dp = 32.dp,
    val navGlyphHeight: Dp = 28.dp,
    val navInlineGlyph: Dp = 48.dp,
    val navCircleSize: Dp = 40.dp,
    val navSelectedIconScale: Float = 1.08f,
    val navBarItemGap: Dp = 12.dp,
    val navRailCollapsedWidth: Dp = 88.dp,
    val navRailExpandedWidth: Dp = 280.dp,
    val navDrawerWidth: Dp = 280.dp,
    val navDrawerNestIndent: Dp = 24.dp,
    val topBarHeight: Dp = 56.dp,
    val topBarLargeHeight: Dp = 112.dp,
    val tabBarHeight: Dp = 48.dp,

    // --- Overlays and surfaces ---
    val menuGap: Dp = 4.dp,
    val tooltipMaxWidth: Dp = 280.dp,
    val commandPaletteWidth: Dp = 560.dp,
    val commandPaletteMaxHeight: Dp = 360.dp,
    val commandPaletteTopInset: Dp = 96.dp,
    val coachmarkSpotlightPadding: Dp = 8.dp,
    val backdropBlurRadius: Dp = 24.dp,
    /** How far the page behind a sheet or drawer recedes. */
    val backdropScaleBack: Float = 0.94f,
    val glassAlpha: Float = 0.58f,
    /** What a glass surface falls back to at the high-contrast tier. */
    val glassOpaqueAlpha: Float = 0.94f,
    val glassBlurRadius: Dp = 14.dp,
    val sheetMaxWidth: Dp = 640.dp,
    val sideSheetWidth: Dp = 480.dp,
    /** How much of the toast beneath the top one shows. */
    val toastPeek: Dp = 12.dp,
    val toastMaxWidth: Dp = 420.dp,
    val toastPillWidth: Dp = 72.dp,
    val toastPillHeight: Dp = 36.dp,
)
