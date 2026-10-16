package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay

/**
 * What the gallery does at launch before the reader can open anything: an
 * experiment, run on a phone, to find out why the first sheet of a launch is
 * slow and every one after it is not.
 *
 * Reported from an iPhone: the first animation of a kind stutters — the first
 * sheet opened above all — and later ones, on any page, do not; and the first
 * launch after an install is worse than the launches after it. The JVM does not
 * reproduce it (`FirstOpenCostDiagnostic`), and on iOS there is no interpreter
 * to warm, so two candidates are left, and each mode below answers for one:
 *
 * - [Compose] composes and lays out a sheet once, never drawn. If the first
 *   real sheet is smooth after it, the cost was code running for the first
 *   time — Kotlin/Native's lazy initialisation, a first composition.
 * - [Draw] does that and draws it, behind the gallery where it is never seen.
 *   If only this one helps, the cost was the GPU's: a pipeline compiled for
 *   each new kind of drawing, a blur, a shadow, a clip — which iOS also keeps
 *   between launches, which fits the first launch after an install being worst.
 *
 * Stored, and read at launch, because the question is about a launch.
 */
internal enum class WarmUp { Off, Compose, Draw }

/** Whether this platform keeps a [WarmUp] between launches: iOS, where the question is. */
internal expect val warmUpStored: Boolean

/** The [WarmUp] to run at this launch. */
internal expect fun storedWarmUp(): WarmUp

/** Keeps [mode] for the next launch. */
internal expect fun storeWarmUp(mode: WarmUp)

/**
 * The warm-up sheet, opened once and closed, in a host of its own behind the
 * gallery, and then gone. See [WarmUp].
 */
@Composable
internal fun FirstUseWarmUp(mode: WarmUp) {
    if (mode == WarmUp.Off) return
    var done by remember { mutableStateOf(false) }
    if (done) return
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // A frame of the gallery first, so the warm-up is not part of the
        // launch's own first frame.
        withFrameNanos { }
        open = true
        delay(OpenMillis)
        open = false
        delay(CloseMillis)
        done = true
    }
    Box(
        Modifier
            .fillMaxSize()
            // Composed, measured and placed; drawn only for [WarmUp.Draw].
            .then(if (mode == WarmUp.Compose) Modifier.drawWithContent { } else Modifier),
    ) {
        OverlayHost(Modifier.fillMaxSize()) {
            ModalBottomSheet(visible = open, onDismissRequest = { open = false }) {
                SheetHeader {
                    +"Warming up"
                    supporting { +"Drawn behind the gallery, once" }
                }
                Column(Modifier.padding(Theme.spacing.md)) {
                    Text("The first sheet of a launch, so the reader's is the second.")
                }
            }
        }
    }
}

/** Long enough for the sheet to arrive and settle. */
private const val OpenMillis = 900L

/** And to leave. */
private const val CloseMillis = 700L
