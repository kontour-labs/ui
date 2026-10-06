@file:OptIn(ExperimentalWasmJsInterop::class)

package io.kontour.ui.platform

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnitType
import io.kontour.ui.generated.resources.Res
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.math.max
import kotlin.math.min
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch

internal actual val textTouchesGoToTheBrowser: Boolean by lazy {
    isIosWebKit(window.navigator.userAgent, window.navigator.platform, browserMaxTouchPoints()) ||
        isTouchFirst(window.navigator.userAgent, primaryPointerIsCoarse())
}

private fun browserMaxTouchPoints(): Int = js("(navigator.maxTouchPoints || 0)")

private fun primaryPointerIsCoarse(): Boolean =
    js("(typeof matchMedia === 'function') && matchMedia('(pointer: coarse)').matches")

internal actual fun Modifier.browserText(text: BrowserText): Modifier =
    if (textTouchesGoToTheBrowser) this then BrowserTextElement(text) else this

private class BrowserTextElement(val text: BrowserText) : ModifierNodeElement<BrowserTextNode>() {
    override fun create() = BrowserTextNode(text)

    override fun update(node: BrowserTextNode) {
        node.text = text
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "browserText"
    }

    override fun equals(other: Any?) = other is BrowserTextElement && other.text === text

    override fun hashCode() = text.hashCode()
}

/**
 * Lays the browser's input over its field's text while the field is focused, and
 * lets go of it when it is not. See [textTouchesGoToTheBrowser].
 */
private class BrowserTextNode(var text: BrowserText) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    private var coordinates: LayoutCoordinates? = null

    override fun onAttach() {
        // What moves the text without moving the field: the text being laid out
        // again, and the field scrolling it.
        coroutineScope.launch {
            snapshotFlow { Triple(text.focused, text.layout, text.scroll?.value) }.collect { publish() }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        publish()
    }

    override fun onDetach() {
        BrowserTextOverlay.release(this)
        coordinates = null
    }

    private fun publish() {
        val at = coordinates
        val layout = text.layout
        if (!text.focused || at == null || !at.isAttached || layout == null) {
            BrowserTextOverlay.release(this)
            return
        }
        val density = currentValueOf(LocalDensity)
        val scale = density.density
        val style = text.style
        val origin = at.positionInWindow()
        val shown = at.boundsInWindow()
        val fontSize = with(density) { style.fontSize.toPx() } / scale
        val lineHeight = if (style.lineHeight.type == TextUnitType.Unspecified) {
            (layout.getLineBottom(0) - layout.getLineTop(0)) / scale
        } else {
            with(density) { style.lineHeight.toPx() } / scale
        }
        val spacing = when (style.letterSpacing.type) {
            TextUnitType.Em -> style.letterSpacing.value * fontSize
            TextUnitType.Sp -> with(density) { style.letterSpacing.toPx() } / scale
            else -> 0f
        }
        val scrolled = (text.scroll?.value ?: 0) / scale
        val single = text.singleLine
        BrowserTextOverlay.claim(
            node = this,
            geometry = TextGeometry(
                left = origin.x / scale - if (single) scrolled else 0f,
                textTop = origin.y / scale - if (single) 0f else scrolled,
                firstBaseline = layout.firstBaseline / scale,
                // As wide as the text, so the browser never scrolls it on its
                // own: where it is scrolled to is Compose's to say.
                width = if (single) max(at.size.width / scale + scrolled, layout.size.width / scale + fontSize) else at.size.width / scale,
                height = if (single) lineHeight else max(layout.size.height / scale, at.size.height / scale + scrolled),
                shown = Rect(shown.left / scale, shown.top / scale, shown.right / scale, shown.bottom / scale),
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = spacing,
                weight = (style.fontWeight ?: FontWeight.Normal).weight,
                face = text.face,
                rtl = currentValueOf(LocalLayoutDirection) == LayoutDirection.Rtl,
                caret = text.caret,
                selection = text.selection,
                singleLine = single,
                masked = text.masked,
            ),
        )
    }

    /** A paste, a cut or a drop the browser's own menu made, onto the field's state. */
    fun edit(kind: String, inserted: String, start: Int, end: Int) {
        val state = text.state ?: return
        val transformation = text.inputTransformation
        val typed = if (text.singleLine) inserted.replace(LineBreaks, " ") else inserted
        state.edit {
            val from = min(start, end).coerceIn(0, length)
            val to = max(start, end).coerceIn(0, length)
            when (kind) {
                "insert" -> {
                    replace(from, to, typed)
                    selection = TextRange(from + typed.length)
                }
                "delete" -> {
                    if (from < to) replace(from, to, "")
                    selection = TextRange(from)
                }
            }
            transformation?.let { with(it) { transformInput() } }
        }
    }
}

