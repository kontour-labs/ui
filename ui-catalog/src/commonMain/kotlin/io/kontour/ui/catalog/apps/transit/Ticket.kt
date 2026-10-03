package io.kontour.ui.catalog.apps.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.kontour.ui.catalog.apps.rememberAppToast
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CircularStepProgress
import io.kontour.ui.components.display.Gauge
import io.kontour.ui.components.display.GaugeIndicator
import io.kontour.ui.components.display.GaugeTickPlacement
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.pageBack
import io.kontour.ui.nav.TopBar
import io.kontour.ui.overlay.AlertDialog
import io.kontour.ui.overlay.LoadingOverlay
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The ticket, over the navigation: a page pushed with `coversNavigation`, so
 * it slides in over the bar as well as the page, and back uncovers both.
 *
 * The balance is a gauge rather than a number alone, because the question it
 * answers is "is that a lot?"; the ring beside it counts today's paid trips
 * towards the daily cap, after which the rest of the day is free. Topping up
 * asks first, works for a moment behind a loading overlay, and says so when it
 * is done.
 */
@Composable
internal fun TicketPage() {
    val toast = rememberAppToast()
    var balance by remember { mutableFloatStateOf(Balance) }
    var amount by remember { mutableIntStateOf(1) }
    var asking by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf(false) }
    val top = TopUps[amount]

    LaunchedEffect(paying) {
        if (!paying) return@LaunchedEffect
        delay(PayMillis)
        balance += top
        paying = false
        toast("Topped up $${top.roundToInt()}. Balance ${dollars(balance)}")
    }

    Box(Modifier.fillMaxSize().testTag("ticket")) {
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = pageBack()) {
                +"Ticket"
                supporting { +"SmartRider · Standard fare" }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Theme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Card(Modifier.fillMaxWidth().widthIn(max = TicketWidth)) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // The ticks outside the arc and unlabelled, so the
                        // middle is the balance's alone: the scale's job is
                        // only to say how full, and the number says how much.
                        Gauge(
                            value = balance,
                            valueRange = 0f..GaugeTop,
                            size = GaugeSize,
                            indicator = GaugeIndicator.Thumb,
                            majorTicks = 5,
                            tickPlacement = GaugeTickPlacement.Outside,
                            contentDescription = "Balance",
                            stateDescription = { dollars(it) },
                        ) {
                            +dollars(balance)
                            label("Balance")
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularStepProgress(
                                current = TripsToday,
                                total = DailyCapTrips,
                                contentDescription = "$TripsToday of $DailyCapTrips paid trips today",
                            )
                            Text(
                                "One more paid trip today and the rest of the day is free.",
                                style = Theme.typography.bodySmall,
                                colour = Theme.colours.contentMuted,
                            )
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().widthIn(max = TicketWidth),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                ) {
                    Text("Top up by", style = Theme.typography.titleSmall)
                    SegmentedControl(
                        options = TopUps.map { "$${it.roundToInt()}" },
                        selectedIndex = amount,
                        onSelectedIndexChange = { amount = it },
                    )
                    Button(onClick = { asking = true }, modifier = Modifier.fillMaxWidth()) {
                        +"Top up $${top.roundToInt()}"
                    }
                }
            }
        }
        LoadingOverlay(visible = paying, label = "Topping up")
    }

    AlertDialog(
        visible = asking,
        onDismissRequest = { asking = false },
        confirmLabel = "Top up",
        onConfirm = {
            asking = false
            paying = true
        },
    ) {
        +"Top up $${top.roundToInt()}?"
        message { +"From the card ending 4821. It's on your ticket straight away." }
    }
}

private val TopUps = listOf(10f, 20f, 50f)
private const val GaugeTop = 100f
private const val PayMillis = 1_200L
private val GaugeSize = 200.dp
private val TicketWidth = 480.dp
