package io.kontour.ui.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.adaptive.AspectRatioBox
import io.kontour.ui.adaptive.ListDetailPaneScaffold
import io.kontour.ui.adaptive.PaneFocus
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.adaptive.windowSizeClass
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.display.Kbd
import io.kontour.ui.components.text.KontourTextToolbar
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.rememberImeChain
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Scrim
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.VerticalDivider
import io.kontour.ui.motion.GlassSurface
import io.kontour.ui.motion.atmosphere
import io.kontour.ui.nav.TopBar
import io.kontour.ui.theme.Theme

@Composable
fun TextBasics() {
    Text("Perth Underground", style = Theme.typography.titleMedium)

    Text(
        "Platform 2 · Joondalup line",
        style = Theme.typography.bodySmall,
        color = Theme.colors.contentMuted,
    )

    // The `AnnotatedString` overload is why there are two: a route number in
    // the accent colour inside a sentence, without a second component and
    // without breaking the line box.
    Text(
        buildAnnotatedString {
            append("The ")
            withStyle(SpanStyle(color = Theme.colors.accent.solid)) { append("950") }
            append(" leaves in 4 minutes.")
        },
    )
}

@Composable
fun IconBasics() {
    // A decorative icon beside a label that already says the same thing takes
    // `null`, so a screen reader announces the label once rather than twice.
    Icon(Tabler.Outline.Star, contentDescription = null)

    // One that carries the meaning on its own describes itself.
    Icon(Tabler.Outline.Star, contentDescription = "Favourite", size = Theme.sizing.iconLarge)
}

@Composable
fun SurfaceBasics() {
    // No colour argument on either child: the surface set `LocalContentColor`
    // from its own background, and both resolve against it.
    Surface(color = Theme.colors.primary, shape = Theme.shapes.medium, shadow = Theme.elevation.low) {
        Column(Modifier.padding(Theme.spacing.md)) {
            Text("Perth Underground")
            Icon(Tabler.Outline.Star, contentDescription = null)
        }
    }
}

@Composable
fun DividerBasics() {
    Column {
        Text("Departures")
        HorizontalDivider()
        Text("Alerts")
    }

    Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Platform 2")
        VerticalDivider(Modifier.height(16.dp))
        Text("Joondalup line")
    }
}

@Composable
fun ScrimBasics() {
    var open by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Screen()
        // `fraction` is a lambda rather than a `Float`: a sheet reads its own
        // drag offset through it every frame, so the dimming tracks the gesture
        // without the scrim recomposing.
        Scrim(fraction = { if (open) 1f else 0f }, onDismissRequest = { open = false })
    }
}

@Composable
fun ScaffoldBasics() {
    Scaffold(
        topBar = { TopBar { +"Favourites" } },
        floatingActionButton = {
            FloatingActionButton(
                icon = Tabler.Outline.Star,
                contentDescription = "Add a favourite",
                onClick = { add() },
            )
        },
    ) { padding ->
        // Handed over rather than applied, so content can scroll *under* the
        // bar instead of starting below it.
        Column(Modifier.fillMaxSize().padding(padding)) {
            Screen()
        }
    }
}

@Composable
fun PaneScaffoldBasics() {
    var focus by remember { mutableStateOf(PaneFocus.List) }
    var selected by remember { mutableStateOf<String?>(null) }

    // One pane on a phone and two on a tablet, from the same call. `onBack` is
    // what closes the detail on a phone, where there is nowhere else to go.
    ListDetailPaneScaffold(
        focus = focus,
        onBack = { focus = PaneFocus.List },
        list = {
            Column {
                stops.forEach { stop ->
                    Text(
                        stop.name,
                        modifier = Modifier.fillMaxWidth().padding(Theme.spacing.md),
                    )
                }
            }
        },
        detail = { Text(selected ?: "Pick a stop") },
    )
}

@Composable
fun WindowSizeClassBasics() {
    // The class of the *container*, not of the device: a pane 380dp wide inside
    // a 1400dp window is Compact, and a layout that asked the window would put
    // a two-column grid in it.
    if (windowSizeClass.width.hasRoomBeside) {
        Row { Screen() }
    } else {
        Column { Screen() }
    }

    // Provide a fresh one wherever a subtree gets its own width.
    WindowSizeClassProvider {
        if (windowSizeClass.width == WindowWidthClass.Compact) Screen() else Screen()
    }
}

@Composable
fun GlassSurfaceBasics() {
    Box(Modifier.fillMaxSize().atmosphere()) {
        Screen()
        GlassSurface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(Theme.spacing.md),
            shape = Theme.shapes.pill,
        ) {
            Text("Live departures", modifier = Modifier.padding(Theme.spacing.md))
        }
    }
}

@Composable
fun AspectRatioBoxBasics() {
    // The space is reserved before the photo arrives, so nothing below it moves
    // when it does — which is the whole point.
    AspectRatioBox(
        ratio = 16f / 9f,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Theme.shapes.medium)
            .background(Theme.colors.surfaceSunken),
    ) {
        Screen()
    }
}

@Composable
fun ImeChainBasics() {
    val from = rememberTextFieldState()
    val to = rememberTextFieldState()
    // Declared in order, once. The first field's action key says Next and moves
    // focus; the last one says Done and runs `onSubmit`.
    val chain = rememberImeChain("from", "to", onSubmit = { plan() })

    TextField(state = from, label = "From", imeChain = chain["from"])
    TextField(state = to, label = "To", imeChain = chain["to"])
}

@Composable
fun TextToolbarBasics() {
    // Wrap the app once. Cut, copy, paste and select-all are then drawn by the
    // library rather than by the platform, so they look the same everywhere and
    // read their labels from `Theme.strings`.
    KontourTextToolbar {
        Screen()
    }
}

@Composable
fun KbdBasics() {
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Kbd { +"⌘" }
        Kbd { +"K" }
        Text("opens the command palette", style = Theme.typography.bodySmall)
    }
}
