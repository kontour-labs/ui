package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Dots
import io.kontour.icons.tabler.outline.Eye
import io.kontour.icons.tabler.outline.Search
import io.kontour.icons.tabler.outline.Selector
import io.kontour.icons.tabler.outline.AlertCircle
import io.kontour.icons.tabler.outline.AlertTriangle
import io.kontour.icons.tabler.outline.ArrowDown
import io.kontour.icons.tabler.outline.ArrowUp
import io.kontour.icons.tabler.outline.Check
import io.kontour.icons.tabler.outline.CircleCheck
import io.kontour.icons.tabler.outline.InfoCircle
import io.kontour.icons.tabler.outline.ChevronDown
import io.kontour.icons.tabler.outline.ChevronLeft
import io.kontour.icons.tabler.outline.ChevronRight
import io.kontour.icons.tabler.outline.ChevronUp
import io.kontour.icons.tabler.outline.Minus
import io.kontour.icons.tabler.outline.Paperclip
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.Star
import io.kontour.icons.tabler.outline.X
import io.kontour.icons.tabler.filled.Star as FilledStar
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone

/**
 * The glyphs the design system draws on its own behalf, read through
 * [Theme.icons].
 *
 * Everything a *caller* puts in a component — a button's leading icon, a menu
 * item's icon, an empty state's illustration — is still passed in as an
 * [ImageVector], so an app can use whatever set it likes. This covers only
 * glyphs that are part of a component's *structure*: the tick that means "this
 * one is selected", the chevron that means "there is more inside", the cross
 * that means "close".
 *
 * Those are not decoration. A submenu with no chevron gives no sign it opens
 * anything, and a dismissible banner with no cross has no visible dismissal.
 * Making the caller supply them means every component ships looking broken until
 * someone remembers to pass one in.
 *
 * ### An app's own set
 *
 * They are [Tabler] outline by default, and every one of them is the app's to
 * change. Pass the ones to replace and the rest stay Tabler:
 *
 * ```kotlin
 * KontourTheme(
 *     icons = SystemIcons(
 *         close = FontAwesome.Solid.Times,
 *         chevronDown = FontAwesome.Solid.ChevronDown,
 *     ),
 * ) { App() }
 * ```
 *
 * Every component reads them from the theme, so that one argument reaches a
 * select's chevron, a toast's close and a menu's submenu arrow alike; a
 * component that draws one also takes it as a parameter, for the one place that
 * wants something else. Until 0.5.0 this was an `object` of Tabler glyphs, and
 * two thirds of them could only be changed by forking the component.
 *
 * ### Why not a data class
 *
 * A data class holds its values, so the default instance would hold every
 * glyph, and every app would carry every one of them whichever components it
 * used. Here a constructor argument is an *override*, `null` unless given, and
 * each property falls back to Tabler only when it is read. A glyph no component
 * of the app reads is never reached, and the Wasm, JS and Native compilers and
 * R8 leave it out.
 *
 * A few marks are still drawn rather than taken from here, because they move:
 * a checkbox's tick draws itself in, a switch's tick turns into its dash, and a
 * password field's slash crosses its eye. A glyph cannot do either.
 */
