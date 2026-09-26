package io.kontour.ui.catalog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A back gesture played by hand, into the same dispatcher a real one arrives
 * at — for the desktop and the web, which have Escape and nothing to drag.
 *
 * A `NavigationEventInput` like the platform's own, so what it drives is
 * exactly what the Android gesture or iOS's edge pan would: the same handler
 * answers, with the same progress, and the same thing happens when it is let go.
 */
internal class ScrubberInput : NavigationEventInput() {
    /** Whether anything in the frame would answer back now. */
    var armed by mutableStateOf(false)
        private set

    /** Whether a gesture is being held. */
    var inProgress by mutableStateOf(false)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        armed = hasEnabledHandlers
    }

    fun start(edge: Int) {
        inProgress = true
        progress = 0f
        dispatchOnBackStarted(NavigationEvent(edge, 0f, 0f, 0f))
    }

    fun drag(to: Float, edge: Int) {
        progress = to
        dispatchOnBackProgressed(NavigationEvent(edge, to, 0f, 0f))
    }

    fun release() {
        inProgress = false
        progress = 0f
        dispatchOnBackCompleted()
    }

    fun cancel() {
        inProgress = false
        progress = 0f
        dispatchOnBackCancelled()
    }

    /** A key or a button: back with no gesture before it. */
    fun press() = dispatchOnBackCompleted()
}

/**
 * The simulator's controls: back at once, two gestures played out, and one held
 * wherever the slider leaves it, to look at.
 *
 * Greyed out while nothing in the frame would answer, which is itself the
 * model: at the root of a stack, with nothing open, back is not the frame's.
 */
@Composable
internal fun BackSimulator(input: ScrubberInput, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var edgeIndex by remember { mutableIntStateOf(0) }
    val edge = if (edgeIndex == 0) NavigationEvent.EDGE_LEFT else NavigationEvent.EDGE_RIGHT
    var playing by remember { mutableStateOf<Job?>(null) }
    val idle = playing == null && !input.inProgress
    val canStart = input.armed && idle

    fun play(to: Float, letGo: Boolean) {
        playing = scope.launch {
            try {
                input.start(edge)
                // A frame between the start and the first movement, as a hand
                // gives: the pages to move are made on the frame after it.
                withFrameNanos {}
                val travel = Animatable(0f)
                travel.animateTo(to, tween(PlayMillis, easing = FastOutSlowInEasing)) { input.drag(value, edge) }
                delay(HoldMillis)
                if (letGo) input.release() else input.cancel()
            } finally {
                if (input.inProgress) input.cancel()
                playing = null
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Text(
            if (input.armed || input.inProgress) {
                "Back reaches something in the frame. Escape is back on a keyboard."
            } else {
                "Nothing in the frame answers back now: it would go past it, to the gallery."
            },
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
        SegmentedControl(
            options = listOf("From the left edge", "From the right edge"),
            selectedIndex = edgeIndex,
            onSelectedIndexChange = { edgeIndex = it },
            enabled = idle,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            SimulatorButton("Back", enabled = canStart) { input.press() }
            SimulatorButton("Peek and let it return", enabled = canStart) { play(to = 0.4f, letGo = false) }
            SimulatorButton("Drag and let go", enabled = canStart) { play(to = 0.7f, letGo = true) }
        }
        Text(
            "Hold a gesture at ${(input.progress * 100).roundToInt()}%",
            style = Theme.typography.labelMedium,
        )
        Slider(
            value = input.progress,
            onValueChange = {
                if (!input.inProgress) input.start(edge)
                input.drag(it, edge)
            },
            enabled = playing == null && (input.armed || input.inProgress),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            SimulatorButton("Let go", enabled = input.inProgress && playing == null) { input.release() }
            SimulatorButton("Abandon", enabled = input.inProgress && playing == null) { input.cancel() }
        }
    }
}

@Composable
private fun SimulatorButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, variant = ButtonVariant.Secondary, size = ButtonSize.Small) {
        Text(label)
    }
}

/** How long a played gesture takes to reach where it stops. */
private const val PlayMillis = 450

/** How long it rests there, so the pose can be seen, before it is let go. */
private const val HoldMillis = 250L
