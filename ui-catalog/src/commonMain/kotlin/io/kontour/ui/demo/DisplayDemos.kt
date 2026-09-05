package io.kontour.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import io.kontour.ui.components.display.Kbd
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.LinearProgress
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.PageIndicatorStyle
import io.kontour.ui.components.display.CircularProgress
import io.kontour.ui.components.display.Skeleton
import io.kontour.ui.components.display.SkeletonListItem
import io.kontour.ui.components.display.SkeletonText
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.display.StatTrend
import io.kontour.ui.components.display.StepProgress
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.display.TagTone
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.marquee
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch

private val cardVariant = Knob.Choice("Variant", CardVariant.entries.toList())
private val cardClickable = Knob.Flag("Clickable")

internal val CardDemo = ComponentDemo(
    slug = "card",
    knobs = listOf(cardVariant, cardClickable),
) {
    val clickable = this[cardClickable]
    Card(
        variant = this[cardVariant],
        onClick = if (clickable) ({ echo("Opened Perth Station") }) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Perth Station", style = Theme.typography.titleMedium)
        Text(
            "Platform 3 — Armadale line",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

private val tagTone = Knob.Choice("Tone", TagTone.entries.toList())

internal val TagDemo = ComponentDemo(slug = "tag", knobs = listOf(tagTone)) {
    val tone = this[tagTone]
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            Tag(tone = tone) { +"Live" }
            Tag(tone = tone) {
                +Tabler.Outline.Bus
                +"950"
            }
        }
        Text(
            "A route colour from a feed, with the label derived rather than designed:",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            Tag(colour = Color(0xFF1B5E20)) { +"960" }
            Tag(colour = Color(0xFFFFD54F)) { +"SPT" }
            Tag(colour = Color(0xFFB3261E)) { +"RED" }
        }
    }
}

internal val BadgeDemo = ComponentDemo(slug = "badge") {
    var count by remember { mutableStateOf(3) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgedBox(badge = { Badge(count = count) }) {
            Icon(Tabler.Outline.Bus, contentDescription = "Routes")
        }
        BadgedBox(badge = { Badge() }) {
            Icon(Tabler.Outline.Bus, contentDescription = "Routes")
        }
        Button(
            onClick = { count = (count + 7) % 130 },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Small,
        ) { +"More" }
    }
}

private val avatarSize = Knob.Choice(
    "Size",
    listOf(AvatarSize.Small, AvatarSize.Medium, AvatarSize.Large),
    AvatarSize.Medium,
    name = { if (it == AvatarSize.Small) "Small" else if (it == AvatarSize.Medium) "Medium" else "Large" },
)

internal val AvatarDemo = ComponentDemo(slug = "avatar", knobs = listOf(avatarSize)) {
    val size = this[avatarSize]
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = "Aaron", size = size)
        Avatar(name = "Jamie Lee", size = size)
        Avatar(fallbackIcon = Tabler.Outline.User, size = size)
        AvatarGroup(names = listOf("Aaron", "Sunny", "Jamie", "Kit", "Robin", "Sam"), size = size)
    }
}

private val progressIndeterminate = Knob.Flag("Indeterminate")
private val progressWorking = Knob.Flag("Step working")

internal val ProgressDemo = ComponentDemo(
    slug = "progress",
    knobs = listOf(progressIndeterminate, progressWorking),
) {
    val indeterminate = this[progressIndeterminate]
    val working = this[progressWorking]
    var step by remember { mutableStateOf(2) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        LinearProgress(
            progress = if (indeterminate) null else 0.6f,
            contentDescription = if (indeterminate) "Loading" else "Uploading",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A ring has no indeterminate sweep of its own: at `progress = null`
            // it hands off to `Spinner`, which is the library's one loader. So
            // the knob reaches all three, and the third of them is a different
            // component drawn in the ring's place.
            CircularProgress(progress = if (indeterminate) null else 0.35f)
            // Two unknowns, and the knobs show they are different. No current
            // step walks one lit segment along the row — "somewhere in this
            // sequence, not yet known". `working` keeps the step and animates
            // inside it: the position is known, the progress within it is not.
            StepProgress(
                current = if (indeterminate) null else step,
                total = 4,
                working = working,
            )
            Button(
                onClick = { step = step % 4 + 1 },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
            ) { +"Next step" }
        }
    }
}

private val bannerTone = Knob.Choice("Tone", BannerTone.entries.toList(), BannerTone.Warning)

internal val BannerDemo = ComponentDemo(slug = "banner", knobs = listOf(bannerTone)) {
    var shown by remember { mutableStateOf(true) }
    val tone = this[bannerTone]
    if (shown) {
        Banner(
            tone = tone,
            onDismissRequest = { shown = false },
            dismissIcon = Tabler.Outline.X,
            modifier = Modifier.fillMaxWidth(),
        ) {
            +"Services are running up to 12 minutes late."
            title { +"Delays on the Armadale line" }
            leading { +Tabler.Outline.AlertTriangle }
        }
    } else {
        Button(
            onClick = { shown = true },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Small,
        ) { +"Bring it back" }
    }
}

internal val CalloutDemo = ComponentDemo(slug = "callout") {
    Callout(Modifier.fillMaxWidth()) {
        Text(
            "Melbourne, Sydney and Canberra do not currently support journey planning.",
            style = Theme.typography.bodySmall,
        )
    }
}

