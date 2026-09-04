package io.kontour.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.adaptive.AspectRatioBox
import io.kontour.ui.adaptive.ListDetailPaneScaffold
import io.kontour.ui.adaptive.PaneFocus
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.adaptive.windowSizeClass
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.TextToolbarAction
import io.kontour.ui.components.text.TextSelectionToolbar
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.VerticalDivider
import io.kontour.ui.motion.GlassSurface
import io.kontour.ui.motion.atmosphere
import io.kontour.ui.nav.TopBar
import io.kontour.ui.components.text.rememberImeChain
import io.kontour.ui.foundation.Scrim
import io.kontour.ui.theme.Theme

// --- Foundation -----------------------------------------------------------

internal val TextDemo = ComponentDemo(slug = "text") {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Text("displaySmall", style = Theme.typography.displaySmall)
        Text("titleMedium", style = Theme.typography.titleMedium)
        Text("bodyMedium — the default for prose", style = Theme.typography.bodyMedium)
        Text(
            "labelSmall, muted",
            style = Theme.typography.labelSmall,
            colour = Theme.colours.contentMuted,
        )
        // The AnnotatedString overload is the whole reason there are two: a
        // route number in the accent colour inside a sentence, without a second
        // component and without breaking the line box.
        Text(
            buildAnnotatedString {
                append("The ")
                withStyle(SpanStyle(color = Theme.colours.accent.solid)) { append("950") }
                append(" leaves in 4 minutes.")
            },
            style = Theme.typography.bodyMedium,
        )
    }
}

internal val IconDemo = ComponentDemo(slug = "icon") {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Tabler.Outline.Bus, contentDescription = "Bus routes")
        Icon(Tabler.Outline.Star, contentDescription = null, size = Theme.sizing.iconLarge)
        // Inside a coloured surface, to show the tint following content colour
        // rather than being passed in.
        Surface(colour = Theme.colours.primary, shape = Theme.shapes.small) {
            Box(Modifier.padding(Theme.spacing.sm)) {
                Icon(Tabler.Outline.Star, contentDescription = null)
            }
        }
    }
}

internal val SurfaceDemo = ComponentDemo(slug = "surface") {
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        listOf(
            "surface" to Theme.colours.surface,
            "sunken" to Theme.colours.surfaceSunken,
            "primary" to Theme.colours.primary,
        ).forEach { (name, colour) ->
            Surface(
                colour = colour,
                shape = Theme.shapes.medium,
                shadow = Theme.elevation.low,
            ) {
                Column(Modifier.padding(Theme.spacing.md)) {
                    // No colour argument anywhere below: the surface set
                    // `LocalContentColour` and both children resolved against it.
                    Text(name, style = Theme.typography.labelMedium)
                    Icon(Tabler.Outline.Star, contentDescription = null)
                }
            }
        }
    }
}

internal val DividerDemo = ComponentDemo(slug = "divider") {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Text("Above", style = Theme.typography.bodyMedium)
        HorizontalDivider()
        Text("Below", style = Theme.typography.bodyMedium)
        Row(
            modifier = Modifier.height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Left", style = Theme.typography.bodyMedium)
            VerticalDivider(Modifier.height(24.dp))
            Text("Right", style = Theme.typography.bodyMedium)
        }
    }
}

private val scrimDim = Knob.Flag("Dimmed", initial = true)

internal val ScrimDemo = ComponentDemo(slug = "scrim", knobs = listOf(scrimDim)) {
    // `fraction` is a lambda rather than a Float: a sheet reads its own drag
    // offset through it every frame, so the dimming tracks the gesture without
    // the scrim recomposing.
    val fraction = if (this[scrimDim]) 1f else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(Theme.shapes.medium)
            .background(Theme.colours.surfaceSunken),
    ) {
        Column(Modifier.padding(Theme.spacing.md)) {
            Text("Perth Underground", style = Theme.typography.titleSmall)
            Text(
                "Content behind the scrim, so there is something to dim.",
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
        }
        Scrim(fraction = { fraction }, onDismissRequest = { echo("Dismissed") })
    }
}

