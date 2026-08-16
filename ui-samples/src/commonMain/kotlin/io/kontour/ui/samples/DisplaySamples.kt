package io.kontour.ui.samples

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import io.kontour.ui.components.display.Accordion
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.display.StatTrend
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.foundation.Text
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
