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
 *   under no obligation to pass a contrast checker and [accent] is under every
 *   obligation, so one token could not be both. The GTurbo demo theme is the
 *   worked example: its logo red `#E11F26` is 4.17:1 on its own near-black
 *   ground — fine as a mark, unreadable as text — while its [accent] carries
 *   both a fill label and body copy. [io.kontour.ui.a11y.brandIsSafeForText] is
 *   the question to ask of your own scheme.
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
    /**
     * The ground a moving indicator runs in: a segmented track, a wheel's band.
     *
     * Distinct from [surfaceSunken] because the two want opposite things. A well
     * is a hint that content is inset and should stay quiet — a page of code
     * blocks in a loud well is a page of grey boxes. A track has something
     * sliding along it whose position *is* the control's state, so it has to be
     * far enough under that thing to show it.
     *
     * They were one token until a reader disliked what holding both jobs cost:
     * making a segmented thumb visible turned every code block on the
     * documentation site grey, and the thumb still needed a border on top. This
     * is the token that lets the track go dark on its own.
     */
    val surfaceTrack: Color,
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
    /**
     * The boundary of an interactive control.
     *
     * Clears 3:1 against every ground **and against the fills it has to bound**
     * — `surface`, `surfaceSunken`, `surfaceRaised` and `accent.container`. Both
     * halves are walked by [io.kontour.ui.a11y.contrastFailures]. The second
     * half is there because a tint cannot separate itself: a selected chip is
     * `accent.container` at 1.29:1 against the page, so what says it is selected
     * is the border around it, and that border is this.
     *
     * It is **not** what identifies a segmented control's selected segment, and
     * used to be. That thumb is now a lighter fill on a
     * [surfaceTrack] dark enough to show it — 1.53:1 or so, depending on the
     * scheme — with a shadow under it in light and the label going from
     * `contentMuted` to `content`. Three carriers rather than a line, which is
     * a deliberate step away from the 3:1 a boundary would give; see
     * `SurfaceLadderTest` for the floor those carriers hold and
     * `IndicatorVisibilityTest` for what is given up.
     *
     * [surfaceTrack] is therefore not in the fill half of the walk. Nothing
     * bounds a track.
     */
    val outlineStrong: Color,
    /** The faintest rule the scheme offers, for dense lists. */
    val outlineSubtle: Color,

    // --- Primary action ---
    /**
     * The solid call-to-action fill: near-black on light, near-white on dark.
     *
     * **Structural, not brand.** The product's colour is [accent] (as a tone) and
     * [brand] (as a mark); this is the third role and it is the neutral one. It
     * reaches far past a filled button — the slider's active track, the radio
     * mark, all three progress forms, the selected calendar day — so a scheme
     * that sets it to its brand colour tints every one of those and leaves no
     * component able to tell the two roles apart. Nothing errors when that
     * happens and contrast still passes, which is why
     * `PrimaryIsStructuralTest` asserts it instead.
     */
    val primary: Color,
    /** What is legible on [primary]. */
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
 * The default light scheme: white and near-black structure, blue as accent.
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
    surfaceTrack: Color = Palette.Grey300,
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
    surfaceTrack = surfaceTrack,
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
    surfaceTrack: Color = Palette.Black,
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
    surfaceTrack = surfaceTrack,
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
 * Every default below is the value the ratio demands — at AAA the grounds are
 * pure white, the content is pure black, and the greys are the lightest values
 * that still clear 7:1. **A caller who supplies nothing gets a scheme that
 * clears WCAG AAA, checked on every build.**
 *
 * ### Why it takes twenty-eight parameters and not three
 *
 * It took three — `accent`, `brand`, `focusRing` — and this KDoc argued that
 * parameterising the rest "would offer a caller the freedom to break the only
 * thing the tier exists to guarantee". True, and it also meant a product whose
 * ground is not pure white or pure black had **no high-contrast tier at all**:
 * there was no argument that kept the design's own ground and took the tuned
 * greys, washes and status tones with it. The library's own GTurbo demo theme
 * declined the tier for exactly that reason.
 *
 * Withholding a parameter protects the guarantee by blocking every legitimate
 * use along with the illegitimate ones. What the guarantee actually *is* is a
 * ratio, so check the ratio:
 *
 * ```
 * val mine = highContrastDarkColourScheme(background = Color(0xFF0A0A0B))
 *
 * @Test
 * fun myEnhancedTierIsEnhanced() {
 *     assertEquals(emptyList(), contrastFailures(mine, ContrastLevel.High))
 * }
 * ```
 *
 * [io.kontour.ui.a11y.contrastFailures] is one call and names the token and the
 * number it missed by. This function does not run it for you: an app whose brand
 * lands at 6.9:1 should not crash, and it should certainly not crash only for the
 * users who have the high-contrast setting switched on.
 *
 * ### Two things the defaults do that are not obvious
 *
 * [brand] and [focusRing] default to `accent.solid` rather than to a constant,
 * so supplying an accent moves all three. The standard [lightColourScheme] uses
 * constants that merely happen to agree at the default; the difference only shows
 * when a caller passes something.
 *
 * [code]'s `plain` and `comment` follow [content] and [contentMuted], the way
 * they do in [lightColourScheme] — so a brand that darkens its text gets code
 * that darkens with it. `keyword` and `literal` stay constants: they are hues
 * rather than a ground relationship.
 *
 * The worked example is `GTurbo` in `ui-catalog`, which is compiled, contrast-
 * walked and photographed on every build.
 */
