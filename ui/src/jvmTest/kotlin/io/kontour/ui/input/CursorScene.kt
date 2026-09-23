package io.kontour.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.theme.KontourTheme
import kotlinx.coroutines.Dispatchers

/**
 * An offscreen scene that remembers which cursor it was last asked to show.
 *
 * `ImageComposeScene` draws frames and has no pointer in them, so a cursor set on
 * it goes nowhere — its platform context drops the request. That is why the
 * mapping in `PointerCursor.kt` could only ever be checked as a *mapping*, and
 * why a component could ask for the wrong cursor, or for none, without any test
 * noticing. The scene underneath is built the same way `ImageComposeScene` builds
 * its own, with a platform context that keeps the icon instead.
 *
 * Nothing here is rendered. A cursor is decided by hit-testing, which needs
 * layout and not pixels, so each step composes, lays out and stops.
 *
 * The internal-API opt-in is the price of reaching the platform context at all;
 * a test in the same repository as its Compose pin can pay it.
 */
@OptIn(InternalComposeUiApi::class)
internal class CursorScene(
    width: Int,
    height: Int,
    density: Float = 2f,
    content: @Composable () -> Unit,
) : AutoCloseable {

    /** The last cursor the scene asked the platform for, or null if it never asked. */
    var cursor: PointerIcon? = null
        private set

    private val platform = object : PlatformContext.Empty() {
        override fun setPointerIcon(pointerIcon: PointerIcon) {
            cursor = pointerIcon
        }

        /**
         * A desktop's touch slop, which is 18**dp**. The default here is 18
         * *pixels*, half that at this scene's density — and a gesture that
         * wrongly waits for the touch slop under a mouse loses that much of every
         * drag, so half of it was hidden. The reorder gesture did exactly that,
         * and `ReorderMouseTest` measures it against the real number.
         */
        override val viewConfiguration: ViewConfiguration =
            object : ViewConfiguration by PlatformContext.DefaultViewConfiguration {
                override val touchSlop: Float = DesktopSlopDp * density
            }
    }
    private val recomposer = FrameRecomposer(Dispatchers.Unconfined)
    private val scene = CanvasLayersComposeScene(
        recomposer,
        Density(density),
        LayoutDirection.Ltr,
        IntSize(width, height),
        platform,
    )
    private var nanos = 0L
    private var buttons = PointerButtons()

    init {
        scene.setContent(recomposer.compositionContext) {
            KontourTheme {
                // Provided rather than earned. The theme learns the modality from
                // the first pointer event, and a cursor gated on it appears from
                // the second — invisible to a person, and a lost first hover here.
                CompositionLocalProvider(LocalInputModality provides InputModality.Mouse) {
                    content()
                }
            }
        }
        frames(2)
    }

    fun frames(count: Int) = repeat(count) {
        nanos += FrameNanos
        recomposer.performFrame(nanos)
        scene.measureAndLayout()
    }

    /** A mouse at [at], no button held. What the cursor is for. */
    fun hover(at: Offset) {
        send(PointerEventType.Move, at)
        frames(1)
    }

    /** The primary button, down at [at]. */
    fun press(at: Offset) {
        buttons = PointerButtons(isPrimaryPressed = true)
        send(PointerEventType.Press, at, PointerButton.Primary)
        frames(1)
    }

    /** Moves with whatever is held, in [steps] events so a drag clears its slop. */
    fun drag(from: Offset, to: Offset, steps: Int = 8) {
        for (step in 1..steps) {
            send(PointerEventType.Move, from + (to - from) * (step / steps.toFloat()))
            frames(1)
        }
    }

    fun release(at: Offset) {
        buttons = PointerButtons()
        send(PointerEventType.Release, at, PointerButton.Primary)
        frames(1)
    }

    private fun send(type: PointerEventType, at: Offset, button: PointerButton? = null) {
        scene.sendPointerEvent(
            eventType = type,
            position = at,
            timeMillis = nanos / 1_000_000L,
            type = PointerType.Mouse,
            buttons = buttons,
            button = button,
        )
    }

    override fun close() {
        scene.close()
        recomposer.close()
    }

    private companion object {
        const val FrameNanos = 16_000_000L
        const val DesktopSlopDp = 18f
    }
}
