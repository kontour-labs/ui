package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.nav.navigationSuitePadding

/**
 * A page's vertical scroll with the library's scrollbar on it.
 *
 * Every page in the showcase shows one, the mini-apps' too — asked for as *"can
 * you put the scrollbar in every page in our showcase app (including all the
 * mini-apps)? just so we can demo it"*. The bar keeps clear of a floating
 * navigation bar the page sits under; a sheet or a page that covers the
 * navigation passes no padding.
 */
@Composable
internal fun Modifier.pageScroll(
    state: ScrollState = rememberScrollState(),
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    barPadding: PaddingValues = navigationSuitePadding(),
): Modifier = scrollbar(state, contentPadding = barPadding).verticalScroll(state, flingBehavior = flingBehavior)
