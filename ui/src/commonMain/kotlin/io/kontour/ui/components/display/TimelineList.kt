package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.shape
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/** How a [TimelineList]'s rows are drawn. */
enum class TimelineListStyle {
    /**
     * Clear rows on the page, with the rail running down beside them — a stop
     * list, read like a [Timeline] and tapped like a list.
     */
    Plain,

    /**
     * Sunken rows grouped as one object, as a `ListGroup` draws them, with the
     * rail running through the rows and across the seams between them.
     */
    Grouped,
}

object TimelineListDefaults {
    /** A node's diameter: the same as a [TimelineItem]'s. */
    val NodeSize: Dp get() = TimelineNodeSize

    /** The column the rail runs down: the same as a [TimelineItem]'s. */
    val GutterWidth: Dp get() = TimelineGutterWidth
}

/**
 * Where a [TimelineList]'s stops and a [BranchTimeline]'s commits are declared:
 * each one a list row on the rail, declared the same way in both.
 *
 * **Collects rather than emits**, like `ListGroupScope`: a stop's connector is
 * drawn half in its own row and half in the next, and a row's corners can
 * depend on where it sits, so nothing is drawn until every row is declared. The
 * builder is plain Kotlin that runs in composition — `if` works, and reading
 * state in it recomposes the list.
 *
 * @property filledByDefault Whether a node is solid unless [item] says: true,
 *   except for a merge in a [BranchTimeline], which is a ring so it reads as a
 *   join rather than as work.
 */
@Stable
@LayoutScopeMarker
abstract class TimelineRowScope internal constructor(val filledByDefault: Boolean) {

    internal val stops = mutableListOf<TimelineStop>()

    /**
     * One row, filled like a `ListItem` — a bare `+` for the label, then
     * `supporting`, `overline`, `leading` and `trailing` by name.
     *
     * @param nodeColour The node's colour. Unspecified takes the timeline's.
     * @param filled A solid node for a stop; a ring for a point passed through.
     * @param loading A spinner in place of the node, for the one being waited
     *   on. Drawn, not announced: say "in progress" in the row's words too.
     * @param connector How this row joins the next — the leg after it, or in a
     *   history, the lines down to its parents.
     * @param connectorColour The leg's colour. Unspecified takes the timeline's.
     * @param connectorWidth The leg's weight, and the ring's. Unspecified is
     *   the strong border width, as a [TimelineItem]'s is.
     * @param enabled Whether this row can be used. A disabled timeline disables
     *   every row whatever this says.
     * @param selected Marks the row that is current in a list that picks one.
     * @param role What a screen reader calls the row, when it has [onClick].
     * @param onClick The row's action. Without one the row is not a control.
     */
    fun item(
        nodeColour: Color = Color.Unspecified,
        filled: Boolean = filledByDefault,
        loading: Boolean = false,
        connector: ConnectorStyle = ConnectorStyle.Solid,
        connectorColour: Color = Color.Unspecified,
        connectorWidth: Dp = Dp.Unspecified,
        enabled: Boolean = true,
        selected: Boolean = false,
        role: Role = Role.Button,
        onClick: (() -> Unit)? = null,
        content: ListItemScope.() -> Unit,
    ) {
        stops += TimelineStop(
            nodeColour, filled, loading, connector, connectorColour, connectorWidth,
            enabled, selected, role, onClick, content,
        )
    }

