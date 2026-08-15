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
 * | [accent] | Purple that carries text or a fill label |
 * | [brand] | Purple as pure decoration, where nothing needs to be legible on it |
 *
 * @property brand The literal Kontour purple, `#BB86FC`. Decorative only in
 *   light mode — it fails contrast against white, so it must never carry text,
 *   a fill label, or the focus ring. Use [accent] for those. Kept as a token so
 *   brand moments stay on-brand instead of drifting.
 * @property focusRing The keyboard focus indicator. Resolves to a purple that
 *   clears 3:1 against every ground in the scheme, which is why it is a
 *   separate token from [brand].
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
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,

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
    accent: Color = Palette.PurpleReadable,
    onAccent: Color = Palette.White,
    accentContainer: Color = Palette.PurpleTintLight,
    onAccentContainer: Color = Palette.PurpleDeep,
    brand: Color = Palette.Brand,
    focusRing: Color = Palette.PurpleReadable,
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
        solid = Palette.PurpleReadable,
        onSolid = Palette.White,
        container = Palette.PurpleTintLight,
        onContainer = Palette.PurpleDeep,
        border = Color(0xFFDCC9FB),
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
    onAccent = onAccent,
    accentContainer = accentContainer,
    onAccentContainer = onAccentContainer,
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
    accent: Color = Palette.Brand,
    onAccent: Color = Palette.PurpleOnLight,
    accentContainer: Color = Palette.PurpleTintDark,
    onAccentContainer: Color = Palette.PurpleLight,
    brand: Color = Palette.Brand,
    focusRing: Color = Palette.Brand,
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
        solid = Palette.VioletLight,
        onSolid = Palette.VioletOnLight,
        container = Palette.PurpleTintDark,
        onContainer = Palette.PurpleLight,
        border = Color(0xFF453259),
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
    onAccent = onAccent,
    accentContainer = accentContainer,
    onAccentContainer = onAccentContainer,
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

/** The light scheme at [ContrastLevel.High]: pure black text, AAA body contrast. */
fun highContrastLightColorScheme(): ColorScheme = lightColorScheme(
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
    accent = Palette.PurpleStrong,
    accentContainer = Palette.PurpleTintLightHc,
    onAccentContainer = Palette.PurpleDeeper,
    focusRing = Palette.PurpleDeep,
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
        solid = Palette.PurpleStrong,
        onSolid = Palette.White,
        container = Palette.PurpleTintLightHc,
        onContainer = Palette.PurpleDeeper,
        border = Palette.PurpleStrong,
    ),
)

/** The dark scheme at [ContrastLevel.High]: pure black ground, pure white text. */
fun highContrastDarkColorScheme(): ColorScheme = darkColorScheme(
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
    accent = Palette.PurpleLightHc,
    onAccent = Palette.PurpleHcOnLight,
    accentContainer = Palette.PurpleTintDarkHc,
    onAccentContainer = Palette.PurplePaleHc,
    focusRing = Palette.PurpleLightHc,
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
        solid = Palette.VioletPale,
        onSolid = Palette.VioletHcOnLight,
        container = Palette.PurpleTintDarkHc,
        onContainer = Palette.PurplePaleHc,
        border = Palette.VioletPale,
    ),
)

/** Picks the built-in scheme for a given mode and contrast tier. */
fun kontourColorScheme(dark: Boolean, contrast: ContrastLevel): ColorScheme = when {
    dark && contrast == ContrastLevel.High -> highContrastDarkColorScheme()
    dark -> darkColorScheme()
    contrast == ContrastLevel.High -> highContrastLightColorScheme()
    else -> lightColorScheme()
}