fun highContrastLightColourScheme(
    // Pure white, and it was previously not written down here at all: these four
    // and `onPrimary` fell through to `lightColourScheme`'s own defaults, so they
    // *tracked* the standard scheme. Pinning them is the one thing the widening
    // changes about the no-argument result's provenance — Kotlin has no way to
    // say "whatever the standard factory defaults to" — and it is stated rather
    // than left to be discovered.
    background: Color = Palette.White,
    surface: Color = Palette.White,
    surfaceSunken: Color = Palette.GreyHcSunken,
    surfaceTrack: Color = Palette.Grey300,
    surfaceRaised: Color = Palette.White,
    surfaceInverse: Color = Palette.Black,
    onSurfaceInverse: Color = Palette.White,
    content: Color = Palette.Black,
    contentMuted: Color = Palette.GreyHcMuted,
    contentSubtle: Color = Palette.GreyHcSubtle,
    contentDisabled: Color = Palette.GreyHcDisabled,
    outline: Color = Palette.GreyHcOutline,
    outlineStrong: Color = Palette.GreyHcMuted,
    outlineSubtle: Color = Palette.GreyHcOutlineSubtle,
    primary: Color = Palette.Black,
    onPrimary: Color = Palette.White,
    accent: StatusColours = StatusColours(
        solid = Palette.BlueStrong,
        onSolid = Palette.White,
        container = Palette.BlueTintLightHc,
        onContainer = Palette.BlueDeeper,
        border = Palette.BlueStrong,
    ),
    brand: Color = accent.solid,
    focusRing: Color = accent.solid,
    success: StatusColours = StatusColours(
        solid = Palette.GreenHcSolid,
        onSolid = Palette.White,
        container = Palette.GreenHcTint,
        onContainer = Palette.GreenHcDeep,
        border = Palette.GreenHcSolid,
    ),
    warning: StatusColours = StatusColours(
        solid = Palette.AmberHcSolid,
        onSolid = Palette.White,
        container = Palette.AmberHcTint,
        onContainer = Palette.AmberHcDeep,
        border = Palette.AmberHcSolid,
    ),
    danger: StatusColours = StatusColours(
        solid = Palette.RedHcSolid,
        onSolid = Palette.White,
        container = Palette.RedHcTint,
        onContainer = Palette.RedHcDeep,
        border = Palette.RedHcSolid,
    ),
    info: StatusColours = StatusColours(
        solid = Palette.SkyHcSolid,
        onSolid = Palette.White,
        container = Palette.SkyHcTint,
        // `SkyDeep`, not `SkyHcDeep` — there is no such constant, and this is
        // the one status `onContainer` in this tier borrowed from the standard
        // palette. It reads as a typo beside its four siblings and is not one.
        onContainer = Palette.SkyDeep,
        border = Palette.SkyHcSolid,
    ),
    // The press and hover washes are the four values easiest to miss, because
    // they are not named after anything visible: a 6% wash is invisible at this
    // tier, so a control the user is pressing looks like a control they are not.
    // The scrim goes darker for the same reason — what it is separating from is
    // now higher contrast, so the old alpha separates less.
    //
    // They are also the four that neither the goldens nor `contrastFailures` can
    // see: nothing in a still frame is hovered or pressed, and a wash composites
    // over what is behind it so there is no pairing to take a ratio of.
    // `HighContrastWideningTest` is what covers them.
    scrim: Color = Color(0xB3000000),
    overlayHover: Color = Color(0x1F000000),
    overlayPressed: Color = Color(0x3D000000),
    overlayDragged: Color = Color(0x4D000000),
    // Deeper, because 7:1 on a near-white ground leaves no room for the
    // standard pair — and still 45 and 18 ΔE from each other and from black,
    // which is what stops high contrast collapsing into one colour.
    //
    // `plain` and `comment` track [content] and [contentMuted] the way they do
    // in [lightColourScheme]; `literal` is `GreenOnLight`, a dark-mode entry
    // borrowed here for its depth, not the `GreenHcDeep` its name suggests.
    code: CodeColours = CodeColours(
        plain = content,
        keyword = Palette.BlueDeeper,
        literal = Palette.GreenOnLight,
        comment = contentMuted,
    ),
): ColourScheme = lightColourScheme(
    background = background,
    surface = surface,
    surfaceSunken = surfaceSunken,
    surfaceTrack = surfaceTrack,
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
)

