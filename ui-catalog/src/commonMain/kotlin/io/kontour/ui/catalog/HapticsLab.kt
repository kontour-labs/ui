package io.kontour.ui.catalog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.kontour.haptics.HapticCapability
import io.kontour.haptics.HapticEffect
import io.kontour.haptics.Haptics
import io.kontour.haptics.ImpactStyle
import io.kontour.haptics.NotificationType
import io.kontour.haptics.Rumble
import io.kontour.haptics.hapticPattern
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.LocalHaptics
import io.kontour.ui.interaction.defaultEffect
import io.kontour.ui.interaction.rememberHaptics
import io.kontour.ui.interaction.rememberHoldFeedback
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Every haptic the library plays, to feel on a device — and a way to tune them.
 *
 * An instrument, like [FramesPage], and for the same reason: what it measures
 * cannot be measured anywhere else. A haptic has no pixels for a golden and no
 * number a test can read that says whether it is felt, too strong, or buzzy; the
 * only place to judge one is a hand holding the phone. So this plays each intent
 * the way the components do, each raw effect at any strength, a rumble with live
 * controls, and timing runs — and its tuner rewrites the intent table in place,
 * with the playground underneath feeling the result and the table printed as
 * Kotlin to paste back into `FeedbackEffects.kt`.
 */
@Composable
internal fun HapticsLab(modifier: Modifier = Modifier) {
    var route by remember { mutableIntStateOf(0) }
    var rumbleRoute by remember { mutableIntStateOf(0) }
    // A route pinned for comparison, where the platform has more than one.
    val routed = rememberRoutedHaptics(route, rumbleRoute)

    CompositionLocalProvider(LocalHaptics provides (routed ?: LocalHaptics.current)) {
        val player = rememberHaptics()
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            ThisDevice(player, route, { route = it }, rumbleRoute, { rumbleRoute = it })
            Intents()
            Effects(player)
            RumbleCard(player)
            Timing(player)
            Tuner(player)
        }
    }
}

@Composable
private fun LabSection(title: String, note: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            Text(title, style = Theme.typography.titleSmall)
            if (note != null) Text(note, style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
            content()
        }
    }
}

@Composable
private fun LabButtons(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) { content() }
}

@Composable
private fun LabPlay(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, variant = ButtonVariant.Secondary, size = ButtonSize.Small) { Text(label) }
}

@Composable
private fun ThisDevice(
    player: Haptics,
    route: Int,
    onRoute: (Int) -> Unit,
    rumbleRoute: Int,
    onRumbleRoute: (Int) -> Unit,
) {
    val capability = player.capability
    LabSection("This device") {
        Text(
            when (capability.level) {
                HapticCapability.Level.None -> "Nothing plays here."
                HapticCapability.Level.Basic -> "The system's own feedback, in a few fixed kinds."
                HapticCapability.Level.Rich -> "Tuned effects at any strength."
            },
        )
        if (capability.level != HapticCapability.Level.None) {
            Text(
                "Strength: " + (if (capability.honoursStrength) "felt finely" else "coarse, or not at all") +
                    ". Rumble: " + when (capability.rumble) {
                        HapticCapability.RumbleSupport.None -> "none"
                        HapticCapability.RumbleSupport.Pulsed -> "a run of light taps"
                        HapticCapability.RumbleSupport.Continuous -> "continuous"
                    } + ".",
                style = Theme.typography.bodySmall,
            )
        }
        capability.details.forEach { Text(it, style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted) }
        if (HapticsRoutes.isNotEmpty()) {
            Text("Route, for comparing", style = Theme.typography.labelMedium)
            SegmentedControl(options = HapticsRoutes, selected = route, onSelectedChange = onRoute)
            Text("Rumble route", style = Theme.typography.labelMedium)
            SegmentedControl(options = RumbleRoutes, selected = rumbleRoute, onSelectedChange = onRumbleRoute)
        }
    }
}

/** Each intent through the app's own dispatcher: the effect a component plays, at the level in Settings. */
@Composable
private fun Intents() {
    val feedback = LocalFeedback.current
    val hold = rememberHoldFeedback()
    val scope = rememberCoroutineScope()
    LabSection(
        "Intents",
        "What each interaction plays, through the app's dispatcher — so the Haptics setting applies, " +
            "and an intent it drops plays nothing here either.",
    ) {
        LabButtons {
            FeedbackIntent.entries.filter { it != FeedbackIntent.Hold }.forEach { intent ->
                LabPlay(intent.name) { feedback.perform(intent) }
            }
            LabPlay("Hold (700 ms)") {
                scope.launch {
                    val progress = Animatable(0f)
                    hold.start()
                    try {
                        progress.animateTo(1f, tween(700, easing = LinearEasing)) { hold.progress(value) }
                    } finally {
                        hold.stop()
                    }
                }
            }
        }
    }
}

