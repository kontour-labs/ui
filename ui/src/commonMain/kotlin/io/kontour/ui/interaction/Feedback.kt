package io.kontour.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import io.kontour.ui.platform.platformHapticFor

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
     * A control acknowledged a press.
     *
     * **Not [Selection], and the four levels are why.** "A detent crossed under
     * a finger" and "the thing you pressed answered you" are different enough
     * that somebody should be able to keep one and drop the other, and an intent
     * that means both cannot be filtered apart.
     *
     * **This used to claim to be "the lightest thing the device can do", and it
     * was not.** Below Android 14 it and [Tick] were the same `VirtualKey` pulse;
     * on Android 14 and up it was *lighter* than [Tick]; on iOS the two were the
     * same generator. Three platforms, three different orderings, from one
     * sentence that read like a policy. [FeedbackFeel] is where the ordering
     * lives now, and a press is [FeedbackFeel.Medium] — a step above the texture
     * of a detent going past, because a control answering is one event and a
     * detent is a stream of them.
     *
     * Shares [Tick]'s rate floor rather than having its own. A hand feels one
     * rattle, not one per component.
     */
    Tap,

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
 * What the hand gets: three pulses of increasing weight, and two rhythms.
 *
 * A [FeedbackIntent] says what *happened*. This says how much of the hand that is
 * worth — and it exists because nothing did, so each platform's own actual chose a
 * constant per intent independently and the orderings disagreed. Reported from a
 * phone in those words: *"all the haptics feel heavy, there doesn't seem to be the
 * concept of a soft interaction for anything"*. On Android below 14 that was
 * literally true — [FeedbackIntent.Tick] and [FeedbackIntent.Tap] were the same
 * `VirtualKey` click, which is the platform's *middle* weight, so every detent in
 * the library was firing at the weight a button press wants.
 *
 * **The three weights are a scale and are declared lightest first.** The two
 * rhythms are not on it: asking whether [Danger] is heavier than [Heavy] has no
 * answer, because one is a bigger pulse and the other is several beats that mean
 * something. That is the whole reason this type is not called a weight.
 *
 * Which intent gets which is [FeedbackIntent.feel], and it is the one place the
 * assignment lives. Each platform then answers with the closest thing it actually
 * has — see `io.kontour.ui.platform.platformHapticFor`, which is a capability
 * table per platform and not a second opinion about policy.
 */
enum class FeedbackFeel {
    /**
     * A texture going past: a detent crossed, a row of a drum, a page snapping.
     *
     * The tier that did not exist. It arrives in *streams* — a flung wheel
     * crosses a row every 8ms — which is why it is also the tier the shared rate
     * floor gates. See [DetentTicker].
     */
    Light,

    /** A control answering, or what letting go will do changing. One event. */
    Medium,

    /** A threshold held long enough to mean something. */
    Heavy,

    /** It worked. A rhythm, not a pulse. */
    Success,

    /** It was refused, or it is about to be irreversible. A rhythm. */
    Danger,
}

/**
 * How hard [this] should feel. The one place the assignment lives.
 *
 * Public because replacing [LocalFeedback] with your own dispatcher is a supported
 * thing to do, and a dispatcher that cannot ask how hard an intent is meant to be
 * has to re-derive the whole policy from the intent names.
 *
 * The four arguable rows, written down because they were argued:
 *
 * - [FeedbackIntent.Tick] is lighter than [FeedbackIntent.Tap]. A detent is a
 *   surface going past under a finger and a press is the control answering once.
 * - [FeedbackIntent.DragThreshold] is a press rather than a thud. It is the most
 *   consequential moment in a gesture, but a switch's midpoint can be crossed
 *   back and forth under one finger and the heaviest tier would be too much for
 *   that.
 * - [FeedbackIntent.Selection] is a press. A reorderable row visibly changing
 *   place is a change, not a texture — which is also what keeps the drop
 *   ([FeedbackIntent.Tick]) lighter than the reorders it follows.
 * - [FeedbackIntent.Warn] and [FeedbackIntent.Reject] share [FeedbackFeel.Danger],
 *   which is the sharing the two have always had, stated once instead of as two
 *   coincidental branches. They remain two intents so a consumer can pull them
 *   apart.
 */
