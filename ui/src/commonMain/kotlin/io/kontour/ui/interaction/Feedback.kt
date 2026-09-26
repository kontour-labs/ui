package io.kontour.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.kontour.haptics.Haptics
import io.kontour.haptics.scaled

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
    /**
     * A value was chosen. Nothing in the library performs it: a toggle reports
     * [ToggleOn] or [ToggleOff], a radio, a segment or a swatch a [Tap], and a row
     * that changes place a [Snap]. Kept for a dispatcher or a component of your
     * own that has a choice to report and no better word for it.
     */
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

    /**
     * A control without steps is being dragged: a slider or a knob moving
     * continuously under a finger.
     *
     * A texture rather than a detent — there is nothing to cross, so what it
     * reports is the movement itself: faint grains a little way apart, closer and
     * firmer the faster the value is moving, and nothing while it is still. Each
     * grain is performed at a strength of its own
     * ([FeedbackDispatcher.perform] with a strength), which is how the speed
     * reaches the hand. Rate-limited with [Tick], and dropped under
     * [HapticsLevel.Reduced], with the rest of the running commentary.
     */
    Scrub,

    /**
     * A drag passed a place the thing will come to rest: a sheet's detent, a
     * carousel's page, a tab, a segment, a row's new slot.
     *
     * Where [Tick] is a texture — a slider has a hundred of them — this is a
     * step, a few large ones, and it is felt as one: Android's own pair is
     * `SEGMENT_FREQUENT_TICK` and `SEGMENT_TICK`, and this is the second. Not
     * rate-limited, for the reason [Selection] never was: a row the hand did not
     * feel move is a row the eye has to go looking for.
     */
    Snap,

    /**
     * A switch, a checkbox or a filter turned **on** by a press.
     *
     * Its own intent rather than a [Tap] because on and off are different
     * events, and feel it — Android 14 has a constant for each. A switch
     * *dragged* across its middle reports a [DragThreshold] instead, which is
     * what that moment is.
     */
    ToggleOn,

    /** The same, turned **off** — softer than [ToggleOn]. */
    ToggleOff,

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
     * Coming back out of it is [DragThresholdBack], which is softer: the thing
     * letting go would have done is off again, and that matters less than it
     * being on. A sheet passing its detents is a [Snap], not this.
     */
    DragThreshold,

    /**
     * A drag came back out of a [DragThreshold]: letting go will no longer do
     * what it would have. Softer than going in — Android 14's pair is
     * `GESTURE_THRESHOLD_ACTIVATE` and `…_DEACTIVATE`, and this is the second.
     */
    DragThresholdBack,

    /**
     * A drag ran into the end of its range: a slider, a knob or a colour track
     * pushed against its stop. A dull knock rather than a click, because a wall
     * is not a choice.
     */
    Limit,

    /**
     * Two parts of one control met: a range slider's thumb brought up against
     * the other one.
     *
     * Lighter than a [Limit], because it is not a wall — the other thumb gives,
     * and is shoved along — but a meeting the hand should feel happen, once, and
     * not again until the two have come apart. Not rate-limited, so the step
     * that brought them together cannot swallow it; dropped under
     * [HapticsLevel.Reduced], where the shove on screen is the report.
     */
    Bump,

    /**
     * A hold is under way: keep holding and something will happen.
     *
     * Not performed but *sustained* — [FeedbackDispatcher.sustain], through a
     * [HoldFeedback] — as a faint, continuous rumble that builds as the hold
     * nears its end, so the hand knows the wait is counting and not stuck. The
     * first use is a date range's handle held past the month's first or last
     * day, where the month pages when the ring fills, with a [DragThreshold] as
     * it does. Dropped under [HapticsLevel.Reduced] with the other intents that
     * report progress rather than an outcome.
     *
     * It used to be a pulse of the lightest tick every 70ms, which is the
     * nearest Compose's feedback constants came to a rumble and was felt as a
     * rattle.
     */
    Hold,

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
 * Which intent gets which is [FeedbackIntent.feel]. The theme's own dispatcher
 * no longer plays feels — it plays [FeedbackIntent.defaultEffect], one effect per
 * intent from the `:haptics` module, which is finer than five tiers can be — but
 * the tiers are still the policy in words, and a dispatcher of your own that
 * wants only a weight can still ask for one.
 */
