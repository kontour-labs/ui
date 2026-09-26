package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme

/**
 * Back and predictive back, in every place they have to work — an instrument,
 * like [HapticsLab], because back can only be judged by driving it.
 *
 * **One model, two feels.** Back goes to the innermost thing that can take it:
 * the top overlay, then a stack inside it, then a pane, then the page stack,
 * then out. That order is the same on every platform; what differs is how a
 * gesture looks while it runs — Android's predictive back or iOS's swipe — and
 * the page lets either be tried on either platform.
 *
 * The frame is an app of its own: its own overlay host, and its own back
 * dispatcher under the gallery's, so a real gesture on a phone drives it, and
 * the readout above it names what the dispatcher says back would reach. On the
 * desktop and the web, where there is no gesture, the simulator plays one into
 * the same dispatcher.
 */
@Composable
internal fun BackPage(modifier: Modifier = Modifier) {
    var scenario by rememberSaveable { mutableStateOf(BackScenario.Pages) }
    val platform = LocalBackStyle.current
    var style by rememberSaveable { mutableStateOf(platform) }
    var tablet by rememberSaveable { mutableStateOf(false) }
    var dismissible by rememberSaveable { mutableStateOf(true) }

    val parent = LocalNavigationEventDispatcherOwner.current
    val frame = remember(parent) {
        parent?.let { NavigationEventDispatcher(it.navigationEventDispatcher) } ?: NavigationEventDispatcher()
    }
    val owner = remember(frame) {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher: NavigationEventDispatcher = frame
        }
    }
    val scrubber = remember { ScrubberInput() }
    DisposableEffect(frame) {
        frame.addInput(scrubber)
        onDispose {
            frame.removeInput(scrubber)
            frame.dispose()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
                Text("Back and predictive back", style = Theme.typography.titleSmall)
                Text(
                    "Back goes to the innermost thing that can take it: the top overlay, then a stack inside it, " +
                        "then a pane, then the page stack, then out of the app. The order is the same everywhere; " +
                        "how a gesture looks while it runs is the platform's.",
                    style = Theme.typography.bodySmall,
                    colour = Theme.colours.contentMuted,
                )
                SegmentedControl(
                    options = listOf("Predictive back · Android", "Swipe back · iOS"),
                    selectedIndex = if (style == BackStyle.Predictive) 0 else 1,
                    onSelectedIndexChange = { style = if (it == 0) BackStyle.Predictive else BackStyle.Swipe },
                )
                SegmentedControl(
                    options = listOf("Phone", "Tablet"),
                    selectedIndex = if (tablet) 1 else 0,
                    onSelectedIndexChange = { tablet = it == 1 },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    for (each in BackScenario.entries) {
                        FilterChip(selected = each == scenario, onSelectedChange = { scenario = each }) { Text(each.title) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
                    Switch(checked = dismissible, onCheckedChange = { dismissible = it })
                    Text("Sheets and dialogs may be dismissed", style = Theme.typography.bodyMedium)
                }
                Text(scenario.note, style = Theme.typography.bodySmall)
            }
        }

        BackReadout(frame)

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = minOf(maxWidth, if (tablet) TabletWidth else PhoneWidth)
            val shape = Theme.shapes.large
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .width(width)
                    .height(FrameHeight)
                    .clip(shape)
                    .border(1.dp, Theme.colours.outline, shape)
                    .background(Theme.colours.background),
            ) {
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides owner,
                    LocalBackStyle provides style,
                ) {
                    WindowSizeClassProvider(Modifier.fillMaxSize()) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            // A fresh app for each scenario and each feel: a
                            // stack pushed in one is not left behind in the next.
                            key(scenario, style) {
                                BackScenarioContent(scenario, dismissible)
                            }
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
                Text("Simulator", style = Theme.typography.titleSmall)
                BackSimulator(scrubber)
            }
        }
    }
}

/**
 * What back would reach now, as the dispatcher itself says — not worked out
 * from the scenario, so it is the model being shown rather than a description
 * of it.
 */
@Composable
private fun BackReadout(frame: NavigationEventDispatcher) {
    val history by frame.history.collectAsState()
    val current = history.mergedHistory.getOrNull(history.currentIndex)
    Text(
        "Back goes to: " + describe(current),
        style = Theme.typography.labelLarge,
    )
}

/**
 * The handler's own name for itself, in words.
 *
 * Read from the info's class, which is every handler's identity here: the
 * library's are internal to it, and a gallery that asked for them to be public
 * so it could print them would be the gallery shaping the API.
 */
private fun describe(info: NavigationEventInfo?): String = when (info?.let { it::class.simpleName }) {
    null, "None" -> "nothing in the frame — it would go past it"
    "OverlayBackInfo" -> "the overlay on top"
    "SceneBackInfo" -> "the pane, which closes itself"
    "PaneBackInfo" -> "the detail, back to its list"
    "SceneInfo" -> "a Navigation 3 stack, which pops a page"
    else -> info::class.simpleName ?: "a handler"
}

private val PhoneWidth = 390.dp
private val TabletWidth = 880.dp
private val FrameHeight = 640.dp
