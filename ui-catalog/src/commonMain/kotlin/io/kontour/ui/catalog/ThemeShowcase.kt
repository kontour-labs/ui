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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.StatusColors
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
    Surface(modifier = modifier, color = Theme.colors.background) {
        Column(
            modifier = Modifier.padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            TypeScale()
            ColourRamp()
            StatusTones()
            SurfacesAndElevation()
            ShapeScale()
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = Theme.typography.monoLabel,
        color = Theme.colors.accent.solid,
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
            color = Theme.colors.contentMuted,
        )
        Text(
            "Body small, in the subtle tone used for placeholders and hints.",
            style = Theme.typography.bodySmall,
            color = Theme.colors.contentSubtle,
        )
        Text("LABEL LARGE", style = Theme.typography.labelLarge)
    }
}

@Composable
private fun Swatch(name: String, color: Color, onColor: Color, width: Int = 132) {
    Surface(
        modifier = Modifier.width(width.dp).height(56.dp),
        shape = Theme.shapes.small,
        color = color,
        contentColor = onColor,
        // A hairline on every swatch, so the ones that match the page ground —
        // `surface` and `surfaceRaised` in light mode are both white — are still
        // visible as swatches rather than vanishing into the background.
        border = BorderStroke(Theme.sizing.borderWidth, Theme.colors.outline),
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
    val c = Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        SectionHeading("Colour")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Swatch("primary", c.primary, c.onPrimary)
            Swatch("accent", c.accent.solid, c.accent.onSolid)
            Swatch("accent.container", c.accent.container, c.accent.onContainer)
            // Identical to `accent` here, and that is the point: the default
            // schemes have no product in them, so `brand` resolves to the
            // accent until an app sets one. Labelled so the swatch reads as
            // "unset" rather than as a duplicate.
            Swatch("brand — unset", c.brand, c.accent.onSolid)
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
private fun StatusRow(name: String, tone: StatusColors) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(name, tone.solid, tone.onSolid, width = 110)
        Surface(
            modifier = Modifier.width(260.dp).height(56.dp),
            shape = Theme.shapes.small,
            color = tone.container,
            contentColor = tone.onContainer,
        ) {
            Box(Modifier.padding(Theme.spacing.sm), contentAlignment = Alignment.CenterStart) {
                Text("$name container — banner and chip tint", style = Theme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusTones() {
    val c = Theme.colors
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
                        color = Theme.colors.surface,
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
                    color = Theme.colors.surfaceSunken,
                    border = BorderStroke(Theme.sizing.borderWidth, Theme.colors.outline),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(name, style = Theme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
