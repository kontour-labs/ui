package io.kontour.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The raw colour values behind the default themes.
 *
 * These are the only literal colours in the design system. Everything else —
 * every component, every layout — reads semantic tokens off [ColourScheme], so
 * that swapping a theme swaps meaning rather than hex codes.
 *
 * **Monochrome, plus Kontour Labs' violet.** Ink and grey for everything
 * structural, the violet for the one role that has to say "interactive", and
 * the four conventional status hues. The violet is `#BB86FC` — [VioletLight],
 * the dark scheme's accent and every scheme's `brand` — and a deeper tone of
 * the same hue, [VioletReadable], wherever it has to carry text on white,
 * which `#BB86FC` cannot: it is 2.65:1 there.
 *
 * It is one hue, 303° in OKLCH, and every value in the ramp below sits on it.
 * An app that wants its own colour replaces `accent`, `brand` and `focusRing`
 * and nothing else; see `ui-docs/content/theming.md`.
 *
 * What remains comes from two places:
 *
 *  - **Uber's structural palette.** Near-black primary actions on white or
 *    near-black grounds, with grey used sparingly and deliberately.
 *  - **WCAG.** Nothing here is eyeballed. Every pairing these feed into is
 *    asserted by `ColourSchemeContrastTest`, and several candidate values were
 *    replaced because they could not survive contact with a contrast checker.
 *
 * **Public**, because the scheme factories default from it — `lightColourScheme`
 * lists `Palette.White` and `Palette.Ink` twenty-eight times — and an app that
 * wants to change one colour should not have to start from raw hex to keep the
 * other twenty-seven.
 */
object Palette {

