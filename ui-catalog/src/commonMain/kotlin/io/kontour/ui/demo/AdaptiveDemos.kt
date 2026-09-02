package io.kontour.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.Card
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.PageTransition
import io.kontour.ui.motion.sharedBounds
import io.kontour.ui.theme.Theme

/** The key both pages use for the card that becomes the header. */
private const val HeroKey = "stop-hero"

internal val PageTransitionDemo = ComponentDemo(slug = "page-transition") {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(Theme.shapes.large)
            .background(Theme.colours.surfaceSunken),
    ) {
        PageTransition(target = open, modifier = Modifier.fillMaxSize()) { detail ->
            if (detail) {
                Column(Modifier.fillMaxSize()) {
                    Card(
                        modifier = Modifier
                            .sharedBounds(HeroKey, clip = Theme.shapes.large)
                            .fillMaxWidth()
                            .height(120.dp),
                    ) {
                        Text("Perth Underground", style = Theme.typography.titleMedium)
                        Text(
                            "4 platforms · Mandurah, Joondalup, Airport",
                            style = Theme.typography.bodySmall,
                            colour = Theme.colours.contentMuted,
                        )
                    }
                    Box(
                        Modifier.fillMaxSize().padding(Theme.spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(
                            onClick = { open = false },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Small,
                        ) { +"Back" }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(Theme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                ) {
                    Card(
                        modifier = Modifier
                            .sharedBounds(HeroKey, clip = Theme.shapes.large)
                            .fillMaxWidth(),
                        onClick = { open = true },
                    ) {
                        Text("Perth Underground", style = Theme.typography.titleSmall)
                    }
                    Text(
                        "Tap the card — it becomes the header.",
                        style = Theme.typography.bodySmall,
                        colour = Theme.colours.contentMuted,
                    )
                }
            }
        }
    }
}

internal val adaptiveDemos = listOf(PageTransitionDemo)
