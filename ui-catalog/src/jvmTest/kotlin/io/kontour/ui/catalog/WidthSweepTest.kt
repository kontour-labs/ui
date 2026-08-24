package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.contract.ComponentSpec
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.kontourSizing
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every component, at every width it might be given.
 *
 * `Modifier.size` states a *preference*. A `Row` that has run out of width hands
 * out less than that, or nothing at all, and a component has no say in it — so
 * "narrower than I asked for" is a state every one of these has to survive, not
 * an edge case a caller can be told to avoid.
 *
 * `SqueezedControlTest` established that for three controls, after a `Switch`
 * took the frame down from inside its draw on a phone. This is the same question
 * asked of all 45, at a ladder of widths rather than at the one width the render
 * gallery uses. Two of the defects fixed in Round 13 were width- or
 * density-dependent and neither had a test that could see them; the gallery
 * renders at 600dp and nothing else did until this.
 *
 * Three things are asked at each width, in the order they can fail:
 *
 * 1. **It does not throw.** From measure or from draw, where there is nothing to
 *    catch it and the whole frame goes down with the component.
 * 2. **Its ink stays inside the box it was given.** Overflow is always a defect:
 *    either it is being clipped, or it is drawing on whatever is beside it.
 * 3. **It still draws something** once there is room to.
 *
 * Vertical overflow is deliberately *not* asserted. A component given 120dp is
 * supposed to get taller — text wraps, rows become columns — and calling that a
 * failure would be asking every component to be as short as its widest form.
 */
class WidthSweepTest {