    // --- Neutrals ---------------------------------------------------------
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)

    /**
     * Uber gray50 — the sunken well behind inset content.
     *
     * 1.08:1 against white, which is close to nothing, and that is the point:
     * a well is a hint that content is inset, not a boundary. Round 31 pushed
     * this to `#E2E2E2` so that a segmented thumb would separate from its own
     * track, and every code block, table and filled card on the documentation
     * site went grey with it — one token doing five jobs, moved for the sake of
     * the fifth. [Grey300] is that fifth job now, so this one is free to be
     * quiet again.
     */
    val Grey50 = Color(0xFFF6F6F6)

    val Grey100 = Color(0xFFEFEFEF)

    /** `home --border-subtle`. Decorative rules only; too light to bound a control. */
    val Grey200 = Color(0xFFE5E5E5)

    /**
     * The ground a moving indicator runs in — a segmented track, a wheel's band.
     *
     * **1.54:1 under white**, which is the whole reason this token exists apart
     * from [Grey50]. A track is not a well: something slides along it and has to
     * be seen doing so, and the thumb is plain `surface`, so the separation can
     * only come from how far down the track sits. At 1.08 it came from a border
     * instead, and the border is what a reader disliked.
     *
     * Why it stops here, measured rather than picked: the labels of the
     * *unselected* segments sit on this ground, and it is the **high-contrast**
     * tier's `contentMuted` that binds — `GreyHcMuted` reads 7.37:1 here against
     * the 7.0 that tier asks of body text, and fails at `#C8C8C8`. The standard
     * tier's `contentMuted` runs out at almost exactly the same place (4.91
     * against 4.5), so both tiers agree on where the floor is. This sits a
     * couple of steps above it so that a later nudge to either token does not
     * silently break a control.
     *
     * [Grey500] reads 2.38:1 here and that is deliberate rather than a failure:
     * its 3:1-against-every-fill contract exists so a fill that cannot separate
     * itself can be *bounded*, and nothing bounds a track any more. See the
     * exemption in `SchemeContrast`.
     */
    val Grey300 = Color(0xFFD0D0D0)

    val Grey400 = Color(0xFFA3A3A3)

    /**
     * Lightest grey that still clears 3:1 on every fill it has to bound.
     *
     * Not the `#8A8A8A` it was: the binding fill is the accent tint,
     * [VioletTintLight], not the well. A selected chip is that tint, this is its
     * border, and it reads 3.23:1 against it. The well going back to [Grey50]
     * does not release this one.
     */
    val Grey500 = Color(0xFF818181)

    /** Lightest grey that still clears 4.5:1 on white, [Grey50] and [VioletTintLight]. */
    val Grey600 = Color(0xFF646464)

    /** Uber gray500, and close to `home --text-muted`. */
    val Grey700 = Color(0xFF545454)
    val Grey800 = Color(0xFF3A3A3A)

    /** `home --background` / `--text-main`. The near-black everything sits on. */
    val Ink = Color(0xFF121212)

    // --- Violet ------------------------------------------------------------
    //
    // The accent, and the one hue in an otherwise monochrome default: Kontour
    // Labs' own, `#BB86FC`, at 303° in OKLCH. Every value here is that hue at
    // another lightness, so a tint, a solid and the text on either read as one
    // colour rather than a family of near-misses.
    //
    // The ramp mirrors what a status colour needs — a solid that carries white
    // text, a deep tone for text on a tint, the tint itself, and light-on-dark
    // versions of all three. Every pairing is asserted by
    // `ColourSchemeContrastTest`; the ratios are not eyeballed.

    /**
     * Carries white text and clears non-text contrast on white. 6.67:1.
     *
     * The light scheme's accent. [VioletLight] itself cannot be: 2.65:1 on white
     * fails both text and the 3:1 a control boundary needs.
     */
    val VioletReadable = Color(0xFF7C37BE)

    /** Text on [VioletTintLight], 8.9:1, and the light scheme's code keyword. */
    val VioletDeep = Color(0xFF542482)

    /** The high-contrast tier's text on a tint. 11.7:1 on its tint. */
    val VioletDeeper = Color(0xFF3D0D65)

    /** The high-contrast tier's solid. 9.3:1 on white. */
    val VioletStrong = Color(0xFF62259B)

    /**
     * The accent container.
     *
     * Bounded on both sides: 1.21:1 against white, over the 1.15 a selected
     * fill needs to separate from the page, and [Grey500] still clears 3.23:1
     * against it, because a selected chip is this tint and that is its border.
     */
    val VioletTintLight = Color(0xFFF1E5FF)
    val VioletTintLightHc = Color(0xFFEEE1FF)

    /**
     * The dark accent container.
     *
     * Dark has a narrow window here: at least 1.15:1 against `surface`, and dark
     * enough that `outlineStrong` keeps 3:1 against it. This is 1.17 and 3.11.
     */
    val VioletTintDark = Color(0xFF332742)
    val VioletTintDarkHc = Color(0xFF392B4C)

    /**
     * Kontour Labs' violet, `#BB86FC`.
     *
     * The dark scheme's accent — 7.07:1 on ink, and 4.16:1 on the lightest
     * surface a focus ring meets — and `brand` in every scheme.
     */
    val VioletLight = Color(0xFFBB86FC)
    val VioletLightHc = Color(0xFFD4B1FF)

    /** What sits *on* [VioletLight]: near-black with a violet cast. 6.93:1. */
    val VioletOnLight = Color(0xFF1C0D2C)
    val VioletHcOnLight = Color(0xFF12071E)

    /** Text on [VioletTintDark]. */
    val VioletPale = Color(0xFFE2CBFF)
    val VioletPaleHc = Color(0xFFECDCFF)

    // --- Sky, for `info` -----------------------------------------------
    //
    // A separate hue from [VioletReadable], and it has to be. `accent` says
    // "press this" and `info` says "here is something you should know"; drawn in
    // the same colour, an info banner and an accent banner are the same object
    // wearing two names. The other three status tones each have their own hue
    // for exactly this reason, and info was once the odd one out — a straight
    // copy of an earlier purple accent.

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

    val VioletBorderLight = Color(0xFFDAC7F8)
    val VioletBorderDark = Color(0xFF4A3961)
    // There were `…Hc` siblings here and nothing read them. Every high-contrast
    // tone — the accent and all four status tones, in both tiers — uses its own
    // `solid` as its `border`, which is a real difference from the standard
    // tiers and looks like an oversight beside two unused constants named for
    // exactly that slot. Widening the high-contrast factories made "wire them
    // up" a plausible tidy-up that would have changed two colours, so they are
    // gone: softer high-contrast borders would be a decision to take on purpose.

    // --- Dark-mode surfaces (from `home html.dark`) -------------------------
    //
    // Dark has less room than light does, and the ceiling is [Slate600]:
    // `outlineStrong` has to clear 3:1 on the *lightest* surface, so the top of
    // this ramp cannot rise. Round 31 read that as "dark cannot separate a thumb
    // from its track" and pushed the well all the way to [Black] trying to.
    //
    // It had the move backwards. A track is not a well, and the thumb is not
    // stuck at [Slate850]: dark puts the track at [Black] and lifts the *thumb*
    // to [Slate800], which separates at 1.53:1 — better than light manages —
    // off a ramp that was declared exhausted. The well goes back to [Slate900],
    // one quiet step under the page, which is all a well was ever for.
    val Slate900 = Color(0xFF1A1820)
    val Slate850 = Color(0xFF221E29)

    /**
     * `surfaceRaised`, and the selected segment in dark.
     *
     * 1.19:1 above [Slate850] where it used to be 1.11, and **1.53:1 above
     * [Black]**, which is the number that matters: this is what a segmented
     * thumb is drawn in once the track drops to black under it.
     */
    val Slate800 = Color(0xFF302B3B)

    /**
     * The selected segment's fill in dark, and nothing else.
     *
     * A job rather than a step on the ramp, which is why it is named like
     * [InkHcRaised] and not like [Slate700]. The ground under a segmented thumb
     * used to be [Black] so that [Slate800] could sit 1.53:1 above it; the
     * ground is [Slate900] now — the same well every text field uses — and
     * [Slate800] is only 1.28:1 on that. Moving the *thumb* up instead reaches
     * **1.59:1**, which is better than the arrangement it replaces.
     *
     * Deliberately not [Slate700], which is 1.50:1 on the nose and is already
     * `outline` in dark. A fill and a line wanting the same value is how two
     * roles end up impossible to tell apart.
     */
    val SlateIndicator = Color(0xFF403852)

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

    /** The enhanced tier's well. A shade tighter to the page than [Grey50]. */
    val GreyHcSunken = Color(0xFFF0F0F0)

    val InkHcSurface = Color(0xFF201B2A)

    /** The enhanced tier's raised ground, and its selected segment: 1.52:1 on [Black]. */
    val InkHcRaised = Color(0xFF302942)

    /** The enhanced tier's well in dark, a step under [Black]'s page rather than on it. */
    val InkHcSunken = Color(0xFF0B0910)

    /**
     * The enhanced tier's selected segment: **1.94:1** on [InkHcSunken].
     *
     * The tier's counterpart to [SlateIndicator], and further up than it for the
     * reason the tier exists — [InkHcRaised] reads 1.43:1 on this tier's well,
     * which is under the floor, where it used to read 1.52:1 on [Black].
     */
    val InkHcIndicator = Color(0xFF453D58)
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
