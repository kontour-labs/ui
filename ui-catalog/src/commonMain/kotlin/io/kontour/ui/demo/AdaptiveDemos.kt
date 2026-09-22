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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.adaptive.AspectRatioBox
import io.kontour.ui.adaptive.FabPosition
import io.kontour.ui.adaptive.ListDetailPaneScaffold
import io.kontour.ui.adaptive.PaneFocus
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.adaptive.SupportingPaneScaffold
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.adaptive.windowSizeClass
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.GlassSurface
import io.kontour.ui.motion.PageTransition
import io.kontour.ui.motion.atmosphere
import io.kontour.ui.motion.sharedBounds
import io.kontour.ui.motion.sharedElement
import io.kontour.ui.nav.TopBar
import io.kontour.ui.theme.Theme

/** The key both pages use for the card that becomes the header. */
private const val HeroKey = "stop-hero"

/** The name, which is the same run of text on both sides. */
private const val TitleKey = "stop-title"

/**
 * Which pane a narrow window is showing.
 *
 * The state the scaffold is *driven* by, and the whole of what collapsing
 * means: wide enough for two panes it changes nothing, and narrow enough for
 * one it decides which one. Pressing it is the only way to see the second half
 * of that without a tablet.
 */
private val paneFocus = Knob.Choice("Focus", PaneFocus.entries.toList(), PaneFocus.List)

private val paneResizable = Knob.Flag("Resizable")

/**
 * A handle between the two panes that the user can drag.
 *
 * Only means anything at a width that shows both — on a phone there is one pane
 * and nothing to divide — so it is off by default and the frame below is wide
 * enough here to make it reachable.
 */
/**
 * The two scaffolds, which are two answers to one question.
 *
 * `ListDetailPaneScaffold` is for panes that are *the same content at two
 * depths* — pick a stop, see the stop. `SupportingPaneScaffold` is for a main
 * thing with something beside it that helps, and its supporting pane is
 * dismissible where a detail pane is navigated back from. Under a narrow window
 * they collapse differently, which is the whole reason to have both and the
 * thing this knob shows.
 */
private val paneShape = Knob.Choice("Scaffold", listOf("List and detail", "Main and supporting"))

/**
 * Where the floating action button sits.
 *
 * Worth pressing rather than reading, because the choice is about reach rather
 * than about taste: `End` is under a right thumb, `Start` under a left one, and
 * `Center` is the compromise a bar of navigation underneath usually forces.
 */
private val scaffoldFab = Knob.Choice("FAB", FabPosition.entries.toList(), FabPosition.End)