// --- Adaptive -------------------------------------------------------------

internal val ScaffoldDemo = ComponentDemo(slug = "scaffold") {
    Box(
        Modifier
            .fillMaxWidth()
            .height(280.dp)
            .border(Theme.sizing.borderWidth, Theme.colours.outline, Theme.shapes.medium)
            .clip(Theme.shapes.medium),
    ) {
        Scaffold(
            topBar = { TopBar { +"Favourites" } },
            floatingActionButton = {
                FloatingActionButton(
                    icon = Tabler.Outline.Star,
                    contentDescription = "Add",
                    onClick = { echo("Add") },
                )
            },
        ) { padding ->
            // The padding is handed over rather than applied, so content can
            // scroll under the bar instead of starting below it.
            Column(Modifier.fillMaxSize().padding(padding)) {
                ListGroup(spacing = 2.dp) {
                    item(label = "Perth Underground", supporting = "Platform 2")
                    item(label = "Elizabeth Quay", supporting = "Platform 1")
                    item(label = "Perth Busport", supporting = "Stand 24")
                }
            }
        }
    }
}

/**
 * A handle between the two panes that the user can drag.
 *
 * Only means anything at a width that shows both — on a phone there is one pane
 * and nothing to divide — so it is off by default and the frame below is wide
 * enough here to make it reachable.
 */
private val paneResizable = Knob.Flag("Resizable")

internal val PaneScaffoldDemo = ComponentDemo(
    slug = "pane-scaffold",
    knobs = listOf(paneResizable),
) {
    var focus by remember { mutableStateOf(PaneFocus.List) }
    var selected by remember { mutableStateOf(1) }
    val stops = listOf("Perth Underground", "Elizabeth Quay", "Perth Busport", "McIver")

    Box(
        Modifier
            .fillMaxWidth()
            .height(280.dp)
            .border(Theme.sizing.borderWidth, Theme.colours.outline, Theme.shapes.medium)
            .clip(Theme.shapes.medium),
    ) {
        // Its own size-class provider: the scaffold shows one pane or two from
        // the width of *this box*, which is what makes the behaviour visible in
        // a card rather than only on a tablet.
        WindowSizeClassProvider(Modifier.fillMaxSize()) {
            ListDetailPaneScaffold(
                focus = focus,
                onBack = { focus = PaneFocus.List },
                resizable = this@ComponentDemo[paneResizable],
                list = {
                    ListGroup(spacing = 2.dp) {
                        stops.forEachIndexed { index, name ->
                            item(
                                label = name,
                                selected = index == selected,
                                onClick = { selected = index; focus = PaneFocus.Detail },
                            )
                        }
                    }
                },
                detail = {
                    Column(Modifier.padding(Theme.spacing.md)) {
                        Text(stops[selected], style = Theme.typography.titleMedium)
                        Text(
                            "Departures, alerts and the route map would go here.",
                            style = Theme.typography.bodySmall,
                            colour = Theme.colours.contentMuted,
                        )
                    }
                },
            )
        }
    }
}

internal val WindowSizeClassDemo = ComponentDemo(slug = "window-size-class") {
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        listOf(360.dp, 700.dp, 1000.dp).forEach { width ->
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
                Box(
                    Modifier
                        .width(width / 3)
                        .height(80.dp)
                        .border(Theme.sizing.borderWidth, Theme.colours.outline, Theme.shapes.small)
                        .clip(Theme.shapes.small),
                ) {
                    // Measured, not assumed: each box reports its own class, so
                    // three of them side by side on one desktop report three
                    // different answers.
                    WindowSizeClassProvider(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                windowSizeClass.width.name,
                                style = Theme.typography.labelSmall,
                            )
                        }
                    }
                }
                Text(
                    "${(width / 3).value.toInt()}dp",
                    style = Theme.typography.labelSmall,
                    colour = Theme.colours.contentMuted,
                )
            }
        }
    }
}

