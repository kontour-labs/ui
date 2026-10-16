package io.kontour.ui.nav

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.RememberObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.ui.foundation.SurfaceDefaults
import io.kontour.ui.theme.Theme

/**
 * The destinations of a navigation surface — a [NavBar], a [NavRail], a
 * [ModalNavRail] or a [NavigationSuiteScaffold] — written once and drawn by
 * whichever one they are in.
 *
 * ```kotlin
 * NavRail {
 *     item(selected = here == Stops, onClick = { go(Stops) }, key = Stops, icon = Tabler.Outline.MapPin) {
 *         +"Stops"
 *     }
 *     section("Saved") {
 *         item(here == Favourites, { go(Favourites) }, key = Favourites, icon = Tabler.Outline.Star,
 *             badge = { Badge(count = 3) }) { +"Favourites" }
 *         group(routesOpen, { routesOpen = it }, key = "routes", icon = Tabler.Outline.Route, header = { +"Routes" }) {
 *             item(route == "950", { go("950") }, key = "950", icon = null) { +"950" }
 *         }
 *     }
 * }
 * ```
 *
 * **Emits, as a menu does.** Each call draws where it is written, so the
 * destinations can be read from state, loops and `stringResource` like any
 * other content. What each call draws depends on the surface:
 *
 * | | a bar | a collapsed rail | an expanded or modal rail |
 * |---|---|---|---|
 * | [item] | a circle | an icon | a row |
 * | [section] | nothing: its items are inline | a hairline | a title over its items |
 * | [group] | a circle opening a menu of its children | an icon opening a menu | a row that discloses its children |
 * | [divider] | nothing | a hairline | a hairline |
 * | [more] | a "More" circle opening a menu of its children | its children, inline | its children, inline |
 *
 * A group carries the selection marker while the destination selected inside it
 * is hidden, so the surface always says where you are.
 *
 * **Every destination has a `key`**, the identity the selection marker travels
 * between, and no two in one surface may share one: a duplicate throws. It is a
 * value of yours — a route, an enum — never the label, which a translation
 * changes under the marker.
 *
 * **Every destination has a label**, written `+"Stops"`, and one is required: it
 * is what a screen reader announces wherever the surface draws only the icon.
 * It is a `String` rather than content for that reason — see
 * `ui-docs/content/dsls.md`, on announcements.
 *
 * **A call draws where it is written, whichever scope it is made on.** A
 * helper written in the outer block — `fun stop(id: String) = item(…)` — closes
 * over the outer scope, and called inside a [group] it still draws as one of
 * the group's. The DSL marker on this scope already refuses an implicit outer
 * receiver, but cannot see a captured one.
 */
@Stable
@LayoutScopeMarker
sealed interface NavScope {

    /**
     * One destination.
     *
     * @param selected Whether this is where the app is. Per item, so a
     *   destination shown or hidden by a condition does not shift which one is
     *   selected.
     * @param key This destination's identity: unique in the surface.
     * @param icon The glyph. `null` draws the label's initial wherever labels
     *   are hidden, and nothing where they show — the rows of a text index.
     * @param selectedIcon A filled counterpart for the current destination.
     * @param contentDescription What a screen reader says instead of the label,
     *   when a single word does not say where the destination goes.
     * @param badge Drawn on the icon's corner: `{ Badge(count = 3) }`, or
     *   `{ Badge() }` for a dot. Part of what the destination announces.
     * @param label `+"Stops"`. Required.
     */
    @Composable
    fun item(
        selected: Boolean,
        onClick: () -> Unit,
        key: Any,
        icon: ImageVector?,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        selectedIcon: ImageVector? = null,
        contentDescription: String? = null,
        interactionSource: MutableInteractionSource? = null,
        badge: (@Composable () -> Unit)? = null,
        label: NavLabelScope.() -> Unit,
    )

    /**
     * A titled run of destinations. A rail at its widest draws [title] over
     * them; narrower, a hairline the same height, so the icons do not move as
     * it grows; a bar draws the destinations inline.
     */
    @Composable
    fun section(title: String, modifier: Modifier = Modifier, content: @Composable NavScope.() -> Unit)

