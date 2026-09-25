package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.takeOrElse
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.ListItemImpl
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.components.list.shape
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlin.math.abs

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

/**
 * The colours of a [TimelineList].
 *
 * @param node A stop's node, unless the stop names its own.
 * @param rail The connectors, unless a stop names its own — and, while there is
 *   a `progress`, the nodes not reached yet.
 * @param progress The rail and nodes already passed.
 * @param container The rows' ground, in [TimelineListStyle.Grouped].
 */
@Immutable
data class TimelineListColours(
    val node: Color,
    val rail: Color,
    val progress: Color,
    val container: Color,
)

object TimelineListDefaults {
    /** The theme's accent for nodes and progress, a strong outline for the rail. */
    @Composable
    fun colours(
        node: Color = Theme.colours.primary,
        rail: Color = Theme.colours.outlineStrong,
        progress: Color = Theme.colours.primary,
        container: Color = Theme.colours.surfaceSunken,
    ): TimelineListColours = TimelineListColours(node, rail, progress, container)

    /** A node's diameter: the same as a [TimelineItem]'s. */
    val NodeSize: Dp get() = TimelineNodeSize

    /** The column the rail runs down: the same as a [TimelineItem]'s. */
    val GutterWidth: Dp get() = TimelineGutterWidth
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
 *
 * **Collects rather than emits**, like `ListGroupScope`: a stop's connector is
 * drawn half in its own row and half in the next, and in `Grouped` a row's
 * corners depend on where it sits, so nothing can be drawn until every stop has
 * been declared. The builder is plain Kotlin that runs in composition — `if`
 * works, and reading state in it recomposes the list.
 */
@Stable
@LayoutScopeMarker
class TimelineListScope internal constructor() {

    internal val stops = mutableListOf<TimelineStop>()

