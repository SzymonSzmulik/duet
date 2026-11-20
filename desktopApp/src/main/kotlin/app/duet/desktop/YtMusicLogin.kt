package app.duet.desktop

import app.duet.music.extension.WebLoginSession
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
import javafx.scene.web.WebView
import javafx.stage.Stage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI

/**
 * The browser's cookies for the sign-in windows, kept in a file between runs (YouTube Music's session is its cookies,
 * as on Android). The JavaFX WebView reads and writes whatever [CookieHandler.getDefault] is, so this is installed
 * once at start ([install]).
 */
class CookieJar(private val file: File) {
    val manager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    init { load() }

    /** The cookies the site would send for [uri], as a `Cookie:` header value. */
    fun header(uri: URI): String? = cookiesFor(uri).takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.name}=${it.value}" }

    fun cookiesFor(uri: URI): List<HttpCookie> = manager.cookieStore.get(uri)

    fun has(uri: URI, vararg names: String): Boolean = cookiesFor(uri).map { it.name }.containsAll(names.toList())

    /** Adds cookies copied from a browser's request header ("a=1; b=2") for [domain]. */
    fun addHeader(header: String, domain: String) {
        parseCookieHeader(header).forEach { (name, value) ->
            val cookie = HttpCookie(name, value).apply { this.domain = domain; path = "/"; secure = true; version = 0; maxAge = ONE_YEAR }
            manager.cookieStore.add(URI.create("https://${domain.trimStart('.')}/"), cookie)
        }
        save()
    }

    /** Adds the cookies a real browser reported after a sign-in (see [BrowserSignIn]). */
    fun addBrowser(cookies: List<BrowserCookie>) {
        val now = System.currentTimeMillis() / 1000.0
        cookies.filter { it.expires <= 0 || it.expires > now }.forEach { c ->
            val cookie = HttpCookie(c.name, c.value).apply {
                domain = c.domain; path = c.path; secure = c.secure; isHttpOnly = c.httpOnly; version = 0
                maxAge = if (c.expires > 0) (c.expires - now).toLong() else ONE_YEAR
            }
            manager.cookieStore.add(URI.create("https://${c.domain.trimStart('.')}/"), cookie)
        }
        save()
    }

    /** Forgets the cookies of [suffix] and its subdomains (signing out of one site), keeping the rest. */
    fun removeDomain(suffix: String) {
        manager.cookieStore.cookies.filter { (it.domain ?: "").trimStart('.').endsWith(suffix) }.forEach { c ->
            manager.cookieStore.remove(URI.create("https://${(c.domain ?: suffix).trimStart('.')}/"), c)
        }
        save()
    }

    fun clear() {
        manager.cookieStore.removeAll()
        file.delete()
    }

    /** Writes every cookie to the file (tab-separated; values are cookie-safe, so they hold no tabs or newlines). */
    fun save() {
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(manager.cookieStore.cookies.filter { !it.hasExpired() }.joinToString("\n") {
                listOf(it.domain, it.path ?: "/", it.name, it.value, it.secure, it.isHttpOnly).joinToString("\t")
            })
        }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            file.readLines().forEach { line ->
                val p = line.split('\t')
                if (p.size < 6) return@forEach
                val c = HttpCookie(p[2], p[3]).apply { domain = p[0]; path = p[1]; secure = p[4].toBoolean(); isHttpOnly = p[5].toBoolean(); version = 0; maxAge = ONE_YEAR }
                manager.cookieStore.add(URI.create("https://${p[0].trimStart('.')}/"), c)
            }
        }
    }

    companion object {
        private const val ONE_YEAR = 365L * 24 * 3600

        /** Makes WebViews use this jar. Call once before any WebView exists. */
        fun install(jar: CookieJar) = CookieHandler.setDefault(jar.manager)

        fun parseCookieHeader(header: String): List<Pair<String, String>> = header.removePrefix("cookie:").removePrefix("Cookie:").split(';')
            .mapNotNull { part -> part.trim().split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0].trim() to it[1].trim() } }
    }
}