    /**
     * A destination that holds others.
     *
     * In a rail wide enough for labels it is a disclosure: tapping it opens and
     * closes the run of its children below it, which is why [expanded] is
     * hoisted — an app opens the group holding the current page. In a bar or a
     * collapsed rail it opens a menu of its children instead, beside it.
     *
     * @param key Its identity for the selection marker, which it carries while
     *   the destination selected inside it is hidden.
     * @param header The group's name, `+"Routes"`. Required, as an item's label is.
     */
    @Composable
    fun group(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        key: Any,
        icon: ImageVector?,
        header: NavLabelScope.() -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        interactionSource: MutableInteractionSource? = null,
        badge: (@Composable () -> Unit)? = null,
        content: @Composable NavScope.() -> Unit,
    )

    /** A hairline between runs of destinations, where the surface has room for one. */
    @Composable
    fun divider(modifier: Modifier = Modifier)
}

/**
 * A [NavBar]'s destinations, and what else shares the bar with them.
 *
 * ```kotlin
 * NavBar {
 *     item(here == Home, { go(Home) }, key = Home, icon = Tabler.Outline.Home) { +"Home" }
 *     slot(weight = 1f) { NavSearch(search) }
 *     item(here == Saved, { go(Saved) }, key = Saved, icon = Tabler.Outline.Star) { +"Saved" }
 * }
 * ```
 */
@Stable
@LayoutScopeMarker
sealed interface NavBarScope : NavScope {

    /**
     * Anything else that belongs inside the bar: a search, a button. Not counted
     * among the destinations a screen reader hears.
     *
     * A floating action button goes in [NavBar]'s `action` instead, which puts
     * it beside a floating bar rather than squeezing it into the capsule.
     *
     * @param weight Zero, the default, sizes it to its content. Above zero it
     *   takes that share of the room the rest leave, up to
     *   [NavBarDefaults.SlotMaxWidth], which is what a search wants and what
     *   makes an empty slot a spacer.
     */
    @Composable
    fun slot(weight: Float = 0f, content: @Composable () -> Unit)
}

/**
 * The destinations with no room of their own, behind a "More" at the end of a
 * bar.
 *
 * ```kotlin
 * NavBar {
 *     item(here == Home, { go(Home) }, key = Home, icon = Tabler.Outline.Home) { +"Home" }
 *     item(here == Map, { go(Map) }, key = Map, icon = Tabler.Outline.Map) { +"Map" }
 *     item(here == Saved, { go(Saved) }, key = Saved, icon = Tabler.Outline.Star) { +"Saved" }
 *     more {
 *         item(here == Settings, { go(Settings) }, key = Settings, icon = Tabler.Outline.Settings) { +"Settings" }
 *         item(here == Help, { go(Help) }, key = Help, icon = Tabler.Outline.Help) { +"Help" }
 *     }
 * }
 * ```
 *
 * A [NavScope.group] with nothing to decide: the library's horizontal dots
 * (`Theme.icons.more`), `Theme.strings.more` for its name, and a key of its
 * own, so there is one per surface. Asked for as *"an easy api to add a '…'
 * menu to the end with one of those dropdown options. this is useful for lots
 * of nav items"* — a bar has room for five destinations, and an app with nine
 * needs somewhere for the other four.
 *
 * **Where it is written**, which is the end of the bar by convention, as it is
 * on every platform: the destinations people reach for first come first.
 *
 * **Only a bar needs it.** In a rail, at any width, there is room, and the
 * destinations are drawn inline where `more` is written, as if it were not
 * there; in a group's menu they are rows of the menu. So the same block reads
 * right in a [NavigationSuiteScaffold], which draws a bar on a phone and a rail
 * on anything wider.
 *
 * Like any group it carries the marker while the current destination is inside
 * it, and the bar's marker can be dragged to it to open it.
 *
 * @param badge Drawn on the dots, as a group's is: `{ Badge() }` when something
 *   inside wants attention.
 */
@Composable
fun NavScope.more(
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable NavScope.() -> Unit,
) {
    when (val here = emitter()) {
        is NavBarScope -> {
            val name = Theme.strings.more
            here.group(
                expanded = false,
                onExpandedChange = {},
                key = NavMoreKey,
                icon = Theme.icons.more,
                header = { +name },
                modifier = modifier,
                badge = badge,
                content = content,
            )
        }
        else -> here.content()
    }
}

/** [more]'s key: one per surface, and never one of an app's own. */
private data object NavMoreKey

/**
 * The scope composing destinations at this point of the composition: a
 * surface's own at its root, a group's inside its run or its menu, a probe's
 * while a group's destinations are hidden.
 *
 * Every destination call goes through it ([emitter]), so a call made on a
 * scope captured from further out — a helper written in the outer block,
 * called inside a group — draws as what the place it is called from draws.
 * Reported from an app: *"items I emitted inside `group { … }` through a
 * helper ended up added to the outer scope, outside their group"*. In a rail
 * they drew over a shut group, unindented in an open one; in a bar as a
 * circle of their own, and opening the group's menu claimed their key twice,
 * which throws.
 */