    /**
     * One stop: a list row, filled like a `ListItem` — a bare `+` for the label,
     * then `supporting`, `overline`, `leading` and `trailing` by name.
     *
     * @param nodeColour The node's colour. Unspecified takes the list's.
     * @param filled A solid node for a stop; a ring for a point passed through.
     * @param loading A spinner in place of the node, for the stop being waited
     *   on. Drawn, not announced: say "in progress" in the row's words too.
     * @param connector How this stop joins the next — the leg after it. The last
     *   stop's leads nowhere; the list's `leadOut` says what leaves it.
     * @param connectorColour The leg's colour. Unspecified takes the list's rail.
     * @param connectorWidth The leg's weight, and the ring's. Unspecified is
     *   the strong border width, as a [TimelineItem]'s is.
     * @param enabled Whether this row can be used. A disabled list disables
     *   every row whatever this says.
     * @param selected Marks the stop that is current in a list that picks one.
     * @param role What a screen reader calls the row, when it has [onClick].
     * @param onClick The row's action. Without one the row is not a control.
     */
    fun item(
        nodeColour: Color = Color.Unspecified,
        filled: Boolean = true,
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
     * One stop, as text: a label, a second line under it and a value at the end
     * — a time, a platform.
     */
    fun item(
        label: String,
        supporting: String? = null,
        trailing: String? = null,
        nodeColour: Color = Color.Unspecified,
        filled: Boolean = true,
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
 *   there take the progress colour, and the stop it is at, if it is at one, gets
 *   a ring. Null for a list that is not being travelled. Each leg changes colour
 *   halfway, where one row hands it to the next, which is the middle of the leg
 *   when the rows are the same height.
 * @param leadIn A leg arriving at the first stop from above.
 * @param leadOut A leg leaving the last stop downwards.
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
    colours: TimelineListColours = TimelineListDefaults.colours(),
    nodeSize: Dp = TimelineListDefaults.NodeSize,
    gutterWidth: Dp = TimelineListDefaults.GutterWidth,
    content: TimelineListScope.() -> Unit,
) {
    val rows = timelineRows(TimelineListScope().apply(content).stops, leadIn, leadOut)
    Column(modifier.fillMaxWidth()) {
        rows.forEach { TimelineListRow(it, enabled, style, progress, colours, nodeSize, gutterWidth) }
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
 * @param colours Null takes [TimelineListDefaults.colours], which needs the
 *   theme and so is read inside each item.
 */
fun <T> LazyListScope.timelineList(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    enabled: Boolean = true,
    style: TimelineListStyle = TimelineListStyle.Plain,
    progress: Float? = null,
    leadIn: ConnectorStyle = ConnectorStyle.None,
    leadOut: ConnectorStyle = ConnectorStyle.None,
    colours: TimelineListColours? = null,
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
            val resolved = colours ?: TimelineListDefaults.colours()
            if (mine.size == 1) {
                TimelineListRow(mine[0], enabled, style, progress, resolved, nodeSize, gutterWidth)
            } else {
                Column {
                    mine.forEach { TimelineListRow(it, enabled, style, progress, resolved, nodeSize, gutterWidth) }
                }
            }
        }
    }
}

/** One stop as declared. */
internal class TimelineStop(
    val nodeColour: Color,
    val filled: Boolean,
    val loading: Boolean,
    val connector: ConnectorStyle,
    val connectorColour: Color,
    val connectorWidth: Dp,
    val enabled: Boolean,
    val selected: Boolean,
    val role: Role,
    val onClick: (() -> Unit)?,
    val content: ListItemScope.() -> Unit,
)

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

@Composable
private fun TimelineListRow(
    row: TimelineRow,
    listEnabled: Boolean,
    style: TimelineListStyle,
    progress: Float?,
    colours: TimelineListColours,
    nodeSize: Dp,
    gutterWidth: Dp,
) {
    val stop = row.stop
    val index = row.index
    val grouped = style == TimelineListStyle.Grouped
    val defaultWidth = Theme.sizing.borderWidthStrong
    val ringWidth = stop.connectorWidth.takeOrElse { defaultWidth }
    val passed = progress != null && index <= progress + ProgressSlack
    val nodeColour = when {
        stop.nodeColour.isSpecified -> stop.nodeColour
        progress == null -> colours.node
        passed -> colours.progress
        else -> colours.rail
    }
    val here = progress != null && abs(progress - index) <= ProgressSlack
    // Where each half-leg changes to the progress colour, as a share of it.
    val aboveProgress = progress?.let { ((it - (index - 0.5f)) / 0.5f).coerceIn(0f, 1f) } ?: 0f
    val belowProgress = progress?.let { ((it - index) / 0.5f).coerceIn(0f, 1f) } ?: 0f

    val start = Theme.spacing.xs
    val gap = if (grouped && index < row.count - 1) ListItemDefaults.Spacing else 0.dp
    val label = Theme.typography.bodyMedium
    val lineHeight = with(LocalDensity.current) {
        if (label.lineHeight.isSpecified) label.lineHeight.toPx() else label.fontSize.toPx() * FallbackLeading
    }
    // Written by the measure pass below, read by the draw: where the label's
    // first line landed, which is where the node goes.
    val nodeY = remember { mutableFloatStateOf(0f) }

    Layout(
        content = {
            ListItemImpl(
                modifier = Modifier,
                enabled = listEnabled && stop.enabled,
                onClick = stop.onClick,
                selected = stop.selected,
                role = stop.role,
                shape = if (grouped) {
                    ListItemPosition.of(index, row.count).shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner)
                } else {
                    ListItemDefaults.Shape
                },
                containerColour = if (grouped) colours.container else Color.Transparent,
                selectedContainerColour = Theme.colours.accent.container,
                contentColour = Theme.colours.content,
                minHeight = Dp.Unspecified,
                interactionSource = null,
                slots = listItemSlots(stop.content),
                startPadding = start + gutterWidth,
                labelModifier = Modifier.timelineNodeLine(lineHeight),
                edged = grouped,
                disabledContainerColour = if (grouped) {
                    Theme.colours.surfaceSunken.copy(alpha = DisabledGroundAlpha)
                } else {
                    Color.Transparent
                },
            )
            if (stop.loading) Spinner(size = nodeSize, colour = nodeColour, strokeWidth = ringWidth)
        },
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val fromStart = (start + gutterWidth / 2).toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - fromStart else fromStart
                val radius = nodeSize.toPx() / 2f
                val clear = radius + TimelineNodeGap.toPx()
                val y = nodeY.floatValue
                fun RailSegment.draw(from: Offset, to: Offset, passedShare: Float, passedFromNode: Boolean) {
                    val stroke = width.takeOrElse { defaultWidth }.toPx()
                    val leg = this@draw.style
                    drawConnectorRun(leg, from, to, stroke, colour.takeOrElse { colours.rail }, RunEnd.Seam)
                    if (progress != null && passedShare > 0f) {
                        drawPassed(leg, from, to, stroke, colours.progress, passedShare, passedFromNode)
                    }
                }
                if (y - clear > 0f) {
                    row.above?.draw(Offset(x, y - clear), Offset(x, 0f), aboveProgress, passedFromNode = false)
                }
                if (y + clear < size.height) {
                    row.below?.draw(Offset(x, y + clear), Offset(x, size.height), belowProgress, passedFromNode = true)
                }
                if (!stop.loading) {
                    if (here) drawCircle(nodeColour.copy(alpha = HaloAlpha), radius = clear, center = Offset(x, y))
                    drawTimelineNode(Offset(x, y), radius, ringWidth.toPx(), nodeColour, stop.filled)
                }
            },
    ) { measurables, constraints ->
        val card = measurables[0].measure(constraints.copy(minHeight = 0))
        val line = card[TimelineNodeLine]
        val y = if (line == AlignmentLine.Unspecified) card.height / 2 else line
        nodeY.floatValue = y.toFloat()
        val spinner = measurables.getOrNull(1)?.measure(Constraints())
        val railFromStart = (start + gutterWidth / 2).roundToPx()
        layout(card.width, card.height + gap.roundToPx()) {
            card.placeRelative(0, 0)
            spinner?.placeRelative(railFromStart - spinner.width / 2, y - spinner.height / 2)
        }
    }
}

/**
 * The part of a half-leg already travelled, over the rest in the progress colour.
 *
 * The same run drawn again and clipped, rather than a shorter run, so its dots
 * and dashes land exactly on the ones under it. A half-leg is drawn from its
 * node outwards; the part passed is at the node end below a node and at the far
 * end above one.
 */
private fun DrawScope.drawPassed(
    style: ConnectorStyle,
    from: Offset,
    to: Offset,
    stroke: Float,
    colour: Color,
    share: Float,
    fromNode: Boolean,
) {
    val top = minOf(from.y, to.y)
    val bottom = maxOf(from.y, to.y)
    val length = bottom - top
    val (clipTop, clipBottom) = if (fromNode == (from.y < to.y)) {
        // Passed from the top of the run down.
        top - stroke to top + length * share + if (share >= 1f) stroke else 0f
    } else {
        bottom - length * share - (if (share >= 1f) stroke else 0f) to bottom + stroke
    }
    clipRect(top = clipTop, bottom = clipBottom) {
        drawConnectorRun(style, from, to, stroke, colour, RunEnd.Seam)
    }
}

/** How close to a stop `progress` has to be to count as at it. */
private const val ProgressSlack: Float = 0.001f

/** The ring round the stop the journey is at. */
private const val HaloAlpha: Float = 0.3f

/** A disabled grouped row's ground, as `ListItem` draws it. */
private const val DisabledGroundAlpha: Float = 0.5f

/** A line's height against its type size, for a style that does not set one. */
private const val FallbackLeading: Float = 1.4f
