package app.duet.desktop

import app.duet.music.DuetGraph
import app.duet.music.player.PlaybackStatus
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * Lets a phone on the same Wi-Fi control this computer's player from its browser: a tiny web page plus a few
 * commands. Every request must carry the secret key kept in ~/.duet/remote-key, so only someone who has the
 * link (shown with Ctrl+Shift+R) can use it. Nothing leaves the local network.
 */
class PhoneRemote(private val graph: DuetGraph) {
    private val keyFile = File(System.getProperty("user.home"), ".duet/remote-key")
    private val key: String = keyFile.takeIf { it.canRead() }?.readText()?.trim()?.takeIf { it.length >= 32 }
        ?: ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            .also { keyFile.parentFile.mkdirs(); keyFile.writeText(it) }
    private val server: HttpServer? = runCatching { bind() }.getOrNull()

    private fun bind(): HttpServer {
        val s = runCatching { HttpServer.create(InetSocketAddress(PORT), 0) }.getOrElse { HttpServer.create(InetSocketAddress(0), 0) }
        s.createContext("/") { ex -> runCatching { handle(ex) }.onFailure { runCatching { ex.sendResponseHeaders(500, -1) } }; ex.close() }
        s.executor = Executors.newCachedThreadPool { r -> Thread(r, "duet-remote").apply { isDaemon = true } }
        s.start()
        return s
    }

    /** The link to open on the phone, or null when the server could not start. */
    fun link(): String? = server?.let { "http://${lanAddress()}:${it.address.port}/?k=$key" }

    private fun lanAddress(): String = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }
        .firstOrNull { it.isSiteLocalAddress && it.address.size == 4 }?.hostAddress ?: "localhost"

    private fun handle(ex: HttpExchange) {
        val query = ex.requestURI.rawQuery.orEmpty().split('&').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 } }.associate { it[0] to it[1] }
        // Constant-time compare, so the key can't be guessed from response timing.
        if (!MessageDigest.isEqual((query["k"] ?: "").toByteArray(), key.toByteArray())) { ex.sendResponseHeaders(403, -1); return }
        when (ex.requestURI.path) {
            "/" -> send(ex, "text/html; charset=utf-8", PAGE)
            "/api/state" -> send(ex, "application/json", stateJson())
            "/api/cmd" -> {
                if (ex.requestMethod != "POST") { ex.sendResponseHeaders(405, -1); return }
                val p = graph.player
                graph.mainScope.launch {
                    when (query["c"]) {
                        "toggle" -> p.togglePlayPause()
                        "next" -> p.next()
                        "prev" -> p.previous()
                        "shuffle" -> p.toggleShuffle()
                        "repeat" -> p.cycleRepeat()
                        "seek" -> query["ms"]?.toLongOrNull()?.let { p.seekTo(it.coerceAtLeast(0)) }
                    }
                }
                send(ex, "application/json", "{}")
            }
            else -> ex.sendResponseHeaders(404, -1)
        }
    }

    private fun send(ex: HttpExchange, type: String, body: String) {
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", type)
        ex.responseHeaders.add("Cache-Control", "no-store")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun stateJson(): String {
        val s = graph.player.state.value
        val t = s.track
        fun q(v: String?) = if (v == null) "null" else "\"" + v.flatMap { c -> if (c == '"' || c == '\\') listOf('\\', c) else if (c < ' ') " ".toList() else listOf(c) }.joinToString("") + "\""
        return """{"title":${q(t?.title)},"artist":${q(t?.artist)},"art":${q(t?.artworkUrl)},"playing":${s.status == PlaybackStatus.Playing},"pos":${s.positionMs},"dur":${s.durationMs ?: 0},"shuffle":${s.shuffle},"repeat":${q(s.repeat.name)}}"""
    }

    private companion object {
        const val PORT = 8765
        val PAGE = """<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>Duet remote</title>
<style>body{margin:0;background:#111;color:#fff;font:16px system-ui;display:flex;flex-direction:column;align-items:center;padding:24px;gap:14px}
img{width:min(80vw,340px);aspect-ratio:1;border-radius:14px;object-fit:cover;background:#222}h2,p{margin:0;text-align:center}p{opacity:.65}
input{width:min(80vw,340px)}.r{display:flex;gap:12px}button{background:#2a2a2e;color:#fff;border:0;border-radius:14px;padding:16px 22px;font-size:22px}button.on{background:#4c6fff}</style>
<img id=a><h2 id=t>Nothing playing</h2><p id=s></p><input id=p type=range min=0 max=1 value=0>
<div class=r><button id=sh>⇄</button><button id=pv>⏮</button><button id=tg>▶</button><button id=nx>⏭</button><button id=rp>↻</button></div>
<script>const k=new URLSearchParams(location.search).get('k'),c=(x,e='')=>fetch('/api/cmd?k='+k+'&c='+x+e,{method:'POST'}).then(u);let drag=0
async function u(){const s=await(await fetch('/api/state?k='+k)).json();t.textContent=s.title||'Nothing playing';document.getElementById('s').textContent=s.artist||'';a.src=s.art||'';tg.textContent=s.playing?'⏸':'▶'
sh.className=s.shuffle?'on':'';rp.className=s.repeat!='Off'?'on':'';if(!drag){p.max=s.dur;p.value=s.pos}}
tg.onclick=()=>c('toggle');nx.onclick=()=>c('next');pv.onclick=()=>c('prev');sh.onclick=()=>c('shuffle');rp.onclick=()=>c('repeat')
p.oninput=()=>drag=1;p.onchange=()=>{drag=0;c('seek','&ms='+p.value)};setInterval(u,1000);u()</script>"""
    }
}
