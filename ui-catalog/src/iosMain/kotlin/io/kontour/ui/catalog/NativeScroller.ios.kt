package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UILabel
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewDecelerationRateNormal
import platform.UIKit.UIScrollViewDelegateProtocol
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.secondarySystemBackgroundColor
import platform.darwin.NSObject

internal actual val hasNativeScroller: Boolean = true

/**
 * A `UIScrollView` at UIKit's normal rate, with [rows] labels 44 points apart:
 * the list an iPhone's own apps scroll, to throw beside the library's.
 *
 * Its throw is read from UIKit itself — the velocity `scrollViewWillEndDragging`
 * is handed, in points a millisecond, and the distance from there to where
 * `scrollViewDidEndDecelerating` finds it stopped.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun NativeScroller(modifier: Modifier, rows: Int, onThrow: (Throw) -> Unit) {
    val report by rememberUpdatedState(onThrow)
    val delegate = remember { ThrowReader { report(it) } }
    UIKitView(
        factory = {
            UIScrollView().apply {
                decelerationRate = UIScrollViewDecelerationRateNormal
                backgroundColor = UIColor.secondarySystemBackgroundColor
                val row = RowHeightDp.toDouble()
                repeat(rows) { index ->
                    addSubview(
                        UILabel(frame = CGRectMake(16.0, index * row, 1000.0, row)).apply {
                            text = "UIKit ${index + 1}"
                            autoresizingMask = UIViewAutoresizingFlexibleWidth
                        },
                    )
                }
                setContentSize(CGSizeMake(0.0, rows * row))
                setDelegate(delegate)
            }
        },
        modifier = modifier,
        // Its own touches from the first moment, as a list's are: a cooperative
        // view waits to see whether Compose wants the drag first.
        properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative),
    )
}

/** Held by the composable: a scroll view's delegate is weak. */
@OptIn(ExperimentalForeignApi::class)
private class ThrowReader(private val onThrow: (Throw) -> Unit) : NSObject(), UIScrollViewDelegateProtocol {
    private var releasedAt = 0.0
    private var released = 0.0

    override fun scrollViewWillEndDragging(
        scrollView: UIScrollView,
        withVelocity: CValue<CGPoint>,
        targetContentOffset: CPointer<CGPoint>?,
    ) {
        releasedAt = scrollView.contentOffset.useContents { y }
        // Points a millisecond, and the other way up from Compose's: a throw
        // that moves the content up is a positive offset change.
        released = withVelocity.useContents { y } * 1000.0
        if (released == 0.0) onThrow(Throw(0f, 0f))
    }

    override fun scrollViewDidEndDecelerating(scrollView: UIScrollView) {
        val travelled = scrollView.contentOffset.useContents { y } - releasedAt
        onThrow(Throw(released.toFloat(), travelled.toFloat()))
    }
}
