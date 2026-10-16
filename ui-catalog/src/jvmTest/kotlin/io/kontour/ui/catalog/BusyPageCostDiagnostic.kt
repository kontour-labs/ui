package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.rememberScrollbarListState
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.demo.DemoCard
import io.kontour.ui.demo.demoFamilies
import io.kontour.ui.overlay.OverlayHost
import kotlin.test.Test
import org.junit.experimental.categories.Category

/**
 * What a busy page costs a frame, on `rememberScrollbarListState` and on
 * Compose's own state. **Diagnostic only**: it prints and asserts nothing, and
 * the milliseconds are this machine's software rasteriser, so only the
 * comparison between the two arms means anything.
 *
 * Written for a report from both phones that busy pages — the gallery's date
 * and time page above all — dropped frames. The scrollbar's state measures
 * every row ahead, and its rows are urgent, composed whole in the frame they
 * start in; it ran them from the first frame, so a page of heavy rows was
 * composed in full while it arrived. Date and time, idle frames by tens:
 *
 * | | frames 1–30 | frames 31–60 |
 * |---|---|---|
 * | scrollbar state, measuring at once | 55 · 44 · 43ms | 32 · 34 · 30ms |
 * | scrollbar state, measuring once still | 27 · 27 · 28ms | 36 · 55 · 49ms |
 * | plain state | 25 · 28 · 29ms | 26 · 30 · 31ms |
 *
 * The cost did not go: it moved to where the list has been still for 300ms,
 * and a frame in which nothing moves is a frame nobody sees.
 */
@Category(Slow::class)
class BusyPageCostDiagnostic {

    private fun arm(scrollbarState: Boolean, family: String): String {
        val demos = demoFamilies.single { it.name == family }.demos
        val composed = mutableSetOf<String>()
        val idle = mutableListOf<Long>()
        val scrolling = mutableListOf<Long>()
        Scene(width = 800, height = 1720, density = 2f, frameNanos = 8_333_333L) {
            OverlayHost(Modifier.fillMaxSize()) {
                val list: LazyListState = if (scrollbarState) rememberScrollbarListState() else rememberLazyListState()
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().then(if (scrollbarState) Modifier.scrollbar(list) else Modifier),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(demos, key = { it.slug }, contentType = { it.slug }) { demo ->
                        SideEffect { composed += demo.slug }
                        DemoCard(demo)
                    }
                }
            }
        }.use { scene ->
            scene.advance(2)
            repeat(60) {
                val t = System.nanoTime(); scene.advance(); idle += System.nanoTime() - t
            }
            val composedIdle = composed.size
            repeat(90) {
                scene.scroll(Offset(400f, 900f), Offset(0f, 1f))
                val t = System.nanoTime(); scene.advance(); scrolling += System.nanoTime() - t
            }
            fun stats(xs: List<Long>) = xs.map { it / 1e6 }.let { ms -> "mean %.1fms, p95 %.1fms, max %.1fms".format(ms.average(), ms.sorted()[(ms.size * 95) / 100 - 1], ms.max()) }
            val series = idle.chunked(10).joinToString(" | ") { c -> "%.0f".format(c.average() / 1e6) }
            return "${if (scrollbarState) "scrollbar state" else "plain state    "}: composed while idle $composedIdle of ${demos.size}; idle ${stats(idle)}; scrolling ${stats(scrolling)}; idle by tens of frames: $series"
        }
    }

    @Test
    fun busyPageFrames() {
        for (family in listOf("Date and time", "Display")) {
            arm(true, family); arm(false, family) // warm the JIT for both
            println("DIAG $family")
            println("DIAG   " + arm(true, family))
            println("DIAG   " + arm(false, family))
        }
    }
}
