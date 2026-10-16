package io.kontour.ui.components.display

import io.kontour.ui.theme.clipProxy
import io.kontour.ui.locale.bidiIsolated
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.a11y.optionalBorder
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.contentScope
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import kotlin.math.roundToInt
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import io.kontour.ui.a11y.LocalNamedByContent


/**
 * A small label — a status, a category, a route number.
 *
 * ```
 * Tag(tone = Tone.Success) { +"Live" }
 * Tag(containerColour = routeColour) { +"960" } // colour straight out of a GTFS feed
 * Tag(onClick = { openRoute(960) }) { +"960" }   // a label that leads to what it labels
 * ```
 *
 * Not a [io.kontour.ui.components.selection.Chip]. A chip is a choice or an
 * action among others; a tag is something the interface is telling the reader.
 * An [onClick] makes a tag a way *to* what it labels — a route number that opens
 * the route, a category that lists everything in it — and it stays a label to
 * look at: same size, same colours, a press and a focus ring added. On a touch
 * screen it reserves the platform's touch target around itself, so a clickable
 * tag takes more room in a row than a plain one.
 *
 * ### Arbitrary colours
 *
 * Transit feeds supply their own route colours, and they are not drawn from any
 * palette — a route can be pale yellow or near-black. Passing [containerColour] resolves
 * the label with [contentColourFor], which picks whichever of light or dark reads
 * better on it. That is the whole reason this component exists rather than
 * callers styling a `Surface` themselves: the one thing they would get wrong is
 * the case where the feed hands them a colour nobody designed for.
 *
 * @param containerColour An explicit background. Overrides [tone]. The label colour is
 *   derived, not guessed.
 * @param onClick What a press does, or `null`, the default, for a tag that is only
 *   a label. Announced as a button with its text.
 * @param enabled Whether a clickable tag takes presses. A disabled one keeps its
 *   colours and dims its text.
 */
