package io.kontour.ui.demo.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.CodeColours
import io.kontour.ui.theme.ColourScheme
import io.kontour.ui.theme.ComponentDefaults
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.Shapes
import io.kontour.ui.theme.StatusColours
import io.kontour.ui.theme.darkColourScheme
import io.kontour.ui.theme.highContrastDarkColourScheme
import io.kontour.ui.theme.jetBrainsMonoFontFamily
import io.kontour.ui.theme.kontourShapes
import io.kontour.ui.theme.kontourTypography
import io.kontour.ui.theme.outfitFontFamily

/**
 * A worked example of a theme that is not this library's own.
 *
 * GTurbo is a vehicle-tuning app: near-black, red-accented, technical. It is
 * here because the default theme is deliberately neutral and a neutral theme
 * cannot, on its own, tell you whether the token system is a *system* or just a
 * dark-mode switch. This is as far from the default as a real product gets —
 * different grounds, a different accent, a corner ladder a third the size, and
 * a monospaced face for numbers — and every value below goes through a parameter
 * that already existed.
 *
 * ### It was written verbosely on purpose, and all three are now closed
 *
 * Three things here were longer than they should have been, and each was a
 * specification rather than an oversight. They are left named as the record of
 * what the exercise was for: in every case the verbose version was the
 * specification, and the seam was accepted only once this file got shorter.
 *
 * - The numeric face was a `FontFamily.Monospace` constant nothing could read,
 *   because `Typography` had one family for all sixteen styles. It gained a
 *   second family and a `mono` style; a dead constant and nine lines of KDoc
 *   explaining why it was dead became one extra argument.
 * - The shape scale was thirteen restated tokens, twelve of which repeated the
 *   library's own policy back to it. `kontourShapes` took the ladder and the
 *   capsule cap as arguments; what is left is the two fields where GTurbo
 *   actually disagrees.
 * - **Uppercase labels** could not be expressed at all. The design sets every
 *   button, field label and badge in capitals, and casing is not something a
 *   `TextStyle` can carry — so there was no argument to pass and the theme
 *   simply did not have them. `ComponentDefaults.uppercaseLabels` is where it
 *   goes, and it is the one field on that object that is not a measurement.
 *
 * The acceptance test for each seam was the same: *this file gets shorter, and
 * its render moves only where the seam was supposed to move it*.
 *
 * ### The palette, and where it came from
 *
 * Sampled from the design's own pixels rather than eyeballed. The contrast
 * figures are against `#0A0A0B`, and every one of them is checked on every build
 * by `DemoThemeContrastTest` — the table is the prediction, the test is the
 * check.
 *
 * The interesting part is the red. **`#C6252B` is 3.49:1 on the page** — fine as
 * a fill with white on it (5.67:1), not readable as text. The design solves that
 * with a second, lighter red for anywhere red has to be read, and that is
 * exactly [StatusColours.solid] against [StatusColours.onContainer]. The
 * `brand`/`accent` split was arrived at independently by a designer who had
 * never seen this library, which is the strongest evidence available that the
 * split is real rather than a taxonomy.
 */
val gTurboDemoTheme = DemoTheme(
    name = "GTurbo",
    // Dark only, and honestly declared. A light GTurbo would be a different
    // design, not this one inverted — the whole product reads as instrumentation
    // on an unlit dashboard.
    modes = setOf(ThemeMode.Dark),
    // Both tiers now, and the enhanced one is authored rather than claimed:
    // `DemoThemeContrastTest` walks every combination a theme declares, so
    // adding `High` here is what gates `gTurboEnhancedColours` on every build.
    tiers = setOf(ContrastLevel.Standard, ContrastLevel.High),
    colours = { _, contrast ->
        if (contrast == ContrastLevel.High) gTurboEnhancedColours() else gTurboStandardColours()
    },
    shapes = gTurboShapes(),
    // Outfit for text, JetBrains Mono for figures — which is one argument now
    // that `kontourTypography` takes a second family. Before stage 4 this file
    // carried a `NumericFace` constant set to `FontFamily.Monospace` that
    // nothing could read, because `Typography` had one family for all sixteen
    // styles; the design's telemetry, prices and VINs had nowhere to go.
    typography = { kontourTypography(outfitFontFamily(), jetBrainsMonoFontFamily()) },
    // Capitals on every control label, which is the third thing this file could
    // not say. It reaches buttons, chips, tags, tabs, the extended FAB and field
    // labels — a control's *name* — and deliberately not dialog titles, banner
    // messages or list rows, which are prose in this design as much as any other.
    //
    // The 6dp corners are the other half of the same instrument look: nothing
    // here is a soft product surface, so the labels are set the way a gauge is.
    componentDefaults = ComponentDefaults(uppercaseLabels = true),
)