    /**
     * One row, as text: a label, a second line under it and a value at the end
     * — a time, a platform.
     */
    fun item(
        label: String,
        supporting: String? = null,
        trailing: String? = null,
        nodeColour: Color = Color.Unspecified,
        filled: Boolean = filledByDefault,
        loading: Boolean = false,
        connector: ConnectorStyle = ConnectorStyle.Solid,
        onClick: (() -> Unit)? = null,
    ) {
        item(
            nodeColour = nodeColour,
            filled = filled,
            loading = loading,
            connector = connector,
            onClick = onClick,
        ) {
            +label
            if (supporting != null) supporting { +supporting }
            if (trailing != null) {
                trailing {
                    Text(
                        trailing,
                        style = Theme.typography.bodyMedium,
                        colour = Theme.colours.contentMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The stops of a [TimelineList], each one a list row on the rail.
 *
 * ```kotlin
 * TimelineList {
 *     item(onClick = { open(perth) }) {
 *         +"Perth Station"
 *         supporting { +"Platform 3" }
 *         trailing { +"08:12" }
 *     }
 *     item("Walk 4 min", connector = ConnectorStyle.Dashed, filled = false)
 *     item("Elizabeth Quay", trailing = "08:31")
 * }
 * ```
 */
@Stable
@LayoutScopeMarker
class TimelineListScope internal constructor() : TimelineRowScope(filledByDefault = true)

/**
 * A stop list: [Timeline]'s rail beside list rows, with a row's slots and its
 * tap.
 *
 * ```kotlin
 * TimelineList(progress = 1.5f) {
 *     item(onClick = { open(perth) }) {
 *         +"Perth Station"
 *         supporting { +"Platform 3" }
 *         trailing { +"08:12" }
 *     }
 *     item("Walk 4 min", connector = ConnectorStyle.Dashed, filled = false)
 *     item("Elizabeth Quay", trailing = "08:31")
 * }
 * ```
 *
 * For a trip's stops, a delivery's scans, a day's appointments — a sequence
 * whose steps are each something to open, with a value at the end of the line.
 * [Timeline] takes any content beside its rail and has no rows; this takes rows,
 * and the rows are `ListItem`s, so they press, select and read the way every
 * other list in the library does.
 *
 * ### The rail
 *
 * A stop's `connector` is the leg **after** it, as a [TimelineItem]'s is. Each
 * row draws half of the leg above its node and half of the one below, so the
 * rail is unbroken whatever the rows' heights, and a list built lazily
 * ([timelineList]) draws exactly the same. The node sits on the middle of the
 * label's first line, so an overline above it or a second line below does not
 * move it.
 *
 * [leadIn] and [leadOut] draw a leg into the first stop and out of the last,
 * for a list that is a window onto a longer journey.
 *
 * @param enabled Whether the rows can be used. The rail is drawn either way.
 * @param style Clear rows beside the rail, or grouped rows it runs through.
 * @param progress How far along the journey is, in stops: `0f` at the first,
 *   `1.5f` halfway between the second and the third. The rail and nodes up to
 *   there take the progress colour; the stop it is at, if it is at one, pulses,
 *   and the leg it is on carries a band travelling towards the next stop. Null
 *   for a list that is not being travelled. Each leg is split between two rows,
 *   halfway, which is the middle of the leg when the rows are the same height.
 * @param leadIn A leg arriving at the first stop from above.
 * @param leadOut A leg leaving the last stop downwards.
 * @param colours The nodes', rail's and progress's colours, for stops that do
 *   not name their own — the same as a [Timeline]'s.
 * @param containerColour The rows' ground, in [TimelineListStyle.Grouped].
 * @param nodeSize The nodes' diameter.
 * @param gutterWidth The column the rail runs down, at the start of each row.
 * @param content The stops, in order.
 */
@Composable
fun TimelineList(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TimelineListStyle = TimelineListStyle.Plain,
    progress: Float? = null,
    leadIn: ConnectorStyle = ConnectorStyle.None,
    leadOut: ConnectorStyle = ConnectorStyle.None,
    colours: TimelineColours = TimelineDefaults.colours(),
    containerColour: Color = Theme.colours.surfaceSunken,
    nodeSize: Dp = TimelineListDefaults.NodeSize,
    gutterWidth: Dp = TimelineListDefaults.GutterWidth,
    content: TimelineListScope.() -> Unit,
) {
    val rows = timelineRows(TimelineListScope().apply(content).stops, leadIn, leadOut)
    Column(modifier.fillMaxWidth()) {
        rows.forEach { TimelineListRow(it, enabled, style, progress, colours, containerColour, nodeSize, gutterWidth) }
    }
}

/**
 * The same stop list, inside a `LazyColumn`.
 *
 * ```kotlin
 * LazyColumn {
 *     item { SectionHeader { +"Stops" } }
 *     timelineList(stops, key = { it.id }, progress = position) { stop ->
 *         item(stop.name, trailing = stop.time) { open(stop) }
 *     }
 * }
 * ```
 *
 * **Leave the `LazyColumn`'s arrangement alone.** The rail is unbroken because
 * the rows touch; spacing between them leaves gaps in it. Grouped rows keep
 * their own gap inside themselves, which the rail crosses.
 *
 * Like `listGroup`, the builder runs for every element up front — a row cannot
 * draw the top half of a leg until it knows the stop before it — and the rows
 * themselves stay lazy.
 *
 * @param colours Null takes [TimelineDefaults.colours], which needs the
 *   theme and so is read inside each item.
 * @param containerColour Unspecified takes the theme's sunken surface, read
 *   inside each item for the same reason.
 */
fun <T> LazyListScope.timelineList(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    enabled: Boolean = true,
    style: TimelineListStyle = TimelineListStyle.Plain,
    progress: Float? = null,
    leadIn: ConnectorStyle = ConnectorStyle.None,
    leadOut: ConnectorStyle = ConnectorStyle.None,
    colours: TimelineColours? = null,
    containerColour: Color = Color.Unspecified,
    nodeSize: Dp = TimelineListDefaults.NodeSize,
    gutterWidth: Dp = TimelineListDefaults.GutterWidth,
    content: TimelineListScope.(item: T) -> Unit,
) {
    val perElement = items.map { element -> TimelineListScope().apply { content(element) }.stops.toList() }
    val rows = timelineRows(perElement.flatten(), leadIn, leadOut)
    var at = 0
    items.forEachIndexed { index, element ->
        // Captured now: `at` keeps moving after this item is declared.
        val mine = rows.subList(at, at + perElement[index].size)
        at += perElement[index].size
        item(key = key?.invoke(element), contentType = "timelineListRow") {
            val resolved = colours ?: TimelineDefaults.colours()
            val ground = containerColour.takeOrElse { Theme.colours.surfaceSunken }
            if (mine.size == 1) {
                TimelineListRow(mine[0], enabled, style, progress, resolved, ground, nodeSize, gutterWidth)
            } else {
                Column {
                    mine.forEach { TimelineListRow(it, enabled, style, progress, resolved, ground, nodeSize, gutterWidth) }
                }
            }
        }
    }
}

/** One leg of the rail, or half of one: its style, and its colour and width if given. */
internal class RailSegment(val style: ConnectorStyle, val colour: Color, val width: Dp)

/** A stop with the legs its row draws: half of the one above its node and half of the one below. */
internal class TimelineRow(
    val index: Int,
    val count: Int,
    val stop: TimelineStop,
    val above: RailSegment?,
    val below: RailSegment?,
)

/**
 * Which legs each stop's row draws. The leg between two stops is the first
 * one's connector, drawn half in each row; the list's lead-in and lead-out are
 * drawn at the colour and weight of the stop they touch.
 */
internal fun timelineRows(
    stops: List<TimelineStop>,
    leadIn: ConnectorStyle,
    leadOut: ConnectorStyle,
): List<TimelineRow> {
    fun TimelineStop.leg(style: ConnectorStyle = connector): RailSegment? =
        if (style == ConnectorStyle.None) null else RailSegment(style, connectorColour, connectorWidth)
    return stops.mapIndexed { index, stop ->
        TimelineRow(
            index = index,
            count = stops.size,
            stop = stop,
            above = if (index == 0) stop.leg(leadIn) else stops[index - 1].leg(),
            below = if (index == stops.lastIndex) stop.leg(leadOut) else stop.leg(),
        )
    }
}

/**
 * The rail this row draws: half of the leg above its node and half of the one
 * below, each a piece of its whole leg for the progress along it — the leg
 * above arrives here at 1 and is handed over from the row before at ½, the leg
 * below leaves at 0 and is handed on at ½.
 */
internal fun TimelineRow.rail(
    progress: Float?,
    node: Color,
    rail: Color,
    progressColour: Color,
    defaultWidth: Dp,
): RailRow {
    val here = stopProgress(progress, index)
    fun RailSegment.leg(towards: LegAt, leg: Int, from: Float) = RailLeg(
        start = LegEnd(0, LegAt.Node, RunEnd.Mark),
        end = LegEnd(0, towards, RunEnd.Seam),
        style = style,
        colour = colour.takeOrElse { rail },
        width = width.takeOrElse { defaultWidth },
        travel = stopProgress(progress, leg)?.let {
            LegTravel(from = from, to = 0.5f, passed = it.legPassed, band = it.legBand, colour = progressColour)
        },
    )
    return RailRow(
        node = RailNode(
            lane = 0,
            colour = nodeColourFor(stop.nodeColour, here, node, progressColour, rail),
            filled = stop.filled,
            loading = stop.loading,
            ringWidth = stop.connectorWidth.takeOrElse { defaultWidth },
            here = here?.here == true,
        ),
        legs = listOfNotNull(
            above?.leg(LegAt.Start, index - 1, from = 1f),
            below?.leg(LegAt.End, index, from = 0f),
        ),
    )
}

@Composable
private fun TimelineListRow(
    row: TimelineRow,
    listEnabled: Boolean,
    style: TimelineListStyle,
    progress: Float?,
    colours: TimelineColours,
    containerColour: Color,
    nodeSize: Dp,
    gutterWidth: Dp,
) {
    val grouped = style == TimelineListStyle.Grouped
    val defaultWidth = Theme.sizing.borderWidthStrong
    RailListRow(
        stop = row.stop,
        rail = row.rail(progress, colours.node, colours.rail, colours.progress, defaultWidth),
        listEnabled = listEnabled,
        lanes = 1,
        laneWidth = gutterWidth,
        gutterWidth = gutterWidth,
        nodeSize = nodeSize,
        shape = if (grouped) {
            ListItemPosition.of(row.index, row.count).shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner)
        } else {
            ListItemDefaults.Shape
        },
        containerColour = if (grouped) containerColour else Color.Transparent,
        disabledContainerColour = if (grouped) {
            Theme.colours.surfaceSunken.copy(alpha = DisabledGroundAlpha)
        } else {
            Color.Transparent
        },
        edged = grouped,
        gapBelow = if (grouped && row.index < row.count - 1) ListItemDefaults.Spacing else 0.dp,
    )
}

/** A disabled grouped row's ground, as `ListItem` draws it. */
private const val DisabledGroundAlpha: Float = 0.5f
