package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.Avatar
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CircularProgress
import io.kontour.ui.components.display.LinearProgress
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.RadioButton
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.platform.awaitBrowserIdle
import io.kontour.ui.platform.platformRehearsesOverlays
import io.kontour.ui.platform.platformRehearsesWhenIdle
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.clipProxy
import androidx.compose.foundation.shape.ZeroCornerSize

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
 * page appeared, more than a dialog's first opening does.
 *
 * **A desktop's browser rehearses more, and later.** A Mac's Chrome compiles
 * through Metal, at 30–50ms a program and far more for a large shadow's blur,
 * and drawing every overlay piece as the page appeared was a frame of
 * 843–968ms. But not rehearsing at all left a first visit to the docs site,
 * with its compiled shaders cleared, stopping for 54–484ms at a time as it was
 * read: about a second of it, in the hover, the first scroll, the component
 * pages and the icons. So there it waits until the browser is idle and the
 * reader has left the page alone for a moment, see the web's
 * [awaitBrowserIdle], and draws one kind a time: text and icons through
 * rounded corners, the controls, each panel's shape and shadow, the page
 * through a scale. Whole, through a layer at the faintest alpha rather than
 * clipped to a pixel, so a switch's thumb and a progress arc are drawn too.
 * Measured the same way after, nothing the reader did stopped for more than
 * 111ms, about a third of a second in all; the rehearsal itself is about a
 * second of compiling in six frames, while the reader is not using the page.
 * Chrome and the system keep what is compiled, so a later visit pays nothing.
 *
 * Composed only where [platformRehearsesOverlays] or [platformRehearsesWhenIdle]
 * says so, and gone for good after it, so it is nothing in a screenshot test,
 * the accessibility tree or the app's layout.
 */
@Composable
internal fun OverlayRehearsal() {
    val whenIdle = platformRehearsesWhenIdle
    if (!platformRehearsesOverlays && !whenIdle) return
    val kinds = if (whenIdle) IdleRehearsal else StartRehearsal
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        // After the app's own first frame, which is the one being waited for.
        withFrameNanos { }
        for (next in 1..kinds.size) {
            if (whenIdle) awaitBrowserIdle()
            step = next
            withFrameNanos { }
        }
        step = kinds.size + 1
    }
    val kind = kinds.getOrNull(step - 1) ?: return

    if (whenIdle) {
        // Whole, so every part of every piece is drawn, and through a layer at
        // the faintest alpha there is, under the app: not a pixel to be seen
        // even where the app has no ground of its own over it. A pixel's clip,
        // as on a phone, keeps Skia from drawing anything that does not reach
        // the pixel, and a switch's thumb or a progress arc would not.
        Box(Modifier.graphicsLayer { alpha = 1f / 255f }.clearAndSetSemantics { }) {
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) { Rehearse(kind) }
        }
    } else {
        Box(Modifier.size(1.dp).clipToBounds().clearAndSetSemantics { }) {
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) { Rehearse(kind) }
        }
    }
}

/**
 * What is drawn in one frame of a rehearsal. An overlay's pieces come whole, as
 * [Overlays], or one at a time, as the entries with a [panel]: which of
 * [RehearsePanel]'s it is. An enum rather than sealed classes, which would put
 * the Compose compiler's stability markers for them in the library's ABI dump.
 */
private enum class RehearsalKind(val panel: Int = -1) {
    Overlays, Scaled, Cards, Controls,
    Scrim(0), SideSheet(1), Sheet(2), Panel(3), Card(4), RaisedCard(5),
}

/** A phone's, in its first frames: an overlay's pieces, measured to pay there. */
private val StartRehearsal = listOf(RehearsalKind.Overlays, RehearsalKind.Scaled)

/**
 * A desktop's, one kind each time the browser is idle: what a first visit
 * compiled as it was read, text and icons through rounded corners, the
 * controls, and the shadows a panel and a card are drawn with. The panels one
 * shape a frame: all at once they were a frame of 695ms in a Mac's Chrome, and
 * without them the docs site's icons page stopped for 407ms on first sight.
 */
private val IdleRehearsal = listOf(RehearsalKind.Cards, RehearsalKind.Controls) +
    RehearsalKind.entries.filter { it.panel >= 0 } + RehearsalKind.Scaled