@Composable
fun Tag(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Tone = Tone.Neutral,
    containerColour: Color = Color.Unspecified,
    shape: Shape = Theme.shapes.control,
    onClick: (() -> Unit)? = null,
    /**
     * Overrides what the tag announces.
     *
     * Rarely needed: the content is merged, so `+"Delayed"` already announces as
     * "Delayed". This is for a tag whose text is an abbreviation the spoken form
     * should expand — "PU" reading as "Perth Underground".
     */
    contentDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowContentScope.() -> Unit
) {
    val container = if (containerColour != Color.Unspecified) containerColour else tagContainerFor(tone)
    val clickable = onClick != null
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val contentColour = if (clickable && !enabled) {
        Theme.colours.contentDisabled
    } else if (containerColour != Color.Unspecified) {
        contentColourFor(
            background = containerColour,
            light = Theme.colours.onPrimary.takeIf { !Theme.colours.isDark } ?: Theme.colours.content,
            dark = Theme.colours.content.takeIf { !Theme.colours.isDark } ?: Theme.colours.onPrimary,
        )
    } else {
        tagContentFor(tone)
    }

    Row(
        modifier = modifier
            // Target first, then the visuals, as a chip does: reserve the room a
            // finger needs, and draw the tag inside it.
            .then(
                if (clickable) Modifier.minimumTouchTarget().focusRing(interactions, shape, enabled = enabled)
                else Modifier
            )
            // Merged rather than described: a tag used to build its own
            // `contentDescription` from its `label`, and with the label in a slot
            // there is no string to build one from. Merging is the better answer
            // anyway — whatever the content is, that is what it announces.
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .clip(shape.clipProxy())
            .background(container, shape)
            // Each tone's own border rather than a grey rectangle drawn around
            // a coloured one. Neutral is the tone that needs this — it is
            // `surfaceSunken`, which at the dark enhanced tier is the same
            // colour as the page it sits on — but a tag that gained an edge only when
            // it was grey would read as a different kind of tag.
            .then(
                contrastEdge(tagBorderFor(tone))
                    ?.let { Modifier.border(it, shape) } ?: Modifier
            )
            .then(
                if (onClick != null) {
                    Modifier.pointerCursor(enabled = enabled).clickable(
                        interactionSource = interactions,
                        indication = kontourIndication(shape),
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColour provides contentColour) {
            ProvideTextStyle(
                Theme.typography.labelSmall.copy()
            ) {
                contentScope(
                    iconSize = TagIconSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    content = content,
                )
            }
        }
    }
}

/** Smaller than `iconSmall`: a tag is a label, not a control. */
private val TagIconSize = 12.dp

/**
 * A count or a dot, for attaching to something else.
 *
 * ```
 * BadgedBox(badge = { Badge(count = unread) }) {
 *     Icon(Tabler.Outline.Bell, contentDescription = "Alerts")
 * }
 * ```
 *
 * Pass no [count] for a bare dot — "there is something here" without a number,
 * which is the right choice when the exact figure is not actionable.
 *
 * Counts above [max] render as "9+" rather than growing the badge, because a
 * badge wide enough for "247" stops being a badge.
 */
@Composable
fun Badge(
    modifier: Modifier = Modifier,
    count: Int? = null,
    max: Int = 9,
    containerColour: Color = Theme.colours.danger.solid,
    contentColour: Color = Theme.colours.danger.onSolid,
    contentDescription: String? = null,
) {
    val strings = Theme.strings
    val formats = Theme.formats
    // Isolated, so `99+` stays `99+` inside a right-to-left line rather than
    // reading `+99` — the plus is a neutral and would take the line's direction.
    val label = when {
        count == null -> null
        count > max -> bidiIsolated(strings.cappedCount(formats.quantity(max)))
        else -> formats.integer(count)
    }

    val announcement = contentDescription ?: when {
        count == null -> strings.new
        count > max -> strings.moreThan(formats.quantity(max))
        else -> strings.newCount(formats.quantity(count))
    }
    // In a row named by its own text, said as text among the rest, not as a
    // description read in the row's place: a chat row was "3 unread" and
    // nothing else. See `LocalNamedByContent`.
    val said = if (LocalNamedByContent.current) {
        Modifier.clearAndSetSemantics { text = AnnotatedString(announcement) }
    } else {
        Modifier.semantics { this.contentDescription = announcement }
    }

    // At the enhanced tier a ring of the page's colour round the badge, so a red
    // dot straddling a black icon is a dot on the icon rather than part of it.
    val ring = if (highContrast()) BorderStroke(Theme.sizing.borderWidth, Theme.colours.background) else null
    val round = Theme.shapes.pill
    if (label == null) {
        Box(
            modifier
                .then(said)
                .defaultMinSize(minWidth = 8.dp, minHeight = 8.dp)
                .clip(round.clipProxy())
                .background(containerColour)
                .optionalBorder(ring, round)
        )
    } else {
        Box(
            modifier = modifier
                .then(said)
                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                .clip(Theme.shapes.capsule.clipProxy())
                .background(containerColour)
                .optionalBorder(ring, Theme.shapes.capsule)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = Theme.typography.labelSmall,
                colour = contentColour,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * Straddles a [Badge] over the top-trailing corner of [content].
 *
 * The badge's centre sits **on** the corner, so it overhangs by half of itself
 * in each direction and does not eat into the icon it is annotating.
 *
 * ### Why this is a Layout and not a Box with padding
 *
 * It used to be `align(TopEnd).padding(start = 12.dp, bottom = 12.dp)`, which
 * shifts the badge up and right by a fixed 12dp however big the badge is. A dot
 * badge is 8dp and a "9+" is nearer 24, so the same call left one of them
 * floating clear of the corner and the other sitting mostly inside the icon.
 * The offset has to come from the badge's own measured size, and a modifier
 * cannot read that.
 *
 * ### Exactly the size of what it badges
 *
 * It reports [content]'s size, and its baselines, and the badge hangs outside
 * them. It used to report a size that included the overhang — half the badge,
 * on the top and the trailing side only — with the content pushed to the
 * bottom-start of that, so nothing was outside the bounds for a clipping parent
 * to cut. But a row centres what it is given: a badged button in a top bar's
 * actions sat lower than the buttons beside it, by half a badge, and further
 * from its neighbour. Now a badged thing lines up with unbadged ones, as a nav
 * bar's destinations already did. A parent that clips needs half a badge of
 * room round the corner — a top bar's 40dp buttons in a 64dp row have it.
 */
@Composable
fun BadgedBox(
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(contentAlignment = Alignment.Center) { content() }
            Box { badge() }
        },
    ) { measurables, constraints ->
        val body = measurables[0].measure(constraints)
        // Unbounded: the badge is an annotation, and squeezing it to fit the
        // thing it annotates is how a count ends up ellipsised.
        val mark = measurables[1].measure(Constraints())

        val outX = (mark.width * BadgeDefaults.Overhang).roundToInt()
        val outY = (mark.height * BadgeDefaults.Overhang).roundToInt()

        // The content's size and baselines, so it lines up as it would unbadged.
        val lines = buildMap<AlignmentLine, Int> {
            for (line in listOf(FirstBaseline, LastBaseline)) {
                val at = body[line]
                if (at != AlignmentLine.Unspecified) put(line, at)
            }
        }
        layout(body.width, body.height, lines) {
            body.placeRelative(0, 0)
            // Its centre on the corner, overhanging by half of itself.
            mark.placeRelative(body.width + outX - mark.width, -outY)
        }
    }
}

/** What a [Badge] takes by default. */
object BadgeDefaults {
    /**
     * How much of the badge hangs past the corner, as a fraction of itself.
     *
     * `0.5` puts its centre exactly on the corner. Less would inset it into the
     * icon; more would leave it floating beside one.
     */
    const val Overhang: Float = 0.5f
}

@Composable
@ReadOnlyComposable
private fun tagContainerFor(tone: Tone): Color = when (tone) {
    Tone.Neutral -> Theme.colours.surfaceSunken
    Tone.Accent -> Theme.colours.accent.container
    Tone.Success -> Theme.colours.success.container
    Tone.Warning -> Theme.colours.warning.container
    Tone.Danger -> Theme.colours.danger.container
    Tone.Info -> Theme.colours.info.container
}

@Composable
@ReadOnlyComposable
private fun tagBorderFor(tone: Tone): Color = when (tone) {
    Tone.Neutral -> Theme.colours.outline
    Tone.Accent -> Theme.colours.accent.solid
    Tone.Success -> Theme.colours.success.border
    Tone.Warning -> Theme.colours.warning.border
    Tone.Danger -> Theme.colours.danger.border
    Tone.Info -> Theme.colours.info.border
}

@Composable
@ReadOnlyComposable
private fun tagContentFor(tone: Tone): Color = when (tone) {
    Tone.Neutral -> Theme.colours.contentMuted
    Tone.Accent -> Theme.colours.accent.onContainer
    Tone.Success -> Theme.colours.success.onContainer
    Tone.Warning -> Theme.colours.warning.onContainer
    Tone.Danger -> Theme.colours.danger.onContainer
    Tone.Info -> Theme.colours.info.onContainer
}
