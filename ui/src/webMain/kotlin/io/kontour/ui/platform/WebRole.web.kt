@file:OptIn(ExperimentalWasmJsInterop::class)

package io.kontour.ui.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.state.ToggleableState
import kotlin.js.ExperimentalWasmJsInterop

internal actual fun Modifier.webRole(role: WebRole, state: ToggleableState): Modifier =
    this then WebRoleElement(role, state)

private data class WebRoleElement(val role: WebRole, val state: ToggleableState) : ModifierNodeElement<WebRoleNode>() {
    override fun create() = WebRoleNode(role, state)

    override fun update(node: WebRoleNode) {
        node.role = role
        node.state = state
        node.publish()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "webRole"
        properties["role"] = role
        properties["state"] = state
    }
}

/**
 * Says where its control is and what it is, for [WebRoles] to find its element
 * in the accessibility tree by: the same box Compose places the element at,
 * which is the node's bounds in the root, in CSS pixels.
 */
private class WebRoleNode(var role: WebRole, var state: ToggleableState) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    private var coordinates: LayoutCoordinates? = null
    private val key = WebRoles.newKey()

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        publish()
    }

    fun publish() {
        val at = coordinates ?: return
        if (!isAttached || !at.isAttached) return
        val scale = currentValueOf(LocalDensity).density
        val bounds = at.boundsInRoot()
        WebRoles.put(
            key = key,
            left = (bounds.left / scale).toDouble(),
            top = (bounds.top / scale).toDouble(),
            width = (bounds.width / scale).toDouble(),
            height = (bounds.height / scale).toDouble(),
            role = role.aria,
            attribute = role.stateAttribute,
            value = ariaState(state),
        )
    }

    override fun onDetach() {
        WebRoles.remove(key)
        coordinates = null
    }
}

/**
 * Every control that has said what it is, and the watch on the accessibility
 * tree that puts it back.
 *
 * Compose writes an element's role each time it syncs the node, so this watches
 * for that and writes the control's own over it, once Compose has finished:
 * the element is found by its box, which Compose writes on the same sync.
 */
private object WebRoles {
    private var next = 0

    fun newKey(): Int = next++

    fun put(key: Int, left: Double, top: Double, width: Double, height: Double, role: String, attribute: String, value: String) {
        putRole(key, left, top, width, height, role, attribute, value)
    }

    fun remove(key: Int) = removeRole(key)
}

private fun putRole(
    key: Int,
    left: Double,
    top: Double,
    width: Double,
    height: Double,
    role: String,
    attribute: String,
    value: String,
): Unit = js(
    """{
        var k = window.__kontourRoles;
        if (!k) {
            k = window.__kontourRoles = { controls: new Map(), root: null, scheduled: false, observer: null };
            k.find = function () {
                if (k.root && k.root.isConnected) return k.root;
                var all = document.querySelectorAll('div');
                for (var i = 0; i < all.length; i++) {
                    var root = all[i].shadowRoot;
                    var a11y = root && root.getElementById('cmp_a11y_root');
                    if (a11y) { k.root = a11y; return a11y; }
                }
                return null;
            };
            k.near = function (a, b) { return Math.abs(a - b) < 0.75; };
            k.repair = function () {
                k.scheduled = false;
                var root = k.find();
                if (!root) return;
                if (!k.observer) {
                    k.observer = new MutationObserver(function () { k.schedule(); });
                    k.observer.observe(root, { subtree: true, childList: true, attributes: true, attributeFilter: ['role', 'style'] });
                }
                var elements = root.querySelectorAll('[role="button"], [role="tab"], [role="checkbox"], [role="switch"], [role="radio"], [role="menuitemcheckbox"], [role="menuitemradio"]');
                if (elements.length === 0) return;
                // Compose places each element, fixed, at the root's own offset
                // on the page plus the node's bounds in its root.
                var at = root.getBoundingClientRect();
                k.controls.forEach(function (c) {
                    for (var i = 0; i < elements.length; i++) {
                        var e = elements[i], s = e.style;
                        if (!k.near(parseFloat(s.left) - at.left, c.left) || !k.near(parseFloat(s.top) - at.top, c.top) ||
                            !k.near(parseFloat(s.width), c.width) || !k.near(parseFloat(s.height), c.height)) continue;
                        if (e.getAttribute('role') !== c.role) e.setAttribute('role', c.role);
                        if (e.getAttribute(c.attribute) !== c.value) e.setAttribute(c.attribute, c.value);
                    }
                });
            };
            k.schedule = function () {
                if (k.scheduled) return;
                k.scheduled = true;
                // After Compose's own sync of the frame, which is what it repairs.
                requestAnimationFrame(function () { setTimeout(k.repair, 0); });
            };
        }
        k.controls.set(key, { left: left, top: top, width: width, height: height, role: role, attribute: attribute, value: value });
        k.schedule();
    }"""
)

private fun removeRole(key: Int): Unit = js(
    """{
        var k = window.__kontourRoles;
        if (k) k.controls.delete(key);
    }"""
)
