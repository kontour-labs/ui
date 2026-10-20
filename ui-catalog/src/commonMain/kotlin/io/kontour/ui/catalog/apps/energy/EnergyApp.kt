package io.kontour.ui.catalog.apps.energy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.CalendarEvent
import io.kontour.icons.tabler.outline.ChartBar
import io.kontour.icons.tabler.outline.ReportAnalytics
import io.kontour.icons.tabler.outline.Settings
import io.kontour.icons.tabler.outline.Sun
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.adaptive.bottomEdges
import io.kontour.ui.catalog.apps.LeaveAppButton
import io.kontour.ui.catalog.apps.MiniApp
import io.kontour.ui.catalog.apps.transit.LocalLeaveInTopBar
import io.kontour.ui.charts.ChartDefaults
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.LocalNavExpansion
import io.kontour.ui.nav.NavRailDefaults
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.navigationSuitePadding
import io.kontour.ui.nav.navigationSuiteTypeFor
import io.kontour.ui.nav3.coversNavigation
import io.kontour.ui.nav3.rememberNavigationSuiteSceneStrategy
import io.kontour.ui.nav3.rememberPageTransitionStrategy
import io.kontour.ui.theme.Theme

/*
 * Energy's places. Plain objects, as Transit's are: the stack lives as long as
 * the app runs and no longer.
 */
internal data object TodayRoute
internal data object UsageRoute
internal data object ReportsRoute
internal data object PlanRoute
internal data object EnergySettingsRoute
internal data class MonthRoute(val name: String)

private val tabs = listOf(TodayRoute, UsageRoute, ReportsRoute, PlanRoute, EnergySettingsRoute)

/**
 * Energy: a house's roof, battery and meter, as charts.
 *
 * Asked for as *"a demo mini-app that shows off the new components. It should
 * obviously be based around charts, and show off a heap of those in different
 * configurations, but try to include all the components we've added."* So
 * every kind of mark is here doing a job it is the right mark for — a day's
 * power as stacked areas with tonight's forecast in a band, a month of days as
 * stacked bars, a week's busy hours as a heat map, a year as dumbbells, ranges,
 * boxes and a stream — and around them the rest of the release: documents in
 * [DocumentTabs][io.kontour.ui.nav.DocumentTabs], tariffs typed into
 * `TextFieldListItem` rows, a wide table whose pinned column narrows, a sheet
 * that leaves a live chart where it is, tooltips with icons, patterned
 * calendar events and the patterns setting that follows the device's
 * *Differentiate Without Colour*.
 *
 * The same shape as Transit: one back stack with the tabs at its root, a
 * floating bar on a phone and a rail or sidebar above that, and a month's page
 * over the navigation on a phone.
 */
@Composable
internal fun EnergyApp() {
    val backStack = remember { mutableStateListOf<Any>(TodayRoute) }
    val prefs = remember { EnergyPrefs() }
    val width = LocalWindowSizeClass.current.width
    val compact = width == WindowWidthClass.Compact
    val root = backStack.firstOrNull()

    fun go(tab: Any) {
        backStack.clear()
        backStack += tab
    }

    val suite = rememberNavigationSuiteSceneStrategy<Any> { pages ->
        NavigationSuiteScaffold(
            destinations = {
                for (tab in tabs) {
                    item(
                        selected = root == tab,
                        onClick = { go(tab) },
                        key = tab,
                        icon = iconFor(tab),
                        modifier = Modifier.testTag("energy-tab-${labelFor(tab)}"),
                    ) { +labelFor(tab) }
                }
            },
            type = navigationSuiteTypeFor(width),
            railExpandable = true,
            header = {
                if (LocalNavExpansion.current.expanded) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = Theme.spacing.sm),
                    ) {
                        LeaveAppButton()
                        Icon(MiniApp.Energy.icon, contentDescription = null)
                        Text(MiniApp.Energy.title, style = Theme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Box(
                        Modifier.width(NavRailDefaults.GlyphSize.width).padding(bottom = Theme.spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) { LeaveAppButton() }
                }
            },
        ) { pages() }
    }

    // A month is a page of its own on a phone, over the bar, as Transit's stop is.
    val detail: Map<String, Any> = if (compact) coversNavigation() else emptyMap()

    CompositionLocalProvider(LocalLeaveInTopBar provides compact, LocalEnergyPrefs provides prefs) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(suite),
            sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
            entryProvider = entryProvider {
                entry<TodayRoute> { TodayPage() }
                entry<UsageRoute> { UsagePage() }
                entry<ReportsRoute> { ReportsPage(onMonth = { backStack += MonthRoute(it) }) }
                entry<PlanRoute> { PlanPage() }
                entry<EnergySettingsRoute> { EnergySettingsPage() }
                entry<MonthRoute>(metadata = detail) { route ->
                    MonthPage(months.first { it.name == route.name }, onBack = { backStack.removeLastOrNull() })
                }
            },
        )
    }
}

private fun labelFor(tab: Any): String = when (tab) {
    TodayRoute -> "Today"
    UsageRoute -> "Usage"
    ReportsRoute -> "Reports"
    PlanRoute -> "Plan"
    else -> "Settings"
}

private fun iconFor(tab: Any) = when (tab) {
    TodayRoute -> Tabler.Outline.Sun
    UsageRoute -> Tabler.Outline.ChartBar
    ReportsRoute -> Tabler.Outline.ReportAnalytics
    PlanRoute -> Tabler.Outline.CalendarEvent
    else -> Tabler.Outline.Settings
}

/** When the app's charts draw their patterns as well as their colours. */
internal enum class PatternChoice(val label: String) {
    Device("Like the device"),
    Always("Always"),
    Never("Never"),
}

/**
 * What the app's settings page sets and its other pages read: kept above the
 * back stack, so changing tab does not forget a tariff half typed.
 */
internal class EnergyPrefs {
    var patterns by mutableStateOf(PatternChoice.Device)
    var live by mutableStateOf(false)
    val peak = TextFieldState("0.55")
    val offPeak = TextFieldState("0.24")
    val feedIn = TextFieldState("0.07")
    val supply = TextFieldState("1.12")
    val roof = TextFieldState("6.6")
    val battery = TextFieldState("13.5")
}

internal val LocalEnergyPrefs = staticCompositionLocalOf { EnergyPrefs() }

/**
 * Whether the app's charts draw patterns: the device's answer by default —
 * high contrast, or *Differentiate Without Colour* — and the setting's once
 * the reader has made one.
 */
@Composable
internal fun energyPatterns(): Boolean = when (LocalEnergyPrefs.current.patterns) {
    PatternChoice.Device -> ChartDefaults.showPatterns()
    PatternChoice.Always -> true
    PatternChoice.Never -> false
}

/** A page's padding inside the navigation, and [around] more for its margin. */
@Composable
internal fun energyPagePadding(around: Dp = Theme.spacing.md): PaddingValues {
    val suite = navigationSuitePadding()
    val direction = LocalLayoutDirection.current
    val foot = with(LocalDensity.current) { WindowInsets.bottomEdges.getBottom(this).toDp() }
    return PaddingValues(
        start = suite.calculateStartPadding(direction) + around,
        top = suite.calculateTopPadding() + around,
        end = suite.calculateEndPadding(direction) + around,
        bottom = maxOf(suite.calculateBottomPadding(), foot) + around,
    )
}

/** A heading over one of a page's charts. */
@Composable
internal fun EnergyHeading(text: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Column(modifier) {
        Text(text, style = Theme.typography.titleSmall)
        if (supporting != null) {
            Text(supporting, style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
        }
    }
}
