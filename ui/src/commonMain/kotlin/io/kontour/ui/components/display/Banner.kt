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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColor
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.StatusColors
import io.kontour.ui.theme.Theme

/** How serious a [Banner] is. Ports the four severities from `home`'s `StatusBanner`. */
enum class BannerTone { Info, Success, Warning, Danger }

/**
 * An inline message about the state of something.
 *
 * ```
 * Banner(
 *     tone = BannerTone.Warning,
 *     title = "Delays on the Armadale line",
 *     message = "Services are running up to 12 minutes late.",
 *     icon = Tabler.Outline.AlertTriangle,
 *     onDismiss = viewModel::dismissAlert,
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
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    title: String? = null,
    icon: ImageVector? = null,
    onDismiss: (() -> Unit)? = null,
    dismissIcon: ImageVector? = null,
    dismissLabel: String = "Dismiss",
    action: (@Composable () -> Unit)? = null,
) {
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
            .border(BorderStroke(1.dp, colors.border), shape)
            .padding(Theme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onContainer) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    size = Theme.sizing.iconMedium,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (title != null) {
                    Text(title, style = Theme.typography.titleSmall)
                }
                Text(message, style = Theme.typography.bodySmall)
                if (action != null) {
                    Box(Modifier.padding(top = Theme.spacing.xs)) { action() }
                }
            }

            if (onDismiss != null && dismissIcon != null) {
                IconButton(
                    icon = dismissIcon,
                    contentDescription = dismissLabel,
                    onClick = onDismiss,
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
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    title: String? = null,
    icon: ImageVector? = null,
    onDismiss: (() -> Unit)? = null,
    dismissIcon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val motion = Theme.motion
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.tweenFast()) + expandVertically(motion.tweenDefault()),
        exit = fadeOut(motion.tweenFast()) + shrinkVertically(motion.tweenDefault()),
    ) {
        Banner(
            message = message,
            modifier = modifier,
            tone = tone,
            title = title,
            icon = icon,
            onDismiss = onDismiss,
            dismissIcon = dismissIcon,
            action = action,
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
    accent: Color = Theme.colors.accent,
    container: Color = Theme.colors.accentContainer,
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
                LocalContentColor provides Theme.colors.onAccentContainer,
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
}
