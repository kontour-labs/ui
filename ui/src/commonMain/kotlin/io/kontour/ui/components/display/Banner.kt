package io.kontour.ui.components.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.StatusColours
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

/** How serious a [Banner] is. Ports the four severities from `home`'s `StatusBanner`. */
enum class BannerTone { Info, Success, Warning, Danger, Accent }

/**
 * An inline message about the state of something.
 *
 * ```
 * Banner(tone = BannerTone.Warning, onDismissRequest = viewModel::dismissAlert) {
 *     leading { +Tabler.Outline.AlertTriangle }
 *     title { +"Delays on the Armadale line" }
 *     supporting { +"Services are running up to 12 minutes late." }
 *     action { Button(onClick = ::showDetail) { +"View details" } }
 * }
 * ```
 *
 * For something the user should know about the screen they are on. For something
 * that happened *because of an action they took*, use a
 * [io.kontour.ui.overlay.Toast] instead — a banner that appears in
 * response to a tap is easy to miss, because the user is looking at their finger.
 *
 * A banner is a live region, so it is read out when it appears.
 * [BannerTone.Danger] announces assertively and everything else politely —
 * interrupting for a routine notice trains people to ignore the interruption.
 *
 * A `leading` icon is strongly recommended, and a distinct one per tone: a tone
 * read purely as hue fails WCAG 1.4.1 and is invisible to the most common form
 * of colour blindness. `action` renders below the message — "Retry", "View
 * details".
 */
@Composable
fun Banner(
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    onDismissRequest: (() -> Unit)? = null,
    dismissIcon: ImageVector? = null,
    dismissLabel: String = Theme.strings.dismiss,
    content: BannerScope.() -> Unit,
) {
    val slots = bannerSlots(content)
    val colours = bannerColoursFor(tone)
    val shape = Theme.shapes.container

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = if (tone == BannerTone.Danger) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            }
            .clip(shape)
            .background(colours.container, shape)
            .border(BorderStroke(Theme.sizing.borderWidth, colours.border), shape)
            .padding(Theme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        // Everything in the row, the message included.
        //
        // This used to be the default `Alignment.Top`, with the icon and the
        // dismiss each opting out of it — and the comment on the dismiss below
        // defended that: "a body that centred itself would drift as the text
        // grew". **It cannot.** Once the body is the tallest child in the row,
        // centring it and topping it are the same placement, so there is nothing
        // there to drift.
        //
        // What the top alignment did instead was hand the row's height to
        // whichever *other* child was tallest, and on a one-line banner that is
        // the dismiss button. `minimumTouchTarget` grows a measured size rather
        // than a hit rect, and the platform minimum is 44dp on the web against
        // 24dp on a desktop — so the same banner put its message 14dp above
        // centre on a phone and 6dp above centre on a laptop. Reported as "the
        // text in banner is not centred vertically on mobile web", and the
        // platform in that sentence is why it lasted: every golden in the
        // repository is taken at the JVM's 24dp, on a specimen whose text wraps
        // to three lines and therefore sets the height itself.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColour provides colours.onContainer) {
            slots.leading?.let { leading ->
                // Centred in the banner, like the dismiss on the other side.
                //
                // It used to sit at the top of the row, nudged down by a bare
                // `1.dp` to land on the title's line. Two things were wrong with
                // that. The nudge is a number with no derivation, so it is right
                // for one type size and wrong for every other; and on a banner
                // that is a single line — which most of them are — "level with
                // the first line" and "centred" are meant to be the same place
                // and the fudge made them differ.
                //
                // A tone icon belongs to the banner rather than to any line of
                // it: it says *this is a warning*, which is a fact about the
                // whole message. So it is centred against the whole message, and
                // the two things flanking the text now agree with each other.
                Box(Modifier.align(Alignment.CenterVertically)) {
                    ContentSlot(iconSize = Theme.sizing.iconMedium, content = leading)
                }
            }

            BannerBody(
                modifier = Modifier.weight(1f),
                action = slots.action?.let { action -> { ContentSlot(content = action) } },
            ) {
                slots.title?.let { title ->
                    ProvideTextStyle(Theme.typography.titleSmall) {
                        ContentSlot(content = title)
                    }
                }
                slots.message?.let { message ->
                    ProvideTextStyle(Theme.typography.bodySmall) {
                        ContentSlot(content = message)
                    }
                }
            }

            if (onDismissRequest != null && dismissIcon != null) {
                IconButton(
                    icon = dismissIcon,
                    contentDescription = dismissLabel,
                    onClick = onDismissRequest,
                    // Centred in the banner, not sitting on the title's line.
                    //
                    // The dismiss belongs to the banner rather than to any line
                    // of it: inheriting a top alignment put it up in the corner
                    // of a three-line banner, level with the title and a long way
                    // from the middle of the box.
                    //
                    // Redundant now that the row centres everything, and kept
                    // because it says what this button wants rather than what the
                    // row happens to do. Same reasoning as the leading icon.
                    modifier = Modifier.align(Alignment.CenterVertically),
                    size = ButtonSize.XSmall,
                )
            }
        }
    }
}

