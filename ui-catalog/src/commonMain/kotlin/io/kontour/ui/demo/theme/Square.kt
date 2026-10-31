package io.kontour.ui.demo.theme

import io.kontour.ui.theme.kontourColourScheme
import io.kontour.ui.theme.squareShapes

/**
 * The library's own colours with every corner square and every line ending
 * flat: what an app asking for a "square" theme gets from one call.
 *
 * Added for 0.5.2, when a theme could first reach every corner the library
 * draws. Before it, a square theme had square buttons and cards beside round
 * progress bars, slider tracks, page dots and timeline stops, rounded
 * checkboxes, and grouped rows whose insides were rounder than their outsides
 * — *"I'm able to make some things square, but not all things"*. Its goldens,
 * `theme-square-*`, are the record that it now reaches all of them.
 *
 * A radio button is still round. That is the one exception, and on purpose:
 * round is how a radio button says it is not a checkbox.
 */
val squareDemoTheme = DemoTheme(
    name = "Square",
    colours = { dark, contrast -> kontourColourScheme(dark, contrast) },
    shapes = squareShapes(),
)
