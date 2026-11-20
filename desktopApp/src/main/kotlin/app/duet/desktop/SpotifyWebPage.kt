package app.duet.desktop

import app.duet.music.sources.spotify.SpotifyWebSession
import app.duet.music.sources.spotify.SpotifyWebToken
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextInputDialog
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import netscape.javascript.JSObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spotify's Web Player in a hidden browser page (JavaFX's WebView, never shown), the way the phone does it with its
 * WebView (docs/DECISIONS.md D24): the page makes its own calls to Spotify with a bearer token and a `client-token`, a
 * script that runs before the page's own scripts copies them, and [pageFetch] sends Duet's requests from inside the page
 * (Spotify refuses its GraphQL from anywhere else). It loads signed out too: the anonymous token is enough to search.
 * Sign-in is Spotify's own page in a browser window (or a pasted `sp_dc` cookie); the session is that site's cookies.
 */
class SpotifyWebPage(private val jar: CookieJar, private val scope: CoroutineScope) : SpotifyWebSession {
    private val _token = MutableStateFlow<SpotifyWebToken?>(null)
    override val token: StateFlow<SpotifyWebToken?> = _token.asStateFlow()
    private val _headers = MutableStateFlow<Map<String, String>>(emptyMap())
    override val apiHeaders: StateFlow<Map<String, String>> = _headers.asStateFlow()
    private val _signedIn = MutableStateFlow(hasSession())
    override val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private var engine: WebEngine? = null
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Pair<Int, String>>>()
    private val nextId = AtomicInteger()

    /** What the page's scripts call (public, and kept in a field: JavaFX holds it weakly). */
    inner class Bridge {
        fun token(bearer: String) {
            if (bearer.isBlank() || bearer == _token.value?.accessToken) return
            // The page does not say when it runs out; it asks for a new one about hourly, and every new one arrives here.
            _token.value = SpotifyWebToken(bearer, System.currentTimeMillis() + 50 * 60_000L, isAnonymous = !hasSession())
            if (hasSession() && !_signedIn.value) _signedIn.value = true
        }

        fun headers(json: String) {
            val o = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
            _headers.value = o.mapNotNull { (k, v) -> v.jsonPrimitive.contentOrNull?.let { k to it } }.toMap()
        }

        fun fetched(id: Int, status: Int, body: String) { pending.remove(id)?.complete(status to body) }
    }

    private val bridge = Bridge()

    init {
        runCatching { Platform.startup {} }
        Platform.runLater { startPlayer() }
    }

    private fun hasSession() = jar.has(SPOTIFY, "sp_dc")

    private fun startPlayer() {
        val view = WebView()
        val e = view.engine
        e.userAgent = SAFARI
        // A WebView only runs inside a scene; the window is never shown.
        Stage().apply { scene = Scene(view) }
        // As soon as each page's document exists, before its own scripts, put the bridge and the copying script in.
        e.documentProperty().addListener { _, _, doc ->
            if (doc != null) runCatching {
                (e.executeScript("window") as JSObject).setMember("DuetBridge", bridge)
                e.executeScript(HOOK)
            }
        }
        engine = e
        e.load(PLAYER)
    }

    override suspend fun pageFetch(url: String, method: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        val id = nextId.incrementAndGet()
        val answer = CompletableDeferred<Pair<Int, String>>()
        pending[id] = answer
        val headerJson = buildJsonObject { headers.forEach { (k, v) -> put(k, v) } }.toString()
        val js = "(function(){fetch(" + quote(url) + ",{method:" + quote(method) + ",headers:" + headerJson + (body?.let { ",body:" + quote(it) } ?: "") +
            ",credentials:'include'}).then(function(r){return r.text().then(function(t){DuetBridge.fetched($id,r.status,t);});})" +
            ".catch(function(e){DuetBridge.fetched($id,0,String(e));});})()"
        Platform.runLater { runCatching { engine?.executeScript(js) }.onFailure { pending.remove(id)?.complete(0 to "the page is not ready") } }
        return try { withTimeout(20_000) { answer.await() } } finally { pending.remove(id) }
    }

    private fun quote(s: String) = Json.encodeToString(kotlinx.serialization.serializer<String>(), s)

    /** In the person's own Chrome, Edge or Brave when there is one (Spotify's login refuses embedded browsers); else a window of our own. */
    override fun signIn() {
        if (BrowserSignIn.findBrowser() == null) { Platform.runLater { openLoginWindow() }; return }
        scope.launch {
            val cookies = BrowserSignIn.run(LOGIN) { list -> list.any { it.name == "sp_dc" && it.domain.trimStart('.').endsWith("spotify.com") } } ?: return@launch
            jar.addBrowser(cookies)
            Platform.runLater { signedInNow() }
        }
    }

