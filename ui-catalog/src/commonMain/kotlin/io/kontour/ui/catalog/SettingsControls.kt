package io.kontour.ui.catalog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.demo.theme.demoThemes

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
