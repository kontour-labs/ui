package io.kontour.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The content of a [NavigationSuiteScaffold] is one composable, whichever
 * surface the window has, and is told the bar's height from its first frame.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationSuiteContentTest {

    private class Fixture {
        var type by mutableStateOf(NavigationSuiteType.Bar)
        var token: Any? = null
        var taps = 0
        var tap: () -> Unit = {}
        var list: LazyListState? = null
    }

    @Composable
    private fun Harness(fixture: Fixture) {
        KontourTheme(darkTheme = false, reduceMotion = true) {
            OverlayHost {
                Box(Modifier.size(800.dp, 600.dp)) {
                    NavigationSuiteScaffold(
                        destinations = {
                            item(true, {}, key = "nearby", icon = null) { +"Nearby" }
                            item(false, {}, key = "saved", icon = null) { +"Saved" }
                        },
                        type = fixture.type,
                    ) { padding ->
                        val token = remember { Any() }
                        var taps by rememberSaveable { mutableIntStateOf(0) }
                        val list = rememberLazyListState()
                        // Read here, in composition, so a tap composes this again
                        // and the side effect below reports it: read only inside
                        // the side effect, it was read by nothing that runs again,
                        // and the fixture went on saying 0.
                        val tapped = taps
                        SideEffect {
                            fixture.token = token
                            fixture.taps = tapped
                            fixture.tap = { taps++ }
                            fixture.list = list
                        }
                        LazyColumn(Modifier.fillMaxSize().testTag("content"), state = list, contentPadding = padding) {
                            items(100) { Text("Departure $it", Modifier.fillMaxWidth().height(48.dp)) }
                        }
                    }
                }
            }
        }
    }

    /**
     * A bar becoming a rail — a phone turned on its side, a foldable opened, a
     * window dragged past 600dp — and the screen inside it carrying on.
     *
     * The content was called from a different branch for each surface, inside a
     * different parent, and a composable that changes branch is a new one: the
     * whole screen started again, its `remember`, its `rememberSaveable` and its
     * scroll position with it, every time the window crossed a breakpoint.
     */
    @Test
    fun theContentCarriesOnFromABarToARailAndBack() = runComposeUiTest {
        val fixture = Fixture()
        setContent { Harness(fixture) }
        waitForIdle()
        val token = assertNotNull(fixture.token)
        runOnUiThread { repeat(3) { fixture.tap() } }
        onNodeWithTag("content").performScrollToIndex(40)
        waitForIdle()
        assertEquals(3, fixture.taps)
        assertEquals(40, assertNotNull(fixture.list).firstVisibleItemIndex, "the list did not scroll")

        for (type in listOf(NavigationSuiteType.Rail, NavigationSuiteType.ExpandedRail, NavigationSuiteType.ModalRail, NavigationSuiteType.Bar)) {
            runOnUiThread { fixture.type = type }
            waitForIdle()
            assertSame(token, fixture.token, "the content's remember started again on becoming a $type")
            assertEquals(3, fixture.taps, "the content's rememberSaveable started again on becoming a $type")
            assertEquals(
                40,
                assertNotNull(fixture.list).firstVisibleItemIndex,
                "the content's list went back to its top on becoming a $type",
            )
        }
    }

    /**
     * The bar's height, handed to the content on the first frame rather than an
     * estimate of it.
     *
     * It was measured *after* the content had been laid out, from a guess of
     * the shortest a bar can be — 44 or 48dp, where a floating bar with its
     * margins is half as tall again, and more on an iPhone over its home
     * indicator. Anything anchored to the bottom of the content drew under the
     * bar for a frame and then jumped clear of it.
     */
    @Test
    fun theFirstFramesPaddingIsTheBarsHeight() {
        val height = 1200
        var estimate: Dp = Dp.Unspecified
        var first = Float.NaN
        var last = Float.NaN
        val scene = ImageComposeScene(width = 800, height = height, density = Density(2f)) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                estimate = NavBarDefaults.MinHeight
                NavigationSuiteScaffold(
                    destinations = {
                        item(true, {}, key = "nearby", icon = null) { +"Nearby" }
                        item(false, {}, key = "saved", icon = null) { +"Saved" }
                    },
                    type = NavigationSuiteType.Bar,
                ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .onGloballyPositioned {
                                val bottom = it.positionInRoot().y + it.size.height
                                if (first.isNaN()) first = bottom
                                last = bottom
                            }
                    )
                }
            }
        }
        try {
            scene.render().close()
            repeat(4) { scene.render().close() }
        } finally {
            scene.close()
        }
        val settledPadding = (height - last) / 2f
        assertTrue(
            settledPadding > estimate.value + 1f,
            "the bar is no taller than the estimate ($settledPadding dp against ${estimate.value}), so this proves nothing",
        )
        assertEquals(
            last,
            first,
            "the content's first frame ended ${(height - first) / 2f}dp above the bottom, and settled at $settledPadding",
        )
    }
}
