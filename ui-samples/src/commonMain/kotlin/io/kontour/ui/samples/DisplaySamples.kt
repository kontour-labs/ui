package io.kontour.ui.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import androidx.compose.ui.Modifier
import io.kontour.ui.components.display.Accordion
import io.kontour.ui.components.display.AnimatedCounter
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.display.StatTrend
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.PageTransition
import io.kontour.ui.motion.marquee
import kotlinx.coroutines.launch

@Composable
fun StatBasics() {
    Stat {
        value("4 min")
        +"Next departure"
        supporting("Platform 2")
        trend(StatTrend.Positive, "2 min earlier than usual")
    }
}

@Composable
fun KeyValueListBasics() {
    KeyValueList {
        item("Operator", "Transperth")
        item("Platform", "2")
        item("Fare", "$3.20")
        // A slot draws nothing a screen reader can read, so it says what to
        // announce instead.
        item("Accessible", announcement = "yes") { +Tabler.Outline.Check }
    }
}

@Composable
fun CarouselWithIndicator(photos: List<String>) {
    val scope = rememberCoroutineScope()
    val carousel = rememberCarouselState { photos.size }

    Carousel(carousel, contentDescription = "Stop photos") { page ->
        Text(photos[page])
    }
    PageIndicator(carousel, onPageSelect = { scope.launch { carousel.scrollToPage(it) } })
}

@Composable
fun AccordionBasics(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    Accordion(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        header = { +"Accessibility" },
    ) {
        Text("Step-free access at all platforms.")
    }
}

@Composable
fun AnimatedCounterBasics(minutesAway: Int) {
    AnimatedCounter(value = minutesAway, format = { "$it min" })
}

@Composable
fun MarqueeBasics(stop: Stop) {
    Text(
        text = stop.name,
        maxLines = 1,
        modifier = Modifier.marquee(),
    )
}

@Composable
fun PageTransitionBasics() {
    var route by remember { mutableStateOf<Route>(Route.List) }

    PageTransition(target = route, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            is Route.List -> Column {
                for (stop in stops) {
                    Card(
                        modifier = Modifier.sharedBounds("stop-${stop.name}"),
                        onClick = { route = Route.Detail(stop) },
                    ) {
                        Text(stop.name)
                    }
                }
            }

            is Route.Detail -> Column {
                Card(modifier = Modifier.sharedBounds("stop-${page.stop.name}")) {
                    Text(page.stop.name)
                }
                Text("${page.stop.routes} routes")
            }
        }
    }
}

sealed interface Route {
    data object List : Route
    data class Detail(val stop: Stop) : Route
}