private val LineBreaks = Regex("\r\n|\n|\r")

/** Where a field's text is and what it is set in, in CSS pixels from the top left of its Compose surface. */
private class TextGeometry(
    val left: Float,
    /** The top of the text, as Compose lays it out, scrolled. */
    val textTop: Float,
    val firstBaseline: Float,
    val width: Float,
    val height: Float,
    /** The part of the field on screen. */
    val shown: Rect,
    val fontSize: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    val weight: Int,
    val face: BrowserFace?,
    val rtl: Boolean,
    val caret: Color,
    val selection: Color,
    val singleLine: Boolean,
    val masked: Boolean,
)

/**
 * The one field the browser's input is over, and the page-wide parts of it: the
 * stylesheet in Compose's shadow root, the library's faces for the browser, and
 * the listener that takes the menu's paste and cut.
 */
private object BrowserTextOverlay {
    private var owner: BrowserTextNode? = null
    private var host: JsAny? = null

    fun claim(node: BrowserTextNode, geometry: TextGeometry) {
        val surface = host ?: findComposeHost()?.also { found ->
            host = found
            installFaces(
                Res.getUri("font/outfit_regular.ttf"),
                Res.getUri("font/outfit_medium.ttf"),
                Res.getUri("font/outfit_semibold.ttf"),
                Res.getUri("font/outfit_bold.ttf"),
                Res.getUri("font/outfit_extrabold.ttf"),
                Res.getUri("font/jetbrains_mono_regular.ttf"),
                Res.getUri("font/jetbrains_mono_bold.ttf"),
            )
            installStyle(found)
            installEdits(found) { kind, inserted, start, end -> owner?.edit(kind, inserted, start, end) }
        } ?: return
        owner = node
        val family = when (geometry.face) {
            BrowserFace.Outfit -> "KontourOutfit, system-ui, sans-serif"
            BrowserFace.Mono -> "KontourMono, ui-monospace, monospace"
            null -> "system-ui, sans-serif"
        }
        place(
            host = surface,
            left = geometry.left.toDouble(),
            textTop = geometry.textTop.toDouble(),
            baseline = geometry.firstBaseline.toDouble(),
            width = geometry.width.toDouble(),
            height = geometry.height.toDouble(),
            shownLeft = geometry.shown.left.toDouble(),
            shownTop = geometry.shown.top.toDouble(),
            shownRight = geometry.shown.right.toDouble(),
            shownBottom = geometry.shown.bottom.toDouble(),
            size = geometry.fontSize.toDouble(),
            line = geometry.lineHeight.toDouble(),
            spacing = geometry.letterSpacing.toDouble(),
            weight = geometry.weight,
            family = family,
            rtl = geometry.rtl,
            caret = cssColour(geometry.caret),
            selection = cssColour(geometry.selection),
            multiline = !geometry.singleLine,
            masked = geometry.masked,
        )
    }

    fun release(node: BrowserTextNode) {
        if (owner !== node) return
        owner = null
        host?.let { clear(it) }
    }
}

private fun cssColour(colour: Color): String {
    if (!colour.isSpecified) return "auto"
    val argb = colour.toArgb()
    val a = ((argb ushr 24) and 0xFF) / 255.0
    return "rgba(${(argb shr 16) and 0xFF}, ${(argb shr 8) and 0xFF}, ${argb and 0xFF}, $a)"
}

/** The element whose shadow root holds Compose's canvas: the first, as a page has one Compose surface. */
private fun findComposeHost(): JsAny? = js(
    """{
        var all = document.querySelectorAll('div');
        for (var i = 0; i < all.length; i++) {
            var root = all[i].shadowRoot;
            if (root && root.querySelector('canvas')) return all[i];
        }
        return null;
    }"""
)

/** The library's two faces, for the browser's input to set a field's text in. */
private fun installFaces(
    regular: String,
    medium: String,
    semiBold: String,
    bold: String,
    extraBold: String,
    mono: String,
    monoBold: String,
): Unit = js(
    """{
        if (document.getElementById('kontour-faces')) return;
        var css = '';
        var faces = [['KontourOutfit', regular, 400], ['KontourOutfit', medium, 500], ['KontourOutfit', semiBold, 600],
                     ['KontourOutfit', bold, 700], ['KontourOutfit', extraBold, 800], ['KontourMono', mono, 400], ['KontourMono', monoBold, 700]];
        for (var i = 0; i < faces.length; i++) {
            css += "@font-face{font-family:'" + faces[i][0] + "';src:url('" + faces[i][1] + "') format('truetype');font-weight:" + faces[i][2] + ";font-display:block}";
        }
        var style = document.createElement('style');
        style.id = 'kontour-faces';
        style.textContent = css;
        document.head.appendChild(style);
    }"""
)

