package io.kontour.ui.platform

/**
 * Whether the overlay host draws each kind of overlay once, out of sight, as the
 * app starts: see `OverlayRehearsal`.
 *
 * | | |
 * |---|---|
 * | Web, on a phone or a tablet | Yes: Skia compiles a GPU program the first time a kind of drawing is asked for, in the frame that asks |
 * | Web, on a desktop | No: compiling them all at once cost a Mac's Chrome far more than it saved |
 * | Android, iOS, desktop | No: nothing measured there, and a desktop test's screenshot would change |
 */
internal expect val platformRehearsesOverlays: Boolean
