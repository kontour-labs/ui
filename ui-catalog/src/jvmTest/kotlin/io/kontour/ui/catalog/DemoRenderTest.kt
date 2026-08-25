package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.demo.ComponentDemo
import io.kontour.ui.demo.DemoCard
import io.kontour.ui.demo.Knob
import io.kontour.ui.demo.componentDemos
import io.kontour.ui.demo.DemoContentForTest
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.foundation.Surface
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every demo draws something, at a phone's width and at a desktop's.
 *
 * The demos are what a reader presses, and unlike `componentRegistry` they are
 * hand-written — which means they can be wrong in ways a list of specimens
 * cannot. A demo that throws, or that renders an empty card because its state
 * was seeded into a state that draws nothing, is invisible to the compiler and
 * invisible to `check-components.py`, which only counts them.
 *
 * ### Not goldens
 *
 * Deliberately. `ComponentRenderTest` already keeps 148 committed images of
 * these same components, and a second set that moved every time somebody
 * improved a demo would be reviewed by rubber stamp within a month. What is
 * asserted is the thing that is never intentional: ink on the canvas.
 *
 * ### Both widths
 *
 * 390dp because that is where a demo's knob row wraps and where a component
 * that assumed a desktop stops fitting; 1440dp because the knob row switches
 * from selects to segmented controls above Compact and that branch would
 * otherwise never run in a test.
 */
class DemoRenderTest {

    @Test
    fun `every demo draws at both widths`() {
        val failures = mutableListOf<String>()

        for (demo in componentDemos.values.sortedBy { it.slug }) {
            for (width in listOf(390, 1440)) {
                val result = runCatching { drewInk(demo, width) }
                result.onFailure {
                    failures += "${demo.slug} at ${width}dp threw ${it::class.simpleName}: ${it.message}"
                }
                result.onSuccess { ink ->
                    if (!ink) failures += "${demo.slug} at ${width}dp drew nothing"
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail("${failures.size} demo renders failed:\n\n" + failures.joinToString("\n"))
        }
    }

    /**
     * Every setting of every knob draws something too.
     *
     * A `Choice` taken from an enum's `entries` grows by itself when somebody
     * adds a variant, which is the point — but it also means a new variant
     * arrives in the demo untested. This is what notices a variant that draws
     * nothing, and it costs one render each.
     */
    @Test
    fun `every knob setting draws`() {
        val failures = mutableListOf<String>()

        for (demo in componentDemos.values.sortedBy { it.slug }) {
            for (knob in demo.knobs) {
                val settings: List<Any?> = when (knob) {
                    is Knob.Flag -> listOf(true, false)
                    is Knob.Choice<*> -> knob.options
                }
                for (setting in settings) {
                    val result = runCatching { drewInk(demo, 1440, knob to setting) }
                    val where = "${demo.slug} with ${knob.label}=$setting"
                    result.onFailure {
                        failures += "$where threw ${it::class.simpleName}: ${it.message}"
                    }
                    result.onSuccess { ink -> if (!ink) failures += "$where drew nothing" }
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail("${failures.size} knob settings failed:\n\n" + failures.joinToString("\n"))
        }
    }

    /** Renders [demo] at [width] dp, optionally forcing one knob, and reports whether ink landed. */
    private fun drewInk(
        demo: ComponentDemo,
        width: Int,
        forced: Pair<Knob<*>, Any?>? = null,
    ): Boolean {
        var uniform = true
        ImageComposeScene(
            width = width,
            height = 700,
            density = Density(1f),
            content = {
                KontourTheme {
                    // Both providers are load-bearing rather than ceremony.
                    //
                    // `WindowSizeClassProvider`: `ChoiceKnob` asks the width
                    // class whether to draw a segmented control or a select, and
                    // the default with no provider is Compact — so without this
                    // the 1440 pass would quietly re-test the 390 branch.
                    //
                    // `OverlayHost`: a `Select` opens into one, and so do
                    // `SplitButton` and `FabMenu`. The first run of this test
                    // failed on every demo with a Choice knob for exactly that,
                    // which is a fair description of what the site provides and
                    // a bare harness does not.
                    WindowSizeClassProvider(Modifier.fillMaxWidth()) {
                        OverlayHost(Modifier.fillMaxWidth()) {
                            Surface(Modifier.fillMaxWidth(), color = Theme.colors.background) {
                                Box(Modifier.padding(16.dp)) {
                                    if (forced == null) {
                                        DemoCard(demo)
                                    } else {
                                        DemoContentForTest(demo, mapOf(forced))
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ).use { scene ->
            val bytes = requireNotNull(scene.render(0L).encodeToData(EncodedImageFormat.PNG)) {
                "Skia failed to encode ${demo.slug}"
            }.bytes
            val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return false
            val first = image.getRGB(0, 0)
            outer@ for (x in 0 until image.width step 5) {
                for (y in 0 until image.height step 5) {
                    if (image.getRGB(x, y) != first) {
                        uniform = false
                        break@outer
                    }
                }
            }
        }
        return !uniform
    }

    private inline fun <T> ImageComposeScene.use(block: (ImageComposeScene) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
