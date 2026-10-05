package io.kontour.ui.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek

/** The three keywords a tag can change conventions with are read the same on every platform. */
class LocaleTagTest {

    @Test
    fun languageScriptAndRegion() {
        val tag = LocaleTag.parse("zh-Hant-TW")
        assertEquals("zh", tag.language)
        assertEquals("Hant", tag.script)
        assertEquals("TW", tag.region)
        assertEquals("419", LocaleTag.parse("es-419").region)
        assertEquals("en", LocaleTag.parse("en_AU").language)
    }

    @Test
    fun keywords() {
        val tag = LocaleTag.parse("en-AU-u-hc-h23-fw-sun-nu-arab")
        assertEquals(HourCycle.H23, tag.hourCycle)
        assertEquals(DayOfWeek.SUNDAY, tag.firstDayOfWeek)
        assertEquals('٠', tag.zeroDigit)
        assertEquals("h23", tag.keyword("hc"))
        assertNull(LocaleTag.parse("en-AU").hourCycle)
        assertNull(LocaleTag.parse("en-AU-x-u-hc-h23").hourCycle, "a private-use section is not an extension")
        assertEquals(HourCycle.H12, LocaleTag.parse("de-t-en-u-hc-h12").hourCycle, "another extension before -u- is skipped")
    }

    @Test
    fun keywordsWinOverThePlatform() {
        val tag = LocaleTag.parse("en-US-u-hc-h23-fw-mon-nu-latn")
        val platform = DateTimeConventions(hourCycle = HourCycle.H12, firstDayOfWeek = DayOfWeek.SUNDAY)
        assertEquals(DateTimeConventions(HourCycle.H23, DateOrder.DayMonthYear, DayOfWeek.MONDAY), platform.honouring(tag))
        assertEquals('0', NumberSymbols(zeroDigit = '٠').honouring(tag).zeroDigit)
    }

    @Test
    fun direction() {
        assertTrue(LocaleTag.parse("ar-EG").isRightToLeft)
        assertTrue(LocaleTag.parse("he").isRightToLeft)
        assertTrue(LocaleTag.parse("pa-Arab").isRightToLeft)
        assertFalse(LocaleTag.parse("pa").isRightToLeft)
        assertFalse(LocaleTag.parse("az-Latn").isRightToLeft)
        assertFalse(LocaleTag.parse("en").isRightToLeft)
    }

    /**
     * What a platform reports, cleaned into a tag `Locale(tag)` can take.
     *
     * Headless Chromium's `navigator.language` is `en-US@posix`, and Compose's
     * web `Locale(tag)` throws on it — reported as a blank page. Checked against
     * `Intl` in Node: `en-US@posix`, `en_US`, `en-US.UTF-8` and `""` all throw.
     */
    @Test
    fun platformTagsAreCleaned() {
        assertEquals("en-US", LocaleTag.cleanPlatformTag("en-US@posix"))
        assertEquals("en-AU", LocaleTag.cleanPlatformTag("en_AU.UTF-8"))
        assertEquals("sr-Latn-RS", LocaleTag.cleanPlatformTag("sr_Latn_RS@calendar=gregorian"))
        assertEquals("de", LocaleTag.cleanPlatformTag(" de "))
        assertNull(LocaleTag.cleanPlatformTag("C"))
        assertNull(LocaleTag.cleanPlatformTag("POSIX"))
        assertNull(LocaleTag.cleanPlatformTag("C.UTF-8"))
        assertNull(LocaleTag.cleanPlatformTag(""))
    }

    /**
     * A browser's languages, in order, until one is taken: the choice the web
     * makes with `Intl.getCanonicalLocales`, here with a stand-in for it that
     * refuses what Headless Chromium's `Intl.Locale` refused.
     */
    @Test
    fun theFirstLanguageTheBrowserTakesIsTheOne() {
        val refused = setOf("en-US@posix", "xx-INVALID")
        val canonical = { tag: String -> if (tag in refused || tag.length > 12) null else tag.replace("en-us", "en-US") }
        assertEquals("en-US", LocaleTag.firstUsableTag(listOf("en-US@posix", "de-DE"), "en-AU", canonical), "cleaned, the first is taken")
        assertEquals("de-DE", LocaleTag.firstUsableTag(listOf("C", "POSIX", "de-DE"), "en-AU", canonical), "none of a locale is skipped")
        assertEquals("en-US", LocaleTag.firstUsableTag(listOf("en-us"), "en-AU", canonical), "as the platform writes it")
        assertEquals("fr", LocaleTag.firstUsableTag(listOf("xx-INVALID", "fr"), "en-AU", canonical), "one it refuses is passed over")
        assertEquals("en-AU", LocaleTag.firstUsableTag(listOf("", "C.UTF-8"), "en-AU", canonical), "with none, the default")
        assertEquals("en-AU", LocaleTag.firstUsableTag(listOf("ja-JP"), "en-AU") { "" }, "an empty answer is a refusal")
    }
}
