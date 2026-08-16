package io.kontour.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * How hard the theme pushes contrast.
 *
 * [Standard] targets WCAG AA — 4.5:1 for body text, 3:1 for the boundary of an
 * interactive control. [High] targets AAA for text (7:1) and 4.5:1 for
 * boundaries, for users who have asked their OS for more contrast.
 *
 * A `Medium` tier can be added later; it is deliberately absent rather than
 * stubbed, because every tier we name is a tier the contrast suite has to
 * actually verify.
 */
enum class ContrastLevel { Standard, High }

/**
 * The colours of one status tone — success, warning, danger or info.
 *
 * The split mirrors how the web properties already use them: a [solid] fill for
 * badges and buttons, a soft [container] tint for banners and chips, and
 * [onContainer] doing double duty as the standalone text colour for that tone.
 */
@Immutable
data class StatusColors(
    /** Filled backgrounds — badges, solid buttons, progress fills. */
    val solid: Color,
    /** Labels and icons drawn on [solid]. */
    val onSolid: Color,
    /** Soft tinted backgrounds — banners, chips, callouts. */
    val container: Color,
    /** Text and icons on [container]; also the tone's standalone text colour. */
    val onContainer: Color,
    /** Hairline around [container]. Decorative — no contrast requirement. */
    val border: Color,
)

/**
 * Every colour a component is allowed to use, named for what it means rather
 * than what it looks like.
 *
 * Read these through [Theme.colors]; never hardcode a [Color] in a component.
 * That indirection is the whole reason a theme can be swapped, a contrast tier
 * can be raised, or a generated palette can be dropped in later without
 * touching a single component.
 *
 * ### Choosing between neighbours
 *
 * | Reach for | When |
 * |---|---|
 * | [content] | Anything the user reads to understand the screen |
 * | [contentMuted] | Supporting text — captions, timestamps, secondary labels |
 * | [contentSubtle] | Placeholders and tertiary hints. Still real text; still 4.5:1 |
 * | [contentDisabled] | Only for genuinely disabled controls (WCAG-exempt) |
 * | [outline] | Dividers and decorative rules |
 * | [outlineStrong] | The boundary of anything interactive — inputs, checkboxes |
 * | [accent] | The interactive colour, which has to carry text and a fill label |
 * | [brand] | The product's own colour, which may be too vivid to carry anything |
 *
 * @property brand The product's own colour, wherever a brand moment wants it and
 *   nothing has to be legible on top — a splash, a marketing surface, an
 *   illustration.
 *
 *   It is a **role**, not a hue. The default schemes have no product in them, so
 *   it resolves to the same blue as [accent]; an app that sets one separates the
 *   two. The reason it is a separate token at all is that a brand colour is
 *   under no obligation to pass a contrast checker — Kontour's own `#BB86FC` is
 *   2.1:1 on white — and [accent] is under every obligation, so one token could
 *   not be both. `KontourBrandTheme` in `anyways` is the worked example.
 * @property focusRing The keyboard focus indicator. Held to 3:1 against every
 *   ground in the scheme, which is why it is separate from [brand] and follows
 *   [accent] instead.
 */
@Immutable
data class ColorScheme(
    // --- Grounds ---
    /** The page itself. */
    val background: Color,
    /** Cards, sheets, menus — anything sitting on [background]. */
    val surface: Color,
    /** Wells and inset areas: input fills, code blocks, table stripes. */
    val surfaceSunken: Color,
    /** Above [surface]: menus over cards, elevated dialogs. */
    val surfaceRaised: Color,
    /** Inverted ground for toasts and tooltips. */
    val surfaceInverse: Color,
    /** Content on [surfaceInverse]. */
    val onSurfaceInverse: Color,

    // --- Content ---
    val content: Color,
    val contentMuted: Color,
    val contentSubtle: Color,
    val contentDisabled: Color,

    // --- Lines ---
    /** Dividers and decorative rules. Too light to bound a control — use [outlineStrong]. */
    val outline: Color,
    /** The boundary of an interactive control. Clears 3:1 against every ground. */
    val outlineStrong: Color,
    /** The faintest rule the scheme offers, for dense lists. */
    val outlineSubtle: Color,

    // --- Primary action ---
    /** The solid call-to-action fill: near-black on light, near-white on dark. */
    val primary: Color,
    val onPrimary: Color,

    // --- Accent ---
    /**
     * The brand tone, shaped exactly like the four status tones.
     *
     * It used to be four loose fields — `accent`, `onAccent`, `accentContainer`,
     * `onAccentContainer` — beside four grouped [StatusColors]. One tone type and
     * six tones means a component that takes a tone can take *this* one, which is
     * what `ButtonVariant.Accent` and `BannerTone.Accent` are made of, and it is
     * why `TagTone.Accent` had to reach past the group to build itself.
     */
    val accent: StatusColors,

    // --- Brand ---
    val brand: Color,
    val focusRing: Color,

    // --- Status ---
    val success: StatusColors,
    val warning: StatusColors,
    val danger: StatusColors,
    val info: StatusColors,

    // --- Overlays ---
    /** Dims content behind a modal. */
    val scrim: Color,
    /** Tonal wash applied on hover, composited over whatever is underneath. */
    val overlayHover: Color,
    /** Tonal wash applied while pressed. */
    val overlayPressed: Color,
    /** Tonal wash applied while an element is being dragged. */
    val overlayDragged: Color,

    /** Whether this scheme reads as dark. Drives status-bar icons and image scrims. */
    val isDark: Boolean,
)

