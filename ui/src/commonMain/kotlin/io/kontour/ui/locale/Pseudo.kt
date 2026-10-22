package io.kontour.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.theme.ProvideTokens
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.mapped
import kotlin.math.ceil
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * A language that does not exist, for finding what a real one would break.
 *
 * A screen that has only ever been read in English has three kinds of fault
 * waiting in it, and each of these finds one without a translator:
 *
 * - [Accented] — every letter a look-alike with an accent, every string
 *   bracketed and about 40% longer, as German and Finnish run. A word still in
 *   plain English was never routed through `Strings`; a bracket cut off is a
 *   label that truncates.
 * - [Bidi] — every string reversed with a right-to-left override, the layout
 *   right to left, and the digits Arabic-Indic, as Android's `ar-XB` does. What
 *   still reads left to right, or in Western digits, was laid out by hand.
 * - [Tall] — marks stacked above and below every letter, as Vietnamese and Thai
 *   stack them. A clipped mark is a line height or a fixed box too small for
 *   half the world's scripts.
 *
 * ```kotlin
 * PseudoLocalised(PseudoLocale.Accented) { App() }
 * ```
 */
enum class PseudoLocale { Accented, Bidi, Tall }

/** [content] with the theme's strings and formats in [mode], and laid out right to left for [PseudoLocale.Bidi]. */
@Composable
fun PseudoLocalised(mode: PseudoLocale, content: @Composable () -> Unit) {
    val strings = Theme.strings
    val formats = Theme.formats
    ProvideTokens(
        strings = remember(strings, mode) { strings.pseudoLocalised(mode) },
        formats = remember(formats, mode) { formats.pseudoLocalised(mode) },
    ) {
        CompositionLocalProvider(
            LocalPseudoLocale provides mode,
            LocalLayoutDirection provides if (mode == PseudoLocale.Bidi) LayoutDirection.Rtl else LocalLayoutDirection.current,
            content = content,
        )
    }
}

/**
 * The pseudo-locale [PseudoLocalised] is drawing in, or `null` outside one: for
 * words of a module's own, beside `Theme.strings`, to follow it. The charts'
 * `ChartStrings` read it, so the gallery's sweeps catch an English word in a
 * chart as they catch one anywhere else.
 */
val LocalPseudoLocale = staticCompositionLocalOf<PseudoLocale?> { null }

/** Every word in these strings in [mode]. */
fun Strings.pseudoLocalised(mode: PseudoLocale): Strings = mapped { pseudoLocalise(it, mode) }

/** These formats' words in [mode], and for [PseudoLocale.Bidi] their digits Arabic-Indic. */
fun LocaleFormats.pseudoLocalised(mode: PseudoLocale): LocaleFormats = PseudoFormats(this, mode)

/** [text] in [mode]. Digits and anything that is not a letter are left alone. */
fun pseudoLocalise(text: String, mode: PseudoLocale): String = when (mode) {
    PseudoLocale.Accented -> {
        if (text.isEmpty()) {
            text
        } else {
            val accented = text.map { Accents[it] ?: it }.joinToString("")
            val letters = text.count { it.isLetter() }
            "[" + accented + " " + "·".repeat(ceil(letters * Expansion).toInt().coerceAtLeast(1)) + "]"
        }
    }
    PseudoLocale.Bidi -> if (text.isEmpty()) text else "‮" + text + "‬"
    PseudoLocale.Tall -> buildString {
        for (char in text) {
            append(char)
            if (char.isLetter()) append(TallMarks)
        }
    }
}

private const val Expansion = 0.4

/** Two marks stacked above a letter and one below, as Vietnamese writes `ệ`. */
private const val TallMarks = "̣̂́"

private val Accents: Map<Char, Char> = run {
    val plain = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val accented = "àƀçđéƒĝĥîĵķĺɱñöþǫŕšţûṽŵẋýžÀƁÇĐÉƑĜĤÎĴĶĹṀÑÖÞǪŔŠŢÛṼŴẊÝŽ"
    plain.zip(accented).toMap()
}

private class PseudoFormats(private val base: LocaleFormats, private val mode: PseudoLocale) : LocaleFormats by base {
    private fun words(text: String) = pseudoLocalise(text, mode)

    /** Western digits to Arabic-Indic under [PseudoLocale.Bidi]; everything else as it is. */
    private fun number(text: String) = if (mode != PseudoLocale.Bidi) {
        text
    } else {
        buildString(text.length) { for (char in text) append(if (char in '0'..'9') '٠' + (char - '0') else char) }
    }

    override val symbols: NumberSymbols
        get() = if (mode == PseudoLocale.Bidi) base.symbols.copy(zeroDigit = '٠') else base.symbols

    override fun integer(value: Long): String = number(base.integer(value))
    override fun integer(value: Int): String = integer(value.toLong())
    override fun digits(value: Long): String = number(base.digits(value))
    override fun decimal(value: Double, fractionDigits: Int): String = number(base.decimal(value, fractionDigits))
    override fun percent(fraction: Double, fractionDigits: Int): String = number(base.percent(fraction, fractionDigits))
    override fun currency(amount: Double, currency: String): String = number(base.currency(amount, currency))
    override fun quantity(count: Long): Quantity = Quantity(count, integer(count), pluralCategory(count))
    override fun quantity(count: Int): Quantity = quantity(count.toLong())

    override fun monthName(month: Month, width: NameWidth, standalone: Boolean) = words(base.monthName(month, width, standalone))
    override fun weekdayName(day: DayOfWeek, width: NameWidth, standalone: Boolean) = words(base.weekdayName(day, width, standalone))
    override fun dayPeriod(time: LocalTime) = words(base.dayPeriod(time))
    override fun date(date: LocalDate, style: DateStyle) = words(number(base.date(date, style)))
    override fun monthAndYear(date: LocalDate) = words(number(base.monthAndYear(date)))
    override fun time(time: LocalTime) = words(number(base.time(time)))
    override fun dateTime(date: LocalDate, time: LocalTime) = words(number(base.dateTime(date, time)))
    override fun relativeTime(value: Long, unit: RelativeUnit) = words(number(base.relativeTime(value, unit)))
    override fun relativeNow() = words(base.relativeNow())
    // Wrapped under Bidi, as every other formatter is, so the language's own
    // joiners (", ", " and ") are inside the right-to-left run and a sweep can
    // tell them from a comma written into a component. Only under Bidi: the
    // items are wrapped already in the other modes, and marking them again
    // would stack a tall pseudo-locale's marks twice.
    override fun list(items: List<String>, style: ListStyle) =
        if (mode == PseudoLocale.Bidi) words(base.list(items, style)) else base.list(items, style)

    override fun withConventions(conventions: DateTimeConventions): LocaleFormats =
        PseudoFormats(base.withConventions(conventions), mode)

    override fun equals(other: Any?): Boolean = other is PseudoFormats && other.base == base && other.mode == mode
    override fun hashCode(): Int = base.hashCode() * 31 + mode.hashCode()
    override fun toString(): String = "PseudoFormats($mode, $base)"
}
