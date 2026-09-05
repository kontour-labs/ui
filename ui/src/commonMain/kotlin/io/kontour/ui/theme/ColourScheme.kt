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
 * The four colours source code is drawn in, on the documentation site.
 *
 * Highlighting is **decorative**: the code says exactly the same thing in one
 * colour, and nothing here carries information the characters do not. That is
 * why there are four classes and not the fifteen an editor uses — a page of
 * documentation is read once, and a reader picking out `fun` from a string
 * literal at a glance is the whole benefit.
 *
 * [plain] and [comment] are ordinarily the scheme's own [ColourScheme.content]
 * and [ColourScheme.contentMuted], and are named separately so a consumer
 * theming the site can move them without moving its body text.
 *
 * All four are drawn on [ColourScheme.surfaceSunken] and all four are checked
 * against it by `ColourSchemeContrastTest` at the scheme's own tier — 4.5:1
 * standard, 7:1 high contrast. That check is the reason these can be palette
 * values shared with the status tones rather than colours of their own:
 * retuning one to suit a banner fails here.
 */
@Immutable
data class CodeColours(
    /** Identifiers, punctuation, everything with no special meaning. */
    val plain: Color,
    /** `fun`, `val`, `when`, and annotations. */
    val keyword: Color,
    /** Strings, characters and numbers. */
    val literal: Color,
    /** `//` and `/* */`. */
    val comment: Color,
)

/**
 * The colours of one status tone — success, warning, danger or info.
 *
 * The split mirrors how the web properties already use them: a [solid] fill for
 * badges and buttons, a soft [container] tint for banners and chips, and
 * [onContainer] doing double duty as the standalone text colour for that tone.
 */
@Immutable
data class StatusColours(
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
 * Read these through [Theme.colours]; never hardcode a [Color] in a component.
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
data class ColourScheme(
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
     * `onAccentContainer` — beside four grouped [StatusColours]. One tone type and
     * six tones means a component that takes a tone can take *this* one, which is
     * what `ButtonVariant.Accent` and `BannerTone.Accent` are made of, and it is
     * why `TagTone.Accent` had to reach past the group to build itself.
     */
    val accent: StatusColours,

    // --- Brand ---
    val brand: Color,
    val focusRing: Color,

    // --- Status ---
    val success: StatusColours,
    val warning: StatusColours,
    val danger: StatusColours,
    val info: StatusColours,

    // --- Overlays ---
    /**
     * Dims content behind a modal.
     *
     * Lighter than it was, because it is no longer working alone: a modal also
     * blurs what is behind it, and the two together separate better than either
     * did at twice the strength. Under a 54% dim a blur is invisible, so the
     * blur was worth nothing until this came down.
     *
     * It still has to carry the separation **on its own**, and that is not a
     * hedge — Android below API 31 has no `RenderEffect` and this library's
     * `minSdk` is 29, so on Android 10 and 11 the dim is all there is. Check any
     * change to it against `contrastRatio` with the blur discounted, not by
     * looking at a render that has one.
     *
     * The high-contrast tiers are deliberately left where they were. High
     * contrast exists to maximise separation, and it is a per-user setting
     * rather than a platform, so nothing about it is a cross-platform
     * difference.
     */
    val scrim: Color,
    /** Tonal wash applied on hover, composited over whatever is underneath. */
    val overlayHover: Color,
    /** Tonal wash applied while pressed. */
    val overlayPressed: Color,
    /** Tonal wash applied while an element is being dragged. */
    val overlayDragged: Color,

    /** How source code is drawn on the documentation site. See [CodeColours]. */
    val code: CodeColours,

    /** Whether this scheme reads as dark. Drives status-bar icons and image scrims. */
    val isDark: Boolean,
)

/**
 * The default light scheme: white and near-black structure, purple as accent.
 *
 * Every parameter is defaulted, so a product theme overrides only what it needs:
 * ```
 * val ocean = lightColourScheme(accent = Color(0xFF0B6E99), focusRing = Color(0xFF0B6E99))
 * ```
 */
fun lightColourScheme(
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
    accent: StatusColours = StatusColours(
        solid = Palette.BlueReadable,
        onSolid = Palette.White,
        container = Palette.BlueTintLight,
        onContainer = Palette.BlueDeep,
        border = Palette.BlueBorderLight,
    ),
    brand: Color = Palette.BlueReadable,
    focusRing: Color = Palette.BlueReadable,
    success: StatusColours = StatusColours(
        solid = Palette.GreenSolid,
        onSolid = Palette.White,
        container = Palette.GreenTint,
        onContainer = Palette.GreenDeep,
        border = Color(0xFFC5E3C7),
    ),
    warning: StatusColours = StatusColours(
        solid = Palette.AmberSolid,
        onSolid = Palette.White,
        container = Palette.AmberTint,
        onContainer = Palette.AmberDeep,
        border = Color(0xFFF3D9B5),
    ),
    danger: StatusColours = StatusColours(
        solid = Palette.RedSolid,
        onSolid = Palette.White,
        container = Palette.RedTint,
        onContainer = Palette.RedDeep,
        border = Color(0xFFF6C9C9),
    ),
    info: StatusColours = StatusColours(
        solid = Palette.SkySolid,
        onSolid = Palette.White,
        container = Palette.SkyTint,
        onContainer = Palette.SkyDeep,
        border = Palette.SkyBorderLight,
    ),
    scrim: Color = Color(0x70121212),
    overlayHover: Color = Color(0x0F121212),
    overlayPressed: Color = Color(0x1F121212),
    overlayDragged: Color = Color(0x29121212),
    // Blue and green off the ramps above rather than colours of their own —
    // they already clear 4.5:1 on `surfaceSunken`, which is the only ground
    // code is ever drawn on, and `ColourSchemeContrastTest` now says so.
    code: CodeColours = CodeColours(
        plain = content,
        keyword = Palette.BlueDeep,
        literal = Palette.GreenDeep,
        comment = contentMuted,
    ),
): ColourScheme = ColourScheme(
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
    code = code,
    isDark = false,
)

/** The default dark scheme. Surfaces and muted tones come from `home html.dark`. */
fun darkColourScheme(
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
    accent: StatusColours = StatusColours(
        solid = Palette.BlueLight,
        onSolid = Palette.BlueOnLight,
        container = Palette.BlueTintDark,
        onContainer = Palette.BluePale,
        border = Palette.BlueBorderDark,
    ),
    brand: Color = Palette.BlueLight,
    focusRing: Color = Palette.BlueLight,
    success: StatusColours = StatusColours(
        solid = Palette.GreenLight,
        onSolid = Palette.GreenOnLight,
        container = Palette.GreenDarkTint,
        onContainer = Palette.GreenPale,
        border = Color(0xFF2A5232),
    ),
    warning: StatusColours = StatusColours(
        solid = Palette.AmberLight,
        onSolid = Palette.AmberOnLight,
        container = Palette.AmberDarkTint,
        onContainer = Palette.AmberPale,
        border = Color(0xFF5C3D1B),
    ),
    danger: StatusColours = StatusColours(
        solid = Palette.RedLight,
        onSolid = Palette.RedOnLight,
        container = Palette.RedDarkTint,
        onContainer = Palette.RedPale,
        border = Color(0xFF5E2630),
    ),
    info: StatusColours = StatusColours(
        solid = Palette.SkyLight,
        onSolid = Palette.SkyOnLight,
        container = Palette.SkyDarkTint,
        onContainer = Palette.SkyPale,
        border = Palette.SkyBorderDark,
    ),
    scrim: Color = Color(0x80000000),
    overlayHover: Color = Color(0x14FFFFFF),
    overlayPressed: Color = Color(0x29FFFFFF),
    overlayDragged: Color = Color(0x33FFFFFF),
    code: CodeColours = CodeColours(
        plain = content,
        keyword = Palette.BlueLight,
        literal = Palette.GreenLight,
        comment = contentMuted,
    ),
): ColourScheme = ColourScheme(
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
    code = code,
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
fun highContrastLightColourScheme(
    accent: StatusColours = StatusColours(
        solid = Palette.BlueStrong,
        onSolid = Palette.White,
        container = Palette.BlueTintLightHc,
        onContainer = Palette.BlueDeeper,
        border = Palette.BlueStrong,
    ),
    brand: Color = accent.solid,
    focusRing: Color = accent.solid,
): ColourScheme = lightColourScheme(
    // The press and hover washes are the four values easiest to miss, because
    // they are not named after anything visible: a 6% wash is invisible at this
    // tier, so a control the user is pressing looks like a control they are not.
    // The scrim goes darker for the same reason — what it is separating from is
    // now higher contrast, so the old alpha separates less.
    scrim = Color(0xB3000000),
    overlayHover = Color(0x1F000000),
    overlayPressed = Color(0x3D000000),
    overlayDragged = Color(0x4D000000),
    // Deeper, because 7:1 on a near-white ground leaves no room for the
    // standard pair — and still 45 and 18 ΔE from each other and from black,
    // which is what stops high contrast collapsing into one colour.
    code = CodeColours(
        plain = Palette.Black,
        keyword = Palette.BlueDeeper,
        literal = Palette.GreenOnLight,
        comment = Palette.GreyHcMuted,
    ),
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
    success = StatusColours(
        solid = Palette.GreenHcSolid,
        onSolid = Palette.White,
        container = Palette.GreenHcTint,
        onContainer = Palette.GreenHcDeep,
        border = Palette.GreenHcSolid,
    ),
    warning = StatusColours(
        solid = Palette.AmberHcSolid,
        onSolid = Palette.White,
        container = Palette.AmberHcTint,
        onContainer = Palette.AmberHcDeep,
        border = Palette.AmberHcSolid,
    ),
    danger = StatusColours(
        solid = Palette.RedHcSolid,
        onSolid = Palette.White,
        container = Palette.RedHcTint,
        onContainer = Palette.RedHcDeep,
        border = Palette.RedHcSolid,
    ),
    info = StatusColours(
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
 * Takes the same three tones as [highContrastLightColourScheme], for the same
 * reason: at AAA on black, everything but the accent is fixed by the ratio it
 * has to clear.
 */
fun highContrastDarkColourScheme(
    accent: StatusColours = StatusColours(
        solid = Palette.BlueLightHc,
        onSolid = Palette.BlueHcOnLight,
        container = Palette.BlueTintDarkHc,
        onContainer = Palette.BluePaleHc,
        border = Palette.BlueLightHc,
    ),
    brand: Color = accent.solid,
    focusRing: Color = accent.solid,
): ColourScheme = darkColourScheme(
    scrim = Color(0xC2000000),
    overlayHover = Color(0x24FFFFFF),
    overlayPressed = Color(0x47FFFFFF),
    overlayDragged = Color(0x54FFFFFF),
    code = CodeColours(
        plain = Palette.White,
        keyword = Palette.BlueLightHc,
        literal = Palette.GreenPale,
        comment = Palette.SlateHcMuted,
    ),
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
    success = StatusColours(
        solid = Palette.GreenHcLight,
        onSolid = Palette.GreenHcOnLight,
        container = Palette.GreenHcDarkTint,
        onContainer = Palette.GreenHcPale,
        border = Palette.GreenHcLight,
    ),
    warning = StatusColours(
        solid = Palette.AmberHcLight,
        onSolid = Palette.AmberHcOnLight,
        container = Palette.AmberHcDarkTint,
        onContainer = Palette.AmberHcPale,
        border = Palette.AmberHcLight,
    ),
    danger = StatusColours(
        solid = Palette.RedHcLight,
        onSolid = Palette.RedHcOnLight,
        container = Palette.RedHcDarkTint,
        onContainer = Palette.RedHcPale,
        border = Palette.RedHcLight,
    ),
    info = StatusColours(
        solid = Palette.SkyHcLight,
        onSolid = Palette.SkyHcOnLight,
        container = Palette.SkyHcDarkTint,
        onContainer = Palette.SkyHcPale,
        border = Palette.SkyHcLight,
    ),
)

/** Picks the built-in scheme for a given mode and contrast tier. */
fun kontourColourScheme(dark: Boolean, contrast: ContrastLevel): ColourScheme = when {
    dark && contrast == ContrastLevel.High -> highContrastDarkColourScheme()
    dark -> darkColourScheme()
    contrast == ContrastLevel.High -> highContrastLightColourScheme()
    else -> lightColourScheme()
}

/**
 * This colour, invisible — the target to *animate* to rather than
 * [Color.Transparent].
 *
 * `Color.Transparent` is **black** with an alpha of zero, and colour
 * interpolation moves the channels as well as the alpha. So a tint animating out
 * to `Color.Transparent` does not fade: it darkens on its way to nothing, and
 * comes back out of the dark on its way in. On a light tint against a light
 * ground that is a grey flash, and it was visible in two places at once — a text
 * field greyed for two frames every time it took or lost focus, and a date range
 * picker left a grey ghost on every day it had just released.
 *
 * ```kotlin
 * animateColorAsState(if (selected) colours.accent.container else colours.accent.container.invisible())
 * ```
 *
 * Painted, this is identical to [Color.Transparent] — nothing is drawn either
 * way. It only differs *between* two values, which is exactly where it matters.
 * `Color.Transparent` is still the right thing to write for a colour that is
 * never animated.
 */
fun Color.invisible(): Color = copy(alpha = 0f)
