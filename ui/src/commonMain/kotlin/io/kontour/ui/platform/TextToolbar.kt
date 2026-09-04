package io.kontour.ui.platform

/**
 * Whether this platform shows a selection toolbar of its own worth deferring to.
 *
 * Not "does Compose install a `TextToolbar`" — it does on every target — but
 * *is the thing it installs a system surface with the user's own tools in it*.
 * On Android that is an `ActionMode` carrying Look Up, Translate, Share, the
 * user's keyboard extensions and their configured text replacements; on iOS a
 * `UIMenuController` with the same plus the writing tools. Replacing either one
 * removes functionality the user expects in exchange for matching a design
 * system they never asked the toolbar to match.
 *
 * On desktop and on the web there is no such surface. Compose falls back to a
 * bare unstyled popup on the JVM and to nothing recognisable on the web, so
 * "leave the platform alone" leaves the user with less rather than more — which
 * is the opposite of the reason for deferring in the first place.
 *
 * So the same rule reads both ways: show the user the richest selection toolbar
 * available, which is the system's where there is one and the library's where
 * there is not.
 */
internal expect val platformHasSystemTextToolbar: Boolean
