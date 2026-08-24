package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.display.TagTone
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.sheet.SideSheet
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.sheet.sheetPeekAnchor
import io.kontour.ui.theme.Theme

/** Bottom sheets at each detent, plus the modal and side variants. */
@Composable
fun SheetShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Theme.colors.background) {
        Panels {
            SheetPanel("Peeking over a map") {
                val sheet = rememberSheetState(
                    detents = listOf(
                        SheetDetent.Hidden,
                        SheetDetent.peek(140.dp),
                        SheetDetent.Half,
                        SheetDetent.Expanded,
                    ),
                    initialDetent = SheetDetent.Hidden,
                )
                LaunchedEffect(Unit) { sheet.animateTo(SheetDetent.peek(140.dp)) }

                MapStandIn()
                BottomSheet(
                    state = sheet,
                    actions = {
                        IconButton(
                            icon = Tabler.Outline.CurrentLocation,
                            contentDescription = "Recentre",
                            onClick = tap("Recentre"),
                            variant = ButtonVariant.Secondary,
                        )
                    },
                ) { StopSheetBody(peekAnchored = true) }
            }

            SheetPanel("Half open") {
                val sheet = rememberSheetState(
                    detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Full),
                    initialDetent = SheetDetent.Hidden,
                )
                LaunchedEffect(Unit) { sheet.animateTo(SheetDetent.Half) }

                MapStandIn()
                BottomSheet(sheet) { StopSheetBody(peekAnchored = false) }
            }

            SheetPanel("Modal") {
                // Seeded from the literal these used to be, so the goldens still
                // catch them open and the scrim, the close button and the swipe
                // all actually work. A dead `onDismissRequest` on a *controlled*
                // component is not an inert demo — it is a sheet that refuses to
                // close, which is what it looked like.
                val modal = seed(true)
                ModalBottomSheet(
                    visible = modal.value,
                    onDismissRequest = { modal.value = false },
                ) {
                    SheetHeader() {
                        +"Rename favourite"
                        supporting { +"Perth Underground" }
                    }
                    Column(
                        modifier = Modifier.padding(
                            start = Theme.spacing.md,
                            end = Theme.spacing.md,
                            bottom = Theme.spacing.lg,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                    ) {
                        Text(
                            "Give it a name you will recognise on the home screen.",
                            style = Theme.typography.bodySmall,
                            color = Theme.colors.contentMuted,
                        )
                        Button(
                            onClick = tap("Save"),
                            modifier = Modifier.fillMaxWidth(),
                        ) { +"Save" }
                        Button(
                            onClick = tap("Cancel"),
                            modifier = Modifier.fillMaxWidth(),
                            variant = ButtonVariant.Ghost,
                        ) {
                            +"Cancel"
                        }
                    }
                }
            }

            SheetPanel("Side sheet") {
                val side = seed(true)
                SideSheet(
                    visible = side.value,
                    onDismissRequest = { side.value = false },
                    // Start side and a back arrow: both were reachable and
                    // neither was ever rendered, which is how `SheetSide.Start`
                    // went unconfirmed for as long as it did.
                    side = SheetSide.Start,
                    width = 260.dp,
                    onBack = { side.value = false },
                ) {
                    SheetHeader() {
                        +"Filters"
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = Theme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    ) {
                        Text("Only show routes that", style = Theme.typography.labelMedium)
                        Text(
                            "run in the next hour",
                            style = Theme.typography.bodySmall,
                            color = Theme.colors.contentMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StopSheetBody(peekAnchored: Boolean) {
    SheetHeader(
        modifier = if (peekAnchored) Modifier.sheetPeekAnchor() else Modifier,
        actions = {
            IconButton(Tabler.Outline.Star, "Add to favourites", onClick = tap("Favourite"))
        },
    ) {
        +"Perth Underground"
        supporting { +"Platform 2 · Joondalup line" }
    }
    Column(
        modifier = Modifier.padding(
            start = Theme.spacing.md,
            end = Theme.spacing.md,
            bottom = Theme.spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        repeat(6) { index ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(tone = if (index == 1) TagTone.Warning else TagTone.Neutral) {
                    +Tabler.Outline.Bus
                    +"${950 + index}"
                }
                Column(Modifier.weight(1f)) {
                    Text("Elizabeth Quay", style = Theme.typography.bodyMedium)
                    Text(
                        if (index == 1) "3 min late" else "on time",
                        style = Theme.typography.bodySmall,
                        color = Theme.colors.contentMuted,
                    )
                }
                Text("${4 + index * 7} min", style = Theme.typography.titleSmall)
            }
        }
    }
}

/** Stands in for the map behind a non-modal sheet. */
@Composable
private fun MapStandIn() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.colors.surfaceSunken)
    ) {
        Text(
            text = "map",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = Theme.spacing.lg),
            style = Theme.typography.monoLabel,
            color = Theme.colors.contentSubtle,
        )
    }
}

@Composable
private fun SheetPanel(title: String, content: @Composable () -> Unit) {
    Panel(width = 340.dp, spacing = Theme.spacing.xs) {
        Text(
            text = title.uppercase(),
            style = Theme.typography.monoLabel,
            color = Theme.colors.accent.solid,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .border(
                    width = Theme.sizing.borderWidth,
                    color = Theme.colors.outline,
                    shape = Theme.shapes.medium,
                )
                .clip(Theme.shapes.medium),
            color = Theme.colors.surface,
        ) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}