    @Test
    fun everyComponentSurvivesEveryWidth() {
        val failures = mutableListOf<String>()

        for (spec in componentRegistry) {
            for (width in Widths) {
                val complaint = try {
                    check(spec, width)
                } catch (error: Throwable) {
                    "threw at ${width}dp: ${error::class.simpleName}: " +
                        error.message?.lineSequence()?.firstOrNull()
                }
                if (complaint != null) failures += "${spec.name} — $complaint"
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} width failures across ${componentRegistry.size} " +
                    "components:\n" + failures.joinToString("\n") { "  · $it" }
            )
        }
    }

    /**
     * The same question at 150% and 200% type.
     *
     * A narrow window and large type are the pair that breaks a fixed height or
     * a hard-coded padding, and the contract test's version of this asserted
     * only that the component had not collapsed to zero — it would have passed
     * something twice the width of its window. This asks the question the width
     * sweep asks, with the type turned up.
     *
     * At 320dp, because that is where it bites: a phone with the accessibility
     * type size on is the case, and a component that survives 200% at 600dp has
     * not been asked anything.
     */
    @Test
    fun everyComponentSurvivesLargeType() {
        val failures = mutableListOf<String>()

        for (spec in componentRegistry) {
            for (scale in FontScales) {
                val complaint = try {
                    check(spec, NarrowWindow, scale)
                } catch (error: Throwable) {
                    "threw at ${scale}x type: ${error::class.simpleName}: " +
                        error.message?.lineSequence()?.firstOrNull()
                }
                if (complaint != null) {
                    failures += "${spec.name} at ${scale}x type — $complaint"
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} failures at large type in a ${NarrowWindow}dp " +
                    "window:\n" + failures.joinToString("\n") { "  · $it" }
            )
        }
    }

    /** What is wrong with this component at this width, or `null`. */
    private fun check(spec: ComponentSpec, width: Int, fontScale: Float = 1f): String? {
        var box = Rect.Zero
        val margin = Margin * Density
        val sceneWidth = (width + Margin * 2) * Density

        Scene(width = sceneWidth, height = SceneHeight, density = Density.toFloat()) {
            Harness(fontScale) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .padding(top = Margin.dp)
                            .width(width.dp)
                            .reportBounds { box = it }
                    ) {
                        spec.content(Modifier, true) {}
                    }
                }
            }
        }.use { scene ->
            val frame = scene.frames(Frames)
            if (box.width <= 0f && width > 0) return "reported no bounds at ${width}dp"

            val ink = frame.inkBounds() ?: return if (width >= DrawsFrom) {
                "drew nothing at ${width}dp"
            } else {
                null
            }

            // Overflow is only a meaningful question once there is a box to
            // overflow. At 0dp *any* ink is past it by definition, and every
            // component with an intrinsic minimum or a padding fails that
            // trivially — `Slider` is 22dp of horizontal padding before it draws
            // anything at all. The question at 0dp is whether it throws, which
            // is asked above and is what `SqueezedControlTest` was written for.
            val spill = maxOf(
                (box.left - ink.first).toInt(),
                (ink.last - box.right).toInt(),
            )
            val floor = maxOf(spec.minWidth ?: 0, DrawsFrom)
            if (width >= floor && spill > Tolerance) {
                return "ink runs ${spill}px past its ${width}dp box " +
                    "(box ${box.left.toInt()}..${box.right.toInt()}, " +
                    "ink ${ink.first}..${ink.last})"
            }
            // Only the margin is outside the box, so anything reaching the very
            // edge of the canvas has overflowed further than the margin can show.
            if (width >= floor && (ink.first <= 1 || ink.last >= sceneWidth - 2)) {
                return "ink reaches the edge of a canvas ${margin}px wider than " +
                    "its ${width}dp box on each side"
            }
            return null
        }
    }

    /** The theme the app runs, with a phone's touch target and a type size. */
    @Composable
    private fun Harness(fontScale: Float, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(Density.toFloat(), fontScale)
        ) {
            KontourTheme(
                reduceMotion = true,
                sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = 48.dp),
            ) {
                OverlayHost(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    /** The horizontal extent of everything drawn, or `null` for a blank frame. */
    private fun BufferedImage.inkBounds(): IntRange? {
        val page = getRGB(1, 1)
        var left = -1
        var right = -1
        for (x in 0 until width) {
            var inked = false
            for (y in 0 until height) {
                if (differsFrom(getRGB(x, y), page)) {
                    inked = true
                    break
                }
            }
            if (inked) {
                if (left < 0) left = x
                right = x
            }
        }
        return if (left < 0) null else left..right
    }

    private fun differsFrom(a: Int, b: Int): Boolean =
        kotlin.math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > 12 ||
            kotlin.math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > 12 ||
            kotlin.math.abs((a and 0xFF) - (b and 0xFF)) > 12

    private companion object {
        /**
         * The ladder.
         *
         * `0` is the crash case — a row that has run out of width entirely.
         * `48` is a thumb. `120` and `200` are what a component gets inside a
         * three-column row on a phone. `320` is a small phone, `360` the common
         * one, `600` the gallery's width and the Compact/Medium boundary, and
         * `1200` where `WindowSizeClass` calls it Large.
         */
        val Widths = listOf(0, 48, 120, 200, 320, 360, 600, 1200)

        /**
         * 100%, and the two the accessibility settings actually offer.
         *
         * 200% is the ceiling WCAG asks for and the one Android's largest
         * display-size step lands near.
         */
        val FontScales = listOf(1f, 1.5f, 2f)

        /** A small phone in portrait, less its margins. */
        const val NarrowWindow = 320

        const val Density = 2

        /** Tall enough that a 120dp-wide component still has somewhere to wrap into. */
        const val SceneHeight = 1600

        /** Room either side of the box, so an overflow has somewhere to show. */
        const val Margin = 60

        /** Long enough for an entry animation to have finished moving. */
        const val Frames = 12

        /**
         * Below this, drawing nothing is a legitimate answer.
         *
         * A component measured at 0dp that draws nothing is behaving correctly —
         * `SqueezedControlTest`'s whole point is that this is the only correct
         * answer available to it.
         *
         * A component may raise its own floor with `ComponentSpec.minWidth`,
         * which is a declaration that it has parts too big to shrink. Exactly one
         * does.
         */
        const val DrawsFrom = 48

        /**
         * Antialiasing and a drop shadow, and nothing more.
         *
         * Elevation is drawn outside the bounds by design, so a few pixels of
         * spill is a shadow rather than a fault. Calibrated from what the sweep
         * actually reported rather than guessed — see the commit.
         */
        const val Tolerance = 12
    }
}
