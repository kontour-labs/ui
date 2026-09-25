package io.kontour.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Train
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
import io.kontour.ui.components.display.CarouselDefaults
import io.kontour.ui.components.display.CarouselStyle
import io.kontour.ui.components.display.CircularProgress
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.display.ErrorState
import io.kontour.ui.components.display.Kbd
import io.kontour.ui.components.display.KbdDefaults
import io.kontour.ui.components.display.KbdIcons
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.LinearProgress
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.PageIndicatorStyle
import io.kontour.ui.components.display.Skeleton
import io.kontour.ui.components.display.SkeletonListItem
import io.kontour.ui.components.display.SkeletonText
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.display.StatTrend
import io.kontour.ui.components.display.StepProgress
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.display.TagTone
import io.kontour.ui.components.display.HorizontalTimeline
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.BranchTimeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.components.display.TimelineList
import io.kontour.ui.components.display.TimelineListStyle
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Redacted
import io.kontour.ui.foundation.Text
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Spacer
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.display.BubblePosition
import io.kontour.ui.components.display.BubbleSide
import io.kontour.ui.components.display.ChatBubble
import io.kontour.ui.components.display.GaugeTickPlacement
import io.kontour.ui.components.display.ScaleColours
import io.kontour.ui.components.display.GaugeIndicator
import io.kontour.ui.components.display.GaugeDefaults
import io.kontour.ui.components.display.Gauge
import io.kontour.ui.motion.marquee
import io.kontour.ui.theme.Theme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
    // Wraps rather than squeezing: at Large, four avatars and a group are wider
    // than a phone's card, and a squeezed row is how the group's count ended up
    // drawn as a sliver on an iPhone.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        itemVerticalAlignment = Alignment.CenterVertically,
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

private val calloutTone = Knob.Choice("Tone", BannerTone.entries.toList())

internal val CalloutDemo = ComponentDemo(slug = "callout", knobs = listOf(calloutTone)) {
    Callout(Modifier.fillMaxWidth(), tone = this[calloutTone]) {
        Text("Melbourne, Sydney and Canberra do not currently support journey planning.")
    }
}

// The middle leg is the walk, and a dashed connector is how a journey planner
// says "you are on your own for this bit". The knob is what shows that the
// choice is per-item rather than per-timeline.
private val timelineConnector =
    Knob.Choice("Walk connector", ConnectorStyle.entries.toList(), ConnectorStyle.Dashed)

/**
 * Whether the leg in progress is still resolving.
 *
 * The node becomes a spinner and the connector below it carries on, which is the
 * whole shape of the feature: the itinerary is not in doubt, one step of it is.
 */
private val timelineLoading = Knob.Flag("Walk in progress", initial = false)

/**
 * The same three stops laid across the page, by a `HorizontalTimeline`.
 *
 * The items are the ones the vertical timeline takes, unchanged: which way they
 * run is the container's to say.
 */
private val timelineAcross = Knob.Flag("Horizontal", initial = false)

/** Across, every stop as wide as the widest, so the nodes are evenly spaced. */
private val timelineEqualWidths = Knob.Flag("Equal widths", initial = false)

