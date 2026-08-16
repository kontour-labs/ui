package io.kontour.ui.samples

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.rememberImeChain

@Composable
fun TextFieldBasics() {
    val query = rememberTextFieldState()

    TextField(state = query, label = "Where to?", placeholder = "Station, stop or address")
}

@Composable
fun ImeChainForm() {
    val from = rememberTextFieldState()
    val to = rememberTextFieldState()
    val note = rememberTextFieldState()

    val chain = rememberImeChain("from", "to", "note", onSubmit = { plan() })

    TextField(state = from, label = "From", imeChain = chain["from"])
    TextField(state = to, label = "To", imeChain = chain["to"])
    TextField(state = note, label = "Note", imeChain = chain["note"])
}