internal val PageTransitionDemo = ComponentDemo(slug = "page-transition") {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(Theme.shapes.large)
            .background(Theme.colours.surfaceSunken),
    ) {
        PageTransition(target = open, modifier = Modifier.fillMaxSize()) { detail ->
            if (detail) {
                Column(Modifier.fillMaxSize()) {
                    Card(
                        modifier = Modifier
                            .sharedBounds(HeroKey, clip = Theme.shapes.large)
                            .fillMaxWidth()
                            .height(120.dp),
                    ) {
                        // `sharedElement`, not `sharedBounds`, and the pair is
                        // the distinction the page is about. The card is a
                        // *container* whose contents differ either side, so its
                        // bounds morph and its contents cross-fade. The title is
                        // the *same* text in both, so it travels instead — one
                        // glyph run moving, rather than one fading out under
                        // another fading in.
                        Text(
                            text = "Perth Underground",
                            modifier = Modifier.sharedElement(TitleKey),
                            style = Theme.typography.titleMedium,
                        )
                        Text(
                            "4 platforms · Mandurah, Joondalup, Airport",
                            style = Theme.typography.bodySmall,
                            colour = Theme.colours.contentMuted,
                        )
                    }
                    Box(
                        Modifier.fillMaxSize().padding(Theme.spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(
                            onClick = { open = false },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Small,
                        ) { +"Back" }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(Theme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                ) {
                    Card(
                        modifier = Modifier
                            .sharedBounds(HeroKey, clip = Theme.shapes.large)
                            .fillMaxWidth(),
                        onClick = { open = true },
                    ) {
                        Text(
                            text = "Perth Underground",
                            modifier = Modifier.sharedElement(TitleKey),
                            style = Theme.typography.titleSmall,
                        )
                    }
                    Text(
                        "Tap the card — it becomes the header, and the name " +
                            "travels rather than cross-fading.",
                        style = Theme.typography.bodySmall,
                        colour = Theme.colours.contentMuted,
                    )
                }
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

internal val PaneScaffoldDemo = ComponentDemo(
    slug = "pane-scaffold",
    knobs = listOf(paneShape, paneFocus, paneResizable),
) {
    if (this@ComponentDemo[paneShape] == "Main and supporting") {
        SupportingPaneDemoBody()
        return@ComponentDemo
    }
    // Keyed on the knob, so the knob *sets* the pane and the list below can
    // still navigate from it — the same bargain the modal sheet demo strikes
    // with its Open knob.
    var focus by remember(this[paneFocus]) { mutableStateOf(this[paneFocus]) }
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

internal val ScaffoldDemo = ComponentDemo(slug = "scaffold", knobs = listOf(scaffoldFab)) {
    val fabPosition = this[scaffoldFab]
    Box(
        Modifier
            .fillMaxWidth()
            .height(280.dp)
            .border(Theme.sizing.borderWidth, Theme.colours.outline, Theme.shapes.medium)
            .clip(Theme.shapes.medium),
    ) {
        Scaffold(
            topBar = { TopBar { +"Favourites" } },
            fabPosition = fabPosition,
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
 * The supporting-pane half of the pane-scaffold demo.
 *
 * Its own function rather than a branch inline: the list-detail body holds three
 * pieces of state that mean nothing here, and a `when` around both would keep
 * them alive across a knob change.
 */
@Composable
private fun SupportingPaneDemoBody() {
    var supportingVisible by remember { mutableStateOf(true) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(280.dp)
            .border(Theme.sizing.borderWidth, Theme.colours.outline, Theme.shapes.medium)
            .clip(Theme.shapes.medium),
    ) {
        // The same trick the list-detail body uses: a size-class provider scoped
        // to this box, so one pane or two follows the *card's* width and the
        // behaviour is visible without a tablet.
        WindowSizeClassProvider(Modifier.fillMaxSize()) {
            SupportingPaneScaffold(
                supportingVisible = supportingVisible,
                onDismissSupporting = { supportingVisible = false },
                main = {
                    Column(Modifier.padding(Theme.spacing.md)) {
                        Text("Toodyay Rd run", style = Theme.typography.titleMedium)
                        Text(
                            "42.0 km · 38:04 · peak boost 18.6 psi",
                            style = Theme.typography.bodySmall,
                            colour = Theme.colours.contentMuted,
                        )
                    }
                },
                supporting = {
                    Column(Modifier.padding(Theme.spacing.md)) {
                        Text("Conditions", style = Theme.typography.labelMedium)
                        Text(
                            "24 °C, dry. Two of the four markers fell inside a " +
                                "rain radius on the previous run.",
                            style = Theme.typography.bodySmall,
                            colour = Theme.colours.contentMuted,
                        )
                        if (!supportingVisible) return@Column
                        Button(
                            onClick = { supportingVisible = false },
                            variant = ButtonVariant.Ghost,
                            size = ButtonSize.Small,
                        ) { +"Hide" }
                    }
                },
            )
        }
    }
    if (!supportingVisible) {
        Button(
            onClick = { supportingVisible = true },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Small,
        ) { +"Show supporting pane" }
    }
}

internal val adaptiveDemos = listOf(
    WindowSizeClassDemo,
    ScaffoldDemo,
    PaneScaffoldDemo,
    AspectRatioBoxDemo,
    GlassSurfaceDemo,
    PageTransitionDemo,
)
