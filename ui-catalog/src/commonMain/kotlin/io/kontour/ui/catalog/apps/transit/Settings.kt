package io.kontour.ui.catalog.apps.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.kontour.ui.catalog.apps.LocalAppSettings
import io.kontour.ui.catalog.apps.rememberAppToast
import io.kontour.ui.components.list.ListSection
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.components.text.WheelSelect
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * The app's settings: how it looks, which are the gallery's own switches, and
 * how it plans, which are the app's.
 *
 * **The appearance rows change the gallery.** Dark, high contrast and reduce
 * motion here are the same settings as the gallery's display panel, so
 * turning one on in the app is seeing the whole app redraw under it — and it
 * stays on when you leave, as a phone's own setting would.
 */
@Composable
internal fun TransitSettingsPage() {
    val toast = rememberAppToast()
    val settings = LocalAppSettings.current
    val collapse = rememberTopBarCollapseState()
    var walk by remember { mutableFloatStateOf(WalkDefault) }
    var miles by remember { mutableIntStateOf(0) }
    var home by remember { mutableStateOf("Glendalough") }
    var alerts by remember { mutableStateOf(true) }
    // Outside the gallery there is nothing to bind to, so the rows keep their own.
    var ownDark by remember { mutableStateOf<Boolean?>(null) }
    var ownContrast by remember { mutableStateOf(false) }
    var ownMotion by remember { mutableStateOf(false) }

    val dark = if (settings != null) settings.dark else ownDark
    val contrast = settings?.highContrast ?: ownContrast
    val motion = settings?.reduceMotion ?: ownMotion

    Column(Modifier.fillMaxSize().nestedScroll(collapse.nestedScrollConnection)) {
        TopBar(style = TopBarStyle.Large, navigation = rootNavigation(), collapseState = collapse) {
            +"Settings"
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(transitPagePadding()).testTag("settings"),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            ListSection(
                title = { +"Appearance" },
                supporting = { +"The gallery's own settings, from inside the app" },
            ) {
                SegmentedControl(
                    options = listOf("System", "Light", "Dark"),
                    selectedIndex = when (dark) {
                        null -> 0
                        false -> 1
                        true -> 2
                    },
                    onSelectedIndexChange = { i ->
                        val chosen = when (i) {
                            0 -> null
                            1 -> false
                            else -> true
                        }
                        if (settings != null) settings.dark = chosen else ownDark = chosen
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = Theme.spacing.sm),
                )
                SelectionRow(
                    selected = contrast,
                    onSelectedChange = { if (settings != null) settings.highContrast = it else ownContrast = it },
                    role = Role.Switch,
                    position = GroupPosition.First,
                ) {
                    +"High contrast"
                    supporting { +"Stronger edges and ink" }
                    trailing { Switch(checked = contrast, onCheckedChange = null) }
                }
                SelectionRow(
                    selected = motion,
                    onSelectedChange = { if (settings != null) settings.reduceMotion = it else ownMotion = it },
                    role = Role.Switch,
                    position = GroupPosition.Last,
                ) {
                    +"Reduce motion"
                    supporting { +"Fades instead of slides" }
                    trailing { Switch(checked = motion, onCheckedChange = null) }
                }
            }

            ListSection(
                title = { +"Planning" },
                footer = { +"Used to work out how long the walks between stops take." },
            ) {
                Text("Walking speed: ${walk.roundToInt()} km/h", style = Theme.typography.bodyMedium)
                Slider(
                    value = walk,
                    onValueChange = { walk = it },
                    valueRange = 2f..7f,
                    steps = 4,
                    showTicks = true,
                    stateDescription = { "${it.roundToInt()} kilometres an hour" },
                )
                SegmentedControl(
                    options = listOf("Kilometres", "Miles"),
                    selectedIndex = miles,
                    onSelectedIndexChange = { miles = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = Theme.spacing.sm),
                )
                WheelSelect(
                    value = home,
                    options = stops.map { it.name },
                    onValueChange = { home = it },
                    // Once the wheel has stopped, not for every stop it turned
                    // past on the way.
                    onValueChangeFinished = { toast("Home is $home") },
                    label = "Home stop",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ListSection(title = { +"Alerts" }) {
                SelectionRow(
                    selected = alerts,
                    onSelectedChange = {
                        alerts = it
                        toast(if (it) "We'll tell you about delays on your lines" else "Alerts are off")
                    },
                    role = Role.Switch,
                    position = GroupPosition.Only,
                ) {
                    +"Service alerts"
                    supporting { +"Delays and changes on the lines you use" }
                    trailing { Switch(checked = alerts, onCheckedChange = null) }
                }
            }
        }
    }
}

private const val WalkDefault = 5f
