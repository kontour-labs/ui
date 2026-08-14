package io.kontour.ui.components.text

/**
 * False: the platform toolbar here is a real system surface. It carries "Look
 * Up", "Translate", "Share", the user's keyboard extensions and their configured
 * text replacements — all of which vanish the moment it is replaced with four
 * buttons of our own.
 */
internal actual val platformWantsCustomTextToolbar: Boolean = false
