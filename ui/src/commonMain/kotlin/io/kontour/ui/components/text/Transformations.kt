package io.kontour.ui.components.text

import androidx.compose.runtime.Immutable
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.maxLength

/**
 * Rejects anything that is not a digit.
 *
 * Applied as the text arrives, so a rejected keystroke never reaches the state
 * and the field cannot flicker through an invalid value. Prefer this to
 * validating afterwards and showing an error — the best error message is the
 * one that never has to appear.
 */
fun InputTransformation.Companion.digitsOnly(): InputTransformation =
    InputTransformation { if (!asCharSequence().all(Char::isDigit)) revertAllChanges() }

/**
 * Rejects anything that is not a digit or a single leading minus / decimal point.
 *
 * Deliberately permissive about *intermediate* states: `-`, `1.` and `-0.` are
 * all allowed through, because a user typing `-0.5` passes through every one of
 * them. Rejecting them would make the field impossible to type into.
 *
 * [decimalSeparator] is the reader's own — `,` in German — and is accepted
 * beside `.`, which a pasted number or a keyboard that only has a point still
 * uses. Read the value back with `Theme.formats.parseDecimal`, which takes
 * either.
 */
fun InputTransformation.Companion.decimal(
    allowNegative: Boolean = false,
    decimalSeparator: Char = '.',
): InputTransformation =
    InputTransformation {
        if (!admitsNumber(asCharSequence(), allowDecimal = true, allowNegative, point = decimalSeparator)) revertAllChanges()
    }

/**
 * Rejects anything that is not a whole number, with an optional leading minus.
 *
 * [digitsOnly] with room for a sign. `-` on its own is allowed through for the
 * same reason [decimal] allows `1.`: it is where a user typing `-4` has to pass.
 */
fun InputTransformation.Companion.integer(allowNegative: Boolean = false): InputTransformation =
    InputTransformation {
        if (!admitsNumber(asCharSequence(), allowDecimal = false, allowNegative)) revertAllChanges()
    }

/**
 * Whether [text] is a number — or on its way to one — under these rules.
 *
 * The one test [decimal], [integer] and the number field's own clearing share, so
 * the field cannot disagree with its own filter about what it may hold.
 */
internal fun admitsNumber(
    text: CharSequence,
    allowDecimal: Boolean,
    allowNegative: Boolean,
    maxLength: Int? = null,
    point: Char = '.',
): Boolean {
    if (maxLength != null && text.length > maxLength) return false
    val body = if (allowNegative && text.startsWith("-")) text.drop(1) else text
    fun isPoint(char: Char) = char == '.' || char == point
    return body.all { it.isDigit() || (allowDecimal && isPoint(it)) } && body.count(::isPoint) <= 1
}

/** Caps the field at [max] characters. */
fun InputTransformation.Companion.limit(max: Int): InputTransformation =
    InputTransformation.maxLength(max)

/**
 * Displays digits grouped by a mask, without changing what is stored.
 *
 * ```kotlin
 * // Stored: "0412345678"   Displayed: "0412 345 678"
 * MaskTransformation(groups = listOf(4, 3, 3), separator = ' ')
 * ```
 *
 * The separation between stored and displayed value is the point: the caller
 * reads clean digits out of the [androidx.compose.foundation.text.input.TextFieldState]
 * and never has to strip formatting back out, which is where mask
 * implementations usually go wrong.
 *
 * @param groups Sizes of each run of characters. Digits past the last group are
 *   shown unseparated rather than dropped — truncation belongs in an
 *   [InputTransformation], not here.
 */
class MaskTransformation(
    private val groups: List<Int>,
    private val separator: Char = ' ',
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        if (groups.isEmpty()) return

        // Walk backwards: each insertion shifts every index after it, and going
        // right-to-left means the indices computed so far stay valid.
        val boundaries = buildList {
            var offset = 0
            for (size in groups) {
                offset += size
                add(offset)
            }
        }
        for (index in boundaries.reversed()) {
            if (index in 1 until length) {
                insert(index, separator.toString())
            }
        }
    }
}

/**
 * A phone number shown in [format]'s groups — `0412 345 678` for
 * [PhoneFormat.AustralianMobile]. There is no default: numbers are grouped
 * differently in every country, and a mask for the wrong one is worse than none.
 */
fun phoneMask(format: PhoneFormat): OutputTransformation =
    MaskTransformation(groups = format.groups)

/**
 * A payment card number in [groups]: `1234 5678 9012 3456` by default, and
 * `listOf(4, 6, 5)` for an American Express card's `3782 822463 10005`.
 */
fun cardMask(groups: List<Int> = listOf(4, 4, 4, 4)): OutputTransformation = MaskTransformation(groups = groups)

/**
 * How a [PhoneField] groups the digits it is typed, and how many it takes.
 *
 * Numbers are written differently in every country and often within one, so the
 * field does not guess: [None] takes up to fifteen digits — the most a number
 * can have anywhere — and shows them as typed, and an app that knows its
 * readers' country gives the grouping it uses.
 *
 * @property groups The size of each group, in order; empty for none.
 * @property maxDigits The most digits the field accepts.
 */
@Immutable
data class PhoneFormat(val groups: List<Int>, val maxDigits: Int = groups.sum()) {
    companion object {
        /** Up to fifteen digits, as typed. */
        val None = PhoneFormat(emptyList(), maxDigits = 15)

        /** `0412 345 678`. */
        val AustralianMobile = PhoneFormat(listOf(4, 3, 3))
    }
}

/** `12:34` — a 24-hour time typed as four digits. */
fun timeMask(): OutputTransformation = MaskTransformation(groups = listOf(2, 2), separator = ':')