/**
 * Google sign-in for YouTube Music, on Google's own page in a JavaFX browser window (the session is the site's
 * cookies; Duet never sees a password). If Google refuses the embedded browser, "Paste cookie instead" takes the
 * cookie header copied from a normal browser. Signed in = music.youtube.com has the SAPISID and HSID cookies.
 */
class YtMusicLogin(private val jar: CookieJar, private val scope: kotlinx.coroutines.CoroutineScope) : WebLoginSession {
    private val _signedIn = MutableStateFlow(hasSession())
    override val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    init { runCatching { Platform.startup {} } }

    override fun cookieHeader(): String? = if (hasSession()) jar.header(MUSIC) else null

    private fun hasSession() = jar.has(MUSIC, "SAPISID", "HSID")

    /** In the person's own Chrome, Edge or Brave when there is one (Google refuses embedded browsers); else a window of our own. */
    override fun signIn() {
        if (BrowserSignIn.findBrowser() == null) { Platform.runLater { openWindow() }; return }
        scope.launch {
            val cookies = BrowserSignIn.run(LOGIN) { list ->
                val names = list.filter { it.domain.trimStart('.').endsWith("youtube.com") }.map { it.name }
                "SAPISID" in names && "HSID" in names
            } ?: return@launch
            jar.addBrowser(cookies)
            _signedIn.value = hasSession()
        }
    }

    override fun signOut() {
        jar.clear()
        _signedIn.value = false
    }

    private fun openWindow() {
        val stage = Stage()
        stage.title = "Sign in to YouTube Music"
        val view = WebView()
        val engine = view.engine
        // Google signs in to what looks like Safari; JavaFX's own identity says "JavaFX", which it refuses.
        engine.userAgent = SAFARI
        fun check() {
            if (hasSession()) { jar.save(); _signedIn.value = true; stage.close() }
        }
        engine.locationProperty().addListener { _, _, url -> if (url?.startsWith("https://music.youtube.com/") == true) check() }
        engine.loadWorker.stateProperty().addListener { _, _, _ -> if (engine.location?.startsWith("https://music.youtube.com/") == true) check() }
        val paste = Button("Paste cookie instead…").apply { setOnAction { askForCookie(stage); check() } }
        val top = HBox(10.0, Label("Sign in with your Google account."), Region().also { HBox.setHgrow(it, Priority.ALWAYS) }, paste).apply { padding = Insets(8.0) }
        stage.scene = Scene(BorderPane(view).apply { this.top = top }, 520.0, 720.0)
        // DUET_SMOKE_LOGIN=1 prints what the sign-in page says once it has loaded (a check that Google accepts the window).
        if (System.getenv("DUET_SMOKE_LOGIN") != null) engine.loadWorker.stateProperty().addListener { _, _, state ->
            if (state == javafx.concurrent.Worker.State.SUCCEEDED) println("LOGIN page=${engine.location.take(80)} title=${engine.title} text=" +
                engine.executeScript("document.body ? document.body.innerText.slice(0, 160).replace(/\\s+/g, ' ') : ''"))
            if (state == javafx.concurrent.Worker.State.FAILED) println("LOGIN failed ${engine.loadWorker.exception}")
        }
        engine.load(LOGIN)
        stage.show()
    }

    private fun askForCookie(owner: Stage) {
        val dialog = TextInputDialog().apply {
            initOwner(owner)
            title = "Paste cookie"
            headerText = "In your normal browser, sign in at music.youtube.com, press F12, open the Network tab, click any request to " +
                "music.youtube.com and copy the value of its \"cookie\" request header. Paste it here."
            contentText = "Cookie:"
            editor.prefColumnCount = 40
        }
        dialog.showAndWait().ifPresent { text -> jar.addHeader(text, ".youtube.com") }
    }

    private companion object {
        val MUSIC: URI = URI.create("https://music.youtube.com/")
        const val LOGIN = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https://music.youtube.com/"
        const val SAFARI = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
    }
}
