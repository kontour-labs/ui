package io.kontour.ui.locale

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import io.kontour.ui.theme.kontourTypography
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The scale is told its locale, and made safe for the locale's script. */
class ScriptTypographyTest {

    private val scale = kontourTypography(FontFamily.Default)

    @Test
    fun latinOnlyLearnsItsLocale() {
        val english = scale.forLocale(Locale("en-AU"))
        assertEquals(LocaleList(Locale("en-AU")), english.bodyMedium.localeList)
        assertEquals(scale.eyebrow.letterSpacing, english.eyebrow.letterSpacing)
        assertEquals(scale.labelMedium.lineHeight, english.labelMedium.lineHeight)
        assertEquals(scale.displayLarge.copy(localeList = LocaleList(Locale("en-AU"))), english.displayLarge)
    }

    @Test
    fun arabicLosesItsTrackingAndGainsLeading() {
        val arabic = scale.forLocale(Locale("ar-EG"))
        assertEquals(0.em, arabic.eyebrow.letterSpacing, "tracking pulls joined letters apart")
        assertEquals(0.em, arabic.displayLarge.letterSpacing)
        assertTrue(arabic.labelMedium.lineHeight.value >= arabic.labelMedium.fontSize.value * 1.5f)
        assertEquals(LineHeightStyle.Trim.None, arabic.labelMedium.lineHeightStyle?.trim)
        assertEquals(FontSynthesis.Weight, arabic.bodyMedium.fontSynthesis)
    }

    @Test
    fun hindiAndVietnameseAreTall() {
        assertTrue(scale.forLocale(Locale("hi")).labelSmall.lineHeight.value >= 12f * 1.5f)
        assertTrue(scale.forLocale(Locale("vi")).labelSmall.lineHeight.value >= 12f * 1.5f)
        assertEquals(scale.labelSmall.lineHeight, scale.forLocale(Locale("de")).labelSmall.lineHeight)
    }

    @Test
    fun anAppsFaceForAScript() {
        val gothic = FontFamily.SansSerif
        val japanese = scale.forLocale(Locale("ja-JP"), ScriptTypography(families = mapOf("Jpan" to gothic)))
        assertEquals(gothic, japanese.bodyMedium.fontFamily)
        assertEquals(scale.mono.fontFamily, japanese.mono.fontFamily, "the mono face has its own map")
        assertEquals(scale.bodyMedium.fontFamily, scale.forLocale(Locale("ko"), ScriptTypography(families = mapOf("Jpan" to gothic))).bodyMedium.fontFamily)
    }
}
