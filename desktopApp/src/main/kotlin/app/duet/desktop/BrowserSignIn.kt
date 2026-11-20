package app.duet.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A cookie as the browser reports it. [expires] is seconds since 1970, or 0 or less for a session cookie. */
data class BrowserCookie(val domain: String, val path: String, val name: String, val value: String, val secure: Boolean, val httpOnly: Boolean, val expires: Double)

/**
 * Signs in to a website in the person's own Chrome, Edge or Brave: Google and Spotify refuse their login pages inside
 * another app's embedded browser ("this browser or app may not be secure"), but accept a real browser. Duet opens it on
 * the login page with a throwaway profile and a debugging port on this computer only, waits until [ready] says the
 * cookies show a signed-in session, reads the cookies over that connection, and closes the browser. Nothing is read
 * from the person's own browser profile, and the password is only ever typed into the real browser.
 */
object BrowserSignIn {
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()

    /** The first Chromium-based browser installed here (Chrome, then Edge, then Brave), or null. */
    fun findBrowser(): File? {
        val os = System.getProperty("os.name").lowercase()
        val candidates = if ("mac" in os) listOf(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
            "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
        ) else if ("windows" in os) {
            val roots = listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), System.getenv("LOCALAPPDATA"))
            roots.flatMap { r -> listOf("$r\\Google\\Chrome\\Application\\chrome.exe", "$r\\Microsoft\\Edge\\Application\\msedge.exe", "$r\\BraveSoftware\\Brave-Browser\\Application\\brave.exe") }
        } else emptyList()
        return candidates.map(::File).firstOrNull { it.isFile }
    }

    /**
     * Opens [url] in the browser and returns the cookies once [ready] is true for them, or null if the person closed the
     * browser, there is none, or ten minutes passed.
     */
    suspend fun run(url: String, ready: (List<BrowserCookie>) -> Boolean): List<BrowserCookie>? = withContext(Dispatchers.IO) {
        val browser = findBrowser() ?: return@withContext null
        val port = ServerSocket(0).use { it.localPort }
        val profile = Files.createTempDirectory("duet-sign-in").toFile()
        val process = ProcessBuilder(
            browser.absolutePath, "--remote-debugging-port=$port", "--remote-allow-origins=*", "--user-data-dir=${profile.absolutePath}",
            "--no-first-run", "--no-default-browser-check", "--new-window", url,
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        var socket: WebSocket? = null
        try {
            val endpoint = withTimeoutOrNull(20_000) {
                var found: String? = null
                while (found == null && process.isAlive) {
                    found = runCatching {
                        http.newCall(Request.Builder().url("http://127.0.0.1:$port/json/version").build()).execute().use { r ->
                            Json.parseToJsonElement(r.body!!.string()).jsonObject["webSocketDebuggerUrl"]?.jsonPrimitive?.contentOrNull
                        }
                    }.getOrNull()
                    if (found == null) delay(300)
                }
                found
            } ?: return@withContext null
            val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
            val ids = AtomicInteger()
            socket = http.newWebSocket(Request.Builder().url(endpoint).build(), object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                    o["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.let { pending.remove(it)?.complete(o) }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { pending.values.forEach { it.cancel() } }
            })
            suspend fun cookies(): List<BrowserCookie>? {
                val id = ids.incrementAndGet()
                val answer = CompletableDeferred<JsonObject>().also { pending[id] = it }
                socket.send(buildJsonObject { put("id", id); put("method", "Storage.getCookies") }.toString())
                val reply = withTimeoutOrNull(5_000) { runCatching { answer.await() }.getOrNull() } ?: return null
                return parse(reply)
            }
            val deadline = System.currentTimeMillis() + 10 * 60_000
            while (process.isAlive && System.currentTimeMillis() < deadline) {
                val list = cookies()
                if (list != null && ready(list)) return@withContext list
                delay(1500)
            }
            null
        } finally {
            runCatching { socket?.send(buildJsonObject { put("id", 9999); put("method", "Browser.close") }.toString()) }
            runCatching { socket?.close(1000, null) }
            if (process.isAlive) process.waitFor(3, TimeUnit.SECONDS)
            process.destroy()
            runCatching { profile.deleteRecursively() }
        }
    }

    /** The cookies in a `Storage.getCookies` answer. */
    internal fun parse(reply: JsonObject): List<BrowserCookie> =
        (reply["result"]?.jsonObject?.get("cookies") as? JsonArray ?: JsonArray(emptyList())).mapNotNull { e ->
            val c = e.jsonObject
            BrowserCookie(
                domain = c["domain"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                path = c["path"]?.jsonPrimitive?.contentOrNull ?: "/",
                name = c["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                value = c["value"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                secure = c["secure"]?.jsonPrimitive?.boolean ?: false,
                httpOnly = c["httpOnly"]?.jsonPrimitive?.boolean ?: false,
                expires = c["expires"]?.jsonPrimitive?.double ?: -1.0,
            )
        }
}
