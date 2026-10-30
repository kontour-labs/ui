package io.kontour.ui.components.selection

import io.kontour.ui.a11y.LocalTouchTargetOwnedByParent
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.CompositionLocalProvider
import io.kontour.ui.locale.LocaleFormats
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.ceil

/**
 * A score out of [count], as a row of marks.
 *
 * ```kotlin
 * Rating(value = 4f, onValueChange = { rating = it }, contentDescription = "Your rating")
 * Rating(value = 4.3f, onValueChange = null, contentDescription = "Average rating")   // read-only
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
 * separate tap targets. The score follows the finger from the moment it lands —
 * there is no slop to cross first. The press answers with a tap when it changes
 * the score, and each mark a drag then crosses ticks like a slider's detent, so a
 * score can be set without looking and a fast drag is not a run of presses.
 *
 * The row owns the pointer for the whole gesture, the way [Slider] does and for
 * the same reason: it is an absolute control, mapping a position to a value, so
 * there is no part of a press on it that means something else. The marks keep
 * their `selectable` for the keyboard and for assistive tech, which is where a
 * whole-mark activation still comes from.
 *
 * A fractional [value] is drawn as a partly-filled mark, and with [allowHalf] a
 * press can produce one: the left half of a mark's *target* is the half score,
 * which is a good deal more than the left half of the glyph.
 *
 * @param value The score. Clamped into `0f..count`.
 * @param allowHalf Whether a press or drag can stop on a half mark. Off by
 *   default: a rating that emits 3.5 when every caller expected 3 or 4 is a
 *   change of contract, not a nicety. On, the left half of a mark is `.5` and
 *   the right half is whole, which is the only mapping anyone guesses right
 *   first time — and the halves divide the mark's whole 48dp target, not its
 *   glyph, so the half score is a 24dp-wide region rather than a 10dp one.
 * @param allowZero Whether the score can be taken back to nothing. On by
 *   default, as asked for from a phone: a rating with no way back to zero is one
 *   a slip of the thumb commits the reader to. A tap on the score already given
 *   clears it — decided when the finger lifts, so a drag that starts there still
 *   drags — as does a drag carried off the start of the row, or choosing the
 *   selected mark again from the keyboard. Off, the lowest score is one mark.
 * @param contentDescription What the score is *of* — "Your rating", "Average
 *   rating". Required: five stars with no name is a row of decoration.
 * @param onValueChange `null` for read-only. Receives whole numbers unless
 *   [allowHalf] is on, in which case it may also receive halves. A `Float`
 *   either way, so the emitted score assigns straight back without a conversion
 *   at every call site — and because an average is a `Float`, which is the more
 *   common rating on any screen.
 * @param icon The empty mark. Defaults to a star outline.
 * @param filledIcon The full mark. Defaults to a filled star.
 * @param onValueChangeFinished Called once the score has been set — when a press
 *   or a drag lifts, or a mark is chosen from the keyboard — as a slider's is. For
 *   saving the score, where [onValueChange] would save every mark a drag crossed.
 * @param interactionSource Hears every mark's interactions — focus, hover, the
 *   keyboard's press. Each mark keeps a source of its own as well, which is
 *   what its focus ring reads, so the ring goes round the focused mark alone.
 */
