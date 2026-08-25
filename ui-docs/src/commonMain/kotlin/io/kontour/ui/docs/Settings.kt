package io.kontour.ui.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * The display switches, and where they are kept.
 *
 * These exist on the site for a reason no other section of the documentation
 * can serve: contrast, text size, right-to-left and reduced motion are things a
 * reader cannot check by reading. A page can *say* that every component copes at
 * 200% type; the only way to answer it is to set the type to 200% and look, and
 * until now that required changing an operating-system setting and reloading.
 *
 * The gallery has had these since Round 13, in a bottom sheet behind a toolbar
 * icon — one route away from every component page and reachable from none of
 * them.
 */
@Stable
class DisplaySettings {
    /**
     * Starts from the system rather than at light.
     *
     * `KontourTheme` already defaults `darkTheme` to `isSystemInDarkTheme()`; the
     * site overrode it with `false`, so a reader in dark mode got the boot screen
     * painted `#121212` by the CSS and then a white site over the top of it.
     */
    var dark by mutableStateOf<Boolean?>(null)
    var highContrast by mutableStateOf(false)
    var reduceMotion by mutableStateOf(false)
    var rightToLeft by mutableStateOf(false)
    var textScale by mutableStateOf(1f)
}

@Composable
fun rememberDisplaySettings(): DisplaySettings = remember { DisplaySettings() }

/** The scales worth offering. 200% is the one the accessibility page promises. */
internal val textScales = listOf("85%" to 0.85f, "100%" to 1f, "130%" to 1.3f, "200%" to 2f)

@Composable
internal fun SettingsPanel(settings: DisplaySettings, systemDark: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Toggle("Dark", settings.dark ?: systemDark) { settings.dark = it }
        Toggle("High contrast", settings.highContrast) { settings.highContrast = it }
        Toggle("Reduce motion", settings.reduceMotion) { settings.reduceMotion = it }
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
