package app.duet.music.ytmusic

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import app.duet.music.extension.WebLoginSession
import app.duet.music.weblogin.WebLoginHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google sign-in for YouTube Music, on Google's own page in a WebView (as SpatialFlow's
 * `GoogleSignInScreen`, Apache-2.0, docs/THIRD_PARTY.md). Signed in = music.youtube.com sees the
 * `SAPISID` and `HSID` cookies. The WebView exists only while the login screen is open.
 */
class AndroidYtMusicLogin(appContext: Context) : WebLoginHost(appContext), WebLoginSession {
    override val site = SITE
    override val serviceName = "YouTube Music"
    private val cookies = CookieManager.getInstance()

    private val _signedIn = MutableStateFlow(hasSession())
    override val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private var web: WebView? = null
    override val view: WebView get() = web ?: createView().also { web = it }

    override fun cookieHeader(): String? = if (hasSession()) cookies.getCookie(MUSIC_URL) else null

    override fun signIn() {
        openLoginScreen(LOGIN_URL)
    }

    override fun signOut() {
        clearCookies(
            listOf(MUSIC_URL, "https://www.youtube.com/", "https://accounts.google.com/", "https://www.google.com/"),
            listOf(".youtube.com", ".google.com", "accounts.google.com"),
        )
        _signedIn.value = hasSession()
    }

    override fun afterLogin() {
        web?.destroy()
        web = null
        _signedIn.value = hasSession()
    }

    private fun hasSession(): Boolean = cookieNames(MUSIC_URL).containsAll(listOf("SAPISID", "HSID"))

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView(): WebView = WebView(webContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Google's sign-in accepts the phone's own WebView identity (Metrolist signs in this way); a
        // desktop user agent next to Android client hints is what it calls "may not be secure".
        settings.setSupportMultipleWindows(false)
        // Without the "; wv" marker Android adds for in-app browsers, which Google's check looks for.
        settings.userAgentString = WebSettings.getDefaultUserAgent(appContext).replace("; wv", "")
        hideAppIdentity(this)
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.scheme != "https" && request.url.scheme != "http"

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { pageLoading.value = true }

            override fun onPageFinished(view: WebView, url: String) {
                pageLoading.value = false
                if (url.startsWith(MUSIC_URL) && hasSession()) {
                    cookies.flush()
                    _signedIn.value = true
                    onSignedIn?.invoke()
                }
            }
        }
    }

    companion object {
        const val SITE = "ytmusic"
        const val MUSIC_URL = "https://music.youtube.com/"
        // Same address as SpatialFlow's GoogleSignInScreen.
        const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https://music.youtube.com/"
    }
}
