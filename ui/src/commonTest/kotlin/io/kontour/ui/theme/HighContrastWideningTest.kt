package io.kontour.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * The two high-contrast factories widened from three parameters to twenty-seven,
 * proved against the bodies they replaced.
 *
 * A widening like this is a transcription: some forty values that were hardcoded
 * in a function body become defaults on its signature, and every one of them is
 * a chance to write the wrong `Palette` symbol. Nothing about the change is
 * interesting; everything about it is fragile.
 *
 * ### Why the expected side is a copy rather than a hand-written pin
 *
 * The obvious check —
 * `assertEquals(kontourColourScheme(dark, High), highContrastDarkColourScheme())`
 * — is `assertEquals(f(), f())`, because the dispatch *calls* the factory. The
 * next idea, pinning all twenty-eight fields as hex literals, is better and
 * still transcribes the same values a second time by hand, which is the exact
 * error it exists to catch.
 *
 * So [legacyHighContrastLight] and [legacyHighContrastDark] are the pre-widening
 * bodies **pasted verbatim** out of `git show`, with nothing changed but the
 * function name and the visibility. They cannot be mis-transcribed independently
 * of the thing they check, and a reviewer verifies them with a diff instead of by
 * reading colours.
 *
 * ### The goldens are the weak check here, not the strong one
 *
 * It is tempting to lean on `theme-light-high-contrast.png` and its dark sibling
 * and call the widening proved. They are blinder than they look:
 *
 * - `ScreenshotHarness` counts a pixel as differing only when a channel moves by
 *   more than eight, so **a token that shifts by ≤8 per channel is invisible by
 *   construction**, whatever area it covers.
 * - `ThemeShowcase` never draws eleven of the twenty-eight values at all — the
 *   `scrim`, the three overlay washes, the four `code` colours, `contentDisabled`,
 *   `outlineSubtle` and the five `border` fields. Nothing on that page is
 *   hovered, pressed, dragged, disabled, focused or modal.
 *
 * The four that the high-contrast light body's own comment calls "the values
 * easiest to miss" — the scrim and the three washes — are among the eleven, and
 * `contrastFailures` is structurally blind to all four as well, because a wash
 * composites over whatever is behind it and has no pairing to take a ratio of.
 * This file is what covers them.
 */
class HighContrastWideningTest {

    @Test
    fun theWidenedLightFactoryIsTheBodyItReplaced() {
        assertSameScheme("light", legacyHighContrastLight(), highContrastLightColourScheme())
    }

    @Test
    fun theWidenedDarkFactoryIsTheBodyItReplaced() {
        assertSameScheme("dark", legacyHighContrastDark(), highContrastDarkColourScheme())
    }

    /**
     * And with an argument, which is the only way to see the derived defaults.
     *
     * `brand` and `focusRing` default to `accent.solid` in this pair rather than
     * to a constant — unlike the standard pair, where both are `Palette` symbols
     * that merely *happen* to equal the default accent's solid. So rewriting the
     * high-contrast factories "to match the standard pair" silently changes
     * behaviour for anyone who passes an accent: they used to get their own tone
     * as the brand and the focus ring, and would start getting the library's blue.
     *
     * A no-argument comparison cannot see that, because at the default the two
     * formulations agree. This is the arm that can, and it is the reason the
     * widening is not the mechanical change it looks like.
     */
    @Test
    fun theDerivedDefaultsStillFollowTheAccent() {
        assertSameScheme(
            "light, accent passed",
            legacyHighContrastLight(accent = Probe), highContrastLightColourScheme(accent = Probe),
        )
        assertSameScheme(
            "dark, accent passed",
            legacyHighContrastDark(accent = Probe), highContrastDarkColourScheme(accent = Probe),
        )

        for ((tier, scheme) in listOf(
            "light" to highContrastLightColourScheme(accent = Probe),
            "dark" to highContrastDarkColourScheme(accent = Probe),
        )) {
            assertEquals(
                Probe.solid, scheme.brand,
                "$tier: brand stopped following accent.solid. It must not become a " +
                    "constant — brand is the one token the contrast walk exempts, so " +
                    "a caller who set an accent and silently got the library's blue " +
                    "has nothing that would tell them.",
            )
            assertEquals(
                Probe.solid, scheme.focusRing,
                "$tier: focusRing stopped following accent.solid",
            )
        }
    }


