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

    /**
     * A discrete step was crossed: a slider tick, a picker detent, a segment.
     *
     * Every component that performs this goes through [DetentTicker], which is
     * both where the "once per crossing, not once per frame" guard lives and
     * where the rate limit does.
     *
     * It used to say "a stepper" as well, and no `Stepper` has ever performed
     * it. That is deliberate rather than missing: a stepper is two buttons and a
     * number that changes where you are already looking, and the rule the
     * round-25 audit settled on is that a haptic reports something the user
     * could not otherwise tell. The doc was the thing that was wrong.
     */
    Tick,

    /** An action succeeded. Sparingly — not on every button. */
    Confirm,

    /** An action was refused: invalid input, a blocked gesture. */
    Reject,

    /**
     * Something with consequences is being *asked*, and has not happened yet.
     *
     * A destructive confirmation opening is the case it exists for. Distinct
     * from [Reject], which reports that something was refused, and from
     * [Confirm], which reports that something worked: this one is the only
     * intent in the list that fires *before* the thing it is about. The two
     * share a platform constant today — a notification-style buzz is what both
     * want — and they are still two intents, because a consumer retuning the
     * mapping should be able to make "you are about to delete this" feel
     * different from "that did not work".
     */
    Warn,

    /** A long-press threshold was reached and something is about to happen. */
    LongPress,

    /**
     * A drag threshold was crossed: a row passing its action point, a
     * pull-to-refresh passing the point where letting go will refresh.
     *
     * What has changed is *what letting go will do*, and nothing on screen
     * necessarily said so first.
     *
     * This used to name a sheet snapping too, and no sheet performs it. The
     * reason is worth keeping rather than the claim: `SheetState.targetDetent`
     * is exactly the right signal — it "changes the instant a drag passes the
     * threshold" — but nothing distinguishes that from the same field changing
     * because code called `animateTo`. A sheet that buzzes when it is opened
     * programmatically is worse than one that is silent, so this waits for a
     * drag signal the sheet does not currently expose.
     */
    DragThreshold,

    /** A gesture completed and the element settled. */
    GestureEnd,

    /** A key or on-screen key was struck. */
    KeyPress,
}

/**
 * How much physical feedback the theme asks for.
 *
 * Replacing [LocalFeedback] with your own dispatcher has always been possible
 * and is still the way to retune the *mapping*. This is the other question —
 * how much of it there should be — and it did not have an answer short of
 * writing a dispatcher to answer it.
 *
 * It is a real setting rather than a debug switch. Continuous feedback is the
 * kind most likely to be unwelcome: a slider ticking through forty detents is
 * delightful once and wearing on a long form, and some users find it actively
 * unpleasant. [Essential] keeps the feedback that reports an *outcome* and drops
 * the feedback that reports *progress*.
 */
enum class HapticsLevel {
    /** Every intent. The default. */
    Full,

    /**
     * Outcomes only — a confirmation, a refusal, a threshold crossed, a long
     * press. The continuous ones ([FeedbackIntent.Tick],
     * [FeedbackIntent.Selection], [FeedbackIntent.KeyPress]) are dropped, so a
     * drag still reports arriving somewhere without buzzing the whole way there.
     */
    Essential,

    /** Nothing. For a kiosk, a test, or a user who has asked for silence. */
    Off,
    ;

