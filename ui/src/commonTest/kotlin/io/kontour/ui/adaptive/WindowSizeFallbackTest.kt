package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Without a [WindowSizeClassProvider], the size class is the window's.
 *
 * Reported from an app: *"Without `WindowSizeClassProvider`, the navigation
 * silently assumes a phone. Leave it out and you get the bottom bar on a 1440px
 * window."* The class defaulted to a 400×800 phone, and the root setup the docs
 * give — `KontourTheme { OverlayHost { … } }` — has no provider in it. Now the
 * theme reads the window the app is in wherever nothing measured one.
 */
@OptIn(ExperimentalTestApi::class)
class WindowSizeFallbackTest {

    private class Window(width: Int, height: Int) : WindowInfo {
        var size by mutableStateOf(IntSize(width, height))
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
        override val containerDpSize: DpSize get() = DpSize(size.width.dp, size.height.dp)
    }

    @Composable
    private fun Read(into: (WindowSizeClass) -> Unit) {
        val here = LocalWindowSizeClass.current
        SideEffect { into(here) }
    }

    @Test
    fun aThemeWithoutAProviderReadsTheWindow() = runComposeUiTest {
        val window = Window(1440, 900)
        var seen: WindowSizeClass? = null
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides window) {
                KontourTheme(reduceMotion = true) { Read { seen = it } }
            }
        }
        waitForIdle()
        assertEquals(WindowWidthClass.Large, seen?.width, "a 1440dp window is ${seen?.width}")
    }

    @Test
    fun aProviderOutsideTheThemeIsKept() = runComposeUiTest {
        var seen: WindowSizeClass? = null
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides Window(1440, 900)) {
                Box(Modifier.size(400.dp, 800.dp)) {
                    WindowSizeClassProvider {
                        KontourTheme(reduceMotion = true) { Read { seen = it } }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(WindowWidthClass.Compact, seen?.width, "the region a provider measured should win")
    }

    @Test
    fun aProviderInsideWins() = runComposeUiTest {
        var seen: WindowSizeClass? = null
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides Window(1440, 900)) {
                KontourTheme(reduceMotion = true) {
                    Box(Modifier.size(700.dp, 800.dp)) {
                        WindowSizeClassProvider { Read { seen = it } }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(WindowWidthClass.Medium, seen?.width)
    }

    @Test
    fun aClassSaidOutrightIsKept() = runComposeUiTest {
        var seen: WindowSizeClass? = null
        setContent {
            CompositionLocalProvider(
                LocalWindowInfo provides Window(1440, 900),
                LocalWindowSizeClass provides WindowSizeClass.of(400.dp, 800.dp),
            ) {
                KontourTheme(reduceMotion = true) { Read { seen = it } }
            }
        }
        waitForIdle()
        assertEquals(WindowWidthClass.Compact, seen?.width, "a class provided outright, even a phone's, is the app's to say")
    }

    @Test
    fun aNestedThemeKeepsTheOuterClass() = runComposeUiTest {
        val window = Window(1440, 900)
        var seen: WindowSizeClass? = null
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides window) {
                KontourTheme(reduceMotion = true) {
                    Box(Modifier.size(500.dp, 800.dp)) {
                        WindowSizeClassProvider {
                            KontourTheme(darkTheme = true, reduceMotion = true) { Read { seen = it } }
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(WindowWidthClass.Compact, seen?.width, "a nested theme should not put the window back over a measured region")
    }

    @Test
    fun noWindowSizeKeepsThePhone() = runComposeUiTest {
        var seen: WindowSizeClass? = null
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides Window(0, 0)) {
                KontourTheme(reduceMotion = true) { Read { seen = it } }
            }
        }
        waitForIdle()
        assertEquals(WindowWidthClass.Compact, seen?.width)
    }

    /** A window that changes size without changing class recomposes nothing; one that crosses a boundary does. */
    @Test
    fun onlyACrossingRecomposes() = runComposeUiTest {
        val window = Window(1440, 1000)
        var compositions = 0
        var seen: WindowSizeClass? = null
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides window) {
                KontourTheme(reduceMotion = true) {
                    val here = LocalWindowSizeClass.current
                    SideEffect {
                        compositions++
                        seen = here
                    }
                }
            }
        }
        waitForIdle()
        val settled = compositions
        window.size = IntSize(1400, 960)
        waitForIdle()
        assertEquals(settled, compositions, "a window 40dp narrower is the same class, and should recompose nothing")
        window.size = IntSize(500, 960)
        waitForIdle()
        assertEquals(WindowWidthClass.Compact, seen?.width, "narrowed to a phone's width, the class should follow")
    }
}
