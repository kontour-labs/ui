package io.kontour.ui.theme

import io.kontour.ui.components.selection.ColourChannel
import io.kontour.ui.components.selection.ColourFormat
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.locale.PluralCategory
import io.kontour.ui.locale.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Strings.from` reaches every field, and a translated phrase gets its plural
 * form and its arguments in the order its parameters list them.
 */
class StringsLookupTest {

    @Test
    fun everyWordIsAskedForByItsName() {
        val strings = Strings.from(StringLookup { key -> "«$key»" })
        // Every String field, found by the JVM's own reflection so a field added
        // later without a line in `from` fails here rather than staying English.
        val words = Strings::class.java.declaredFields.filter { it.type == String::class.java }
        assertTrue(words.size > 60, "only ${words.size} string fields were found, so this is not reading Strings")
        val missed = words.mapNotNull { field ->
            field.isAccessible = true
            val value = field.get(strings) as String
            if (value == "«${field.name}»") null else "${field.name} = \"$value\""
        }
        assertTrue(missed.isEmpty(), "Strings.from leaves these in English: $missed")
    }

    @Test
    fun aPhraseTakesItsPluralFormAndItsArgumentsInOrder() {
        val polish = mapOf(
            "peopleCount" to mapOf(
                PluralCategory.One to "%1\$s osoba",
                PluralCategory.Few to "%1\$s osoby",
                PluralCategory.Many to "%1\$s osób",
            ),
        )
        val strings = Strings.from(object : StringLookup {
            override fun text(key: String): String? = when (key) {
                "pageOfCount" -> "%2\$s ページ中 %1\$s"
                "stepOfCountInProgress" -> "Schritt %1\$s von %2\$s, läuft"
                "sentenceSeparator" -> "。"
                "colourChannel_Red" -> "Rt"
                "activityOnDayNone" -> "Nichts am %1\$s"
                else -> null
            }

            override fun plural(key: String, category: PluralCategory): String? = polish[key]?.get(category)
        })
        val formats = LocaleFormats.english()
        fun q(value: Int, category: PluralCategory) = Quantity(value.toLong(), formats.integer(value), category)

        assertEquals("1 osoba", strings.peopleCount(q(1, PluralCategory.One)))
        assertEquals("3 osoby", strings.peopleCount(q(3, PluralCategory.Few)))
        assertEquals("5 osób", strings.peopleCount(q(5, PluralCategory.Many)))
        assertEquals("1,000 people", strings.peopleCount(q(1000, PluralCategory.Other)), "a form it lacks falls back to the English")
        assertEquals("10 ページ中 2", strings.pageOfCount(q(2, PluralCategory.Other), q(10, PluralCategory.Other)))
        assertEquals("Schritt 2 von 4, läuft", strings.stepOfCount(q(2, PluralCategory.Other), q(4, PluralCategory.Other), true))
        assertEquals("Step 2 of 4", strings.stepOfCount(q(2, PluralCategory.Other), q(4, PluralCategory.Other), false))
        assertEquals("a。b", strings.sentences(listOf("a", "b")))
        assertEquals("Rt", strings.colourChannel(ColourChannel.Red))
        assertEquals("G", strings.colourChannel(ColourChannel.Green))
        assertEquals("HSV", strings.colourFormatName(ColourFormat.Hsv))
        assertEquals("Nichts am 5 Jun", strings.activityOnDay(q(0, PluralCategory.Other), "5 Jun"))
        assertEquals("2 activities on 5 Jun", strings.activityOnDay(q(2, PluralCategory.Other), "5 Jun"))
    }

    @Test
    fun placeholders() {
        assertEquals("b a", fillArguments("%2\$s %1\$s", arrayOf("a", "b")))
        assertEquals("a b", fillArguments("%s %s", arrayOf("a", "b")))
        assertEquals("50% of 3", fillArguments("50%% of %d", arrayOf("3")))
        assertEquals("%x", fillArguments("%x", arrayOf("a")))
    }

    @Test
    fun englishPluralsAreRight() {
        val english = LocaleFormats.english()
        val strings = Strings()
        assertEquals("1 person", strings.peopleCount(english.quantity(1)))
        assertEquals("2 people", strings.peopleCount(english.quantity(2)))
        assertEquals("1 activity on today", strings.activityOnDay(english.quantity(1), "today"))
        assertEquals("No activity on today", strings.activityOnDay(english.quantity(0), "today"))
    }
}
