package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.theme.Theme

/**
 * One number, said loudly, with the word for what it is.
 *
 * ```kotlin
 * Stat {
 *     value { +"4 min" }
 *     +"Next departure"
 *     supporting { +"Platform 2" }
 *     trend(StatTrend.Positive, "2 min earlier than usual")
 * }
 * ```
 *
 * The label goes **under** the value, not above it. A dashboard is scanned by
 * its numbers — the reader finds the big thing first and then asks what it is,
 * and a label on top makes them read every caption to find the figure they
 * came for.
 *
 * ### It announces as one thing
 *
 * A screen reader reading four nodes — "4 min", "Next departure", "Platform 2",
 * "2 min earlier than usual" — makes the reader assemble the sentence. So the
 * whole block merges into one `contentDescription`, in the order a person would
 * say it: the label, then the value, then the rest.
 *
 * **Reach for a [KeyValueList] instead** when there are several figures and none
 * of them is the headline. A screen with six `Stat`s has no headline, which is
 * the same as having none.
 */
@Composable
fun Stat(
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
    content: StatScope.() -> Unit,
) {
    val slots = statSlots(content)
    val spoken = slots.spoken()

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            if (spoken != null) contentDescription = spoken
        },
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
    ) {
        slots.value?.let { value ->
            ProvideTextStyle(Theme.typography.displaySmall) {
                ContentSlot(maxLines = 1, content = value)
            }
        }

        slots.label?.let { label ->
            ProvideContentColour(Theme.colours.contentMuted) {
                ProvideTextStyle(Theme.typography.labelMedium) {
                    ContentSlot(maxLines = 2, content = label)
                }
            }
        }

        slots.supporting?.let { supporting ->
            ProvideContentColour(Theme.colours.contentSubtle) {
                ProvideTextStyle(Theme.typography.bodySmall) {
                    ContentSlot(maxLines = 2, content = supporting)
                }
            }
        }

        slots.trend?.let { trend ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProvideContentColour(slots.trendDirection.colour()) {
                    ProvideTextStyle(Theme.typography.labelSmall) {
                        ContentSlot(iconSize = Theme.sizing.iconSmall, content = trend)
                    }
                }
            }
        }
    }
}

/**
 * Which way a [Stat]'s trend points, and therefore what colour it is.
 *
 * Direction and sentiment are separate on purpose. A departure two minutes
 * *earlier* is good news and points down; a fare two dollars higher is bad news
 * and points up. Nothing here can tell which, so the caller says.
 */
enum class StatTrend {
    /** Good news, whichever way the arrow points. */
    Positive,

    /** Bad news. */
    Negative,

    /** Movement worth showing that is neither. */
    Neutral,
}

@Composable
private fun StatTrend.colour() = when (this) {
    StatTrend.Positive -> Theme.colours.success.solid
    StatTrend.Negative -> Theme.colours.danger.solid
    StatTrend.Neutral -> Theme.colours.contentMuted
}

/**
 * The regions of a [Stat]. A bare `+` fills the label, as everywhere else.
 *
 * Collects rather than emits, because the merged announcement is assembled in
 * speaking order once every region has been declared — and so the builder is
 * plain Kotlin, for the reason argued on
 * [io.kontour.ui.components.list.ListGroupScope].
 */
@LayoutScopeMarker
@Stable
class StatScope internal constructor() {
    internal var value: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var label: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var supporting: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var trend: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var trendDirection: StatTrend = StatTrend.Neutral
        private set

    /**
     * Plain text for each region, collected so the merged announcement can be
     * built in speaking order. A slot given a composable contributes nothing
     * here, which is why [announcement] exists.
     */
    private val spokenValue = StringBuilder()
    private val spokenLabel = StringBuilder()
    private val spokenRest = StringBuilder()
    private var override: String? = null

    /** What this is the number *of*. */
    operator fun String.unaryPlus() {
        val text = this
        spokenLabel.append(text)
        label = { +text }
    }

    /** The number itself. */
    fun value(content: @Composable ContentScope.() -> Unit) {
        value = content
    }

    /** The number, when it is plain text — the common case. */
    fun value(text: String) {
        spokenValue.append(text)
        value = { +text }
    }

    /** A second line under the label. */
    fun supporting(content: @Composable ContentScope.() -> Unit) {
        supporting = content
    }

    fun supporting(text: String) {
        if (spokenRest.isNotEmpty()) spokenRest.append(". ")
        spokenRest.append(text)
        supporting = { +text }
    }

    /** Which way it has moved, and by how much. */
    fun trend(direction: StatTrend, content: @Composable ContentScope.() -> Unit) {
        trendDirection = direction
        trend = content
    }

    fun trend(direction: StatTrend, text: String) {
        trendDirection = direction
        if (spokenRest.isNotEmpty()) spokenRest.append(". ")
        spokenRest.append(text)
        trend = { +text }
    }

    /**
     * Replaces the whole spoken form.
     *
     * For a value a reader would mangle — "4 min" is fine, "PT4M" is not, and a
     * figure written "1.2k" should be said as "one thousand two hundred".
     */
    fun announcement(spoken: String) {
        override = spoken
    }

    internal fun spoken(): String? {
        override?.let { return it }
        val parts = listOfNotNull(
            spokenLabel.toString().takeIf { it.isNotBlank() },
            spokenValue.toString().takeIf { it.isNotBlank() },
            spokenRest.toString().takeIf { it.isNotBlank() },
        )
        return parts.joinToString(". ").takeIf { it.isNotBlank() }
    }
}

internal fun statSlots(content: StatScope.() -> Unit): StatScope =
    StatScope().apply(content)
