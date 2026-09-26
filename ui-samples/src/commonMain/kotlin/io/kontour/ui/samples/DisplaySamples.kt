package io.kontour.ui.samples

import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import io.kontour.ui.components.display.BranchTimeline
import io.kontour.ui.components.display.BubblePosition
import io.kontour.ui.components.display.BubbleSide
import io.kontour.ui.components.display.ChatBubble
import io.kontour.ui.components.display.GaugeTickPlacement
import io.kontour.ui.components.display.GaugeIndicator
import io.kontour.ui.components.display.GaugeDefaults
import io.kontour.ui.components.display.Gauge
import io.kontour.ui.components.display.Meter
import io.kontour.ui.components.display.MeterContentPlacement
import io.kontour.ui.components.display.MeterDefaults
import io.kontour.ui.components.display.MeterOrientation
import io.kontour.ui.components.display.ScaleColours
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Train
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
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
import io.kontour.ui.components.display.CircularProgress
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.LinearProgress
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.Skeleton
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.display.StatTrend
import io.kontour.ui.components.display.StepProgress
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.display.TagTone
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.HorizontalTimeline
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.components.display.TimelineList
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.foundation.Redacted
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.PageTransition
import io.kontour.ui.motion.marquee
import io.kontour.ui.motion.sharedBounds
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch

@Composable
fun StatBasics() {
    Stat {
        value("4 min")
        +"Next departure"
        supporting("Platform 2")
        trend(StatTrend.Positive, "2 min earlier than usual")
    }
}

@Composable
fun GaugeBasics() {
    // An engine's speed: a needle over a gradient, labelled ticks inside the arc.
    Gauge(
        value = 8_500f,
        valueRange = 0f..10_000f,
        indicator = GaugeIndicator.Needle,
        majorTicks = 6,
        minorTicks = 1,
        tickLabel = { "${(it / 1000).roundToInt()}K" },
        colours = GaugeDefaults.colours(
            indicator = ScaleColours.gradient(listOf(Color(0xFFFF5C9E), Color(0xFF7C5CFF))),
        ),
        contentDescription = "Engine speed",
        stateDescription = { "${it.roundToInt()} revolutions a minute" },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("8.5k", style = Theme.typography.titleLarge)
            Text("RPM", style = Theme.typography.labelSmall)
        }
    }

    // A thermostat: a thumb on a thicker arc, the reading in the middle.
    Gauge(
        value = 40f,
        valueRange = 0f..100f,
        thickness = 20.dp,
        indicator = GaugeIndicator.Thumb,
        majorTicks = 11,
        tickPlacement = GaugeTickPlacement.Outside,
        contentDescription = "Humidity",
    ) {
        Text("40", style = Theme.typography.displaySmall)
    }
}

@Composable
fun GaugeColourBands() {
    // Zones on a tachometer, in revolutions — the gauge's own units, so the scale
    // is only said once. Green to 6,000, amber to 8,000, red to the end, and the
    // needle in the colour of the zone it points at.
    Gauge(
        value = 7_200f,
        valueRange = 0f..10_000f,
        indicator = GaugeIndicator.Needle,
        needleLength = 1f,
        needleMatchesFill = true,
        colours = GaugeDefaults.colours(
            indicator = ScaleColours.bands {
                band(from = 0f, colour = Theme.colours.success.solid)
                band(from = 6_000f, colour = Theme.colours.warning.solid)
                band(from = 8_000f, colour = Theme.colours.danger.solid)
            },
        ),
        contentDescription = "Engine speed",
    )

    // The same zones blended at their edges.
    Gauge(
        value = 7_200f,
        valueRange = 0f..10_000f,
        colours = GaugeDefaults.colours(
            indicator = ScaleColours.bands(smoothing = 0.6f) {
                band(from = 0f, colour = Theme.colours.success.solid)
                band(from = 6_000f, colour = Theme.colours.warning.solid)
                band(from = 8_000f, colour = Theme.colours.danger.solid)
            },
        ),
        contentDescription = "Engine speed",
    )

    // A comfort band that stops short: 20 to 24 degrees is green, and the scale
    // either side of it is the gauge's own colour.
    Gauge(
        value = 22f,
        valueRange = 10f..30f,
        colours = GaugeDefaults.colours(
            indicator = ScaleColours.bands {
                band(from = 20f, until = 24f, colour = Theme.colours.success.solid)
            },
        ),
        contentDescription = "Room temperature",
    )
}

