package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetPresentation
import io.kontour.ui.sheet.SideSheet
import io.kontour.ui.sheet.rememberSheetState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sheet keeps clear of the keyboard once, not twice.
 *
 * Reported from an Android app: *"when a keyboard opens with a sheet open, it
 * sometimes pushes the sheet to way higher than it needs to be"*. The sheet read
 * the keyboard's inset as a number and lifted itself by all of it, wherever its
 * container was — and in an app whose root already made room for the keyboard,
 * with `imePadding()`, a scaffold padded by `safeDrawing` or a window that resized
 * itself, the container already ended where the keyboard began. The sheet then
 * stood a whole keyboard above it.
 *
 * There is no keyboard in a test scene, so the inset is handed in, as it is in
 * `SheetSafeAreaTest`: `WindowInsets(bottom = 150.dp)` takes exactly the path
 * `WindowInsets.ime` does. "Somebody already made room" is a container padded by
 * the same 150dp. Each case is compared with the same sheet in a container that
 * reaches the window's edge with no inset at all, so nothing here depends on the
 * sheet's own margins or chrome.
 */
class SheetKeyboardInsetTest {

    private companion object {
        const val Density = 2f
        const val Width = 600
        const val Height = 1200
        val Keyboard = 150.dp
        val StatusBar = 40.dp

        /** A landscape cutout's worth, at the left. */
        val Cutout = 30.dp

        /** Rounding, not leeway: a pixel either way. */
        const val Px = 1.5f
    }

    private class Measured(val content: Rect, val handed: PaddingValues)

    private fun measure(
        presentation: SheetPresentation,
        insets: WindowInsets,
        padBottom: Dp = 0.dp,
        padTop: Dp = 0.dp,
        padLeft: Dp = 0.dp,
        detent: SheetDetent = SheetDetent.Expanded,
        tall: Boolean = false,
    ): Measured {
        var content = Rect.Zero
        var handed = PaddingValues(0.dp)
        Scene(width = Width, height = Height, density = Density, reduceMotion = true) {
            Box(Modifier.fillMaxSize().padding(start = padLeft, top = padTop, bottom = padBottom)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val sheet = rememberSheetState(
                        detents = listOf(SheetDetent.Hidden, detent),
                        initialDetent = SheetDetent.Hidden,
                    )
                    LaunchedEffect(Unit) { sheet.animateTo(detent) }
                    BottomSheet(sheet, presentation = presentation, dragHandle = null, windowInsets = insets) { padding ->
                        handed = padding
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(if (tall) 3000.dp else 100.dp)
                                .reportBounds { content = it },
                        )
                    }
                }
            }
        }.use { it.frames(30) }
        assertTrue(content.height > 0f, "the sheet's content never appeared")
        return Measured(content, handed)
    }

    private fun px(dp: Dp) = dp.value * Density

    private fun assertNear(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) <= Px, "$what: ${actual}px, where ${expected}px was expected")

    /** The report: a floating sheet in a container that already ends at the keyboard. */
    @Test
    fun aFloatingSheetIsNotLiftedAgainAboveRoomAlreadyMade() {
        val alone = measure(SheetPresentation.Floating, WindowInsets(0.dp))
        val made = measure(SheetPresentation.Floating, WindowInsets(bottom = Keyboard), padBottom = Keyboard)
        assertNear(
            alone.content.bottom - px(Keyboard),
            made.content.bottom,
            "a floating sheet above a keyboard its container already ends at sits",
        )
    }

    /** And where nobody made room, it still clears the keyboard: by all of it, less the margin it had anyway. */
    @Test
    fun aFloatingSheetStillClearsAKeyboardNobodyMadeRoomFor() {
        val alone = measure(SheetPresentation.Floating, WindowInsets(0.dp))
        val lifted = measure(SheetPresentation.Floating, WindowInsets(bottom = Keyboard))
        // Its margin is kept above the keyboard, as above the window's edge, so
        // it rises by the whole keyboard: it used to keep the larger of the two,
        // and rose by the keyboard less the margin. See `FloatingMarginInsets`.
        assertNear(
            alone.content.bottom - px(Keyboard),
            lifted.content.bottom,
            "a floating sheet over a keyboard in a container that reaches the window's bottom sits",
        )
    }

    /** An edge sheet hands its content the keyboard as padding: none of it, when the container ends there. */
    @Test
    fun anEdgeSheetHandsItsContentOnlyTheKeyboardOverIt() {
        val made = measure(SheetPresentation.Edge, WindowInsets(bottom = Keyboard), padBottom = Keyboard)
        val bottom = made.handed.calculateBottomPadding()
        assertTrue(bottom <= 0.5.dp, "an edge sheet in a container that ends at the keyboard handed its content $bottom of it")

        val over = measure(SheetPresentation.Edge, WindowInsets(bottom = Keyboard))
        val all = over.handed.calculateBottomPadding()
        assertTrue(abs((all - Keyboard).value) <= 0.5f, "an edge sheet over the keyboard handed its content $all, not $Keyboard")
    }

    /**
     * The other edges by the same rule: a container that starts below the status
     * bar owes it nothing, so a sheet at full height puts its content as close to
     * the container's top as it would with no status bar at all.
     */
    @Test
    fun aContainerBelowTheStatusBarOwesItNothing() {
        val alone = measure(SheetPresentation.Edge, WindowInsets(0.dp), detent = SheetDetent.Full, tall = true)
        val below = measure(
            SheetPresentation.Edge,
            WindowInsets(top = StatusBar),
            padTop = StatusBar,
            detent = SheetDetent.Full,
            tall = true,
        )
        assertNear(
            alone.content.top + px(StatusBar),
            below.content.top,
            "a full sheet's content in a container that starts below the status bar starts",
        )
    }

    /** And the sides: an inset left of a container that starts clear of it pads nothing. */
    @Test
    fun aSideInsetOutsideTheContainerPadsNothing() {
        val alone = measure(SheetPresentation.Edge, WindowInsets(0.dp))
        val clear = measure(SheetPresentation.Edge, WindowInsets(left = Cutout), padLeft = Cutout)
        assertNear(
            alone.content.left + px(Cutout),
            clear.content.left,
            "content in a container that starts right of a left inset starts",
        )
    }

    /**
     * A side sheet pads its content clear of the keyboard: by none of it in a
     * container that already ends there, and by all of it in one that does not.
     */
    @Test
    fun aSideSheetPadsOnlyForTheKeyboardOverItsContainer() {
        fun bottomOfContent(insets: WindowInsets, padBottom: Dp): Float {
            var content = Rect.Zero
            var container = Rect.Zero
            Scene(width = Width, height = Height, density = Density, reduceMotion = true) {
                Box(Modifier.fillMaxSize().padding(bottom = padBottom).reportBounds { container = it }) {
                    SideSheet(visible = true, windowInsets = insets) {
                        Box(Modifier.fillMaxWidth().weight(1f).reportBounds { content = it })
                    }
                }
            }.use { it.frames(30) }
            assertTrue(content.height > 0f, "the side sheet's content never appeared")
            return container.bottom - content.bottom
        }
        val made = bottomOfContent(WindowInsets(bottom = Keyboard), padBottom = Keyboard)
        assertNear(0f, made, "a side sheet in a container that ends at the keyboard keeps its content this far above it")
        val over = bottomOfContent(WindowInsets(bottom = Keyboard), padBottom = 0.dp)
        assertNear(px(Keyboard), over, "a side sheet over the keyboard keeps its content this far above the window's bottom")
    }
}
