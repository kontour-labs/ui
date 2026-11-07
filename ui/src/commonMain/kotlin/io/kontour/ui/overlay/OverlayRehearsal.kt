package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.platform.platformRehearsesOverlays
import io.kontour.ui.theme.Theme

/**
 * **What an overlay draws, drawn once out of sight as the app starts, so the
 * first sheet opened does not stop to compile it.** On the web the GPU programs
 * Skia draws with are compiled the first time each kind of drawing is asked
 * for, and an overlay asks for several that a page does not: a squircle with its
 * shadow blurred under it, the scrim over everything, the page drawn smaller
 * behind a sheet. Measured in November 2026 on the docs site, the first side
 * sheet opened compiled about twenty programs as it slid in, and on a Pixel 11
 * Pro XL its frames took 107ms and 86ms, the second in the middle of the slide;
 * every later opening was smooth.
 *
 * So for the two frames after the first, those kinds are drawn here, under the
 * app and clipped to a single pixel in its corner, which the app paints straight
 * over: most of the programs are compiled then, while nothing on the page is
 * moving. Measured the same way after, the Pixel's first opening compiled under
 * a third as many and its worst frames came 67–75ms and 50ms apart, for one
 * frame of 80–100ms as the page appeared; an iPhone 14 Pro, whose programs are
 * cheaper and whose first opening is mostly its code running for the first
 * time, was the same before and after. A dialog's blur was drawn here too at
 * first and was taken out: it cost the iPhone frames of 418ms and 643ms as the
 * page appeared, more than a dialog's first opening does. And a desktop's
 * browser does not do it at all: a Mac's Chrome compiles through Metal, and all
 * of it at once was a frame of 843–968ms on a first visit. See the web's
 * [platformRehearsesOverlays].
 *
 * Composed only where [platformRehearsesOverlays] says so, and gone for good
 * after it, so it is nothing in a screenshot test, the accessibility tree or the
 * app's layout.
 */
@Composable
internal fun OverlayRehearsal() {
    if (!platformRehearsesOverlays) return
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        // After the app's own first frame, which is the one being waited for.
        withFrameNanos { }
        for (next in 1..RehearsalSteps) {
            step = next
            withFrameNanos { }
        }
        step = RehearsalSteps + 1
    }
    if (step !in 1..RehearsalSteps) return

    Box(Modifier.size(1.dp).clipToBounds().clearAndSetSemantics { }) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(RehearsalWidth, RehearsalHeight),
        ) {
            when (step) {
                // The scrim, and every overlay's panel with its shadow.
                1 -> {
                    Box(Modifier.requiredSize(RehearsalWidth, RehearsalHeight).background(Theme.colours.scrim))
                    for (shape in listOf(Theme.shapes.sideSheet, Theme.shapes.sheet, Theme.shapes.panel, Theme.shapes.container)) {
                        Surface(
                            modifier = Modifier.requiredSize(RehearsalWidth, RehearsalHeight),
                            shape = shape,
                            containerColour = Theme.colours.surfaceRaised,
                            shadow = Theme.elevation.overlay,
                        ) { }
                    }
                    Surface(
                        modifier = Modifier.requiredSize(RehearsalWidth, RehearsalHeight),
                        shape = Theme.shapes.container,
                        shadow = Theme.elevation.high,
                    ) { }
                }
                // The page drawn smaller behind a sheet: its text, its icons and
                // its shapes through a scale.
                else -> Column(Modifier.graphicsLayer { scaleX = 0.93f; scaleY = 0.93f }) {
                    Surface(shape = Theme.shapes.capsule, containerColour = Theme.colours.surfaceRaised) {
                        Text(Theme.strings.navigation)
                    }
                    Icon(Theme.icons.chevronBack, contentDescription = null)
                }
            }
        }
    }
}

/** One frame for each kind, so no one frame compiles them all. */
private const val RehearsalSteps = 2

private val RehearsalWidth = 160.dp
private val RehearsalHeight = 120.dp