@Composable
fun Rating(
    value: Float,
    onValueChange: ((Float) -> Unit)?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    count: Int = RatingDefaults.Count,
    allowHalf: Boolean = false,
    allowZero: Boolean = true,
    icon: ImageVector? = null,
    filledIcon: ImageVector? = null,
    markSize: Dp = Theme.sizing.iconLarge,
    colours: RatingColours = RatingDefaults.colours(),
    stateDescription: ((Float) -> String)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val tap = rememberTapFeedback()
    // Marks crossed by a drag are detents, not presses: the lightest tick, and the
    // shared rate floor. It used to tap for each one — a click per star, twice
    // with halves — which was felt as heavy against every other drag in the set.
    val ticker = rememberDetentTicker()
    val empty = icon ?: Theme.icons.star
    val full = filledIcon ?: Theme.icons.starFilled
    val clamped = value.coerceIn(0f, count.toFloat())
    val strings = Theme.strings
    val formats = Theme.formats
    val outOf: (String, Int) -> String = remember(strings, formats) { { written, of -> strings.outOf(written, formats.quantity(of)) } }
    val spoken = stateDescription?.invoke(clamped) ?: defaultRatingDescription(clamped, count, outOf, formats)

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
                    filledColour = colours.filled,
                    emptyColour = colours.empty,
                )
            }
        }
        return
    }

    val selected = ceil(clamped).toInt()

    /**
     * Where each mark actually is, relative to the row.
     *
     * Measured rather than divided, because the marks need not be evenly
     * pitched against the row: a row wider than its marks, or a mark grown to a
     * target, and `width / count` drifts by the right-hand end until a drag over
     * the fifth star sets the fourth. Five callbacks is
     * cheap; `CalendarMonth` chose arithmetic over forty-two, and this is not
     * that.
     */
    val markLeft = remember(count) { FloatArray(count) }
    val markWidth = remember(count) { FloatArray(count) }
    val currentValue by rememberUpdatedState(clamped)
    val currentChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Whether the gesture's press landed on the score already given — which,
    // lifted without a drag, takes it back to zero. See `allowZero`.
    var pressedOnScore = false

    fun setFromX(x: Float, press: Boolean) {
        if (markWidth.all { it <= 0f }) return
        val nearest = markLeft.indices.minByOrNull { abs(markLeft[it] + markWidth[it] / 2f - x) }
            ?: return
        // Already the mark's own index, in either direction. [markLeft] is kept
        // per mark, at wherever the row placed that mark, so in right to left
        // mark 0 is measured at the right-hand end and the nearest one is
        // already the logical one. Mirroring it again — which this did — made
        // a tap on the right-most star in Arabic the fifth star, and on the
        // left-most the first.
        val index = nearest
        val within = ((x - markLeft[nearest]) / markWidth[nearest]).coerceIn(0f, 1f)
        // Carried off the start of the row: before the first mark in reading
        // order, which is its left in LTR and its right in RTL — and the first
        // mark is mark 0 in both. It was measured against the *last* mark's
        // right edge in RTL, which is the left-hand star's, so nearly every
        // press on a right-to-left rating read as off the start and cleared it.
        val offStart = if (isRtl) {
            x > markLeft[0] + markWidth[0]
        } else {
            x < markLeft[0]
        }
        val next = when {
            allowZero && offStart -> 0f
            !allowHalf -> (index + 1).toFloat()
            // The left half of a mark is the half mark. Mirrored in RTL, where
            // "the left half" is the far side of the same star.
            (if (isRtl) 1f - within else within) < 0.5f -> index + 0.5f
            else -> (index + 1).toFloat()
        }
        // Indexed in half marks, so a half is its own detent where there are halves.
        val detent = next * 2f
        if (press) {
            // The press arms the ticker on the value it lands on — a drag that
            // starts there has crossed nothing yet — and answers with a tap if it
            // changed the score.
            ticker.reset()
            ticker.at(detent)
            pressedOnScore = next == currentValue && next > 0f
            if (next == currentValue) return
            tap()
        } else {
            ticker.at(detent)
            if (next == currentValue) return
        }
        currentChange(next)
    }

    // **The row is the target, not each star.** It takes every press itself
    // and turns it into the nearest mark, so a star's own target only ever
    // served the keyboard and assistive tech — and reserving it spread the
    // marks: on a phone each 24dp star sat in 48dp, 28dp of air between one
    // and the next, more gap than star. *"it's always bugged me how much gap
    // there is between the stars by default"*. The row is at least a target
    // tall instead and the stars are [RatingDefaults.Gap] apart, as a
    // read-only rating's always were. See [LocalTouchTargetOwnedByParent].
    CompositionLocalProvider(LocalTouchTargetOwnedByParent provides true) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = Theme.sizing.minTouchTarget)
                .selectableGroup()
                .semantics {
                    this.contentDescription = contentDescription
                    this.stateDescription = spoken
                    if (!enabled) disabled()
                }
                .then(
                    if (enabled) {
                        Modifier.pointerInput(count, allowHalf, allowZero, isRtl) {
                            awaitEachGesture {
                                // Claimed on the down, on the initial pass, which
                                // travels parent to child — so the marks' own
                                // `selectable` never starts a click and the score
                                // comes from *where* the press landed rather than
                                // from which mark it landed on. That is the whole
                                // of the half-mark problem: a tap on the left of a
                                // star could only ever mean the star.
                                //
                                // It was a `detectHorizontalDragGestures`, which
                                // waits for touch slop. Giving a half meant pressing,
                                // travelling 16dp, and coming back to the right side
                                // of the star's midpoint — while a plain tap went to
                                // the mark underneath and could only be whole.
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                down.consume()
                                setFromX(down.position.x, press = true)
                                var travel = Offset.Zero
                                var lifted = false

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    // Before the consume, always: `positionChange`
                                    // reports zero for a change that has already
                                    // been consumed, so reading it afterwards is a
                                    // gesture that can never see any movement.
                                    val moved = change.positionChange()
                                    change.consume()
                                    if (!change.pressed) {
                                        lifted = true
                                        break
                                    }
                                    travel += moved
                                    if (moved != Offset.Zero) setFromX(change.position.x, press = false)
                                }
                                // A tap on the score already given, lifted where it
                                // landed: take it back. Not decided on the press, so
                                // a drag from the current score is a drag.
                                if (allowZero && lifted && pressedOnScore && travel.getDistance() < viewConfiguration.touchSlop) {
                                    tap()
                                    currentChange(0f)
                                }
                                pressedOnScore = false
                                ticker.reset()
                                currentFinished?.invoke()
                            }
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

                // **Each mark's own**, so the ring goes round the one mark that
                // has focus. Every mark used to take the caller's source when
                // one was given, and a ring reads focus from its source — so
                // tabbing to the third star drew a ring round all five. The
                // caller's source still hears everything: each mark's
                // interactions are passed on to it, so focus, hover and the
                // keyboard's press anywhere in the row reach it as before.
                val interactions = remember { MutableInteractionSource() }
                if (interactionSource != null) {
                    LaunchedEffect(interactions, interactionSource) {
                        interactions.interactions.collect { interactionSource.emit(it) }
                    }
                }

                Box(
                    modifier = Modifier
                        .onGloballyPositioned {
                            markLeft[index] = it.positionInParent().x
                            markWidth[index] = it.size.width.toFloat()
                        }
                        .minimumTouchTarget()
                        .focusRing(interactions, Theme.shapes.small)
                        // Whole marks, always: this is what the keyboard and
                        // assistive tech activate, and neither of them has a
                        // position within the mark to mean a half by. The pointer
                        // never reaches here — the row consumed it — so there is no
                        // second answer to the same tap.
                        .pointerCursor(enabled = enabled)
                        .selectable(
                            // The scored mark, and only that one: the marks
                            // are radio buttons, and a group of them has one
                            // selected. Every filled mark said it was, so the
                            // first star announced itself selected and
                            // activating it changed the score.
                            selected = markValue == selected,
                            // The keyboard and assistive-tech path, and the only one
                            // that reaches here — the row consumes the pointer on the
                            // initial pass, so this and `setFromX` never both answer
                            // the same input. Both acknowledge a change, because a
                            // checkbox activated from the keyboard does — and neither
                            // answers the score it already has, as a radio does not.
                            onClick = {
                                // The selected mark again takes the score back, where
                                // that is allowed — the keyboard's way to zero.
                                val next = if (allowZero && markValue.toFloat() == currentValue) 0f else markValue.toFloat()
                                if (next != currentValue) tap()
                                onValueChange(next)
                                currentFinished?.invoke()
                            },
                            enabled = enabled,
                            role = androidx.compose.ui.semantics.Role.RadioButton,
                            interactionSource = interactions,
                            indication = null,
                        )
                        .semantics {
                            this.contentDescription = outOf(formats.integer(markValue), count)
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
                        filledColour = if (enabled) colours.filled else colours.filledDisabled,
                        emptyColour = if (enabled) colours.empty else colours.emptyDisabled,
                        springOnFill = true,
                    )
                }
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
    filledColour: Color,
    emptyColour: Color,
    springOnFill: Boolean = false,
) {
    // This and the fill below are held as states and read where they are
    // drawn, as `Slider`'s are. Read here through `by`, every frame of either
    // spring composed the mark again — the box, both icons and the modifiers
    // round them — to move a clip and a scale. Setting a score animates every
    // mark between the old one and the new, so that was each of them, every
    // frame, until the bounciest spring settled: 117 scopes for one score in
    // `RatingFillRecompositionTest`, against 27 now.
    val markScale = animateFloatAsState(
        targetValue = if (springOnFill && fill > 0f) 1f else 0.92f,
        animationSpec = Theme.motion.springOrTween(Theme.motion.springBouncy),
        label = "ratingMark",
    )
    // The fill travels rather than appearing.
    //
    // A mark used to be full or empty between one frame and the next, so
    // setting a score was five stars blinking on. Easing the clip is what turns
    // it into them filling — and it is the same value on the way out, so a
    // score coming down empties rather than switching off.
    //
    // Starts at its target, so a read-only rating renders settled on its first
    // frame and no golden waits for a spring.
    val shownFill = animateFloatAsState(
        targetValue = fill,
        animationSpec = Theme.motion.springOrTween(Theme.motion.springSnappy),
        label = "ratingFill",
    )
    // Whether there is any fill to draw, which is all composition needs of it.
    val filled by remember(shownFill) { derivedStateOf { shownFill.value > 0f } }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(empty, contentDescription = null, tint = emptyColour, modifier = Modifier.size(size))
        if (filled) {
            Box(
                Modifier
                    .size(size)
                    // Clipped in the *draw* phase, not the layout phase.
                    //
                    // Narrowing the layout instead — which is what this did —
                    // leaves `Modifier.size` holding a fixed box around a child
                    // that measures smaller, and a fixed box centres what it
                    // cannot fill. So a half-filled star was clipped correctly
                    // and then placed in the middle of the cell, floating free
                    // of the empty star underneath it. Clipping the drawing
                    // leaves the glyph exactly where it was laid out.
                    //
                    // Scaled here too, about the middle, where `Modifier.scale`
                    // scaled it: the same transform round the same clip, read
                    // in the draw pass so the spring repaints the mark rather
                    // than composing it.
                    .drawWithContent {
                        // From the start of the line: a half star in Arabic is
                        // lit on its right.
                        val lit = this.size.width * shownFill.value
                        val rtl = layoutDirection == LayoutDirection.Rtl
                        scale(if (springOnFill) markScale.value else 1f) {
                            clipRect(
                                left = if (rtl) this.size.width - lit else 0f,
                                right = if (rtl) this.size.width else lit,
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    },
            ) {
                Icon(full, contentDescription = null, tint = filledColour, modifier = Modifier.size(size))
            }
        }
    }
}

/**
 * "4.3 out of 5", or "4 out of 5" when the score is whole — through
 * [io.kontour.ui.theme.Strings.outOf], which one mark announces as well: "3 out
 * of 5", not "star 3".
 */
private fun defaultRatingDescription(value: Float, count: Int, outOf: (String, Int) -> String, formats: LocaleFormats): String {
    val whole = value.toInt()
    val shown = if (value == whole.toFloat()) formats.integer(whole) else formats.decimal(value.toDouble(), 1)
    return outOf(shown, count)
}

/** The colours a [Rating] draws its marks in. */
@Immutable
data class RatingColours(
    /** The filled part of a mark. */
    val filled: Color,
    /** The outline of a mark, and the part a partial score leaves empty. */
    val empty: Color,
    val filledDisabled: Color,
    val emptyDisabled: Color,
)

/** What a [Rating] takes by default. */
object RatingDefaults {
    /** Five, which is what a rating means to almost everybody. */
    const val Count: Int = 5

    /** The theme's colours for a rating: warning-yellow marks on a strong outline. */
    @Composable
    @ReadOnlyComposable
    fun colours(
        filled: Color = Theme.colours.warning.solid,
        empty: Color = Theme.colours.outlineStrong,
        filledDisabled: Color = Theme.colours.contentDisabled,
        // Fainter than the filled marks, so a disabled rating still says what
        // it is. Both were the disabled grey, and three stars of five read as
        // five grey stars.
        emptyDisabled: Color = Theme.colours.outline,
    ): RatingColours = RatingColours(filled, empty, filledDisabled, emptyDisabled)

    /**
     * The gap between marks.
     *
     * Small enough that the row reads as one score rather than as five separate
     * icons — the touch targets around them are much wider than the gap and
     * overlap nothing, because `minimumTouchTarget` reserves layout space.
     */
    val Gap: Dp @Composable @ReadOnlyComposable get() = Theme.spacing.xxs
}
