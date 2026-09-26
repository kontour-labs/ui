package io.kontour.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.kontour.haptics.Haptics
import io.kontour.haptics.RecordingHaptics
import io.kontour.ui.platform.rememberPlatformHaptics

/**
 * The haptics player for this subtree, when something other than the platform's
 * should play them.
 *
 * `null` — the default — means the platform's own, which is what an app wants.
 * Provide one to play every haptic the theme's dispatcher performs through
 * something else: a [RecordingHaptics] in a test, to read back what a gesture
 * played rather than which intent it asked for; `Haptics.None` in a screenshot
 * run; a player with its route pinned, to compare routes on a phone.
 *
 * Above the theme, to reach the theme's dispatcher:
 * ```kotlin
 * CompositionLocalProvider(LocalHaptics provides RecordingHaptics()) {
 *     KontourTheme { … }
 * }
 * ```
 *
 * For *which* effect an interaction gets, this is the wrong layer — replace
 * [LocalFeedback] instead, or read [FeedbackIntent.defaultEffect].
 */
val LocalHaptics: ProvidableCompositionLocal<Haptics?> = staticCompositionLocalOf { null }

/**
 * The haptics player to play through here: [LocalHaptics] if something provided
 * one, and otherwise this platform's, remembered and closed with the call site.
 *
 * For a screen that plays effects directly rather than through a
 * [FeedbackDispatcher] — which means it answers to no [HapticsLevel], so it is
 * for things like a haptics test page, not for components.
 */
@Composable
fun rememberHaptics(): Haptics = LocalHaptics.current ?: rememberPlatformHaptics()
