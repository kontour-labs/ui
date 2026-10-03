package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.icons.tabler.outline.Route
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavScope
import io.kontour.ui.overlay.OverlayHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a navigation surface is allowed to buzz for: one thing.
 *
 * The policy is `DetentHapticsTest`'s — a press that already carries its own
 * visible answer is silent, and a discrete commit acknowledges with one light
 * `Tap`. On a navigation surface that sorts three presses three ways, and the
 * docs name all three: a **destination** is silent, because the page changing
 * is the answer; a **disclosure** taps, as an `Accordion` does, because opening
 * a run of rows in place is a commit with nothing moving under the finger; and
 * a **menu opening** is silent, as every menu trigger is.
 *
 * The group is where they meet — one `group` call is a disclosure in a sidebar
 * and a menu trigger in a bar — so it is the case most likely to answer the
 * wrong way on one surface. Recorded as `FeedbackIntent`s through
 * `LocalFeedback`, as in `DetentHapticsTest`: a component that performs no
 * intent is silent at every haptics level, so an empty list is the whole claim.
 */
class NavHapticsTest {

    @Test
    fun tappingADestinationIsSilent() {
        for (rail in listOf(false, true)) {
            val surface = if (rail) "sidebar" else "bar"
            val felt = mutableListOf<FeedbackIntent>()
            var clicks = 0
            var target = Rect.Zero

            Scene(width = 800, height = 600, reduceMotion = true) {
                Recording(felt) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        val content: @Composable NavScope.() -> Unit = {
                            item(true, {}, key = "nearby", icon = Tabler.Outline.MapPin) { +"Nearby" }
                            item(
                                false, { clicks++ }, key = "routes", icon = Tabler.Outline.Bus,
                                modifier = Modifier.reportBounds { target = it },
                            ) { +"Routes" }
                        }
                        if (rail) NavRail(expanded = true, content = content) else NavBar(content = content)
                    }
                }
            }.use { scene ->
                scene.frames(5)
                scene.tap(target.center)
                scene.frames(5)
            }

            assertEquals(1, clicks, "the tap never reached the $surface's destination")
            assertEquals(
                emptyList(), felt,
                "tapping a destination in a $surface fired $felt. The page changing is the answer to " +
                    "that press; a destination is silent, as a tab is.",
            )
        }
    }

    /** A group opening in place, in a rail wide enough for labels, taps once. */
    @Test
    fun aDisclosureInAnExpandedRailTapsOnce() {
        val felt = mutableListOf<FeedbackIntent>()
        var open by mutableStateOf(false)
        var header = Rect.Zero

        Scene(width = 800, height = 600, reduceMotion = true) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    NavRail(expanded = true) {
                        item(true, {}, key = "nearby", icon = Tabler.Outline.MapPin) { +"Nearby" }
                        group(
                            open, { open = it }, key = "routes", icon = Tabler.Outline.Route,
                            header = { +"Routes" }, modifier = Modifier.reportBounds { header = it },
                        ) {
                            item(false, {}, key = "950", icon = null) { +"950" }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(5)
            scene.tap(header.center)
            scene.frames(10)
        }

        assertTrue(open, "the tap never reached the group's disclosure")
        assertEquals(
            listOf(FeedbackIntent.Tap), felt,
            "opening a group in place fired $felt, where one Tap was wanted — a disclosure " +
                "acknowledges as an Accordion does, once",
        )
    }

    /** A group opening its menu, in a bar or a collapsed rail, is silent. */
    @Test
    fun openingAGroupsMenuIsSilent() {
        for (rail in listOf(false, true)) {
            val surface = if (rail) "collapsed rail" else "bar"
            val felt = mutableListOf<FeedbackIntent>()
            var trigger = Rect.Zero
            var child = Rect.Zero

            Scene(width = 800, height = 600, reduceMotion = true) {
                Recording(felt) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            val content: @Composable NavScope.() -> Unit = {
                                item(true, {}, key = "nearby", icon = Tabler.Outline.MapPin) { +"Nearby" }
                                group(
                                    false, {}, key = "routes", icon = Tabler.Outline.Route,
                                    header = { +"Routes" }, modifier = Modifier.reportBounds { trigger = it },
                                ) {
                                    item(false, {}, key = "950", icon = null, modifier = Modifier.reportBounds { child = it }) {
                                        +"950"
                                    }
                                }
                            }
                            if (rail) NavRail(content = content) else NavBar(content = content)
                        }
                    }
                }
            }.use { scene ->
                scene.frames(5)
                scene.tap(trigger.center)
                scene.frames(20)
            }

            assertTrue(
                child.width > 0f,
                "tapping the $surface's group never opened its menu, so its silence says nothing",
            )
            assertEquals(
                emptyList(), felt,
                "opening a group's menu in a $surface fired $felt. A menu opening is silent, as every " +
                    "menu trigger is; only the sidebar's in-place disclosure taps.",
            )
        }
    }

    @Composable
    private fun Recording(into: MutableList<FeedbackIntent>, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalFeedback provides FeedbackDispatcher { into += it },
            content = content,
        )
    }
}
