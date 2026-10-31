package io.kontour.ui.components.display

import kotlin.math.round
import io.kontour.ui.theme.cornerPx
import androidx.compose.foundation.shape.CornerBasedShape
import io.kontour.ui.theme.drawDot
import io.kontour.ui.theme.cornerRadius
import io.kontour.ui.locale.mirrorInRtl
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.trackAtTier
import io.kontour.ui.theme.Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.first

/**
 * A horizontal progress bar.
 *
 * Pass a [progress] for determinate progress; pass `null` when the duration is
 * unknown and it becomes an indeterminate sweep.
 *
 * ```
 * LinearProgress(progress = uploaded / total, contentDescription = "Uploading photo")
 * LinearProgress(progress = null, contentDescription = "Loading departures")
 * ```
 *
 * Prefer determinate whenever you can compute it. An indeterminate bar tells the
 * user nothing except "still working", and after a few seconds that reads as
 * "stuck".
 *
 * **Indeterminate is two bars chasing each other**: a long one sweeping out and
 * a short one after it, each speeding up and slowing down — Material's timing,
 * reported as wanted over the single band at an even speed, which read as a
 * loading bar from another decade.
 *
 * Under reduced motion the indeterminate sweep stops and the bar shows a static
 * partial fill — still visibly "in progress", without the looping travel.
 */
@Composable
fun LinearProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    colour: Color = Theme.colours.primary,
    trackColour: Color = trackAtTier(Theme.colours.outline),
    height: Dp = ProgressDefaults.LinearHeight,
) {
    val motion = Theme.motion
    // Its ends: half its height, as they always were, unless the theme's
    // capsule is squarer than that. 0.5.2.
    val capsule = Theme.shapes.capsule

    // Registered only when it is going to be drawn.
    //
    // `rememberInfiniteTransition` subscribes to the frame clock the moment it
    // is composed, and it keeps asking for frames forever whether or not
    // anything reads the value it produces. So gating the *read* — which is what
    // the draw below used to do on its own — stops the picture moving and leaves
    // the whole cost in place: a determinate bar, or one under reduced motion,
    // drove sixty frames a second of an unchanging shape. On web that is the
    // frame budget for everything else on the page, on one thread.
    val sweep = if (progress == null && !motion.reduceMotion) {
        rememberInfiniteTransition(label = "linearProgress").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = ChaseCycle, easing = LinearEasing),
            ),
            label = "linearSweep",
        )
    } else {
        null
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = motion.tweenDefault(),
        label = "linearProgressValue",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            // Fills from the start of the line, which is the right in right to left.
            .mirrorInRtl()
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = if (progress != null) {
                    ProgressBarRangeInfo(current = progress.coerceIn(0f, 1f), range = 0f..1f)
                } else {
                    ProgressBarRangeInfo.Indeterminate
                }
            }
    ) {
        val radius = capsule.cornerRadius(size, this)
        drawRoundRect(color = trackColour, cornerRadius = radius)

        if (progress != null) {
            drawRoundRect(
                color = colour,
                size = Size(size.width * animatedProgress, size.height),
                cornerRadius = radius,
            )
        } else if (motion.reduceMotion) {
            // Static, so it still reads as "working" without the travel.
            drawRoundRect(
                color = colour,
                size = Size(size.width * 0.35f, size.height),
                cornerRadius = radius,
            )
        } else {
            // Two bars chasing along the track, each cut to the track rather than
            // clipped, so its ends stay round as it enters and leaves.
            chasingBars(sweep?.value ?: 0f) { from, to ->
                drawCapsule(colour, size.width * from, size.width * to, capsule)
            }
        }
    }
}

/**
 * A circular progress ring.
 *
 * For progress that sits inside something round — a trip's completion around an
 * avatar, a download around an icon. For plain "we are working on it", use
 * [Spinner], which is lighter and reads as activity rather than as a measurable
 * quantity.
 */
@Composable
fun CircularProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = ProgressDefaults.CircularSize,
    colour: Color = Theme.colours.primary,
    trackColour: Color = trackAtTier(Theme.colours.outline),
    strokeWidth: Dp = ProgressDefaults.CircularStrokeWidth,
) {
    // Indeterminate is the [Spinner], not a second sweep of the same arc.
    //
    // Two components drawing a rotating arc drift: one gets a new duration or a
    // rounded cap and the other does not, and an app showing both at once shows
    // two different products. `Spinner` is the one loader in the library and
    // every other indeterminate state in it — a button, `LoadMore`,
    // `PullToRefresh` — already goes through it.
    if (progress == null) {
        Spinner(
            modifier = modifier,
            size = size,
            colour = colour,
            strokeWidth = strokeWidth,
            contentDescription = contentDescription,
        )
        return
    }

    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = Theme.motion.tweenDefault(),
        label = "progressRing",
    )

    val lineCap = Theme.shapes.lineCap
    // Round the theme's pill: a circle, unless the theme is squarer, when the
    // ring runs round that box, as a spinner does. 0.5.2.
    val pill = Theme.shapes.pill
    val traced = remember { RingTrack() }
    Canvas(
        modifier
            .size(size)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(current = clamped, range = 0f..1f)
            }
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val corner = pill.cornerPx(arcSize, this)
        if (corner < arcSize.minDimension / 2f) {
            traced.fit(arcSize, corner)
            traced.drawWhole(this, trackColour, Offset(inset, inset), Stroke(width = stroke))
            traced.drawRun(this, colour, Offset(inset, inset), -90f, 360f * animated, Stroke(width = stroke, cap = lineCap))
            return@Canvas
        }

        drawArc(
            color = trackColour,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = colour,
            // From twelve o'clock, like every other progress ring a user has seen.
            startAngle = -90f,
            sweepAngle = 360f * animated,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = lineCap),
        )
    }
}