/** One effect, by name, with a builder and the Kotlin that builds it — for the buttons and the tuner. */
private class EffectKind(val label: String, val make: (Float) -> HapticEffect, val code: (String) -> String)

private val EffectKinds: List<EffectKind> = listOf(
    EffectKind("Tick", { HapticEffect.Tick(it) }, { "HapticEffect.Tick($it)" }),
    EffectKind("Low tick", { HapticEffect.LowTick(it) }, { "HapticEffect.LowTick($it)" }),
    EffectKind("Click", { HapticEffect.Click(it) }, { "HapticEffect.Click($it)" }),
    EffectKind("Thud", { HapticEffect.Thud(it) }, { "HapticEffect.Thud($it)" }),
    EffectKind("Spin", { HapticEffect.Spin(it) }, { "HapticEffect.Spin($it)" }),
    EffectKind("Quick rise", { HapticEffect.QuickRise(it) }, { "HapticEffect.QuickRise($it)" }),
    EffectKind("Slow rise", { HapticEffect.SlowRise(it) }, { "HapticEffect.SlowRise($it)" }),
    EffectKind("Quick fall", { HapticEffect.QuickFall(it) }, { "HapticEffect.QuickFall($it)" }),
    EffectKind("Selection", { HapticEffect.Selection(fine = false, strength = it) }, { "HapticEffect.Selection(fine = false, strength = $it)" }),
    EffectKind("Selection, fine", { HapticEffect.Selection(fine = true, strength = it) }, { "HapticEffect.Selection(fine = true, strength = $it)" }),
) + ImpactStyle.entries.map { style ->
    EffectKind("Impact ${style.name.lowercase()}", { HapticEffect.Impact(style, it) }, { "HapticEffect.Impact(ImpactStyle.${style.name}, $it)" })
} + NotificationType.entries.map { type ->
    EffectKind(type.name, { HapticEffect.Notification(type, it) }, { "HapticEffect.Notification(NotificationType.${type.name}, $it)" })
} + listOf(
    EffectKind("Toggle on", { HapticEffect.Toggle(on = true, strength = it) }, { "HapticEffect.Toggle(on = true, strength = $it)" }),
    EffectKind("Toggle off", { HapticEffect.Toggle(on = false, strength = it) }, { "HapticEffect.Toggle(on = false, strength = $it)" }),
    EffectKind("Threshold in", { HapticEffect.Threshold(activate = true, strength = it) }, { "HapticEffect.Threshold(activate = true, strength = $it)" }),
    EffectKind("Threshold back", { HapticEffect.Threshold(activate = false, strength = it) }, { "HapticEffect.Threshold(activate = false, strength = $it)" }),
    EffectKind("Long press", { HapticEffect.LongPress(it) }, { "HapticEffect.LongPress($it)" }),
    EffectKind("Key press", { HapticEffect.KeyPress(it) }, { "HapticEffect.KeyPress($it)" }),
)

/** A strength as Kotlin: two places, which is finer than a hand can tell apart. */
private fun kotlinFloat(value: Float): String = "${(value * 100).roundToInt() / 100f}f"

@Composable
private fun StrengthSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Text("$label ${(value * 100).roundToInt()}%", style = Theme.typography.labelMedium)
        Slider(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f))
    }
}

/** Every effect, straight to the player: no level, no rate limit, any strength. */
@Composable
private fun Effects(player: Haptics) {
    var strength by remember { mutableFloatStateOf(1f) }
    LabSection("Effects", "Straight to the player, at this strength. No level applies here.") {
        StrengthSlider("Strength", strength) { strength = it }
        LabButtons { EffectKinds.forEach { kind -> LabPlay(kind.label) { player.play(kind.make(strength)) } } }
    }
}

@Composable
private fun RumbleCard(player: Haptics) {
    var intensity by remember { mutableFloatStateOf(0.3f) }
    var sharpness by remember { mutableFloatStateOf(0.2f) }
    var rumble by remember { mutableStateOf<Rumble?>(null) }
    DisposableEffect(player) { onDispose { rumble?.stop() } }
    LabSection("Rumble", "Continuous, steered live. The calendar's hold builds from 15% to 45% at 20% sharpness.") {
        LabPlay(if (rumble?.isActive == true) "Stop" else "Start") {
            val playing = rumble
            rumble = if (playing?.isActive == true) {
                playing.stop()
                null
            } else {
                player.startRumble(intensity, sharpness)
            }
        }
        StrengthSlider("Intensity", intensity) {
            intensity = it
            rumble?.update(intensity, sharpness)
        }
        StrengthSlider("Sharpness", sharpness) {
            sharpness = it
            rumble?.update(intensity, sharpness)
        }
    }
}

