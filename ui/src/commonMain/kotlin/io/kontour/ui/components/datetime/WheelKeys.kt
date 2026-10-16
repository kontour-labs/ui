package io.kontour.ui.components.datetime

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModalityState
import io.kontour.ui.overlay.TypeAheadPause
import io.kontour.ui.overlay.letterOf
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A drum turned from the keyboard and by a screen reader, as a finger turns it.
 *
 * A drum was a box that said its value and nothing else: it could not hold the
 * focus, so Tab went past every wheel of a `TimePicker`, and a screen reader
 * heard the hour and could not change it.
 *
 * **One stop, not a row of them.** The drum takes the focus as one control, as
 * its rows are one control to a screen reader — see the tap-to-turn note in
 * [WheelDrum] — and the keys move the row in its band:
 *
 * - **Up and Down** a row back or on. A drum with ends stops at them; an endless
 *   one goes round, as a finger would take it.
 * - **Page Up and Page Down** a drum's face of rows.
 * - **Home and End** the first and last rows.
 * - **Typing** turns to the next row that begins with what was typed, letters
 *   within [TypeAheadPause] of each other being one word, as a menu's do: "17"
 *   is five o'clock in the afternoon on an hour drum, not a one and then a
 *   seven; the same letter again goes on to the next row beginning with it.
 *
 * Each key turns the drum the way a tapped row does, so it is seen to turn, is
 * reported row by row and comes to rest once. A key pressed while a turn is
 * still under way carries on from where that one is going rather than from the
 * row in the band, so a held arrow does not lose rows to the animation.
 *
 * And a screen reader is handed the drum as a control it can adjust — the rows
 * as a range, and the row in the band as where it is — which is what the
 * platforms' own pickers are to TalkBack and VoiceOver. It still hears the
 * row's words: the drum's state description says them, over the number.
 *
 * Disabled, none of this is there. Not [focusable] — a drum in a panel that
 * folds itself away, whose field takes the keys instead — it is a screen
 * reader's to turn and not a stop for the keyboard.
 *
 * @param count How many rows the drum has.
 * @param wraps Whether it goes round, past its last row to its first.
 * @param page How many rows Page Up and Page Down go: the drum's face.
 * @param at The row the drum is turning to, or the one in its band.
 * @param nameOf A row's words, which typing finds it by.
 * @param turnTo Turns the drum to a row, seen.
 * @param focusable Whether the drum takes the focus and the keys.
 */
@Composable
internal fun Modifier.wheelControl(
    enabled: Boolean,
    count: Int,
    wraps: Boolean,
    page: Int,
    at: () -> Int,
    nameOf: (Int) -> String,
    turnTo: (Int) -> Unit,
    interactionSource: MutableInteractionSource,
    focusRequester: FocusRequester,
    focusable: Boolean = true,
): Modifier {
    if (!enabled || count == 0) return this
    val currentAt by rememberUpdatedState(at)
    val currentName by rememberUpdatedState(nameOf)
    val currentTurn by rememberUpdatedState(turnTo)
    val currentCount by rememberUpdatedState(count)
    val formats = Theme.formats
    val modality = LocalInputModalityState.current
    val scope = rememberCoroutineScope()
    val typing = remember { Typing() }

    /** [by] rows on from where the drum is going: round the ends, or stopped at them. */
    fun stepped(by: Int): Int {
        val to = currentAt() + by
        return if (wraps) to.mod(currentCount) else to.coerceIn(0, currentCount - 1)
    }

    /**
     * The next row whose words begin with [word]. A word of one letter, or of
     * one letter over and over, looks from the row after the one the drum is at,
     * so the same letter again goes on; a longer word looks from that row, which
     * it may still describe.
     */
    fun find(word: String): Int {
        val repeated = word.all { it == word[0] }
        val prefix = formats.foldForSearch(if (repeated) word.substring(0, 1) else word)
        val from = currentAt() + if (repeated) 1 else 0
        for (n in 0 until currentCount) {
            val index = (from + n).mod(currentCount)
            if (formats.foldForSearch(currentName(index)).startsWith(prefix)) return index
        }
        return -1
    }

    val turned = this
        .semantics {
            // As a slider is, so a screen reader can turn it: the platforms'
            // adjustable controls are a range and a way to set a place in it.
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = at().toFloat(),
                range = 0f..(count - 1).coerceAtLeast(0).toFloat(),
                steps = (count - 2).coerceAtLeast(0),
            )
            setProgress { value ->
                val target = value.roundToInt().coerceIn(0, currentCount - 1)
                if (target != currentAt()) currentTurn(target)
                true
            }
        }
    if (!focusable) return turned
    return turned
        .focusRequester(focusRequester)
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return@onKeyEvent false
            val target = when (event.key) {
                Key.DirectionDown -> stepped(1)
                Key.DirectionUp -> stepped(-1)
                Key.PageDown -> stepped(page)
                Key.PageUp -> stepped(-page)
                Key.MoveHome -> 0
                Key.MoveEnd -> currentCount - 1
                else -> {
                    val letter = letterOf(event)?.takeUnless { it == ' ' } ?: return@onKeyEvent false
                    typing.word += letter
                    typing.forget?.cancel()
                    typing.forget = scope.launch {
                        delay(TypeAheadPause)
                        typing.word = ""
                    }
                    // Typing is not a key the modality tracker counts, and a row
                    // found by it should show where the focus is.
                    modality?.current = InputModality.Keyboard
                    // Kept even where nothing matches: there is nothing else for
                    // a letter to do on a drum.
                    find(typing.word).takeIf { it >= 0 } ?: return@onKeyEvent true
                }
            }
            if (target != currentAt()) currentTurn(target)
            // An arrow at an end is still the drum's: it moves nothing, and
            // nothing round it should move instead.
            true
        }
        .focusable(interactionSource = interactionSource)
}

/** What has been typed at a drum, and when to forget it. Plain: only keys read it. */
private class Typing {
    var word = ""
    var forget: Job? = null
}

/**
 * Where a drum is turning to by a key, for the next key to carry on from: the
 * row it was sent to, or -1 once it has arrived or was taken by anything else.
 * Plain, as only keys read it.
 */
internal class KeyTurn {
    var aim = -1
    var job: Job? = null
}
