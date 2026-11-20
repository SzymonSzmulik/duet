package app.duet.desktop

import app.duet.music.DuetGraph
import app.duet.music.player.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Shows what Duet is playing in macOS's Now Playing (Control Center, the media keys, and "notch" or Dynamic-Island style
 * apps, which show whatever the system's Now Playing says), and lets those controls drive Duet. Done by a small helper
 * program (`DuetNowPlaying`, from src/macMain/swift) that Duet starts and talks to in lines of text: one JSON line to
 * it per change, one command per line back (play, pause, toggle, next, previous, seek N).
 */
class MacNowPlaying(private val graph: DuetGraph, private val scope: CoroutineScope, helper: File) {
    private val process = ProcessBuilder(helper.absolutePath).redirectErrorStream(false).start()
    private val out = PrintWriter(process.outputStream.bufferedWriter(), true)
    private val http = OkHttpClient()
    private val artwork = ConcurrentHashMap<String, String?>()
    private var sentKey: String? = null
    private var sentAt = 0L
    private var sentPosition = 0L

    init {
        Thread { process.inputStream.bufferedReader().forEachLine { runCatching { command(it) } } }.apply { isDaemon = true; start() }
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { process.destroy() } })
        scope.launch(Dispatchers.IO) {
            graph.player.state.map { Triple(it.track?.key, it.status == PlaybackStatus.Playing, it.positionMs / SEEK_STEP_MS) }
                .distinctUntilChanged().collect { publish() }
        }
    }

    private suspend fun publish() {
        val s = graph.player.state.value
        val track = s.track
        if (track == null || s.status == PlaybackStatus.Idle) {
            if (sentKey != null) { sentKey = null; send(clear()) }
            return
        }
        val playing = s.status == PlaybackStatus.Playing
        val key = "${track.key}|$playing"
        val now = System.currentTimeMillis()
        val expected = sentPosition + if (sentKey?.endsWith("|true") == true) now - sentAt else 0L
        // Between changes the system counts the time itself; send again only for a new song, play or pause, or a jump.
        if (key == sentKey && abs(s.positionMs - expected) < SEEK_STEP_MS) return
        sentKey = key; sentAt = now; sentPosition = s.positionMs
        send(message(track.title, track.artist, track.album, s.durationMs ?: track.durationMs, s.positionMs, playing, track.artworkUrl?.let { artworkFile(it) }))
    }

    private fun send(line: String) { runCatching { out.println(line) } }

    /** Downloads the cover once and returns its file, for the helper to read; null if it cannot be had. */
    private fun artworkFile(url: String): String? = artwork.getOrPut(url) {
        runCatching {
            val file = File.createTempFile("duet-art-", ".img").apply { deleteOnExit() }
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                file.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
            }
            file.absolutePath
        }.getOrNull()
    }

    /** What people pressed in Control Center, on the media keys or in a notch app. */
    private fun command(line: String) {
        val playing = graph.player.state.value.status == PlaybackStatus.Playing
        scope.launch {
            when {
                line == "play" -> if (!playing) graph.player.togglePlayPause()
                line == "pause" -> if (playing) graph.player.togglePlayPause()
                line == "toggle" -> graph.player.togglePlayPause()
                line == "next" -> graph.player.next()
                line == "previous" -> graph.player.previous()
                line.startsWith("seek ") -> line.removePrefix("seek ").toDoubleOrNull()?.let { graph.player.seekTo((it * 1000).toLong()) }
            }
        }
    }

    companion object {
        private const val SEEK_STEP_MS = 2000L

        /** The helper's JSON line for a song (seconds, as macOS wants them). */
        fun message(title: String, artist: String, album: String?, durationMs: Long?, positionMs: Long, playing: Boolean, artworkPath: String?): String =
            buildJsonObject {
                put("title", title); put("artist", artist)
                album?.let { put("album", it) }
                durationMs?.let { put("duration", it / 1000.0) }
                put("position", positionMs / 1000.0)
                put("playing", playing)
                artworkPath?.let { put("artwork", it) }
            }.toString()

        fun clear(): String = buildJsonObject { put("clear", true) }.toString()

        /** Starts it where there is a helper (a Mac build made with swiftc); nothing happens elsewhere. */
        fun start(graph: DuetGraph, scope: CoroutineScope): MacNowPlaying? {
            if (!System.getProperty("os.name").lowercase().contains("mac")) return null
            val resources = System.getProperty("compose.application.resources.dir") ?: return null
            val packaged = File(resources, "DuetNowPlaying").takeIf { it.isFile } ?: return null
            return runCatching { MacNowPlaying(graph, scope, runnableCopy(packaged)) }.getOrNull()
        }

        /**
         * The helper as a runnable file in Duet's own folder. Packaging drops the "runnable" mark from resources, and the app
         * itself may not be changeable, so it is copied (to a temporary name, then renamed, so a running copy is never
         * overwritten) and marked there.
         */
        internal fun runnableCopy(packaged: File, folder: File = File(System.getProperty("user.home"), ".duet")): File {
            val target = File(folder, packaged.name)
            if (!target.isFile || target.length() != packaged.length() || target.lastModified() < packaged.lastModified()) {
                folder.mkdirs()
                val temp = File(folder, packaged.name + ".new")
                packaged.copyTo(temp, overwrite = true)
                temp.setExecutable(true)
                temp.renameTo(target)
            }
            target.setExecutable(true)
            return target
        }
    }
}
