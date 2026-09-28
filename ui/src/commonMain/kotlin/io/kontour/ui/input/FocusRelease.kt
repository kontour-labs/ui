package io.kontour.ui.input

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Takes focus off whatever holds it when a press arrives unhandled.
 *
 * Put it on the node that covers the screen. [io.kontour.ui.overlay.OverlayHost]
 * and [io.kontour.ui.adaptive.Scaffold] both apply it already, so an app built
 * on either gets it without asking; this is here for a root that is neither,
 * and because "tapping the page does not put the keyboard away" is a report
 * that should have a one-line answer.
 *
 * ```
 * Box(Modifier.fillMaxSize().clearFocusOnTap()) { Form() }
 * ```
 *
 * It is bounded by the node it is on, which is the one thing to get right: a
 * press landing outside those bounds never reaches it. That is why it belongs
 * at the root rather than around a form.
 *
 * @param enabled When false the modifier is inert — for a screen that manages
 *   focus itself, or a kiosk where the keyboard should stay up.
 *
 * ### Why it watches two passes rather than adding a click
 *
 * The obvious version — a `clickable` on the root — is wrong twice over: it puts
 * a click action into the semantics tree, so a screen reader announces the whole
 * page as a button, and it competes with the children for the gesture rather
 * than observing it.
 *
 * The press is read on the **Initial** pass, which runs before children see it,
 * so nothing can hide a press from this. The release is read on the ordinary
 * **Main** pass — and that is not the obvious choice, so it is worth saying why
 * it is the right one.
 *
 * Pointer passes run outside-in and then back: a parent's Initial, then the
 * child's Initial, the child's Main, then the *parent's* Main. So by the time
 * this sees the release, every child has already had its turn to consume it. A
 * release that is still unconsumed there is one nothing on the page wanted, and
 * that is the definition of "outside".
 *
 * **Asking for the Final pass instead does not work**, which took a probe to
 * see rather than a reading: `waitForUpOrCancellation` awaits Final *itself* as
 * its cancellation check, so handing it Final makes it await the same pass
 * twice and the whole gesture runs one event behind. The symptom is a rule that
 * fires on the press after the one you made. Its default is Main for this
 * reason.
 *
 * Nothing here consumes, so every other gesture is untouched.
 *
 * ### What that rule gets right by construction
 *
 * - **A button still runs.** Its `clickable` consumes the release, so this stays
 *   out of the way entirely. Whether pressing a button *also* moves focus is the
 *   button's business and the platform's — a desktop click focuses what it hits
 *   and a touch does not — and this rule neither causes that nor prevents it.
 * - **Another field takes focus normally.** It consumes, and it was going to
 *   move focus itself.
 * - **Scrolling does not drop the keyboard.** `waitForUpOrCancellation` returns
 *   null when a drag claims the pointer, so a flick past a focused field leaves
 *   it alone. That is deliberate rather than incidental: a list that dismissed
 *   the keyboard on every scroll would be its own report.
 * - **A press on an overlay's empty space counts.** This sits on the outer box,
 *   above both the content and the stack, so the rule is the same everywhere.
 *
 * `clearFocus()` and not `clearFocus(force = true)`: a field that has
 * deliberately captured focus — a validation that refuses to let go — is making
 * a statement this should not overrule.
 */
@Composable
fun Modifier.clearFocusOnTap(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val focus = LocalFocusManager.current
    return pointerInput(focus) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val release = waitForUpOrCancellation()
            if (release != null && !release.isConsumed) focus.clearFocus()
        }
    }
}
