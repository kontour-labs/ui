package io.kontour.ui.platform

/**
 * Whether a touch's movement that nothing in the app claims is the platform's
 * to take, cancelling the touch for the app.
 *
 * The web, and only the web. Compose's canvas lets the browser pan (`touch-action:
 * pan-y`), and keeps it out only when a move was consumed and no scroll has run,
 * or a scroll consumed something; a scroll that met an edge — a finger drifting
 * down at the top of a page — is handed to the browser, which takes the gesture
 * as its own and sends `pointercancel`. Everywhere else an unclaimed move is
 * still the app's, and anything can still claim the next one.
 *
 * See [io.kontour.ui.interaction.keepsHeldTouches], which is what this is for.
 */
internal expect val browserTakesUnclaimedTouchMoves: Boolean