/** The page ground. Nearly black, and not quite neutral — it leans blue. */
private val Ground = Color(0xFF0A0A0B)
private val Card = Color(0xFF131314)
private val Well = Color(0xFF1C1B1C)
private val Raised = Color(0xFF201F20)

private val Ink = Color(0xFFE5E2E3)

/** A cool lavender grey rather than a neutral one. 5.84:1 on [Card]. */
private val Muted = Color(0xFF958DA1)
private val Subtle = Color(0xFF928B9A)
private val Disabled = Color(0xFF55505C)

private val Line = Color(0xFF2A2A2B)
private val LineSubtle = Color(0xFF1F1F20)

/**
 * The boundary of anything interactive — and **not** the value read off the
 * design.
 *
 * The design's rules are all around `#353436`, which is 1.6:1 on the page. That
 * is fine for the ones it uses them as: a divider between rows, the edge of a
 * card. It is not fine for the token whose job is to bound a control a person
 * has to find and press, which WCAG 1.4.11 holds to 3:1 — and the two are
 * different tokens here for exactly this reason, `outline` being exempt as
 * decorative and this one not.
 *
 * So the design's grey is [Line], and this is the lightest value on the same hue
 * that clears 3:1 against the *raised* ground, which is the hardest of the four.
 * Caught by `DemoThemeContrastTest` rather than by looking: at `#353436` it
 * reported 1.32:1 against `surfaceRaised`, and no amount of staring at the deck
 * would have said so.
 */
private val LineStrong = Color(0xFF6D6775)

/**
 * The same three greys at [ContrastLevel.High], on the same purple-grey hue.
 *
 * Lightened until each clears its threshold against `surfaceRaised`, which is
 * the hardest of GTurbo's four grounds rather than the page — 8.11, 7.36 and
 * 4.91 against 5.17, 4.99 and 3.01. `DisabledHc` is exempt from the walk (WCAG
 * 1.4.3 exempts disabled controls) and moves with them anyway: a disabled
 * control that has become invisible is not a passing result.
 */
private val MutedHc = Color(0xFFB9B4C1)
private val SubtleHc = Color(0xFFB1ABBA)
private val DisabledHc = Color(0xFF7B7488)
private val LineStrongHc = Color(0xFF908A98)

/** The fill red. Fails as text by design; carries white at 5.67:1. */
private val Red = Color(0xFFC6252B)

/** The logo red, brighter than the fill. Decoration only — see `brand`. */
private val LogoRed = Color(0xFFE11F26)

/** Red where red has to be read. 11.59:1 on the ground. */
private val Salmon = Color(0xFFFFB3AD)
private val RedTint = Color(0xFF2A0F11)
private val RedEdge = Color(0xFF4A1A1D)

private val Green = Color(0xFF79FC7B)
private val GreenTint = Color(0xFF0D1E11)
private val GreenEdge = Color(0xFF1E4A26)

private val Periwinkle = Color(0xFFBEC2FF)
private val PeriwinkleTint = Color(0xFF14162E)
private val PeriwinkleEdge = Color(0xFF2A2E52)

private val Amber = Color(0xFFF0B156)
private val AmberTint = Color(0xFF2A1D0C)
private val AmberEdge = Color(0xFF54401D)

/**
 * The scheme.
 *
 * Built on [darkColourScheme] so anything not named here keeps a value that has
 * already been through the contrast suite, rather than starting from raw hex.
 *
 * **`danger` and `accent` are the same red, and that is a real tension rather
 * than a shortcut.** The brand *is* a warning colour; a destructive action in
 * this product cannot distinguish itself by hue and distinguishes itself by
 * outline instead. Worth knowing before copying this palette into a product
 * whose brand is not already red.
 */
