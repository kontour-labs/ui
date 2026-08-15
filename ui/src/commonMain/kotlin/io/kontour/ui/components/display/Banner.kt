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
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.StatusColors
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

/** How serious a [Banner] is. Ports the four severities from `home`'s `StatusBanner`. */
enum class BannerTone { Info, Success, Warning, Danger, Accent }

/**
 * An inline message about the state of something.
 *
 * ```
 * Banner(
 *     tone = BannerTone.Warning,
 *     title = "Delays on the Armadale line",
 *     message = "Services are running up to 12 minutes late.",
 *     icon = Tabler.Outline.AlertTriangle,
 *     onDismissRequest = viewModel::dismissAlert,
 * )
 * ```
 *
 * For something the user should know about the screen they are on. For something
 * that happened *because of an action they took*, use a
 * [io.kontour.ui.components.overlay.Toast] instead — a banner that appears in
 * response to a tap is easy to miss, because the user is looking at their finger.
 *
 * ### Announcing
 *
 * A banner is a live region, so it is read out when it appears. [BannerTone.Danger]
 * announces assertively — interrupting whatever the screen reader is saying —
 * and everything else politely. That distinction matters: interrupting for a
 * routine notice trains people to ignore the interruption.
 *
 * Colour is never the only signal. Each tone has its own icon slot and the text
 * says what is wrong, because a tone read purely as hue fails WCAG 1.4.1 and is
 * invisible to the most common form of colour blindness.
 *
 * @param icon Strongly recommended. Pass a distinct icon per tone.
 * @param action A button rendered below the message — "Retry", "View details".
 */
@Composable
fun Banner(
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    onDismissRequest: (() -> Unit)? = null,
    dismissIcon: ImageVector? = null,
    dismissLabel: String = "Dismiss",
    content: BannerScope.() -> Unit,
) {
    val slots = bannerSlots(content)
    val colors = bannerColorsFor(tone)
    val shape = Theme.shapes.small

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
            .background(colors.container, shape)
            .border(BorderStroke(Theme.sizing.borderWidth, colors.border), shape)
            .padding(Theme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onContainer) {
            slots.leading?.let { leading ->
                Box(Modifier.padding(top = 1.dp)) {
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
    accent: Color = Theme.colors.accent.solid,
    container: Color = Theme.colors.accent.container,
    content: @Composable () -> Unit,
) {
    val shape = Theme.shapes.extraSmall

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Intrinsic height so the accent rule can match the tallest child;
            // a Row otherwise gives fillMaxHeight nothing to fill.
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(container, shape),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Box(Modifier.padding(Theme.spacing.sm)) {
            CompositionLocalProvider(
                LocalContentColor provides Theme.colors.accent.onContainer,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun bannerColorsFor(tone: BannerTone): StatusColors = when (tone) {
    BannerTone.Info -> Theme.colors.info
    BannerTone.Success -> Theme.colors.success
    BannerTone.Warning -> Theme.colors.warning
    BannerTone.Danger -> Theme.colors.danger
    // Reachable at all only because `accent` is a `StatusColors` now. As four
    // loose fields it had no `border`, so a banner could not have been built
    // out of it without inventing one here.
    BannerTone.Accent -> Theme.colors.accent
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
