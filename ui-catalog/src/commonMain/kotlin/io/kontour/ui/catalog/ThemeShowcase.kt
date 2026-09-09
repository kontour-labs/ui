package io.kontour.ui.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.Chip
import io.kontour.ui.components.selection.RadioButton
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.TextFieldVariant
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.StatusColours
import io.kontour.ui.theme.Theme

/**
 * Renders the whole token set at once.
 *
 * The first thing to look at when a palette, a type scale or an elevation ramp
 * changes — a contrast test tells you a pairing is legal, this tells you whether
 * it looks right. It is also the source for the theme screenshot goldens, so a
 * token change shows up as a visual diff in review.
 */
@Composable
fun ThemeShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, colour = Theme.colours.background) {
        Column(
            modifier = Modifier.padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            TypeScale()
            ColourRamp()
            StatusTones()
            SurfacesAndElevation()
            ShapeScale()
            ContrastSensitive()
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = Theme.typography.eyebrow,
        colour = Theme.colours.accent.solid,
    )
}

@Composable
private fun TypeScale() {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
        SectionHeading("Type")
        Text("Display large", style = Theme.typography.displayLarge)
        Text("Display small", style = Theme.typography.displaySmall)
        Text("Headline medium", style = Theme.typography.headlineMedium)
        Text("Title medium", style = Theme.typography.titleMedium)
        Text(
            "Body large — the all-in-one public transport companion. " +
                "Real-time tracking for Perth and Melbourne.",
            style = Theme.typography.bodyLarge,
            colour = Theme.colours.contentMuted,
        )
        Text(
            "Body small, in the subtle tone used for placeholders and hints.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentSubtle,
        )
        Text("LABEL LARGE", style = Theme.typography.labelLarge)
        Text("EYEBROW — ABOVE A SECTION HEADING", style = Theme.typography.eyebrow)
        // Two rows of the same width in a monospaced face, one row of figures
        // that a theme with no mono still has to column-align through `tnum`.
        // A theme that swapped its typeface shows it here or nowhere: this is
        // the only picture of `mono` in the repository, and without it the
        // GTurbo golden was byte-identical whether its numeric face was wired
        // up or not.
        Text("mono — 09:42  $18.60  1HGBH41JXMN109186", style = Theme.typography.mono)
        Text("mono — 23:07  $ 4.05  WVWZZZ1JZ3W386752", style = Theme.typography.mono)
    }
}

