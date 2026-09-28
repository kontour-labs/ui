package io.kontour.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.kontour.ui.theme.Typography

/**
 * What the type scale does for scripts other than Latin.
 *
 * The library's faces — Outfit and JetBrains Mono — are Latin, and it bundles
 * nothing else: a Japanese, Arabic or Hindi reader gets the platform's own font
 * for their script, which is the one they read everything else in. What it does
 * do, by default, is make the scale safe for whichever script the locale writes
 * in; [families] is how an app that wants its own face for a script gives one.
 *
 * @property families A face for a script, by its ISO 15924 code — `Jpan`,
 *   `Arab`, `Deva`. On a Skia platform this is the only way to choose Japanese
 *   rather than Chinese forms of the same Han characters.
 * @property monoFamilies The same for `Typography.mono`.
 * @property adjustMetrics Whether to make the scale safe for the locale's script:
 *   tracking off for scripts that join or have no letter case, taller lines and
 *   untrimmed leading for scripts whose marks reach above and below the line,
 *   and no synthesised italic where there is no italic tradition. On by default.
 */
@Immutable
data class ScriptTypography(
    val families: Map<String, FontFamily> = emptyMap(),
    val monoFamilies: Map<String, FontFamily> = emptyMap(),
    val adjustMetrics: Boolean = true,
)

/**
 * This scale, for text in [locale].
 *
 * Every style is told its locale, so a platform chooses the right glyphs and the
 * right line breaks for it. With [ScriptTypography.adjustMetrics], the styles
 * are adjusted for the locale's script as [ScriptTypography] describes; for
 * Latin, Greek and Cyrillic nothing else changes.
 */
fun Typography.forLocale(locale: Locale, scripts: ScriptTypography = ScriptTypography()): Typography {
    val tag = LocaleTag.of(locale)
    val script = tag.script.ifEmpty { likelyScript(tag.language) }
    val locales = LocaleList(locale)
    val family = scripts.families[script]
    val monoFamily = scripts.monoFamilies[script]
    val adjust = scripts.adjustMetrics
    fun TextStyle.fitted(mono: Boolean = false): TextStyle {
        var style = copy(localeList = locales)
        (if (mono) monoFamily else family)?.let { style = style.copy(fontFamily = it) }
        if (!adjust) return style
        if (script in UntrackedScripts && style.letterSpacing.isSpecified && style.letterSpacing.value != 0f) {
            style = style.copy(letterSpacing = 0.em)
        }
        if (script in NoItalicScripts) style = style.copy(fontSynthesis = FontSynthesis.Weight)
        if (script in TallScripts || tag.language in TallLatinLanguages) {
            style = style.copy(
                lineHeight = floorOf(style.lineHeight, style.fontSize),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            )
        }
        return style
    }
    return copy(
        displayLarge = displayLarge.fitted(),
        displayMedium = displayMedium.fitted(),
        displaySmall = displaySmall.fitted(),
        headlineLarge = headlineLarge.fitted(),
        headlineMedium = headlineMedium.fitted(),
        headlineSmall = headlineSmall.fitted(),
        titleLarge = titleLarge.fitted(),
        titleMedium = titleMedium.fitted(),
        titleSmall = titleSmall.fitted(),
        bodyLarge = bodyLarge.fitted(),
        bodyMedium = bodyMedium.fitted(),
        bodySmall = bodySmall.fitted(),
        labelLarge = labelLarge.fitted(),
        labelMedium = labelMedium.fitted(),
        labelSmall = labelSmall.fitted(),
        eyebrow = eyebrow.fitted(),
        mono = mono.fitted(mono = true),
    )
}

/**
 * Whether every face in [families] has loaded, starting them now.
 *
 * On the web a font is a download, and Compose draws with a fallback until it
 * arrives — and for a script the bundled faces do not cover, Compose for Web
 * fetches Noto from Google's font servers by itself, which a strict content
 * security policy or an offline app will refuse. An app that supplies its own
 * faces through [ScriptTypography] preloads them with this and holds its first
 * frame until they are in.
 */
@Composable
fun rememberFontsPreloaded(families: List<FontFamily>): Boolean {
    val resolver = LocalFontFamilyResolver.current
    var ready by remember(families) { mutableStateOf(families.isEmpty()) }
    LaunchedEffect(resolver, families) {
        families.forEach { resolver.preload(it) }
        ready = true
    }
    return ready
}

/** The script a language is written in when its tag does not say. */
internal fun likelyScript(language: String): String = when (language) {
    "ar", "fa", "ur", "ps", "sd", "ug", "ckb", "ks" -> "Arab"
    "he", "iw", "yi" -> "Hebr"
    "zh" -> "Hans"
    "ja" -> "Jpan"
    "ko" -> "Kore"
    "hi", "mr", "ne", "sa" -> "Deva"
    "bn", "as" -> "Beng"
    "ta" -> "Taml"
    "te" -> "Telu"
    "kn" -> "Knda"
    "ml" -> "Mlym"
    "gu" -> "Gujr"
    "pa" -> "Guru"
    "or" -> "Orya"
    "si" -> "Sinh"
    "th" -> "Thai"
    "lo" -> "Laoo"
    "km" -> "Khmr"
    "my" -> "Mymr"
    "bo", "dz" -> "Tibt"
    "am", "ti" -> "Ethi"
    "ka" -> "Geor"
    "hy" -> "Armn"
    "el" -> "Grek"
    "ru", "uk", "bg", "be", "kk", "mk", "ky", "mn", "sr", "tg" -> "Cyrl"
    "dv" -> "Thaa"
    else -> "Latn"
}

/** Scripts that join their letters or have no case, where tracking pulls words apart. */
private val UntrackedScripts = setOf(
    "Arab", "Hebr", "Thaa", "Syrc", "Nkoo", "Hans", "Hant", "Jpan", "Kore", "Hani",
    "Deva", "Beng", "Taml", "Telu", "Knda", "Mlym", "Gujr", "Guru", "Orya", "Sinh",
    "Thai", "Laoo", "Khmr", "Mymr", "Tibt", "Ethi",
)

/** Scripts with no italic tradition, where a synthesised slant only damages the letters. */
private val NoItalicScripts = setOf(
    "Arab", "Hebr", "Hans", "Hant", "Jpan", "Kore", "Hani",
    "Deva", "Beng", "Taml", "Telu", "Knda", "Mlym", "Gujr", "Guru", "Orya", "Sinh",
    "Thai", "Laoo", "Khmr", "Mymr", "Tibt",
)

/** Scripts whose vowel signs and marks reach well above and below a Latin line. */
private val TallScripts = setOf(
    "Arab", "Deva", "Beng", "Taml", "Telu", "Knda", "Mlym", "Gujr", "Guru", "Orya", "Sinh",
    "Thai", "Laoo", "Khmr", "Mymr", "Tibt",
)

/** Latin written with stacked diacritics — Vietnamese `ệ`. */
private val TallLatinLanguages = setOf("vi")

/** A line height of at least one and a half times the size, which tall scripts need. */
private fun floorOf(lineHeight: TextUnit, fontSize: TextUnit): TextUnit {
    if (!fontSize.isSpecified) return lineHeight
    val floor = (fontSize.value * TallLineHeight).sp
    return if (!lineHeight.isSpecified || lineHeight.value < floor.value) floor else lineHeight
}

private const val TallLineHeight = 1.5f
