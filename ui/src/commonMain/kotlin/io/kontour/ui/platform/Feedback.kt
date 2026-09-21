package io.kontour.ui.platform

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.kontour.ui.interaction.FeedbackFeel

/**
 * The constant a [FeedbackFeel] is performed with, which is not the same everywhere.
 *
 * ### What crosses this seam, and what deliberately does not
 *
 * A *feel* crosses it. A [io.kontour.ui.interaction.FeedbackIntent] does not, and
 * that is the test of whether the split is real: an intent is what happened, which
 * is a decision about the library, and it is made once in common by
 * `FeedbackIntent.feel`. What is left here is a capability table — what this
 * platform has for each of five feels — which is the only kind of thing
 * `platform/` is allowed to hold.
 *
 * ### Why the seam exists at all
 *
 * `rememberDefaultFeedbackDispatcher` says, at length, that `HapticFeedbackType` is
 * a common Compose type each platform's `LocalHapticFeedback` already resolves
 * natively, so a mapping written in common is not a mapping that ignores the
 * platform. That holds for every value whose platforms want the *same* constant.
 *
 * This file used to be that argument applied twice, one value at a time:
 * `platformTickHaptic` earned a seam by measurement, `platformTapHaptic` earned a
 * second one by the same shape of argument, and the file's own KDoc said a third
 * "should have to argue for itself the way this one does". Two values in, the
 * pattern was clear enough to name: the thing that differs per platform is never
 * the intent, it is **how light the platform can go**. So the two values became one
 * function of a feel, and the argument is made once.
 *
 * ### What each platform answers, and why
 *
 * | | [FeedbackFeel.Light] | [FeedbackFeel.Medium] | [FeedbackFeel.Heavy] |
 * |---|---|---|---|
 * | iOS | `SegmentTick` — `selectionChanged()`, the picker tick | `GestureThresholdActivate` — light impact | `LongPress` — medium impact |
 * | Android | `SegmentTick` on 34+, else `TextHandleMove` (API 27, under `minSdk`) | `VirtualKey` — `EFFECT_CLICK` | `LongPress` |
 * | Web | 20ms; 12ms is unmeasured and 6ms is silence | the same 20ms, said plainly | 30ms |
 * | Desktop | no motor; the handler returns immediately whatever this says | | |
 *
 * [FeedbackFeel.Success] and [FeedbackFeel.Danger] are rhythms rather than weights,
 * and every platform has both: `Confirm`/`Reject` on Android from API 30 and in
 * Compose's own mapping elsewhere. Android's two fall back rather than going silent
 * on API 29, which is this library's floor and one release below those constants.
 */
internal expect fun platformHapticFor(feel: FeedbackFeel): HapticFeedbackType
