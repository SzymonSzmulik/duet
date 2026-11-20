package app.duet.music.spotify

import android.content.Context
import android.view.View
import android.webkit.WebView

/**
 * A WebView that Chromium always treats as visible, adapted from Myspoti's `AwakeWebView`
 * (host-android/WebPlayerHost.kt, GPL-3.0, docs/THIRD_PARTY.md).
 *
 * The Spotify Web Player page runs off screen. A page Chromium sees as hidden has its timers held to
 * one wake-up a second, then one a minute, and its player stalls; a view that is not attached to any
 * window is hidden to it. So the view is attached to the activity's window but INVISIBLE (never
 * drawn, no cost), while Chromium is told it is visible, and leaving the window (app closed) is kept
 * from Chromium so the music doesn't stop.
 */
class AwakeWebView(context: Context) : WebView(context) {
    override fun getVisibility(): Int = View.VISIBLE
    override fun getWindowVisibility(): Int = View.VISIBLE
    override fun onWindowVisibilityChanged(visibility: Int) = super.onWindowVisibilityChanged(View.VISIBLE)

    private var detachHeld = false

    override fun onDetachedFromWindow() {
        // The activity went away (app closed): Chromium would hide the page and stop the music.
        detachHeld = true
    }

    override fun onAttachedToWindow() {
        if (detachHeld) {
            detachHeld = false
            super.onDetachedFromWindow()
        }
        super.onAttachedToWindow()
    }
}
