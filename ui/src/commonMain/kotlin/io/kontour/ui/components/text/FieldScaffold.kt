package io.kontour.ui.components.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.AnimatedSlot
import io.kontour.ui.theme.Theme

/**
 * The frame every form control shares: label above, bordered box, helper or
 * error below.
 *
 * Extracted so a [Select] is not a careful re-creation of a [TextField] that
 * drifts from it on the next change. Anything that sits in a form and takes a
 * value goes through here, which is what makes a column of mixed controls line
 * up without per-call-site padding.
 *
 * @param frameModifier Applied to the bordered box, not the whole control —
 *   where a `clickable` belongs for a control that opens something, so the tap
 *   target is the field and not the label and helper text as well.
 * @param content Fills the box between the leading slots and the trailing slot.
 *   Give it `Modifier.weight(1f)` unless the control is meant to hug its value.
 */
/** The gap between a field's label, its frame and its message. */
private val FieldStackGap = 6.dp

@Composable
internal fun FieldScaffold(
    modifier: Modifier,
    enabled: Boolean,
    focused: Boolean,
    /**
     * Whether the value can be read and copied but not typed into.
     *
     * Only the *appearance* of focus is suppressed by this. A read-only field is
     * still focusable, still in the keyboard's traversal order, and its text is
     * still selectable — that is what foundation's `readOnly` means, and copying
     * a booking reference out of a locked field is the reason it exists.
     *
     * What goes is the accent border and the ground tint, which between them say
     * "you are typing here" about a field nobody can type in. It used not to
     * reach this scaffold at all: `readOnly` was passed to `BasicTextField` and
     * nowhere else, so the frame went on lighting up.
     */
    readOnly: Boolean = false,
    colours: TextFieldColours,
    metrics: TextFieldMetrics,
    shape: Shape,
    label: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    frameModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val motion = Theme.motion
    val isError = errorMessage != null

    // Focus that the *frame* reacts to, which is not the same as focus.
    //
    // A read-only field still takes focus and still shows a caret and a
    // selection; what it must not do is put on the accent border and the ground
    // tint, because those say "typing happens here". Error is deliberately not
    // gated: a read-only field can still be invalid, and it still has to say so.
    val litUp = focused && !readOnly

    val borderColour by animateColorAsState(
        targetValue = colours.border(enabled, litUp, isError),
        animationSpec = motion.tweenFast(),
        label = "fieldBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (litUp || isError) {
            Theme.sizing.borderWidthStrong
        } else {
            Theme.sizing.borderWidth
        },
        animationSpec = motion.tweenFast(),
        label = "fieldBorderWidth",
    )
    // Animated for the same reason the border is: a ground that changes colour
    // between frames reads as a repaint, and one that fades reads as a response.
    val containerColour by animateColorAsState(
        targetValue = colours.container(enabled, litUp),
        animationSpec = motion.tweenFast(),
        label = "fieldContainer",
    )
    val labelColour by animateColorAsState(
        targetValue = when {
            !enabled -> colours.contentDisabled
            isError -> colours.error
            litUp -> colours.labelFocused
            else -> colours.label
        },
        animationSpec = motion.tweenFast(),
        label = "fieldLabel",
    )

    // No `verticalArrangement`: the message slot carries the gap above it, so a
    // field that stops being in error loses the message *and* its gap over the
    // same animation. With `spacedBy` the gap went in one frame at the end —
    // the vertical case of the snap `AnimatedSlot` documents.
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                // The one label that does not reach `contentScope`: a field
                // takes its label as a `String` rather than a slot, because the
                // control below it needs the same text as its accessible name.
                text = if (Theme.componentDefaults.uppercaseLabels) label.uppercase() else label,
                // Visible, but not a node of its own. Every control that goes
                // through this scaffold takes the label as its accessible *name*
                // — see `TextField` and `SelectFrame` — so leaving this
                // announceable would make a screen reader read "Origin" and then
                // "Origin, Perth, edit box", one field sounding like two things.
                // `ComponentContractTest.everyLabelledControlAnnouncesItsLabel`
                // is what stops this from silently losing the label instead.
                modifier = Modifier.clearAndSetSemantics {},
                style = Theme.typography.labelMedium,
                colour = labelColour,
            )
            Spacer(Modifier.height(FieldStackGap))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = metrics.minHeight)
                .clip(shape)
                .background(containerColour, shape)
                .border(borderWidth, borderColour, shape)
                .then(frameModifier)
                // Each side is padded for what is actually on it. A glyph does
                // not fill its own box, so an icon padded like text reads as
                // further in than the text beside it — see
                // `TextFieldMetrics.iconPadding` for the measurement.
                //
                // Horizontally only. See the inner row.
                .padding(
                    start = if (leading != null || leadingIcon != null) {
                        metrics.iconPadding
                    } else {
                        metrics.horizontalPadding
                    },
                    end = if (trailing != null) {
                        metrics.iconPadding
                    } else {
                        metrics.horizontalPadding
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()

            /**
             * The vertical padding belongs to the **text**, not to the frame.
             *
             * `defaultMinSize` is a minimum and a `Row` grows to its tallest
             * child, so padding the frame meant padding whatever a caller put in
             * a slot — and a slot routinely holds an `IconButton`, which carries
             * `minimumTouchTarget`: 48dp on Android, 44 on iOS, 24 on the JVM.
             * 12 + 48 + 12 is 72, against the 52 of every field without one. So
             * a `PasswordField` with a reveal toggle stood 20dp taller than the
             * field above it, and a `SearchField` **grew by 20dp the moment you
             * typed the first character**, because that is when its clear button
             * appears. On desktop all of it clamped back to 52 and no golden or
             * geometry test could see any of it.
             *
             * A 48dp target fits inside a 52dp field with room to spare. It only
             * did not fit because the padding was applied outside it. Moved in
             * here, the frame is `max(52dp, text + 24dp)` — which is 52dp for
             * every single-line field on every platform — and the touch target
             * is kept whole rather than relocated.
             *
             * `weight(1f)` so `content`'s own `weight(1f)` still means "the rest
             * of the field", and the gap arrangement is repeated so a leading
             * icon sits exactly where it did.
             */
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = metrics.verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(metrics.gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (enabled) colours.label else colours.contentDisabled,
                        size = Theme.sizing.iconMedium,
                    )
                }

                content()
            }

            trailing?.invoke()
        }

        /**
         * The message, and the last one when there is no message.
         *
         * The slot below closes when both the error and the supporting text are
         * gone, and while it closes it still has to *draw* something. Handing it
         * an empty string meant the content's width animated to zero on the way
         * out and back up on the way in, which the slot then clipped — the
         * report's "wipes in from the left as well as from the top", and the
         * reason it only happened on a field with no supporting text to fall
         * back to.
         *
         * Keeping the last non-empty message costs one `remember` and means the
         * width never travels anywhere. Nobody sees the retained text: by the
         * time it is showing, the slot around it has already faded and collapsed.
         */
        val message = errorMessage ?: supporting
        var lastMessage by remember { mutableStateOf(message.orEmpty()) }
        if (message != null) lastMessage = message
        val shownMessage = message ?: lastMessage

        // Helper and error occupy the same slot and animate in place, so the
        // form does not jump by a line height every time validation flips.
        AnimatedSlot(
            visible = errorMessage != null || supporting != null,
            gap = FieldStackGap,
            orientation = Orientation.Vertical,
            enter = fadeIn(motion.tweenFast()) + expandVertically(motion.tweenFast()),
            exit = fadeOut(motion.tweenFast()) + shrinkVertically(motion.tweenFast()),
        ) {
            // Crossfaded, not swapped. The slot already animates open and
            // shut; what it did not do was change *between* two messages, so a
            // field that was showing a hint and then failed validation replaced
            // one sentence with another between frames — the one moment in the
            // form where the user most needs to notice something changed.
            //
            // No size transform, and it took two goes to get there. The slot
            // above expands the message downward, which is right and is the
            // whole of the motion this wants; `AnimatedContent`'s default size
            // transform animates *and clips to* a box travelling between the two
            // messages' widths, so a longer error replacing a shorter hint was
            // also revealed left to right.
            //
            // The first fix was `SizeTransform(clip = false)`, which stopped this
            // box clipping and left it still animating its width — and the slot
            // wrapping it is an `AnimatedVisibility`, which clips to whatever its
            // content measures. So the reveal moved one node out and carried on,
            // which is the "still sometimes" in the second report of it.
            //
            // Two changes finish it: no size animation at all, and a target that
            // never goes empty. See `shownMessage` above for the second.
            AnimatedContent(
                targetState = shownMessage,
                transitionSpec = {
                    (fadeIn(motion.tweenFast()) togetherWith fadeOut(motion.tweenFast()))
                        // No size animation at all, not merely an unclipped one.
                        //
                        // `clip = false` above stopped *this* box clipping and
                        // was the right half of the fix; what it could not stop
                        // is the box still animating its width, because the
                        // `AnimatedSlot` wrapping it is an `AnimatedVisibility`
                        // and clips to whatever its content currently measures.
                        // So the width was still the thing being revealed
                        // through — just one node further out.
                        //
                        // The slot's vertical expand is the whole of the motion
                        // this ever wanted. Taking the target width immediately
                        // leaves nothing horizontal to reveal.
                        .using(sizeTransform = null)
                },
                label = "fieldMessage",
            ) { message ->
                Text(
                    text = message,
                    style = Theme.typography.bodySmall,
                    colour = if (isError) colours.error else colours.helper,
                )
            }
        }
    }
}
