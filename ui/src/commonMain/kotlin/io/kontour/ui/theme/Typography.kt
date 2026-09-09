package io.kontour.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.kontour.ui.generated.resources.Res
import io.kontour.ui.generated.resources.jetbrains_mono_bold
import io.kontour.ui.generated.resources.jetbrains_mono_regular
import io.kontour.ui.generated.resources.outfit_bold
import io.kontour.ui.generated.resources.outfit_extrabold
import io.kontour.ui.generated.resources.outfit_medium
import io.kontour.ui.generated.resources.outfit_regular
import io.kontour.ui.generated.resources.outfit_semibold
import org.jetbrains.compose.resources.Font

/**
 * Outfit — the family both Kontour web properties already use.
 *
 * Shipped as five static instances cut from the upstream variable font rather
 * than as the variable font itself, so every target renders identical weights
 * without depending on per-platform variable-axis support. Licensed under the
 * SIL Open Font License; see `ui/licenses/Outfit-OFL.txt`.
 *
 * **Public**, so overriding the type scale does not lock you out of the bundled
 * font: `kontourTypography(outfitFontFamily()).copy(displayLarge = …)` keeps
 * Outfit and changes one style, where building a `Typography` from scratch
 * would mean shipping a font of your own or falling back to the platform's.
 */
@Composable
fun outfitFontFamily(): FontFamily {
    val regular = Font(Res.font.outfit_regular, FontWeight.Normal)
    val medium = Font(Res.font.outfit_medium, FontWeight.Medium)
    val semiBold = Font(Res.font.outfit_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.outfit_bold, FontWeight.Bold)
    val extraBold = Font(Res.font.outfit_extrabold, FontWeight.ExtraBold)
    return remember(regular, medium, semiBold, bold, extraBold) {
        FontFamily(regular, medium, semiBold, bold, extraBold)
    }
}

/**
 * JetBrains Mono — the face for code, keyboard keys and figures.
 *
 * **This is not a feature for one brand.** The library already hardcoded
 * `FontFamily.Monospace` in [io.kontour.ui.components.display.Kbd], and the
 * documentation site did it six more times for every code block and parameter
 * table. `FontFamily.Monospace` is whatever the platform happens to have — Menlo,
 * Consolas, Droid Sans Mono, whatever a browser was configured with — which is
 * the same inconsistency the five bundled Outfit cuts exist to avoid. A module
 * that refuses hardcoded *colours* on principle should not be shipping a
 * hardcoded appeal to the platform's taste in typefaces.
 *
 * Two weights, Regular and Bold. [io.kontour.ui.components.display.Kbd] draws at
 * `labelSmall`, which is SemiBold, and a single weight would leave that to
 * synthetic bolding.
 *
 * ### Subset, and what is in it
 *
 * Cut from the upstream 1.0.6 release with `fontTools.subset` to the Google
 * Fonts latin range plus ⌘ ⌥ ⇧ ⌃ ← →, at 76,704 raw bytes for the pair against
 * 411,448 for the full font. The extra six codepoints are the reason this is not
 * the ready-made `@fontsource` latin subset, which was measured first and
 * carries **none** of the four modifier symbols — bundling it would have made
 * `Kbd`'s documented fallback spread worse rather than better, which is the one
 * thing this was supposed to fix.
 *
 * Nine `KbdDefaults` symbols still fall back, because upstream JetBrains Mono
 * does not draw them either: ⏎ ⌫ ⌦ ⎋ ⇥ ⇪ ⇞ ⇟ ␣. Measured, not assumed —
 * `KbdIcons` remains the answer for the four it covers, and `Kbd`'s own KDoc
 * says why.
 *
 * Ligatures are **off**: the `calt` feature is dropped in the subset. A
 * documentation site that silently redraws `!=` as `≠` is showing the reader
 * something other than the code they are meant to copy. An app that wants them
 * can bundle the ligature cut itself.
 *
 * Licensed under the SIL Open Font License; see
 * `ui/licenses/JetBrainsMono-OFL.txt`.
 */
@Composable
fun jetBrainsMonoFontFamily(): FontFamily {
    val regular = Font(Res.font.jetbrains_mono_regular, FontWeight.Normal)
    val bold = Font(Res.font.jetbrains_mono_bold, FontWeight.Bold)
    return remember(regular, bold) { FontFamily(regular, bold) }
}

/**
 * The type scale.
 *
 * Four families of role, each in three sizes, mirroring the rhythm of the
 * marketing site:
 *
 * | Role | For | Weight |
 * |---|---|---|
 * | `display` | Hero moments. One per screen at most | 800, tight tracking |
 * | `headline` | Section and screen titles | 700 |
 * | `title` | Card headers, list headlines, dialog titles | 600 |
 * | `body` | Everything the user actually reads | 400, 1.6 line height |
 * | `label` | Buttons, chips, tabs, form labels | 600 |
 *
 * Plus two that are not rungs on that ladder:
 *
 * | Role | For |
 * |---|---|
 * | [eyebrow] | The uppercase letterspaced label above a section heading |
 * | [mono] | Code, keyboard keys and figures — [bodyMedium]'s metrics in a monospaced face |
 *
 * [eyebrow] was called `monoLabel` and is not monospaced: it is Outfit at
 * 13sp/700/+0.14em, which is `.mono-label` in `home/src/lib/styles/app.css`, a
 * name the web stylesheet could carry because nothing there had a real
 * monospaced token to be confused with. Here it did — six call sites reached
 * past it for `FontFamily.Monospace` because it plainly was not the thing they
 * wanted — so it is named for its shape and [mono] has its old name.
 *
 * Sizes are in `sp`, so they scale with the user's OS text-size preference.
 * [io.kontour.ui.theme.Theme.typography] resolves the family for you — a
 * component should read a style from here rather than building a [TextStyle].
 */
