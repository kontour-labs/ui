package io.kontour.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColorFor
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.contentScope
import io.kontour.ui.theme.Theme

/** The meaning a [Tag] carries. Maps to the scheme's status tones. */
enum class TagTone { Neutral, Accent, Success, Warning, Danger, Info }

/**
 * A small, non-interactive label — a status, a category, a route number.
 *
 * ```
 * Tag(tone = TagTone.Success) { +"Live" }
 * Tag(color = routeColor) { +"960" }          // colour straight out of a GTFS feed
 * ```
 *
 * Not a [io.kontour.ui.components.selection.Chip]. A chip is something the user
 * can act on; a tag is something the interface is telling them. If it can be
 * tapped, it is a chip.
 *
 * ### Arbitrary colours
 *
 * Transit feeds supply their own route colours, and they are not drawn from any
 * palette — a route can be pale yellow or near-black. Passing [color] resolves
 * the label with [contentColorFor], which picks whichever of light or dark reads
 * better on it. That is the whole reason this component exists rather than
 * callers styling a `Surface` themselves: the one thing they would get wrong is
 * the case where the feed hands them a colour nobody designed for.
 *
 * @param color An explicit background. Overrides [tone]. The label colour is
 *   derived, not guessed.
 * @param contentDescription What a screen reader announces. Defaults to the
 *   label, which is usually right; override for abbreviations a reader would
 *   mangle — "960" is fine, "PM Pk" is not.
 */
@Composable
fun Tag(
    modifier: Modifier = Modifier,
    tone: TagTone = TagTone.Neutral,
    color: Color = Color.Unspecified,
    shape: Shape = Theme.shapes.extraSmall,
    /**
     * Overrides what the tag announces.
     *
     * Rarely needed: the content is merged, so `+"Delayed"` already announces as
     * "Delayed". This is for a tag whose text is an abbreviation the spoken form
     * should expand — "PU" reading as "Perth Underground".
     */
    contentDescription: String? = null,
    content: @Composable RowContentScope.() -> Unit
) {
    val container = if (color != Color.Unspecified) color else tagContainerFor(tone)
    val contentColor = if (color != Color.Unspecified) {
        contentColorFor(
            background = color,
            light = Theme.colors.onPrimary.takeIf { !Theme.colors.isDark } ?: Theme.colors.content,
            dark = Theme.colors.content.takeIf { !Theme.colors.isDark } ?: Theme.colors.onPrimary,
        )
    } else {
        tagContentFor(tone)
    }

    Row(
        modifier = modifier
            // Merged rather than described: a tag used to build its own
            // `contentDescription` from its `label`, and with the label in a slot
            // there is no string to build one from. Merging is the better answer
            // anyway — whatever the content is, that is what it announces.
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .clip(shape)
            .background(container, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(
                Theme.typography.labelSmall.copy()
            ) {
                contentScope(iconSize = TagIconSize, content = content)
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
    color: Color = Theme.colors.danger.solid,
    contentColor: Color = Theme.colors.danger.onSolid,
    contentDescription: String? = null,
) {
    val label = when {
        count == null -> null
        count > max -> "$max+"
        else -> count.toString()
    }

    val announcement = contentDescription ?: when {
        count == null -> "New"
        count > max -> "More than $max"
        else -> "$count"
    }

    if (label == null) {
        Box(
            modifier
                .semantics { this.contentDescription = announcement }
                .defaultMinSize(minWidth = 8.dp, minHeight = 8.dp)
                .clip(Theme.shapes.pill)
                .background(color)
        )
    } else {
        Box(
            modifier = modifier
                .semantics { this.contentDescription = announcement }
                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                .clip(Theme.shapes.pill)
                .background(color)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = Theme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * Positions a [Badge] over the top-trailing corner of [content].
 *
 * The badge overhangs rather than being inset, so it does not eat into the icon
 * it is annotating. Give the parent a little padding if it might be clipped.
 */
@Composable
fun BadgedBox(
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        content()
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(start = 12.dp, bottom = 12.dp),
        ) {
            badge()
        }
    }
}

@Composable
@ReadOnlyComposable
private fun tagContainerFor(tone: TagTone): Color = when (tone) {
    TagTone.Neutral -> Theme.colors.surfaceSunken
    TagTone.Accent -> Theme.colors.accentContainer
    TagTone.Success -> Theme.colors.success.container
    TagTone.Warning -> Theme.colors.warning.container
    TagTone.Danger -> Theme.colors.danger.container
    TagTone.Info -> Theme.colors.info.container
}

@Composable
@ReadOnlyComposable
private fun tagContentFor(tone: TagTone): Color = when (tone) {
    TagTone.Neutral -> Theme.colors.contentMuted
    TagTone.Accent -> Theme.colors.onAccentContainer
    TagTone.Success -> Theme.colors.success.onContainer
    TagTone.Warning -> Theme.colors.warning.onContainer
    TagTone.Danger -> Theme.colors.danger.onContainer
    TagTone.Info -> Theme.colors.info.onContainer
}
