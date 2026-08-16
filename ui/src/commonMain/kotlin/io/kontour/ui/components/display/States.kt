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
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * What to show where content would be, when there is none.
 *
 * ```
 * EmptyState {
 *     leading { +Tabler.Outline.Star }
 *     title { +"No favourites yet" }
 *     message { +"Star a stop or route and it will appear here." }
 *     action { Button(onClick = ::browse) { +"Browse routes" } }
 * }
 * ```
 *
 * The message should say *how to get out of the empty state*, not restate the
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
    modifier: Modifier = Modifier,
    content: StateScope.() -> Unit,
) {
    StateBlock(
        modifier = modifier,
        slots = stateSlots(content),
        iconTint = Theme.colors.contentSubtle,
        assertive = false,
    )
}

/**
 * What to show when something failed.
 *
 * ```
 * ErrorState(onRetry = viewModel::refresh, retryLabel = "Try again") {
 *     title { +"Couldn't load departures" }
 *     message { +"Check your connection and try again." }
 * }
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
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = Theme.strings.retry,
    content: StateScope.() -> Unit,
) {
    val slots = stateSlots {
        content()
        // The retry button is the component's, not the caller's: an error with no
        // way forward is a dead end, and the commonest cause of one — a dropped
        // connection — is exactly where retrying works. A caller that wants
        // something else puts it in `action` and leaves `onRetry` null.
        if (onRetry != null && action == null) {
            action {
                io.kontour.ui.components.action.Button(
                    onClick = onRetry,
                    variant = io.kontour.ui.components.action.ButtonVariant.Secondary,
                ) { +retryLabel }
            }
        }
    }
    StateBlock(
        modifier = modifier,
        slots = slots,
        iconTint = Theme.colors.danger.onContainer,
        assertive = true,
    )
}

@Composable
private fun StateBlock(
    modifier: Modifier,
    slots: StateScope,
    iconTint: androidx.compose.ui.graphics.Color,
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
        slots.leading?.let { leading ->
            Box(Modifier.padding(bottom = Theme.spacing.xs)) {
                ProvideContentColor(iconTint) {
                    ContentSlot(iconSize = StateIconSize, content = leading)
                }
            }
        }

        slots.title?.let { title ->
            ProvideTextStyle(Theme.typography.titleMedium.copy(textAlign = TextAlign.Center)) {
                ContentSlot(content = title)
            }
        }

        slots.supporting?.let { supporting ->
            // Long lines are hard to read centred; cap the measure.
            Box(Modifier.widthIn(max = 320.dp)) {
                ProvideContentColor(Theme.colors.contentMuted) {
                    ProvideTextStyle(
                        Theme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
                    ) {
                        ContentSlot(content = supporting)
                    }
                }
            }
        }

        slots.action?.let { action ->
            Box(Modifier.padding(top = Theme.spacing.sm)) {
                ContentSlot(content = action)
            }
        }
    }
}

/** Large enough to read as an illustration rather than as an icon in a row. */
private val StateIconSize = 40.dp
