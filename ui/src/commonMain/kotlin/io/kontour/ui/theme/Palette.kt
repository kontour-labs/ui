package io.kontour.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The raw colour values behind the default themes.
 *
 * These are the only literal colours in the design system. Everything else —
 * every component, every layout — reads semantic tokens off [ColorScheme], so
 * that swapping a theme swaps meaning rather than hex codes.
 *
 * The values come from three places:
 *
 *  - **Kontour's web properties.** `home/src/lib/styles/app.css` and
 *    `admin/src/lib/styles/app.css` are the source for the purple family, the
 *    dark surfaces and the four status tints.
 *  - **Uber's structural palette.** Near-black primary actions on white or
 *    near-black grounds, with grey used sparingly and deliberately.
 *  - **WCAG.** Several web values could not survive contact with a contrast
 *    checker and were replaced. The most important: `#BB86FC` on white is only
 *    2.1:1, so it cannot carry text, a fill label or a focus ring in light mode.
 *    It survives here as [Brand] — decoration only — while [PurpleReadable]
 *    does the work that needs contrast.
 *
 * Every pairing these feed into is asserted by `ColorSchemeContrastTest`.
 */
internal object Palette {

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

    // --- Purple -----------------------------------------------------------
    /** `home --accent`. The Kontour purple. Decorative in light mode — see the KDoc above. */
    val Brand = Color(0xFFBB86FC)

    /** `home --severity-minor-text`. The purple that can carry text on white. */
    val PurpleReadable = Color(0xFF6D28D9)
    val PurpleDeep = Color(0xFF4C1D95)
    val PurpleDeeper = Color(0xFF3B0F7A)
    val PurpleStrong = Color(0xFF5B21B6)

    /** `home --nav-active-bg` flattened onto white — kept opaque so it can be contrast-tested. */
    val PurpleTintLight = Color(0xFFF3ECFE)
    val PurpleTintLightHc = Color(0xFFEDE4FD)
    val PurpleTintDark = Color(0xFF2A1F3D)
    val PurpleTintDarkHc = Color(0xFF352751)
    val PurpleLight = Color(0xFFD9BBFD)
    val PurpleLightHc = Color(0xFFD6B3FF)
    val PurpleOnLight = Color(0xFF1A1024)

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

    val VioletLight = Color(0xFFC4B5FD)
    val VioletOnLight = Color(0xFF1E1B4B)
    val VioletPale = Color(0xFFD6C7FF)

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

    val VioletHcOnLight = Color(0xFF14102E)
    val PurpleHcOnLight = Color(0xFF14091F)
    val PurplePaleHc = Color(0xFFEBDCFF)
}