/**
 * The dark scheme at [ContrastLevel.High]: pure black ground, pure white text.
 *
 * Takes the same twenty-eight parameters as [highContrastLightColourScheme] and
 * for the same reasons — read that one. **This is the tier a near-black product
 * could not previously have**: `background` was fixed to pure black and the
 * surfaces to the `InkHc` ladder, so a design whose ground is `#0A0A0B` had to
 * decline the enhanced tier rather than author one.
 *
 * Every default is still the value the ratio demands, and a caller who supplies
 * nothing gets AAA. A caller who supplies a ground owns the ratio, and
 * [io.kontour.ui.a11y.contrastFailures] is the one call that checks it.
 */
fun highContrastDarkColourScheme(
    background: Color = Palette.Black,
    surface: Color = Palette.InkHcSurface,
    surfaceSunken: Color = Palette.InkHcSunken,
    surfaceTrack: Color = Palette.Black,
    surfaceRaised: Color = Palette.InkHcRaised,
    surfaceInverse: Color = Palette.White,
    onSurfaceInverse: Color = Palette.Black,
    content: Color = Palette.White,
    contentMuted: Color = Palette.SlateHcMuted,
    contentSubtle: Color = Palette.SlateHcSubtle,
    contentDisabled: Color = Palette.SlateHcDisabled,
    outline: Color = Palette.SlateHcOutline,
    outlineStrong: Color = Palette.SlateHcOutlineStrong,
    outlineSubtle: Color = Palette.SlateHcOutlineSubtle,
    primary: Color = Palette.White,
    onPrimary: Color = Palette.Black,
    accent: StatusColours = StatusColours(
        solid = Palette.BlueLightHc,
        onSolid = Palette.BlueHcOnLight,
        container = Palette.BlueTintDarkHc,
        onContainer = Palette.BluePaleHc,
        // The accent's border is its own solid here, as it is for all four
        // status tones in both high-contrast tiers — unlike the standard tiers,
        // where borders are separate softer values. `Palette.BlueBorderDarkHc`
        // exists and is read by nothing; it looks like the value that belongs
        // here and is not.
        border = Palette.BlueLightHc,
    ),
    brand: Color = accent.solid,
    focusRing: Color = accent.solid,
    success: StatusColours = StatusColours(
        solid = Palette.GreenHcLight,
        onSolid = Palette.GreenHcOnLight,
        container = Palette.GreenHcDarkTint,
        onContainer = Palette.GreenHcPale,
        border = Palette.GreenHcLight,
    ),
    warning: StatusColours = StatusColours(
        solid = Palette.AmberHcLight,
        onSolid = Palette.AmberHcOnLight,
        container = Palette.AmberHcDarkTint,
        onContainer = Palette.AmberHcPale,
        border = Palette.AmberHcLight,
    ),
    danger: StatusColours = StatusColours(
        solid = Palette.RedHcLight,
        onSolid = Palette.RedHcOnLight,
        container = Palette.RedHcDarkTint,
        onContainer = Palette.RedHcPale,
        border = Palette.RedHcLight,
    ),
    info: StatusColours = StatusColours(
        solid = Palette.SkyHcLight,
        onSolid = Palette.SkyHcOnLight,
        container = Palette.SkyHcDarkTint,
        onContainer = Palette.SkyHcPale,
        border = Palette.SkyHcLight,
    ),
    scrim: Color = Color(0xC2000000),
    overlayHover: Color = Color(0x24FFFFFF),
    overlayPressed: Color = Color(0x47FFFFFF),
    overlayDragged: Color = Color(0x54FFFFFF),
    // `plain` and `comment` track [content] and [contentMuted]. `literal` is
    // `GreenPale`, the *standard* dark tone — `GreenHcPale` exists and is used
    // two fields up for `success.onContainer`, which makes this look like a slip
    // and it is the shipped value.
    code: CodeColours = CodeColours(
        plain = content,
        keyword = Palette.BlueLightHc,
        literal = Palette.GreenPale,
        comment = contentMuted,
    ),
): ColourScheme = darkColourScheme(
    background = background,
    surface = surface,
    surfaceSunken = surfaceSunken,
    surfaceTrack = surfaceTrack,
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
