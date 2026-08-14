package io.kontour.ui.a11y

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
 * The target shrinks to [pointerMinTouchTarget] when the active input is a
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
 */
fun Modifier.minimumTouchTarget(enabled: Boolean = true): Modifier =
    if (enabled) this then MinimumTouchTargetElement else this

/** WCAG 2.2 SC 2.5.8 "Target Size (Minimum)": 24×24 CSS pixels. */
val pointerMinTouchTarget: Dp = 24.dp

private object MinimumTouchTargetElement : ModifierNodeElement<MinimumTouchTargetNode>() {
    override fun create() = MinimumTouchTargetNode()
    override fun update(node: MinimumTouchTargetNode) = Unit
    override fun hashCode() = "MinimumTouchTarget".hashCode()
    override fun equals(other: Any?) = other === this
    override fun InspectorInfo.inspectableProperties() {
        name = "minimumTouchTarget"
    }
}

private class MinimumTouchTargetNode :
    Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)

        val modality = currentValueOf(LocalInputModality)
        val minimum = if (modality.needsLargeTargets) {
            currentValueOf(LocalSizing).minTouchTarget
        } else {
            pointerMinTouchTarget
        }
        val minimumPx = minimum.roundToPx()

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

/** True when [modality] implies the larger, finger-sized target. */
internal val InputModality.usesTouchSizedTargets: Boolean
    get() = needsLargeTargets
