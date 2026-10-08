package io.kontour.ui.components.display

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.Transitions
import io.kontour.ui.motion.sizeSpec
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
        iconTint = Theme.colours.contentSubtle,
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
        iconTint = Theme.colours.danger.onContainer,
        assertive = true,
    )
}

/**
 * What to show where content will be, while it is on its way.
 *
 * ```
 * LoadingState { +"Loading departures" }
 * ```
 *
 * The third of the states a screen of fetched content passes through, drawn as
 * the other two are — the same block, the same place, a spinner where their
 * icon is — so moving from one to another with [StateSwitcher] changes what it
 * says rather than where it stands.
 *
 * A title is optional. Without one the spinner is announced as [loadingLabel];
 * with one, the title is what is read, and should say what is loading.
 *
 * Polite, as an empty state is: something on its way is not something the user
 * needs to stop for.
 *
 * **For a region with nothing to show yet** — a list before its first page, a
 * screen before its data. Where the shape of what is coming is known, a
 * [Skeleton] of it says more and moves less when it arrives; where something
 * small is busy inside content that is already there — a button, a row — a
 * [Spinner] on its own is the size of the thing that is waiting.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    loadingLabel: String = Theme.strings.loading,
    content: StateScope.() -> Unit = {},
) {
    val slots = stateSlots {
        content()
        // The spinner is the component's, as an error's retry is: a loading
        // state is the spinner, and a caller's own `leading` replaces it.
        if (leading == null) {
            val unnamed = title == null
            leading {
                Spinner(size = StateIconSize, contentDescription = if (unnamed) loadingLabel else null)
            }
        }
    }
    StateBlock(
        modifier = modifier,
        slots = slots,
        iconTint = Theme.colours.contentSubtle,
        assertive = false,
    )
}

/**
 * One of several states in one place — loading, empty, failed, the content
 * itself — and a transition between them when [target] changes.
 *
 * ```
 * StateSwitcher(target = status) { status ->
 *     when (status) {
 *         Status.Loading -> LoadingState { +"Loading stops" }
 *         Status.Failed -> ErrorState(onRetry = ::reload) { +"Couldn't load stops" }
 *         Status.Empty -> EmptyState { +"No stops nearby" }
 *         Status.Ready -> StopList(stops)
 *     }
 * }
 * ```
 *
 * A `when` on its own swaps one for the other in a frame, and a loading state
 * that a fast network replaces after 80ms flashes. Here the one leaving fades
 * and the one arriving fades in through it, the way content replaces content
 * with no relationship between them ([io.kontour.ui.motion.Transitions.fadeThrough]),
 * and the space they take changes size smoothly rather than in a step. Under
 * reduced motion it is a cross-fade, and the size is the new one at once.
 *
 * The state leaving is cleared from the semantics while it goes, so a screen
 * reader hears the new one and not both.
 *
 * **Each state is laid out as a column**, so a state that is a page — a
 * heading, a list, a form — has its sections one under another, as it would
 * anywhere else. A state that wants them side by side or over each other says
 * so with a `Row` or a `Box` of its own.
 *
 * @param target Which state to show. Anything with `equals` — an enum, a sealed
 *   class; a change of it is a change of state.
 * @param contentAlignment Where a smaller state sits in the space of a larger
 *   one while the size changes.
 * @param content Draws a state, given it: called with the state arriving and,
 *   while it goes, the one leaving, so it should draw from what it is given
 *   rather than from [target].
 */
@Composable
fun <T> StateSwitcher(
    target: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable (T) -> Unit,
) {
    val motion = Theme.motion
    val through = Transitions.fadeThrough()
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = { through.using(SizeTransform(clip = false) { _, _ -> motion.sizeSpec() }) },
        contentAlignment = contentAlignment,
        label = "stateSwitcher",
    ) { state ->
        val leaving = transition.targetState == EnterExitState.PostExit
        // A column, not a box: a state's content is a page's more often than
        // not, and a page has sections. In a box they were all drawn at its
        // top, over each other — reported from an app whose archive and several
        // forms broke that way, and worked round by keeping the content out of
        // the switcher.
        Column(if (leaving) Modifier.clearAndSetSemantics {} else Modifier) { content(state) }
    }
}

/**
 * A state's icon, title, message and action, centred.
 *
 * @param announced Whether it is a live region. A [DropZone] lays its words out
 *   the same way and is not news when it appears.
 * @param messageColour The message's colour: muted, unless the ground under it
 *   is a tone's.
 */
@Composable
internal fun StateBlock(
    modifier: Modifier,
    slots: StateScope,
    iconTint: Color,
    assertive: Boolean,
    announced: Boolean = true,
    messageColour: Color = Theme.colours.contentMuted,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (announced) liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite
            }
            .padding(Theme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        slots.leading?.let { leading ->
            Box(Modifier.padding(bottom = Theme.spacing.xs)) {
                ProvideContentColour(iconTint) {
                    ContentSlot(iconSize = StateIconSize, content = leading)
                }
            }
        }

        slots.title?.let { title ->
            ProvideTextStyle(Theme.typography.titleMedium.copy(textAlign = TextAlign.Center)) {
                ContentSlot(content = title)
            }
        }

        slots.message?.let { message ->
            // Long lines are hard to read centred; cap the measure.
            Box(Modifier.widthIn(max = 320.dp)) {
                ProvideContentColour(messageColour) {
                    ProvideTextStyle(
                        Theme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
                    ) {
                        ContentSlot(content = message)
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
