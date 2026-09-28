package io.kontour.ui.locale

import androidx.compose.runtime.Immutable

/**
 * Which of a language's plural forms a number takes.
 *
 * English has two — *one* stop, *other* stops — and that is the mistake an
 * `if (count == 1)` bakes in. Polish has four: 1 przystanek, 2 przystanki, 5
 * przystanków, 1,5 przystanku. Arabic has six. Japanese has one, because it does
 * not inflect for number at all. The six names are CLDR's, and every language
 * uses some subset of them; `Other` is the one every language has.
 */
enum class PluralCategory { Zero, One, Two, Few, Many, Other }

/**
 * A number on its way into a sentence: its value, the value written the way
 * this reader writes numbers, and the plural form it takes in their language.
 *
 * ```kotlin
 * Strings(
 *     selectedCount = { n -> n.select(one = "${n.formatted} Haltestelle", other = "${n.formatted} Haltestellen") },
 * )
 * ```
 *
 * This is what a phrase with a number in it receives, and it exists so that a
 * translation can put the number anywhere in the sentence and pick the right
 * word for it: `Int` → `String` could do the first and not the second. What
 * [formatted] contains is the reader's digits and grouping — `1,234` in
 * English, `1.234` in German, `١٬٢٣٤` in Egyptian Arabic — so a phrase built
 * from it is localised the moment the formats are.
 *
 * @property value The number itself.
 * @property formatted The number as this reader writes it.
 * @property category Its plural form in this reader's language.
 */
@Immutable
class Quantity(
    val value: Long,
    val formatted: String,
    val category: PluralCategory,
) {
    /**
     * The form of a phrase for [category].
     *
     * A language that does not use a category never produces it, so only the
     * forms a translation needs have to be given; any it leaves out fall back
     * to [other], which every language has.
     */
    fun select(
        other: String,
        zero: String? = null,
        one: String? = null,
        two: String? = null,
        few: String? = null,
        many: String? = null,
    ): String = when (category) {
        PluralCategory.Zero -> zero
        PluralCategory.One -> one
        PluralCategory.Two -> two
        PluralCategory.Few -> few
        PluralCategory.Many -> many
        PluralCategory.Other -> null
    } ?: other

    override fun equals(other: Any?): Boolean =
        other is Quantity && other.value == value && other.formatted == formatted && other.category == category

    override fun hashCode(): Int = (value.hashCode() * 31 + formatted.hashCode()) * 31 + category.hashCode()

    override fun toString(): String = "Quantity($value, \"$formatted\", $category)"
}