/**
 * The default light scheme: white and near-black structure, purple as accent.
 *
 * Every parameter is defaulted, so a product theme overrides only what it needs:
 * ```
 * val ocean = lightColorScheme(accent = Color(0xFF0B6E99), focusRing = Color(0xFF0B6E99))
 * ```
 */
fun lightColorScheme(
    background: Color = Palette.White,
    surface: Color = Palette.White,
    surfaceSunken: Color = Palette.Grey50,
    surfaceRaised: Color = Palette.White,
    surfaceInverse: Color = Palette.Ink,
    onSurfaceInverse: Color = Palette.White,
    content: Color = Palette.Ink,
    contentMuted: Color = Palette.Grey700,
    contentSubtle: Color = Palette.Grey600,
    contentDisabled: Color = Palette.Grey400,
    outline: Color = Palette.Grey200,
    outlineStrong: Color = Palette.Grey500,
    outlineSubtle: Color = Palette.Grey100,
    primary: Color = Palette.Ink,
    onPrimary: Color = Palette.White,
    accent: StatusColors = StatusColors(
        solid = Palette.BlueReadable,
        onSolid = Palette.White,
        container = Palette.BlueTintLight,
        onContainer = Palette.BlueDeep,
        border = Palette.BlueBorderLight,
    ),
    brand: Color = Palette.BlueReadable,
    focusRing: Color = Palette.BlueReadable,
    success: StatusColors = StatusColors(
        solid = Palette.GreenSolid,
        onSolid = Palette.White,
        container = Palette.GreenTint,
        onContainer = Palette.GreenDeep,
        border = Color(0xFFC5E3C7),
    ),
    warning: StatusColors = StatusColors(
        solid = Palette.AmberSolid,
        onSolid = Palette.White,
        container = Palette.AmberTint,
        onContainer = Palette.AmberDeep,
        border = Color(0xFFF3D9B5),
    ),
    danger: StatusColors = StatusColors(
        solid = Palette.RedSolid,
        onSolid = Palette.White,
        container = Palette.RedTint,
        onContainer = Palette.RedDeep,
        border = Color(0xFFF6C9C9),
    ),
    info: StatusColors = StatusColors(
        solid = Palette.SkySolid,
        onSolid = Palette.White,
        container = Palette.SkyTint,
        onContainer = Palette.SkyDeep,
        border = Palette.SkyBorderLight,
    ),
    scrim: Color = Color(0x8A121212),
    overlayHover: Color = Color(0x0F121212),
    overlayPressed: Color = Color(0x1F121212),
    overlayDragged: Color = Color(0x29121212),
): ColorScheme = ColorScheme(
    background = background,
    surface = surface,
    surfaceSunken = surfaceSunken,
    surfaceRaised = surfaceRaised,
    surfaceInverse = surfaceInverse,
    onSurfaceInverse = onSurfaceInverse,
    content = content,
    contentMuted = contentMuted,
    contentSubtle = contentSubtle,
    contentDisabled = contentDisabled,
    outline = outline,
    outlineStrong = outlineStrong,
    outlineSubtle = outlineSubtle,
    primary = primary,
    onPrimary = onPrimary,
    accent = accent,
    brand = brand,
    focusRing = focusRing,
    success = success,
    warning = warning,
    danger = danger,
    info = info,
    scrim = scrim,
    overlayHover = overlayHover,
    overlayPressed = overlayPressed,
    overlayDragged = overlayDragged,
    isDark = false,
)

