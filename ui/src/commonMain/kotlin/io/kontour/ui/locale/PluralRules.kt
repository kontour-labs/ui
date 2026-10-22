package io.kontour.ui.locale

import kotlin.math.abs

/**
 * CLDR's plural rules, read.
 *
 * The rules in [CldrPluralRules] are CLDR's own text — `v = 0 and i % 10 = 2..4
 * and i % 100 != 12..14` — and this is the small interpreter for them: operands,
 * `%`, `=` and `!=` against values and ranges, `and` binding tighter than `or`.
 * The whole grammar is in UTS #35 part 3, section 5.1, and fits in a page, which
 * is why it is read at run time rather than compiled to Kotlin by the script.
 *
 * One table, on every platform, so an Arabic reader gets the same six forms from
 * an Android phone, an iPhone and a browser. Each platform has its own answer
 * and they come from different CLDR releases.
 */
internal object PluralRules {

    /**
     * Rules already read, by language. Replaced rather than mutated, so two
     * threads asking at once at worst both read the same rule — a `HashMap`
     * written from two threads can lose more than that.
     */
    @kotlin.concurrent.Volatile
    private var parsed: Map<String, List<Pair<PluralCategory, Condition>>> = emptyMap()

    /** The category of the whole number [count] in [language]. */
    fun categoryOf(language: String, count: Long): PluralCategory =
        categoryOf(language, Operands.of(count))

    /**
     * The category of a number written with fraction digits — `1.5`, `2.00` —
     * which in several languages is not the category of the same value as a
     * whole number: English says "1 star" and "1.0 stars".
     */
    fun categoryOf(language: String, written: String): PluralCategory =
        categoryOf(language, Operands.of(written))

    private fun categoryOf(language: String, operands: Operands): PluralCategory {
        val rules = rulesFor(language)
        for ((category, condition) in rules) {
            if (condition.matches(operands)) return category
        }
        return PluralCategory.Other
    }

    private fun rulesFor(language: String): List<Pair<PluralCategory, Condition>> {
        val key = language.lowercase().substringBefore('-').substringBefore('_')
        parsed[key]?.let { return it }
        val source = CldrPluralRules[key] ?: CldrPluralRules["root"] ?: emptyList()
        val rules = source.map { (name, rule) ->
            categoryNamed(name) to Parser(rule.substringBefore('@').trim()).parse()
        }
        parsed = parsed + (key to rules)
        return rules
    }

    private fun categoryNamed(name: String): PluralCategory = when (name) {
        "zero" -> PluralCategory.Zero
        "one" -> PluralCategory.One
        "two" -> PluralCategory.Two
        "few" -> PluralCategory.Few
        "many" -> PluralCategory.Many
        else -> PluralCategory.Other
    }

    /**
     * CLDR's operands for a number: `n` its absolute value, `i` the integer
     * digits, `v` how many fraction digits are written and `f` them as an
     * integer, `w` and `t` the same with trailing zeros dropped, and `e`/`c`
     * the compact exponent, which is 0 for any number written out in full.
     */
    class Operands(val n: Double, val i: Long, val v: Int, val w: Int, val f: Long, val t: Long, val e: Int) {
        fun value(operand: Char): Double = when (operand) {
            'n' -> n
            'i' -> i.toDouble()
            'v' -> v.toDouble()
            'w' -> w.toDouble()
            'f' -> f.toDouble()
            't' -> t.toDouble()
            'e', 'c' -> e.toDouble()
            else -> error("no plural operand '$operand'")
        }