@Composable
private fun Swatch(name: String, colour: Color, onColor: Color, width: Int = 132) {
    Surface(
        modifier = Modifier.width(width.dp).height(56.dp),
        shape = Theme.shapes.small,
        colour = colour,
        contentColour = onColor,
        // A hairline on every swatch, so the ones that match the page ground —
        // `surface` and `surfaceRaised` in light mode are both white — are still
        // visible as swatches rather than vanishing into the background.
        border = BorderStroke(Theme.sizing.borderWidth, Theme.colours.outline),
    ) {
        Box(Modifier.padding(Theme.spacing.xs), contentAlignment = Alignment.BottomStart) {
            Text(
                text = name,
                style = Theme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColourRamp() {
    val c = Theme.colours
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        SectionHeading("Colour")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Swatch("primary", c.primary, c.onPrimary)
            Swatch("accent", c.accent.solid, c.accent.onSolid)
            Swatch("accent.container", c.accent.container, c.accent.onContainer)
            // In the built-in schemes this is identical to `accent`, and that is
            // the point: they have no product in them, so `brand` resolves to
            // the accent until an app sets one, and the label says so rather
            // than letting the swatch read as a duplicate.
            //
            // It used to say "unset" unconditionally, which was true while the
            // default scheme was the only one anybody drew this under. A theme
            // that sets a real brand — GTurbo's logo red, one step off its fill
            // red — made the label wrong, which is the sort of thing a second
            // theme exists to find.
            Swatch(
                if (c.brand == c.accent.solid) "brand — unset" else "brand",
                c.brand,
                c.accent.onSolid,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Swatch("surface", c.surface, c.content)
            Swatch("surfaceSunken", c.surfaceSunken, c.content)
            Swatch("surfaceRaised", c.surfaceRaised, c.content)
            Swatch("surfaceInverse", c.surfaceInverse, c.onSurfaceInverse)
        }
    }
}

@Composable
private fun StatusRow(name: String, tone: StatusColours) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(name, tone.solid, tone.onSolid, width = 110)
        Surface(
            modifier = Modifier.width(260.dp).height(56.dp),
            shape = Theme.shapes.small,
            colour = tone.container,
            contentColour = tone.onContainer,
        ) {
            Box(Modifier.padding(Theme.spacing.sm), contentAlignment = Alignment.CenterStart) {
                Text("$name container — banner and chip tint", style = Theme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusTones() {
    val c = Theme.colours
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        SectionHeading("Status")
        StatusRow("success", c.success)
        StatusRow("warning", c.warning)
        StatusRow("danger", c.danger)
        StatusRow("info", c.info)
    }
}

@Composable
private fun SurfacesAndElevation() {
    val e = Theme.elevation
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        SectionHeading("Elevation")
        // Wraps, because four 120dp cards and their gaps need 528dp and a phone
        // has 360. Unwrapped, the fourth was off the edge entirely and the third
        // was a sliver with its label broken across two lines mid-word.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            listOf("low" to e.low, "medium" to e.medium, "high" to e.high, "overlay" to e.overlay)
                .forEach { (name, shadow) ->
                    Surface(
                        modifier = Modifier.size(width = 120.dp, height = 72.dp),
                        shape = Theme.shapes.medium,
                        colour = Theme.colours.surface,
                        shadow = shadow,
                    ) {
                        Box(Modifier.padding(Theme.spacing.sm), contentAlignment = Alignment.Center) {
                            Text(name, style = Theme.typography.labelMedium)
                        }
                    }
                }
        }
    }
}

@Composable
private fun ShapeScale() {
    val s = Theme.shapes
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        SectionHeading("Shape")
        // Six 96dp swatches need 616dp. Same reason as the elevation strip above.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            listOf(
                "xs" to s.extraSmall,
                "sm" to s.small,
                "md" to s.medium,
                "lg" to s.large,
                "xl" to s.extraLarge,
                "pill" to s.pill,
            ).forEach { (name, shape) ->
                Surface(
                    modifier = Modifier.size(width = 96.dp, height = 56.dp),
                    shape = shape,
                    colour = Theme.colours.surfaceSunken,
                    border = BorderStroke(Theme.sizing.borderWidth, Theme.colours.outline),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(name, style = Theme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * The five things a contrast tier used to change invisibly.
 *
 * `ContrastLevel` reaches the UI through one mechanism — a different
 * `ColourScheme` — so at the enhanced tier `outline` jumped while every *filled*
 * and every *borderless* container stayed exactly as it was. That went unnoticed
 * for as long as it did because the tier was only ever screenshotted as a
 * palette, never as components.
 *
 * These five are where the gap lived: containers that lean on a shadow for their
 * edge, buttons whose variants have no border, the selection controls, and a
 * filled text field whose border was transparent at every tier. They are here,
 * on the page that already draws four times for four schemes, rather than in
 * nine whole-page goldens nobody opened.
 */
@Composable
private fun ContrastSensitive() {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        SectionHeading("Contrast")

        Card(Modifier.fillMaxWidth()) {
            Text("Card — its edge is a shadow", style = Theme.typography.bodySmall)
        }

        ListItem(onClick = {}) {
            +"ListItem — the same, in a group"
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Chip(onClick = {}) { +"Chip" }
            Button(onClick = {}, variant = ButtonVariant.Ghost) { +"Ghost" }
            Button(onClick = {}, variant = ButtonVariant.Secondary) { +"Secondary" }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Checkbox(checked = true, onCheckedChange = {})
            Checkbox(checked = false, onCheckedChange = {})
            Switch(checked = true, onCheckedChange = {})
            Switch(checked = false, onCheckedChange = {})
            RadioButton(selected = true, onClick = {})
            RadioButton(selected = false, onClick = {})
        }

        TextField(
            state = rememberTextFieldState("Filled — a transparent border at every tier"),
            variant = TextFieldVariant.Filled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
