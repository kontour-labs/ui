package io.kontour.ui.platform

/**
 * Whether the overlay host draws each kind of overlay once, out of sight, as the
 * app starts: see `OverlayRehearsal`.
 *
 * | | |
 * |---|---|
 * | Web | Yes: Skia compiles a GPU program the first time a kind of drawing is asked for, in the frame that asks |
 * | Android, iOS, desktop | No: nothing measured there, and a desktop test's screenshot would change |
 */
internal expect val platformRehearsesOverlays: Boolean
