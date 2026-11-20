package app.duet.desktop

import app.duet.music.domain.Track
import app.duet.music.downloads.Downloads
import app.duet.music.downloads.SongMatch
import app.duet.music.extension.Capability
import app.duet.music.extension.Playable
import app.duet.music.extension.SourceRegistry
import app.duet.music.library.MediaCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Base64

private val home = File(System.getProperty("user.home"), ".duet")
private const val CHUNK = 1_048_576L

/**
 * Fetches [stream] into [file] in 1 MB Range pieces (YouTube slows a plain whole-file request to playback speed),
 * through a ".part" file so a broken download never looks finished.
 */
internal fun fetchRanged(http: OkHttpClient, stream: Playable.Stream, file: File, progress: (Float) -> Unit = {}) {
    val part = File(file.path + ".part")
    var start = 0L
    var total = Long.MAX_VALUE
    part.outputStream().use { out ->
        while (start < total) {
            val request = Request.Builder().url(stream.url).apply { stream.headers.forEach { (k, v) -> header(k, v) } }
                .header("Range", "bytes=$start-${start + CHUNK - 1}").build()
            http.newCall(request).execute().use { r ->
                if (r.code != 200 && r.code != 206) error("The song's server said ${r.code}")
                total = if (r.code == 206) r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: Long.MAX_VALUE else 0L
                start += r.body!!.byteStream().copyTo(out)
                if (total in 1 until Long.MAX_VALUE) progress(start.toFloat() / total)
            }
        }
    }
    if (!part.renameTo(file)) { part.delete(); error("Couldn't store the file") }
}

/**
 * Songs kept on disk after streaming (~/.duet/cache), so they play again without the network. The least recently
 * played go first when the folder passes [maxBytes]. A file's name is its song key, so the folder is its own index.
 */
class DesktopMediaCache(private val maxBytes: Long = 1L shl 30) : MediaCache {
    private val dir = File(home, "cache").apply { mkdirs() }
    private fun name(key: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray()) + ".m4a"
    private val _keys = MutableStateFlow(
        dir.listFiles { f -> f.extension == "m4a" }.orEmpty().mapNotNull { runCatching { String(Base64.getUrlDecoder().decode(it.nameWithoutExtension)) }.getOrNull() }.toSet(),
    )
    override val cachedKeys: StateFlow<Set<String>> = _keys.asStateFlow()

    /** The cached file for a song (and marks it as just played), or null. */
    fun fileFor(key: String): File? = File(dir, name(key)).takeIf { it.exists() }?.also { it.setLastModified(System.currentTimeMillis()) }
    fun target(key: String) = File(dir, name(key))

    /** Call after the file at [target] is complete. */
    fun committed(key: String) {
        _keys.update { it + key }
        var total = bytesUsed()
        dir.listFiles { f -> f.extension == "m4a" }.orEmpty().sortedBy { it.lastModified() }.forEach { f ->
            if (total <= maxBytes) return
            if (f.name == name(key)) return@forEach
            total -= f.length(); f.delete()
            runCatching { String(Base64.getUrlDecoder().decode(f.nameWithoutExtension)) }.getOrNull()?.let { k -> _keys.update { it - k } }
        }
    }

    override fun bytesUsed(): Long = dir.listFiles { f -> f.extension == "m4a" }.orEmpty().sumOf { it.length() }
    override fun clear() { dir.listFiles()?.forEach { it.delete() }; _keys.value = emptySet() }
}

/** Songs saved on purpose (~/.duet/downloads) with a small JSON index of what each file is; DRM audio (Spotify) is never saved. */
class DesktopDownloads(private val sources: SourceRegistry, private val scope: CoroutineScope, private val http: OkHttpClient) : Downloads {
    private val dir = File(home, "downloads").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val files = HashMap<String, File>()
    private fun name(key: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray()) + ".m4a"

    private val _saved = MutableStateFlow(load())
    override val saved: StateFlow<List<Track>> = _saved.asStateFlow()
    private val _inProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    override val inProgress: StateFlow<Map<String, Float>> = _inProgress.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override fun download(track: Track) {
        if (localUri(track.key) != null || track.key in _inProgress.value) return
        // A song whose service can't be saved (Spotify) is saved from one that can (YouTube Music), found by title, artist and length.
        val own = sources[track.sourceId]?.takeIf { Capability.Download in it.manifest.capabilities }
        val saver = own ?: sources.providers.firstOrNull { Capability.Download in it.manifest.capabilities && Capability.Search in it.manifest.capabilities }
        if (saver == null) { _lastError.value = "Songs from ${sources[track.sourceId]?.manifest?.name ?: "this service"} can't be saved."; return }
        _lastError.value = null
        _inProgress.update { it + (track.key to -1f) }
        scope.launch {
            try {
                val audioTrack = if (own != null) track else SongMatch.best(track, saver.search(SongMatch.query(track)))
                    ?: run { _lastError.value = "Couldn't find \"${track.title}\" on ${saver.manifest.name}, so it can't be saved."; return@launch }
                val stream = saver.resolve(audioTrack) as? Playable.Stream ?: error("This song has no audio file to save")
                val file = File(dir, name(track.key))
                withContext(Dispatchers.IO) { fetchRanged(http, stream, file) { p -> _inProgress.update { it + (track.key to p) } } }
                files[track.key] = file
                _saved.update { listOf(track) + it.filter { t -> t.key != track.key } }
                save()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _lastError.value = "Couldn't save \"${track.title}\". Check your internet and try again."
            } finally {
                _inProgress.update { it - track.key }
            }
        }
    }

    override fun delete(trackKey: String) { files.remove(trackKey)?.delete(); _saved.update { l -> l.filter { it.key != trackKey } }; save() }
    override fun deleteAll() { files.values.forEach { it.delete() }; files.clear(); _saved.value = emptyList(); save() }
    override fun localUri(trackKey: String): String? = files[trackKey]?.takeIf { it.exists() }?.toURI()?.toString()
    override fun bytesUsed(): Long = files.values.sumOf { it.length() }

    private fun load(): List<Track> = runCatching {
        Json.parseToJsonElement(index.readText()).jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
            val t = Track(s("sourceId")!!, s("id")!!, s("title")!!, s("artist") ?: "", s("album"), s("artworkUrl"), o["durationMs"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 })
            File(dir, name(t.key)).takeIf { it.exists() }?.also { files[t.key] = it }?.let { t }
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val arr = JsonArray(_saved.value.map { t ->
            JsonObject(mapOf("sourceId" to JsonPrimitive(t.sourceId), "id" to JsonPrimitive(t.id), "title" to JsonPrimitive(t.title), "artist" to JsonPrimitive(t.artist),
                "album" to JsonPrimitive(t.album ?: ""), "artworkUrl" to JsonPrimitive(t.artworkUrl ?: ""), "durationMs" to JsonPrimitive(t.durationMs ?: 0L)))
        })
        runCatching { index.writeText(arr.toString()) }
    }
}
