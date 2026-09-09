package io.kontour.ui.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import io.kontour.ui.platform.platformPrefersHighContrast
import io.kontour.ui.platform.platformPrefersReducedMotion
import androidx.compose.ui.Modifier
import io.kontour.ui.catalog.CatalogSettings
import io.kontour.ui.catalog.SettingToggle
import io.kontour.ui.catalog.ThemePicker
import io.kontour.ui.catalog.inputModalities
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.ContrastLevel
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
    // No padding of its own: the only caller is a `Popover`, and `PopoverPanel`
    // already wraps its content in a `Column` with exactly this padding and this
    // arrangement. Padding twice spent 32 of the popover's 320dp cap on nothing,
    // which is 8dp off every segment of the two `SegmentedControl`s below — the
    // difference between "Keyboard" fitting and arriving as "Keyboar".
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Text("Theme", style = Theme.typography.labelMedium)
        ThemePicker(settings)

        // A theme may decline a mode or a tier — GTurbo is near-black and offers
        // only dark. Its switch is drawn inert showing the resolved value rather
        // than live-and-ignored, and the reader's own preference underneath is
        // untouched, so leaving the theme restores it. `SettingToggle` carries
        // that policy so it is not written twice.
        val theme = settings.theme
        SettingToggle(
            "Dark",
            theme.resolveDark(settings.dark ?: systemDark),
            enabled = theme.offersBothModes,
        ) { settings.dark = it }
        SettingToggle(
            "High contrast",
            theme.resolveTier(
                if (settings.highContrast ?: systemHighContrast) ContrastLevel.High
                else ContrastLevel.Standard
            ) == ContrastLevel.High,
            enabled = theme.offersBothTiers,
        ) { settings.highContrast = it }
        SettingToggle("Reduce motion", settings.reduceMotion ?: systemReduceMotion) {
            settings.reduceMotion = it
        }
        SettingToggle("Right to left", settings.rightToLeft) { settings.rightToLeft = it }

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