/**
 * Progress through a known number of discrete steps.
 *
 * ```
 * StepProgress(current = 2, total = 4, contentDescription = "Step 2 of 4")
 * ```
 *
 * Segments rather than a continuous bar, because the user can count them and
 * know how much is left — which a percentage does not tell them as directly.
 *
 * @param current Which step is in progress, counting from one, or `null` for
 *   "somewhere in this sequence, not yet known" — a wizard restoring a session,
 *   a checkout waiting on a server that has not said where it got to. `null`
 *   walks one lit segment along the row rather than claiming a position; it is
 *   not the same as `1`, which says the first step is under way.
 * @param working Whether [current] is *itself* still going. The step's position
 *   is known and its progress within that step is not, so the segment carries
 *   [LinearProgress]'s two chasing bars instead of a fill, at the width of one
 *   segment. When [current] moves on, the step just finished fills first and
 *   only then do the bars start in the next one, from its start — asked for
 *   from a phone: "the current step fills up before the indeterminate animation
 *   moves to the next step". Ignored when [current] is `null`, which is already
 *   animating.
 * @param stepProgress How far through [current] it has got, from 0 to 1, as
 *   [LinearProgress]'s `progress` is: the steps before it full and this one
 *   filled that far, for a step that can say how much of itself is done — a
 *   file of a batch uploading, a stage of an install. Takes the place of
 *   [working]'s bars, which are for a step that cannot. `null`, the default,
 *   is a step reached and not measured, drawn full as it always has been.
 *   Ignored when [current] is `null`.
 * @param weights How long each step is against the others, one a step, as
 *   `Modifier.weight` shares out a row: `listOf(1f, 3f)` makes the second step
 *   three times the first, for a short step before a long one. `null`, the
 *   default, makes them all the same, as does a list that does not have one
 *   weight above nothing for every step.
 * @param gap The space between segments. `0.dp` draws one continuous bar with
 *   round ends and no seams, filled to a step's end — a stepped bar that does
 *   not show its steps, for where the count is said elsewhere.
 */