@Composable
private fun Rehearse(kind: RehearsalKind) {
    when (kind) {
        // The scrim, and every overlay's panel with its shadow.
        RehearsalKind.Overlays -> Box(Modifier.requiredSize(RehearsalWidth, RehearsalHeight)) {
            for (index in 0 until PanelShapes) RehearsePanel(index)
        }
        // The page drawn smaller behind a sheet: its text, its icons and its
        // shapes through a scale.
        RehearsalKind.Scaled -> Column(Modifier.graphicsLayer { scaleX = 0.93f; scaleY = 0.93f }) {
            Surface(shape = Theme.shapes.capsule, containerColour = Theme.colours.surfaceRaised) {
                Text(Theme.strings.navigation)
            }
            Icon(Theme.icons.chevronBack, contentDescription = null)
        }
        // Text and an icon running into a card's rounded corners, which Skia
        // draws with the corner's clip worked into the program, and the same
        // in a card in a card. And a grouped list's rows, whose first and last
        // round only two corners each: Skia's program for a clip rounded at the
        // bottom alone is not the one for all four, and the first scroll of a
        // page of grouped rows compiled four of them.
        RehearsalKind.Cards -> Column {
            // Text and icons through the corners themselves, as a list scrolled
            // under a rounded edge draws them: rounded all round, at the top
            // alone and at the bottom alone, each its own program in Skia.
            val corner = Theme.shapes.container
            for (shape in listOf(corner, corner.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize), corner.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize))) {
                Box(Modifier.requiredSize(RehearsalWidth, RehearsalHeight).clip(shape.clipProxy())) {
                    Text(Theme.strings.navigation.repeat(12), modifier = Modifier.requiredSize(RehearsalWidth, RehearsalHeight))
                    Icon(Theme.icons.chevronBack, contentDescription = null, modifier = Modifier.align(Alignment.TopStart))
                    Icon(Theme.icons.chevronBack, contentDescription = null, modifier = Modifier.align(Alignment.BottomEnd))
                }
            }
            val label = Theme.strings.navigation
            for (position in listOf(GroupPosition.First, GroupPosition.Middle, GroupPosition.Last)) {
                ListItem(onClick = { }, position = position) {
                    +label
                    supporting { Text(Theme.strings.navigation) }
                    trailing { Icon(Theme.icons.chevronForward, contentDescription = null) }
                }
            }
            Card(contentPadding = PaddingValues(0.dp)) {
                Text(Theme.strings.navigation)
                Icon(Theme.icons.chevronBack, contentDescription = null)
            }
            Card(contentPadding = PaddingValues(0.dp)) {
                Card(contentPadding = PaddingValues(0.dp)) {
                    Text(Theme.strings.navigation)
                    Icon(Theme.icons.chevronBack, contentDescription = null)
                }
            }
        }
        RehearsalKind.Controls -> Column {
            Switch(checked = true, onCheckedChange = { })
            Checkbox(checked = true, onCheckedChange = { })
            RadioButton(selected = true, onClick = { })
            Slider(value = 0.5f, onValueChange = { })
            CircularProgress(progress = 0.6f)
            CircularProgress(progress = null)
            LinearProgress(progress = 0.4f)
            Button(onClick = { }) { Text(Theme.strings.navigation) }
            IconButton(onClick = { }, icon = Theme.icons.chevronBack, contentDescription = Theme.strings.back)
            Avatar(name = Theme.strings.navigation)
        }
        // One panel's shape and shadow, for the entries that have one.
        else -> Box(Modifier.requiredSize(RehearsalWidth, RehearsalHeight)) { RehearsePanel(kind.panel) }
    }
}

/**
 * One panel of an overlay: the scrim, then each of a side sheet's, a sheet's, a
 * panel's and a card's shapes with an overlay's shadow, and a card's with a
 * raised one.
 */
@Composable
private fun RehearsePanel(index: Int) {
    val size = Modifier.requiredSize(RehearsalWidth, RehearsalHeight)
    when (index) {
        0 -> Box(size.background(Theme.colours.scrim))
        5 -> Surface(modifier = size, shape = Theme.shapes.container, shadow = Theme.elevation.high) { }
        else -> Surface(
            modifier = size,
            shape = listOf(Theme.shapes.sideSheet, Theme.shapes.sheet, Theme.shapes.panel, Theme.shapes.container)[index - 1],
            containerColour = Theme.colours.surfaceRaised,
            shadow = Theme.elevation.overlay,
        ) { }
    }
}

/** How many [RehearsePanel] draws: one for each [RehearsalKind] with a panel. */
private const val PanelShapes = 6

private val RehearsalWidth = 160.dp
private val RehearsalHeight = 120.dp
