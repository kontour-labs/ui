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
 */
@Composable
internal fun outfitFontFamily(): FontFamily {
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
 * Plus [monoLabel], the uppercase letterspaced eyebrow the site uses above
 * section headings (`.mono-label` in `home/src/lib/styles/app.css`).
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
    val monoLabel: TextStyle,
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
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.em,
    lineHeightStyle = TrimmedLineHeight,
)

/**
 * The default type scale, in Outfit.
 *
 * Pass a different [family] to reskin the whole system's typography in one line:
 * ```
 * KontourTheme(typography = kontourTypography(family = myBrandFamily)) { … }
 * ```
 */
fun kontourTypography(family: FontFamily): Typography = Typography(
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

    monoLabel = scaleStyle(family, 13, 1.20f, FontWeight.Bold, 0.14f),
)
