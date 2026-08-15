package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Moon
import com.composables.icons.tabler.outline.Palette
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.display.TagTone
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.ListSection
import io.kontour.ui.components.list.LoadMore
import io.kontour.ui.components.list.LoadMoreState
import io.kontour.ui.components.list.SettingRow
import io.kontour.ui.components.list.settingValue
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import io.kontour.ui.components.list.fadingEdges
import io.kontour.ui.components.list.listPositions
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay

private val stops = listOf(
    "Perth Underground" to "Platform 2 · Joondalup line",
    "Elizabeth Quay" to "Platform 1 · Mandurah line",
    "Perth Busport" to "Stand 24 · Route 950",
    "McIver" to "Platform 1 · Midland line",
)

private val themes = listOf("Match system", "Always light", "Always dark")
private val accents = listOf("Anyways", "Transperth", "Monochrome")

/** List items, sections, swipe actions and the scroll affordances. */
@Composable
fun ListShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Theme.colors.background) {
        Row(
            modifier = Modifier.padding(Theme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            Column(
                modifier = Modifier.width(380.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("Grouped rows") {
                    // Written with the DSL, and the golden for this panel did not
                    // move — which is the claim the shorthands make: the same
                    // components, with the index arithmetic deleted rather than
                    // rewritten at every call site.
                    val current = seed(1)
                    ListGroup(spacing = 2.dp) {
                        stops.forEachIndexed { index, (name, detail) ->
                            item(
                                label = name,
                                supporting = detail,
                                icon = Tabler.Outline.Bus,
                                selected = index == current.value,
                                trailing = {
                                    Tag(tone = TagTone.Neutral) { +"${4 + index * 6} min" }
                                },
                                onClick = { current.value = index },
                            )
                        }
                    }
                }

                Section("A single row") {
                    // The case a three-item example never exercises: all four
                    // corners round, not just the outside ones.
                    ListItem(
                        position = ListItemPosition.Only,
                        onClick = tap("Perth Underground"),
                    ) {
                        +"Perth Underground"
                        supporting { +"Only stop on this route" }
                    }
                }
            }

            Column(
                modifier = Modifier.width(380.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("Sections and settings") {
                    ListSection(
                        title = "Appearance",
                        description = "How the app looks on this device",
                        footer = "Delay alerts use your last-known location and " +
                            "stop when you close the app.",
                    ) {
                        // A setting row's whole job is to open something and
                        // come back with a new value. Cycling through the values
                        // in place is the shortest thing that shows that
                        // happening without a picker in the way.
                        val theme = seed(0)
                        val accent = seed(0)

                        SettingRow(
                            position = ListItemPosition.First,
                            onClick = { theme.value = (theme.value + 1) % themes.size },
                        ) {
                            +"Theme"
                            leading { +Tabler.Outline.Moon }
                            trailing { settingValue(themes[theme.value]) }
                        }
                        SettingRow(
                            position = ListItemPosition.Middle,
                            onClick = { accent.value = (accent.value + 1) % accents.size },
                        ) {
                            +"Accent colour"
                            leading { +Tabler.Outline.Palette }
                            trailing { settingValue(accents[accent.value]) }
                        }
                        var notify by remember { mutableStateOf(true) }
                        SettingRow(
                            position = ListItemPosition.Last,
                            onClick = { notify = !notify },
                        ) {
                            +"Delay alerts"
                            supporting { +"Only for favourited routes" }
                            leading { +Tabler.Outline.Bell }
                            trailing { Switch(checked = notify, onCheckedChange = null) }
                        }
                    }
                }

                Section("Loading more") {
                    // The spinner is the exhibit, and a request in flight is
                    // supposed to swallow a second tap — the one control on this
                    // page that is inert on purpose.
                    LoadMore(state = LoadMoreState.Loading, onLoadMore = {})

                    // The retry actually retries, and fails again a moment later,
                    // so the round trip can be watched rather than described.
                    val more = seed(LoadMoreState.Error)
                    LaunchedEffect(more.value) {
                        if (more.value == LoadMoreState.Loading) {
                            delay(1_200)
                            more.value = LoadMoreState.Error
                        }
                    }
                    LoadMore(
                        state = more.value,
                        onLoadMore = { more.value = LoadMoreState.Loading },
                        errorLabel = "Couldn't load more departures",
                    )
                }
            }

            Column(
                modifier = Modifier.width(380.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("Swipe actions, revealed") {
                    val swipe = rememberSwipeActionsState()
                    // Held open so the golden shows them; in use a drag reveals
                    // them and a full swipe fires the destructive one outright.
                    LaunchedEffect(Unit) { swipe.animateTo(SwipeValue.End) }
                    SwipeActions(
                        state = swipe,
                        end = listOf(
                            SwipeAction(
                                label = "Remove",
                                icon = Tabler.Outline.Trash,
                                onAction = tap("Remove"),
                                background = Theme.colors.danger.solid,
                                isFullSwipeAction = true,
                            ),
                        ),
                        start = listOf(
                            SwipeAction(
                                label = "Favourite",
                                icon = Tabler.Outline.Star,
                                onAction = tap("Favourite"),
                                background = Theme.colors.success.solid,
                            ),
                        ),
                    ) {
                        ListItem(onClick = tap("Perth Busport")) {
                            +"Perth Busport"
                            supporting { +"Swipe either way" }
                        }
                    }
                }

                Section("Fading edges") {
                    val scroll = rememberScrollState()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fadingEdges(scroll)
                                .verticalScroll(scroll),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ListGroup(spacing = 2.dp) {
                                repeat(10) { index ->
                                    item(
                                        label = "Departure ${index + 1}",
                                        supporting = "Elizabeth Quay",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
