package io.kontour.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The raw colour values behind the default themes.
 *
 * These are the only literal colours in the design system. Everything else —
 * every component, every layout — reads semantic tokens off [ColorScheme], so
 * that swapping a theme swaps meaning rather than hex codes.
 *
 * **Monochrome, plus one blue.** The default scheme has no product in it: ink
 * and grey for everything structural, blue for the one role that has to say
 * "interactive", and the four conventional status hues. A library that shipped
 * somebody's brand would make every app that used it look like that somebody,
 * and the app that owned the brand would be the only one not fighting it.
 *
 * Kontour's own purple used to live here and does not any more. It is in
 * `anyways`, as `KontourBrandTheme` — which is the shape every product using
 * this library should copy, and is documented in `docs/using/theming.md`.
 *
 * What remains comes from two places:
 *
 *  - **Uber's structural palette.** Near-black primary actions on white or
 *    near-black grounds, with grey used sparingly and deliberately.
 *  - **WCAG.** Nothing here is eyeballed. Every pairing these feed into is
 *    asserted by `ColorSchemeContrastTest`, and several candidate values were
 *    replaced because they could not survive contact with a contrast checker.
 *
 * **Public**, because the scheme factories default from it — `lightColorScheme`
 * lists `Palette.White` and `Palette.Ink` twenty-eight times — and an app that
 * wants to change one colour should not have to start from raw hex to keep the
 * other twenty-seven.
 */
object Palette {