@Composable
fun MeterBasics() {
    // A battery across the page: bands along the scale, a thumb at the reading,
    // and the label above it.
    Meter(
        value = 62f,
        valueRange = 0f..100f,
        modifier = Modifier.fillMaxWidth(),
        indicator = GaugeIndicator.Thumb,
        majorTicks = 5,
        tickLabel = { "${it.roundToInt()}%" },
        colours = MeterDefaults.colours(
            indicator = ScaleColours.bands {
                band(from = 0f, colour = Theme.colours.danger.solid)
                band(from = 20f, colour = Theme.colours.warning.solid)
                band(from = 40f, colour = Theme.colours.success.solid)
            },
        ),
        contentDescription = "Battery",
        stateDescription = { "${it.roundToInt()} percent charged" },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("Battery", modifier = Modifier.weight(1f))
            Text("62%")
        }
    }

    // A tank up the page, a needle pointing at the level and the reading beside it.
    Meter(
        value = 340f,
        valueRange = 0f..500f,
        orientation = MeterOrientation.Vertical,
        thickness = 16.dp,
        indicator = GaugeIndicator.Needle,
        majorTicks = 6,
        minorTicks = 1,
        tickLabel = { "${it.roundToInt()}" },
        contentDescription = "Water tank",
        stateDescription = { "${it.roundToInt()} litres" },
    ) {
        Text("340 L", style = Theme.typography.titleLarge)
    }
}

@Composable
fun MeterAtTheReading() {
    // The label rides along with the reading, on a capsule of its own.
    Meter(
        value = 0.35f,
        modifier = Modifier.fillMaxWidth(),
        contentPlacement = MeterContentPlacement.AtValue,
        contentBackground = true,
        contentDescription = "Download",
    ) {
        Text("35%", style = Theme.typography.labelMedium)
    }

    // A balance either side of zero: the fill runs from the middle out.
    Meter(
        value = -12f,
        valueRange = -50f..50f,
        origin = 0f,
        modifier = Modifier.fillMaxWidth(),
        indicator = GaugeIndicator.Needle,
        majorTicks = 3,
        tickLabel = { if (it == 0f) "0" else "${it.roundToInt()}" },
        contentPlacement = MeterContentPlacement.AtValue,
        contentDescription = "Balance",
    ) {
        Text("−12", style = Theme.typography.labelMedium)
    }
}

@Composable
fun ChatBubbleBasics() {
    val messages = listOf(
        "sam" to "Is the 950 running tonight?",
        "sam" to "The app says it's delayed",
        "me" to "Every 15 minutes until 11pm",
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        messages.forEachIndexed { index, (sender, text) ->
            ChatBubble(
                side = if (sender == "me") BubbleSide.Outgoing else BubbleSide.Incoming,
                // Consecutive messages from one sender are a run; the last has the tail.
                position = BubblePosition.of(messages, index) { it.first },
            ) {
                Text(text)
            }
        }
    }
}

@Composable
fun KeyValueListBasics() {
    KeyValueList {
        item("Operator", "Transperth")
        item("Platform", "2")
        item("Fare", "$3.20")
        // A slot draws nothing a screen reader can read, so it says what to
        // announce instead.
        item("Accessible", announcement = "yes") { +Tabler.Outline.Check }
    }
}

@Composable
fun CarouselWithIndicator(photos: List<String>) {
    val scope = rememberCoroutineScope()
    val carousel = rememberCarouselState { photos.size }

    Carousel(carousel, contentDescription = "Stop photos") { page ->
        Text(photos[page])
    }
    PageIndicator(carousel, onPageSelect = { scope.launch { carousel.scrollToPage(it) } })
}

@Composable
fun AccordionBasics(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    Accordion(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        header = { +"Accessibility" },
    ) {
        Text("Step-free access at all platforms.")
    }
}

@Composable
fun AnimatedCounterBasics(minutesAway: Int) {
    AnimatedCounter(value = minutesAway, format = { "$it min" })
}

@Composable
fun MarqueeBasics(stop: Stop) {
    Text(
        text = stop.name,
        maxLines = 1,
        modifier = Modifier.marquee(),
    )
}