@Composable
fun StepProgress(
    current: Int?,
    total: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    working: Boolean = false,
    stepProgress: Float? = null,
    colour: Color = Theme.colours.primary,
    trackColour: Color = trackAtTier(Theme.colours.outline),
    height: Dp = ProgressDefaults.StepHeight,
    weights: List<Float>? = null,
    gap: Dp = ProgressDefaults.StepGap,
) {
    if (total <= 0) return
    val strings = Theme.strings
    val formats = Theme.formats
    val indeterminate = current == null
    val steps = rememberStepMotion(current, total, working, stepProgress)
    val shares = remember(total, weights) { stepShares(total, weights) }
    val capsule = Theme.shapes.capsule

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .mirrorInRtl()
            .semantics {
                this.contentDescription = contentDescription ?: when {
                    indeterminate -> strings.inProgress
                    else -> strings.stepOfCount(formats.quantity(current), formats.quantity(total), steps.underWay)
                }
                // `Indeterminate` rather than a made-up position: a screen reader
                // saying "step 0 of 4" would be stating something the caller
                // explicitly said it does not know.
                progressBarRangeInfo = if (indeterminate) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    // The step it is on, as `LinearProgress` reports its value —
                    // not the fill on its way there, which re-ran these semantics
                    // on every frame of the spring for a number between steps.
                    steps.rangeInfo()
                }
            }
    ) {
        // The gap closes as the row melts, so the segments arrive touching.
        val melt = steps.melt
        val gapPx = gap.toPx() * (1f - melt)
        // Each step its share of what the gaps leave: see [weights].
        //
        // Added up as each loop below walks the row, rather than laid out in
        // two arrays first: this runs on every frame of a fill or a chase, and
        // the arrays were two allocations a frame for numbers the loops can
        // keep as they go. The reach is two functions rather than one handing
        // back a pair, which was another two for every segment.
        val room = size.width - gapPx * (total - 1)
        val radius = capsule.cornerRadius(size, this)
        // How far the seams are hidden: as far as the row has melted, or all the
        // way for a row asked for with no gap. Segments that merely touch meet
        // at two round caps and pinch there, so a gapless row came out as a bar
        // with a notch at every step.
        val seam = if (gap <= 0.dp) 1f else melt

        /**
         * How far a segment reaches into its neighbour at its start, to hide
         * the seam, and [reachEnd] at its end.
         *
         * Closing the gaps is not enough on its own: two capsules that merely
         * touch still meet at two semicircular caps, and the join pinches. Push
         * each one a full radius into the segment beside it and the overlap is
         * painted at full height all the way across, so the union is a
         * continuous bar with round ends and nothing in between — no per-corner
         * path, and no allocation in a draw loop.
         *
         * Zero at either end of the row, which is where the bar's own round ends
         * belong.
         */
        fun reachStart(index: Int): Float = if (index == 0) 0f else radius.x * seam

        fun reachEnd(index: Int): Float = if (index == total - 1) 0f else radius.x * seam

        // Read here rather than in composition: the value changes every frame,
        // and reading it above would recompose the whole row on each one instead
        // of redrawing it.
        val phase = steps.phase
        val chasePhase = steps.chasePhase
        val animated = steps.filled

        // Every track before any fill. A segment reaching into its neighbour
        // reaches under that neighbour's track too, and drawn in turn, the next
        // track covered the end of a fill that had reached into it.
        var left = 0f
        for (index in 0 until total) {
            val segmentWidth = room * shares[index]
            val reachStart = reachStart(index)
            val reachEnd = reachEnd(index)
            drawRoundRect(
                color = trackColour,
                topLeft = Offset(left - reachStart, 0f),
                size = Size(segmentWidth + reachStart + reachEnd, size.height),
                cornerRadius = radius,
            )
            left += segmentWidth + gapPx
        }
        left = 0f
        for (index in 0 until total) {
            val segmentWidth = room * shares[index]
            val reachStart = reachStart(index)
            val reachEnd = reachEnd(index)
            val fill = steps.fillOf(index)
            if (fill > 0f) {
                // The same reach as the track, so a filled segment merges with
                // its neighbours exactly as its track does — but only at the
                // end it actually reaches, and only into a neighbour the fill
                // goes on into. A part-filled segment's right edge is the value,
                // and the last full one's is the step it finished: either way
                // that end stays round where it stops, and does not run on past
                // it into a step that has not started.
                val fillEnd = if (fill >= 1f && animated > index + 1) reachEnd else 0f
                drawRoundRect(
                    color = colour,
                    topLeft = Offset(left - reachStart, 0f),
                    size = Size(segmentWidth * fill + reachStart + fillEnd, size.height),
                    cornerRadius = radius,
                )
            }

            // Same construction as `LinearProgress` — a band 35% as wide as a
            // segment, entering one end as it leaves the other, and kept inside
            // this segment so it cannot spill into the gaps.
            //
            // It used to be *clipped* to the segment, which is where the square
            // ends came from: the band is drawn round and `clipRect` then sliced
            // off whichever cap overhung, leaving a straight vertical edge.
            // Measured at 42 of 88 frames of a working animation, the end column
            // carrying the band's full 24 rows of ink instead of a tip's one or
            // two. `LinearProgress` has always avoided it by shrinking the rect
            // rather than cutting it, and that is what this does now.
            if (steps.chasing(index)) {
                // Across the whole row when it is indeterminate — occluded where
                // they pass over a gap, and not a chase per segment, which went
                // dark at every boundary — or inside the one working segment.
                val spanLeft = if (indeterminate) 0f else left
                val span = if (indeterminate) size.width else segmentWidth
                // The segment's own bounds, widened by however far it has
                // melted into its neighbours: while the row is one bar the bars
                // have to be able to cross the joins, or they blink at each one.
                val limitLeft = left - reachStart
                val limitRight = left + segmentWidth + reachEnd
                chasingBars(if (indeterminate) phase else chasePhase) { from, to ->
                    drawCapsule(
                        colour,
                        (spanLeft + span * from).coerceAtLeast(limitLeft),
                        (spanLeft + span * to).coerceAtMost(limitRight),
                        capsule,
                    )
                }
            }
            left += segmentWidth + gapPx
        }
    }
}

/**
 * Progress through a known number of discrete steps, as a ring.
 *
 * ```
 * CircularStepProgress(current = 2, total = 4, contentDescription = "Step 2 of 4")
 * ```
 *
 * [StepProgress] bent round into a [CircularProgress]'s ring: one arc per step,
 * from twelve o'clock, with a gap between each. Everything [StepProgress] does
 * it does the same way, through the same animation — a step filling before the
 * next one starts working, `current = null` melting the arcs into one ring with
 * the two chasing bars running round it, a still stub under reduced motion — so
 * the two can sit in one screen and read as one thing. For a space too small
 * for a row of steps: a card's corner, beside a title, round an avatar.
 *
 * @param current Which step is in progress, counting from one, or `null` when
 *   the step is not known. See [StepProgress].
 * @param working Whether [current] is itself still going: its arc carries the
 *   chasing bars instead of a fill. See [StepProgress].
 * @param stepProgress How far through [current] it has got, from 0 to 1: its
 *   arc filled that far, in place of [working]'s bars. `null` draws it full.
 *   See [StepProgress].
 * @param weights How much of the ring each step takes against the others, one
 *   a step: `listOf(1f, 3f)` makes the second arc three times the first. `null`,
 *   the default, shares it out evenly. See [StepProgress].
 * @param gap The space between arcs, along the ring. `0.dp` draws one continuous
 *   ring, filled to the end of the step it has reached.
 */