    /**
     * Every widened parameter actually reaches the scheme.
     *
     * The equalities above all pass if a parameter is accepted and then dropped
     * on the floor: `background: Color = Palette.White` that never reaches
     * `lightColourScheme(background = …)` produces exactly the old scheme at the
     * default and ignores its caller forever. The copy-paste slip that forwards
     * `surface = surface` twice and never assigns `surfaceRaised` has the same
     * shape, and neither a golden nor a contrast walk can see either.
     *
     * So each of the fifty-four parameters is set on its own to a value nothing
     * else in the scheme uses, and the matching field has to come back holding
     * it. Written out one line per token on purpose — there is no way to name a
     * Kotlin argument dynamically, and a loop over reflection would be a second
     * thing to get wrong.
     */
    @Test
    fun everyWidenedParameterReachesTheScheme() {
        val forwarded: List<Pair<String, Boolean>> = listOf(
            // --- light ---
            "light.background" to (highContrastLightColourScheme(background = Ink).background == Ink),
            "light.surface" to (highContrastLightColourScheme(surface = Ink).surface == Ink),
            "light.surfaceSunken" to (highContrastLightColourScheme(surfaceSunken = Ink).surfaceSunken == Ink),
            "light.surfaceRaised" to (highContrastLightColourScheme(surfaceRaised = Ink).surfaceRaised == Ink),
            "light.surfaceInverse" to (highContrastLightColourScheme(surfaceInverse = Ink).surfaceInverse == Ink),
            "light.onSurfaceInverse" to (highContrastLightColourScheme(onSurfaceInverse = Ink).onSurfaceInverse == Ink),
            "light.content" to (highContrastLightColourScheme(content = Ink).content == Ink),
            "light.contentMuted" to (highContrastLightColourScheme(contentMuted = Ink).contentMuted == Ink),
            "light.contentSubtle" to (highContrastLightColourScheme(contentSubtle = Ink).contentSubtle == Ink),
            "light.contentDisabled" to (highContrastLightColourScheme(contentDisabled = Ink).contentDisabled == Ink),
            "light.outline" to (highContrastLightColourScheme(outline = Ink).outline == Ink),
            "light.outlineStrong" to (highContrastLightColourScheme(outlineStrong = Ink).outlineStrong == Ink),
            "light.outlineSubtle" to (highContrastLightColourScheme(outlineSubtle = Ink).outlineSubtle == Ink),
            "light.primary" to (highContrastLightColourScheme(primary = Ink).primary == Ink),
            "light.onPrimary" to (highContrastLightColourScheme(onPrimary = Ink).onPrimary == Ink),
            "light.brand" to (highContrastLightColourScheme(brand = Ink).brand == Ink),
            "light.focusRing" to (highContrastLightColourScheme(focusRing = Ink).focusRing == Ink),
            "light.scrim" to (highContrastLightColourScheme(scrim = Ink).scrim == Ink),
            "light.overlayHover" to (highContrastLightColourScheme(overlayHover = Ink).overlayHover == Ink),
            "light.overlayPressed" to (highContrastLightColourScheme(overlayPressed = Ink).overlayPressed == Ink),
            "light.overlayDragged" to (highContrastLightColourScheme(overlayDragged = Ink).overlayDragged == Ink),
            "light.accent" to (highContrastLightColourScheme(accent = Probe).accent == Probe),
            "light.success" to (highContrastLightColourScheme(success = Probe).success == Probe),
            "light.warning" to (highContrastLightColourScheme(warning = Probe).warning == Probe),
            "light.danger" to (highContrastLightColourScheme(danger = Probe).danger == Probe),
            "light.info" to (highContrastLightColourScheme(info = Probe).info == Probe),
            "light.code" to (highContrastLightColourScheme(code = Listing).code == Listing),
            // --- dark ---
            "dark.background" to (highContrastDarkColourScheme(background = Ink).background == Ink),
            "dark.surface" to (highContrastDarkColourScheme(surface = Ink).surface == Ink),
            "dark.surfaceSunken" to (highContrastDarkColourScheme(surfaceSunken = Ink).surfaceSunken == Ink),
            "dark.surfaceRaised" to (highContrastDarkColourScheme(surfaceRaised = Ink).surfaceRaised == Ink),
            "dark.surfaceInverse" to (highContrastDarkColourScheme(surfaceInverse = Ink).surfaceInverse == Ink),
            "dark.onSurfaceInverse" to (highContrastDarkColourScheme(onSurfaceInverse = Ink).onSurfaceInverse == Ink),
            "dark.content" to (highContrastDarkColourScheme(content = Ink).content == Ink),
            "dark.contentMuted" to (highContrastDarkColourScheme(contentMuted = Ink).contentMuted == Ink),
            "dark.contentSubtle" to (highContrastDarkColourScheme(contentSubtle = Ink).contentSubtle == Ink),
            "dark.contentDisabled" to (highContrastDarkColourScheme(contentDisabled = Ink).contentDisabled == Ink),
            "dark.outline" to (highContrastDarkColourScheme(outline = Ink).outline == Ink),
            "dark.outlineStrong" to (highContrastDarkColourScheme(outlineStrong = Ink).outlineStrong == Ink),
            "dark.outlineSubtle" to (highContrastDarkColourScheme(outlineSubtle = Ink).outlineSubtle == Ink),
            "dark.primary" to (highContrastDarkColourScheme(primary = Ink).primary == Ink),
            "dark.onPrimary" to (highContrastDarkColourScheme(onPrimary = Ink).onPrimary == Ink),
            "dark.brand" to (highContrastDarkColourScheme(brand = Ink).brand == Ink),
            "dark.focusRing" to (highContrastDarkColourScheme(focusRing = Ink).focusRing == Ink),
            "dark.scrim" to (highContrastDarkColourScheme(scrim = Ink).scrim == Ink),
            "dark.overlayHover" to (highContrastDarkColourScheme(overlayHover = Ink).overlayHover == Ink),
            "dark.overlayPressed" to (highContrastDarkColourScheme(overlayPressed = Ink).overlayPressed == Ink),
            "dark.overlayDragged" to (highContrastDarkColourScheme(overlayDragged = Ink).overlayDragged == Ink),
            "dark.accent" to (highContrastDarkColourScheme(accent = Probe).accent == Probe),
            "dark.success" to (highContrastDarkColourScheme(success = Probe).success == Probe),
            "dark.warning" to (highContrastDarkColourScheme(warning = Probe).warning == Probe),
            "dark.danger" to (highContrastDarkColourScheme(danger = Probe).danger == Probe),
            "dark.info" to (highContrastDarkColourScheme(info = Probe).info == Probe),
            "dark.code" to (highContrastDarkColourScheme(code = Listing).code == Listing),
        )

        val dropped = forwarded.filterNot { it.second }.map { it.first }
        if (dropped.isNotEmpty()) {
            fail(
                "${dropped.size} of ${forwarded.size} widened parameters are accepted " +
                    "and never forwarded, so a caller who sets one is ignored in " +
                    "silence: " + dropped.joinToString(", "),
            )
        }
    }

