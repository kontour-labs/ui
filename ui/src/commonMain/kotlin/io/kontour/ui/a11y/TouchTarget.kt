package io.kontour.ui.a11y

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.LocalSizing
import kotlin.math.max

/**
 * Guarantees this component is at least as large as the platform's minimum
 * touch target, without changing how large it *looks*.
 *
 * A 20dp checkbox stays a 20dp checkbox on screen; it just reserves 48dp of
 * layout space and centres itself in it, so the tappable area matches what a
 * fingertip actually covers. Reserving the space — rather than only widening
 * the hit rectangle — is what stops two adjacent small controls from having
 * overlapping, ambiguous touch areas.
 *
 * The target shrinks to [TouchTargetDefaults.PointerMinimum] when the active input is a
 * mouse, because 48dp of padding around a 16dp icon looks absurd on a desktop
 * toolbar and a mouse does not need the slack. It grows straight back the
 * moment a finger touches the screen. See [io.kontour.ui.input.InputModality].
 *
 * Applied automatically by every interactive component in the design system.
 * Reach for it directly only when you are building a new interactive component
 * out of raw foundation pieces.
 *
 * ```
 * Box(
 *     Modifier
 *         .minimumTouchTarget()
 *         .size(20.dp)          // the visual size
 *         .clickable(onClick = onToggle)
 * )
 * ```
 *
 * @param enabled Pass `false` for a component that is decorative or whose hit
 *   area is deliberately managed by a parent — a segment inside a slider track,
 *   for instance. Prefer leaving it on.
 * @param fill Grow the content to the reserved target instead of centring it in
 *   there. Off by default, because the default is the right answer for a control
 *   with a drawn shape: a 20dp checkbox that filled its target would be a 48dp
 *   checkbox.
 *
 *   On for a **row**, where the reserved slack is the difference between the
 *   thing you press and the thing that lights up. A menu row is content plus
 *   8dp of padding — about 36dp — floating in a 48dp slot, so its highlight, its
 *   clip and its press ripple all stopped 6dp short of the target top and
 *   bottom while reaching the panel's edge at the sides. That reads as a menu
 *   with 10dp of margin above its first row and 4dp beside it, which is what was
 *   reported. Nothing about it is visible on a desktop, where the minimum is
 *   24dp and a row already clears it.
 */
fun Modifier.minimumTouchTarget(enabled: Boolean = true, fill: Boolean = false): Modifier =
    if (enabled) this then MinimumTouchTargetElement(fill) else this

/** The sizes [minimumTouchTarget] works to. */
object TouchTargetDefaults {
    /**
     * The target for a precise pointer — WCAG 2.2 SC 2.5.8 "Target Size
     * (Minimum)": 24×24 CSS pixels. A finger gets the theme's
     * `Sizing.minTouchTarget` instead.
     */
    val PointerMinimum: Dp = 24.dp
}

/**
 * Set by a parent that guarantees the touch target on its children's behalf.
 *
 * A `ButtonGroup`, a `SegmentedControl` or a `Toolbar` is **one control made of
 * parts**, and the parts must sit flush. If each part reserves its own 48dp,
 * the reserved slack becomes real space between them: a group of three 40dp
 * icon buttons joined by a 1dp seam renders that seam at **9dp** on Android —
 * 4dp of transparent padding either side — and the joined group falls apart
 * into three loose buttons, which is the one thing it exists to prevent. On
 * desktop it looked perfect, because the JVM's minimum is 24dp and nothing here
 * is smaller than that, so the modifier did nothing at all.
 *
 * The parent takes on the duty instead: it sizes the whole row to at least
 * `Theme.sizing.minTouchTarget` tall, and each segment is then a full-height
 * strip that a fingertip can hit. The target is not lost, it is relocated to
 * the thing that is actually one target.
 *
 * `staticCompositionLocalOf` because it changes only when a subtree is built,
 * never while one is alive.
 */
internal val LocalTouchTargetOwnedByParent = staticCompositionLocalOf { false }

private data class MinimumTouchTargetElement(
    private val fill: Boolean,
) : ModifierNodeElement<MinimumTouchTargetNode>() {
    override fun create() = MinimumTouchTargetNode(fill)

    override fun update(node: MinimumTouchTargetNode) {
        node.fill = fill
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "minimumTouchTarget"
        properties["fill"] = fill
    }
}

private class MinimumTouchTargetNode(var fill: Boolean) :
    Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // A parent that has taken the duty on gets no expansion here — see
        // [LocalTouchTargetOwnedByParent]. Read in `measure` rather than in
        // composition so a group can wrap children it did not compose itself.
        if (currentValueOf(LocalTouchTargetOwnedByParent)) {
            val owned = measurable.measure(constraints)
            return layout(owned.width, owned.height) { owned.place(0, 0) }
        }

        val modality = currentValueOf(LocalInputModality)
        val minimum = if (modality.needsLargeTargets) {
            currentValueOf(LocalSizing).minTouchTarget
        } else {
            TouchTargetDefaults.PointerMinimum
        }
        val minimumPx = minimum.roundToPx()

        // The whole of [fill] is here: raise the *minimum* the content is
        // measured against rather than measuring it loose and centring it.
        // Everything below then holds unchanged — the `max` is already
        // satisfied and the centring places at zero — so one branch covers both
        // behaviours and there is no second path to keep in step.
        //
        // The incoming maximum still wins, for the reason the `coerceAtMost`
        // below gives: inside a constrained row, growing past it pushes
        // siblings off the screen.
        val placeable = measurable.measure(
            if (fill) {
                constraints.copy(
                    minWidth = max(constraints.minWidth, minimumPx)
                        .coerceAtMost(constraints.maxWidth),
                    minHeight = max(constraints.minHeight, minimumPx)
                        .coerceAtMost(constraints.maxHeight),
                )
            } else {
                constraints
            }
        )

        // Never exceed the incoming maximum: inside a constrained row, growing
        // past it would push siblings off screen — a worse outcome than a
        // slightly small target.
        val width = max(placeable.width, minimumPx).coerceAtMost(constraints.maxWidth)
        val height = max(placeable.height, minimumPx).coerceAtMost(constraints.maxHeight)

        return layout(width, height) {
            // Centre the visual content inside the reserved area.
            placeable.place(
                x = (width - placeable.width) / 2,
                y = (height - placeable.height) / 2,
            )
        }
    }
}

/** True when this [InputModality] implies the larger, finger-sized target. */
internal val InputModality.usesTouchSizedTargets: Boolean
    get() = needsLargeTargets
