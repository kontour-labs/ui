package io.kontour.ui.catalog

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.GlassSurface
import io.kontour.ui.motion.atmosphere
import io.kontour.ui.motion.edgeVignette
import io.kontour.ui.motion.shimmer
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme

/** Pane scaffolds at two window sizes, plus the decorative surfaces. */
@Composable
fun AdaptiveShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Theme.colors.background) {
        Column(
            modifier = Modifier.padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                Frame("List–detail, one pane", width = 380.dp, height = 460.dp) {
                    ListDetailPaneScaffold(
                        focus = PaneFocus.List,
                        onBack = {},
                        list = { StopList() },
                        detail = { StopDetail() },
                    )
                }

                Frame("List–detail, two panes", width = 900.dp, height = 460.dp) {
                    ListDetailPaneScaffold(
                        focus = PaneFocus.Detail,
                        onBack = {},
                        list = { StopList() },
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

            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                Column(
                    modifier = Modifier.width(600.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    Text(
                        text = "ATMOSPHERE AND GLASS",
                        style = Theme.typography.monoLabel,
                        color = Theme.colors.accent.solid,
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
                                color = Theme.colors.contentMuted,
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
                                    color = Theme.colors.contentMuted,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.width(600.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    Text(
                        text = "EDGE VIGNETTE AND SHIMMER",
                        style = Theme.typography.monoLabel,
                        color = Theme.colors.accent.solid,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(Theme.shapes.large)
                            .background(Theme.colors.surfaceSunken)
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
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(16.dp)
                                .clip(Theme.shapes.small)
                                .shimmer()
                        )
                    }

                    AspectRatioBox(
                        ratio = 16f / 9f,
                        modifier = Modifier
                            .width(280.dp)
                            .clip(Theme.shapes.medium)
                            .shimmer(),
                    ) {
                        Text(
                            "16:9 reserved",
                            style = Theme.typography.labelSmall,
                            color = Theme.colors.contentSubtle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StopList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .padding(Theme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val stops = listOf("Perth Underground", "Elizabeth Quay", "Perth Busport", "McIver")
        stops.forEachIndexed { index, name ->
            ListItem(
                position = ListItemPosition.of(index, stops.size),
                selected = index == 1,
                onClick = {},
            ) {
                +name
                supporting { +"Platform ${index + 1}" }
                leading {
                                    Icon(Tabler.Outline.Bus, contentDescription = null, size = Theme.sizing.iconLarge)
                                
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
            .background(Theme.colors.background)
            .padding(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        Text("Elizabeth Quay", style = Theme.typography.titleLarge)
        Text(
            "Platform 1 · Mandurah line",
            style = Theme.typography.bodySmall,
            color = Theme.colors.contentMuted,
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
            .background(Theme.colors.surfaceSunken)
            .padding(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Text("Filters", style = Theme.typography.titleSmall)
        Text(
            "Supporting panes go on the trailing side — they are about the content, not about where you can go.",
            style = Theme.typography.bodySmall,
            color = Theme.colors.contentMuted,
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
            color = Theme.colors.accent.solid,
        )
        Box(
            Modifier
                .width(width)
                .height(height)
                .border(
                    width = Theme.sizing.borderWidth,
                    color = Theme.colors.outline,
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