@Composable
fun PageTransitionBasics() {
    var route by remember { mutableStateOf<Route>(Route.List) }

    PageTransition(target = route, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            is Route.List -> StopList(onOpen = { route = Route.Detail(it) })
            is Route.Detail -> StopDetail(page.stop)
        }
    }
}

// The pages are ordinary composables. `sharedBounds` reaches the transition
// through a composition local, so neither of these takes a scope, and either
// renders on its own — in a test, or in a pane with no transition around it —
// with the modifier quietly doing nothing.
@Composable
private fun StopList(onOpen: (Stop) -> Unit) {
    Column {
        for (stop in stops) {
            Card(
                modifier = Modifier.sharedBounds("stop-${stop.name}"),
                onClick = { onOpen(stop) },
            ) {
                Text(stop.name)
            }
        }
    }
}

@Composable
private fun StopDetail(stop: Stop) {
    Column {
        Card(modifier = Modifier.sharedBounds("stop-${stop.name}")) {
            Text(stop.name)
        }
        Text("${stop.routes} routes")
    }
}

sealed interface Route {
    data object List : Route
    data class Detail(val stop: Stop) : Route
}

@Composable
fun AvatarBasics() {
    // Initials from the name, so a missing photo is still a person rather than
    // a grey circle. `image` wins when there is one.
    Avatar(name = "Ada Lovelace", size = AvatarSize.Large)

    // The overflow count is part of the accessible name, not a decoration —
    // "+3" read out of context tells a screen-reader user nothing.
    AvatarGroup(names = listOf("Ada Lovelace", "Grace Hopper", "Alan Turing", "Ken Thompson"))
}

@Composable
fun BadgeBasics() {
    // A dot, for "something changed" with no number worth reading.
    BadgedBox(badge = { Badge() }) {
        IconButton(icon = Tabler.Outline.Bell, contentDescription = "Alerts", onClick = { nearby() })
    }

    // A count, which caps at `max` and announces itself.
    BadgedBox(badge = { Badge(count = 12, contentDescription = "12 unread alerts") }) {
        IconButton(icon = Tabler.Outline.Bell, contentDescription = "Alerts", onClick = { nearby() })
    }
}

@Composable
fun BannerBasics() {
    var showing by remember { mutableStateOf(true) }

    if (showing) {
        Banner(tone = BannerTone.Warning, onDismissRequest = { showing = false }) {
            title { +"Track work this weekend" }
            message { +"Buses replace trains between Perth and Bayswater until Monday." }
            action {
                Button(onClick = { plan() }, variant = ButtonVariant.Ghost, size = ButtonSize.Small) {
                    +"Plan around it"
                }
            }
        }
    }
}

@Composable
fun CalloutBasics() {
    // The markdown blockquote treatment, for an aside inside prose. Not a
    // status and not dismissible — if it can go away, it is a `Banner`.
    Callout {
        Text("Melbourne, Sydney and Canberra do not currently support journey planning.")
    }
}

