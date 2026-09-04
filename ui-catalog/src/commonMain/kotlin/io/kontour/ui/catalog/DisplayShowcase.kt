package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.User
import com.composables.icons.tabler.outline.X
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.Accordion
import io.kontour.ui.components.display.AnimatedCounter
import io.kontour.ui.motion.marquee
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
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.display.ErrorState
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.LinearProgress
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.CircularProgress
import io.kontour.ui.components.display.Skeleton
import io.kontour.ui.components.display.SkeletonListItem
import io.kontour.ui.components.display.SkeletonText
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.display.StatTrend
import io.kontour.ui.components.display.StepProgress
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.display.TagTone
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch

/** Every display component. Source for the display goldens. */
@Composable
fun DisplayShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, colour = Theme.colours.background) {
        Panels {
            Panel(width = 420.dp, spacing = Theme.spacing.md) {
                Section("Cards") {
                    Card {
                        Text("Perth Station", style = Theme.typography.titleMedium)
                        Text(
                            "Platform 3 — Armadale line",
                            style = Theme.typography.bodySmall,
                            colour = Theme.colours.contentMuted,
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    ) {
                        Tag(tone = TagTone.Success) { +"Live" }
                        Tag(tone = TagTone.Warning) { +"Delayed" }
                        Tag(tone = TagTone.Danger) { +"Cancelled" }
                        Tag(tone = TagTone.Accent) { +"Beta" }
                        Tag() { +"Neutral" }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                    ) {
                        // Colours a GTFS feed might hand us — the label colour
                        // is derived, not designed.
                        Tag(colour = Color(0xFF1B5E20)) { +"960" }
                        Tag(colour = Color(0xFFFFD54F)) { +"SPT" }
                        Tag(colour = Color(0xFFB3261E)) { +"RED" }
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                    ) {
                        CircularProgress(progress = 0.35f)
                        CircularProgress(progress = 0.8f, size = 28.dp)
                        Spinner()
                        Spinner(size = 32.dp)
                    }
                }
            }

            Panel(width = 420.dp, spacing = Theme.spacing.md) {
                Section("Banners") {
                    val delays = seed(true)
                    if (delays.value) {
                        Banner(
                            tone = BannerTone.Warning,
                            onDismissRequest = { delays.value = false },
                            dismissIcon = Tabler.Outline.X,
                        ) {
                            +"Services are running up to 12 minutes late."
                            title { +"Delays on the Armadale line" }
                            leading { +Tabler.Outline.AlertTriangle }
                        }
                    }
                    Banner(tone = BannerTone.Danger) {
                        +"Couldn't reach the server."
                        leading { +Tabler.Outline.AlertTriangle }
                        action {
                            Button(
                                onClick = tap("Retry"),
                                variant = ButtonVariant.Secondary,
                                size = ButtonSize.XSmall,
                            ) {
                                +"Retry"
                            }
                        }
                    }
                    Banner(tone = BannerTone.Success) {
                        +"Trip saved to favourites."
                    }
                    Banner(tone = BannerTone.Accent) {
                        +"Live tracking is on for this trip."
                    }
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
                        TimelineItem(nodeColour = Color(0xFF1B5E20)) {
                            Text("Perth Station", style = Theme.typography.titleSmall)
                            Text(
                                "08:12 — Platform 3",
                                style = Theme.typography.bodySmall,
                                colour = Theme.colours.contentMuted,
                            )
                        }
                        TimelineItem(
                            connector = ConnectorStyle.Dashed,
                            filled = false,
                            nodeColour = Theme.colours.outlineStrong,
                        ) {
                            Text(
                                "Walk 4 min",
                                style = Theme.typography.bodySmall,
                                colour = Theme.colours.contentMuted,
                            )
                        }
                        TimelineItem(
                            connector = ConnectorStyle.None,
                            nodeColour = Color(0xFF1B5E20),
                        ) {
                            Text("Elizabeth Quay", style = Theme.typography.titleSmall)
                            Text(
                                "08:31",
                                style = Theme.typography.bodySmall,
                                colour = Theme.colours.contentMuted,
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

            Panel(width = 380.dp, spacing = Theme.spacing.md) {
                Section("States") {
                    Card(variant = CardVariant.Outlined) {
                        EmptyState() {
                            +"No favourites yet"
                            supporting { +"Star a stop or route and it will appear here." }
                            leading { +Tabler.Outline.Star }
                            action {
                                Button(
                                    onClick = tap("Browse routes"),
                                    variant = ButtonVariant.Secondary,
                                ) {
                                    +"Browse routes"
                                }
                            }
                        }
                    }
                    Card(variant = CardVariant.Outlined) {
                        ErrorState(onRetry = tap("Retry")) {
                            +"Couldn't load departures"
                            supporting { +"Check your connection and try again." }
                            leading { +Tabler.Outline.AlertTriangle }
                        }
                    }
                }

                Section("Accordion") {
                    Card(variant = CardVariant.Outlined) {
                        val open = seed(true)
                        Accordion(
                            expanded = open.value,
                            onExpandedChange = { open.value = it },
                            header = {
                                +"Accessibility"
                                supporting { +"Contrast, motion, text size" }
                            },
                            chevron = Tabler.Outline.ChevronDown,
                        ) {
                            Text(
                                "Expanded content sits here.",
                                style = Theme.typography.bodySmall,
                                colour = Theme.colours.contentMuted,
                            )
                        }
                        // Seeded closed, so the pair still shows both states
                        // in a golden, and both open.
                        val notifications = seed(false)
                        Accordion(
                            expanded = notifications.value,
                            onExpandedChange = { notifications.value = it },
                            header = { +"Notifications" },
                            chevron = Tabler.Outline.ChevronDown,
                        ) {
                            Text(
                                "Delay alerts, service changes and trip reminders.",
                                style = Theme.typography.bodySmall,
                                colour = Theme.colours.contentMuted,
                            )
                        }
                    }
                }

                Section("Counting and scrolling") {
                    Card(variant = CardVariant.Outlined) {
                        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                            // Tap it and the digits roll. Seeded at a two-digit
                            // value so the first tap crosses a column boundary,
                            // which is the case worth watching.
                            val minutes = seed(14)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AnimatedCounter(
                                    value = minutes.value,
                                    format = { "$it min" },
                                    style = Theme.typography.headlineSmall,
                                )
                                Button(
                                    onClick = { minutes.value = (minutes.value + 12) % 60 },
                                    variant = ButtonVariant.Secondary,
                                    size = ButtonSize.Small,
                                ) { +"Advance" }
                            }

                            // Deliberately too narrow for the label, which is
                            // the only state in which the modifier does
                            // anything. It is still under `reduceMotion` in the
                            // goldens, where it truncates instead.
                            Box(Modifier.width(180.dp)) {
                                Text(
                                    text = "Elizabeth Quay Bus Station, Stand E",
                                    maxLines = 1,
                                    style = Theme.typography.bodyMedium,
                                    modifier = Modifier.marquee(),
                                )
                            }
                        }
                    }
                }

                Section("Stat") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xl),
                    ) {
                        Stat {
                            value("4 min")
                            +"Next departure"
                            supporting("Platform 2")
                        }
                        Stat {
                            value("12")
                            +"Stops away"
                            trend(StatTrend.Negative, "3 more than usual")
                        }
                        // Right-aligned, which is what a figure in a column of
                        // figures wants and the one arrangement a left-aligned
                        // default gets wrong.
                        Stat(alignment = Alignment.End) {
                            value("$3.20")
                            +"Fare"
                            trend(StatTrend.Positive, "concession")
                        }
                    }
                }

                // Stacked rather than side by side: this page lays out in
                // three columns and one of these is already 320dp wide, so a
                // row of two crushes the second into a column of single
                // characters.
                Section("Carousel") {
                    val carousel = rememberCarouselState { 4 }
                    val scope = rememberCoroutineScope()
                    Column(
                        Modifier.widthIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Carousel(
                            state = carousel,
                            contentDescription = "Stop photos",
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                        ) { page ->
                            Card(variant = CardVariant.Filled, modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("Photo ${page + 1}", style = Theme.typography.titleMedium)
                                }
                            }
                        }
                        // Given a handler, so the dots are a pointer route
                        // rather than decoration — which is the whole argument
                        // on the component's page.
                        PageIndicator(
                            state = carousel,
                            onPageSelect = { page -> scope.launch { carousel.scrollToPage(page) } },
                        )
                    }
                }

                Section("KeyValueList") {
                    Panel(width = 320.dp, spacing = Theme.spacing.lg) {
                        KeyValueList {
                            item("Operator", "Transperth")
                            item("Platform", "2")
                            item("Fare", "$3.20")
                            item("Accessible", announcement = "yes") {
                                +Tabler.Outline.Check
                            }
                        }
                        KeyValueList(dividers = true) {
                            item("Route", "950")
                            item("Towards", "Perth Busport via Broadway")
                        }
                    }
                }
            }
        }
    }
}
