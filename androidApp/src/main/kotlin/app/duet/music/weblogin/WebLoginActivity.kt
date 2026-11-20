package app.duet.music.weblogin

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import app.duet.music.DuetApplication
import app.duet.music.ui.login.webLoginView

/** Shows a site's own login page full screen ([WebLoginHost]); closes once the site has a session. */
class WebLoginActivity : ComponentActivity() {
    private var host: WebLoginHost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val host = (application as DuetApplication).loginHost(intent.getStringExtra(EXTRA_SITE))
            ?: return finish()
        this.host = host
        val web = host.view
        host.useContext(this)
        host.onSignedIn = { finish() }

        val accent = (application as DuetApplication).graph.settings.accent.value
        setContentView(webLoginView(this, host.serviceName, accent, web, host.pageLoading, onCancel = ::finish))
        host.pendingUrl?.let { url ->
            host.pageLoading.value = true
            // Load once the WebView has its real size.
            web.post { web.loadUrl(url) }
            host.pendingUrl = null
        }
    }

    override fun onDestroy() {
        host?.let {
            (it.view.parent as? ViewGroup)?.removeView(it.view)
            it.onSignedIn = null
            it.useContext(null)
            it.afterLogin()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SITE = "site"
    }
}
