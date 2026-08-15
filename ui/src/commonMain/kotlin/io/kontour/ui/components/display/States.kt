package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * What to show where content would be, when there is none.
 *
 * ```
 * EmptyState(
 *     icon = Tabler.Outline.Star,
 *     title = "No favourites yet",
 *     message = "Star a stop or route and it will appear here.",
 *     action = { Button(onClick = ::browse) { +"Browse routes" } },
 * )
 * ```
 *
 * The [message] should say *how to get out of the empty state*, not restate the
 * title. "No favourites yet" followed by "You have no favourites" tells the user
 * nothing they cannot see; followed by "Star a stop and it will appear here" it
 * becomes useful.
 *
 * Distinct from [ErrorState]: empty means the request succeeded and there is
 * genuinely nothing, which is often the user's own doing and needs no apology.
 * Showing an error face for an empty list makes people think they broke
 * something.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    StateBlock(
        modifier = modifier,
        icon = icon,
        iconTint = Theme.colors.contentSubtle,
        title = title,
        message = message,
        action = action,
        assertive = false,
    )
}

/**
 * What to show when something failed.
 *
 * ```
 * ErrorState(
 *     title = "Couldn't load departures",
 *     message = "Check your connection and try again.",
 *     onRetry = viewModel::refresh,
 *     retryLabel = "Try again",
 * )
 * ```
 *
 * Announced assertively, because unlike an empty state this is something the
 * user needs to know happened rather than something they can discover at their
 * own pace.
 *
 * Give it an [onRetry] whenever retrying is possible. An error with no way
 * forward is a dead end, and the most common cause of one — a dropped
 * connection — is exactly the case where retrying usually works.
 */
@Composable
fun ErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Try again",
) {
    StateBlock(
        modifier = modifier,
        icon = icon,
        iconTint = Theme.colors.danger.onContainer,
        title = title,
        message = message,
        assertive = true,
        action = if (onRetry != null) {
            {
                io.kontour.ui.components.action.Button(
                    onClick = onRetry,
                    variant = io.kontour.ui.components.action.ButtonVariant.Secondary,
                ) { +retryLabel }
            }
        } else {
            null
        },
    )
}

@Composable
private fun StateBlock(
    modifier: Modifier,
    icon: ImageVector?,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    message: String?,
    action: (@Composable () -> Unit)?,
    assertive: Boolean,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite
            }
            .padding(Theme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                size = 40.dp,
                modifier = Modifier.padding(bottom = Theme.spacing.xs),
            )
        }

        Text(
            text = title,
            style = Theme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        if (message != null) {
            Text(
                text = message,
                style = Theme.typography.bodyMedium,
                color = Theme.colors.contentMuted,
                textAlign = TextAlign.Center,
                // Long lines are hard to read centred; cap the measure.
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }

        if (action != null) {
            Box(Modifier.padding(top = Theme.spacing.sm)) { action() }
        }
    }
}
