package io.kontour.ui.samples

import io.kontour.ui.components.display.ErrorState
import io.kontour.ui.components.display.LoadingState
import io.kontour.ui.components.display.StateSwitcher
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import io.kontour.ui.components.display.BranchTimeline
import io.kontour.ui.foundation.GroupPosition
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
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bell
import io.kontour.icons.tabler.outline.Check
import io.kontour.icons.tabler.outline.Star
import io.kontour.icons.tabler.outline.Train
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
import io.kontour.ui.components.display.ThumbnailStrip
import io.kontour.ui.components.display.Banner
import io.kontour.ui.theme.Tone
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
import io.kontour.ui.components.display.CircularStepProgress
import io.kontour.ui.components.display.StepProgress
import io.kontour.ui.components.display.Tag
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import io.kontour.ui.motion.shimmer

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
        +"8.5k"
        label("RPM")
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
        +"40"
        label("% humidity")
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
    val formats = Theme.formats
    // A battery across the page: bands along the scale, a thumb at the reading,
    // and the label above it.
    Meter(
        value = 62f,
        valueRange = 0f..100f,
        modifier = Modifier.fillMaxWidth(),
        indicator = GaugeIndicator.Thumb,
        majorTicks = 5,
        tickLabel = { formats.percent(it / 100.0) },
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
        +"62%"
        label("Battery")
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
        +"340 L"
    }
}

@Composable
fun MeterAtTheReading() {
    // The label rides along with the reading, on a capsule of its own.
    Meter(
        value = 0.35f,
        modifier = Modifier.fillMaxWidth(),
        contentPlacement = MeterContentPlacement.AtValue,
        showContentBackground = true,
        contentDescription = "Download",
    ) {
        +"35%"
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
        +"−12"
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
                position = GroupPosition.of(messages, index) { it.first },
            ) {
                +text
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
    PageIndicator(carousel, onPageClick = { scope.launch { carousel.animateScrollToPage(it) } })
}

@Composable
fun ThumbnailStripBasics(photoCount: Int, photo: @Composable (page: Int, modifier: Modifier) -> Unit) {
    val pages = rememberCarouselState { photoCount }

    Column {
        Carousel(pages, contentDescription = "Trip photos") { page -> photo(page, Modifier.fillMaxWidth()) }
        // Under the photos: dragged, it scrubs through them; a thumbnail pressed goes to it.
        ThumbnailStrip(pages) { page -> photo(page, Modifier.fillMaxSize()) }
    }
}

/** A photo of the caller's own, with the size it was taken at. */
data class Photo(val url: String, val width: Int, val height: Int)

@Composable
fun ThumbnailStripShapes(photos: List<Photo>, photo: @Composable (Photo, Modifier) -> Unit) {
    val pages = rememberCarouselState { photos.size }

    Column {
        Carousel(pages, contentDescription = "Trip photos") { page -> photo(photos[page], Modifier.fillMaxWidth()) }
        // The current one widens to its photo's own shape.
        ThumbnailStrip(
            pages,
            thumbnailAspectRatio = { page -> photos[page].width.toFloat() / photos[page].height },
        ) { page -> photo(photos[page], Modifier.fillMaxSize()) }
    }
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
        Banner(tone = Tone.Warning, onDismissRequest = { showing = false }) {
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
        +"Melbourne, Sydney and Canberra do not currently support journey planning."
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
        message { +"Star a stop or route and it will appear here." }
        leading { +Tabler.Outline.Star }
        action {
            Button(onClick = { nearby() }, variant = ButtonVariant.Secondary) { +"Browse routes" }
        }
    }
}

/** Where a list's rows are in their journey from the network, as the sample's view model would hold it. */
internal enum class Loaded { Loading, Failed, Empty, Ready }

@Composable
fun StateSwitcherBasics() {
    var status by remember { mutableStateOf(Loaded.Loading) }

    // One place for all four. The one leaving fades through the one arriving,
    // and the space between them changes size smoothly rather than in a frame.
    StateSwitcher(target = status, modifier = Modifier.fillMaxWidth()) { now ->
        when (now) {
            Loaded.Loading -> LoadingState { +"Loading your favourites" }
            Loaded.Failed -> ErrorState(onRetry = { status = Loaded.Loading }) {
                +"Couldn't load your favourites"
                message { +"Check your connection and try again." }
            }
            Loaded.Empty -> EmptyState { +"No favourites yet" }
            Loaded.Ready -> Column { stops.forEach { Text(it.name) } }
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

    // The same steps round a ring, for a space too small for a row.
    CircularStepProgress(current = 2, total = 4, contentDescription = "Step 2 of 4")

    // No gap: one continuous bar, filled to the end of the step.
    StepProgress(current = 2, total = 4, gap = 0.dp, contentDescription = "Step 2 of 4")
}

@Composable
fun SkeletonBasics() {
    // Shaped like the content it stands in for, so nothing moves when the real
    // thing arrives. A spinner in the same place would move everything.
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Skeleton(Modifier.width(180.dp).height(20.dp))
        Skeleton(Modifier.width(120.dp).height(16.dp))
        // A shape no skeleton comes in — a map tile — clipped, with the same
        // sweep, so it loads in step with the lines above it.
        Box(Modifier.size(96.dp).clip(Theme.shapes.medium).shimmer())
    }
}

@Composable
fun TagBasics() {
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Tag(tone = Tone.Success) { +"On time" }
        Tag(tone = Tone.Warning) { +"Delayed" }
        // Not a `Chip`: a tag is a label the reader cannot press. A status that
        // filters the list behind it is a `FilterChip`.
        Tag(tone = Tone.Neutral) { +"Platform 2" }
    }
}

@Composable
fun TimelineBasics() {
    Timeline {
        TimelineItem {
            +"Perth Underground"
            supporting { +"08:14 · Platform 2" }
        }
        TimelineItem {
            +"Elizabeth Quay"
            supporting { +"08:21 · Platform 1" }
        }
        // The last item draws no connector below it, because there is nothing
        // for it to connect to.
        TimelineItem(filled = false, connector = ConnectorStyle.None) {
            +"Perth Busport"
            supporting { +"08:29 · Stand 24" }
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
        key = { it.sha },
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
            +"Ordered"
            supporting { +"Mon 3" }
        }
        TimelineItem {
            +"Packed"
            supporting { +"Tue 4" }
        }
        TimelineItem(connector = ConnectorStyle.Dashed) {
            +"On its way"
            supporting { +"Wed 5" }
        }
        TimelineItem(filled = false, connector = ConnectorStyle.None) {
            +"Delivered"
            supporting { +"Thu 6, expected" }
        }
    }
}

@Composable
fun PageIndicatorBasics() {
    val carousel = rememberCarouselState { 5 }
    val scope = rememberCoroutineScope()

    // Given `onPageClick` the dots become the control as well as the readout.
    // The strip takes the tap and sends it to the nearest dot, so the indicator
    // stays the width of its own ink rather than 48dp per page.
    PageIndicator(
        state = carousel,
        onPageClick = { page -> scope.launch { carousel.animateScrollToPage(page) } },
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