    // --- Neutrals ---------------------------------------------------------
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)

    /** Uber gray50 — the sunken well behind inset content. */
    val Grey50 = Color(0xFFF6F6F6)
    val Grey100 = Color(0xFFEFEFEF)

    /** `home --border-subtle`. Decorative rules only; too light to bound a control. */
    val Grey200 = Color(0xFFE5E5E5)
    val Grey400 = Color(0xFFA3A3A3)

    /** Lightest grey that still clears 3:1 on both white and [Grey50]. */
    val Grey500 = Color(0xFF8A8A8A)

    /** Lightest grey that still clears 4.5:1 on both white and [Grey50]. */
    val Grey600 = Color(0xFF6B6B6B)

    /** Uber gray500, and close to `home --text-muted`. */
    val Grey700 = Color(0xFF545454)
    val Grey800 = Color(0xFF3A3A3A)

    /** `home --background` / `--text-main`. The near-black everything sits on. */
    val Ink = Color(0xFF121212)

    // --- Blue -------------------------------------------------------------
    //
    // The accent, and the one hue in an otherwise monochrome default. Blue
    // because it is the least opinionated colour a library can pick: it says
    // "interactive" in every convention the reader has met, and it is the one
    // an app is most likely to replace without the replacement looking strange.
    //
    // The ramp mirrors what a status colour needs — a solid that carries white
    // text, a deep tone for text on a tint, the tint itself, and light-on-dark
    // versions of all three. Every pairing is asserted by
    // `ColorSchemeContrastTest`; the ratios are not eyeballed.

    /** Carries white text and clears non-text contrast on white. 6.7:1. */
    val BlueReadable = Color(0xFF1D4ED8)

    /** Text on [BlueTintLight]. */
    val BlueDeep = Color(0xFF1E3A8A)

    /** The high-contrast tier's text on a tint. 14.7:1 on white. */
    val BlueDeeper = Color(0xFF172554)

    /** The high-contrast tier's solid. */
    val BlueStrong = Color(0xFF1E3A8A)

    val BlueTintLight = Color(0xFFEFF6FF)
    val BlueTintLightHc = Color(0xFFE6EFFE)
    val BlueTintDark = Color(0xFF1B2739)
    val BlueTintDarkHc = Color(0xFF24314D)

    /** The dark tier's solid — light enough to read against ink. */
    val BlueLight = Color(0xFF93C5FD)
    val BlueLightHc = Color(0xFFB3D3FF)

    /** What sits *on* [BlueLight]: near-black with a blue cast. */
    val BlueOnLight = Color(0xFF0D1B2E)
    val BlueHcOnLight = Color(0xFF0A1220)

    /** Text on [BlueTintDark]. */
    val BluePale = Color(0xFFBFDBFE)
    val BluePaleHc = Color(0xFFD6E6FF)

    // --- Sky, for `info` -----------------------------------------------
    //
    // A separate hue from [BlueReadable], and it has to be. `accent` says
    // "press this" and `info` says "here is something you should know"; drawn in
    // the same blue, an info banner and an accent banner are the same object
    // wearing two names. The other three status tones each have their own hue
    // for exactly this reason, and info was the odd one out — it used to be a
    // straight copy of the purple accent.

    /** 5.9:1 on white, and unmistakably cooler than the accent. */
    val SkySolid = Color(0xFF0369A1)
    val SkyDeep = Color(0xFF0C4A6E)
    val SkyTint = Color(0xFFF0F9FF)
    val SkyBorderLight = Color(0xFFBAE0F5)

    val SkyLight = Color(0xFF7DD3FC)
    val SkyOnLight = Color(0xFF062534)
    val SkyDarkTint = Color(0xFF0E2A3A)
    val SkyPale = Color(0xFFBAE6FD)
    val SkyBorderDark = Color(0xFF1E4A63)

    val SkyHcSolid = Color(0xFF0C4A6E)
    val SkyHcTint = Color(0xFFDFF2FD)
    val SkyHcLight = Color(0xFFA5E8FF)
    val SkyHcOnLight = Color(0xFF04202E)
    val SkyHcDarkTint = Color(0xFF12354A)
    val SkyHcPale = Color(0xFFC8ECFF)

    val BlueBorderLight = Color(0xFFC7DCFD)
    val BlueBorderDark = Color(0xFF2C3E5C)
    val BlueBorderLightHc = Color(0xFFB9CFF8)
    val BlueBorderDarkHc = Color(0xFF3D5480)

    // --- Dark-mode surfaces (from `home html.dark`) -------------------------
    val Slate900 = Color(0xFF1A1820)
    val Slate850 = Color(0xFF221E29)
    val Slate800 = Color(0xFF2A2633)
    val Slate700 = Color(0xFF3C3547)
    val Slate600 = Color(0xFF7C7484)
    val Slate500 = Color(0xFF9A93A2)
    val Slate400 = Color(0xFFA79FB0)
    val Slate300 = Color(0xFF5C5566)
    /**
     * Dark-mode body text.
     *
     * Not [Paper], which is 13.2:1 on the lightest dark surface — nearly three
     * times the 4.5 it has to clear, and bright enough to glare against a
     * near-black page. This sits at 11.8:1: still far above the floor, still
     * clearly separated from [Slate400] and [Slate500] below it, and no longer
     * the brightest thing on the screen.
     */
    val Slate200 = Color(0xFFE8E4EE)

    val Paper = Color(0xFFF4F1F8)

    // --- Status -----------------------------------------------------------
    // Light values are darkened from the web tokens so a white label clears
    // 4.5:1 on the fill; dark values are `home html.dark`'s severity text
    // colours, which already carry their own contrast.
    val GreenSolid = Color(0xFF2E7D32)
    val GreenTint = Color(0xFFE8F5E9)
    val GreenDeep = Color(0xFF1B5E20)
    val GreenLight = Color(0xFF7BE08A)
    val GreenDarkTint = Color(0xFF17301B)
    val GreenOnLight = Color(0xFF0D2010)
    val GreenPale = Color(0xFF9BEBA7)

    val AmberSolid = Color(0xFFB45309)
    val AmberTint = Color(0xFFFEF3E2)
    val AmberDeep = Color(0xFF92400E)
    val AmberLight = Color(0xFFFDBA74)
    val AmberDarkTint = Color(0xFF3A2410)
    val AmberOnLight = Color(0xFF2B1900)
    val AmberPale = Color(0xFFFDD3A8)

    val RedSolid = Color(0xFFB91C1C)
    val RedTint = Color(0xFFFEE9E9)
    val RedDeep = Color(0xFF991B1B)
    val RedLight = Color(0xFFFCA5A5)
    val RedDarkTint = Color(0xFF3A1620)
    val RedOnLight = Color(0xFF2B0715)
    val RedPale = Color(0xFFFDBDBD)


    // --- High-contrast extras ---------------------------------------------
    val GreyHcMuted = Color(0xFF3A3A3A)
    val GreyHcSubtle = Color(0xFF4A4A4A)
    val GreyHcDisabled = Color(0xFF6E6E6E)
    val GreyHcOutline = Color(0xFF767676)
    val GreyHcOutlineSubtle = Color(0xFF949494)
    val GreyHcSunken = Color(0xFFF0F0F0)

    val InkHcSurface = Color(0xFF16131C)
    val InkHcSunken = Color(0xFF0B0910)
    val InkHcRaised = Color(0xFF1F1A28)
    val SlateHcMuted = Color(0xFFD6CFE0)
    val SlateHcSubtle = Color(0xFFBFB6CC)
    val SlateHcDisabled = Color(0xFF8A8296)
    val SlateHcOutline = Color(0xFF9C93AA)
    val SlateHcOutlineStrong = Color(0xFFC6BDD4)
    val SlateHcOutlineSubtle = Color(0xFF6B6377)

    val GreenHcSolid = Color(0xFF1B5E20)
    val GreenHcTint = Color(0xFFDCEFDD)
    val GreenHcDeep = Color(0xFF10380F)
    val GreenHcLight = Color(0xFFA9F0B4)
    val GreenHcDarkTint = Color(0xFF123018)
    val GreenHcOnLight = Color(0xFF04140A)
    val GreenHcPale = Color(0xFFC5F5CC)

    val AmberHcSolid = Color(0xFF8A3D06)
    val AmberHcTint = Color(0xFFFCEBD6)
    val AmberHcDeep = Color(0xFF6B2F04)
    val AmberHcLight = Color(0xFFFFD3A0)
    val AmberHcDarkTint = Color(0xFF40280F)
    val AmberHcOnLight = Color(0xFF1F1200)
    val AmberHcPale = Color(0xFFFFE3C2)

    val RedHcSolid = Color(0xFF8E1414)
    val RedHcTint = Color(0xFFFBDDDD)
    val RedHcDeep = Color(0xFF6B0F0F)
    val RedHcLight = Color(0xFFFFC2C2)
    val RedHcDarkTint = Color(0xFF431A20)
    val RedHcOnLight = Color(0xFF1F0409)
    val RedHcPale = Color(0xFFFFD9D9)

}
