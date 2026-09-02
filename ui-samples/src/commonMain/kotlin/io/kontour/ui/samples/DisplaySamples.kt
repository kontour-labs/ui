package io.kontour.ui.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import androidx.compose.ui.Modifier
import io.kontour.ui.components.display.Accordion
import io.kontour.ui.components.display.AnimatedCounter
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.display.StatTrend
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.PageTransition
import io.kontour.ui.motion.sharedBounds
import io.kontour.ui.motion.marquee
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.Avatar
import io.kontour.ui.components.display.AvatarGroup
import io.kontour.ui.components.display.AvatarSize
import io.kontour.ui.components.display.Badge
import io.kontour.ui.components.display.BadgedBox
import io.kontour.ui.components.display.Banner
import io.kontour.ui.components.display.BannerTone
import io.kontour.ui.components.display.Callout
import io.kontour.ui.components.display.CardVariant
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.display.LinearProgress
import io.kontour.ui.components.display.CircularProgress
import io.kontour.ui.components.display.Skeleton
import io.kontour.ui.components.display.StepProgress
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.display.TagTone
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.theme.Theme

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
        TimelineItem(filled = false) {
            Text("Perth Busport", style = Theme.typography.titleSmall)
            Text("08:29 · Stand 24", style = Theme.typography.bodySmall)
        }
    }
}

@Composable
fun PageIndicatorBasics() {
    val carousel = rememberCarouselState { 5 }
    val scope = rememberCoroutineScope()

    // Given `onPageSelect` the dots become the control as well as the readout,
    // which is what the default style is sized for: every dot keeps its own
    // footprint and its own touch target.
    PageIndicator(
        state = carousel,
        onPageSelect = { page -> scope.launch { carousel.scrollToPage(page) } },
    )
}