@Immutable
class SystemIcons(
    check: ImageVector? = null,
    sortAscending: ImageVector? = null,
    sortDescending: ImageVector? = null,
    dash: ImageVector? = null,
    plus: ImageVector? = null,
    star: ImageVector? = null,
    starFilled: ImageVector? = null,
    close: ImageVector? = null,
    attach: ImageVector? = null,
    info: ImageVector? = null,
    success: ImageVector? = null,
    warning: ImageVector? = null,
    danger: ImageVector? = null,
    more: ImageVector? = null,
    chevronDown: ImageVector? = null,
    chevronUp: ImageVector? = null,
    chevronUpDown: ImageVector? = null,
    chevronRight: ImageVector? = null,
    chevronLeft: ImageVector? = null,
    today: ImageVector? = null,
    search: ImageVector? = null,
    reveal: ImageVector? = null,
) {
    private val overrides: List<ImageVector?> = listOf(
        check, sortAscending, sortDescending, dash, plus, star, starFilled, close, attach, info, success,
        warning, danger, more, chevronDown, chevronUp, chevronUpDown, chevronRight, chevronLeft, today,
        search, reveal,
    )

    /** Marks the selected item in a list of choices. */
    val check: ImageVector get() = overrides[0] ?: Tabler.Outline.Check

    /**
     * A table column sorted smallest first. Structural: a sorted header with no
     * arrow does not say which way it is sorted.
     */
    val sortAscending: ImageVector get() = overrides[1] ?: Tabler.Outline.ArrowUp

    /** A table column sorted largest first. */
    val sortDescending: ImageVector get() = overrides[2] ?: Tabler.Outline.ArrowDown

    /** The indeterminate state of a tri-state control, and a stepper's "less". */
    val dash: ImageVector get() = overrides[3] ?: Tabler.Outline.Minus

    /** A stepper's "more". Structural: a stepper with no `+` is two blank buttons. */
    val plus: ImageVector get() = overrides[4] ?: Tabler.Outline.Plus

    /**
     * A `Rating`'s empty mark.
     *
     * Structural in the same sense as the tick: a rating is *made of* these, and
     * one with no glyph is five blank squares. They are still parameters on the
     * component, because a rating of hearts is a reasonable thing to want — this
     * is only what it falls back to.
     */
    val star: ImageVector get() = overrides[5] ?: Tabler.Outline.Star

    /** A `Rating`'s filled mark. */
    val starFilled: ImageVector get() = overrides[6] ?: Tabler.Filled.FilledStar

    /** Dismiss. */
    val close: ImageVector get() = overrides[7] ?: Tabler.Outline.X

    /** Marks a field that is filled from a picker, not by typing: a file field's. */
    val attach: ImageVector get() = overrides[8] ?: Tabler.Outline.Paperclip

    /**
     * The informational tone's mark, for a `Callout` that has no caller to ask,
     * and for the neutral and accent tones, which are notes rather than states.
     *
     * A `Banner` takes its icon from the call site, because the thing it is a
     * message *about* is the caller's to name. A callout is an aside inside
     * prose, and on a documentation site every markdown blockquote becomes one —
     * so there is nobody there to choose, and a severity with no glyph is a tint
     * and nothing else. The tone is *made of* the colour and the mark together,
     * and colour alone is the one signal a reader may not have.
     */
    val info: ImageVector get() = overrides[9] ?: Tabler.Outline.InfoCircle

    /** The success tone's mark. See [info]. */
    val success: ImageVector get() = overrides[10] ?: Tabler.Outline.CircleCheck

    /** The warning tone's mark. See [info]. */
    val warning: ImageVector get() = overrides[11] ?: Tabler.Outline.AlertTriangle

    /** The danger tone's mark. See [info]. */
    val danger: ImageVector get() = overrides[12] ?: Tabler.Outline.AlertCircle

    /**
     * Whatever did not fit.
     *
     * Horizontal, not vertical: this marks an overflow at the end of a *row* —
     * a toolbar, a bar of actions — and the dots run the way the things they
     * stand for would have.
     */
    val more: ImageVector get() = overrides[13] ?: Tabler.Outline.Dots

    /** Expands downward — accordions, selects, dropdown triggers. */
    val chevronDown: ImageVector get() = overrides[14] ?: Tabler.Outline.ChevronDown

    /** Collapses upward. */
    val chevronUp: ImageVector get() = overrides[15] ?: Tabler.Outline.ChevronUp

    /**
     * Up and down at once — a field whose value turns on a wheel either way,
     * [io.kontour.ui.components.text.WheelSelect]'s, rather than one that drops
     * a list below it.
     */
    val chevronUpDown: ImageVector get() = overrides[16] ?: Tabler.Outline.Selector

    /** Physically rightward, regardless of layout direction. */
    val chevronRight: ImageVector get() = overrides[17] ?: Tabler.Outline.ChevronRight

    /** Physically leftward, regardless of layout direction. */
    val chevronLeft: ImageVector get() = overrides[18] ?: Tabler.Outline.ChevronLeft

    /**
     * The way back to today: a small calendar page with the day marked, on a
     * date picker's and an event calendar's header.
     *
     * Drawn by the library rather than taken from Tabler, whose calendars mark
     * no day, so that it says "today" and not "a calendar".
     */
    val today: ImageVector get() = overrides[19] ?: TodayGlyph

    /** What a search field leads with. */
    val search: ImageVector get() = overrides[20] ?: Tabler.Outline.Search

    /**
     * The eye on a password or secret field. The same eye with a slash drawn
     * across it says the text is hidden, so one glyph serves both states.
     */
    val reveal: ImageVector get() = overrides[21] ?: Tabler.Outline.Eye

    /**
     * "There is more this way" — a submenu, a next page, a disclosure.
     *
     * Follows the reading direction rather than the compass, so it is
     * [chevronLeft] in a right-to-left locale. Resolved here rather than with
     * `autoMirror` on the vector, because Tabler's chevrons are correctly *not*
     * auto-mirroring: an icon named "chevron right" should point right wherever
     * it is drawn. It is this *use* of it that is directional, not the glyph.
     */
    val chevronForward: ImageVector
        @Composable @ReadOnlyComposable
        get() = if (LocalLayoutDirection.current == LayoutDirection.Rtl) chevronLeft else chevronRight

    /** "Back the way you came." Follows the reading direction — see [chevronForward]. */
    val chevronBack: ImageVector
        @Composable @ReadOnlyComposable
        get() = if (LocalLayoutDirection.current == LayoutDirection.Rtl) chevronRight else chevronLeft

    /** The mark for [tone]: its own glyph, and [info] for the two tones that are notes. */
    fun tone(tone: Tone): ImageVector = when (tone) {
        Tone.Neutral -> info
        Tone.Info -> info
        Tone.Success -> success
        Tone.Warning -> warning
        Tone.Danger -> danger
        // The same glyph as `Info`, deliberately. The two differ in emphasis
        // rather than in kind — an accent aside is a note in the brand's colour
        // and an info one is a note in the informational colour — and inventing a
        // second mark to keep them apart would be signalling a difference the
        // component does not have. This is the tone a markdown blockquote
        // becomes, and "here is a note" is what it means.
        Tone.Accent -> info
    }

    /**
     * This set with some glyphs replaced: `Theme.icons.copy(close = …)` for a
     * part of an app, under `ProvideTokens`. An argument left out keeps what
     * this set has, whether that is an override or the default.
     */
    fun copy(
        check: ImageVector? = overrides[0],
        sortAscending: ImageVector? = overrides[1],
        sortDescending: ImageVector? = overrides[2],
        dash: ImageVector? = overrides[3],
        plus: ImageVector? = overrides[4],
        star: ImageVector? = overrides[5],
        starFilled: ImageVector? = overrides[6],
        close: ImageVector? = overrides[7],
        attach: ImageVector? = overrides[8],
        info: ImageVector? = overrides[9],
        success: ImageVector? = overrides[10],
        warning: ImageVector? = overrides[11],
        danger: ImageVector? = overrides[12],
        more: ImageVector? = overrides[13],
        chevronDown: ImageVector? = overrides[14],
        chevronUp: ImageVector? = overrides[15],
        chevronUpDown: ImageVector? = overrides[16],
        chevronRight: ImageVector? = overrides[17],
        chevronLeft: ImageVector? = overrides[18],
        today: ImageVector? = overrides[19],
        search: ImageVector? = overrides[20],
        reveal: ImageVector? = overrides[21],
    ): SystemIcons = SystemIcons(
        check, sortAscending, sortDescending, dash, plus, star, starFilled, close, attach, info, success,
        warning, danger, more, chevronDown, chevronUp, chevronUpDown, chevronRight, chevronLeft, today,
        search, reveal,
    )

    /** Equal when they replace the same glyphs with the same ones. */
    override fun equals(other: Any?): Boolean = other is SystemIcons && other.overrides == overrides

    override fun hashCode(): Int = overrides.hashCode()

    override fun toString(): String = "SystemIcons(${overrides.count { it != null }} replaced)"

    companion object {
        /**
         * Tabler's, every one: what [Theme.icons] is unless `KontourTheme` is
         * given a set. For code outside a composition, such as a list built at
         * the top of a file, that needs one of the library's own glyphs.
         */
        val Default: SystemIcons = SystemIcons()
    }
}

/** A calendar page, its two rings and header rule, and a marked day. 24 by 24, stroked. */
private val TodayGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "Today",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The page.
            moveTo(6f, 5f)
            horizontalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(7f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
            // The rings, and the rule under the month.
            moveTo(16f, 3f)
            verticalLineTo(7f)
            moveTo(8f, 3f)
            verticalLineTo(7f)
            moveTo(4f, 11f)
            horizontalLineTo(20f)
        }
        // Today, marked.
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 14.5f)
            horizontalLineTo(11f)
            verticalLineTo(17.5f)
            horizontalLineTo(8f)
            close()
        }
    }.build()
}
