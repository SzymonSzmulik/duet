package app.duet.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.duet.music.ui.App

class MainActivity : ComponentActivity() {
    /** Keep sign-in cookies on disk whenever Duet leaves the screen, in case the process is killed next. */
    override fun onStop() {
        super.onStop()
        android.webkit.CookieManager.getInstance().flush()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as DuetApplication).graph
        setContent { App(graph) }
        // Spotify's hidden Web Player lives in this window (invisible), so Chromium keeps it running.
        (application as DuetApplication).spotify.attachHidden(findViewById(android.R.id.content))
        // Android 13+: the media notification (play/pause on the lock screen) needs this.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
