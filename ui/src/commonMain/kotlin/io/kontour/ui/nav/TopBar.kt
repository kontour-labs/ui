package io.kontour.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.ProvideContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.adaptive.topEdges
import io.kontour.ui.theme.Theme

/** How much room a [TopBar] takes, and where its title sits. */
enum class TopBarStyle {
    /** One line, title on the leading side. The default. */
    Small,

    /**
     * One line, title centred.
     *
     * For a screen with actions on both sides, where a leading title would sit
     * off-balance. Costs width, so a long title truncates sooner.
     */
    Centred,

    /**
     * Two lines: actions on the first, a large title on the second.
     *
     * Collapses to [Small] as the content scrolls under it. The bar does not
     * own the scroll state, so the caller passes `collapseProgress` — there is
     * a [collapseProgress] overload for a `LazyListState`. For a screen whose
     * title is the thing the user came for, like a route or a stop.
     */
    Large,
}

object TopBarDefaults {
    val Height: Dp = 56.dp
    val LargeHeight: Dp = 112.dp
}

/**
 * A screen's title and its actions.
 *
 * ```kotlin
 * TopBar(
 *     onBack = navController::popBackStack,
 *     actions = { IconButton(Tabler.Outline.Star, "Favourite", onClick = ::favourite) },
 * ) { +"Perth Underground" }
 * ```
 *
 * **Not a navigation surface.** The app's destinations live in a [NavBar] at the
 * bottom or a [NavRail] on the leading edge; this bar carries the current
 * screen's identity and the actions that apply to it. Putting destinations up
 * here is a website's layout, and on a phone it puts them as far from the thumb
 * as the screen allows.
 *
 * The title is marked as a heading, so a screen reader can jump straight to it,
 * and the back button gets a real label rather than a bare glyph.
 *
 * @param onBack Draws a back button. Give it the same thing the system back
 *   gesture does — a visible back that goes somewhere different from the gesture
 *   is worse than no visible back.
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    style: TopBarStyle = TopBarStyle.Small,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    navigation: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    containerColor: Color = Theme.colors.background,
    contentColor: Color = Theme.colors.content,
    showDivider: Boolean = false,
    /**
     * 0 when the content is at the top, 1 when it has scrolled far enough to
     * collapse a [TopBarStyle.Large] bar. Ignored by the other styles.
     */
    collapseProgress: Float = 0f,
    /**
     * What the bar's *content* keeps clear of. The status bar and the display
     * cutout by default; pass `WindowInsets(0)` when something above has already
     * accounted for them, or when this bar is not at the top of the window.
     */
    windowInsets: WindowInsets = WindowInsets.topEdges,
    /**
     * The bar's title, and its subtitle. `+` fills the title.
     *
     * A collected builder rather than a composable slot, and
     * [TopBarStyle.Large] is why: it renders the title *twice* — small and
     * fading in, large and sliding out — so the content has to be something the
     * component can compose in two places at two sizes. A slot that emitted
     * where it was written could only be in one of them.
     */
    content: ListItemScope.() -> Unit,
) {
    val slots = listItemSlots(content)
    Surface(modifier = modifier.fillMaxWidth(), color = containerColor, contentColor = contentColor) {
        // Inside the surface, so the container colour still reaches the top of the
        // window and the status bar sits on the bar rather than on a strip of
        // whatever is behind it.
        Column(Modifier.windowInsetsPadding(windowInsets)) {
            when (style) {
                TopBarStyle.Small, TopBarStyle.Centred -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TopBarDefaults.Height)
                        .padding(horizontal = Theme.spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Leading(onBack, backLabel, navigation)
                    TitleBlock(
                        slots = slots,
                        modifier = Modifier.weight(1f),
                        centred = style == TopBarStyle.Centred,
                    )
                    actions?.invoke(this)
                }

                TopBarStyle.Large -> Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TopBarDefaults.Height)
                            .padding(horizontal = Theme.spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Leading(onBack, backLabel, navigation)
                        // The small title fades in exactly as the large one
                        // leaves, so the screen always has a title somewhere.
                        Box(
                            Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = collapseProgress }
                        ) {
                            slots.label?.let { title ->
                                ProvideTextStyle(Theme.typography.titleMedium) {
                                    ContentSlot(maxLines = 1, content = title)
                                }
                            }
                        }
                        actions?.invoke(this)
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = 1f - collapseProgress
                                // Slides up as it goes, rather than only fading
                                // — a title that fades in place looks like a
                                // rendering fault.
                                translationY = -16f * collapseProgress
                            }
                            .padding(
                                start = Theme.spacing.md,
                                end = Theme.spacing.md,
                                bottom = Theme.spacing.sm,
                            )
                    ) {
                        TitleBlock(slots = slots, large = true)
                    }
                }
            }

            if (showDivider) HorizontalDivider()
        }
    }
}

@Composable
private fun Leading(
    onBack: (() -> Unit)?,
    backLabel: String,
    navigation: (@Composable () -> Unit)?,
) {
    when {
        navigation != null -> navigation()
        onBack != null -> IconButton(
            icon = SystemIcons.ChevronBack,
            contentDescription = backLabel,
            onClick = onBack,
        )
    }
}

@Composable
private fun TitleBlock(
    slots: ListItemScope,
    modifier: Modifier = Modifier,
    centred: Boolean = false,
    large: Boolean = false,
) {
    val align = if (centred) TextAlign.Center else TextAlign.Unspecified
    Column(
        modifier = modifier,
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        slots.label?.let { title ->
            Box(Modifier.semantics { heading() }) {
                ProvideTextStyle(
                    (if (large) Theme.typography.displaySmall else Theme.typography.titleMedium)
                        .copy(textAlign = align)
                ) {
                    ContentSlot(maxLines = if (large) 2 else 1, content = title)
                }
            }
        }
        slots.supporting?.let { subtitle ->
            ProvideContentColor(Theme.colors.contentMuted) {
                ProvideTextStyle(Theme.typography.bodySmall.copy(textAlign = align)) {
                    ContentSlot(maxLines = 1, content = subtitle)
                }
            }
        }
    }
}

/**
 * How far a [TopBarStyle.Large] bar has collapsed, given a scroll position.
 *
 * ```kotlin
 * val listState = rememberLazyListState()
 * TopBar(style = TopBarStyle.Large, collapseProgress = collapseProgress(listState)) { +stop.name }
 * LazyColumn(state = listState) { … }
 * ```
 *
 * Pure, and tested, because the failure is a bar that never quite finishes
 * collapsing or finishes before the user has scrolled — both of which read as a
 * stutter and neither of which shows in a still.
 */
fun collapseProgress(scrolled: Float, distance: Float): Float =
    if (distance <= 0f) 0f else (scrolled / distance).coerceIn(0f, 1f)

/** [collapseProgress] for a lazy list, using the first item's scroll offset. */
@Composable
fun collapseProgress(
    state: androidx.compose.foundation.lazy.LazyListState,
    distance: Dp = TopBarDefaults.LargeHeight - TopBarDefaults.Height,
): Float {
    val distancePx = with(androidx.compose.ui.platform.LocalDensity.current) { distance.toPx() }
    val scrolled = if (state.firstVisibleItemIndex > 0) {
        distancePx
    } else {
        state.firstVisibleItemScrollOffset.toFloat()
    }
    return collapseProgress(scrolled, distancePx)
}