@Composable
private fun Timing(player: Haptics) {
    val gaps = listOf(20, 40, 80, 120)
    var gap by remember { mutableIntStateOf(2) }
    val scope = rememberCoroutineScope()
    LabSection(
        "Timing",
        "Ten ticks at a spacing, to feel where separate taps become a buzz — the detents are held to 80 ms apart.",
    ) {
        SegmentedControl(options = gaps.map { "$it ms" }, selected = gap, onSelectedChange = { gap = it })
        LabButtons {
            LabPlay("Ten ticks") {
                scope.launch {
                    repeat(10) {
                        player.play(HapticEffect.Selection(fine = true))
                        delay(gaps[gap].toLong())
                    }
                }
            }
            LabPlay("Pattern: tick, then click") {
                player.play(
                    hapticPattern {
                        at(0.milliseconds, HapticEffect.Tick(0.5f))
                        at(70.milliseconds, HapticEffect.Click(0.9f))
                    },
                )
            }
        }
    }
}

/**
 * The intent table, rewritten in place: pick an intent, pick what it should play
 * and how strongly, and the playground below plays the new table. What changed is
 * printed as Kotlin for `FeedbackEffects.kt`.
 */
@Composable
private fun Tuner(player: Haptics) {
    val overrides = remember { mutableStateMapOf<FeedbackIntent, Pair<Int, Float>>() }
    var intent by remember { mutableStateOf(FeedbackIntent.Tick) }
    var kind by remember { mutableIntStateOf(0) }
    var strength by remember { mutableFloatStateOf(0.5f) }
    val tuned = remember(player) {
        FeedbackDispatcher { asked ->
            val override = overrides[asked]
            player.play(if (override != null) EffectKinds[override.first].make(override.second) else asked.defaultEffect)
        }
    }

    LabSection("Tuner", "Pick an intent and give it an effect; the playground plays the table with your changes.") {
        Text("Intent", style = Theme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs), verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            FeedbackIntent.entries.forEach { each ->
                FilterChip(selected = each == intent, onClick = { intent = each }) { Text(each.name) }
            }
        }
        Text("Effect", style = Theme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs), verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            EffectKinds.forEachIndexed { i, each ->
                FilterChip(selected = i == kind, onClick = { kind = i }) { Text(each.label) }
            }
        }
        StrengthSlider("Strength", strength) { strength = it }
        LabButtons {
            LabPlay("Set ${intent.name}") {
                overrides[intent] = kind to strength
                tuned.perform(intent)
            }
            LabPlay("Reset ${intent.name}") { overrides.remove(intent) }
        }

        Text("Playground", style = Theme.typography.labelMedium)
        CompositionLocalProvider(LocalFeedback provides tuned) {
            var value by remember { mutableFloatStateOf(0.5f) }
            var on by remember { mutableStateOf(false) }
            var ticked by remember { mutableStateOf(false) }
            var segment by remember { mutableIntStateOf(0) }
            Slider(value = value, onValueChange = { value = it }, steps = 9, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                Switch(checked = on, onCheckedChange = { on = it })
                Checkbox(checked = ticked, onCheckedChange = { ticked = it })
            }
            SegmentedControl(options = listOf("Day", "Week", "Month"), selected = segment, onSelectedChange = { segment = it })
        }

        if (overrides.isNotEmpty()) {
            Text("For FeedbackEffects.kt", style = Theme.typography.labelMedium)
            SelectionContainer {
                Text(
                    overrides.entries.sortedBy { it.key.ordinal }.joinToString("\n") { (asked, choice) ->
                        "FeedbackIntent.${asked.name} -> ${EffectKinds[choice.first].code(kotlinFloat(choice.second))}"
                    },
                    style = Theme.typography.bodySmall,
                )
            }
        }
    }
}

/** The routes this platform's player can be pinned to for a comparison — none where there is one. */
internal expect val HapticsRoutes: List<String>

/** The same, for rumbles. */
internal expect val RumbleRoutes: List<String>

/** A player pinned to [route] and [rumbleRoute], or null for the platform's own choice. */
@Composable
internal expect fun rememberRoutedHaptics(route: Int, rumbleRoute: Int): Haptics?
