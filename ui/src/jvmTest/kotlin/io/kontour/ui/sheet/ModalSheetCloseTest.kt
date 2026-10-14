package io.kontour.ui.sheet

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlayHostState
import io.kontour.ui.overlay.rememberOverlayHostState
import io.kontour.ui.theme.KontourTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A modal sheet with nowhere to slide to still closes, and does not take the app
 * with it.
 *
 * Closing waits for the sheet to reach hidden, round again whenever something
 * stops it short. A sheet that may not rest at hidden — one with no hidden detent,
 * or one refusing it through `confirmDetentChange` once it has opened, which is
 * the documented way to make a sheet the user must answer — is one `hide()` turns
 * down at once, without moving or suspending. So the wait went round without ever
 * yielding, and held the main thread for good: the app hung on the close.
 *
 * ### Why this lives on the JVM, and what the fuse is
 *
 * A loop that never suspends cannot be timed out from inside: no frame budget, no
 * `withTimeout`, no test timeout can reach a thread that never gives one up, and
 * the test clock runs the composition on the window system's event thread, which
 * every later test in the fork needs. So each sheet here refuses hidden through a
 * predicate that also reads a fuse, and a thread of the test's own lights the fuse
 * if the test has not finished by then. Lit, the sheet's detents are worked out
 * again on the next pass of the loop, the predicate throws, and the loop comes out
 * as a failure rather than as a hung build. Fixed, the close is over in a few
 * frames and the fuse is never lit.
 */
@OptIn(ExperimentalTestApi::class)
class ModalSheetCloseTest {

    @Test
    fun aSheetRefusingHiddenClosesWithoutHangingTheApp() = closes(
        detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
        initialDetent = SheetDetent.Hidden,
        refuses = { it == SheetDetent.Hidden },
    )

    @Test
    fun aSheetWithNoHiddenDetentClosesWithoutHangingTheApp() = closes(
        detents = listOf(SheetDetent.Half, SheetDetent.Expanded),
        initialDetent = SheetDetent.Half,
        refuses = { false },
    )

    private fun closes(
        detents: List<SheetDetent>,
        initialDetent: SheetDetent,
        refuses: (SheetDetent) -> Boolean,
    ) {
        val fuse = mutableStateOf(false)
        val finished = AtomicBoolean(false)
        try {
            runComposeUiTest {
                var visible by mutableStateOf(true)
                lateinit var host: OverlayHostState
                lateinit var sheet: SheetState

                setContent {
                    host = rememberOverlayHostState()
                    KontourTheme(reduceMotion = true) {
                        OverlayHost(Modifier.fillMaxSize(), state = host) {
                            sheet = rememberSheetState(
                                detents = detents,
                                initialDetent = initialDetent,
                                confirmDetentChange = { detent ->
                                    check(!fuse.value) { Spun }
                                    !refuses(detent)
                                },
                            )
                            ModalBottomSheet(
                                visible = visible,
                                onDismissRequest = { visible = false },
                                state = sheet,
                            ) {
                                Text("A required choice")
                            }
                        }
                    }
                }
                waitForIdle()
                assertEquals(1, host.visible.size, "the sheet never opened, so there was nothing to close")
                assertTrue(sheet.isVisible, "the sheet never opened, so there was nothing to close")

                Thread {
                    Thread.sleep(FuseMillis)
                    if (!finished.get()) Snapshot.withMutableSnapshot { fuse.value = true }
                }.apply { isDaemon = true }.start()

                mainClock.autoAdvance = false
                visible = false
                repeat(CloseFrames) { mainClock.advanceTimeByFrame() }

                assertTrue(
                    host.rendered.isEmpty(),
                    "the sheet's caller closed it and its entry is still in the host: ${host.rendered.size} left",
                )
            }
        } finally {
            finished.set(true)
        }
    }

    private companion object {
        /** A close is a handful of frames; this is a long way past any of them. */
        const val CloseFrames = 30

        /** Wall-clock, for a thread the composition cannot block: well past a slow machine's thirty frames. */
        const val FuseMillis = 15_000L

        const val Spun = "the modal sheet's close went round without ever suspending — the main thread is held for good"
    }
}
