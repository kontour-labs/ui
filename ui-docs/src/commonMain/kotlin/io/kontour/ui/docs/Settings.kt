package io.kontour.ui.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import io.kontour.ui.platform.platformPrefersHighContrast
import io.kontour.ui.platform.platformPrefersReducedMotion
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.kontour.ui.catalog.CatalogSettings
import io.kontour.ui.catalog.inputModalities
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/** The scales worth offering. 200% is the one the accessibility page promises. */
internal val textScales = listOf("85%" to 0.85f, "100%" to 1f, "130%" to 1.3f, "200%" to 2f)

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
 */
@Composable
internal fun SettingsPanel(settings: CatalogSettings, systemDark: Boolean) {
    // Read here as well as at the theme, so a toggle a reader has not touched
    // shows what they are actually getting rather than what the site would
    // default to on its own.
    val systemReduceMotion = platformPrefersReducedMotion()
    val systemHighContrast = platformPrefersHighContrast()
    Column(
        modifier = Modifier.fillMaxWidth().padding(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Toggle("Dark", settings.dark ?: systemDark) { settings.dark = it }
        Toggle("High contrast", settings.highContrast ?: systemHighContrast) {
            settings.highContrast = it
        }
        Toggle("Reduce motion", settings.reduceMotion ?: systemReduceMotion) {
            settings.reduceMotion = it
        }
        Toggle("Right to left", settings.rightToLeft) { settings.rightToLeft = it }

        Text(
            text = "Text size",
            style = Theme.typography.labelMedium,
            modifier = Modifier.padding(top = Theme.spacing.sm),
        )
        SegmentedControl(
            options = textScales.map { it.first },
            selected = textScales.indexOfFirst { it.second == settings.textScale }
                .coerceAtLeast(0),
            onSelectedChange = { settings.textScale = textScales[it].second },
            modifier = Modifier.fillMaxWidth(),
        )

        // The gallery has offered this since Round 13 and the site has not,
        // which is the wrong way round: hover and focus-visible styling is
        // exactly what a reader of a component's page wants to check without
        // owning the hardware that produces it.
        Text(
            text = "Input modality",
            style = Theme.typography.labelMedium,
            modifier = Modifier.padding(top = Theme.spacing.sm),
        )
        SegmentedControl(
            options = inputModalities.map { it.first },
            selected = inputModalities.indexOfFirst { it.second == settings.modality }
                .coerceAtLeast(0),
            onSelectedChange = { settings.modality = inputModalities[it].second },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


@Composable
private fun Toggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SelectionRow(
        selected = checked,
        onSelectedChange = onCheckedChange,
        role = Role.Switch,
        modifier = Modifier.fillMaxWidth(),
    ) {
        +label
        trailing { Switch(checked = checked, onCheckedChange = null) }
    }
}
