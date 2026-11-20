package app.duet.music.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import app.duet.music.domain.Track
import app.duet.music.extension.Capability
import app.duet.music.extension.Playable
import app.duet.music.extension.SourceRegistry
import app.duet.music.settings.AppSettings
import app.duet.music.settings.Flag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Saves songs in the app's private storage (`files/downloads`), with a small JSON index of what each
 * file is. Audio is fetched in 1 MB range requests, as NewPipe's downloader does: YouTube cuts long
 * single requests short. Files stay inside Duet (other apps cannot open them) and go away with the app.
 */
class AndroidDownloads(
    private val context: Context,
    private val sources: SourceRegistry,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) : Downloads {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val files = HashMap<String, File>()

    private val _saved = MutableStateFlow(load())
    override val saved: StateFlow<List<Track>> = _saved.asStateFlow()
    private val _inProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    override val inProgress: StateFlow<Map<String, Float>> = _inProgress.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override fun download(track: Track) {
        if (localUri(track.key) != null || track.key in _inProgress.value) return
        // A song whose own service can't be saved (Spotify: DRM) is saved from a service that can
        // (YouTube Music): the same song is looked up there by title, artist and length.
        val own = sources[track.sourceId]?.takeIf { Capability.Download in it.manifest.capabilities }
        val saver = own ?: sources.providers.firstOrNull { Capability.Download in it.manifest.capabilities && Capability.Search in it.manifest.capabilities }
        if (saver == null) {
            _lastError.value = "Songs from ${sources[track.sourceId]?.manifest?.name ?: "this service"} can't be saved."
            return
        }
        if (settings.flag(Flag.DownloadWifiOnly).value && !onUnmeteredNetwork()) {
            _lastError.value = "Connect to Wi-Fi to save songs, or turn off \"Download on Wi-Fi only\" in Settings."
            return
        }
        _lastError.value = null
        _inProgress.update { it + (track.key to -1f) }
        scope.launch {
            try {
                val audioTrack = if (own != null) track else {
                    SongMatch.best(track, saver.search(SongMatch.query(track)))
                        ?: throw NoMatch("Couldn't find \"${track.title}\" on ${saver.manifest.name}, so it can't be saved.")
                }
                val stream = saver.resolve(audioTrack) as? Playable.Stream ?: error("This song has no audio file to save")
                val file = File(dir, safeName(track.key) + extension(stream.mimeType))
                withContext(Dispatchers.IO) { fetch(stream, file) { p -> _inProgress.update { it + (track.key to p) } } }
                files[track.key] = file
                _saved.update { listOf(track) + it.filter { t -> t.key != track.key } }
                save()
            } catch (e: NoMatch) {
                _lastError.value = e.message
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "download failed: ${track.key}", e)
                _lastError.value = "Couldn't save \"${track.title}\". Check your internet and try again."
            } finally {
                _inProgress.update { it - track.key }
            }
        }
    }

    override fun delete(trackKey: String) {
        files.remove(trackKey)?.delete()
        _saved.update { list -> list.filter { it.key != trackKey } }
        save()
    }

    override fun deleteAll() {
        files.values.forEach { it.delete() }
        files.clear()
        _saved.value = emptyList()
        save()
    }

    override fun localUri(trackKey: String): String? = files[trackKey]?.takeIf { it.exists() }?.let { "file://${it.absolutePath}" }

    override fun bytesUsed(): Long = files.values.sumOf { it.length() }

    /** Range-request download into [file] via a temporary file, so a broken download never looks finished. */
    private fun fetch(stream: Playable.Stream, file: File, progress: (Float) -> Unit) {
        val part = File(file.path + ".part")
        RandomAccessFile(part, "rw").use { out ->
            out.setLength(0)
            var pos = 0L
            var total = -1L
            while (total < 0 || pos < total) {
                val c = (URL(stream.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    stream.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    setRequestProperty("Range", "bytes=$pos-${pos + CHUNK - 1}")
                }
                try {
                    val code = c.responseCode
                    if (code != 206 && code != 200) error("HTTP $code")
                    if (total < 0) {
                        total = c.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
                            ?: c.contentLengthLong.takeIf { code == 200 } ?: -1L
                    }
                    var got = 0L
                    c.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                        }
                    }
                    pos += got
                    if (code == 200 || got == 0L) break // whole file in one answer, or nothing more
                    progress(if (total > 0) pos.toFloat() / total else -1f)
                } finally {
                    c.disconnect()
                }
            }
            if (total > 0 && pos < total) error("Download stopped at $pos of $total bytes")
        }
        if (!part.renameTo(file)) error("Couldn't store the file")
    }

    private fun onUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun load(): List<Track> = runCatching {
        val arr = JSONArray(index.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val file = File(dir, o.getString("file")).takeIf { it.exists() } ?: return@mapNotNull null
            Track(
                sourceId = o.getString("sourceId"),
                id = o.getString("id"),
                title = o.getString("title"),
                artist = o.optString("artist"),
                album = o.optString("album").ifEmpty { null },
                artworkUrl = o.optString("artworkUrl").ifEmpty { null },
                durationMs = o.optLong("durationMs").takeIf { it > 0 },
            ).also { files[it.key] = file }
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val arr = JSONArray()
        _saved.value.forEach { t ->
            val file = files[t.key] ?: return@forEach
            arr.put(JSONObject().apply {
                put("sourceId", t.sourceId); put("id", t.id); put("title", t.title); put("artist", t.artist)
                put("album", t.album ?: ""); put("artworkUrl", t.artworkUrl ?: ""); put("durationMs", t.durationMs ?: 0L)
                put("file", file.name)
            })
        }
        index.writeText(arr.toString())
    }

    private class NoMatch(message: String) : Exception(message)

    private companion object {
        const val TAG = "DuetDownloads"
        const val CHUNK = 1L shl 20
        fun safeName(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        fun extension(mime: String?) = when {
            mime == null -> ".audio"
            "webm" in mime -> ".webm"
            "mp4" in mime || "m4a" in mime -> ".m4a"
            else -> ".audio"
        }
    }
}