/**
 * The rules that lay Compose's input over the focused field, in the shadow root
 * the input is in — the only place a rule can reach it. Every one of them keyed
 * on the host's `data-kontour-text`, so with no field claimed, Compose's input is
 * exactly as Compose left it.
 *
 * Its text is drawn in nothing: Compose draws the field's text, and the browser
 * draws its caret, its selection and its handles where its own letters are,
 * which, in the same face at the same size, is over Compose's.
 */
private fun installStyle(host: JsAny): Unit = js(
    """{
        var root = host.shadowRoot;
        if (root.getElementById('kontour-text')) return;
        var style = document.createElement('style');
        style.id = 'kontour-text';
        var f = ':host([data-kontour-text]) .compose-backing-field';
        style.textContent =
            f + '{' +
                'left:var(--kt-left)!important;top:var(--kt-top)!important;' +
                'width:var(--kt-width)!important;height:var(--kt-height)!important;' +
                'transform:scale(var(--kt-scale));transform-origin:0 0;' +
                'clip-path:inset(var(--kt-clip))!important;' +
                'z-index:1!important;pointer-events:auto!important;' +
                'font-family:var(--kt-family)!important;font-size:var(--kt-size)!important;' +
                'font-weight:var(--kt-weight)!important;line-height:var(--kt-line)!important;' +
                'letter-spacing:var(--kt-spacing)!important;font-feature-settings:normal;' +
                'color:transparent!important;-webkit-text-fill-color:transparent;caret-color:var(--kt-caret)!important;' +
                '-webkit-user-select:text!important;user-select:text!important;-webkit-touch-callout:default!important;' +
                'white-space:pre!important;overflow:hidden!important;padding:0!important;margin:0!important;border:0!important;' +
                'align-content:start!important;text-align:start;direction:var(--kt-dir);' +
                'background:transparent!important;outline:none;resize:none;-webkit-tap-highlight-color:transparent;' +
                '-webkit-text-size-adjust:100%;text-size-adjust:100%;' +
            '}' +
            ':host([data-kontour-text][data-kontour-multiline]) .compose-backing-field{white-space:pre-wrap!important;overflow-wrap:break-word;}' +
            ':host([data-kontour-text][data-kontour-masked]) .compose-backing-field{-webkit-text-security:disc;}' +
            f + '::selection{background:var(--kt-selection);color:transparent;}';
        root.appendChild(style);
    }"""
)

/**
 * The edits a browser's own menu makes — Paste, Cut, a drop — which Compose's
 * input does not pass on: it hears the keyboard's typing and nothing it does not
 * know the name of. Taken before the input changes, and made to the field's
 * state instead; Compose then writes the state back into the input.
 */
private fun installEdits(host: JsAny, edit: (String, String, Int, Int) -> Unit) {
    listenForEdits(host) { kind, inserted, start, end -> edit(kind, inserted, start, end) }
}

private fun listenForEdits(host: JsAny, onEdit: (String, String, Int, Int) -> Unit): Unit = js(
    """{
        host.shadowRoot.addEventListener('beforeinput', function (e) {
            if (!host.hasAttribute('data-kontour-text')) return;
            var input = e.target;
            if (!input || !input.classList || !input.classList.contains('compose-backing-field')) return;
            var t = e.inputType;
            var kind = null, text = '';
            if (t === 'insertFromPaste' || t === 'insertFromPasteAsQuotation' || t === 'insertFromDrop' || t === 'insertFromYank') {
                kind = 'insert';
                text = (e.dataTransfer && e.dataTransfer.getData('text/plain')) || e.data || '';
            } else if (t === 'deleteByCut' || t === 'deleteByDrag' || t === 'deleteContentForward') {
                kind = 'delete';
            }
            if (kind === null) return;
            var start = input.selectionStart, end = input.selectionEnd;
            if (kind === 'delete' && t === 'deleteContentForward' && start === end) end = start + 1;
            if (e.cancelable) e.preventDefault();
            onEdit(kind, text, start, end);
        }, true);
    }"""
)

/**
 * Lays the input out over the field: where its text is, in its face and size,
 * clipped to the part of the field on screen. The baseline is matched rather
 * than the top, as the browser and Compose lay a line's leading out differently,
 * and the browser's line box is worked out from the face's own ascent and
 * descent, which only the browser can measure.
 *
 * Under 16px, set at 16px and scaled down to the size: an iPhone zooms the
 * whole page in to an input focused with smaller type.
 */
