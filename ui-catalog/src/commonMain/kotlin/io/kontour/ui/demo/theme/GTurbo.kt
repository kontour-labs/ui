package io.kontour.ui.demo.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.CapsuleCornerSize
import io.kontour.ui.theme.CodeColours
import io.kontour.ui.theme.ColourScheme
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.Shapes
import io.kontour.ui.theme.SquircleShape
import io.kontour.ui.theme.StatusColours
import io.kontour.ui.theme.darkColourScheme
import io.kontour.ui.theme.jetBrainsMonoFontFamily
import io.kontour.ui.theme.kontourTypography
import io.kontour.ui.theme.leadingCornersOnly
import io.kontour.ui.theme.outfitFontFamily
import io.kontour.ui.theme.topCornersOnly

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
 * ### It is written verbosely on purpose
 *
 * Three things here are longer than they should be, and each one is a
 * specification rather than an oversight:
 *
 * 1. **Thirteen restated shape tokens**, because there is no way to say "the
 *    same ladder, from 6dp, in steps of 2" in one line. The corners are still
 *    squircles — what is missing is a way to compress the scale, not a way to
 *    turn its geometry off.
 * 2. **No uppercase labels**, though the design sets every button, field label
 *    and badge in capitals. Casing is not something a `TextStyle` can carry and
 *    there is nowhere else to put it.
 *
 * A third is now closed and is left here as the record of what the exercise is
 * for: this file used to carry `FontFamily.Monospace` in a constant nothing
 * could read, because `Typography` had one family for all sixteen styles. Stage
 * 4 gave it two and a `mono` style, and the line went from a dead constant plus
 * nine lines of KDoc explaining why it was dead to one extra argument.
 *
 * Each of those closes in a later stage, and the acceptance test for the seam is
 * that *this file gets shorter and its render does not change*.
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
    // Standard only, for now. An enhanced palette needs the high-contrast
    // factories to take more than three parameters, which is a later stage; and
    // claiming a tier this has not authored would be worse than declining it.
    tiers = setOf(ContrastLevel.Standard),
    colours = { _, _ -> gTurboColours() },
    shapes = gTurboShapes(),
    // Outfit for text, JetBrains Mono for figures — which is one argument now
    // that `kontourTypography` takes a second family. Before stage 4 this file
    // carried a `NumericFace` constant set to `FontFamily.Monospace` that
    // nothing could read, because `Typography` had one family for all sixteen
    // styles; the design's telemetry, prices and VINs had nowhere to go.
    typography = { kontourTypography(outfitFontFamily(), jetBrainsMonoFontFamily()) },
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
private fun gTurboColours(): ColourScheme = darkColourScheme(
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

    // The solid call to action is the brand red, not the near-white the default
    // scheme uses. This is the one place GTurbo departs from the library's idea
    // of `primary` as structural rather than brand — and it is what the design
    // does on every screen, so the theme follows the design.
    primary = Red,
    onPrimary = Color.White,

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
private fun gTurboShapes(): Shapes {
    val extraSmall = SquircleShape(6.dp)
    val small = SquircleShape(8.dp)
    val medium = SquircleShape(10.dp)
    val large = SquircleShape(12.dp)
    val extraLarge = SquircleShape(14.dp)
    return Shapes(
        extraSmall = extraSmall,
        small = small,
        medium = medium,
        large = large,
        extraLarge = extraLarge,
        // A capsule stays a capsule: the design's avatars and status dots are as
        // round as anybody's, and `capsule` is half the shorter side rather than
        // a rung of the ladder above.
        capsule = SquircleShape(CapsuleCornerSize(cap = 10.dp)),
        // A GTurbo button is a small-radius rectangle at every size rather than
        // a capsule that squares off as it grows, so `control` and `field` leave
        // the height-derived rule and take a fixed rung.
        control = small,
        field = small,
        container = medium,
        panel = large,
        sheet = extraLarge.topCornersOnly(),
        sideSheet = extraLarge.leadingCornersOnly(),
    )
}
