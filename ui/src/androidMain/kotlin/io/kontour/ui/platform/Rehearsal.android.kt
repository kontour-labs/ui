package io.kontour.ui.platform

internal actual val platformRehearsesOverlays: Boolean = false

internal actual val platformRehearsesWhenIdle: Boolean = false

internal actual suspend fun awaitBrowserIdle() = Unit