@Composable
fun CircularStepProgress(
    current: Int?,
    total: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    working: Boolean = false,
    stepProgress: Float? = null,
    colour: Color = Theme.colours.primary,
    trackColour: Color = trackAtTier(Theme.colours.outline),
    size: Dp = ProgressDefaults.CircularSize,
    strokeWidth: Dp = ProgressDefaults.CircularStrokeWidth,
    weights: List<Float>? = null,
    gap: Dp = ProgressDefaults.StepGap,
) {
    if (total <= 0) return
    val strings = Theme.strings
    val formats = Theme.formats
    val indeterminate = current == null
    val steps = rememberStepMotion(current, total, working, stepProgress)
    val shares = remember(total, weights) { stepShares(total, weights) }
    val lineCap = Theme.shapes.lineCap
    val pill = Theme.shapes.pill
    val traced = remember { RingTrack() }

    Canvas(
        modifier
            .size(size)
            // Counter-clockwise from twelve o'clock in a right-to-left reading,
            // as a row of steps runs from the right.
            .mirrorInRtl()
            .semantics {
                this.contentDescription = contentDescription ?: when {
                    indeterminate -> strings.inProgress
                    else -> strings.stepOfCount(formats.quantity(current), formats.quantity(total), steps.underWay)
                }
                progressBarRangeInfo = if (indeterminate) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    steps.rangeInfo()
                }
            }
    ) {
        val ring = StepRing(
            stroke = strokeWidth.toPx(),
            diameter = this.size.minDimension,
            shares = shares,
            gap = gap.toPx(),
            melt = steps.melt,
            // One step is one ring, with nowhere for a gap to go.
            seamless = gap <= 0.dp || total == 1,
        )
        val animated = steps.filled
        // Round the theme's pill, as [CircularProgress] is: the ring's own box
        // when the pill is squarer than a circle on it. 0.5.2.
        val ringBox = Size(ring.radius * 2f, ring.radius * 2f)
        val ringCorner = pill.cornerPx(ringBox, this)
        val track = if (ringCorner < ring.radius) traced.also { it.fit(ringBox, ringCorner) } else null
        track?.let { ring.keepClearOfCorners(it.degreesFor(ring.stroke * 1.5f)) }
        val at = Offset((this.size.width - ringBox.width) / 2f, (this.size.height - ringBox.height) / 2f)

        // Every track before any fill, as in [StepProgress].
        for (index in 0 until total) {
            drawRingCapsule(trackColour, ring, ring.start(index) - ring.reach, ring.end(index) + ring.reach, lineCap, track, at)
        }
        for (index in 0 until total) {
            val fill = steps.fillOf(index)
            if (fill > 0f) {
                // From twelve o'clock exactly, where the first step starts, and
                // to the end of the last full step; reaching into the next only
                // when the fill goes on into it. Round where it stops.
                val from = ring.start(index) - if (index == 0) 0f else ring.reach
                val to = when {
                    fill < 1f -> ring.start(index) + (ring.end(index) - ring.start(index)) * fill
                    // All the way round: past twelve o'clock and over where the
                    // fill began, so the ring closes without a pinch.
                    index == total - 1 -> ring.end(index) + ring.reach * 2f
                    animated > index + 1 -> ring.end(index) + ring.reach
                    else -> ring.end(index)
                }
                drawRingCapsule(colour, ring, from, to, lineCap, track, at)
            }
            if (steps.chasing(index)) {
                // Round the whole ring when it is indeterminate, from twelve
                // o'clock back to it; inside the working step's arc otherwise.
                val spanStart = if (indeterminate) ring.top else ring.start(index)
                val span = if (indeterminate) 360f else ring.end(index) - ring.start(index)
                val limitStart = ring.start(index) - ring.reach
                val limitEnd = ring.end(index) + ring.reach
                chasingBars(if (indeterminate) steps.phase else steps.chasePhase) { from, to ->
                    drawRingCapsule(
                        colour,
                        ring,
                        (spanStart + span * from).coerceAtLeast(limitStart),
                        (spanStart + span * to).coerceAtMost(limitEnd),
                        lineCap,
                        track,
                        at,
                    )
                }
            }
        }
    }
}

/**
 * The arithmetic of a [CircularStepProgress]'s ring, in degrees clockwise from
 * three o'clock, as `drawArc` takes them.
 *
 * Each step is its share of the ring, from [stepShares], less a [gap] that closes as the
 * steps [melt] into one. Every arc is described by the stretch of ring its
 * *ink* covers, round ends included — as a [StepProgress] segment is by its
 * rect — and [drawRingCapsule] turns that into a round-capped arc. So the gap a
 * reader sees is [gap], and not [gap] less the two caps that a plain `drawArc`
 * would draw out into it.
 */