@Immutable
data class Typography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
    val eyebrow: TextStyle,
    /**
     * Figures that have to line up, and code that has to be read as code.
     *
     * Tabular by construction in a monospaced face, and by `tnum` in a
     * proportional one — Outfit ships the feature, so the default scale's
     * figures column-align even though nothing about it is monospaced. That
     * matters wherever a number is redrawn in place: a frame-time readout in
     * the library's own gallery jittered horizontally on every update, under
     * `monoLabel`, whose name promised exactly the fix it was not providing.
     *
     * A caller who wants a *different* size in this face takes the family and
     * leaves the metrics: `bodySmall.copy(fontFamily = Theme.typography.mono.fontFamily)`.
     * That is what the documentation site's code blocks do, and it is why this
     * is one style rather than a parallel scale of nine.
     */
    val mono: TextStyle,
)

/**
 * Trims the half-leading above the first line and below the last, so a text
 * block's visual bounds match its layout bounds. Without this, generous line
 * heights leave phantom padding that makes vertical centring look wrong.
 */
private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun scaleStyle(
    family: FontFamily,
    size: Int,
    lineHeight: Float,
    weight: FontWeight,
    letterSpacing: Float = 0f,
    features: String? = null,
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.em,
    lineHeightStyle = TrimmedLineHeight,
    fontFeatureSettings = features,
)

/**
 * The default type scale, built once.
 *
 * [kontourTypography] resolves nineteen [androidx.compose.ui.text.TextStyle]s
 * from a family, and it was the one default in [KontourTheme]'s parameter list
 * not wrapped in a `remember` — so all nineteen were rebuilt on every
 * recomposition of the theme, to feed a **static** composition local whose value
 * changing invalidates the whole application. [outfitFontFamily] already
 * remembers its side, so the key here is stable and this settles after the fonts
 * land.
 */
@Composable
internal fun rememberDefaultTypography(): Typography {
    val family = outfitFontFamily()
    val mono = jetBrainsMonoFontFamily()
    return remember(family, mono) { kontourTypography(family, mono) }
}

/**
 * The default type scale, in Outfit with JetBrains Mono for figures and code.
 *
 * Pass a different [family] to reskin the whole system's typography in one line:
 * ```
 * KontourTheme(typography = kontourTypography(family = myBrandFamily)) { … }
 * ```
 *
 * @param mono The face for [Typography.mono] — code, keyboard keys and figures.
 *   Defaults to [family] rather than to the bundled mono, because a brand that
 *   supplies its own text face and says nothing about a second one has asked for
 *   *its* face, not for its face beside somebody else's. A caller who wants both
 *   says so: `kontourTypography(brand, jetBrainsMonoFontFamily())`. The default
 *   scale does exactly that, one function up.
 */
fun kontourTypography(family: FontFamily, mono: FontFamily = family): Typography = Typography(
    // Display — the marketing hero voice. -0.02em tracking keeps large Outfit
    // from feeling loose, matching `.headline` on the home page.
    displayLarge = scaleStyle(family, 48, 1.10f, FontWeight.ExtraBold, -0.02f),
    displayMedium = scaleStyle(family, 40, 1.15f, FontWeight.ExtraBold, -0.02f),
    displaySmall = scaleStyle(family, 32, 1.20f, FontWeight.ExtraBold, -0.015f),

    headlineLarge = scaleStyle(family, 28, 1.25f, FontWeight.Bold, -0.01f),
    headlineMedium = scaleStyle(family, 24, 1.30f, FontWeight.Bold, -0.01f),
    headlineSmall = scaleStyle(family, 20, 1.35f, FontWeight.SemiBold),

    titleLarge = scaleStyle(family, 18, 1.40f, FontWeight.SemiBold),
    titleMedium = scaleStyle(family, 16, 1.40f, FontWeight.SemiBold),
    titleSmall = scaleStyle(family, 14, 1.40f, FontWeight.SemiBold),

    // 1.6 line height throughout, from `.markdown p` and `.subhead`.
    bodyLarge = scaleStyle(family, 17, 1.60f, FontWeight.Normal),
    bodyMedium = scaleStyle(family, 15, 1.60f, FontWeight.Normal),
    bodySmall = scaleStyle(family, 13, 1.50f, FontWeight.Normal),

    // Labels sit on a single line inside a control, so they get tight leading.
    labelLarge = scaleStyle(family, 16, 1.20f, FontWeight.SemiBold),
    labelMedium = scaleStyle(family, 14, 1.20f, FontWeight.SemiBold),
    labelSmall = scaleStyle(family, 12, 1.20f, FontWeight.SemiBold),

    eyebrow = scaleStyle(family, 13, 1.20f, FontWeight.Bold, 0.14f),

    // `tnum` on [bodyMedium]'s metrics. Redundant in a monospaced face, where
    // every figure is already one advance wide, and the reason this style is
    // worth having when [mono] *is* [family]: Outfit ships the feature, so a
    // readout that redraws in place stops jittering under the default theme
    // too rather than only under a brand that bundled a mono.
    mono = scaleStyle(mono, 15, 1.60f, FontWeight.Normal, features = "tnum"),
)
