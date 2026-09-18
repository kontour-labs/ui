package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Shapes
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.kontourShapes

/**
 * Frame cost, on the device, with the expensive things switchable.
 *
 * ### Why this page exists at all
 *
 * A full-screen generic-path clip sat in `overlayBackdrop` for three rounds of
 * profiling, under every sheet the library opens, and nothing here found it.
 * The diagnostics that exercise that path — `BackdropCostDiagnostic` and the two
 * beside it — run on a **software rasteriser**, where a non-rectangular clip is
 * a mask and a mask is more pixel work, priced at about what it looks like it
 * should cost. On a GPU the same clip is an offscreen surface, a `saveLayer` and
 * a masked composite: an architectural penalty, not pixel work, and on a tiled
 * mobile GPU a known cliff. It was found by reading the source, and reported
 * from a phone as 58.3ms to open a sheet.
 *
 * So the instrument moved to where the question is. This page runs on the
 * phone, in the showcase, and each arm can be switched **on the device** —
 * which makes the reading a comparison rather than a number, and a comparison
 * is the only thing a single device can honestly produce.
 *
 * ### Read it in a release build, and read the peak
 *
 * A debug build of Compose has no baseline profile, so the first run through any
 * code path is interpreted and the first sheet you open is not the sheet anybody
 * will see. [FrameReadout]'s note has this at more length; it applies here
 * unchanged.
 *
 * The **peak** and the **95th percentile** are both reported because they fail
 * differently. One 99.9ms frame in an otherwise perfect run is a stutter and
 * wants a different fix from a run that sits uniformly at 12ms on a 120Hz
 * display — and a mean cannot tell those apart, which is why there is not one.
 */
@Composable
internal fun FramesPage(modifier: Modifier = Modifier) {
    var fade by remember { mutableStateOf(true) }
    var blur by remember { mutableStateOf(true) }
    var squircles by remember { mutableStateOf(true) }
    var dark by remember { mutableStateOf(false) }

    val run = rememberFrameRun("arms")
    val shapes = rememberArmShapes(squircles)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Reading(run)

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(Theme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                Text("Arms", style = Theme.typography.titleSmall)
                Arm("Theme cross-fade", fade) { fade = it }
                Arm("Backdrop blur", blur) { blur = it }
                Arm("Squircle corners", squircles) { squircles = it }
            }
        }

        // **The arms apply to the workload, not to the page.** A nested theme is
        // the only way to switch `animateThemeChanges` and `backdropBlur` at all
        // — they are `KontourTheme` parameters — and keeping the readout outside
        // it means the numbers do not change colour when an arm does.
        //
        // Its own `OverlayHost`, because a sheet renders into the *nearest* one
        // and the nearest one would otherwise be the app's, outside this theme:
        // the sheet under test would then be drawn with the blur arm the app has
        // rather than the one this page is set to.
        KontourTheme(
            darkTheme = dark,
            backdropBlur = blur,
            animateThemeChanges = fade,
            shapes = shapes,
        ) {
            Card(Modifier.fillMaxWidth()) {
                OverlayHost(Modifier.fillMaxWidth().height(WorkloadHeight.dp)) {
                    Workload(
                        recording = run.collecting,
                        // The flip closes off a segment as well as flipping, so
                        // the readout can report one worst frame per flip rather
                        // than one for the whole run. See `FrameRun.mark`.
                        onFlipTheme = {
                            run.mark()
                            dark = !dark
                        },
                    )
                }
            }
        }

        Text(
            "Press an arm, then Record, then do the thing — flip the theme, open " +
                "the sheet, flick the list. The run ends on its own.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

/** The numbers, and the display they are being judged against. */
@Composable
private fun Reading(run: FrameRun) {
    val displayHz = platformDisplayHz()
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Text(
                when {
                    run.collecting -> "Recording…"
                    run.hasReading -> "${run.frames} frames at $displayHz Hz"
                    else -> "Not recorded"
                },
                style = Theme.typography.titleSmall,
            )
            Verdict("peak", run.peakTenths, displayHz, run.hasReading)
            Verdict("p95", run.p95Tenths, displayHz, run.hasReading)
            // **Per flip, because one peak cannot answer the question asked.**
            // Reported of the theme switch: it stutters, but only for the first
            // few after opening the app. A run's single peak reads the same
            // whether the first flip cost three times the fourth or one frame was
            // unlucky. Each entry here is the worst frame between two presses of
            // "Flip theme", in order, so the shape is visible: on the JVM the
            // first totals 2.39x the fourth and it settles by the third.
            if (run.marks.size > 1) {
                Text(
                    "per flip  " + run.marks.joinToString("  ") { tenths ->
                        "${tenths / 10}.${tenths % 10}"
                    },
                    style = Theme.typography.bodySmall,
                )
            }
            Button(
                onClick = { run.start() },
                enabled = !run.collecting,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            ) { +if (run.collecting) "Recording" else "Record" }
        }
    }
}

