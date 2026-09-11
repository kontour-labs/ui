package io.kontour.ui.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.kontour.ui.catalog.CatalogSettings
import io.kontour.ui.catalog.DisplaySettingsControls
import io.kontour.ui.theme.Theme

/**
 * The display switches the masthead's popover draws.
 *
 * These exist on the site for a reason no other section of the documentation can
 * serve: contrast, text size, right-to-left and reduced motion are things a
 * reader cannot check by reading. A page can *say* that every component copes at
 * 200% type; the only way to answer it is to set the type to 200% and look.
 *
 * **The state itself is [CatalogSettings], which lives in `:ui-catalog`.** It
 * used to be a `DisplaySettings` of the site's own, and the gallery had a second
 * copy — so `#/gallery`, a route on this site, was a boundary where the
 * masthead's switches stopped meaning anything. One object, read by both themes.
 *
 * **And so are the controls now.** This function used to spell the rows out, and
 * so did the gallery's sheet: the two lists drifted into a different order, and
 * the font-scale defect that made every one of these switches ignore the device
 * lived in `Catalog` and `Site` in identical words. `DisplaySettingsControls` is
 * that list, written once. What is left here is the popover's own shape.
 */
@Composable
internal fun SettingsPanel(settings: CatalogSettings, systemDark: Boolean) {
    // No padding of its own: the only caller is a `Popover`, and `PopoverPanel`
    // already wraps its content in a `Column` with exactly this padding and this
    // arrangement. Padding twice spent 32 of the popover's 320dp cap on nothing,
    // which is 8dp off every segment of the two `SegmentedControl`s below — the
    // difference between "Keyboard" fitting and arriving as "Keyboar".
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        DisplaySettingsControls(settings, systemDark)
    }
}
