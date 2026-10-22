package io.kontour.ui.catalog.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.X
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.catalog.CatalogSettings
import io.kontour.ui.catalog.apps.board.BoardApp
import io.kontour.ui.catalog.apps.energy.EnergyApp
import io.kontour.ui.catalog.apps.messages.MessagesApp
import io.kontour.ui.catalog.apps.transit.TransitApp
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.ToastHostState
import io.kontour.ui.overlay.rememberToastHostState
import io.kontour.ui.theme.Theme

/**
 * The gallery, or the app that is running in place of it.
 *
 * **In place of, not over.** The app takes the whole window: the gallery's
 * sidebar or menu goes, and the app's own navigation is the only navigation
 * on screen, which is the point of running one — a nav bar under a gallery's
 * rail is a picture of two apps. What stays is everything around the
 * navigation: the theme and the display settings the reader chose, the root
 * overlay host, and the page the reader was on, which comes back where it was
 * left because its saved state lives above this.
 *
 * Arrives with a fade and a slight growth, as an app opening does; under
 * reduced motion, the fade alone.
 */
@Composable
internal fun AppTakeover(
    running: MiniApp?,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    gallery: @Composable () -> Unit,
) {
    val motion = Theme.motion
    AnimatedContent(
        targetState = running,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val grows = if (motion.reduceMotion) EnterTransition.None else scaleIn(motion.tweenDefault(), initialScale = AppOpenScale)
            (fadeIn(motion.tweenDefault()) + grows) togetherWith fadeOut(motion.tweenExit())
        },
        label = "app",
    ) { app ->
        if (app == null) gallery() else AppHost(app, onLeave)
    }
}

/** How small an app starts as it opens, as a share of the window. */
private const val AppOpenScale = 0.96f

/**
 * One app, with what an app has of its own: an overlay host for its sheets and
 * menus, toasts, and a window size of its own to adapt to.
 *
 * **Back leaves at the root.** [LeaveAppHandler] is registered before anything
 * inside the app is composed, so every handler the app registers — a page
 * stack, a pane, a sheet — answers before it while it has somewhere to go back
 * to, and only at the root does back reach it. That is what back does at the
 * root of a real app, too: it leaves it. Escape, and the leading-edge swipe
 * where the host has taken back over from the platform, arrive the same way.
 */
@Composable
internal fun AppHost(app: MiniApp, onLeave: () -> Unit) {
    val latestLeave by rememberUpdatedState(onLeave)
    val leave = remember(app) { LeaveApp(app) { latestLeave() } }
    val owner = LocalNavigationEventDispatcherOwner.current
    val handler = remember(leave) { LeaveAppHandler(leave) }
    DisposableEffect(owner, handler) {
        owner?.navigationEventDispatcher?.addHandler(handler)
        onDispose { handler.remove() }
    }

    CompositionLocalProvider(LocalLeaveApp provides leave) {
        WindowSizeClassProvider(Modifier.fillMaxSize()) {
            OverlayHost(Modifier.fillMaxSize()) {
                val toasts = rememberToastHostState()
                ToastHost(toasts)
                CompositionLocalProvider(LocalAppToasts provides toasts) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Theme.colours.background)
                            .testTag("app-${app.name}"),
                    ) {
                        when (app) {
                            MiniApp.Transit -> TransitApp()
                            MiniApp.Messages -> MessagesApp()
                            MiniApp.Board -> BoardApp()
                            MiniApp.Energy -> EnergyApp()
                        }
                    }
                }
            }
        }
    }
}

/** Leaving the running app: its name, for the button, and the way out. */
internal class LeaveApp(val app: MiniApp, val leave: () -> Unit)

/** The app that is running, and how to leave it. Provided by [AppHost]. */
internal val LocalLeaveApp = staticCompositionLocalOf<LeaveApp?> { null }

/**
 * The gallery's display settings, for an app's own settings page to change: an
 * app's "Dark" switch is the gallery's, so leaving the app keeps what was set
 * in it. Provided by `Catalog`; null outside the gallery, where an app's
 * settings change nothing but themselves.
 */
internal val LocalAppSettings = staticCompositionLocalOf<CatalogSettings?> { null }

/** The running app's toasts. Provided by [AppHost]. */
internal val LocalAppToasts = staticCompositionLocalOf<ToastHostState?> { null }

/** Shows [message] as a toast in the running app, if there is one. */
@Composable
internal fun rememberAppToast(): (String) -> Unit {
    val toasts = LocalAppToasts.current
    return remember(toasts) { { message: String -> toasts?.show(message) } }
}

/**
 * The way back to the gallery, for the navigation slot of an app's root pages.
 *
 * Only on a root page: a page with somewhere to go back to has its back button
 * there instead, and back from it goes to that page, not out of the app.
 * Nothing outside an app.
 */
@Composable
internal fun LeaveAppButton(modifier: Modifier = Modifier) {
    val leave = LocalLeaveApp.current ?: return
    IconButton(
        icon = Tabler.Outline.X,
        contentDescription = "Leave ${leave.app.title}",
        onClick = leave.leave,
        modifier = modifier,
    )
}

private object LeaveAppInfo : NavigationEventInfo()

/** Back at an app's root: out of it, to the gallery. See [AppHost]. */
private class LeaveAppHandler(
    private val leave: LeaveApp,
) : NavigationEventHandler<NavigationEventInfo>(LeaveAppInfo, true) {
    override fun onBackCompleted() {
        leave.leave()
    }
}
