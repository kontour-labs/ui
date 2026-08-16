package io.kontour.ui.components.display

import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import io.kontour.ui.foundation.ContentScope

/**
 * The regions of a [Banner].
 *
 * ```kotlin
 * Banner(tone = BannerTone.Warning) {
 *     +"Services are running up to 12 minutes late."
 *     title { +"Delays on the Armadale line" }
 *     leading { +Tabler.Outline.AlertTriangle }
 *     action { Button(onClick = ::retry) { +"Retry" } }
 * }
 * ```
 *
 * `+` fills the **message**, not the title, because the message is the part a
 * banner cannot do without. That is the rule everywhere in the library: the bare
 * `+` takes whatever the component requires, so the one-region call is always one
 * line. For a banner that is the message; for an [EmptyState] it is the title.
 */
@LayoutScopeMarker
@Stable
class BannerScope internal constructor() {

    internal var message: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var title: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var leading: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var action: (@Composable ContentScope.() -> Unit)? = null
        private set

    /** What happened. The one region a banner must have. */
    operator fun String.unaryPlus() {
        val text = this
        message = { +text }
    }

    /** As above, carrying its own spans. */
    operator fun AnnotatedString.unaryPlus() {
        val text = this
        message = { +text }
    }

    /** The message, when it is more than text. */
    fun message(content: @Composable ContentScope.() -> Unit) {
        message = content
    }

    /** A heading above the message, for a banner worth scanning past. */
    fun title(content: @Composable ContentScope.() -> Unit) {
        title = content
    }

    /** The glyph at the leading edge. Decorative — the tone and text carry it. */
    fun leading(content: @Composable ContentScope.() -> Unit) {
        leading = content
    }

    /** What the user can do about it. Sits under the message. */
    fun action(content: @Composable ContentScope.() -> Unit) {
        action = content
    }
}

/**
 * The regions of an [EmptyState] or [ErrorState].
 *
 * ```kotlin
 * EmptyState {
 *     +"No favourites yet"
 *     supporting { +"Star a stop and it will appear here." }
 *     leading { +Tabler.Outline.Star }
 *     action { Button(onClick = ::browse) { +"Browse routes" } }
 * }
 * ```
 *
 * `+` fills the **title** here — the required region, and the one a state screen
 * always has.
 */
@LayoutScopeMarker
@Stable
class StateScope internal constructor() {

    internal var title: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var supporting: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var leading: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var action: (@Composable ContentScope.() -> Unit)? = null
        private set

    /** What is going on, in a few words. */
    operator fun String.unaryPlus() {
        val text = this
        title = { +text }
    }

    /** As above, carrying its own spans. */
    operator fun AnnotatedString.unaryPlus() {
        val text = this
        title = { +text }
    }

    /** The title, when it is more than text. */
    fun title(content: @Composable ContentScope.() -> Unit) {
        title = content
    }

    /**
     * How to get *out* of the state, rather than a restatement of the title.
     *
     * "No favourites yet" followed by "You have no favourites" tells the user
     * nothing they cannot see; followed by "Star a stop and it will appear here"
     * it becomes useful.
     */
    fun supporting(content: @Composable ContentScope.() -> Unit) {
        supporting = content
    }

    /** The illustration above it all. */
    fun leading(content: @Composable ContentScope.() -> Unit) {
        leading = content
    }

    /** The way out, as a control. */
    fun action(content: @Composable ContentScope.() -> Unit) {
        action = content
    }
}

internal fun bannerSlots(content: BannerScope.() -> Unit): BannerScope =
    BannerScope().apply(content)

internal fun stateSlots(content: StateScope.() -> Unit): StateScope =
    StateScope().apply(content)
