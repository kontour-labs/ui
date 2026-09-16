package io.kontour.ui.platform

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * The constant a detent tick is performed with, which is not the same everywhere.
 *
 * ### Why this is the one feedback value with a platform seam
 *
 * `rememberDefaultFeedbackDispatcher` says, at length, that there is deliberately
 * no `expect`/`actual` in the feedback mapping: `HapticFeedbackType` is a common
 * Compose type and each platform's `LocalHapticFeedback` already resolves it
 * natively, so a seam here would duplicate the toolkit's own.
 *
 * That argument holds for every intent whose platforms want the *same* constant.
 * It breaks for this one, because they do not.
 *
 * `FeedbackIntent.Tick` moved onto `VirtualKey` after a measurement, and the
 * measurement was a **web** measurement: `SegmentFrequentTick` is 6ms there, a
 * vibration motor needs roughly 10-20ms to spin up far enough to be felt, and
 * every detent in the library was issuing a pulse that reached nobody.
 * `VirtualKey` is 20ms and is felt. Nothing about that has changed.
 *
 * What it settled by accident is iOS, where the constraint is the opposite one.
 * There is no motor floor to clear; `VirtualKey` routes to an **impact**
 * generator, and an impact per detent on a wheel spun with a thumb is the
 * "punchy" complaint arriving from the other direction. Apple built a generator
 * for exactly this case — `UISelectionFeedbackGenerator.selectionChanged()`, the
 * one under the system's own pickers — and it is the lightest thing on the
 * device. `SegmentTick` reaches it.
 *
 * So the tick is soft where a soft tick is available and still felt, and
 * unchanged where the round-25 measurement still applies. Every other intent
 * stays common, and this file is deliberately one value wide: a second entry
 * here should have to argue for itself the way this one does.
 *
 * ### What each platform answers, and why
 *
 * | | | |
 * |---|---|---|
 * | iOS | `SegmentTick` | `selectionChanged()`, the picker tick |
 * | Android | `VirtualKey` | `SegmentTick` is an API-34 constant, so it is nothing on Android 13 and below — which is most devices |
 * | Web | `VirtualKey` | 20ms, the only pattern above the motor floor |
 * | Desktop | `VirtualKey` | No motor; the handler returns immediately whatever this says |
 */
internal expect val platformTickHaptic: HapticFeedbackType

/**
 * The constant behind [io.kontour.ui.interaction.FeedbackIntent.Tap].
 *
 * The second entry in this file, and the argument for it is the same shape as
 * the first one's: a tap wants **the lightest thing that is still felt**, and
 * what that is differs by platform in a way a common constant cannot express.
 *
 * Where a lighter tier exists it is taken. Where it does not, this is the same
 * pulse as [platformTickHaptic] — and saying so is the honest version, because
 * the alternative is a scale that reads as four levels of intensity and is two
 * on half the devices.
 *
 * ### What each platform answers, and why
 *
 * | | | |
 * |---|---|---|
 * | iOS | `SegmentTick` | `selectionChanged()`, the lightest thing on the device — genuinely lighter than an impact |
 * | Android | `SegmentTick` on API 34+, else `VirtualKey` | the soft constant exists from 34; below it, `VirtualKey` rather than silence, because a tap that does nothing is worse than one 20ms too heavy |
 * | Web | `VirtualKey` | 20ms is the only pattern above the motor's spin-up; 12 and 6 are not felt |
 * | Desktop | `VirtualKey` | no motor, and the handler returns immediately whatever this says |
 */
internal expect val platformTapHaptic: HapticFeedbackType
