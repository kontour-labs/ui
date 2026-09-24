package io.kontour.ui.components.text

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
 */
fun InputTransformation.Companion.decimal(allowNegative: Boolean = false): InputTransformation =
    InputTransformation {
        if (!admitsNumber(asCharSequence(), allowDecimal = true, allowNegative)) revertAllChanges()
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
): Boolean {
    if (maxLength != null && text.length > maxLength) return false
    val body = if (allowNegative && text.startsWith("-")) text.drop(1) else text
    return body.all { it.isDigit() || (allowDecimal && it == '.') } && body.count { it == '.' } <= 1
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

/** `0412 345 678` — an Australian mobile number. */
fun phoneMask(): OutputTransformation = MaskTransformation(groups = listOf(4, 3, 3))

/** `1234 5678 9012 3456` — a payment card number. */
fun cardMask(): OutputTransformation = MaskTransformation(groups = listOf(4, 4, 4, 4))

/** `12:34` — a 24-hour time typed as four digits. */
fun timeMask(): OutputTransformation = MaskTransformation(groups = listOf(2, 2), separator = ':')
