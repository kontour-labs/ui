package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.LocalScrollFeel
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.motion.ScrollFeel
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * How a list carries on when it is thrown, tuned live on the phone.
 *
 * Reported from an iPhone as flings that "stop too soon". What the library can
 * change is its own fling — [ScrollDefaults.flingBehavior], which its scrollers
 * and the catalog's use — so this is where its three numbers are set by hand:
 * the rate it slows at, a scale on the throw, and whether flicks add up. The
 * list under them is thrown with whatever they are set to, and says how hard it
 * was thrown and how far it went.
 *
 * On iOS a `UIScrollView` sits beside it, with the same rows, reporting the same
 * two numbers from UIKit's side: the comparison is the point, since "too soon"
 * is only ever against what an iPhone's own lists do.
 */
@Composable
internal fun ScrollFeelCard() {
    val platform = LocalScrollFeel.current
    var library by remember { mutableStateOf(platform != null) }
    var rate by remember { mutableFloatStateOf(platform?.decelerationRate ?: ScrollFeel.UIKitDecelerationRate) }
    var scale by remember { mutableFloatStateOf(platform?.velocityScale ?: 1f) }
    var adds by remember { mutableStateOf(platform?.accumulates ?: true) }
    var compose by remember { mutableStateOf<Throw?>(null) }
    var uikit by remember { mutableStateOf<Throw?>(null) }
    val feel = if (library) ScrollFeel(rate, scale, adds) else null

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Text("Scroll feel", style = Theme.typography.titleSmall)
            Arm("Library fling", library) { library = it }
            FeelSlider("Keeps ${rateText(rate)} per ms", rate, RateRange, enabled = library) { rate = it }
            FeelSlider("Throw × ${hundredths(scale)}", scale, ScaleRange, enabled = library) { scale = it }
            Arm("Flicks add up", adds, enabled = library) { adds = it }

            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
                CompositionLocalProvider(LocalScrollFeel provides feel) {
                    FlingList(Modifier.weight(1f).height(ListHeight)) { compose = it }
                }
                if (hasNativeScroller) {
                    NativeScroller(Modifier.weight(1f).height(ListHeight), rows = Rows) { uikit = it }
                }
            }

            Text(throwLine(if (library) "Library" else "Platform", compose), style = Theme.typography.mono)
            if (hasNativeScroller) Text(throwLine("UIKit", uikit), style = Theme.typography.mono)
            compose?.let { thrown ->
                Text(
                    "UIKit's rate would take that ${abs(thrown.released / UIKitPerSecond).roundToInt()} dp",
                    style = Theme.typography.mono,
                    colour = Theme.colours.contentMuted,
                )
            }
            Text(
                "Throw each list the same way and compare how far it goes. A second flick " +
                    "while one is still moving adds to it when flicks add up.",
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
        }
    }
}

/** A throw: how fast it was let go, and how far it went, both in dp. */
internal class Throw(val released: Float, val travelled: Float)

/** The rows both lists hold, so they are thrown through the same thing. */
internal const val Rows = 200

/** One row's height, the same on both sides: a UIKit table row's 44 points. */
internal const val RowHeightDp = 44

private val ListHeight = 280.dp
private val SliderLabelWidth = 140.dp

/** `-ln(0.998) × 1000`: UIKit's normal rate as a decay per second. A throw at `v` goes `v` over this. */
private val UIKitPerSecond = -ln(ScrollFeel.UIKitDecelerationRate) * 1000f

private val RateRange = 0.990f..0.999f
private val ScaleRange = 0.5f..2f

@Composable
private fun FlingList(modifier: Modifier, onThrow: (Throw) -> Unit) {
    val density = LocalDensity.current.density
    val fling = ScrollDefaults.flingBehavior()
    val recording = remember(fling, density) { Recording(fling) { v, d -> onThrow(Throw(v / density, d / density)) } }
    LazyColumn(
        modifier.clip(Theme.shapes.small).background(Theme.colours.surfaceSunken),
        flingBehavior = recording,
    ) {
        items(Rows) { row ->
            Text(
                "Compose ${row + 1}",
                modifier = Modifier.fillMaxWidth().height(RowHeightDp.dp).padding(horizontal = Theme.spacing.md),
                style = Theme.typography.bodyMedium,
            )
        }
    }
}

/** A fling that says how hard it was thrown and how far it went, in pixels. */
private class Recording(private val inner: FlingBehavior, private val report: (Float, Float) -> Unit) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val outer = this
        var travelled = 0f
        val counting = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float = outer.scrollBy(pixels).also { travelled += it }
        }
        try {
            return with(inner) { counting.performFling(initialVelocity) }
        } finally {
            report(initialVelocity, travelled)
        }
    }
}

@Composable
private fun FeelSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Text(label, modifier = Modifier.width(SliderLabelWidth), style = Theme.typography.labelMedium)
        Slider(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f), enabled = enabled, valueRange = range)
    }
}

private fun throwLine(side: String, thrown: Throw?): String =
    if (thrown == null) "$side  —" else "$side  ${abs(thrown.released).roundToInt()} dp/s → ${abs(thrown.travelled).roundToInt()} dp"

/** A rate to four places: 0.998 and 0.9975 are different feels. */
private fun rateText(rate: Float): String {
    val tenThousandths = (rate * 10_000).roundToInt()
    return "0.${(tenThousandths % 10_000).toString().padStart(4, '0')}"
}

private fun hundredths(value: Float): String {
    val h = (value * 100).roundToInt()
    return "${h / 100}.${(h % 100).toString().padStart(2, '0')}"
}
