package app.duet.music.spotify

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.duet.music.sources.spotify.SpotifyWebSession
import app.duet.music.weblogin.WebLoginHost
import app.duet.music.sources.spotify.SpotifyWebToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Spotify Web Player session in a WebView (approach from Myspoti's `WebPlayerHost`, GPL-3.0,
 * https://github.com/kota-zo/myspoti; see docs/THIRD_PARTY.md):
 * - Sign-in: Spotify's own login page, shown full screen by [WebLoginActivity]. Duet never
 *   sees the password; the session is the `sp_dc` cookie in the WebView's cookie store.
 * - Token: a document-start script on open.spotify.com copies the answer of the page's own
 *   `/api/token` request to [token]. The page keeps refreshing it, so nothing here computes a
 *   TOTP or reads Spotify's code. The page stays loaded off screen while the app runs.
 * Main thread only.
 */
class AndroidSpotifyWebSession(appContext: Context) : WebLoginHost(appContext), SpotifyWebSession {
    override val site = SITE
    override val serviceName = "Spotify"
    private val main = Handler(Looper.getMainLooper())
    private val cookies = CookieManager.getInstance()

    private val _token = MutableStateFlow<SpotifyWebToken?>(null)
    override val token: StateFlow<SpotifyWebToken?> = _token.asStateFlow()

    private val _signedIn = MutableStateFlow(hasSession())
    override val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    /** The off-screen Web Player: token, search and playback. Never shown. */
    private val playerView: WebView by lazy { createView() }

    /**
     * The sign-in page gets its own fresh WebView, created when the sign-in screen opens: the
     * long-lived player view loaded the login page but drew nothing when shown (D26). Cookies are
     * shared through CookieManager, so the player picks up the session afterwards.
     */
    private var loginView: WebView? = null
    override val view: WebView get() = loginView ?: createLoginView().also { loginView = it }

    /**
     * Puts the off-screen player in [container] (the activity's root), full size but INVISIBLE: it is
     * never drawn, yet it lays out like a real window and Chromium treats it as visible ([AwakeWebView]).
     */
    fun attachHidden(container: android.view.ViewGroup) {
        val v = playerView
        (v.parent as? android.view.ViewGroup)?.removeView(v)
        container.addView(v, 0, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        v.visibility = android.view.View.INVISIBLE
        v.isFocusable = false
        v.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    init {
        // Loaded signed out too: the page's anonymous token is enough to search Spotify.
        playerView.loadUrl(PLAYER_URL)
    }

    override fun signIn() {
        openLoginScreen(LOGIN_URL)
    }

    /** The sign-in screen closed: drop its WebView; after a sign-in, reload the player as the user. */
    override fun afterLogin() {
        loginView?.destroy()
        loginView = null
        if (_signedIn.value) {
            _token.value = null
            playerView.loadUrl(PLAYER_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createLoginView(): WebView = WebView(webContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // The phone's own WebView identity: reCAPTCHA on Spotify's login rejects a desktop identity it can
        // tell is a WebView (D34). The sign-in only needs the session cookie; the player keeps the desktop identity.
        settings.userAgentString = WebSettings.getDefaultUserAgent(appContext).replace("; wv", "")
        hideAppIdentity(this)
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                // Stay on web pages; app links such as spotify:// would leave the sign-in.
                request.url.scheme != "https" && request.url.scheme != "http"

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { pageLoading.value = true; check(url) }
            override fun onPageFinished(view: WebView, url: String) { pageLoading.value = false; check(url) }
        }
    }

    override fun signOut() {
        clearCookies(listOf(PLAYER_URL, "https://accounts.spotify.com/", "https://www.spotify.com/"), listOf(".spotify.com", "spotify.com"))
        android.webkit.WebStorage.getInstance().deleteOrigin("https://open.spotify.com")
        _token.value = null
        _signedIn.value = false
        playerView.loadUrl(PLAYER_URL)
    }

    private fun hasSession(): Boolean = cookies.getCookie(PLAYER_URL)?.split(';')?.any { it.trim().startsWith("sp_dc=") } == true

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView(): WebView = AwakeWebView(webContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        // Spotify serves the full Web Player only to desktop browsers (as Myspoti does).
        desktopIdentity(this)
        hideAppIdentity(this)
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(Bridge(), "DuetBridge")
        // Playback commands leave from the app, not the WebView (see SpotifyControlRelay).
        SpotifyControlRelay(this).install()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // First, so the other scripts' fetch wrappers sit on top of the relay.
            WebViewCompat.addDocumentStartJavaScript(this, SpotifyControlRelay.SCRIPT, setOf("https://open.spotify.com"))
            WebViewCompat.addDocumentStartJavaScript(this, TOKEN_HOOK_JS, setOf("https://open.spotify.com"))
        } else {
            Log.w(TAG, "WebView too old for document-start scripts; Spotify token capture is off")
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, DIAG_JS, setOf("https://open.spotify.com"))
        }
        webChromeClient = object : android.webkit.WebChromeClient() {
            // The page's errors and its playback requests (DIAG_JS), so the log shows why a song didn't play.
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                val level = m.messageLevel()
                if (m.message().startsWith("[duet]") || level == android.webkit.ConsoleMessage.MessageLevel.ERROR)
                    Log.i(PAGE_TAG, m.message().take(400))
                return true
            }

            // Spotify's player needs Widevine (DRM) to play; grant only that, only to Spotify's own page.
            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                val drm = android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                if (request.origin.host?.endsWith("spotify.com") == true && drm in request.resources) request.grant(arrayOf(drm)) else request.deny()
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                // Stay on web pages; app links such as spotify:// would leave the sign-in.
                request.url.scheme != "https" && request.url.scheme != "http"

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                val u = request.url.toString()
                // Ad audio is refused, so an ad at the start of a play moves on by itself (D52).
                if (app.duet.music.sources.spotify.SpotifyAds.isAdHost(u))
                    return android.webkit.WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                if (request.method != "GET" || !u.startsWith(TOKEN_URL)) return null
                // The page asks for its token from a worker our script can't reach, so Duet makes that
                // same request for it, keeps the token, and hands the page the same answer.
                return runCatching { proxyToken(request) }.onFailure { Log.w(TAG, "token request failed", it) }.getOrNull()
            }

        }
    }

    private fun check(url: String) {
        val onPlayer = url.startsWith(PLAYER_URL)
        if (onPlayer && hasSession()) {
            if (!_signedIn.value) {
                cookies.flush()
                _signedIn.value = true
            }
            onSignedIn?.invoke()
        }
    }

    /** Runs [js] in the Web Player page; [done] gets the result as JSON text. Main thread. */
    fun evaluate(js: String, done: (String?) -> Unit = {}) = playerView.evaluateJavascript(js, done)

    /** Runs the page's own `/api/token` request (its headers plus the session cookies) and keeps the token. */
    private fun proxyToken(request: WebResourceRequest): android.webkit.WebResourceResponse {
        val c = (java.net.URL(request.url.toString()).openConnection() as java.net.HttpURLConnection).apply {
            request.requestHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            cookies.getCookie(PLAYER_URL)?.let { setRequestProperty("Cookie", it) }
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        if (code == 200) Bridge().token(String(body))
        val headers = c.headerFields.filterKeys { it != null && !it.equals("content-length", true) && !it.equals("content-encoding", true) }
            .mapValues { it.value.joinToString(", ") }
        return android.webkit.WebResourceResponse(
            "application/json", "utf-8", code, c.responseMessage?.ifBlank { null } ?: "OK", headers, java.io.ByteArrayInputStream(body),
        )
    }

    private val pending = HashMap<Int, kotlinx.coroutines.CompletableDeferred<Pair<Int, String>>>()
    private var nextRequest = 0

    override suspend fun pageFetch(url: String, method: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        val id = nextRequest++
        val answer = kotlinx.coroutines.CompletableDeferred<Pair<Int, String>>()
        pending[id] = answer
        val js = "(function(){fetch(" + JSONObject.quote(url) + ",{method:" + JSONObject.quote(method) +
            ",headers:" + JSONObject(headers).toString() + (body?.let { ",body:" + JSONObject.quote(it) } ?: "") +
            ",credentials:'include'}).then(function(r){return r.text().then(function(t){DuetBridge.fetched($id,r.status,t);});})" +
            ".catch(function(e){DuetBridge.fetched($id,0,String(e));});})()"
        main.post { playerView.evaluateJavascript(js, null) }
        return try {
            kotlinx.coroutines.withTimeout(20_000) { answer.await() }
        } finally {
            main.post { pending.remove(id) }
        }
    }

    private val _apiHeaders = MutableStateFlow<Map<String, String>>(emptyMap())
    override val apiHeaders: StateFlow<Map<String, String>> = _apiHeaders.asStateFlow()

    private inner class Bridge {
        @JavascriptInterface
        fun fetched(id: Int, status: Int, body: String) {
            main.post { pending.remove(id)?.complete(status to body) }
        }

        @JavascriptInterface
        fun headers(json: String) {
            val h = runCatching { JSONObject(json).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrNull() ?: return
            main.post { _apiHeaders.value = h; Log.i(TAG, "web player headers: ${h.keys}") }
        }

        @JavascriptInterface
        fun token(json: String) {
            val t = runCatching {
                val o = JSONObject(json)
                // The page's hook sends expiresAtMs; the raw /api/token answer names it accessTokenExpirationTimestampMs.
                val expires = o.optLong("expiresAtMs").takeIf { it > 0 } ?: o.optLong("accessTokenExpirationTimestampMs")
                SpotifyWebToken(o.getString("accessToken"), expires, o.optBoolean("isAnonymous"))
            }.getOrNull() ?: return
            main.post {
                if (!t.isAnonymous && !_signedIn.value && hasSession()) _signedIn.value = true
                _token.value = t
                Log.i(TAG, "web token: anonymous=${t.isAnonymous} expiresIn=${(t.expiresAtMs - System.currentTimeMillis()) / 1000}s")
            }
        }
    }

    companion object {
        private const val TAG = "DuetSpotify"
        private const val PAGE_TAG = "DuetSpotifyPage"

        /**
         * Read-only diagnostics in the Web Player (after Myspoti's player-bridge.js, GPL-3.0): the
         * playback requests with their status (licence, device, track), uncaught errors, and whether
         * Widevine is available. Written to the console as "[duet] ..." for [PAGE_TAG].
         */
        private val DIAG_JS = """
            (function () {
              var log = function (s) { try { console.warn('[duet] ' + s); } catch (e) {} };
              var PLAY = /player\/command|track-playback|connect-state\/v1\/devices|widevine-license|storage-resolve|audio-.*\.scdn\.co|apresolve/;
              try {
                new PerformanceObserver(function (list) {
                  list.getEntries().forEach(function (e) {
                    if (PLAY.test(e.name)) log('req ' + (e.responseStatus || '?') + ' ' + e.name.split('?')[0].slice(0, 120) + ' ' + (e.transferSize || 0) + 'B');
                  });
                }).observe({ type: 'resource', buffered: true });
              } catch (e) {}
              // Real status and answer of Spotify Connect calls (resource timing hides them cross-origin).
              try {
                var F = window.fetch;
                window.fetch = function (input, init) {
                  var u = String((input && input.url) || input);
                  var p = F.apply(this, arguments);
                  if (/connect-state|player\/command|track-playback/.test(u)) p.then(function (r) {
                    r.clone().text().then(function (t) { var m = t.match(/"(disallow[a-z_]*|restrictions|error[a-z_]*|reason[a-z_]*|is_playing|is_paused|playback_id|license|track":\{"uri)"[^,\]]{0,120}/g); log('fetch ' + r.status + ' ' + ((init && init.method) || 'GET') + ' ' + u.split('?')[0].slice(-40) + ' ' + t.length + 'B ' + (m ? m.slice(0, 12).join(' ; ') : t.slice(0, 120))); });
                  }, function (e) { log('fetch failed ' + u.slice(-70) + ' ' + e); });
                  return p;
                };
              } catch (e) {}
              // Spotify Connect pushes play commands to this page over its "dealer" WebSocket.
              try {
                var WS = window.WebSocket;
                window.WebSocket = function (url, p) {
                  var ws = p === undefined ? new WS(url) : new WS(url, p);
                  var host = String(url).split('?')[0].slice(0, 80), n = 0;
                  log('ws open ' + host);
                  ws.addEventListener('close', function (e) { log('ws closed ' + host + ' code=' + e.code + ' after ' + n + ' messages'); });
                  ws.addEventListener('message', function (e) {
                    n++;
                    var s = typeof e.data === 'string' ? e.data : '[binary ' + (e.data && (e.data.byteLength || e.data.size)) + ']';
                    if (n <= 40) log('ws msg ' + s.replace(/"payloads":\[[^\]]*\]/, '"payloads":[..]').slice(0, 200));
                  });
                  return ws;
                };
                window.WebSocket.prototype = WS.prototype;
                ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach(function (k) { window.WebSocket[k] = WS[k]; });
              } catch (e) {}
              addEventListener('error', function (e) { log('jserror ' + String(e.message || '').slice(0, 300)); });
              addEventListener('unhandledrejection', function (e) { var r = e.reason; log('rejection ' + String((r && (r.message || r.name)) || r).slice(0, 300)); });
              setTimeout(function () {
                if (!navigator.requestMediaKeySystemAccess) { log('drm: no EME'); return; }
                navigator.requestMediaKeySystemAccess('com.widevine.alpha', [{ initDataTypes: ['cenc'],
                  audioCapabilities: [{ contentType: 'audio/mp4; codecs="mp4a.40.2"', robustness: '' }] }])
                  .then(function () { log('drm: widevine ok'); }, function (e) { log('drm: widevine refused ' + e.name + ' ' + e.message); });
              }, 15000);
            })();
        """.trimIndent()
        const val SITE = "spotify"
        private const val TOKEN_URL = "https://open.spotify.com/api/token"
        const val PLAYER_URL = "https://open.spotify.com/"
        // Same login address as Myspoti's WebPlayerHost.LOGIN_URL.
        const val LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

        /** Copies the page's own `/api/token` answers (fetch or XHR) to DuetBridge.token. */
        private val TOKEN_HOOK_JS = """
            (function () {
              if (window.__duetHook) return; window.__duetHook = 1;
              var send = function (j) {
                try { if (j && j.accessToken) DuetBridge.token(JSON.stringify({
                  accessToken: j.accessToken, expiresAtMs: j.accessTokenExpirationTimestampMs, isAnonymous: !!j.isAnonymous })); } catch (e) {}
              };
              // Remember the page's audio element, so Duet can read the position and seek.
              var play = HTMLMediaElement.prototype.play;
              HTMLMediaElement.prototype.play = function () { window.__duetMedia = this; return play.apply(this, arguments); };
              var isToken = function (u) { return String(u || '').indexOf('/api/token') >= 0; };
              var f = window.fetch;
              var sentHeaders = false;
              window.fetch = function (input, init) {
                var p = f.apply(this, arguments);
                try {
                  // Copy the headers the page's own GraphQL calls carry (client-token, app version), once.
                  var url = typeof input === 'string' ? input : (input && input.url) || '';
                  if (!sentHeaders && url.indexOf('api-partner.spotify.com/pathfinder') >= 0) {
                    var h = new Headers((init && init.headers) || (input && input.headers) || {});
                    var out = {};
                    ['client-token', 'spotify-app-version', 'app-platform'].forEach(function (k) { if (h.get(k)) out[k] = h.get(k); });
                    if (out['client-token']) { sentHeaders = true; DuetBridge.headers(JSON.stringify(out)); }
                  }
                } catch (e) {}
                try {
                  var u = typeof input === 'string' ? input : (input && input.url);
                  if (isToken(u)) p.then(function (r) { return r.clone().json(); }).then(send).catch(function () {});
                } catch (e) {}
                return p;
              };
              var open = XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open = function (m, u) {
                if (isToken(u)) this.addEventListener('load', function () { try { send(JSON.parse(this.responseText)); } catch (e) {} });
                return open.apply(this, arguments);
              };
            })();
        """.trimIndent()
    }
}
