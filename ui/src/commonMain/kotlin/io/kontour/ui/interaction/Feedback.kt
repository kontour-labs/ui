package io.kontour.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * What just happened, from the user's point of view.
 *
 * Components declare an *intent* rather than a haptic constant. The mapping from
 * intent to physical feedback is one decision made in one place — which is what
 * lets the whole app's haptics be retuned, or muted for a user who has asked for
 * that, without touching a single component.
 *
 * It also stops the common failure where every control in an app fires
 * `LongPress` because that was the constant everyone knew about.
 */
enum class FeedbackIntent {
    /** A value changed: a toggle flipped, a radio selected, a chip filtered. */
    Selection,

    /** A discrete step was crossed: a slider tick, a picker detent, a stepper. */
    Tick,

    /** An action succeeded. Sparingly — not on every button. */
    Confirm,

    /** An action was refused: invalid input, a blocked gesture. */
    Reject,

    /** A long-press threshold was reached and something is about to happen. */
    LongPress,

    /** A drag threshold was crossed — a sheet snapping, a row passing its action point. */
    DragThreshold,

    /** A gesture completed and the element settled. */
    GestureEnd,

    /** A key or on-screen key was struck. */
    KeyPress,
}

/**
 * Performs physical feedback for a [FeedbackIntent].
 *
 * Obtained from [LocalFeedback]; on platforms without haptics — desktop, web —
 * the underlying platform handler is already a no-op, so components do not need
 * to check.
 */
@Stable
fun interface FeedbackDispatcher {
    fun perform(intent: FeedbackIntent)
}

/**
 * The feedback dispatcher for the current subtree.
 *
 * Replace it to mute or retune feedback app-wide:
 * ```
 * CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { }) {
 *     // haptics off for this subtree
 * }
 * ```
 */
val LocalFeedback = staticCompositionLocalOf<FeedbackDispatcher> {
    error(
        "No FeedbackDispatcher found. Wrap your app in KontourTheme { … }, which " +
            "installs one."
    )
}

/** Shorthand for `LocalFeedback.current`. */
val Feedback: FeedbackDispatcher
    @Composable @ReadOnlyComposable get() = LocalFeedback.current

/**
 * The default mapping from intent to platform haptic.
 *
 * [FeedbackIntent.Selection] deliberately maps to `ToggleOn`/`ToggleOff`'s
 * sibling `SegmentTick` rather than to `LongPress`: selection should feel like a
 * detent, not like a thud. The heavier `LongPress` is reserved for the one case
 * that means it.
 */
@Composable
internal fun rememberDefaultFeedbackDispatcher(): FeedbackDispatcher {
    val haptics: HapticFeedback = LocalHapticFeedback.current
    // No `expect`/`actual` here, deliberately, and it is worth writing down
    // because the shape of the code invites one. `HapticFeedbackType` is a
    // *common* Compose type, and each platform's `LocalHapticFeedback` already
    // resolves it natively: on iOS `CupertinoHapticFeedback` routes these onto
    // `UIImpactFeedbackGenerator`, `UISelectionFeedbackGenerator.selectionChanged`
    // and `UINotificationFeedbackGenerator` with Success and Error types. The
    // names below read as Android's `HapticFeedbackConstants` because that is
    // where the vocabulary came from, not because that is where it goes. A
    // platform seam added here would duplicate the toolkit's, and would be worse
    // than it.
    return remember(haptics) {
        FeedbackDispatcher { intent ->
            haptics.performHapticFeedback(
                when (intent) {
                    FeedbackIntent.Selection -> HapticFeedbackType.SegmentTick
                    FeedbackIntent.Tick -> HapticFeedbackType.SegmentFrequentTick
                    FeedbackIntent.Confirm -> HapticFeedbackType.Confirm
                    FeedbackIntent.Reject -> HapticFeedbackType.Reject
                    FeedbackIntent.LongPress -> HapticFeedbackType.LongPress
                    FeedbackIntent.DragThreshold -> HapticFeedbackType.GestureThresholdActivate
                    FeedbackIntent.GestureEnd -> HapticFeedbackType.GestureEnd
                    FeedbackIntent.KeyPress -> HapticFeedbackType.KeyboardTap
                }
            )
        }
    }
}

/**
 * A dispatcher that does nothing.
 *
 * For tests, for screenshot rendering, and for honouring an in-app "haptics off"
 * setting.
 */
val NoFeedback: FeedbackDispatcher = FeedbackDispatcher { }