internal class StepRing(
    val stroke: Float,
    diameter: Float,
    private val shares: FloatArray,
    gap: Float,
    melt: Float,
    seamless: Boolean,
) {
    /** The radius of the stroke's centre line. */
    val radius: Float = ((diameter - stroke) / 2f).coerceAtLeast(0f)

    /** Twelve o'clock. */
    val top: Float = -90f

    /** Where each step's share of the ring begins, in degrees from [top]; the last entry is the whole ring. */
    private val begins: FloatArray = FloatArray(shares.size + 1).also { at ->
        for (index in shares.indices) at[index + 1] = at[index] + shares[index] * 360f
    }

    /** How far a round cap reaches past the end of its arc, in degrees. */
    val cap: Float = if (radius > 0f) degrees(stroke / 2f / radius) else 0f

    /**
     * The gap between steps, in degrees, closing as the ring melts. Never more
     * than half a step, so a ring of many small steps still draws each of them.
     */
    private val gapAngle: Float =
        if (seamless || radius <= 0f) 0f else (degrees(gap / radius) * (1f - melt)).coerceAtMost((shares.minOrNull() ?: 1f) * 180f)

    /**
     * How far an arc reaches into its neighbour, to hide the seam: a cap's
     * length, as far as the ring has melted. Every arc has a neighbour on both
     * sides — the last step's is the first — so, unlike a row's ends, none of
     * them is spared it.
     */
    val reach: Float = cap * if (seamless) 1f else melt

    /** Where step [index]'s ink starts. */
    fun start(index: Int): Float = top + begins[index] + gapAngle / 2f

    /**
     * Moves each step's boundary that falls near a corner of a square ring
     * along the edge it is on, until its gap and [stub] degrees of line beside
     * it are clear of the corner.
     *
     * On a circle a gap can fall anywhere. On a square one, a gap across a
     * corner took the corner out, and a gap just short of one left a stub of
     * line round it: an uneven ring's steps looked chopped, reported for 0.5.2
     * as *"it looks like it chops part of the line"*. The corners are at 45°,
     * 135°, 225° and 315° from the top, where a square's outline turns.
     */
    fun keepClearOfCorners(stub: Float) {
        if (gapAngle <= 0f) return
        val clear = gapAngle / 2f + stub
        for (i in 1 until begins.size - 1) {
            val at = begins[i]
            val corner = round((at - 45f) / 90f) * 90f + 45f
            val off = at - corner
            if (abs(off) < clear) {
                begins[i] = (corner + if (off < 0f) -clear else clear).coerceIn(begins[i - 1], begins[i + 1])
            }
        }
    }

    /** Where step [index]'s ink ends. */
    fun end(index: Int): Float = top + begins[index + 1] - gapAngle / 2f

    private fun degrees(radians: Float): Float = radians * 180f / PI.toFloat()
}

/**
 * The ring's arc whose ink runs from [from] to [to] degrees, round at both ends
 * however short it is: a sliver too short for its two caps is a dot that wide,
 * as [drawCapsule] draws one along a bar, so a chasing bar arrives as a point
 * and stretches into an arc.
 */
internal fun DrawScope.drawRingCapsule(
    colour: Color,
    ring: StepRing,
    from: Float,
    to: Float,
    cap: StrokeCap,
    track: RingTrack? = null,
    at: Offset = Offset.Zero,
) {
    val sweep = to - from
    if (sweep <= 0f || ring.radius <= 0f) return
    // A ring that is the theme's squarer pill rather than a circle: the same
    // run along its box, its caps placed by the same rule as below. 0.5.2.
    if (track != null) {
        val capDegrees = if (cap == StrokeCap.Butt) 0f else track.degreesFor(ring.stroke / 2f)
        when {
            sweep >= capDegrees * 2f ->
                track.drawRun(this, colour, at, from + capDegrees, sweep - capDegrees * 2f, Stroke(width = ring.stroke, cap = cap))
            cap == StrokeCap.Round ->
                drawCircle(colour, radius = track.length * sweep / 360f / 2f, center = track.pointAt(at, (from + to) / 2f))
            else -> track.drawRun(this, colour, at, from, sweep, Stroke(width = ring.stroke, cap = StrokeCap.Butt))
        }
        return
    }
    // A theme whose lines end square ([io.kontour.ui.theme.Shapes.lineCap]):
    // `Butt` stops exactly at both ends, and a sliver is an arc that short
    // rather than a dot. `Square` carries on by half the stroke, as a round cap
    // does, so it is placed as a round one is. 0.5.2.
    if (cap == StrokeCap.Butt || (cap != StrokeCap.Round && sweep < ring.cap * 2f)) {
        drawArc(
            color = colour,
            startAngle = from,
            sweepAngle = sweep.coerceAtMost(360f),
            useCenter = false,
            topLeft = Offset(
                (size.width - ring.radius * 2f) / 2f,
                (size.height - ring.radius * 2f) / 2f,
            ),
            size = Size(ring.radius * 2f, ring.radius * 2f),
            style = Stroke(width = ring.stroke, cap = StrokeCap.Butt),
        )
        return
    }
    if (sweep >= ring.cap * 2f) {
        // Centred, so a canvas that is not square still draws a round ring.
        drawArc(
            color = colour,
            startAngle = from + ring.cap,
            sweepAngle = (sweep - ring.cap * 2f).coerceAtMost(360f),
            useCenter = false,
            topLeft = Offset(
                (size.width - ring.radius * 2f) / 2f,
                (size.height - ring.radius * 2f) / 2f,
            ),
            size = Size(ring.radius * 2f, ring.radius * 2f),
            style = Stroke(width = ring.stroke, cap = cap),
        )
    } else {
        val mid = (from + to) / 2f * PI.toFloat() / 180f
        val length = sweep * PI.toFloat() / 180f * ring.radius
        drawCircle(
            color = colour,
            radius = length / 2f,
            center = Offset(
                size.width / 2f + ring.radius * cos(mid),
                size.height / 2f + ring.radius * sin(mid),
            ),
        )
    }
}