enum class FeedbackFeel {
    /**
     * A texture going past: a detent crossed, a row of a drum, a slider's grain.
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
 * - [FeedbackIntent.Snap] is a press. A resting place passed — a row visibly
 *   changing place, a card, a sheet's detent — is a change, not a texture, which
 *   is also what keeps the drop ([FeedbackIntent.GestureEnd]) lighter than the
 *   reorders it follows.
 * - [FeedbackIntent.Warn] and [FeedbackIntent.Reject] share [FeedbackFeel.Danger],
 *   which is the sharing the two have always had, stated once instead of as two
 *   coincidental branches. They remain two intents so a consumer can pull them
 *   apart.
 */
val FeedbackIntent.feel: FeedbackFeel
    get() = when (this) {
        FeedbackIntent.Tick -> FeedbackFeel.Light
        FeedbackIntent.Scrub -> FeedbackFeel.Light
        FeedbackIntent.Bump -> FeedbackFeel.Light
        // A dragged row's landing: softer than the reorders it follows.
        FeedbackIntent.GestureEnd -> FeedbackFeel.Light
        // A rumble is a stream of the faintest pulse the platform has.
        FeedbackIntent.Hold -> FeedbackFeel.Light
        // Performed by nothing in the library. Assigned anyway, because an intent
        // without a feel is one a replacement dispatcher cannot place — and the
        // `when` is exhaustive, which is what stops the next intent being added
        // without this decision being made.
        FeedbackIntent.KeyPress -> FeedbackFeel.Light

        FeedbackIntent.ToggleOff -> FeedbackFeel.Light
        FeedbackIntent.DragThresholdBack -> FeedbackFeel.Light

        FeedbackIntent.Tap -> FeedbackFeel.Medium
        FeedbackIntent.Snap -> FeedbackFeel.Medium
        FeedbackIntent.ToggleOn -> FeedbackFeel.Medium
        FeedbackIntent.Selection -> FeedbackFeel.Medium
        FeedbackIntent.DragThreshold -> FeedbackFeel.Medium
        FeedbackIntent.Limit -> FeedbackFeel.Medium

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
     * Outcomes only — a confirmation, a refusal, a threshold crossed either
     * way, an end stop, a long press. The ones that report *progress* — a press
     * answered ([FeedbackIntent.Tap], [FeedbackIntent.ToggleOn],
     * [FeedbackIntent.ToggleOff]), a detent or a resting place going past
     * ([FeedbackIntent.Tick], [FeedbackIntent.Snap], [FeedbackIntent.Selection]),
     * a slider's texture ([FeedbackIntent.Scrub]), two thumbs meeting
     * ([FeedbackIntent.Bump]), a key, a hold's rumble, a drop settling
     * ([FeedbackIntent.GestureEnd]) — are dropped, so a drag still reports
     * arriving somewhere without buzzing the whole way there.
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
            FeedbackIntent.ToggleOn,
            FeedbackIntent.ToggleOff,
            FeedbackIntent.Tick,
            FeedbackIntent.Scrub,
            FeedbackIntent.Snap,
            FeedbackIntent.Selection,
            FeedbackIntent.KeyPress,
            FeedbackIntent.Hold,
            FeedbackIntent.GestureEnd,
            FeedbackIntent.Bump,
            -> false

            else -> true
        }
    }
}

/**
 * Performs physical feedback for a [FeedbackIntent].
 *
 * Obtained from [LocalFeedback]; on a device without haptics the player
 * underneath plays nothing, so components do not need to check.
 */
@Stable
fun interface FeedbackDispatcher {
    /** Plays [intent] once. */
    fun perform(intent: FeedbackIntent)

    /**
     * Plays [intent] once at [strength] times its usual strength, 0 to 1 — a
     * [FeedbackIntent.Scrub] grain, firmer the faster the drag.
     *
     * The default ignores the strength and performs it as usual, so a dispatcher
     * written before this existed, and every one-line test lambda, still hears
     * each grain.
     */
    fun perform(intent: FeedbackIntent, strength: Float) = perform(intent)

    /**
     * Starts [intent] as feedback that lasts — a hold's rumble — and returns the
     * handle that follows its progress and stops it.
     *
     * The default performs it once and returns [SustainedFeedback.None], so a
     * dispatcher written before this existed, and every one-line test lambda,
     * still hears a hold begin. Components go through [HoldFeedback] rather than
     * calling this, which is what guarantees the stop.
     */
    fun sustain(intent: FeedbackIntent): SustainedFeedback {
        perform(intent)
        return SustainedFeedback.None
    }
}