val FeedbackIntent.feel: FeedbackFeel
    get() = when (this) {
        FeedbackIntent.Tick -> FeedbackFeel.Light
        // Neither is performed anywhere in the library. Assigned anyway, because
        // an intent without a feel is an intent a replacement dispatcher cannot
        // place — and because the `when` is exhaustive, which is what stops the
        // next intent being added without this decision being made.
        FeedbackIntent.GestureEnd -> FeedbackFeel.Light
        FeedbackIntent.KeyPress -> FeedbackFeel.Light

        FeedbackIntent.Tap -> FeedbackFeel.Medium
        FeedbackIntent.Selection -> FeedbackFeel.Medium
        FeedbackIntent.DragThreshold -> FeedbackFeel.Medium

        FeedbackIntent.LongPress -> FeedbackFeel.Heavy

        FeedbackIntent.Confirm -> FeedbackFeel.Success
        FeedbackIntent.Reject -> FeedbackFeel.Danger
        FeedbackIntent.Warn -> FeedbackFeel.Danger
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
 * unpleasant. [Reduced] keeps the feedback that reports an *outcome* and drops
 * the feedback that reports *progress*.
 */
enum class HapticsLevel {
    /** Nothing. For a kiosk, a test, or a user who has asked for silence. */
    Off,

    /**
     * Outcomes only — a confirmation, a refusal, a threshold crossed, a long
     * press. The ones that report *progress* ([FeedbackIntent.Tap],
     * [FeedbackIntent.Tick], [FeedbackIntent.Selection],
     * [FeedbackIntent.KeyPress]) are dropped, so a drag still reports arriving
     * somewhere without buzzing the whole way there.
     *
     * This was called `Essential` and is the same set, less the new
     * [FeedbackIntent.Tap].
     */
    Reduced,

    /**
     * Everything a control does under a finger. **The default.**
     *
     * Taps, detents, thresholds and outcomes. What it leaves out is
     * [FeedbackIntent.KeyPress], which is the one intent nothing in the library
     * performs and the only one that is decorative rather than reporting
     * something a reader could not otherwise tell.
     */
    Standard,

    /** Every intent, [FeedbackIntent.KeyPress] included. */
    Full,
    ;

    internal fun allows(intent: FeedbackIntent): Boolean = when (this) {
        Full -> true
        Off -> false
        Standard -> intent != FeedbackIntent.KeyPress
        Reduced -> when (intent) {
            FeedbackIntent.Tap,
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
 * Two steps, and the split is the point. An intent is turned into a
 * [FeedbackFeel] here, in common, by [FeedbackIntent.feel] — that is the policy,
 * and it is the same on every platform. The feel is then turned into a constant by
 * `platformHapticFor`, per platform — that is a capability table, and it differs
 * because the platforms differ.
 *
 * It used to be one step, and the step carried both. `HapticFeedbackType` is a
 * *common* Compose type and each platform's `LocalHapticFeedback` already resolves
 * it natively — on iOS `CupertinoHapticFeedback` routes these onto
 * `UIImpactFeedbackGenerator`, `UISelectionFeedbackGenerator.selectionChanged` and
 * `UINotificationFeedbackGenerator` — so the mapping was written once, in common,
 * and the names read as Android's `HapticFeedbackConstants` because that is where
 * the vocabulary came from rather than where it goes.
 *
 * That was right about the mechanism and wrong about the consequence. Two intents
 * had already needed a platform seam of their own, each arguing its case at
 * length; and on Android what the two of them resolved to was the *same constant*
 * on anything below API 34. So the library had a two-tier intent vocabulary and a
 * one-tier result, and the report from a phone was that everything felt heavy.
 * Naming the tier is what fixes that, because a platform can then be asked for its
 * lightest rather than for a constant somebody else measured.
 *
 * ### What each feel actually does, per platform
 *
 * Measured rather than assumed. The web column is the `navigator.vibrate` pattern
 * in milliseconds; iOS is the generator `CupertinoHapticFeedback` routes to; the
 * Android column names the constant and the API level it arrived in.
 *
 * | Feel | Web | iOS | Android |
 * |---|---|---|---|
 * | [FeedbackFeel.Light] | 0, 20ms | **selection tick** | `SegmentTick` (**34**), else `TextHandleMove` (27) |
 * | [FeedbackFeel.Medium] | 0, 20ms | light impact | `VirtualKey` (5) |
 * | [FeedbackFeel.Heavy] | 0, 30ms | medium impact | `LongPress` (3) |
 * | [FeedbackFeel.Success] | 18, 32, 36ms | notification, success | `Confirm` (30), else `VirtualKey` |
 * | [FeedbackFeel.Danger] | 18, 28, 18, 28, 18ms | notification, error | `Reject` (30), else `LongPress` |
 *
 * ### Why Android's middle tier is the constant it is
 *
 * Because `VirtualKey` was never the wrong constant — it was in the wrong row.
 * The round-25 measurement that put the detent tick on it was a **web**
 * measurement: `SegmentFrequentTick` is 6ms there, a vibration motor needs roughly
 * 10–20ms to spin up far enough to be felt, and every detent in the library was
 * issuing a pulse that reached nobody. `VirtualKey` is 20ms and is felt, and that
 * finding stands.
 *
 * What it did not settle is where 20ms *sits*. On Android `VirtualKey` is
 * `EFFECT_CLICK` — a full key click, the weight a button press wants — so using it
 * for a stream of detents was asking for a press per row of a drum. The lighter
 * constants were there all along and unmentioned: `TextHandleMove` has existed
 * since API 27, which is below this library's own `minSdk`, so **no device it runs
 * on lacks a light tier**.
 *
 * ### There is no lighter tier that is still felt — on the web, still
 *
 * The web's patterns below 20ms are 12ms and 6ms, and 6ms is the silence the
 * round-25 measurement was about. 12ms has never been measured either way, so the
 * web answers [FeedbackFeel.Light] and [FeedbackFeel.Medium] with the same 20ms
 * pulse and the table above says so rather than implying a scale it does not have.
 * `docs/measure-web.mjs --vibration` is what would settle it.
 *
 * The **rate** limit stays on every platform and is the part that was never
 * platform-specific: a flung wheel crosses a row every 8ms, and no constant soft
 * enough to survive that is a constant at all. See [DetentTicker].
 *
 * ### The gap this closes, and the one it leaves
 *
 * [FeedbackIntent.DragThreshold] was on `GestureThresholdActivate`, an API-34
 * constant, so a pull-to-refresh threshold was **silent on Android 13 and below** —
 * a hole this file used to name and could not fix without moving the intent. It is
 * [FeedbackFeel.Medium] now and answers on every supported release.
 *
 * [FeedbackIntent.KeyPress] still does nothing at all on iOS, which is to say it is
 * decorative — nothing in the library performs it.
 */
@Composable
internal fun rememberDefaultFeedbackDispatcher(
    level: HapticsLevel = HapticsLevel.Standard,
): FeedbackDispatcher {
    val haptics: HapticFeedback = LocalHapticFeedback.current
    return remember(haptics, level) {
        FeedbackDispatcher { intent ->
            if (!level.allows(intent)) return@FeedbackDispatcher
            haptics.performHapticFeedback(platformHapticFor(intent.feel))
        }
    }
}

/**
 * A press acknowledged, rate-limited, ready to call from a click handler.
 *
 * ```kotlin
 * val tap = rememberTapFeedback()
 * Checkbox(checked = on, onCheckedChange = { tap(); onCheckedChange(it) })
 * ```
 *
 * **One helper rather than a `perform` at each site**, and the reason is not
 * tidiness. Every light haptic in a composition shares one rate floor, so a
 * chip row answering three taps in quick succession is one tick and not three;
 * putting the call behind a single function is what makes that true by
 * construction rather than by each caller remembering. It is also the only
 * reason a dozen components can report a tap without the audit's ceiling on
 * haptic call sites moving, which is a ceiling worth keeping.
 *
 * Silent at [HapticsLevel.Reduced] and below, which is the level's whole point.
 */
@Composable
fun rememberTapFeedback(): () -> Unit {
    val feedback = LocalFeedback.current
    val floor = LocalFeedbackFloor.current
    return remember(feedback, floor) {
        { if (floor.claim(FeedbackIntent.Tap)) feedback.perform(FeedbackIntent.Tap) }
    }
}

/**
 * A dispatcher that does nothing.
 *
 * For tests, for screenshot rendering, and for honouring an in-app "haptics off"
 * setting.
 */
val NoFeedback: FeedbackDispatcher = FeedbackDispatcher { }