/**
 * Each of [total] steps' share of the whole, from [weights], adding up to one:
 * even when there are none, or not one above nothing for every step. A wrong
 * count is not guessed at, since padding or cutting it would put a step's
 * length on another step.
 */
internal fun stepShares(total: Int, weights: List<Float>?): FloatArray {
    if (weights == null || weights.size != total || !weights.all { it > 0f && it.isFinite() }) {
        return FloatArray(total) { 1f / total }
    }
    val sum = weights.sum()
    return FloatArray(total) { weights[it] / sum }
}

/**
 * What a stepped progress indicator animates, shared by [StepProgress] and
 * [CircularStepProgress] so the bar and the ring move the same way: how far the
 * fill has got, how far the steps have melted into one, and where the two
 * chases are. Every value is read in draw.
 */
internal class StepMotion(
    val current: Int?,
    val total: Int,
    val working: Boolean,
    /** How far through [current] it is, 0 to 1, or null for a step not measured. */
    val within: Float?,
    /** Whether anything loops: a chase is running, and motion is not reduced. */
    val animating: Boolean,
    private val fill: State<Float>,
    private val meltState: State<Float>,
    private val sweep: State<Float>?,
    private val chase: Animatable<Float, AnimationVector1D>,
    private val chaseStep: IntState,
) {
    val indeterminate: Boolean get() = current == null

    /**
     * Whether [current] is still going, by either measure: working with no
     * telling how far, or [within] some way through. What a screen reader is
     * told about the step; how far is the range's.
     */
    val underWay: Boolean get() = working || within != null

    /**
     * The step it is on, and with [within] how far into it, as
     * `LinearProgress` reports its value: the number asked for, not the fill
     * on its way to it. Continuous rather than in steps once a step is
     * measured, since a value between steps is then the point.
     *
     * Only a step inside the row is part-way: one past the end is drawn full
     * and one before the start empty, and the number has to say what the bar
     * shows. Step 6 of 4 at half way was heard as three and a half of four
     * under a bar drawn full.
     */
    fun rangeInfo(): ProgressBarRangeInfo {
        val step = (current ?: 0).coerceIn(0, total)
        return if (within == null || current !in 1..total) {
            ProgressBarRangeInfo(current = step.toFloat(), range = 0f..total.toFloat(), steps = total - 1)
        } else {
            ProgressBarRangeInfo(current = step - 1 + within, range = 0f..total.toFloat())
        }
    }

    /** The steps filled, fractional while the fill springs between them. */
    val filled: Float get() = fill.value

    /** How far the steps have melted into one continuous run. 1 is fully merged. */
    val melt: Float get() = meltState.value

    /** The indeterminate chase's phase, or 0 when nothing is animating. */
    val phase: Float get() = sweep?.value ?: 0f

    /** The working step's chase, or NaN for any step it is not running for. */
    val chasePhase: Float
        get() = if (animating && !indeterminate && chaseStep.intValue == current) chase.value else Float.NaN

    /**
     * Whether step [index], from zero, is the one working.
     *
     * A band travels: across the whole row when the row is indeterminate,
     * inside one segment when a known step is working. A segment that is both
     * solid and animated says two things at once, so a band replaces the fill
     * rather than sitting on top of it.
     */
    fun busy(index: Int): Boolean =
        current != null && working && within == null && index == current.coerceIn(1, total) - 1

    /**
     * How full step [index] is drawn, 0 to 1: partly, on the step in progress,
     * so a step that is halfway does not read as not started.
     */
    fun fillOf(index: Int): Float = when {
        // Nothing, unless there is no band to draw it.
        //
        // It used to light one whole segment at a time — `(phase *
        // total).toInt()`, which takes a continuous phase and throws all of it
        // away but the integer part. What that draws is a segment snapping on,
        // holding, and snapping to the next: a stepped animation for a state
        // whose whole meaning is "this has no steps, it is just going". The
        // band was already right here for the `working` case below, and had
        // been since it was written.
        //
        // Under reduced motion nothing travels, so the first segment carries a
        // static stub instead — the same answer `LinearProgress` gives, and for
        // the same reason: neither filled nor animated is invisible.
        indeterminate -> if (animating || index != 0) 0f else BandFraction
        // Static under reduced motion, so a busy step still reads as working
        // without the travel.
        busy(index) -> if (animating) 0f else BandFraction
        else -> (filled - index).coerceIn(0f, 1f)
    }

    /** Whether a band is running in step [index] now: across all of them when indeterminate. */
    fun chasing(index: Int): Boolean = when {
        !animating -> false
        indeterminate -> true
        // Waiting for the fill before it: nothing yet.
        else -> busy(index) && !chasePhase.isNaN()
    }
}

