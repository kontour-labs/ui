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
}