@Composable
fun CardBasics() {
    Card(variant = CardVariant.Outlined, onClick = { openStop("Perth Underground") }) {
        Text("Perth Underground", style = Theme.typography.titleSmall)
        Text(
            "Platform 2 · Joondalup line",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

@Composable
fun EmptyStateBasics() {
    // The action is the part that matters: an empty screen that only says it is
    // empty leaves the reader where they already were.
    EmptyState(Modifier.fillMaxWidth()) {
        +"No favourites yet"
        supporting { +"Star a stop or route and it will appear here." }
        leading { +Tabler.Outline.Star }
        action {
            Button(onClick = { nearby() }, variant = ButtonVariant.Secondary) { +"Browse routes" }
        }
    }
}

@Composable
fun ProgressBasics() {
    // Determinate where the total is known.
    LinearProgress(progress = 0.4f, contentDescription = "Downloading timetables")

    // `null` is indeterminate — for work whose length nobody can predict, which
    // is honest rather than a bar that sits at 90% for a minute.
    LinearProgress(progress = null, contentDescription = "Finding routes")

    CircularProgress(progress = 0.4f, contentDescription = "Downloading timetables")

    // For a wizard, where the count is the story.
    StepProgress(current = 2, total = 4, contentDescription = "Step 2 of 4")
}

@Composable
fun SkeletonBasics() {
    // Shaped like the content it stands in for, so nothing moves when the real
    // thing arrives. A spinner in the same place would move everything.
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Skeleton(Modifier.width(180.dp).height(20.dp))
        Skeleton(Modifier.width(120.dp).height(16.dp))
    }
}

@Composable
fun TagBasics() {
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Tag(tone = TagTone.Success) { +"On time" }
        Tag(tone = TagTone.Warning) { +"Delayed" }
        // Not a `Chip`: a tag is a label the reader cannot press. A status that
        // filters the list behind it is a `FilterChip`.
        Tag(tone = TagTone.Neutral) { +"Platform 2" }
    }
}

@Composable
fun TimelineBasics() {
    Timeline {
        TimelineItem {
            Text("Perth Underground", style = Theme.typography.titleSmall)
            Text("08:14 · Platform 2", style = Theme.typography.bodySmall)
        }
        TimelineItem {
            Text("Elizabeth Quay", style = Theme.typography.titleSmall)
            Text("08:21 · Platform 1", style = Theme.typography.bodySmall)
        }
        // The last item draws no connector below it, because there is nothing
        // for it to connect to.
        TimelineItem(filled = false, connector = ConnectorStyle.None) {
            Text("Perth Busport", style = Theme.typography.titleSmall)
            Text("08:29 · Stand 24", style = Theme.typography.bodySmall)
        }
    }
}

@Composable
fun TimelineListBasics() {
    // One and a half stops along: the first leg is travelled, the walk is half
    // done, and the rows are list rows — each one opens its stop.
    TimelineList(progress = 1.5f) {
        item(onClick = { openStop("Perth Station") }) {
            +"Perth Station"
            supporting { +"Platform 3" }
            trailing { +"08:12" }
        }
        // The connector is the leg after the stop: this one is the walk.
        item("Walk 4 min", connector = ConnectorStyle.Dashed, filled = false)
        item("Elizabeth Quay", supporting = "Stand C", trailing = "08:21") {
            openStop("Elizabeth Quay")
        }
    }
}

@Composable
fun BranchTimelineBasics() {
    // Only which commit comes from which: the lanes are laid out from that. The
    // merge's second parent opens a lane of its own, which closes back into the
    // commit it forked from.
    BranchTimeline(
        items = commits,
        id = { it.sha },
        parents = { it.parents },
    ) { commit ->
        // Each commit is a row declared like a TimelineList stop.
        item(onClick = { openCommit(commit) }) {
            +commit.message
            supporting { +"${commit.author} · ${commit.sha}" }
        }
    }
}

@Composable
fun HorizontalTimelineBasics() {
    // The same items, laid across: each node at its item's start, the content
    // under it, and the connector running on to the next.
    HorizontalTimeline {
        TimelineItem {
            Text("Ordered", style = Theme.typography.titleSmall)
            Text("Mon 3", style = Theme.typography.bodySmall)
        }
        TimelineItem {
            Text("Packed", style = Theme.typography.titleSmall)
            Text("Tue 4", style = Theme.typography.bodySmall)
        }
        TimelineItem(connector = ConnectorStyle.Dashed) {
            Text("On its way", style = Theme.typography.titleSmall)
            Text("Wed 5", style = Theme.typography.bodySmall)
        }
        TimelineItem(filled = false, connector = ConnectorStyle.None) {
            Text("Delivered", style = Theme.typography.titleSmall)
            Text("Thu 6, expected", style = Theme.typography.bodySmall)
        }
    }
}

@Composable
fun PageIndicatorBasics() {
    val carousel = rememberCarouselState { 5 }
    val scope = rememberCoroutineScope()

    // Given `onPageSelect` the dots become the control as well as the readout.
    // The strip takes the tap and sends it to the nearest dot, so the indicator
    // stays the width of its own ink rather than 48dp per page.
    PageIndicator(
        state = carousel,
        onPageSelect = { page -> scope.launch { carousel.scrollToPage(page) } },
    )
}

@Composable
fun RedactionBasics() {
    Redacted(departures == null) {
        ListItem {
            +(departures?.first()?.name ?: "Perth Underground")
            supporting { +(departures?.first()?.detail ?: "08:14 · Platform 2") }
            leading { +Tabler.Outline.Train }
        }
    }
}