/**
 * One number, coloured by what it means on this display.
 *
 * The colour is the reading, for the reason [FrameReadout] gives: a number a
 * reader has to convert against a budget they have to remember is a number that
 * gets glanced at and forgotten.
 */
@Composable
private fun Verdict(label: String, tenths: Int, displayHz: Int, real: Boolean) {
    Text(
        text = if (real) "${tenths / 10}.${tenths % 10} ms  $label" else "—  $label",
        style = Theme.typography.mono,
        colour = when {
            !real -> Theme.colours.contentDisabled
            verdictFor(tenths, displayHz) == FrameVerdict.Smooth -> Theme.colours.success.solid
            verdictFor(tenths, displayHz) == FrameVerdict.Halved -> Theme.colours.warning.solid
            else -> Theme.colours.danger.solid
        },
    )
}

@Composable
private fun Arm(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    SelectionRow(
        selected = on,
        onSelectedChange = onChange,
        role = Role.Switch,
    ) {
        +label
        trailing { Switch(checked = on, onCheckedChange = null) }
    }
}

/**
 * The three things worth timing, in one screen.
 *
 * Deliberately not one workload per arm. Every arm touches all three — a theme
 * fade repaints the list, the blur is on the sheet's backdrop, and the corners
 * are on every card in the list *and* the sheet — so a reader who wants to know
 * what an arm costs presses the same three things with it on and with it off.
 */
@Composable
private fun Workload(recording: Boolean, onFlipTheme: () -> Unit) {
    var sheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(Theme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        ) {
            Button(
                onClick = onFlipTheme,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Secondary,
            ) { +"Flip theme" }
            Button(
                onClick = { sheet = true },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Secondary,
            ) { +"Open sheet" }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(Stops) { stop ->
                    Card(Modifier.fillMaxWidth()) {
                        ListItem { +stop }
                    }
                }
            }
        }

        ModalBottomSheet(visible = sheet, onDismissRequest = { sheet = false }) {
            SheetHeader {
                +"A sheet"
                supporting { +if (recording) "Recording" else "Not recording" }
            }
            Column(Modifier.padding(Theme.spacing.md)) {
                Text(
                    "Opening this is the frame the report was about. The backdrop " +
                        "scales the page behind it, and the blur arm decides " +
                        "whether it blurs it as well.",
                    style = Theme.typography.bodySmall,
                    colour = Theme.colours.contentMuted,
                )
            }
        }
    }
}

/**
 * The corner arm: the library's squircles, or plain rounded rectangles.
 *
 * Not `kontourShapes(smoothing = 0f)`, which would measure nothing. A
 * `SquircleShape` returns `Outline.Generic` at every smoothing including none —
 * its own KDoc says so — and the whole of what this arm is asking is what
 * Skia's rounded-rect **fast path** is worth against a generic path it has to
 * mask with. So the off position is built from `RoundedCornerShape`, which is
 * the only thing that produces `Outline.Rounded`.
 *
 * The radii are the scale's own, so the two arms differ in the curve and in
 * nothing else.
 */
@Composable
private fun rememberArmShapes(squircles: Boolean): Shapes =
    remember(squircles) { if (squircles) kontourShapes() else roundedShapes() }

/**
 * The scale again, in plain rounded rectangles.
 *
 * Two things it deliberately does not reproduce, because the arm is about the
 * *outline kind* and nothing else. The capsules are an uncapped 50%, where the
 * real scale caps them; and `sideSheet` rounds its trailing corners rather than
 * the leading ones a layout direction would decide. Neither changes what Skia
 * does with the shape, which is the whole question.
 */
private fun roundedShapes(): Shapes {
    val xs = RoundedCornerShape(10.dp)
    val sm = RoundedCornerShape(16.dp)
    val md = RoundedCornerShape(22.dp)
    val lg = RoundedCornerShape(28.dp)
    val xl = RoundedCornerShape(34.dp)
    val capsule = RoundedCornerShape(percent = 50)
    return Shapes(
        extraSmall = xs,
        small = sm,
        medium = md,
        large = lg,
        extraLarge = xl,
        pill = capsule,
        capsule = capsule,
        control = capsule,
        field = capsule,
        container = md,
        panel = lg,
        sheet = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        sideSheet = RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp),
    )
}

private val Stops = listOf(
    "Perth Underground", "Elizabeth Quay", "McIver", "Claisebrook", "East Perth",
    "Mount Lawley", "Maylands", "Meltham", "Bayswater", "Ashfield",
    "Success Hill", "Guildford", "Woodbridge", "Midland", "Daglish",
    "Subiaco", "West Leederville", "Leederville", "Glendalough", "Stirling",
)

/** How tall the workload is. Enough for a list to flick and a sheet to come into. */
private const val WorkloadHeight = 420
