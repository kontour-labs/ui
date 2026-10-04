package io.kontour.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.HandleInsets
import io.kontour.ui.adaptive.LocalHandleInsets
import io.kontour.ui.adaptive.handleClearedBottom
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A floating bar sits just clear of a system handle rather than above the
 * whole strip the system keeps for it.
 *
 * Asked for: *"move the navbar down a bit, it's very high (especially on ios,
 * since the home bar is not so visible. and even a little bit on android with
 * gesture navigation, although it's not so bad. on android with button
 * navigation, it's perfect)"*. The bar padded by the whole inset: 34dp of an
 * iPhone's, and about 24dp of Android's gesture handle, under 8dp of its own.
 *
 * Measured as how much higher a destination stands than it does with no inset
 * at all, so the bar's own padding and size cancel out.
 */
@OptIn(ExperimentalTestApi::class)
class NavBarHandleInsetTest {

    /** iOS: the whole foot is the home indicator, and none of it takes taps. */
    private val homeIndicator = HandleInsets(WindowInsets(bottom = 34.dp), WindowInsets(0))

    /** Android's three buttons: the whole foot takes taps. */
    private val threeButtons = HandleInsets(WindowInsets(bottom = 48.dp), WindowInsets(bottom = 48.dp))

    private fun ComposeUiTest.rise(
        insets: WindowInsets,
        system: HandleInsets?,
        style: NavBarStyle = NavBarStyle.Floating,
    ): Dp {
        // One composition, switched: a test may set its content once.
        var shown by mutableStateOf<Pair<WindowInsets, HandleInsets?>>(WindowInsets(0) to null)
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalHandleInsets provides shown.second) {
                    Box(Modifier.fillMaxSize().testTag("window")) {
                        NavBar(style = style, windowInsets = shown.first, modifier = Modifier.align(Alignment.BottomCenter)) {
                            item(true, {}, key = "a", icon = null) { +"Nearby" }
                            item(false, {}, key = "b", icon = null) { +"Saved" }
                        }
                    }
                }
            }
        }
        fun gap(): Float {
            waitForIdle()
            val window = onNodeWithTag("window").fetchSemanticsNode().boundsInRoot.bottom
            val foot = onNodeWithContentDescription("Nearby").fetchSemanticsNode().boundsInRoot.bottom
            return window - foot
        }
        val bare = gap()
        shown = insets to system
        val raised = gap()
        return with(density) { (raised - bare).toDp() }
    }

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) <= 1f, "$what: expected about $expected, was $actual")

    @Test
    fun aFloatingBarSitsLowOverAHomeIndicator() = runComposeUiTest {
        assertNear(14.dp, rise(WindowInsets(bottom = 34.dp), homeIndicator), "a floating bar over iOS's home indicator")
    }

    @Test
    fun overAGestureHandleItSitsAsLow() = runComposeUiTest {
        val gestures = HandleInsets(WindowInsets(bottom = 24.dp), WindowInsets(0))
        assertNear(14.dp, rise(WindowInsets(bottom = 24.dp), gestures), "a floating bar over Android's gesture handle")
    }

    @Test
    fun threeButtonsKeepTheWholeInset() = runComposeUiTest {
        assertNear(48.dp, rise(WindowInsets(bottom = 48.dp), threeButtons), "a floating bar over three buttons")
    }

    @Test
    fun aDockedBarKeepsTheWholeInset() = runComposeUiTest {
        assertNear(34.dp, rise(WindowInsets(bottom = 34.dp), homeIndicator, NavBarStyle.Docked), "a docked bar")
    }

    @Test
    fun aTallerInsetIsKeptWhole() = runComposeUiTest {
        assertNear(300.dp, rise(WindowInsets(bottom = 300.dp), homeIndicator), "a foot with the keyboard in it")
    }

    /** The scaffold pads its content by the bar it has, so the content comes down with it. */
    @Test
    fun theScaffoldPadsByTheLowerBar() = runComposeUiTest {
        var system by mutableStateOf(HandleInsets(WindowInsets(0), WindowInsets(0)))
        var bottom = 0.dp
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalHandleInsets provides system) {
                    NavigationSuiteScaffold(
                        destinations = {
                            item(true, {}, key = "a", icon = null) { +"Nearby" }
                            item(false, {}, key = "b", icon = null) { +"Saved" }
                        },
                        type = NavigationSuiteType.Bar,
                        windowInsets = WindowInsets(bottom = 34.dp),
                    ) { padding: PaddingValues -> bottom = padding.calculateBottomPadding() }
                }
            }
        }
        waitForIdle()
        val whole = bottom
        system = homeIndicator
        waitForIdle()
        assertNear(20.dp, whole - bottom, "the content's padding should come down with the bar")
    }

    @Test
    fun theFootFunction() {
        // No handle: as it was.
        assertEquals(100, handleClearedBottom(100, handle = 0, reach = 14))
        // A handle no taller than where it ends: nothing to trim.
        assertEquals(12, handleClearedBottom(12, handle = 12, reach = 14))
        // All handle: down to where it ends.
        assertEquals(14, handleClearedBottom(34, handle = 34, reach = 14))
        // Less than the handle (a landscape phone's smaller one): no more than it.
        assertEquals(10, handleClearedBottom(10, handle = 34, reach = 14))
        // Above the handle it climbs back, continuously, to the whole foot.
        assertEquals(24, handleClearedBottom(39, handle = 34, reach = 14))
        assertEquals(54, handleClearedBottom(54, handle = 34, reach = 14))
        assertEquals(300, handleClearedBottom(300, handle = 34, reach = 14))
    }
}
