package io.kontour.ui.platform

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Academic: there is no motor, and the platform handler returns immediately
 * whatever it is handed. Matches the web and Android rather than inventing a
 * third answer for a case nobody can feel.
 */
internal actual val platformTickHaptic: HapticFeedbackType = HapticFeedbackType.VirtualKey


/** Academic, as above: no motor, and the handler returns immediately. */
internal actual val platformTapHaptic: HapticFeedbackType = HapticFeedbackType.VirtualKey