/** The default dark scheme. Surfaces and muted tones come from `home html.dark`. */
fun darkColorScheme(
    background: Color = Palette.Ink,
    surface: Color = Palette.Slate850,
    surfaceSunken: Color = Palette.Slate900,
    surfaceRaised: Color = Palette.Slate800,
    surfaceInverse: Color = Palette.Paper,
    onSurfaceInverse: Color = Palette.Ink,
    content: Color = Palette.Slate200,
    contentMuted: Color = Palette.Slate400,
    contentSubtle: Color = Palette.Slate500,
    contentDisabled: Color = Palette.Slate300,
    outline: Color = Palette.Slate700,
    outlineStrong: Color = Palette.Slate600,
    outlineSubtle: Color = Color(0xFF2C2735),
    primary: Color = Palette.Paper,
    onPrimary: Color = Palette.Ink,
    accent: StatusColors = StatusColors(
        solid = Palette.BlueLight,
        onSolid = Palette.BlueOnLight,
        container = Palette.BlueTintDark,
        onContainer = Palette.BluePale,
        border = Palette.BlueBorderDark,
    ),
    brand: Color = Palette.BlueLight,
    focusRing: Color = Palette.BlueLight,
    success: StatusColors = StatusColors(
        solid = Palette.GreenLight,
        onSolid = Palette.GreenOnLight,
        container = Palette.GreenDarkTint,
        onContainer = Palette.GreenPale,
        border = Color(0xFF2A5232),
    ),
    warning: StatusColors = StatusColors(
        solid = Palette.AmberLight,
        onSolid = Palette.AmberOnLight,
        container = Palette.AmberDarkTint,
        onContainer = Palette.AmberPale,
        border = Color(0xFF5C3D1B),
    ),
    danger: StatusColors = StatusColors(
        solid = Palette.RedLight,
        onSolid = Palette.RedOnLight,
        container = Palette.RedDarkTint,
        onContainer = Palette.RedPale,
        border = Color(0xFF5E2630),
    ),
    info: StatusColors = StatusColors(
        solid = Palette.SkyLight,
        onSolid = Palette.SkyOnLight,
        container = Palette.SkyDarkTint,
        onContainer = Palette.SkyPale,
        border = Palette.SkyBorderDark,
    ),
    scrim: Color = Color(0x99000000),
    overlayHover: Color = Color(0x14FFFFFF),
    overlayPressed: Color = Color(0x29FFFFFF),
    overlayDragged: Color = Color(0x33FFFFFF),
): ColorScheme = ColorScheme(
    background = background,
    surface = surface,
    surfaceSunken = surfaceSunken,
    surfaceRaised = surfaceRaised,
    surfaceInverse = surfaceInverse,
    onSurfaceInverse = onSurfaceInverse,
    content = content,
    contentMuted = contentMuted,
    contentSubtle = contentSubtle,
    contentDisabled = contentDisabled,
    outline = outline,
    outlineStrong = outlineStrong,
    outlineSubtle = outlineSubtle,
    primary = primary,
    onPrimary = onPrimary,
    accent = accent,
    brand = brand,
    focusRing = focusRing,
    success = success,
    warning = warning,
    danger = danger,
    info = info,
    scrim = scrim,
    overlayHover = overlayHover,
    overlayPressed = overlayPressed,
    overlayDragged = overlayDragged,
    isDark = true,
)

/**
 * The light scheme at [ContrastLevel.High]: pure black text, AAA body contrast.
 *
 * **Takes the three tones a product actually owns**, and nothing else. The rest
 * of this tier is not a design choice: at AAA the grounds are pure white, the
 * content is pure black, and the greys are the lightest values that still clear 7:1.
 * Parameterising those would offer a caller the freedom to break the only thing
 * the tier exists to guarantee.
 *
 * An app with a brand supplies its high-contrast accent here, the way
 * `KontourBrandTheme` in `anyways` does.
 */