private fun gTurboStandardColours(): ColourScheme = darkColourScheme(
    background = Ground,
    surface = Card,
    surfaceSunken = Well,
    surfaceRaised = Raised,
    surfaceInverse = Ink,
    onSurfaceInverse = Ground,

    content = Ink,
    contentMuted = Muted,
    contentSubtle = Subtle,
    contentDisabled = Disabled,

    outline = Line,
    outlineStrong = LineStrong,
    outlineSubtle = LineSubtle,

    // Structural, not the brand — `primary` is defined as "the solid
    // call-to-action fill: near-black on light, near-white on dark", and every
    // built-in scheme holds to it.
    //
    // This was the brand red for a while, on the argument that the design's
    // call to action is red on every screen. True, and it made `primary` and
    // `accent.solid` the same colour, which is two roles wired to one constant:
    // `ThemeShowcase` drew two identical red swatches under different names, and
    // all 34 sites that read `primary` — both floating action buttons, the
    // slider tracks, the selected radio, every progress form, the timeline
    // nodes, the carousel indicator, the selected calendar day — went red along
    // with the accent, so nothing on screen could tell the two apart.
    //
    // A red call to action is `ButtonVariant.Accent`, which is what the accent
    // tone below is for.
    primary = Ink,
    onPrimary = Ground,

    accent = StatusColours(
        solid = Red,
        onSolid = Color.White,
        container = RedTint,
        onContainer = Salmon,
        border = RedEdge,
    ),
    brand = LogoRed,
    // Not the brand red: at 3.49:1 on the page it clears the 3:1 a focus ring
    // needs by a tenth, and a focus ring is the last thing to leave that little
    // room in. The salmon is 11.59:1.
    focusRing = Salmon,

    success = StatusColours(
        solid = Green,
        onSolid = Ground,
        container = GreenTint,
        onContainer = Green,
        border = GreenEdge,
    ),
    warning = StatusColours(
        solid = Amber,
        onSolid = Ground,
        container = AmberTint,
        onContainer = Amber,
        border = AmberEdge,
    ),
    danger = StatusColours(
        solid = Red,
        onSolid = Color.White,
        container = RedTint,
        onContainer = Salmon,
        border = RedEdge,
    ),
    info = StatusColours(
        solid = Periwinkle,
        onSolid = Ground,
        container = PeriwinkleTint,
        onContainer = Periwinkle,
        border = PeriwinkleEdge,
    ),

    // Heavier than the default's 50%: on a ground this dark, a scrim at half
    // opacity leaves the content behind a modal perfectly legible.
    scrim = Color(0xB3000000),
    overlayHover = Color(0x14FFFFFF),
    overlayPressed = Color(0x29FFFFFF),
    overlayDragged = Color(0x33FFFFFF),

    code = CodeColours(
        plain = Ink,
        keyword = Salmon,
        literal = Green,
        comment = Muted,
    ),
)

/**
 * GTurbo at [ContrastLevel.High] — the tier this theme could not have.
 *
 * Until stage 6 `highContrastDarkColourScheme` took three parameters and forced
 * a pure-black ground with the library's own `InkHc` surface ladder. GTurbo's
 * ground is `#0A0A0B` and its surfaces climb from it, so there was no argument
 * that kept the design and took the tuned rest; the theme declared
 * `tiers = setOf(Standard)` and said why. It takes twenty-seven now, and this is
 * what a brand's enhanced palette looks like: twenty-one values it owns, and six
 * it has no opinion about.
 *
 * **What it inherits** is the scrim, the three overlay washes — tuned for the
 * tier, and the four values the factory's own comment calls the easiest to miss
 * — and `code.keyword` and `code.literal`, which land at 11.19:1 and 12.13:1 on
 * GTurbo's own `surfaceSunken` despite having been chosen against a different
 * one. `code.plain` and `code.comment` follow [content] and [contentMuted]
 * without being passed, which is the coupling stage 6a added to the factory
 * paying for itself immediately.
 *
 * ### The red cannot survive AAA, and the design already says what to do
 *
 * `#C6252B` fails the enhanced tier in **both directions at once**: 3.49:1 on
 * the page against the 4.5 a fill needs, and 5.67:1 under white against the 7 a
 * label needs. Fixing one breaks the other — the red has to be lighter to clear
 * the page and darker to carry white — so there is no version of this red that
 * works, and the tier is not the place to discover that.
 *
 * The answer is in the standard palette already. `success`, `warning` and `info`
 * are each a **light solid with the near-black ground as their label**, and each
 * clears both thresholds with room (15.10, 10.49, 11.64 — the same figure twice,
 * because `onSolid = Ground` makes the two ratios the same measurement). The red
 * is the only tone drawn the other way round, and the salmon the design already
 * uses "wherever red has to be read" is 11.59:1 on the ground.
 *
 * So the enhanced tier **promotes the salmon from text colour to accent**, and
 * the red becomes what `brand` is for. The accent stops being the outlier and
 * starts looking like its three siblings, which is a stronger argument for the
 * change than the ratio is.
 *
 * ### The three greys, measured against the worst ground rather than the page
 *
 * A token has to clear every surface it can land on, and GTurbo's `surfaceRaised`
 * is the hard one. The standard values read 5.17, 4.99 and 3.01 there.
 *
 * | | standard | worst | enhanced | worst | needs |
 * |---|---|---|---|---|---|
 * | `contentMuted` | `#958DA1` | 5.17 | `#B9B4C1` | 8.11 | 7 |
 * | `contentSubtle` | `#928B9A` | 4.99 | `#B1ABBA` | 7.36 | 7 |
 * | `outlineStrong` | `#6D6775` | 3.01 | `#908A98` | 4.91 | 4.5 |
 *
 * All three are the same purple-grey hue, lightened — the tier reads as this
 * design turned up, not as a different one.
 */