/** Feedback that lasts, started by [FeedbackDispatcher.sustain]. */
@Stable
interface SustainedFeedback {
    /** How far through it is, 0 to 1 — a rumble builds with it. */
    fun update(progress: Float)

    /** Ends it. Ending twice is fine. */
    fun stop()

    companion object {
        /** Feedback that was never going to last: nothing to update, nothing to stop. */
        val None: SustainedFeedback = object : SustainedFeedback {
            override fun update(progress: Float) = Unit
            override fun stop() = Unit
        }
    }
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
 * The theme's dispatcher: each intent's [FeedbackIntent.defaultEffect], played on
 * [player], if [level] allows it.
 *
 * **This used to hand Compose a `HapticFeedbackType`**, by way of five weights,
 * and that was as fine as it could get: Compose's feedback is the platform's
 * fixed constants, so there was no softer tick than the lightest constant, no
 * strength at all, and no rumble — the dwell's was a tick every 70ms. The
 * `:haptics` module plays the platforms' own tuned effects at a strength, and
 * continuously where they can, so the policy here is one effect per intent
 * instead of one of five tiers.
 *
 * The level filter still runs first, so a dropped intent reaches no player at
 * all; the rate floor still lives in the helpers that stream.
 */
@Composable
internal fun rememberDefaultFeedbackDispatcher(
    level: HapticsLevel = HapticsLevel.Standard,
    player: Haptics = rememberHaptics(),
): FeedbackDispatcher = remember(player, level) { DefaultFeedbackDispatcher(player, level) }

private class DefaultFeedbackDispatcher(
    private val player: Haptics,
    private val level: HapticsLevel,
) : FeedbackDispatcher {

    override fun perform(intent: FeedbackIntent) {
        if (level.allows(intent)) player.play(intent.defaultEffect)
    }

    override fun perform(intent: FeedbackIntent, strength: Float) {
        if (level.allows(intent)) player.play(intent.defaultEffect.scaled(strength.coerceIn(0f, 1f)))
    }

    override fun sustain(intent: FeedbackIntent): SustainedFeedback {
        if (!level.allows(intent)) return SustainedFeedback.None
        val rumble = intent.defaultRumble ?: return super.sustain(intent)
        val playing = player.startRumble(rumble.intensityAt(0f), rumble.sharpness)
        return object : SustainedFeedback {
            override fun update(progress: Float) = playing.update(rumble.intensityAt(progress), rumble.sharpness)
            override fun stop() = playing.stop()
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
 * A toggle answered, rate-limited like a tap: call it with the value the control
 * is changing *to*.
 *
 * ```kotlin
 * val toggled = rememberToggleFeedback()
 * Checkbox(checked = on, onCheckedChange = { toggled(it); onCheckedChange(it) })
 * ```
 *
 * [FeedbackIntent.ToggleOn] or [FeedbackIntent.ToggleOff], through the same
 * shared floor as [rememberTapFeedback] — a hand running down a column of
 * checkboxes feels one rattle, not one per box — and silent at
 * [HapticsLevel.Reduced] and below.
 */
@Composable
fun rememberToggleFeedback(): (Boolean) -> Unit {
    val feedback = LocalFeedback.current
    val floor = LocalFeedbackFloor.current
    return remember(feedback, floor) {
        { on ->
            val intent = if (on) FeedbackIntent.ToggleOn else FeedbackIntent.ToggleOff
            if (floor.claim(intent)) feedback.perform(intent)
        }
    }
}

/**
 * A press held to its long-press threshold, ready to call from a gesture
 * handler: [FeedbackIntent.LongPress], which no level short of `Off` drops.
 *
 * One helper for the four places that report one — a context menu, a touch
 * tooltip, a row picked up to reorder, a calendar scrubbed — for the reason the
 * tap helper gives: one call site, rather than one each.
 */
@Composable
internal fun rememberLongPressFeedback(): () -> Unit {
    val feedback = LocalFeedback.current
    return remember(feedback) { { feedback.perform(FeedbackIntent.LongPress) } }
}

/**
 * A dispatcher that does nothing.
 *
 * For tests, for screenshot rendering, and for honouring an in-app "haptics off"
 * setting.
 */
val NoFeedback: FeedbackDispatcher = FeedbackDispatcher { }
