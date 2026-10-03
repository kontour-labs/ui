package io.kontour.ui.catalog.apps.transit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Train
import io.kontour.ui.catalog.apps.LeaveAppButton
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.navigationSuitePadding
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * A page's padding inside the navigation: clear of the bar or the rail, as
 * every page inside the suite has to keep itself, and [around] more on every
 * side for the page's own margin.
 */
@Composable
internal fun transitPagePadding(around: Dp = Theme.spacing.md): PaddingValues {
    val suite = navigationSuitePadding()
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = suite.calculateStartPadding(direction) + around,
        top = suite.calculateTopPadding() + around,
        end = suite.calculateEndPadding(direction) + around,
        bottom = suite.calculateBottomPadding() + around + FabRoom,
    )
}

/** Room under a list's last row for the floating button over it. */
private val FabRoom: Dp = 72.dp

/** A heading over a group of rows on a page. */
@Composable
internal fun TransitHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Theme.typography.titleSmall, modifier = modifier)
}

internal fun iconFor(mode: Mode) = if (mode == Mode.Train) Tabler.Outline.Train else Tabler.Outline.Bus

/** [value] dollars as a fare is written: 23.60, never 23.6. */
internal fun dollars(value: Float): String {
    val cents = (value * 100).roundToInt()
    return "$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

/**
 * Whether a root page carries the way out in its top bar: on a phone, where
 * the navigation is a bar with no head to put it in. Beside a rail or a
 * sidebar it is at the navigation's head instead, and once is enough.
 */
internal val LocalLeaveInTopBar = staticCompositionLocalOf { true }

/** A root page's navigation slot: the way out of the app, where it belongs there. */
@Composable
internal fun rootNavigation(): (@Composable () -> Unit)? =
    if (LocalLeaveInTopBar.current) ({ LeaveAppButton() }) else null
