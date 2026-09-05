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
import androidx.compose.foundation.layout.IntrinsicSize
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
                    // The row itself still aligns to the top, because the
                    // *message* does: a title and its supporting line stack from
                    // the top of the banner, and a body that centred itself
                    // would drift as the text grew. The dismiss belongs to the
                    // banner rather than to any line of it, and inheriting the
                    // row's alignment put it up in the corner of a three-line
                    // banner, level with the title and a long way from the
                    // middle of the box. Same reasoning as the leading icon
                    // above, and now the same answer.
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
 * A quoted aside, in the shape the marketing site's markdown already uses.
 *
 * An accent rule down the leading edge and a tinted ground — for a note or a
 * caveat inside a body of text. Unlike a [Banner] it carries no severity and is
 * not a live region: it is part of the prose, not a message about the screen.
 */
@Composable
fun Callout(
    modifier: Modifier = Modifier,
    accent: Color = Theme.colours.accent.solid,
    container: Color = Theme.colours.accent.container,
    content: @Composable () -> Unit,
) {
    val shape = Theme.shapes.container

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Intrinsic height so the accent rule can match the tallest child;
            // a Row otherwise gives fillMaxHeight nothing to fill.
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(container, shape)
            // A rule down the leading edge, with a corner of its own.
            //
            // Two earlier attempts, and the fault they share is worth stating
            // because it is the reason this looks like more code than a 3dp
            // `Box`. A plain bar inside a rounded container is eaten by the clip
            // where the curve turns, so a rule meant to run the full height
            // tapers away at both ends. Stroking the *container's* path instead
            // fixes that by construction — the band is the outline, so it curves
            // with it — but it curves with it all the way, and a 22dp corner
            // carries the rule a good 25dp along the top and bottom edges. The
            // result reads as a "C" bracketing the text rather than a rule
            // beside it.
            //
            // So: the rule is its own rounded rect at its own much smaller
            // radius, and it is *indented* from the leading edge rather than
            // flush against it.
            //
            // The indent is what makes it a rule rather than a stub, and that is
            // arithmetic rather than taste. A bar flush at x=0 is inside a 22dp
            // corner only where the container's edge has finished curving — 22dp
            // down from the top and 22dp up from the bottom — so on a two-line
            // callout there is almost nothing left to draw. Set it 8dp in and
            // the edge clears it 5dp from the top instead, so the rule can run
            // the height of the text it is marking. Which is also the right
            // thing for it to measure: `CalloutRuleInset` matches the content's
            // own padding, so the rule spans the words rather than the box.
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path().apply { addOutline(outline) }
                val width = CalloutRuleWidth.toPx()

                val indent = CalloutRuleIndent.toPx()
                val inset = CalloutRuleInset.toPx()
                val height = (size.height - inset * 2f).coerceAtLeast(0f)

                // Draw coordinates do not flip, but the spacer that reserves this
                // strip is a `Row` child and does. Mirror by hand or the rule is
                // painted under the text in RTL.
                val left = if (layoutDirection == LayoutDirection.Rtl) {
                    size.width - indent - width
                } else {
                    indent
                }

                onDrawWithContent {
                    drawContent()
                    clipPath(path) {
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(left, inset),
                            size = Size(width, height),
                            cornerRadius = CornerRadius(width / 2f),
                        )
                    }
                }
            },
    ) {
        Box(Modifier.width(CalloutRuleIndent + CalloutRuleWidth))
        Box(Modifier.padding(Theme.spacing.sm)) {
            CompositionLocalProvider(
                LocalContentColour provides Theme.colours.accent.onContainer,
            ) {
                content()
            }
        }
    }
}

/** How wide the accent rule down a [Callout]'s leading edge is. */
private val CalloutRuleWidth = 3.dp

/**
 * How far in from the leading edge the rule sits.
 *
 * Not decoration: a bar flush against the edge is inside the container's own
 * 22dp corner only over the straight part of that edge, which on a short callout
 * is almost none of it. See the drawing comment in [Callout].
 */
private val CalloutRuleIndent = 8.dp

/** The rule spans the padded content rather than the whole box. */
private val CalloutRuleInset = 12.dp

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