internal val GlassSurfaceDemo = ComponentDemo(slug = "glass-surface") {
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(Theme.shapes.large)
            .atmosphere(),
    ) {
        Column(Modifier.padding(Theme.spacing.lg)) {
            Text("Get where you're going", style = Theme.typography.titleLarge)
            Text(
                "Live departures for every stop in Perth.",
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
        }
        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Theme.spacing.md)
                .height(48.dp)
                .fillMaxWidth(0.8f),
            shape = Theme.shapes.pill,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "translucent, not blurred",
                    style = Theme.typography.labelSmall,
                    colour = Theme.colours.contentMuted,
                )
            }
        }
    }
}

internal val AspectRatioBoxDemo = ComponentDemo(slug = "aspect-ratio-box") {
    var loaded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        AspectRatioBox(
            ratio = 16f / 9f,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .clip(Theme.shapes.medium)
                .background(
                    if (loaded) Theme.colours.accent.container else Theme.colours.surfaceSunken,
                ),
        ) {
            Text(
                if (loaded) "the photo, 16:9" else "16:9 reserved",
                style = Theme.typography.labelSmall,
                colour = Theme.colours.contentSubtle,
            )
        }
        Text(
            "The text below does not move when the content arrives, which is " +
                "the whole point.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
        Button(
            onClick = { loaded = !loaded },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Small,
        ) { +(if (loaded) "Unload" else "Load the photo") }
    }
}

// --- Text editing ---------------------------------------------------------

internal val ImeChainDemo = ComponentDemo(slug = "ime-chain") {
    val from = rememberTextFieldState("Perth Underground")
    val to = rememberTextFieldState()
    val chain = rememberImeChain("from", "to")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        TextField(
            state = from,
            label = "From",
            imeChain = chain["from"],
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            state = to,
            label = "To",
            placeholder = "Where to?",
            imeChain = chain["to"],
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "The first field's action key says Next; the last one says Done.",
            style = Theme.typography.labelSmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

/**
 * Whether there is an app action to add to the selection menu.
 *
 * On a phone this is the whole component: with no extra actions
 * `TextSelectionToolbar` returns its content untouched and the platform's own
 * toolbar comes up — the one that knows about the system clipboard, Look Up and
 * Translate — and passing one trades that surface for a drawn one, because
 * neither platform gives any way to append to theirs.
 *
 * You are almost certainly reading this in a browser, where there is no such
 * surface to trade. So here the drawn toolbar comes up either way, and the flag
 * only decides whether "Plan a trip" is on it.
 */
private val toolbarActions = Knob.Flag("App action", initial = true)

internal val TextToolbarDemo = ComponentDemo(
    slug = "text-toolbar",
    knobs = listOf(toolbarActions),
) {
    val state = rememberTextFieldState("Select this text to see the toolbar.")
    val extra = this[toolbarActions]
    // Rebuilt when the flag moves, and not otherwise: the actions list is read
    // through `rememberUpdatedState` inside the toolbar, so a new list on every
    // recomposition would be a new list for no reason.
    val actions = remember(extra) {
        if (extra) {
            listOf(TextToolbarAction("Plan a trip") { echo("Plan a trip") })
        } else {
            emptyList()
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        TextSelectionToolbar(actions = actions) {
            TextField(state = state, label = "Try it", modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = if (extra) {
                "Selecting text gives the library's toolbar: the verbs the " +
                    "framework offered, plus “Plan a trip”. A verb the platform " +
                    "did not offer is absent rather than greyed out, and past " +
                    "four items the rest go behind More."
            } else {
                "On desktop and the web this still draws — there is no system " +
                    "selection toolbar to defer to, and Compose's fallback is a " +
                    "bare popup. On Android and iOS, with no actions to add, the " +
                    "field is handed straight back and the system's own menu " +
                    "comes up instead."
            },
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val foundationDemos = listOf(
    TextDemo,
    IconDemo,
    SurfaceDemo,
    DividerDemo,
    ScrimDemo,
    ScaffoldDemo,
    PaneScaffoldDemo,
    WindowSizeClassDemo,
    GlassSurfaceDemo,
    AspectRatioBoxDemo,
    ImeChainDemo,
    TextToolbarDemo,
)