        companion object {
            fun of(count: Long): Operands {
                val n = abs(count)
                return Operands(n.toDouble(), n, 0, 0, 0, 0, 0)
            }

            fun of(written: String): Operands {
                val text = written.trim().removePrefix("-")
                val integer = text.substringBefore('.')
                val fraction = if ('.' in text) text.substringAfter('.') else ""
                val trimmed = fraction.trimEnd('0')
                return Operands(
                    n = text.toDoubleOrNull() ?: 0.0,
                    i = integer.toLongOrNull() ?: 0,
                    v = fraction.length,
                    w = trimmed.length,
                    f = fraction.toLongOrNull() ?: 0,
                    t = trimmed.toLongOrNull() ?: 0,
                    e = 0,
                )
            }
        }
    }

    /** A rule: an `or` of `and`s of relations. */
    fun interface Condition {
        fun matches(operands: Operands): Boolean
    }

    /** Reads one rule's condition, the part before its `@integer` samples. */
    private class Parser(private val text: String) {
        private val tokens = pluralRuleTokens(text)
        private var at = 0

        fun parse(): Condition {
            if (tokens.isEmpty()) return Condition { true }
            val or = mutableListOf(andChain())
            while (peek() == "or") {
                at++
                or += andChain()
            }
            return Condition { number -> or.any { it.matches(number) } }
        }

        private fun andChain(): Condition {
            val and = mutableListOf(relation())
            while (peek() == "and") {
                at++
                and += relation()
            }
            return Condition { number -> and.all { it.matches(number) } }
        }

        private fun relation(): Condition {
            val operand = next().single()
            val modulus = if (peek() == "%") {
                at++
                next().toDouble()
            } else {
                null
            }
            val negated = when (next()) {
                "=" -> false
                "!=" -> true
                else -> error("expected = or != in plural rule '$text'")
            }
            val ranges = mutableListOf<ClosedFloatingPointRange<Double>>()
            do {
                if (peek() == ",") at++
                val from = next().toDouble()
                val to = if (peek() == "..") {
                    at++
                    next().toDouble()
                } else {
                    from
                }
                ranges += from..to
            } while (peek() == ",")
            return Condition { number ->
                val raw = number.value(operand)
                val value = if (modulus != null) raw % modulus else raw
                // A range matches whole numbers only: `n = 2..4` is not true of 2.5.
                val inside = ranges.any { range ->
                    if (range.start == range.endInclusive) value == range.start
                    else value == kotlin.math.floor(value) && value in range
                }
                inside != negated
            }
        }

        private fun peek(): String? = tokens.getOrNull(at)

        private fun next(): String = tokens.getOrNull(at++) ?: error("plural rule '$text' ended early")
    }
}

/**
 * The rule's words, operators and numbers, and nothing else: `..`,
 * `!=`, `=`, `%` and `,`, runs of lower-case letters, and numbers with
 * an optional fraction. Anything else (the spaces) is passed over.
 *
 * By hand rather than by `Regex("""\.\.|!=|[=%,]|[a-z]+|\d+(?:\.\d+)?""")`,
 * with the same tokens: on the web that one pattern brought a
 * regular-expression engine into every app with a theme.
 */
internal fun pluralRuleTokens(text: String): List<String> {
    val tokens = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        val char = text[i]
        val end = when {
            text.startsWith("..", i) || text.startsWith("!=", i) -> i + 2
            char == '=' || char == '%' || char == ',' -> i + 1
            char in 'a'..'z' -> runEnd(text, i) { it in 'a'..'z' }
            char in '0'..'9' -> {
                val whole = runEnd(text, i) { it in '0'..'9' }
                if (text.getOrNull(whole) == '.' && text.getOrNull(whole + 1)?.let { it in '0'..'9' } == true) {
                    runEnd(text, whole + 1) { it in '0'..'9' }
                } else {
                    whole
                }
            }
            else -> {
                i++
                continue
            }
        }
        tokens += text.substring(i, end)
        i = end
    }
    return tokens
}

/** Where the run of characters from [from] that are [inside] ends. */
private inline fun runEnd(text: String, from: Int, inside: (Char) -> Boolean): Int {
    var end = from
    while (end < text.length && inside(text[end])) end++
    return end
}
