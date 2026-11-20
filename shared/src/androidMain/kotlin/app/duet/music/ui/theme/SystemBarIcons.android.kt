package app.duet.music.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, darkIcons) {
        var ctx = view.context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        val controller = (ctx as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = darkIcons
        controller?.isAppearanceLightNavigationBars = darkIcons
        onDispose {
            if (controller != null && before != null) {
                controller.isAppearanceLightStatusBars = before
                controller.isAppearanceLightNavigationBars = before
            }
        }
    }
}
