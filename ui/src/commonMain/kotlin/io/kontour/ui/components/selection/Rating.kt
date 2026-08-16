package io.kontour.ui.components.selection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.ceil

/**
 * A score out of [count], as a row of marks.
 *
 * ```kotlin
 * Rating(value = 4f, contentDescription = "Your rating", onValueChange = { rating = it })
 * Rating(value = 4.3f, contentDescription = "Average rating")   // read-only
 * ```
 *
 * **`onValueChange = null` makes it read-only, and read-only means it is not a
 * control at all** — no role, no touch target, no click action, one node that
 * says "Average rating, 4.3 out of 5". A display rating that a screen reader
 * offers to activate is five buttons that do nothing, and an average score is
 * the most common rating on any screen.
 *
 * Interactive, it is a `selectableGroup` of `Role.RadioButton` marks, because
 * that is what picking one of five is. Each announces its own value, so
 * "3 stars" is a thing a screen-reader user can navigate to and choose rather
 * than a slider they have to drag blind.
 *
 * **Press and drag across the marks to set it.** A row of stars is the most
 * obviously swipeable control there is, and until now every one of them was five
 * separate tap targets. The taps still work — the drag waits for touch slop —
 * and each mark crossed ticks, so a score can be set without looking.
 *
 * A fractional [value] is drawn as a partly-filled mark. A *tap* cannot mean
 * 3.4, so tapping is always whole marks; a drag can, and [allowHalf] is where
 * that is turned on.
 *
 * @param value The score. Clamped into `0f..count`.
 * @param allowHalf Whether a drag can stop on a half mark. Off by default: a
 *   rating that emits 3.5 when every caller expected 3 or 4 is a change of
 *   contract, not a nicety. On, the left half of a mark is `.5` and the right
 *   half is whole, which is the only mapping anyone guesses right first time.
 * @param contentDescription What the score is *of* — "Your rating", "Average
 *   rating". Required: five stars with no name is a row of decoration.
 * @param onValueChange `null` for read-only. Receives whole numbers, as the
 *   `Float` [value] is, so the emitted score assigns straight back without a
 *   conversion at every call site. A tap cannot produce a half mark; an average
 *   can, which is why the parameter is a `Float` in the first place.
 * @param icon The empty mark. Defaults to a star outline.
 * @param filledIcon The full mark. Defaults to a filled star.
 */