internal val TimelineDemo = ComponentDemo(slug = "timeline") {
    Timeline(Modifier.fillMaxWidth()) {
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
        TimelineItem(connector = ConnectorStyle.None, nodeColour = Color(0xFF1B5E20)) {
            Text("Elizabeth Quay", style = Theme.typography.titleSmall)
            Text("08:31", style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
        }
    }
}

internal val SkeletonDemo = ComponentDemo(slug = "skeleton") {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        SkeletonListItem()
        SkeletonText(lines = 3)
        Skeleton(Modifier.fillMaxWidth().height(80.dp))
    }
}

internal val EmptyStateDemo = ComponentDemo(slug = "empty-state") {
    EmptyState(Modifier.fillMaxWidth()) {
        +"No favourites yet"
        supporting { +"Star a stop or route and it will appear here." }
        leading { +Tabler.Outline.Star }
        action {
            Button(
                onClick = { echo("Browse routes") },
                variant = ButtonVariant.Secondary,
            ) { +"Browse routes" }
        }
    }
}

internal val AccordionDemo = ComponentDemo(slug = "accordion") {
    var open by remember { mutableStateOf(false) }
    Accordion(
        expanded = open,
        onExpandedChange = { open = it },
        chevron = Tabler.Outline.ChevronDown,
        modifier = Modifier.fillMaxWidth(),
        header = {
            +"Accessibility"
            supporting { +"Contrast, motion, text size" }
        },
    ) {
        Text(
            "Contrast follows the system tier, motion follows the reduce-motion " +
                "preference, and type scales to 200% without clipping.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val AnimatedCounterDemo = ComponentDemo(slug = "animated-counter") {
    var minutes by remember { mutableStateOf(14) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedCounter(
            value = minutes,
            format = { "$it min" },
            style = Theme.typography.headlineSmall,
        )
        Button(
            onClick = { minutes = (minutes + 12) % 60 },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Small,
        ) { +"Advance" }
        Button(
            onClick = { minutes = if (minutes == 0) 1 else minutes - 1 },
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Small,
        ) { +"Tick down" }
    }
}

internal val MarqueeDemo = ComponentDemo(slug = "modifier-marquee") {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Box(Modifier.width(180.dp)) {
            Text(
                text = "Elizabeth Quay Bus Station, Stand E",
                maxLines = 1,
                style = Theme.typography.bodyMedium,
                modifier = Modifier.marquee(),
            )
        }
        Box(Modifier.width(180.dp)) {
            // The same modifier on text that fits, which does nothing at all —
            // that is the property that makes it safe to apply unconditionally.
            Text(
                text = "Perth",
                maxLines = 1,
                style = Theme.typography.bodyMedium,
                modifier = Modifier.marquee(),
            )
        }
    }
}

private val statTrend = Knob.Choice("Trend", StatTrend.entries.toList())

internal val StatDemo = ComponentDemo(slug = "stat", knobs = listOf(statTrend)) {
    val trend = this[statTrend]
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xl)) {
        Stat {
            value("4 min")
            +"Next departure"
            supporting("Platform 2")
        }
        Stat {
            value("12")
            +"Stops away"
            trend(trend, "3 more than usual")
        }
    }
}

private val indicatorStyle = Knob.Choice("Style", PageIndicatorStyle.entries.toList())

internal val CarouselDemo = ComponentDemo(slug = "carousel", knobs = listOf(indicatorStyle)) {
    val carousel = rememberCarouselState { 4 }
    val scope = rememberCoroutineScope()
    val style = this[indicatorStyle]
    Column(
        modifier = Modifier.fillMaxWidth(),
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
        PageIndicator(
            state = carousel,
            style = style,
            onPageSelect = { page -> scope.launch { carousel.scrollToPage(page) } },
        )
    }
}

internal val PageIndicatorDemo = ComponentDemo(
    slug = "page-indicator",
    knobs = listOf(indicatorStyle),
) {
    val carousel = rememberCarouselState { 5 }
    val scope = rememberCoroutineScope()
    // The indicator is also the control here, which is the case its default
    // style is chosen for: every dot keeps its own footprint and its own target.
    PageIndicator(
        state = carousel,
        style = this[indicatorStyle],
        onPageSelect = { page -> scope.launch { carousel.scrollToPage(page) } },
    )
}

private val kvDividers = Knob.Flag("Dividers")

internal val KeyValueListDemo = ComponentDemo(
    slug = "key-value-list",
    knobs = listOf(kvDividers),
) {
    KeyValueList(dividers = this[kvDividers], modifier = Modifier.fillMaxWidth()) {
        item("Operator", "Transperth")
        item("Platform", "2")
        item("Fare", "$3.20")
        item("Accessible", announcement = "yes") { +Tabler.Outline.Check }
    }
}

internal val KbdDemo = ComponentDemo(slug = "kbd") {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kbd { +"⌘" }
        Kbd { +"K" }
        Text(
            "opens the command palette",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val displayDemos = listOf(
    CardDemo,
    TagDemo,
    BadgeDemo,
    AvatarDemo,
    ProgressDemo,
    BannerDemo,
    CalloutDemo,
    TimelineDemo,
    SkeletonDemo,
    EmptyStateDemo,
    AccordionDemo,
    AnimatedCounterDemo,
    MarqueeDemo,
    StatDemo,
    CarouselDemo,
    PageIndicatorDemo,
    KeyValueListDemo,
    KbdDemo,
)
