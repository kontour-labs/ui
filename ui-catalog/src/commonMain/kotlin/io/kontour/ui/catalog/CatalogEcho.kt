package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where a specimen's action goes when it has nowhere else to go.
 *
 * Most controls in the catalog carry state you can see move — a switch flips, a
 * chip fills in, a picker lands on a date. A **button** does not: "Primary",
 * "Secondary" and "Destructive ghost" have no state to change, and the honest
 * thing they can do is confirm that the press landed. So they raise a toast
 * saying what was tapped.
 *
 * That is not decoration. `onClick = {}` looks alive — the button still presses,
 * still ripples, still focuses — while doing nothing at all, and this project
 * has twice found a real defect hiding behind exactly that: a control whose
 * callback went nowhere, so nobody noticed it was never being called. Anything
 * with a callback in the catalog is wired to something you can see.
 *
 * A composition local with a **no-op default**, because the showcases render
 * standalone in the screenshot suite with no host to raise a toast into. The
 * goldens are unaffected either way: nothing shows until something is tapped.
 */
internal val LocalCatalogEcho = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * The `onClick` for a specimen whose only job is to respond.
 *
 * ```kotlin
 * Button(onClick = tap("Primary")) { +"Primary" }
 * ```
 *
 * Reach for it only when there is genuinely nothing to change. A control with
 * state gets [seed] instead — a live control beats a message saying a dead one
 * was pressed.
 */
@Composable
internal fun tap(what: String): () -> Unit {
    val echo = LocalCatalogEcho.current
    return remember(what, echo) { { echo(what) } }
}