internal val TimelineDemo = ComponentDemo(
    slug = "timeline",
    knobs = listOf(timelineConnector, timelineLoading, timelineAcross, timelineEqualWidths),
) {
    val walking = this[timelineLoading]
    val connector = this[timelineConnector]
    val stops: @Composable () -> Unit = {
        TimelineItem(nodeColour = Color(0xFF1B5E20)) {
            Text("Perth Station", style = Theme.typography.titleSmall)
            Text(
                "08:12 — Platform 3",
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
        }
        TimelineItem(
            connector = connector,
            filled = false,
            loading = walking,
            nodeColour = Theme.colours.outlineStrong,
        ) {
            Text(
                // In the words as well as in the node, which is the rule the
                // component's own KDoc states: the spinner is not announced.
                if (walking) "Walking — 4 min" else "Walk 4 min",
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
        }
        TimelineItem(connector = ConnectorStyle.None, nodeColour = Color(0xFF1B5E20)) {
            Text("Elizabeth Quay", style = Theme.typography.titleSmall)
            Text("08:31", style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
        }
    }
    if (this[timelineAcross]) {
        HorizontalTimeline(Modifier.fillMaxWidth(), equalWidths = this[timelineEqualWidths]) { stops() }
    } else {
        Timeline(Modifier.fillMaxWidth()) { stops() }
    }
}

private val timelineListStyle = Knob.Choice("Style", TimelineListStyle.entries.toList())

/** How far along the trip is, in stops. Halfway between two is on the leg. */
private enum class TripProgress(val label: String, val stops: Float?) {
    None("None", null),
    Start("At Perth Station", 0f),
    Walking("Walking", 1.5f),
    Arrived("Arrived", 3f),
}

private val timelineListProgress = Knob.Choice("Progress", TripProgress.entries.toList(), name = { it.label })

/** The stop being waited on: a spinner where its node was, and the words say so. */
private val timelineListLoading = Knob.Flag("Loading", initial = false)

internal val TimelineListDemo = ComponentDemo(
    slug = "timeline-list",
    knobs = listOf(timelineListStyle, timelineListProgress, timelineListLoading),
) {
    val waiting = this[timelineListLoading]
    var picked by remember { mutableStateOf(0) }
    TimelineList(
        Modifier.fillMaxWidth(),
        style = this[timelineListStyle],
        progress = this[timelineListProgress].stops,
        leadIn = ConnectorStyle.Dotted,
    ) {
        item(
            nodeColour = Color(0xFF1B5E20),
            selected = picked == 0,
            onClick = { picked = 0; echo("Opened Perth Station") },
        ) {
            +"Perth Station"
            supporting { +"Platform 3 · Joondalup line" }
            trailing { Text("08:12", colour = Theme.colours.contentMuted) }
        }
        item(
            connector = ConnectorStyle.Dashed,
            filled = false,
            loading = waiting,
            selected = picked == 1,
            onClick = { picked = 1; echo("Opened the walk") },
        ) {
            +if (waiting) "Finding your platform" else "Walk to Elizabeth Quay"
            supporting { +"4 min · 350 m" }
        }
        item(
            nodeColour = Color(0xFF0D47A1),
            selected = picked == 2,
            onClick = { picked = 2; echo("Opened Elizabeth Quay") },
        ) {
            overline { +"Route 950" }
            +"Elizabeth Quay"
            supporting { +"Stand C" }
            trailing { Text("08:21", colour = Theme.colours.contentMuted) }
        }
        item("Perth Busport", supporting = "Stand 24", trailing = "08:29") {
            picked = 3
            echo("Opened Perth Busport")
        }
    }
}

/** One commit of a demo history: the list is newest first, each above its parents. */
private class DemoCommit(val sha: String, val message: String, val author: String, val parents: List<String>)

private val linearHistory = listOf(
    DemoCommit("c4", "Show platform changes", "Sam", listOf("c3")),
    DemoCommit("c3", "Fix stop search", "Ari", listOf("c2")),
    DemoCommit("c2", "Add journey planner", "Sam", listOf("c1")),
    DemoCommit("c1", "Initial commit", "Ari", emptyList()),
)

private val featureHistory = listOf(
    DemoCommit("m1", "Merge feature/timetables", "Sam", listOf("c3", "f2")),
    DemoCommit("f2", "Cache timetables offline", "Kai", listOf("f1")),
    DemoCommit("c3", "Fix stop search", "Ari", listOf("c2")),
    DemoCommit("f1", "Timetable model", "Kai", listOf("c2")),
    DemoCommit("c2", "Add journey planner", "Sam", listOf("c1")),
    DemoCommit("c1", "Initial commit", "Ari", emptyList()),
)

private val branchingHistory = listOf(
    DemoCommit("r2", "Release 2.1", "Ari", listOf("r1")),
    DemoCommit("m2", "Merge feature/maps", "Sam", listOf("c4", "f2")),
    DemoCommit("f2", "Map tiles", "Kai", listOf("f1")),
    DemoCommit("c4", "Update dependencies", "Sam", listOf("c3")),
    DemoCommit("r1", "Hotfix: crash on launch", "Ari", listOf("c3")),
    DemoCommit("f1", "Map view", "Kai", listOf("c3")),
    DemoCommit("c3", "Fix stop search", "Ari", listOf("c2")),
    DemoCommit("c2", "Add journey planner", "Sam", listOf("c1")),
    DemoCommit("c1", "Initial commit", "Ari", emptyList()),
)

private val branchHistory = Knob.Choice(
    "History",
    listOf("Linear", "Feature merged", "Two branches"),
    initial = "Two branches",
)

/** The release commits' lane in one colour of its own, from the tip down. */
private val branchReleaseColour = Knob.Flag("Colour release lane", initial = false)

internal val BranchTimelineDemo = ComponentDemo(
    slug = "branch-timeline",
    knobs = listOf(branchHistory, branchReleaseColour),
) {
    val commits = when (this[branchHistory]) {
        "Linear" -> linearHistory
        "Feature merged" -> featureHistory
        else -> branchingHistory
    }
    val release = Theme.colours.success.solid
    var picked by remember { mutableStateOf<String?>(null) }
    BranchTimeline(
        items = commits,
        id = { it.sha },
        parents = { it.parents },
        modifier = Modifier.fillMaxWidth(),
        isSelected = { it.sha == picked },
        onItemClick = {
            picked = it.sha
            echo("Opened ${it.message}")
        },
        laneColour = if (this[branchReleaseColour]) {
            { commit -> release.takeIf { commit.sha.startsWith("r") } }
        } else {
            null
        },
    ) { commit ->
        +commit.message
        supporting { +"${commit.author} · ${commit.sha}" }
    }
}

private val redactionOn = Knob.Flag("Loading", initial = true)

internal val RedactionDemo = ComponentDemo(
    slug = "redaction",
    knobs = listOf(redactionOn),
) {
    // The same rows either way. That is the whole demonstration: nothing here
    // is a second, placeholder-shaped copy of the layout — flip the knob and the
    // ink comes back.
    Redacted(this[redactionOn]) {
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            ListItem {
                +"Perth Underground"
                supporting { +"08:14 · Platform 2 · on time" }
                leading { +Tabler.Outline.Train }
            }
            ListItem {
                +"Elizabeth Quay"
                supporting { +"A longer line, so the bar under it wraps and the second one comes out short" }
                leading { +Tabler.Outline.Bus }
            }
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

/**
 * The two states share a page and a shape, so they share a demo.
 *
 * `ErrorState` is not a variant of `EmptyState` — it owns its retry button
 * rather than taking an action slot, on the argument that an error with no way
 * forward is a dead end. A knob is still the right way to show them: they are
 * the same slot on the same screen, and the difference is what a reader came to
 * see.
 */
private val stateKind = Knob.Choice("State", listOf("Empty", "Error"))

internal val EmptyStateDemo = ComponentDemo(
    slug = "empty-state",
    knobs = listOf(stateKind),
) {
    if (this@ComponentDemo[stateKind] == "Error") {
        ErrorState(Modifier.fillMaxWidth(), onRetry = { echo("Retry") }) {
            +"Couldn't load your favourites"
            supporting { +"Check your connection and try again." }
            leading { +Tabler.Outline.AlertTriangle }
        }
        return@ComponentDemo
    }
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

/**
 * Whether a falling number is announced before it falls.
 *
 * A flag and not a choice: `warnBefore` is a `Duration`, and the question a
 * reader has is whether it warns rather than for how long.
 *
 * On by default, because the warning is invisible until a value drops and a
 * reader who presses "Tick down" once with it off has learned nothing about the
 * parameter. That is only safe because `AnimatedCounter` drops `warnBefore`
 * entirely under `reduceMotion` and the response harness sets it. If that gate
 * ever moves, this knob makes the counter look dead to `EverythingRespondsTest`
 * for as long as the warning runs.
 */
private val counterWarn = Knob.Flag("Warn before dropping", initial = true)

/** Two there-and-backs at the tremor's own rhythm, which is what it is tuned for. */
private val CounterWarning: Duration = 450.milliseconds

internal val AnimatedCounterDemo = ComponentDemo(
    slug = "animated-counter",
    knobs = listOf(counterWarn),
) {
    var minutes by remember { mutableStateOf(14) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedCounter(
            value = minutes,
            format = { "$it min" },
            style = Theme.typography.headlineSmall,
            // 450ms, which is the length the tremor is written for: `warnBefore`
            // is the shake itself now rather than a hold with a shake somewhere
            // inside it, so the 1.5s this used to pass would be a second and a
            // half of shaking.
            warnBefore = if (this@ComponentDemo[counterWarn]) CounterWarning else Duration.ZERO,
        )
        Button(
            onClick = {
                minutes = (minutes + 12) % 60
                echo("Advance")
            },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Small,
        ) { +"Advance" }
        Button(
            onClick = {
                minutes = if (minutes == 0) 1 else minutes - 1
                echo("Tick down")
            },
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

// `initial` is the component's own default rather than the first declared, so the
// demo opens on what a caller who passes no `style` actually gets. On one line
// because `check-components.py` reads `Knob.Choice(…, X.entries)` a line at a time
// to know which enums a reader can sweep.
private val indicatorStyle =
    Knob.Choice("Style", PageIndicatorStyle.entries.toList(), initial = PageIndicatorStyle.Pill)

private val carouselStyle = Knob.Choice("Pages", CarouselStyle.entries.toList())

/** Enough drift behind the closing edge to see, well short of a slide. */
private const val CarouselDrift = 0.3f

// How much of the next page a hero carousel shows. Not a dial, because a knob is a
// choice or a flag: off is the component's own default — none, one page in the
// frame and the gap opening only while a swipe is in flight — and on is a peek of
// the next picture.
private val carouselPeek = Knob.Flag("Peek")

/** Wide enough to read as a picture rather than a stripe. See `carouselHeroPeek`. */
private val CarouselPeek = 96.dp

// And whether the page being left behind holds still under the box closing over it
// or travels out with the strip. `0.3` rather than `1` is where the component's own
// docs put it: a drift behind the edge, not a slide.
private val carouselParallax = Knob.Flag("Parallax")

internal val CarouselDemo = ComponentDemo(
    slug = "carousel",
    knobs = listOf(carouselStyle, carouselPeek, carouselParallax, indicatorStyle),
) {
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
            style = this@ComponentDemo[carouselStyle],
            peek = if (this@ComponentDemo[carouselPeek]) CarouselPeek else CarouselDefaults.HeroPeek,
            parallax = if (this@ComponentDemo[carouselParallax]) CarouselDrift else 0f,
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
            // Echoes as well as scrolls, like every other demo. Not decoration: the
        // scroll is a suspending animation, so pressing a dot changes nothing
        // observable until the clock advances — and under a hand-driven clock,
        // which `EverythingRespondsTest` needs because the overlays never stop
        // asking for frames, all four dots read as wired to nothing.
        onPageSelect = { page ->
            echo("Page ${page + 1}")
            scope.launch { carousel.scrollToPage(page) }
        },
        )
    }
}

internal val PageIndicatorDemo = ComponentDemo(
    slug = "page-indicator",
    knobs = listOf(indicatorStyle),
) {
    val carousel = rememberCarouselState { 5 }
    val scope = rememberCoroutineScope()
    // The indicator is also the control here, which is what the strip's single
    // touch target is for: one band the width of the dots rather than 48dp a dot.
    PageIndicator(
        state = carousel,
        style = this[indicatorStyle],
        // Echoes as well as scrolls, like every other demo. Not decoration: the
        // scroll is a suspending animation, so pressing a dot changes nothing
        // observable until the clock advances — and under a hand-driven clock,
        // which `EverythingRespondsTest` needs because the overlays never stop
        // asking for frames, all four dots read as wired to nothing.
        onPageSelect = { page ->
            echo("Page ${page + 1}")
            scope.launch { carousel.scrollToPage(page) }
        },
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

/**
 * The nine keys Tabler draws, as an icon and as a character.
 *
 * Paired rather than listed twice, so the knob is one `if` and the two rows
 * cannot drift apart.
 */
private val KbdKeys = listOf(
    KbdIcons.Command to KbdDefaults.Command,
    KbdIcons.Shift to KbdDefaults.Shift,
    KbdIcons.Return to KbdDefaults.Return,
    KbdIcons.Backspace to KbdDefaults.Backspace,
    KbdIcons.Tab to KbdDefaults.Tab,
    KbdIcons.CapsLock to KbdDefaults.CapsLock,
    KbdIcons.PageUp to KbdDefaults.PageUp,
    KbdIcons.PageDown to KbdDefaults.PageDown,
    KbdIcons.Space to KbdDefaults.Space,
)

/**
 * Icons against characters, which is the page's own argument made pressable.
 *
 * `Kbd`'s KDoc *measures* what a character costs — rendered against its own
 * 20dp cap, ⌘ sits 1.0dp high and ⇧ 0.5dp low, a point and a half of difference
 * between two caps side by side — and a paragraph is the wrong place to settle
 * that. Flipped, the misalignment is the thing a reader sees.
 *
 * This demo used to draw `Kbd { +"⌘" }`, which is the one thing the page and the
 * compiled sample both tell a reader not to do.
 */
private val kbdIcons = Knob.Flag("Icons", initial = true)

internal val KbdDemo = ComponentDemo(slug = "kbd", knobs = listOf(kbdIcons)) {
    val icons = this[kbdIcons]
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Kbd { if (icons) +KbdIcons.Command else +KbdDefaults.Command }
            Kbd { +"K" }
            Text(
                "opens the command palette",
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            KbdKeys.forEach { (icon, character) ->
                Kbd { if (icons) +icon else +character }
            }
        }
        Text(
            "⌥ and ⌃ stay characters because the bundled mono draws them. ⎋ and " +
                "⌦ stay characters because nothing draws them.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val SpinnerDemo = ComponentDemo(slug = "spinner") {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner()
            Text("default", style = Theme.typography.labelSmall, colour = Theme.colours.contentMuted)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner(size = 32.dp)
            Text("32dp", style = Theme.typography.labelSmall, colour = Theme.colours.contentMuted)
        }
    }
}

// --- Gauge -----------------------------------------------------------------

private val gaugeIndicator = Knob.Choice("Indicator", GaugeIndicator.entries.toList(), GaugeIndicator.Needle)
private val gaugeTicks = Knob.Choice("Ticks", GaugeTickPlacement.entries.toList(), GaugeTickPlacement.Inside)

/**
 * What the fill is painted with. Bands are the tachometer's green, amber and red at
 * 6,000 and 8,000 — given in revolutions, the gauge's own units — and smooth bands
 * the same with their edges blended.
 */
private enum class DialFillDemo { Solid, Gradient, Bands, SmoothBands }

private val gaugeFill = Knob.Choice(
    "Fill",
    DialFillDemo.entries.toList(),
    DialFillDemo.Gradient,
    name = { if (it == DialFillDemo.SmoothBands) "Smooth bands" else it.name },
)

/** How far the needle reaches; the middle is the default, clear of the labels. */
private val gaugeNeedleLength =
    Knob.Choice("Needle length", listOf(0.5f, 0.8f, 1.3f), 0.8f, name = { "$it" })

/** The needle's colour: the text colour by default, or the accent, or the warning red. */
private val gaugeNeedleColour =
    Knob.Choice("Needle colour", listOf("Content", "Accent", "Danger"), "Content", name = { it })

/**
 * The needle in the fill's colour at the reading — the band it is in — over the
 * colour chosen above. With bands, turn the reading past 6,000 and 8,000 to see it
 * change.
 */
private val gaugeNeedleMatchesFill = Knob.Flag("Needle matches fill")

/**
 * A translucent capsule behind the reading, so the needle passes under it. Turn the
 * reading down past 1,000 or so, where the needle sweeps across the label, to see
 * what it is for.
 */
private val gaugeLabelBackground = Knob.Flag("Label background")

/** Off, a new reading is drawn where it lands rather than travelling there. */
private val gaugeAnimated = Knob.Flag("Animated", initial = true)

/** How far round the scale goes, with the gap always centred at the bottom. */
private val gaugeSweep =
    Knob.Choice("Sweep", listOf(180f, 240f, 270f, 320f), 270f, name = { "${it.roundToInt()}°" })
private val gaugeThickness =
    Knob.Choice("Thickness", listOf(6.dp, 12.dp, 20.dp), 12.dp, name = { "${it.value.roundToInt()}dp" })

/** Square ends on the track and the fill, for a dial read as an instrument. */
private val gaugeFlatEnds = Knob.Flag("Flat ends")

internal val GaugeDemo = ComponentDemo(
    slug = "gauge",
    knobs = listOf(
        gaugeIndicator,
        gaugeTicks,
        gaugeSweep,
        gaugeThickness,
        gaugeFlatEnds,
        gaugeFill,
        gaugeNeedleLength,
        gaugeNeedleColour,
        gaugeNeedleMatchesFill,
        gaugeLabelBackground,
        gaugeAnimated,
    ),
) {
    var rpm by remember { mutableStateOf(8_500f) }
    val indicator = this[gaugeIndicator]
    val ticks = this[gaugeTicks]
    val fill = when (this[gaugeFill]) {
        DialFillDemo.Solid -> ScaleColours.solid(Theme.colours.primary)
        DialFillDemo.Gradient -> ScaleColours.gradient(listOf(GaugePink, GaugePurple))
        DialFillDemo.Bands -> rpmBands(smoothing = 0f)
        DialFillDemo.SmoothBands -> rpmBands(smoothing = 0.6f)
    }
    val needleColour = when (this[gaugeNeedleColour]) {
        "Accent" -> Theme.colours.primary
        "Danger" -> Theme.colours.danger.solid
        else -> Theme.colours.content
    }
    val animated = this[gaugeAnimated]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Gauge(
            value = rpm,
            valueRange = 0f..10_000f,
            size = 200.dp,
            sweepAngle = this@ComponentDemo[gaugeSweep],
            thickness = this@ComponentDemo[gaugeThickness],
            cap = if (this@ComponentDemo[gaugeFlatEnds]) StrokeCap.Butt else StrokeCap.Round,
            indicator = indicator,
            needleLength = this@ComponentDemo[gaugeNeedleLength],
            needleMatchesFill = this@ComponentDemo[gaugeNeedleMatchesFill],
            contentBackground = this@ComponentDemo[gaugeLabelBackground],
            majorTicks = 6,
            minorTicks = 1,
            tickLabel = { "${(it / 1000).roundToInt()}K" },
            tickPlacement = ticks,
            animated = animated,
            colours = GaugeDefaults.colours(indicator = fill, needle = needleColour),
            contentDescription = "Engine speed",
            stateDescription = { "${it.roundToInt()} revolutions a minute" },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(rpm / 100).roundToInt() / 10f}k", style = Theme.typography.titleLarge)
                Text("RPM", style = Theme.typography.labelSmall, colour = Theme.colours.contentMuted)
            }
        }
        Slider(
            value = rpm,
            onValueChange = { rpm = it },
            valueRange = 0f..10_000f,
            contentDescription = "Engine speed",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val GaugePink = Color(0xFFFF5C9E)
private val GaugePurple = Color(0xFF7C5CFF)

/** The tachometer's zones, in revolutions: green to 6,000, amber to 8,000, then red. */
@Composable
private fun rpmBands(smoothing: Float): ScaleColours =
    ScaleColours.bands(smoothing = smoothing) {
        band(from = 0f, colour = Theme.colours.success.solid)
        band(from = 6_000f, colour = Theme.colours.warning.solid)
        band(from = 8_000f, colour = Theme.colours.danger.solid)
    }

// --- ChatBubble ------------------------------------------------------------

private val bubbleSide = Knob.Choice("Side", BubbleSide.entries.toList(), BubbleSide.Outgoing)
private val bubblePosition = Knob.Choice("Position", BubblePosition.entries.toList(), BubblePosition.Only)

/** Off leaves every bubble of a run without one, for a quieter thread. */
private val bubbleTail = Knob.Flag("Tail", initial = true)

internal val ChatBubbleDemo = ComponentDemo(
    slug = "chat-bubble",
    knobs = listOf(bubbleSide, bubblePosition, bubbleTail),
) {
    val side = this[bubbleSide]
    val position = this[bubblePosition]
    val tail = this[bubbleTail]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChatBubble(side = BubbleSide.Incoming, position = BubblePosition.First, tail = tail) {
            Text("Is the 950 running tonight?")
        }
        ChatBubble(
            side = BubbleSide.Incoming,
            position = BubblePosition.Last,
            tail = tail,
            meta = { Text("9:41") },
        ) {
            Text("The app says it's delayed")
        }
        Spacer(Modifier.height(Theme.spacing.sm))
        ChatBubble(side = side, position = position, tail = tail, meta = { Text("9:42 · Read") }) {
            Text("Every 15 minutes until 11pm, then every 30 overnight.")
        }
    }
}

internal val displayDemos = listOf(
    CardDemo,
    TagDemo,
    BadgeDemo,
    AvatarDemo,
    ProgressDemo,
    SpinnerDemo,
    BannerDemo,
    CalloutDemo,
    TimelineDemo,
    TimelineListDemo,
    BranchTimelineDemo,
    RedactionDemo,
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
    GaugeDemo,
    ChatBubbleDemo,
)
