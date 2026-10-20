package io.kontour.ui.catalog.apps.energy

import io.kontour.ui.foundation.PatternDirection
import io.kontour.ui.foundation.FillPatternDefaults
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.BatteryCharging
import io.kontour.icons.tabler.outline.Coin
import io.kontour.icons.tabler.outline.Flame
import io.kontour.icons.tabler.outline.Moon
import io.kontour.icons.tabler.outline.Plug
import io.kontour.icons.tabler.outline.SolarPanel
import io.kontour.ui.a11y.differentiateWithoutColour
import io.kontour.ui.catalog.apps.LocalAppSettings
import io.kontour.ui.catalog.apps.transit.rootNavigation
import io.kontour.ui.catalog.pageScroll
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.FillPattern
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.fillPattern
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import io.kontour.ui.theme.Theme

/**
 * Settings: the tariff and the system, typed into the rows of a group — a
 * `TextFieldListItem` each, through the group's `field` — and how the charts
 * tell their series apart.
 *
 * **Patterns follow the device until told otherwise.** A chart draws its
 * series' patterns at the high contrast tier and where the device asks to
 * differentiate without colour; the high contrast row here is the gallery's
 * own, so turning it on shows every chart in the app take its patterns.
 */
@Composable
internal fun EnergySettingsPage() {
    val prefs = LocalEnergyPrefs.current
    val settings = LocalAppSettings.current
    val collapse = rememberTopBarCollapseState()
    // Outside the gallery there is nothing to bind to, so the row keeps its own.
    var ownContrast by remember { mutableStateOf(false) }
    val contrast = settings?.highContrast ?: ownContrast
    val withoutColour = differentiateWithoutColour()

    Column(Modifier.fillMaxSize().topBarCollapse(collapse)) {
        TopBar(style = TopBarStyle.Large, navigation = rootNavigation(), collapseState = collapse) { +"Settings" }
        Column(
            Modifier.fillMaxSize().pageScroll().padding(energyPagePadding()).testTag("energy-settings"),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            EnergyHeading("Tariff", supporting = "Dollars a kilowatt-hour. The bills report draws its prices from these.")
            ListGroup {
                field(prefs.peak, label = "Peak", icon = Tabler.Outline.Flame, keyboardType = KeyboardType.Decimal, trailing = { UnitText("$/kWh") })
                field(prefs.offPeak, label = "Off-peak", icon = Tabler.Outline.Moon, keyboardType = KeyboardType.Decimal, trailing = { UnitText("$/kWh") })
                field(prefs.feedIn, label = "Feed-in", icon = Tabler.Outline.Plug, keyboardType = KeyboardType.Decimal, trailing = { UnitText("$/kWh") })
                field(
                    prefs.supply,
                    label = "Supply",
                    icon = Tabler.Outline.Coin,
                    supporting = "A day, however little is used",
                    keyboardType = KeyboardType.Decimal,
                    trailing = { UnitText("$") },
                )
            }

            EnergyHeading("The system")
            ListGroup {
                field(prefs.roof, label = "Roof", icon = Tabler.Outline.SolarPanel, keyboardType = KeyboardType.Decimal, trailing = { UnitText("kW") })
                field(prefs.battery, label = "Battery", icon = Tabler.Outline.BatteryCharging, keyboardType = KeyboardType.Decimal, trailing = { UnitText("kWh") })
            }

            EnergyHeading(
                "Patterns on charts",
                supporting = when {
                    withoutColour -> "On now: this device asks to tell things apart without colour."
                    contrast -> "On now, with high contrast."
                    else -> "A second cue beside each series' colour. Like the device, they come on with high " +
                        "contrast or when it asks to tell things apart without colour."
                },
            )
            SegmentedControl(
                value = prefs.patterns,
                options = PatternChoice.entries,
                onValueChange = { prefs.patterns = it },
                optionLabel = { it.label },
                modifier = Modifier.fillMaxWidth().testTag("energy-patterns"),
            )
            Patterns()
            ListGroup {
                item(
                    "High contrast",
                    supporting = "The gallery's own, from inside the app",
                    role = Role.Switch,
                    trailing = { Switch(checked = contrast, onCheckedChange = null) },
                    onClick = { if (settings != null) settings.highContrast = !contrast else ownContrast = !contrast },
                )
                item(
                    "Live meter",
                    supporting = "Today's chart reads the meter every second",
                    role = Role.Switch,
                    trailing = { Switch(checked = prefs.live, onCheckedChange = null) },
                    onClick = { prefs.live = !prefs.live },
                )
            }
        }
    }
}

@Composable
private fun UnitText(text: String) {
    Text(text, style = Theme.typography.bodyMedium, colour = Theme.colours.contentMuted)
}

/**
 * The patterns a chart's series wear, in the order they take them, each over a
 * swatch with `Modifier.fillPattern`: what any surface of the app's own can
 * draw as well. Then a few made to order — lines further apart and heavier,
 * dots in staggered rows, and a tile of the app's own, a little sun.
 */
@Composable
private fun Patterns() {
    val ink = Theme.colours.content
    val ground = Theme.colours.surfaceSunken
    val shape = Theme.shapes.extraSmall
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            for (pattern in FillPatternDefaults.Series.filter { !it.isSolid }) PatternSwatch(pattern, ink, ground, shape)
        }
        Text("Made to order", style = Theme.typography.labelMedium, colour = Theme.colours.contentMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            for (pattern in madeToOrder) PatternSwatch(pattern, ink, ground, shape)
        }
    }
}

@Composable
private fun PatternSwatch(pattern: FillPattern, ink: Color, ground: Color, shape: Shape) {
    Box(
        Modifier
            .size(PatternSwatchSize)
            .clip(shape)
            .background(ground)
            .fillPattern(pattern, ink = ink, shape = shape),
    )
}

/** Patterns of the app's own: opened out, weighted, staggered and drawn. */
private val madeToOrder = listOf(
    FillPattern.lines(PatternDirection.Rising, spacing = 14.dp, weight = 3.dp),
    FillPattern.lines(PatternDirection.Horizontal, PatternDirection.Rising, spacing = 12.dp),
    FillPattern.dots(spacing = 9.dp, size = 4.dp, staggered = true),
    FillPattern.lines(PatternDirection.Vertical, spacing = 5.dp, weight = 1.dp),
    // A little sun: a dot with four rays, the app's own mark.
    FillPattern.custom(DpSize(16.dp, 16.dp), key = "energy-sun") { ink ->
        val middle = Offset(size.width / 2, size.height / 2)
        val ray = size.minDimension * 0.42f
        val stroke = size.minDimension * 0.08f
        drawCircle(ink, radius = size.minDimension * 0.16f, center = middle)
        drawLine(ink, middle - Offset(ray, 0f), middle + Offset(ray, 0f), strokeWidth = stroke)
        drawLine(ink, middle - Offset(0f, ray), middle + Offset(0f, ray), strokeWidth = stroke)
    },
)

private val PatternSwatchSize = 40.dp