    internal fun allows(intent: FeedbackIntent): Boolean = when (this) {
        Full -> true
        Off -> false
        Essential -> when (intent) {
            FeedbackIntent.Tick,
            FeedbackIntent.Selection,
            FeedbackIntent.KeyPress,
            -> false

            else -> true
        }
    }
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
 * ### What each constant actually does, per platform
 *
 * Measured rather than assumed, because the names are Android's and two of them
 * turn out to be unreachable on most of the devices this library runs on. The
 * web column is the `navigator.vibrate` pattern in milliseconds; iOS is the
 * generator `CupertinoHapticFeedback` routes to; the Android column is the API
 * level the `HapticFeedbackConstants` value was added in.
 *
 * | Intent | Constant | Web | iOS | Android |
 * |---|---|---|---|---|
 * | [FeedbackIntent.Tick] | `VirtualKey` | 0, 20ms | light impact | 5 |
 * | [FeedbackIntent.Selection] | `ContextClick` | 12ms | medium impact | 23 |
 * | [FeedbackIntent.DragThreshold] | `GestureThresholdActivate` | 12ms | light impact | **34** |
 * | [FeedbackIntent.LongPress] | `LongPress` | 0, 30ms | medium impact | 3 |
 * | [FeedbackIntent.GestureEnd] | `GestureEnd` | 12ms | light impact | 30 |
 * | [FeedbackIntent.Confirm] | `Confirm` | 18, 32, 36ms | notification, success | 30 |
 * | [FeedbackIntent.Reject], [FeedbackIntent.Warn] | `Reject` | 18, 28, 18, 28, 18ms | notification, error | 30 |
 * | [FeedbackIntent.KeyPress] | `KeyboardTap` | 6ms | **nothing** | 8 |
 *
 * ### Why [FeedbackIntent.Tick] moved off `SegmentFrequentTick`
 *
 * Because it was never felt. `SegmentFrequentTick` is 6ms on the web, and **a
 * vibration motor needs roughly 10–20ms to spin up far enough to be felt at
 * all** — so every detent in the library issued a pulse that reached nothing.
 * Measured on the built site with `docs/measure-web.mjs --vibration`: a stepped
 * slider dragged across its range produced `3 x [6]`, eighteen milliseconds of
 * motor time for a whole gesture. Meanwhile `LongPress` is 30ms and was being
 * felt, which is exactly the shape the report took — the long presses are
 * enjoyed and the detents do nothing.
 *
 * It is not better on the other two. `SegmentFrequentTick` and `SegmentTick`
 * are the *same* `selectionChanged()` generator on iOS, so the two intents were
 * indistinguishable there; and both are `HapticFeedbackConstants` added in API
 * 34, so on any Android below 14 they do nothing whatsoever.
 *
 * `VirtualKey` is the one constant that is above the motor floor on the web, a
 * distinct generator from [FeedbackIntent.Selection] on iOS, and available back
 * to API 5 on Android.
 *
 * ### There is no lighter tier that is still felt
 *
 * The wheel picker wants a *finer* tick than a slider does, and the obvious
 * shape for that is a second intent mapped to something lighter. There is
 * nothing to map it to: below `VirtualKey` the web patterns are 12ms and 6ms,
 * and 6ms is the silence this whole change is about. A "light" intent would
 * reintroduce the bug on the one component that fires most often.
 *
 * So the wheel gets the same tick as everything else and is quietened by
 * **rate** instead — see [DetentTicker]. On the web an intensity scale does not
 * exist; there is felt and not felt.
 *
 * ### The gaps this leaves, named rather than hidden
 *
 * [FeedbackIntent.DragThreshold] is still on an API-34 constant, so a pull-to-
 * refresh threshold is silent on Android 13 and below. It is 12ms on the web and
 * light impact on iOS, so it clears the floor on the two platforms this round
 * measured; the Android gap is real and unfixed. [FeedbackIntent.KeyPress] does
 * nothing at all on iOS and is 6ms on the web, which is to say it is decorative
 * — nothing in the library performs it.
 */
@Composable
internal fun rememberDefaultFeedbackDispatcher(
    level: HapticsLevel = HapticsLevel.Full,
): FeedbackDispatcher {
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
    return remember(haptics, level) {
        FeedbackDispatcher { intent ->
            if (!level.allows(intent)) return@FeedbackDispatcher
            haptics.performHapticFeedback(
                when (intent) {
                    FeedbackIntent.Selection -> HapticFeedbackType.ContextClick
                    FeedbackIntent.Tick -> HapticFeedbackType.VirtualKey
                    FeedbackIntent.Confirm -> HapticFeedbackType.Confirm
                    FeedbackIntent.Reject -> HapticFeedbackType.Reject
                    FeedbackIntent.Warn -> HapticFeedbackType.Reject
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