@Composable
internal fun rememberStepMotion(current: Int?, total: Int, working: Boolean, stepProgress: Float?): StepMotion {
    val motion = Theme.motion
    val indeterminate = current == null
    // Only of a step that is known, and in place of the bars: see `StepProgress`.
    val within = if (current == null) null else stepProgress?.coerceIn(0f, 1f)
    val chases = working && within == null

    // The steps drawn full: those before a working step, or up to and including
    // the current one. A step that is working is not done, so it is not filled
    // — it carries the bars — and when it is done the fill runs across it
    // before the bars start in the next. A measured step is filled as far as
    // it has got, on the same spring, so it reads as the rest of the row does.
    val completed = when {
        current == null -> 0f
        within != null -> (current - 1).coerceIn(0, total) + if (current in 1..total) within else 0f
        chases -> (current - 1).coerceIn(0, total).toFloat()
        else -> current.coerceIn(0, total).toFloat()
    }
    val filled = animateFloatAsState(
        targetValue = completed,
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "stepProgress",
    )

    // Two different unknowns, and they animate differently.
    //
    // `current == null` is "how many steps there are, but not which one you are
    // on" — a wizard restoring a session, a checkout waiting on a server that
    // has not said where it got to. The alternative was to pass `0`, which
    // claims you are at the start rather than that nobody knows.
    //
    // `working` is the other one: the step *is* known and it is the step that is
    // busy. That wants a band travelling inside one segment, the way
    // [LinearProgress] indeterminate does, and it shares that arithmetic below
    // rather than restating it.
    //
    // The walk used to light several segments at once through a triangular
    // falloff, which read as a glow crossing the row rather than as one step
    // working. It lights one at a time now.
    //
    // Under reduced motion both stop: the travel is what says "working", and a
    // looping animation is exactly what that setting is asking to be spared.
    //
    // Gated around the transition, not after it. `val phase = if (animating)
    // sweep else 0f` is what this was, and it reads correctly while doing
    // nothing at all for the cost: `rememberInfiniteTransition` subscribes to
    // the frame clock when it is composed, so a settled row of steps went on
    // requesting sixty frames a second to draw a picture that could not change.
    val animating = (indeterminate || chases) && !motion.reduceMotion

    /**
     * How far the row has melted into one continuous bar. 1 is fully merged.
     *
     * A row whose whole meaning is "this has no steps, it is just going" was
     * still being drawn as steps: five capsules with four gaps, and a band
     * travelling behind them that went dark at every boundary. Measured as five
     * runs of ink along the row where there should be one.
     *
     * `animateFloatAsState` on the *state*, deliberately not folded into the
     * `rememberInfiniteTransition` above — that one is only composed while
     * something is animating, and joining it would subscribe a settled row of
     * steps to the frame clock forever. `IdleAnimationTest` is the assertion
     * that keeps this honest.
     */
    val meltState = animateFloatAsState(
        targetValue = if (indeterminate) 1f else 0f,
        animationSpec = motion.tweenDefault(),
        label = "stepMelt",
    )
    val sweep = if (animating && indeterminate) {
        rememberInfiniteTransition(label = "stepSweep").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = ChaseCycle, easing = LinearEasing),
            ),
            label = "stepSweepValue",
        )
    } else {
        null
    }

    /**
     * The working step's chase: where it is in its cycle, or NaN before it has
     * started.
     *
     * Its own loop rather than the transition above, because it has to *wait*:
     * on a step moving on, the fill runs across the step just finished, and only
     * once it has arrived do the bars enter the new one — from the start of
     * their cycle, not from wherever a shared clock had got to. Keyed on the step,
     * so each one starts afresh; and only composed while a step is working, so a
     * settled row asks for no frames.
     */
    val chase = remember { Animatable(Float.NaN) }
    // **Which step the chase is running for**, and nothing is drawn for any other.
    //
    // The step moves on in composition, and the chase is reset by an effect that
    // runs after it — so for a frame the new step was drawn at the *old* step's
    // phase, then blank while its fill ran, then its own bars from the start.
    // Reported from a phone as the working animation flickering in and out when
    // "Next step" is pressed mid-chase. Owned by a step, a phase cannot be drawn
    // for the wrong one. Read in draw, so it costs no recomposition.
    val chaseStep = remember { mutableIntStateOf(NoStep) }
    if (animating && !indeterminate) {
        LaunchedEffect(current, total) {
            chaseStep.intValue = NoStep
            chase.snapTo(Float.NaN)
            snapshotFlow { filled.value }.first { abs(it - completed) < FillArrived }
            while (true) {
                chase.snapTo(0f)
                chaseStep.intValue = current
                chase.animateTo(1f, tween(durationMillis = ChaseCycle, easing = LinearEasing))
            }
        }
    }

    return StepMotion(current, total, working, within, animating, filled, meltState, sweep, chase, chaseStep)
}

