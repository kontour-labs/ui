package io.kontour.ui.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle

/**
 * Whether a focused text field's touches are the browser's here: in a browser
 * on a phone or a tablet, and nowhere else.
 *
 * Asked for as *"the platform's text selection toolbar … needs to show just the
 * native one"*. A browser shows its own selection, its handles and its menu —
 * Copy, Paste, Look Up — only for text in the page that the reader selects with
 * their own finger, and Compose draws its text on a canvas, where there is none.
 * What there is, is the input Compose types through: hidden behind the canvas,
 * holding the field's text and its selection. So while a field is focused that
 * input is laid over the field's text, in the same face at the same size, and
 * takes the touches on it; the browser does the rest, and Compose's own toolbar
 * is not shown for the field. See [browserText].
 *
 * Read once: a browser does not stop being on a phone.
 */
internal expect val textTouchesGoToTheBrowser: Boolean

/**
 * Which of the library's faces a field is set in, for the browser's input to be
 * set in the same one. Null for a face of an app's own, which the browser is
 * given its generic sans-serif for: the selection and the caret are then drawn
 * where the browser's face puts the letters, close to Compose's but not exactly
 * on them.
 */
internal enum class BrowserFace { Outfit, Mono }

/**
 * What a field tells the browser's input about itself, written in composition
 * and read by [browserText]'s node as the field is laid out.
 */
@Stable
internal class BrowserText {
    var state: TextFieldState? = null
    var inputTransformation: InputTransformation? = null
    var focused: Boolean by mutableStateOf(false)
    var singleLine: Boolean = true
    /** A password's dots, which the browser draws as its own. */
    var masked: Boolean = false
    var style: TextStyle = TextStyle.Default
    var face: BrowserFace? = null
    var caret: Color = Color.Unspecified
    var selection: Color = Color.Unspecified
    var scroll: ScrollState? = null
    var layout: TextLayoutResult? by mutableStateOf(null)
}

/**
 * On a field's input, where [textTouchesGoToTheBrowser]: while [text] is focused,
 * the browser's input sits over the field's text and takes its touches. Nothing
 * anywhere else.
 */
internal expect fun Modifier.browserText(text: BrowserText): Modifier
