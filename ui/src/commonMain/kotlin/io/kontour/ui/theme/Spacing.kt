package io.kontour.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale — a 4dp grid, named to match `--space-*` in
 * `admin/src/lib/styles/app.css` so values port across without arithmetic.
 *
 * | Token | | Typical use |
 * |---|---|---|
 * | [xxs] | 4dp | Gap between an icon and its label |
 * | [xs] | 8dp | Inside a chip or badge |
 * | [sm] | 12dp | Between related rows |
 * | [md] | 16dp | Default padding inside a card or screen gutter |
 * | [lg] | 24dp | Between groups |
 * | [xl] | 32dp | Between sections |
 * | [xxl] | 40dp | Around a screen's hero |
 *
 * Reach for [of] only when a one-off genuinely does not fit the scale — it
 * still snaps to the 4dp grid, so `of(3)` is 12dp. If you find yourself calling
 * it repeatedly for the same value, that value wants a name here instead.
 */
@Immutable
data class Spacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 40.dp,
) {
    /** [steps] × 4dp, for the rare measurement the named scale does not cover. */
    fun of(steps: Int): Dp = (steps * 4).dp
}
