package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How many times an overlay's content is composed while the overlay opens.
 *
 * The answer should be "a handful". It was "once per frame, for the whole
 * animation", and that is the reported lag: opening any sheet dropped the app
 * to a visibly low frame rate for as long as the sheet was moving.
 *
 * `OverlayHost` drove every overlay's appearance from an `Animatable<Float>` and
 * read `progress.value` **in composition**, to hand it down a composition local:
 *
 *     CompositionLocalProvider(LocalOverlayProgress provides progress.value) {
 *         entry.content()
 *     }
 *
 * A composition read of an animating value invalidates its scope every frame.
 * `entry.content()` is a lambda call rather than a skippable composable, so the
 * invalidation went all the way down — the sheet, and everything the caller put
 * inside it, recomposed sixty times a second.
 *
 * The cost was never really the recomposition. It is what rides on it: a fresh
 * modifier chain every frame means the sheet's two `dropShadow` layers — the
 * outer one a 50dp blur, which on a 3× phone is a 150-pixel radius — are
 * re-rasterised every frame instead of being cached. That is the frame rate.
 *
 * ### Counted, not timed
 *
 * A stopwatch here would measure this machine. A recomposition count is the same
 * number on a JVM and on a phone, and it is the thing that was actually wrong —
 * so it is what this asserts, and what a regression would move.
 */
class OverlayRecompositionTest {

    @Test
    fun aSheetsContentIsNotRecomposedOnEveryFrame() {
        var compositions = 0
        var open by mutableStateOf(false)

        Scene(width = 600, height = 900) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    ModalBottomSheet(visible = open, onDismissRequest = { open = false }) {
                        Counted { compositions++ }
                    }
                }
            }
        }.use { scene ->
            scene.frames(4)
            open = true
            scene.frames(Frames)
        }

        assertTrue(compositions > 0, "the sheet's content never composed at all")
        assertTrue(
            compositions <= Budget,
            "a sheet's content composed $compositions times over $Frames frames of " +
                "opening. Something in the host is reading an animated value during " +
                "composition, so the whole overlay subtree — and the blurred shadow " +
                "it draws — is being rebuilt every frame.",
        )
    }

    @Test
    fun aDialogsContentIsNotRecomposedOnEveryFrameEither() {
        // A dialog is the case that *does* read the progress, to scale and fade
        // itself. Reading it is not the problem; reading it during composition
        // is. Its content should not recompose at all for an animation it plays
        // no part in.
        var compositions = 0
        var open by mutableStateOf(false)

        Scene(width = 600, height = 900) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Dialog(visible = open, onDismissRequest = { open = false }) {
                        Counted { compositions++ }
                    }
                }
            }
        }.use { scene ->
            scene.frames(4)
            open = true
            scene.frames(Frames)
        }

        assertTrue(compositions > 0, "the dialog's content never composed at all")
        assertTrue(
            compositions <= Budget,
            "a dialog's content composed $compositions times over $Frames frames " +
                "of opening — the scale-and-fade is invalidating the content it " +
                "is applied over",
        )
    }

    @Test
    fun theHostDoesNotRecomposeTheAppBehindItEveryFrame() {
        // The content *behind* an overlay is the rest of the app. It has no
        // business recomposing because something appeared on top of it — twice,
        // for the show and the hide, is the honest number.
        var compositions = 0
        var open by mutableStateOf(false)

        Scene(width = 600, height = 900) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Counted { compositions++ }
                }
                ModalBottomSheet(visible = open, onDismissRequest = { open = false }) {
                    Box(Modifier.fillMaxWidth().height(200.dp))
                }
            }
        }.use { scene ->
            scene.frames(4)
            open = true
            scene.frames(Frames)
        }

        assertTrue(
            compositions <= Budget,
            "the app behind the sheet composed $compositions times while the sheet " +
                "opened — the host is invalidating its own content every frame",
        )
    }

    /** Reports every composition, and draws nothing worth looking at. */
    @Composable
    private fun Counted(onCompose: () -> Unit) {
        onCompose()
        Box(Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray))
    }

    private companion object {
        /** Long enough for a spring to settle at 16ms a frame. */
        const val Frames = 60

        /**
         * What "a handful" is allowed to be.
         *
         * Not one: an overlay legitimately composes when it is published, when
         * it mounts, and when the entry list changes underneath it, and pinning
         * an exact number would make this a test about `OverlayHost`'s internal
         * bookkeeping rather than about frames. Ten is comfortably above every
         * honest reason and an order of magnitude below one-per-frame, which is
         * the failure it exists to catch.
         */
        const val Budget = 10
    }
}
