package io.kontour.ui.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState

/** The platform's accessibility tree takes the role from the semantics here. */
internal actual fun Modifier.webRole(role: WebRole, state: ToggleableState): Modifier = this
