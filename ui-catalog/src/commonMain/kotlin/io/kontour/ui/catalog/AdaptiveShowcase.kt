package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import io.kontour.ui.adaptive.AspectRatioBox
import io.kontour.ui.adaptive.ListDetailPaneScaffold
import io.kontour.ui.adaptive.PaneFocus
import io.kontour.ui.adaptive.SupportingPaneScaffold
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.GlassSurface
import io.kontour.ui.motion.PageTransition
import io.kontour.ui.motion.sharedBounds
import io.kontour.ui.motion.atmosphere
import io.kontour.ui.motion.edgeVignette
import io.kontour.ui.motion.shimmer
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme

/** Pane scaffolds at two window sizes, plus the decorative surfaces. */
@Composable
fun AdaptiveShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, colour = Theme.colours.background) {
        Column(
            modifier = Modifier.padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            DeviceStrip {
                Frame("List–detail, one pane", width = 380.dp, height = 460.dp) {
                    // The whole point of the one-pane arrangement is that
                    // picking a stop *replaces* the list and back brings it
                    // returns — which a hardcoded focus can show but not do.
                    val narrow = seed(PaneFocus.List)
                    val narrowStop = seed(1)
                    ListDetailPaneScaffold(
                        focus = narrow.value,
                        onBack = { narrow.value = PaneFocus.List },
                        list = {
                            StopList(selected = narrowStop.value) {
                                narrowStop.value = it
                                narrow.value = PaneFocus.Detail
                            }
                        },
                        detail = { StopDetail() },
                    )
                }

                Frame("List–detail, two panes", width = 900.dp, height = 460.dp) {
                    // Both panes are on screen here, so focus decides which one
                    // the back gesture and the screen reader treat as current
                    // rather than which one is drawn.
                    val wide = seed(PaneFocus.Detail)
                    val wideStop = seed(1)
                    ListDetailPaneScaffold(
                        focus = wide.value,
                        onBack = { wide.value = PaneFocus.List },
                        list = {
                            StopList(selected = wideStop.value) {
                                wideStop.value = it
                                wide.value = PaneFocus.Detail
                            }
                        },
                        detail = { StopDetail() },
                        resizable = true,
                    )
                }

                Frame("Supporting pane", width = 900.dp, height = 460.dp) {
                    SupportingPaneScaffold(
                        main = { StopDetail() },
                        supporting = { FilterPane() },
                    )
                }
            }

            // Decorative surfaces, not device exhibits — so `FlowRow` rather
            // than `DeviceStrip`. A horizontal scroller measures its children at
            // infinite width, which quietly disables `Panel`'s `widthIn(max =)`:
            // these two stayed 600dp apiece on a 360dp phone and the page became
            // a 1,224dp strip nobody would think to scroll.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
            ) {
                Panel(width = 600.dp, spacing = Theme.spacing.xs) {
                    Text(
                        text = "PAGE TRANSITION",
                        style = Theme.typography.monoLabel,
                        colour = Theme.colours.accent.solid,
                    )
                    // Tap the card and it becomes the header of the page it
                    // opens; tap Back and it returns. The whole of what
                    // `PageTransition` adds is that the card and the header are
                    // one element rather than two — which a still cannot show,
                    // so this one is here to be pressed.
                    val open = seed(false)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(Theme.shapes.large)
                            .background(Theme.colours.surfaceSunken),
                    ) {
                        PageTransition(target = open.value, modifier = Modifier.fillMaxSize()) { detail ->
                            if (detail) {
                                Column(Modifier.fillMaxSize()) {
                                    Card(
                                        modifier = Modifier
                                            .sharedBounds(HeroKey, clip = Theme.shapes.large)
                                            .fillMaxWidth()
                                            .height(120.dp),
                                    ) {
                                        Text(
                                            "Perth Underground",
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
                                            onClick = { open.value = false },
                                            variant = ButtonVariant.Secondary,
                                            size = ButtonSize.Small,
                                        ) { +"Back" }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(Theme.spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .sharedBounds(HeroKey, clip = Theme.shapes.large)
                                            .fillMaxWidth(),
                                        onClick = { open.value = true },
                                    ) {
                                        Text(
                                            "Perth Underground",
                                            style = Theme.typography.titleSmall,
                                        )
                                    }
                                    Text(
                                        "Tap the card — it becomes the header.",
                                        style = Theme.typography.bodySmall,
                                        colour = Theme.colours.contentMuted,
                                    )
                                }
                            }
                        }
                    }
                }

                Panel(width = 600.dp, spacing = Theme.spacing.xs) {
                    Text(
                        text = "ATMOSPHERE AND GLASS",
                        style = Theme.typography.monoLabel,
                        colour = Theme.colours.accent.solid,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(Theme.shapes.large)
                            .atmosphere(),
                    ) {
                        Column(
                            modifier = Modifier.padding(Theme.spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        ) {
                            Text("Get where you're going", style = Theme.typography.displaySmall)
                            Text(
                                "Live departures for every stop in Perth.",
                                style = Theme.typography.bodyMedium,
                                colour = Theme.colours.contentMuted,
                            )
                        }

                        GlassSurface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(Theme.spacing.md)
                                .height(56.dp)
                                .width(320.dp),
                            shape = Theme.shapes.pill,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "translucent, not blurred — see the docs",
                                    style = Theme.typography.labelSmall,
                                    colour = Theme.colours.contentMuted,
                                )
                            }
                        }
                    }
                }

                Panel(width = 600.dp, spacing = Theme.spacing.xs) {
                    Text(
                        text = "EDGE VIGNETTE AND SHIMMER",
                        style = Theme.typography.monoLabel,
                        colour = Theme.colours.accent.solid,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(Theme.shapes.large)
                            .background(Theme.colours.surfaceSunken)
                            .edgeVignette(topFade = 1f, bottomFade = 1f, height = 48.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(Theme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        ) {
                            repeat(6) { Text("A line of content that runs under the edge") }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(Theme.shapes.small)
                                .shimmer()
                        )
                        // A short line under a full-width one — as a fraction,
                        // because 360dp was wider than the column it sits in the
                        // moment that column stopped being measured at infinity.
                        Box(
                            Modifier
                                .fillMaxWidth(0.62f)
                                .height(16.dp)
                                .clip(Theme.shapes.small)
                                .shimmer()
                        )
                    }

                    AspectRatioBox(
                        ratio = 16f / 9f,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                            .clip(Theme.shapes.medium)
                            .shimmer(),
                    ) {
                        Text(
                            "16:9 reserved",
                            style = Theme.typography.labelSmall,
                            colour = Theme.colours.contentSubtle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StopList(selected: Int, onSelectedChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colours.background)
            .padding(Theme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val stops = listOf("Perth Underground", "Elizabeth Quay", "Perth Busport", "McIver")
        stops.forEachIndexed { index, name ->
            ListItem(
                position = ListItemPosition.of(index, stops.size),
                selected = index == selected,
                onClick = { onSelectedChange(index) },
            ) {
                +name
                supporting { +"Platform ${index + 1}" }
                leading {
                    Icon(
                        Tabler.Outline.Bus,
                        contentDescription = null,
                        size = Theme.sizing.iconLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun StopDetail() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colours.background)
            .padding(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        Text("Elizabeth Quay", style = Theme.typography.titleLarge)
        Text(
            "Platform 1 · Mandurah line",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
        Card {
            Text("Next departure", style = Theme.typography.labelMedium)
            Text("4 min", style = Theme.typography.displaySmall)
        }
    }
}

@Composable
private fun FilterPane() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colours.surfaceSunken)
            .padding(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Text("Filters", style = Theme.typography.titleSmall)
        Text(
            "Supporting panes go on the trailing side — they are about the content, not about where you can go.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

@Composable
private fun Frame(title: String, width: Dp, height: Dp, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Text(
            text = title.uppercase(),
            style = Theme.typography.monoLabel,
            colour = Theme.colours.accent.solid,
        )
        Box(
            Modifier
                .width(width)
                .height(height)
                .border(
                    width = Theme.sizing.borderWidth,
                    color = Theme.colours.outline,
                    shape = Theme.shapes.medium,
                )
                .clip(Theme.shapes.medium)
        ) {
            WindowSizeClassProvider(Modifier.fillMaxSize()) {
                OverlayHost(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}


/** The card and the header it becomes are one element, so they share a key. */
private const val HeroKey = "stop-hero"
