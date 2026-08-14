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
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.ListSection
import io.kontour.ui.components.list.LoadMore
import io.kontour.ui.components.list.LoadMoreState
import io.kontour.ui.components.list.SettingRow
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

private val stops = listOf(
    "Perth Underground" to "Platform 2 · Joondalup line",
    "Elizabeth Quay" to "Platform 1 · Mandurah line",
    "Perth Busport" to "Stand 24 · Route 950",
    "McIver" to "Platform 1 · Midland line",
)

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
                    val positions = listPositions(stops.size)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        stops.forEachIndexed { index, (name, detail) ->
                            ListItem(
                                headline = name,
                                supporting = detail,
                                leading = {
                                    Icon(
                                        Tabler.Outline.Bus,
                                        contentDescription = null,
                                        size = Theme.sizing.iconLarge,
                                    )
                                },
                                trailing = {
                                    Tag("${4 + index * 6} min", tone = TagTone.Neutral)
                                },
                                position = positions[index],
                                selected = index == 1,
                                onClick = {},
                            )
                        }
                    }
                }

                Section("A single row") {
                    // The case a three-item example never exercises: all four
                    // corners round, not just the outside ones.
                    ListItem(
                        headline = "Perth Underground",
                        supporting = "Only stop on this route",
                        position = ListItemPosition.Only,
                        onClick = {},
                    )
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
                    ) {
                        SettingRow(
                            label = "Theme",
                            value = "Match system",
                            icon = Tabler.Outline.Moon,
                            position = ListItemPosition.First,
                            onClick = {},
                        )
                        SettingRow(
                            label = "Accent colour",
                            value = "Anyways",
                            icon = Tabler.Outline.Palette,
                            position = ListItemPosition.Middle,
                            onClick = {},
                        )
                        var notify by remember { mutableStateOf(true) }
                        SettingRow(
                            label = "Delay alerts",
                            supporting = "Only for favourited routes",
                            icon = Tabler.Outline.Bell,
                            position = ListItemPosition.Last,
                            onClick = { notify = !notify },
                            trailing = {
                                Switch(checked = notify, onCheckedChange = null)
                            },
                        )
                    }
                }

                Section("Loading more") {
                    LoadMore(state = LoadMoreState.Loading, onLoadMore = {})
                    LoadMore(
                        state = LoadMoreState.Error,
                        onLoadMore = {},
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
                                onAction = {},
                                background = Theme.colors.danger.solid,
                                isFullSwipeAction = true,
                            ),
                        ),
                        start = listOf(
                            SwipeAction(
                                label = "Favourite",
                                icon = Tabler.Outline.Star,
                                onAction = {},
                                background = Theme.colors.success.solid,
                            ),
                        ),
                    ) {
                        ListItem(
                            headline = "Perth Busport",
                            supporting = "Swipe either way",
                            onClick = {},
                        )
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
                            repeat(10) { index ->
                                ListItem(
                                    headline = "Departure ${index + 1}",
                                    supporting = "Elizabeth Quay",
                                    position = ListItemPosition.of(index, 10),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