    /**
     * Compares two schemes field by field and names the ones that moved.
     *
     * A bare `assertEquals` on a `ColourScheme` prints both twenty-eight-field
     * values in full and leaves the reader to find the difference in six hundred
     * characters of `Color(0.9411765, …)`. The whole point of this file is that
     * exactly one default will be wrong and it needs saying which, so the
     * comparison is spelled out: fifty entries, one per colour the scheme holds.
     *
     * It is sensitive to a single unit in a single channel — the canary for this
     * file moved `surfaceSunken` from `#F0F0F0` to `#F0F0F1` and both arms went
     * red. The screenshot goldens cannot do that: `ScreenshotHarness` ignores a
     * channel that moves by eight or less.
     */
    private fun assertSameScheme(label: String, a: ColourScheme, b: ColourScheme) {
        val moved = listOfNotNull(
            differ("background", a.background, b.background),
            differ("surface", a.surface, b.surface),
            differ("surfaceSunken", a.surfaceSunken, b.surfaceSunken),
            differ("surfaceRaised", a.surfaceRaised, b.surfaceRaised),
            differ("surfaceInverse", a.surfaceInverse, b.surfaceInverse),
            differ("onSurfaceInverse", a.onSurfaceInverse, b.onSurfaceInverse),
            differ("content", a.content, b.content),
            differ("contentMuted", a.contentMuted, b.contentMuted),
            differ("contentSubtle", a.contentSubtle, b.contentSubtle),
            differ("contentDisabled", a.contentDisabled, b.contentDisabled),
            differ("outline", a.outline, b.outline),
            differ("outlineStrong", a.outlineStrong, b.outlineStrong),
            differ("outlineSubtle", a.outlineSubtle, b.outlineSubtle),
            differ("primary", a.primary, b.primary),
            differ("onPrimary", a.onPrimary, b.onPrimary),
            differ("brand", a.brand, b.brand),
            differ("focusRing", a.focusRing, b.focusRing),
            differ("scrim", a.scrim, b.scrim),
            differ("overlayHover", a.overlayHover, b.overlayHover),
            differ("overlayPressed", a.overlayPressed, b.overlayPressed),
            differ("overlayDragged", a.overlayDragged, b.overlayDragged),
            differ("accent.solid", a.accent.solid, b.accent.solid),
            differ("accent.onSolid", a.accent.onSolid, b.accent.onSolid),
            differ("accent.container", a.accent.container, b.accent.container),
            differ("accent.onContainer", a.accent.onContainer, b.accent.onContainer),
            differ("accent.border", a.accent.border, b.accent.border),
            differ("success.solid", a.success.solid, b.success.solid),
            differ("success.onSolid", a.success.onSolid, b.success.onSolid),
            differ("success.container", a.success.container, b.success.container),
            differ("success.onContainer", a.success.onContainer, b.success.onContainer),
            differ("success.border", a.success.border, b.success.border),
            differ("warning.solid", a.warning.solid, b.warning.solid),
            differ("warning.onSolid", a.warning.onSolid, b.warning.onSolid),
            differ("warning.container", a.warning.container, b.warning.container),
            differ("warning.onContainer", a.warning.onContainer, b.warning.onContainer),
            differ("warning.border", a.warning.border, b.warning.border),
            differ("danger.solid", a.danger.solid, b.danger.solid),
            differ("danger.onSolid", a.danger.onSolid, b.danger.onSolid),
            differ("danger.container", a.danger.container, b.danger.container),
            differ("danger.onContainer", a.danger.onContainer, b.danger.onContainer),
            differ("danger.border", a.danger.border, b.danger.border),
            differ("info.solid", a.info.solid, b.info.solid),
            differ("info.onSolid", a.info.onSolid, b.info.onSolid),
            differ("info.container", a.info.container, b.info.container),
            differ("info.onContainer", a.info.onContainer, b.info.onContainer),
            differ("info.border", a.info.border, b.info.border),
            differ("code.plain", a.code.plain, b.code.plain),
            differ("code.keyword", a.code.keyword, b.code.keyword),
            differ("code.literal", a.code.literal, b.code.literal),
            differ("code.comment", a.code.comment, b.code.comment),
            if (a.isDark == b.isDark) null else "isDark: ${a.isDark} became ${b.isDark}",
        )
        if (moved.isNotEmpty()) {
            fail(
                "$label: ${moved.size} value(s) differ from the pre-widening body:\n" +
                    moved.joinToString("\n") { "  · $it" },
            )
        }
    }

    /** `"outlineStrong: #3A3A3A became #4A4A4A"`, or null when they agree. */
    private fun differ(name: String, a: Color, b: Color): String? =
        if (a == b) null else "$name: ${hex(a)} became ${hex(b)}"

    private fun hex(c: Color): String {
        val argb = (c.value shr 32).toLong() and 0xFFFFFFFFL
        return "#" + argb.toString(16).uppercase().padStart(8, '0')
    }

    private companion object {
        /** A colour nothing else in either scheme uses. */
        val Ink = Color(0xFF9911EE)

        /** Five more of them, for the tone-shaped parameters. */
        val Probe = StatusColours(
            solid = Color(0xFF123456),
            onSolid = Color(0xFF654321),
            container = Color(0xFF0F1E2D),
            onContainer = Color(0xFFABCDEF),
            border = Color(0xFF77AA33),
        )

        /** And four, for `code`. */
        val Listing = CodeColours(
            plain = Color(0xFF101112),
            keyword = Color(0xFF131415),
            literal = Color(0xFF161718),
            comment = Color(0xFF191A1B),
        )
    }
}

private fun legacyHighContrastLight(
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

private fun legacyHighContrastDark(
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
