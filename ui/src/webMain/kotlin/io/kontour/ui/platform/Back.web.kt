package io.kontour.ui.platform

import io.kontour.ui.motion.BackStyle

/** No back gesture — Escape and the browser's button complete at once — so only push and pop show. */
internal actual val platformBackStyle: BackStyle = BackStyle.Predictive
