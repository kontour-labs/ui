package io.kontour.ui.platform

/**
 * Compose's JVM toolbar is a bare unstyled popup, not a system surface — there
 * is nothing here worth deferring to.
 */
internal actual val platformHasSystemTextToolbar: Boolean = false