internal val LocalNavEmitter = compositionLocalOf<NavScope?> { null }

/** Runs [content] on this scope, as the one destinations here draw through. */
@Composable
internal fun <S : NavScope> S.emit(content: @Composable S.() -> Unit) {
    CompositionLocalProvider(LocalNavEmitter provides this) { content() }
}

/** The scope a destination called on this one draws through: see [LocalNavEmitter]. */
@Composable
@ReadOnlyComposable
internal fun NavScope.emitter(): NavScope = LocalNavEmitter.current ?: this

/** Collects a destination's label: `+"Stops"`. */
@LayoutScopeMarker
@Stable
class NavLabelScope internal constructor() {
    internal var text: String? = null
        private set

    /** The label. */
    operator fun String.unaryPlus() {
        text = this
    }
}

/** Runs [label] and hands back what it wrote, or throws if it wrote nothing. */
internal fun navLabel(label: NavLabelScope.() -> Unit): String =
    requireNotNull(NavLabelScope().apply(label).text) {
        "A navigation destination needs a label — `{ +\"Stops\" }` — even where the surface draws only its icon: " +
            "it is what a screen reader announces."
    }

/**
 * The colours of a navigation surface and its destinations.
 *
 * @property container The surface behind the destinations.
 * @property content An unselected destination's icon and label.
 * @property indicator The marker behind the selected destination.
 * @property selectedContent The selected destination's icon and label, on
 *   [indicator].
 * @property contentDisabled A destination that cannot be chosen.
 */
@Immutable
data class NavColours(
    val container: Color,
    val content: Color,
    val indicator: Color,
    val selectedContent: Color,
    val contentDisabled: Color,
)

/** What the navigation surfaces take by default. */
object NavDefaults {

    /**
     * The surfaces' colours. Unselected destinations are muted, as a list's
     * secondary text is; the selected one's content is worked out from
     * [indicator], so a custom marker keeps its content readable on it.
     */
    @Composable
    @ReadOnlyComposable
    fun colours(
        container: Color = Theme.colours.surface,
        content: Color = Theme.colours.contentMuted,
        indicator: Color = Theme.colours.accent.container,
        selectedContent: Color = SurfaceDefaults.contentColour(indicator),
        contentDisabled: Color = Theme.colours.contentDisabled,
    ): NavColours = NavColours(container, content, indicator, selectedContent, contentDisabled)
}

/**
 * The keys a surface's destinations are using, so two that share one are caught
 * as they arrive rather than as a marker that lands on the wrong row.
 */
internal class NavKeys {
    private val claimed = HashMap<Any, Any>()

    fun claim(key: Any, owner: Any) {
        val held = claimed[key]
        require(held == null || held === owner) {
            "Two navigation destinations share the key $key. Each one needs its own: " +
                "the selection marker travels between keys."
        }
        claimed[key] = owner
    }

    fun release(key: Any, owner: Any) {
        if (claimed[key] === owner) claimed.remove(key)
    }
}

/**
 * Claims [key] in [keys] for as long as this is composed.
 *
 * Claimed when remembered rather than during composition: a destination that
 * moves — a list reordered — is forgotten at its old place in the same apply
 * that remembers it at its new one, and forgetting goes first, so only a real
 * duplicate is ever caught.
 */
@Composable
internal fun ClaimNavKey(keys: NavKeys?, key: Any) {
    if (keys == null) return
    remember(keys, key) { NavKeyClaim(keys, key) }
}

private class NavKeyClaim(private val keys: NavKeys, private val key: Any) : RememberObserver {
    override fun onRemembered() = keys.claim(key, this)
    override fun onForgotten() = keys.release(key, this)
    override fun onAbandoned() = keys.release(key, this)
}

/**
 * Which of a group's destinations are selected, reported by them as they are
 * composed — so the group can carry the marker while they are hidden, whether
 * they are hidden in a collapsed run or in a menu that is not open.
 */
@Stable
internal class NavGroupSelection {
    private val selected = mutableStateMapOf<Any, Boolean>()
    val any: Boolean by derivedStateOf { selected.values.any { it } }

    @Composable
    fun Report(key: Any, isSelected: Boolean) {
        SideEffect { selected[key] = isSelected }
        DisposableEffect(key) { onDispose { selected.remove(key) } }
    }
}