/**
 * A [Banner] that animates itself in and out.
 *
 * Collapsing its height rather than just fading means the content below settles
 * into place instead of jumping, which is what makes a dismissible banner feel
 * like part of the page rather than an overlay that vanished.
 */
@Composable
fun AnimatedBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    onDismissRequest: (() -> Unit)? = null,
    dismissIcon: ImageVector? = null,
    content: BannerScope.() -> Unit,
) {
    val motion = Theme.motion
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.tweenFast()) + expandVertically(motion.tweenDefault()),
        exit = fadeOut(motion.tweenFast()) + shrinkVertically(motion.tweenDefault()),
    ) {
        Banner(
            modifier = modifier,
            tone = tone,
            onDismissRequest = onDismissRequest,
            dismissIcon = dismissIcon,
            content = content,
        )
    }
}

/**
 * A quoted aside, tinted and marked the way a [Banner] is.
 *
 * For a note or a caveat inside a body of text. It shares a `Banner`'s ground,
 * border, icon and type, and that is the point: two things that say "pay
 * attention to this" should not look like two different libraries. What it does
 * **not** share is everything that makes a banner a message about the *screen* —
 * no dismiss, no action, no live region. A callout is part of the prose.
 *
 * ### The rule is gone, after three attempts at it
 *
 * It was an accent band down the leading edge, and every version of it lost to
 * the container's own corner. Flush inside the clip, it tapered away at both
 * ends; stroked around the whole outline, a 22dp corner carried it 25dp along
 * the top and bottom and it read as a "C" bracketing the text; indented 8dp to
 * dodge both, it stopped reading as an edge at all and looked, in the report,
 * "like a tally mark left in the box". Three rounds, one stripe, and the honest
 * reading is that a 3dp mark is not enough signal to be worth that much trouble
 * when the component beside it already solved the same problem with a tint and a
 * glyph.
 *
 * @param tone Which of the five, and it decides the ground, the border, the text
 *   colour and the icon together. [BannerTone.Accent] by default, which is the
 *   neutral aside a blockquote becomes.
 * @param icon The mark. Defaults to the tone's own — unlike [Banner], which makes
 *   the caller choose, because a callout often has no caller: a documentation
 *   site turns every markdown blockquote into one. Null for a callout that is
 *   tint alone.
 */
