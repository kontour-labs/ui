package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.catalog.apps.AppHost
import io.kontour.ui.catalog.apps.MiniApp
import io.kontour.ui.catalog.apps.energy.EnergyPrefs
import io.kontour.ui.catalog.apps.energy.EnergySettingsPage
import io.kontour.ui.catalog.apps.energy.LocalEnergyPrefs
import io.kontour.ui.catalog.apps.energy.MonthPage
import io.kontour.ui.catalog.apps.energy.PlanPage
import io.kontour.ui.catalog.apps.energy.ReportsPage
import io.kontour.ui.catalog.apps.energy.TodayPage
import io.kontour.ui.catalog.apps.energy.UsagePage
import io.kontour.ui.catalog.apps.energy.months
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Energy, run in its host on a phone: its tiles open and close, its usage
 * turns into a table, its reports open as tabs and a month from the table,
 * and a run is scheduled from a sheet whose first row has the focus as it
 * opens. And a picture of each page, whole, to look at.
 */
@OptIn(ExperimentalTestApi::class)
class EnergyAppTest {

    @AfterTest
    fun assertGoldensMatched() = Screenshot.assertAllMatched()

    private fun ComposeUiTest.launch() {
        setContent { KontourTheme(darkTheme = false) { AppHost(MiniApp.Energy, onLeave = {}) } }
        waitForIdle()
    }

    private fun ComposeUiTest.tab(label: String) {
        onNodeWithTag("energy-tab-$label", useUnmergedTree = true).performClick()
        waitForIdle()
    }

    /** A tile opens its breakdown when pressed, and a press anywhere else closes it. */
    @Test
    fun aTileOpensAndAPressElsewhereClosesIt() = runDesktopComposeUiTest(width = 400, height = 860) {
        launch()
        onNodeWithTag("tile-made").performClick()
        waitForIdle()
        onNodeWithTag("tile-breakdown").assertExists("the pressed tile did not open its breakdown")

        onNodeWithTag("energy-today").performTouchInput { click(Offset(2f, 2f)) }
        waitForIdle()
        onNodeWithTag("tile-breakdown").assertDoesNotExist()
    }

    @Test
    fun usageTurnsIntoATable() = runDesktopComposeUiTest(width = 400, height = 860) {
        launch()
        tab("Usage")
        onNodeWithTag("energy-usage-chart").assertExists()
        // The switch, by its role: the legend's entries toggle too.
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and hasAnyAncestor(hasTestTag("energy-usage")))
            .performScrollTo()
            .performClick()
        waitForIdle()
        onNodeWithTag("energy-usage-table").assertExists("the switch did not turn the chart into its table")
        onNodeWithTag("energy-usage-chart").assertDoesNotExist()
    }

    @Test
    fun aReportOpensFromItsTabAndAMonthFromTheTable() = runDesktopComposeUiTest(width = 400, height = 860) {
        launch()
        tab("Reports")
        onNodeWithTag("energy-stream").assertExists("the year's report did not open first")

        onNodeWithText("Bills").performClick()
        waitForIdle()
        onNodeWithTag("energy-bills").assertExists("choosing the Bills tab did not open its report")

        onNodeWithText("Year").performClick()
        waitForIdle()
        // The row, which is what takes the click, its words merged into it; by
        // its action, as a screen reader would, since the table also lays its
        // rows out unseen to measure them.
        onAllNodes(hasText("Mar") and hasClickAction() and hasAnyAncestor(hasTestTag("energy-months-table")))
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag("energy-month").assertExists("a month's row did not open the month")
    }

    /**
     * The schedule sheet's first row has the focus as the sheet opens, and a
     * run typed into it goes on the calendar.
     */
    @Test
    fun aRunIsScheduledFromTheSheet() = runDesktopComposeUiTest(width = 400, height = 860) {
        launch()
        tab("Plan")
        onNodeWithTag("energy-schedule").performClick()
        waitForIdle()
        onNodeWithTag("energy-schedule-sheet").assertExists("the schedule sheet did not open")
        onNode(isFocused()).assertExists("the sheet's first row did not take the focus as it opened")

        onNode(isFocused()).performTextInput("Dishwasher")
        onNodeWithText("Schedule").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Dishwasher scheduled for tomorrow", substring = true).assertExists()
    }

    /**
     * Each page whole, on a phone, from its top to past its end: these are the
     * app's pictures, to be looked at when a chart or a component it shows
     * changes. A month's page is the March row's.
     */
    @Test
    fun everyPageIsAPicture() {
        val pages: List<Pair<String, @Composable () -> Unit>> = listOf(
            "today" to { TodayPage() },
            "usage" to { UsagePage() },
            "reports" to { ReportsPage(onMonth = {}) },
            "plan" to { PlanPage() },
            "settings" to { EnergySettingsPage() },
            "month" to { MonthPage(months[4], onBack = {}) },
        )
        for ((name, page) in pages) {
            Screenshot.render(
                name = "apps/energy-$name",
                width = PictureWidth,
                height = PictureHeight,
                density = PictureDensity,
                frames = Frames,
            ) {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    WindowSizeClassProvider(Modifier.fillMaxSize()) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            CompositionLocalProvider(LocalEnergyPrefs provides remember { EnergyPrefs() }) {
                                // A fixed size, so the harness's measure at no
                                // bounds at all is answered by the canvas, not
                                // by a large top bar asked how wide it wants to be.
                                Box(Modifier.requiredSize(PictureWidthDp, PictureHeightDp).background(Theme.colours.background)) { page() }
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        /** A phone 400dp wide, and tall enough for the longest page whole. */
        const val PictureWidth = 800
        const val PictureHeight = 3200
        const val PictureDensity = 2f
        const val Frames = 8
        val PictureWidthDp = 400.dp
        val PictureHeightDp = 1600.dp
    }
}