@Composable
fun Rating(
    value: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChange: ((Float) -> Unit)? = null,
    count: Int = RatingDefaults.Count,
    allowHalf: Boolean = false,
    icon: ImageVector? = null,
    filledIcon: ImageVector? = null,
    markSize: Dp = Theme.sizing.iconLarge,
    filledColor: Color = Theme.colors.warning.solid,
    emptyColor: Color = Theme.colors.outlineStrong,
    stateDescription: ((Float) -> String)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val empty = icon ?: SystemIcons.Star
    val full = filledIcon ?: SystemIcons.StarFilled
    val clamped = value.coerceIn(0f, count.toFloat())
    val spoken = stateDescription?.invoke(clamped) ?: defaultRatingDescription(clamped, count)

    if (onValueChange == null) {
        // One node, and nothing to press. `clearAndSetSemantics` rather than a
        // merge: the marks below have no semantics worth keeping, and merging
        // would leave the row's own description competing with five silent
        // children rather than replacing them.
        Row(
            modifier = modifier.clearAndSetSemantics {
                this.contentDescription = contentDescription
                this.stateDescription = spoken
            },
            horizontalArrangement = Arrangement.spacedBy(RatingDefaults.Gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { index ->
                Mark(
                    fill = (clamped - index).coerceIn(0f, 1f),
                    empty = empty,
                    full = full,
                    size = markSize,
                    filledColor = filledColor,
                    emptyColor = emptyColor,
                )
            }
        }
        return
    }

    val feedback = Feedback
    val selected = ceil(clamped).toInt()

    /**
     * Where each mark actually is, relative to the row.
     *
     * Measured rather than divided, because the marks are not evenly pitched:
     * `minimumTouchTarget` grows each one to 48dp and the gap between them is
     * 4dp, so `width / count` drifts by a third of a mark by the right-hand end
     * and a drag over the fifth star would set the fourth. Five callbacks is
     * cheap; `CalendarMonth` chose arithmetic over forty-two, and this is not
     * that.
     */
    val markLeft = remember(count) { FloatArray(count) }
    val markWidth = remember(count) { FloatArray(count) }
    val currentValue by rememberUpdatedState(clamped)
    val currentChange by rememberUpdatedState(onValueChange)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    fun setFromX(x: Float) {
        if (markWidth.all { it <= 0f }) return
        val nearest = markLeft.indices.minByOrNull { abs(markLeft[it] + markWidth[it] / 2f - x) }
            ?: return
        val index = if (isRtl) count - 1 - nearest else nearest
        val within = ((x - markLeft[nearest]) / markWidth[nearest]).coerceIn(0f, 1f)
        val next = when {
            !allowHalf -> (index + 1).toFloat()
            // The left half of a mark is the half mark. Mirrored in RTL, where
            // "the left half" is the far side of the same star.
            (if (isRtl) 1f - within else within) < 0.5f -> index + 0.5f
            else -> (index + 1).toFloat()
        }
        if (next == currentValue) return
        feedback.perform(FeedbackIntent.Tick)
        currentChange(next)
    }

    Row(
        modifier = modifier
            .selectableGroup()
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = spoken
                if (!enabled) disabled()
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(count, allowHalf, isRtl) {
                        detectHorizontalDragGestures(
                            onDragStart = { setFromX(it.x) },
                            onHorizontalDrag = { change, _ -> setFromX(change.position.x) },
                        )
                    }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(RatingDefaults.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            // The mark's *value* is its index plus one. Tapping the third star
            // means three, not two, and off-by-one here is a rating that can
            // never be five and can always be zero.
            val markValue = index + 1
            val interactions = interactionSource ?: remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .onGloballyPositioned {
                        markLeft[index] = it.positionInParent().x
                        markWidth[index] = it.size.width.toFloat()
                    }
                    .minimumTouchTarget()
                    .focusRing(interactions, Theme.shapes.small)
                    .selectable(
                        selected = markValue <= selected,
                        onClick = {
                            feedback.perform(FeedbackIntent.Selection)
                            onValueChange(markValue.toFloat())
                        },
                        enabled = enabled,
                        role = androidx.compose.ui.semantics.Role.RadioButton,
                        interactionSource = interactions,
                        indication = null,
                    )
                    .semantics {
                        this.contentDescription = ratingMarkDescription(markValue, count)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Mark(
                    // The same expression the read-only row uses. With whole
                    // values it is identical to the old `if (markValue <=
                    // selected) 1f else 0f`; with `allowHalf` it is what draws
                    // the half.
                    fill = (clamped - index).coerceIn(0f, 1f),
                    empty = empty,
                    full = full,
                    size = markSize,
                    filledColor = if (enabled) filledColor else Theme.colors.contentDisabled,
                    emptyColor = if (enabled) emptyColor else Theme.colors.contentDisabled,
                    springOnFill = true,
                )
            }
        }
    }
}

/**
 * One mark, [fill] of the way full.
 *
 * The filled glyph is drawn over the empty one and clipped to a fraction of its
 * width, rather than tinted from a gradient. A star is not a rectangle: a linear
 * gradient across it fills the points before the body and reads as a smudge,
 * where a hard clip reads as a star that is half full.
 */
@Composable
private fun Mark(
    fill: Float,
    empty: ImageVector,
    full: ImageVector,
    size: Dp,
    filledColor: Color,
    emptyColor: Color,
    springOnFill: Boolean = false,
) {
    val scale by animateFloatAsState(
        targetValue = if (springOnFill && fill > 0f) 1f else 0.92f,
        animationSpec = Theme.motion.springOrTween(Theme.motion.springBouncy),
        label = "ratingMark",
    )

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(empty, contentDescription = null, tint = emptyColor, modifier = Modifier.size(size))
        if (fill > 0f) {
            Box(
                Modifier
                    .size(size)
                    .scale(if (springOnFill) scale else 1f)
                    // Clipped in the *draw* phase, not the layout phase.
                    //
                    // Narrowing the layout instead — which is what this did —
                    // leaves `Modifier.size` holding a fixed box around a child
                    // that measures smaller, and a fixed box centres what it
                    // cannot fill. So a half-filled star was clipped correctly
                    // and then placed in the middle of the cell, floating free
                    // of the empty star underneath it. Clipping the drawing
                    // leaves the glyph exactly where it was laid out.
                    .drawWithContent {
                        clipRect(right = this.size.width * fill) {
                            this@drawWithContent.drawContent()
                        }
                    },
            ) {
                Icon(full, contentDescription = null, tint = filledColor, modifier = Modifier.size(size))
            }
        }
    }
}

/** "4.3 out of 5", or "4 out of 5" when the score is whole. */
private fun defaultRatingDescription(value: Float, count: Int): String {
    val whole = value.toInt()
    val shown = if (value == whole.toFloat()) whole.toString() else value.toString()
    return "$shown out of $count"
}

/** What one mark announces: "3 stars", not "star 3". */
private fun ratingMarkDescription(markValue: Int, count: Int): String =
    if (markValue == 1) "1 out of $count" else "$markValue out of $count"

object RatingDefaults {
    /** Five, which is what a rating means to almost everybody. */
    const val Count: Int = 5

    /**
     * The gap between marks.
     *
     * Small enough that the row reads as one score rather than as five separate
     * icons — the touch targets around them are much wider than the gap and
     * overlap nothing, because `minimumTouchTarget` reserves layout space.
     */
    val Gap: Dp @Composable get() = Theme.spacing.xxs
}
