package io.kontour.ui.catalog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.demo.theme.demoThemes
import io.kontour.ui.foundation.Text
import io.kontour.ui.platform.platformPrefersHighContrast
import io.kontour.ui.platform.platformPrefersReducedMotion
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.Theme

/**
 * The controls the gallery's sheet and the site's popover both draw.
 *
 * They lay out differently — a bottom sheet and a masthead popover want
 * different spacing — but the *policy* below them is one thing and is written
 * once here. In particular, which switches a theme has taken away.
 */

/** The theme picker. One line per theme in `demoThemes`; no registration. */
@Composable
fun ThemePicker(settings: CatalogSettings, modifier: Modifier = Modifier) {
    SegmentedControl(
        options = demoThemes.map { it.name },
        selected = demoThemes.indexOf(settings.theme).coerceAtLeast(0),
        onSelectedChange = { settings.theme = demoThemes[it] },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A switch row, drawn disabled when the current theme has no opinion to offer.
 *
 * **Disabled and showing the resolved value, rather than hidden.** A dark-only
 * theme cannot honour a request for light, and there are three things a surface
 * could do about that: draw the switch live and ignore it, take it away, or draw
 * it inert showing what the reader is actually getting. The first is a lie; the
 * second makes the control appear and disappear as you change theme, which reads
 * as a bug. The third is what this does.
 *
 * The reader's own preference is never overwritten — it is stored on
 * [CatalogSettings] and resolved at the point of use — so leaving a theme that
 * had taken the switch away restores the choice they made before.
 */
@Composable
fun SettingToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SelectionRow(
        selected = checked,
        onSelectedChange = onCheckedChange,
        role = Role.Switch,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        +label
        trailing { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    }
}

/**
 * Every display control, in the order both surfaces draw them.
 *
 * **One function, because the two surfaces had the same bug twice.** The
 * gallery's sheet and the site's popover each spelled this list out, which is
 * how they came to differ in the order of two rows — and, less visibly, how the
 * font-scale defect in `hostDensity` existed in both `Catalog` and `Site` in
 * identical words. A list written once cannot drift from itself.
 *
 * Emitted as siblings rather than wrapped in a `Column` of its own: a sheet and
 * a popover want different spacing round them, and that is the caller's to
 * decide. The caller supplies the column; this supplies what goes in it.
 *
 * ### What "Follow device" is for
 *
 * Four of these settings are the *device's* until the reader says otherwise, and
 * before this round there was no way to say otherwise and then take it back. The
 * fields are nullable and the switches were plain two-state `Switch`es, so the
 * first tap on any of them pinned that field for the life of the process: the
 * platform hooks underneath went on reporting the phone's changes to nobody. The
 * report was "the accessibility controls don't react to the device's settings",
 * and the controls did — right up until the first tap.
 *
 * Each row still pins only itself, so forcing dark while contrast goes on
 * following the phone is expressible. This row is the statement about all four
 * at once: it reads whether they are all still Auto, and it is the gesture that
 * puts them back.
 */
@Composable
fun DisplaySettingsControls(
    settings: CatalogSettings,
    systemDark: Boolean,
    showFrameTimes: Boolean = false,
) {
    // Read here as well as at the theme, so a control the reader has not touched
    // shows what they are actually getting rather than what the host would
    // default to on its own.
    val systemHighContrast = platformPrefersHighContrast()
    val systemReduceMotion = platformPrefersReducedMotion()
    // The *installed* font scale, which is the device's exactly when all four
    // settings are still Auto — and that is the only state the one read of it
    // below is reachable from, since `followsDevice` is false whenever any of
    // them is pinned. Reading it here rather than threading the raw platform
    // density down from the host keeps the host's job to one line.
    val deviceFontScale = LocalDensity.current.fontScale

    val theme = settings.theme
    val dark = settings.dark ?: systemDark
    val highContrast = settings.highContrast ?: systemHighContrast
    val reduceMotion = settings.reduceMotion ?: systemReduceMotion

    Text("Theme", style = Theme.typography.labelMedium)
    ThemePicker(settings)

    SettingToggle("Follow device", settings.followsDevice) { follow ->
        if (follow) {
            settings.followDevice()
        } else {
            // Pinned at what the device is giving right now, so leaving Auto
            // changes nothing on screen. A switch that repainted the app would
            // read as a setting of its own rather than as a statement about
            // where the other four are coming from.
            settings.pinToDevice(dark, highContrast, reduceMotion, deviceFontScale)
        }
    }

    // The three platform-backed switches show the *resolved* value, so one the
    // reader has not touched reads as what they are actually getting rather
    // than as the host's own preference — and the first two go inert under a
    // theme that offers only one mode or tier.
    SettingToggle(
        "Dark",
        theme.resolveDark(dark),
        enabled = theme.offersBothModes,
    ) { settings.dark = it }
    SettingToggle(
        "High contrast",
        theme.resolveTier(
            if (highContrast) ContrastLevel.High else ContrastLevel.Standard
        ) == ContrastLevel.High,
        enabled = theme.offersBothTiers,
    ) { settings.highContrast = it }
    SettingToggle("Reduce motion", reduceMotion) { settings.reduceMotion = it }
    SettingToggle("Right to left", settings.rightToLeft) { settings.rightToLeft = it }
    if (showFrameTimes) {
        SettingToggle("Frame times", settings.frameTimes) { settings.frameTimes = it }
    }

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

/**
 * The scales worth offering, "Auto" first.
 *
 * Auto is null and is the default — the device's own setting, which is what a
 * phone's accessibility slider moves. The four after it are absolute rather than
 * multipliers of it, because 200% is the figure the accessibility page promises
 * the library copes at, and that promise is uncheckable if picking 200% on a
 * phone already at 130% gives you 260%.
 */
val textScales: List<Pair<String, Float?>> = listOf(
    "Auto" to null,
    "85%" to 0.85f,
    "100%" to 1f,
    "130%" to 1.3f,
    "200%" to 2f,
)
