package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.FabPosition
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a [Scaffold] hands its content, and where it puts a FAB.
 *
 * Rendered rather than reasoned about, because both failures here are invisible:
 * padding that double-counts looks like a slightly generous gap, and a FAB
 * behind the bottom bar looks fine in a screenshot taken before the bar exists.
 */
class ScaffoldGeometryTest {

    private val density = 2f

    private class Measured(
        var padding: PaddingValues? = null,
        var fabTop: Float = Float.NaN,
        var fabHeight: Float = 0f,
    )

    private fun measure(
        barHeight: androidx.compose.ui.unit.Dp,
        insets: WindowInsets,
        withFab: Boolean = false,
        canvasHeight: Int = 1200,
    ): Measured {
        val measured = Measured()

        val scene = ImageComposeScene(
            width = 800,
            height = canvasHeight,
            density = Density(density),
        ) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Scaffold(
                    topBar = { Box(Modifier.fillMaxWidth().height(barHeight)) },
                    bottomBar = { Box(Modifier.fillMaxWidth().height(barHeight)) },
                    floatingActionButton = if (withFab) {
                        {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .onGloballyPositioned {
                                        measured.fabTop = it.positionInRoot().y
                                        measured.fabHeight = it.size.height.toFloat()
                                    }
                            )
                        }
                    } else {
                        null
                    },
                    fabPosition = FabPosition.End,
                    contentWindowInsets = insets,
                ) { padding ->
                    measured.padding = padding
                    Box(Modifier.fillMaxSize()) { Text("content") }
                }
            }
        }
        try {
            repeat(6) { frame -> scene.render(16_000_000L * frame) }
        } finally {
            scene.close()
        }
        return measured
    }

    @Test
    fun theContentPaddingIsTheBarNotTheBarPlusTheInset() {
        // A bar that is taller than the inset it sits under has already padded
        // itself for that inset. Adding both would inset the content twice, and
        // the result reads as a slightly generous gap rather than as a bug.
        val padding = measure(
            barHeight = 80.dp,
            insets = WindowInsets(top = 48, bottom = 48),
        ).padding!!

        assertEquals(80.dp, padding.calculateTopPadding())
        assertEquals(80.dp, padding.calculateBottomPadding())
    }

    @Test
    fun anInsetLargerThanTheBarStillGetsThrough() {
        // The other direction: a bar shorter than the display cutout must not
        // let content slide under the cutout.
        val padding = measure(
            barHeight = 20.dp,
            insets = WindowInsets(top = 96, bottom = 96),
        ).padding!!

        assertEquals(48.dp, padding.calculateTopPadding())
        assertEquals(48.dp, padding.calculateBottomPadding())
    }

    @Test
    fun horizontalInsetsAreHandedThroughUntouched() {
        val measured = measure(barHeight = 56.dp, insets = WindowInsets(left = 40, right = 40))
        val padding = measured.padding!!
        // 40px at 2x is 20dp. Bars do not consume horizontal insets, so these
        // pass straight through.
        assertEquals(20.dp, padding.calculateStartPadding(LocalLayoutDirectionLtr))
        assertEquals(20.dp, padding.calculateEndPadding(LocalLayoutDirectionLtr))
    }

    @Test
    fun theFabClearsTheBottomBar() {
        val canvasHeight = 1200
        val barHeightDp = 80.dp
        val measured = measure(
            barHeight = barHeightDp,
            insets = WindowInsets(bottom = 0),
            withFab = true,
            canvasHeight = canvasHeight,
        )

        val fabBottom = measured.fabTop + measured.fabHeight
        val barTop = canvasHeight - barHeightDp.value * density
        assertTrue(
            fabBottom <= barTop,
            "FAB bottom ${fabBottom}px is below the bar's top ${barTop}px — " +
                "a FAB behind the bottom bar is a FAB nobody can press",
        )
    }
}

private val LocalLayoutDirectionLtr = androidx.compose.ui.unit.LayoutDirection.Ltr
