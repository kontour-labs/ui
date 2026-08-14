package io.kontour.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/** One step in a [Breadcrumbs] trail. */
@Immutable
data class Crumb(
    val label: String,
    val onClick: (() -> Unit)? = null,
)

/**
 * Where the user is in a hierarchy, and the way back up it.
 *
 * ```kotlin
 * Breadcrumbs(
 *     listOf(
 *         Crumb("Routes", onClick = ::goToRoutes),
 *         Crumb("Route 950", onClick = ::goToRoute),
 *         Crumb("Stops"),
 *     )
 * )
 * ```
 *
 * For a deep tree the user navigates around — the admin panel, chiefly. On a
 * phone a back button says the same thing in far less room, which is why this
 * has no mobile counterpart in the app.
 *
 * The last crumb is the current page and is deliberately **not** a link: a
 * control that navigates to where you already are is a control that appears to
 * do nothing. It carries the current-page announcement instead.
 *
 * Scrolls horizontally rather than truncating. A trail that collapses its middle
 * to an ellipsis hides exactly the levels the user is trying to get back to.
 */
@Composable
fun Breadcrumbs(
    crumbs: List<Crumb>,
    modifier: Modifier = Modifier,
    separator: androidx.compose.ui.graphics.vector.ImageVector = SystemIcons.ChevronForward,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .semantics { }
            .padding(vertical = Theme.spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            val isCurrent = index == crumbs.lastIndex
            CrumbLabel(crumb = crumb, isCurrent = isCurrent)

            if (!isCurrent) {
                Icon(
                    imageVector = separator,
                    contentDescription = null,
                    size = Theme.sizing.iconSmall,
                    tint = Theme.colors.contentSubtle,
                )
            }
        }
    }
}

@Composable
private fun CrumbLabel(crumb: Crumb, isCurrent: Boolean) {
    val colors = Theme.colors
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.small
    val onClick = crumb.onClick

    if (isCurrent || onClick == null) {
        Text(
            text = crumb.label,
            modifier = Modifier.padding(horizontal = Theme.spacing.xs),
            style = Theme.typography.bodySmall,
            color = if (isCurrent) colors.content else colors.contentMuted,
            maxLines = 1,
        )
        return
    }

    Text(
        text = crumb.label,
        modifier = Modifier
            .semantics { role = Role.Button }
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .clip(shape)
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(shape, pressScale = 1f),
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
            )
            .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.xxs),
        style = Theme.typography.bodySmall,
        color = colors.contentMuted,
        maxLines = 1,
    )
}
