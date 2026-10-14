package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A [Scaffold]'s content follows a bar that changes height in the same layout
 * pass, not the one after it.
 *
 * The padding is written in the scaffold's measure, and the content reads it
 * in its own. On Android a write is only *announced* — the snapshot's apply
 * notifications, which are what mark a reader as needing to measure again —
 * when the main thread gets round to it, which is after the frame. So content
 * that was not otherwise dirty, a `LazyColumn` under a top bar collapsing as it
 * scrolls, or under a keyboard sliding up, was measured against the old padding
 * and caught up a frame later: one frame behind the bar, every frame it moved.
 * The desktop's and iOS's frames announce writes between their passes, and a
 * test's frame does too, which is why nothing here noticed.
 *
 * So this scene does what Android does: its dispatcher runs nothing until the
 * frame is over, and a frame ends by announcing what was written in it. The
 * bar's change is announced as Android would announce a scroll — before the
 * pass — and then the pass is run *once*.
 */
@OptIn(InternalComposeUiApi::class)
class ScaffoldPaddingPassTest {

    /** Runs what it is given only when asked: a main thread's queue, after the frame. */
    private class Posted : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    @Test
    fun theContentMovesWithTheBarInOnePass() {
        val posted = Posted()
        val recomposer = FrameRecomposer(posted)
        val scene = CanvasLayersComposeScene(
            recomposer,
            Density(1f),
            LayoutDirection.Ltr,
            IntSize(400, 800),
            object : PlatformContext.Empty() {},
        )
        val barHeight = mutableStateOf(56.dp)
        var content: LayoutCoordinates? = null
        var nanos = 0L

        fun frame() {
            nanos += 16_000_000L
            recomposer.performFrame(nanos)
            scene.measureAndLayout()
            posted.runAll()
            Snapshot.sendApplyNotifications()
        }

        try {
            scene.setContent(recomposer.compositionContext) {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    Scaffold(
                        windowInsets = WindowInsets(0),
                        topBar = {
                            // A bar whose height changes in layout alone, as a
                            // collapsing top bar's does: nothing recomposes.
                            Box(
                                Modifier.fillMaxWidth().layout { measurable, constraints ->
                                    val height = barHeight.value.roundToPx()
                                    val placeable = measurable.measure(
                                        constraints.copy(minHeight = height, maxHeight = height),
                                    )
                                    layout(placeable.width, height) { placeable.place(0, 0) }
                                }
                            )
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            Box(Modifier.fillMaxSize().onPlaced { content = it })
                        }
                    }
                }
            }
            repeat(4) { frame() }
            assertEquals(56f, assertNotNull(content).positionInRoot().y, "the content before the bar moved")

            barHeight.value = 120.dp
            Snapshot.sendApplyNotifications()
            scene.measureAndLayout()

            assertEquals(
                120f,
                assertNotNull(content).positionInRoot().y,
                "the bar is 120dp tall and the content, in the same pass, still starts under the old 56",
            )
        } finally {
            scene.close()
            recomposer.close()
        }
    }
}
