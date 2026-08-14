package io.kontour.ui.components.text

import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction

/**
 * One field's place in an [ImeChain]: which key the keyboard shows, what
 * pressing it does, and how to focus this field from the one before.
 *
 * Passed to a field as a single `imeChain` parameter rather than three separate
 * ones, because getting two of the three right is the same as getting none of
 * them right.
 */
@Immutable
class ImeChainStep internal constructor(
    internal val requester: FocusRequester,
    internal val imeAction: ImeAction,
    internal val handler: KeyboardActionHandler,
)

/**
 * Wires a set of fields so the keyboard's action key walks through them.
 *
 * ```kotlin
 * val chain = rememberImeChain("from", "to", "note", onSubmit = viewModel::plan)
 *
 * TextField(state = from, label = "From", imeChain = chain["from"])
 * TextField(state = to, label = "To", imeChain = chain["to"])
 * TextField(state = note, label = "Note", imeChain = chain["note"])
 * Button("Plan trip", onClick = viewModel::plan)
 * ```
 *
 * Every field but the last shows **Next** and moves to the one after it; the
 * last shows **Done** and calls [onSubmit]. Without this, a soft keyboard's
 * action key does nothing at all, and filling in a three-field form means
 * dismissing the keyboard and tapping the next field between every entry — with
 * the keyboard covering the field you are trying to tap.
 *
 * The order is the argument order here, not the order the fields happen to be
 * composed in. Declaring it in one place is the point: an ordering spread across
 * three call sites goes wrong the first time someone reorders the form, and the
 * failure is silent.
 *
 * Ids are keys, not labels — nothing renders them. Use whatever names the fields
 * by; asking for one that was not declared throws rather than silently giving
 * the field a dead action key.
 *
 * @param onSubmit Runs when the last field's Done is pressed. Give it the same
 *   thing the form's submit button does — a keyboard action that does something
 *   *slightly* different from the visible button is worse than one that does
 *   nothing.
 */
@Composable
fun rememberImeChain(
    vararg ids: String,
    onSubmit: () -> Unit = {},
): ImeChain {
    val submit = rememberUpdatedState(onSubmit)
    // `ids` is a vararg array, whose identity changes every composition; the
    // content is what the chain is keyed on.
    val order = ids.toList()
    return remember(order) { ImeChain(order) { submit.value() } }
}

@Stable
class ImeChain internal constructor(
    private val ids: List<String>,
    private val onSubmit: () -> Unit,
) {
    private val requesters: Map<String, FocusRequester> =
        ids.associateWith { FocusRequester() }

    /** The step for [id]. Throws if it was not declared in [rememberImeChain]. */
    operator fun get(id: String): ImeChainStep {
        val index = ids.indexOf(id)
        require(index >= 0) {
            "No field '$id' in this ImeChain. Declared: ${ids.joinToString()}"
        }
        val isLast = index == ids.lastIndex
        val next = if (isLast) null else requesters.getValue(ids[index + 1])

        return ImeChainStep(
            requester = requesters.getValue(id),
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
            handler = KeyboardActionHandler {
                if (next != null) {
                    // A field that has left the composition — scrolled out of a
                    // lazy list, or hidden by a conditional — has no focus
                    // target, and `requestFocus` throws for it. Swallowing that
                    // leaves focus where it is, which is the same as the action
                    // key doing nothing; crashing on a form the user merely
                    // scrolled is worse.
                    runCatching { next.requestFocus() }
                } else {
                    onSubmit()
                }
            },
        )
    }

    /** Focuses the first field. For a form the user arrived at in order to fill in. */
    fun focusFirst() {
        ids.firstOrNull()?.let { runCatching { requesters.getValue(it).requestFocus() } }
    }
}