@Composable
fun Callout(
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Accent,
    icon: ImageVector? = calloutIcon(tone),
    content: @Composable () -> Unit,
) {
    val colours = bannerColoursFor(tone)
    val shape = Theme.shapes.container

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colours.container, shape)
            .border(BorderStroke(Theme.sizing.borderWidth, colours.border), shape)
            .padding(Theme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        // As in `Banner`, and for the reason written there: the icon is about the
        // whole aside rather than about its first line.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColour provides colours.onContainer) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    // The tone is in the words, or it is not information. A
                    // reader who cannot see the tint is not helped by hearing
                    // "warning" read out before a sentence that already says so.
                    contentDescription = null,
                    modifier = Modifier.size(Theme.sizing.iconMedium),
                )
            }
            ProvideTextStyle(Theme.typography.bodySmall) {
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

/**
 * The glyph a tone falls back to.
 *
 * Not a `when` on a colour: the tone is the vocabulary and the icon is one of
 * the things it decides, alongside the ground and the border in
 * [bannerColoursFor].
 */
@Composable
private fun calloutIcon(tone: BannerTone): ImageVector = when (tone) {
    BannerTone.Info -> SystemIcons.Info
    BannerTone.Success -> SystemIcons.Success
    BannerTone.Warning -> SystemIcons.Warning
    BannerTone.Danger -> SystemIcons.Danger
    // The same glyph as `Info`, deliberately. The two differ in emphasis rather
    // than in kind — an accent aside is a note in the brand's colour and an info
    // one is a note in the informational colour — and inventing a second mark to
    // keep them apart would be signalling a difference the component does not
    // have. This is the tone a markdown blockquote becomes, and "here is a note"
    // is what it means.
    BannerTone.Accent -> SystemIcons.Info
}


@Composable
private fun bannerColoursFor(tone: BannerTone): StatusColours = when (tone) {
    BannerTone.Info -> Theme.colours.info
    BannerTone.Success -> Theme.colours.success
    BannerTone.Warning -> Theme.colours.warning
    BannerTone.Danger -> Theme.colours.danger
    // Reachable at all only because `accent` is a `StatusColours` now. As four
    // loose fields it had no `border`, so a banner could not have been built
    // out of it without inventing one here.
    BannerTone.Accent -> Theme.colours.accent
}

/**
 * The text of a banner, with its action beside it or under it.
 *
 * Beside when the action fits without squeezing the text below
 * [BannerDefaults.MinTextShare] of the width, underneath when it does not. The
 * action was always underneath, which wastes a line on "Delays on the Armadale
 * line" / "Retry" and reads as a second paragraph rather than as the thing to do
 * about the first.
 *
 * `SubcomposeLayout` because the decision needs the action's *measured* width
 * against this banner's *actual* width, and neither is known at composition. A
 * `FlowRow` gets close and gets one case wrong: it wraps on overflow, so an
 * action that fits in the remaining 20% stays on the line and leaves the message
 * as a column of single words.
 */
@Composable
private fun BannerBody(
    modifier: Modifier,
    action: (@Composable () -> Unit)?,
    text: @Composable ColumnScope.() -> Unit,
) {
    val spacing = Theme.spacing
    if (action == null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp), content = text)
        return
    }

    SubcomposeLayout(modifier) { constraints ->
        val available = constraints.maxWidth
        val gap = spacing.sm.roundToPx()
        val stack = spacing.xs.roundToPx()

        val actionPlaceable = subcompose(BannerSlot.Action) {
            Box { action() }
        }.first().measure(Constraints())

        val remaining = available - actionPlaceable.width - gap
        val beside = available != Constraints.Infinity &&
            remaining >= (available * BannerDefaults.MinTextShare).roundToInt()

        val textWidth = if (beside) remaining else available
        val textPlaceable = subcompose(BannerSlot.Text) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = text)
        }.first().measure(constraints.copy(minWidth = 0, maxWidth = textWidth))

        if (beside) {
            val height = maxOf(textPlaceable.height, actionPlaceable.height)
            layout(available, height) {
                textPlaceable.placeRelative(0, (height - textPlaceable.height) / 2)
                actionPlaceable.placeRelative(
                    available - actionPlaceable.width,
                    (height - actionPlaceable.height) / 2,
                )
            }
        } else {
            layout(available, textPlaceable.height + stack + actionPlaceable.height) {
                textPlaceable.placeRelative(0, 0)
                actionPlaceable.placeRelative(0, textPlaceable.height + stack)
            }
        }
    }
}

private enum class BannerSlot { Text, Action }

object BannerDefaults {
    /**
     * How much of the width the text keeps before the action is sent below it.
     *
     * At 0.6 a "Retry" sits beside a one-line message and a paragraph with a
     * "Replan my trip" beside it does not — which is the line between an action
     * that annotates the text and one that competes with it for the row.
     */
    const val MinTextShare: Float = 0.6f
}