    override fun signOut() {
        jar.removeDomain("spotify.com")
        _token.value = null
        _headers.value = emptyMap()
        _signedIn.value = false
        Platform.runLater { engine?.load(PLAYER) }
    }

    /** After a sign-in the hidden page is loaded again, now as the person. */
    private fun signedInNow() {
        jar.save()
        _signedIn.value = true
        _token.value = null
        _headers.value = emptyMap()
        engine?.load(PLAYER)
    }

    private fun openLoginWindow() {
        val stage = Stage()
        stage.title = "Sign in to Spotify"
        val view = WebView()
        val e = view.engine
        // Spotify's login page checks what it is running in; a Safari identity gets through.
        e.userAgent = SAFARI
        fun check() { if (hasSession()) { stage.close(); signedInNow() } }
        e.loadWorker.stateProperty().addListener { _, _, state ->
            check()
            // DUET_SMOKE_LOGIN=1 prints what the sign-in page says once it has loaded (a check that Spotify accepts the window).
            if (System.getenv("DUET_SMOKE_LOGIN") != null && state == javafx.concurrent.Worker.State.SUCCEEDED)
                println("LOGIN spotify page=${e.location.take(70)} text=" + e.executeScript("document.body ? document.body.innerText.slice(0, 200).replace(/\\s+/g, ' ') : ''"))
        }
        val paste = Button("Paste cookie instead…").apply { setOnAction { askForCookie(stage); check() } }
        val top = HBox(10.0, Label("Sign in to your Spotify account."), Region().also { HBox.setHgrow(it, Priority.ALWAYS) }, paste).apply { padding = Insets(8.0) }
        stage.scene = Scene(BorderPane(view).apply { this.top = top }, 520.0, 760.0)
        e.load(LOGIN)
        stage.show()
    }

    private fun askForCookie(owner: Stage) {
        val dialog = TextInputDialog().apply {
            initOwner(owner)
            title = "Paste cookie"
            headerText = "If the sign-in page will not work here: in your normal browser, sign in at open.spotify.com, press F12, open the " +
                "Application (Chrome) or Storage (Firefox) tab, Cookies, open.spotify.com, and copy the Value of the cookie named sp_dc. Paste it here."
            contentText = "sp_dc:"
            editor.prefColumnCount = 40
        }
        dialog.showAndWait().ifPresent { text -> jar.addHeader("sp_dc=" + text.trim().removePrefix("sp_dc=").substringBefore(';'), ".spotify.com") }
    }

    private companion object {
        val SPOTIFY: URI = URI.create("https://open.spotify.com/")
        const val PLAYER = "https://open.spotify.com/"
        const val LOGIN = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
        const val SAFARI = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"

        /** Copies the bearer token and the `client-token` family of headers off the page's own calls to Spotify. */
        val HOOK = """
            (function () {
              if (window.__duetHook) return; window.__duetHook = 1;
              var sentHeaders = false;
              var look = function (url, headers) {
                try {
                  var u = String(url || ''); if (u.indexOf('spotify.com') < 0 || u.indexOf('/pathfinder') < 0 && u.indexOf('api.spotify.com') < 0) return;
                  var h = {};
                  if (headers) {
                    if (typeof headers.forEach === 'function' && !Array.isArray(headers)) headers.forEach(function (v, k) { h[String(k).toLowerCase()] = v; });
                    else if (Array.isArray(headers)) headers.forEach(function (p) { h[String(p[0]).toLowerCase()] = p[1]; });
                    else for (var k in headers) h[k.toLowerCase()] = headers[k];
                  }
                  if (h['authorization'] && /^Bearer /i.test(h['authorization'])) DuetBridge.token(String(h['authorization']).replace(/^Bearer /i, ''));
                  if (!sentHeaders && h['client-token']) {
                    var out = {};
                    ['client-token', 'spotify-app-version', 'app-platform'].forEach(function (k) { if (h[k]) out[k] = h[k]; });
                    sentHeaders = true; DuetBridge.headers(JSON.stringify(out));
                  }
                } catch (e) {}
              };
              var realFetch = window.fetch;
              window.fetch = function (input, init) {
                look(typeof input === 'string' ? input : input && input.url, (init && init.headers) || (input && input.headers));
                return realFetch.apply(this, arguments);
              };
              var open = XMLHttpRequest.prototype.open, setHeader = XMLHttpRequest.prototype.setRequestHeader, send = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function (m, u) { this.__duetUrl = u; this.__duetHeaders = {}; return open.apply(this, arguments); };
              XMLHttpRequest.prototype.setRequestHeader = function (k, v) { if (this.__duetHeaders) this.__duetHeaders[k] = v; return setHeader.apply(this, arguments); };
              XMLHttpRequest.prototype.send = function () { look(this.__duetUrl, this.__duetHeaders); return send.apply(this, arguments); };
            })();
        """.trimIndent()
    }
}