private fun place(
    host: JsAny,
    left: Double,
    textTop: Double,
    baseline: Double,
    width: Double,
    height: Double,
    shownLeft: Double,
    shownTop: Double,
    shownRight: Double,
    shownBottom: Double,
    size: Double,
    line: Double,
    spacing: Double,
    weight: Int,
    family: String,
    rtl: Boolean,
    caret: String,
    selection: String,
    multiline: Boolean,
    masked: Boolean,
): Unit = js(
    """{
        var k = Math.min(1, size / 16);
        var cssSize = size / k;
        // Where the browser puts the first baseline, and so where its box goes
        // for that baseline to be Compose's. The two inputs differ. A text
        // area lays its first line out as a line of text, which is asked of
        // the browser's own layout: a line of the same type with a mark on its
        // baseline, rather than worked out from the face's metrics, which the
        // browser rounds its own way. A one-line input centres its text in its
        // box by the face's ascent and descent, which a canvas measures.
        var inBox;
        if (multiline) {
            var probe = window.__kontourProbe;
            if (!probe) {
                probe = document.createElement('div');
                probe.setAttribute('aria-hidden', 'true');
                probe.style.cssText = 'position:fixed;left:-10000px;top:0;visibility:hidden;white-space:pre;padding:0;margin:0;border:0';
                probe.innerHTML = 'Hg<span style="display:inline-block;width:0;height:0;vertical-align:baseline"></span>';
                document.body.appendChild(probe);
                window.__kontourProbe = probe;
            }
            probe.style.fontFamily = family;
            probe.style.fontWeight = String(weight);
            probe.style.fontSize = cssSize + 'px';
            probe.style.lineHeight = (line / k) + 'px';
            inBox = (probe.lastChild.getBoundingClientRect().top - probe.getBoundingClientRect().top) * k;
        } else {
            var pen = window.__kontourPen || (window.__kontourPen = document.createElement('canvas').getContext('2d'));
            pen.font = weight + ' ' + cssSize + 'px ' + family;
            var m = pen.measureText('Hg');
            var ascent = (m.fontBoundingBoxAscent !== undefined ? m.fontBoundingBoxAscent : m.actualBoundingBoxAscent * 1.1) * k;
            var descent = (m.fontBoundingBoxDescent !== undefined ? m.fontBoundingBoxDescent : m.actualBoundingBoxDescent * 1.1) * k;
            inBox = (line - (ascent + descent)) / 2 + ascent;
        }
        var top = textTop + baseline - inBox;
        var h = multiline ? height + (top < textTop ? textTop - top : 0) : line;
        // Clipped to the field on screen, a line's leading spare above and below.
        var slack = line / 2;
        var clipTop = Math.max(0, shownTop - slack - top);
        var clipLeft = Math.max(0, shownLeft - left);
        var clipRight = Math.max(0, left + width - shownRight);
        var clipBottom = Math.max(0, top + h - (shownBottom + slack));
        var s = host.style;
        s.setProperty('--kt-left', left + 'px');
        s.setProperty('--kt-top', top + 'px');
        s.setProperty('--kt-width', (width / k) + 'px');
        s.setProperty('--kt-height', (h / k) + 'px');
        s.setProperty('--kt-scale', String(k));
        s.setProperty('--kt-clip', (clipTop / k) + 'px ' + (clipRight / k) + 'px ' + (clipBottom / k) + 'px ' + (clipLeft / k) + 'px');
        s.setProperty('--kt-size', cssSize + 'px');
        s.setProperty('--kt-line', (line / k) + 'px');
        s.setProperty('--kt-spacing', (spacing / k) + 'px');
        s.setProperty('--kt-weight', String(weight));
        s.setProperty('--kt-family', family);
        s.setProperty('--kt-dir', rtl ? 'rtl' : 'ltr');
        s.setProperty('--kt-caret', caret);
        s.setProperty('--kt-selection', selection);
        if (multiline) host.setAttribute('data-kontour-multiline', ''); else host.removeAttribute('data-kontour-multiline');
        if (masked) host.setAttribute('data-kontour-masked', ''); else host.removeAttribute('data-kontour-masked');
        host.setAttribute('data-kontour-text', '');
    }"""
)

private fun clear(host: JsAny): Unit = js(
    """{
        host.removeAttribute('data-kontour-text');
        host.removeAttribute('data-kontour-multiline');
        host.removeAttribute('data-kontour-masked');
    }"""
)
