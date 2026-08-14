package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.User
import com.composables.icons.tabler.outline.X
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.Accordion
import io.kontour.ui.components.display.Avatar
import io.kontour.ui.components.display.AvatarGroup
import io.kontour.ui.components.display.AvatarSize
import io.kontour.ui.components.display.Badge
import io.kontour.ui.components.display.BadgedBox
import io.kontour.ui.components.display.Banner
import io.kontour.ui.components.display.BannerTone
import io.kontour.ui.components.display.Callout
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CardVariant
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.display.ErrorState
import io.kontour.ui.components.display.LinearProgress
import io.kontour.ui.components.display.ProgressRing
import io.kontour.ui.components.display.Skeleton
import io.kontour.ui.components.display.SkeletonListItem
import io.kontour.ui.components.display.SkeletonText
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.components.display.StepProgress
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.display.TagTone
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/** Every display component. Source for the display goldens. */
@Composable
fun DisplayShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Theme.colors.background) {
        Row(
            modifier = Modifier.padding(Theme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            Column(
                Modifier.width(420.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("Cards") {
                    Card {
                        Text("Perth Station", style = Theme.typography.titleMedium)
                        Text(
                            "Platform 3 — Armadale line",
                            style = Theme.typography.bodySmall,
                            color = Theme.colors.contentMuted,
                        )
                    }
                    Card(variant = CardVariant.Outlined) {
                        Text("Outlined", style = Theme.typography.titleSmall)
                    }
                    Card(variant = CardVariant.Filled) {
                        Text("Filled", style = Theme.typography.titleSmall)
                    }
                }

                Section("Tags and badges") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tag("Live", tone = TagTone.Success)
                        Tag("Delayed", tone = TagTone.Warning)
                        Tag("Cancelled", tone = TagTone.Danger)
                        Tag("Beta", tone = TagTone.Accent)
                        Tag("Neutral")
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Colours a GTFS feed might hand us — the label colour
                        // is derived, not designed.
                        Tag("960", color = Color(0xFF1B5E20))
                        Tag("SPT", color = Color(0xFFFFD54F))
                        Tag("RED", color = Color(0xFFB3261E))
                        BadgedBox(badge = { Badge(count = 3) }) {
                            Icon(Tabler.Outline.Bus, contentDescription = "Routes")
                        }
                        BadgedBox(badge = { Badge(count = 42) }) {
                            Icon(Tabler.Outline.Bus, contentDescription = "Routes")
                        }
                        BadgedBox(badge = { Badge() }) {
                            Icon(Tabler.Outline.Bus, contentDescription = "Routes")
                        }
                    }
                }

                Section("Avatars") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(name = "Aaron", size = AvatarSize.Small)
                        Avatar(name = "Sunny", size = AvatarSize.Medium)
                        Avatar(name = "Jamie Lee", size = AvatarSize.Large)
                        Avatar(fallbackIcon = Tabler.Outline.User, size = AvatarSize.Medium)
                        AvatarGroup(
                            names = listOf("Aaron", "Sunny", "Jamie", "Kit", "Robin", "Sam"),
                            size = AvatarSize.Medium,
                        )
                    }
                }

                Section("Progress") {
                    LinearProgress(progress = 0.6f, contentDescription = "Uploading")
                    LinearProgress(progress = null, contentDescription = "Loading")
                    StepProgress(current = 2, total = 4)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProgressRing(progress = 0.35f)
                        ProgressRing(progress = 0.8f, size = 28.dp)
                        Spinner()
                        Spinner(size = 32.dp)
                    }
                }
            }

            Column(
                Modifier.width(420.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("Banners") {
                    Banner(
                        tone = BannerTone.Warning,
                        title = "Delays on the Armadale line",
                        message = "Services are running up to 12 minutes late.",
                        icon = Tabler.Outline.AlertTriangle,
                        onDismiss = {},
                        dismissIcon = Tabler.Outline.X,
                    )
                    Banner(
                        tone = BannerTone.Danger,
                        message = "Couldn't reach the server.",
                        icon = Tabler.Outline.AlertTriangle,
                        action = {
                            Button(
                                "Retry",
                                onClick = {},
                                variant = ButtonVariant.Secondary,
                                size = ButtonSize.XSmall,
                            )
                        },
                    )
                    Banner(tone = BannerTone.Success, message = "Trip saved to favourites.")
                    Callout {
                        Text(
                            "Melbourne, Sydney and Canberra do not currently support " +
                                "journey planning.",
                            style = Theme.typography.bodySmall,
                        )
                    }
                }

                Section("Timeline") {
                    Timeline {
                        TimelineItem(nodeColor = Color(0xFF1B5E20)) {
                            Text("Perth Station", style = Theme.typography.titleSmall)
                            Text(
                                "08:12 — Platform 3",
                                style = Theme.typography.bodySmall,
                                color = Theme.colors.contentMuted,
                            )
                        }
                        TimelineItem(
                            connector = ConnectorStyle.Dashed,
                            filled = false,
                            nodeColor = Theme.colors.outlineStrong,
                        ) {
                            Text(
                                "Walk 4 min",
                                style = Theme.typography.bodySmall,
                                color = Theme.colors.contentMuted,
                            )
                        }
                        TimelineItem(
                            connector = ConnectorStyle.None,
                            nodeColor = Color(0xFF1B5E20),
                        ) {
                            Text("Elizabeth Quay", style = Theme.typography.titleSmall)
                            Text(
                                "08:31",
                                style = Theme.typography.bodySmall,
                                color = Theme.colors.contentMuted,
                            )
                        }
                    }
                }

                Section("Skeletons") {
                    SkeletonListItem()
                    SkeletonText(lines = 3)
                    Skeleton(Modifier.fillMaxWidth().height(80.dp))
                }
            }

            Column(
                Modifier.width(380.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("States") {
                    Card(variant = CardVariant.Outlined) {
                        EmptyState(
                            icon = Tabler.Outline.Star,
                            title = "No favourites yet",
                            message = "Star a stop or route and it will appear here.",
                            action = {
                                Button(
                                    "Browse routes",
                                    onClick = {},
                                    variant = ButtonVariant.Secondary,
                                )
                            },
                        )
                    }
                    Card(variant = CardVariant.Outlined) {
                        ErrorState(
                            icon = Tabler.Outline.AlertTriangle,
                            title = "Couldn't load departures",
                            message = "Check your connection and try again.",
                            onRetry = {},
                        )
                    }
                }

                Section("Accordion") {
                    Card(variant = CardVariant.Outlined) {
                        Accordion(
                            title = "Accessibility",
                            supporting = "Contrast, motion, text size",
                            expanded = true,
                            onExpandedChange = {},
                            chevron = Tabler.Outline.ChevronDown,
                        ) {
                            Text(
                                "Expanded content sits here.",
                                style = Theme.typography.bodySmall,
                                color = Theme.colors.contentMuted,
                            )
                        }
                        Accordion(
                            title = "Notifications",
                            expanded = false,
                            onExpandedChange = {},
                            chevron = Tabler.Outline.ChevronDown,
                        ) {}
                    }
                }
            }
        }
    }
}