/** No step's chase: before one has started, and between steps. */
private const val NoStep = -1

/**
 * How wide the travelling indeterminate band is, as a fraction of what it runs
 * inside.
 *
 * Shared by [LinearProgress], which runs it across the whole track, by
 * [StepProgress]'s `working`, which confines it to one segment, and by the
 * timelines' rail, which runs it along the leg ahead of the traveller. One
 * number because they are meant to read as the same animation at three scales.
 */
internal const val BandFraction = 0.35f

/** How long that band takes to cross, whatever it is crossing. */
internal const val BandTravel = 1400

/**
 * The two bars of an indeterminate [LinearProgress], and of [StepProgress]'s
 * melted row and working segment, at [phase] of their cycle: each as the stretch
 * of the track it covers, 0 to 1, handed to [bar] if it is showing.
 *
 * Material's keyframes: a long bar whose front sweeps out fast and whose back
 * follows late, then a short one after it that is quicker still. Each end eases
 * on its own curve, so the bars stretch and shrink as they go rather than
 * sliding at a fixed width — which is what makes it read as working rather than
 * as a loop.
 */
internal inline fun chasingBars(phase: Float, bar: (from: Float, to: Float) -> Unit) {
    val time = phase * ChaseCycle
    for (line in ChaseLines) {
        val front = line.front.at(time)
        val back = line.back.at(time)
        if (front > back) bar(back, front)
    }
}

/**
 * A bar from [left] to [right], round at both ends however short it is.
 *
 * A rounded rect narrower than it is tall has no room for its corners — Skia
 * scales them down, and a sliver comes out square. The chase grows each bar from
 * nothing and shrinks it back, so there is always a sliver somewhere: drawn as
 * a circle that wide instead, it arrives as a point, swells to a dot and
 * stretches into a bar.
 */
internal fun DrawScope.drawCapsule(colour: Color, left: Float, right: Float, shape: CornerBasedShape) {
    val width = right - left
    if (width <= 0f) return
    if (width >= size.height) {
        val bar = Size(width, size.height)
        drawRoundRect(
            color = colour,
            topLeft = Offset(left, 0f),
            size = bar,
            cornerRadius = shape.cornerRadius(bar, this),
        )
    } else {
        drawDot(colour, radius = width / 2f, center = Offset(left + width / 2f, size.height / 2f), shape = shape)
    }
}

/** One end of a chasing bar: when it starts, how long it takes and how it eases. */
internal class ChaseEnd(private val delay: Int, private val duration: Int, private val easing: Easing) {
    fun at(time: Float): Float = easing.transform(((time - delay) / duration).coerceIn(0f, 1f))
}

internal class ChaseLine(val front: ChaseEnd, val back: ChaseEnd)

/** One chase, both bars. */
internal const val ChaseCycle: Int = 1800

internal val ChaseLines: List<ChaseLine> = listOf(
    ChaseLine(
        front = ChaseEnd(0, 750, CubicBezierEasing(0.2f, 0f, 0.8f, 1f)),
        back = ChaseEnd(333, 850, CubicBezierEasing(0.4f, 0f, 1f, 1f)),
    ),
    ChaseLine(
        front = ChaseEnd(1000, 567, CubicBezierEasing(0f, 0f, 0.65f, 1f)),
        back = ChaseEnd(1267, 533, CubicBezierEasing(0.1f, 0f, 0.45f, 1f)),
    ),
)

/** How close the fill has to be to a step before the bars start in the next. */
private const val FillArrived: Float = 0.02f

/** What the progress indicators take by default. */
object ProgressDefaults {
    /** A [LinearProgress] bar's thickness. */
    val LinearHeight: Dp = 6.dp

    /** A [CircularProgress] ring's diameter. */
    val CircularSize: Dp = 40.dp

    /** A [CircularProgress] ring's stroke. */
    val CircularStrokeWidth: Dp = 4.dp

    /** A [StepProgress] segment's thickness. */
    val StepHeight: Dp = 4.dp

    /** The space between [StepProgress] segments. */
    val StepGap: Dp = 4.dp
}