fun highContrastLightColorScheme(
    accent: StatusColors = StatusColors(
        solid = Palette.BlueStrong,
        onSolid = Palette.White,
        container = Palette.BlueTintLightHc,
        onContainer = Palette.BlueDeeper,
        border = Palette.BlueStrong,
    ),
    brand: Color = accent.solid,
    focusRing: Color = accent.solid,
): ColorScheme = lightColorScheme(
    // The press and hover washes are the four values easiest to miss, because
    // they are not named after anything visible: a 6% wash is invisible at this
    // tier, so a control the user is pressing looks like a control they are not.
    // The scrim goes darker for the same reason — what it is separating from is
    // now higher contrast, so the old alpha separates less.
    scrim = Color(0xB3000000),
    overlayHover = Color(0x1F000000),
    overlayPressed = Color(0x3D000000),
    overlayDragged = Color(0x4D000000),
    surfaceSunken = Palette.GreyHcSunken,
    surfaceInverse = Palette.Black,
    content = Palette.Black,
    contentMuted = Palette.GreyHcMuted,
    contentSubtle = Palette.GreyHcSubtle,
    contentDisabled = Palette.GreyHcDisabled,
    outline = Palette.GreyHcOutline,
    outlineStrong = Palette.GreyHcMuted,
    outlineSubtle = Palette.GreyHcOutlineSubtle,
    primary = Palette.Black,
    accent = accent,
    brand = brand,
    focusRing = focusRing,
    success = StatusColors(
        solid = Palette.GreenHcSolid,
        onSolid = Palette.White,
        container = Palette.GreenHcTint,
        onContainer = Palette.GreenHcDeep,
        border = Palette.GreenHcSolid,
    ),
    warning = StatusColors(
        solid = Palette.AmberHcSolid,
        onSolid = Palette.White,
        container = Palette.AmberHcTint,
        onContainer = Palette.AmberHcDeep,
        border = Palette.AmberHcSolid,
    ),
    danger = StatusColors(
        solid = Palette.RedHcSolid,
        onSolid = Palette.White,
        container = Palette.RedHcTint,
        onContainer = Palette.RedHcDeep,
        border = Palette.RedHcSolid,
    ),
    info = StatusColors(
        solid = Palette.SkyHcSolid,
        onSolid = Palette.White,
        container = Palette.SkyHcTint,
        onContainer = Palette.SkyDeep,
        border = Palette.SkyHcSolid,
    ),
)

/**
 * The dark scheme at [ContrastLevel.High]: pure black ground, pure white text.
 *
 * Takes the same three tones as [highContrastLightColorScheme], for the same
 * reason: at AAA on black, everything but the accent is fixed by the ratio it
 * has to clear.
 */
fun highContrastDarkColorScheme(
    accent: StatusColors = StatusColors(
        solid = Palette.BlueLightHc,
        onSolid = Palette.BlueHcOnLight,
        container = Palette.BlueTintDarkHc,
        onContainer = Palette.BluePaleHc,
        border = Palette.BlueLightHc,
    ),
    brand: Color = accent.solid,
    focusRing: Color = accent.solid,
): ColorScheme = darkColorScheme(
    scrim = Color(0xC2000000),
    overlayHover = Color(0x24FFFFFF),
    overlayPressed = Color(0x47FFFFFF),
    overlayDragged = Color(0x54FFFFFF),
    background = Palette.Black,
    surface = Palette.InkHcSurface,
    surfaceSunken = Palette.InkHcSunken,
    surfaceRaised = Palette.InkHcRaised,
    surfaceInverse = Palette.White,
    onSurfaceInverse = Palette.Black,
    content = Palette.White,
    contentMuted = Palette.SlateHcMuted,
    contentSubtle = Palette.SlateHcSubtle,
    contentDisabled = Palette.SlateHcDisabled,
    outline = Palette.SlateHcOutline,
    outlineStrong = Palette.SlateHcOutlineStrong,
    outlineSubtle = Palette.SlateHcOutlineSubtle,
    primary = Palette.White,
    onPrimary = Palette.Black,
    accent = accent,
    brand = brand,
    focusRing = focusRing,
    success = StatusColors(
        solid = Palette.GreenHcLight,
        onSolid = Palette.GreenHcOnLight,
        container = Palette.GreenHcDarkTint,
        onContainer = Palette.GreenHcPale,
        border = Palette.GreenHcLight,
    ),
    warning = StatusColors(
        solid = Palette.AmberHcLight,
        onSolid = Palette.AmberHcOnLight,
        container = Palette.AmberHcDarkTint,
        onContainer = Palette.AmberHcPale,
        border = Palette.AmberHcLight,
    ),
    danger = StatusColors(
        solid = Palette.RedHcLight,
        onSolid = Palette.RedHcOnLight,
        container = Palette.RedHcDarkTint,
        onContainer = Palette.RedHcPale,
        border = Palette.RedHcLight,
    ),
    info = StatusColors(
        solid = Palette.SkyHcLight,
        onSolid = Palette.SkyHcOnLight,
        container = Palette.SkyHcDarkTint,
        onContainer = Palette.SkyHcPale,
        border = Palette.SkyHcLight,
    ),
)

/** Picks the built-in scheme for a given mode and contrast tier. */
fun kontourColorScheme(dark: Boolean, contrast: ContrastLevel): ColorScheme = when {
    dark && contrast == ContrastLevel.High -> highContrastDarkColorScheme()
    dark -> darkColorScheme()
    contrast == ContrastLevel.High -> highContrastLightColorScheme()
    else -> lightColorScheme()
}