private fun gTurboEnhancedColours(): ColourScheme = highContrastDarkColourScheme(
    background = Ground,
    surface = Card,
    surfaceSunken = Well,
    surfaceRaised = Raised,
    surfaceInverse = Ink,
    onSurfaceInverse = Ground,

    content = Ink,
    contentMuted = MutedHc,
    contentSubtle = SubtleHc,
    contentDisabled = DisabledHc,

    outline = Line,
    outlineStrong = LineStrongHc,
    outlineSubtle = LineSubtle,

    // The same structural near-white as the standard tier. It already clears
    // the enhanced thresholds by a wider margin than anything else in the
    // palette — 15.38:1 each way — so the tier changes nothing here.
    primary = Ink,
    onPrimary = Ground,

    accent = StatusColours(
        solid = Salmon,
        onSolid = Ground,
        container = RedTint,
        onContainer = Salmon,
        border = Salmon,
    ),
    // Still the logo red, still exempt from the walk, still 4.17:1 on the ground.
    // The tier changes what a reader has to *read*; a mark is not that.
    brand = LogoRed,
    focusRing = Salmon,

    success = StatusColours(
        solid = Green,
        onSolid = Ground,
        container = GreenTint,
        onContainer = Green,
        border = Green,
    ),
    warning = StatusColours(
        solid = Amber,
        onSolid = Ground,
        container = AmberTint,
        onContainer = Amber,
        border = Amber,
    ),
    danger = StatusColours(
        solid = Salmon,
        onSolid = Ground,
        container = RedTint,
        onContainer = Salmon,
        border = Salmon,
    ),
    info = StatusColours(
        solid = Periwinkle,
        onSolid = Ground,
        container = PeriwinkleTint,
        onContainer = Periwinkle,
        border = Periwinkle,
    ),
)

/**
 * The corner ladder, compressed into the design's band — still squircles.
 *
 * The design's corners sit in the 8–12dp range against a default ladder that
 * starts at 10dp and runs to 34dp, so this is the same idea an octave down. What
 * it is **not** is a different kind of corner: every rung is still a
 * [SquircleShape], because the argument the library makes for continuous
 * curvature does not weaken at small radii — a scale whose smoothing stops part
 * way up is a discontinuity in the *scale*, and a badge whose corner comes from
 * a different geometry than the card it sits on is more visible than the
 * smoothing that was being skipped.
 *
 * The 6dp step becomes 2dp. That matters beyond size: `inset` and `outset` walk
 * this ladder, and concentricity only holds while it steps evenly. A compressed
 * ladder keeps the property; an uneven one would not.
 *
 * **Thirteen rungs restated, and that is the specification for a later stage.**
 * There is no way today to say "the same ladder, from 6dp, in steps of 2, with
 * the capsule capped at 10" — the size rungs, the semantic aliases and the two
 * partial-corner shapes all have to be named. `kontourShapes(...)` will say it
 * in one line, and the test of that seam is that this function collapses and the
 * render does not move.
 */
private fun gTurboShapes(): Shapes =
    kontourShapes(extraSmall = 6.dp, step = 2.dp, capsuleCap = 10.dp).let {
        // The one line that differs from the library, which is the one a reader
        // should be looking at: a GTurbo button is a small-radius rectangle at
        // every size rather than a capsule that squares off as it grows, so
        // `control` and `field` leave the height-derived rule for a fixed rung.
        //
        // `capsule` itself is untouched — the design's avatars and status dots
        // are as round as anybody's, and 10dp is where they cap.
        it.copy(control = it.small, field = it.small)
    }
