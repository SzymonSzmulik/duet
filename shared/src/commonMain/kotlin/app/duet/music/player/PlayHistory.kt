package app.duet.music.player

import app.duet.music.domain.Track
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Songs played on this device, newest first, without repeats, and how often each was played; kept between launches. */
class PlayHistory(private val store: Settings = Settings(), private val max: Int = 200) {
    private val _recent = MutableStateFlow(load())
    val recent: StateFlow<List<Track>> = _recent.asStateFlow()

    private val _counts = MutableStateFlow(loadCounts())
    /** Plays per song key. shortcut: a song played twice in a row counts once; good enough for a recap. */
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun add(track: Track) {
        if (_recent.value.firstOrNull()?.key == track.key) return
        _recent.value = (listOf(track) + _recent.value.filter { it.key != track.key }).take(max)
        store.putString(KEY, encode(_recent.value))
        _counts.value = _counts.value + (track.key to (_counts.value[track.key] ?: 0) + 1)
        store.putString(KEY_COUNTS, _counts.value.entries.joinToString("\n") { "${it.key}\t${it.value}" })
    }

    fun clear() {
        _recent.value = emptyList()
        _counts.value = emptyMap()
        store.remove(KEY)
        store.remove(KEY_COUNTS)
    }

    /** The most played songs that are still in the history, with their play counts, most first (ties: most recent first). */
    fun topSongs(n: Int, skipSource: String? = null): List<Pair<Track, Int>> =
        _recent.value.filter { it.sourceId != skipSource }.map { it to (_counts.value[it.key] ?: 1) }
            .sortedByDescending { it.second }.take(n)

    /** Artists by total plays (first named artist of each song), most first. */
    fun topArtists(n: Int, skipSource: String? = null): List<Pair<String, Int>> =
        _recent.value.filter { it.sourceId != skipSource && it.artist.isNotBlank() }
            .groupBy { app.duet.music.lyrics.LyricsService.firstArtist(it.artist) }
            .map { (artist, tracks) -> artist to tracks.sumOf { _counts.value[it.key] ?: 1 } }
            .filter { it.first.isNotBlank() }
            .sortedByDescending { it.second }.take(n)

    private fun loadCounts(): Map<String, Int> = store.getString(KEY_COUNTS, "").lines().mapNotNull { line ->
        val (k, v) = line.split('\t').takeIf { it.size == 2 } ?: return@mapNotNull null
        v.toIntOrNull()?.let { k to it }
    }.toMap()

    private fun load(): List<Track> = runCatching { decode(store.getString(KEY, "[]")) }.getOrDefault(emptyList())

    companion object {
        private const val KEY = "history.recent"
        private const val KEY_COUNTS = "history.counts"

        fun encode(tracks: List<Track>): String = buildJsonArray {
            tracks.forEach { t ->
                add(buildJsonObject {
                    put("s", t.sourceId); put("i", t.id); put("t", t.title); put("a", t.artist)
                    t.album?.let { put("al", it) }; t.artworkUrl?.let { put("art", it) }; t.durationMs?.let { put("d", it) }
                })
            }
        }.toString()

        fun decode(text: String): List<Track> = (Json.parseToJsonElement(text) as JsonArray).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            Track(s("s") ?: return@mapNotNull null, s("i") ?: return@mapNotNull null, s("t") ?: "", s("a") ?: "", s("al"), s("art"), s("d")?.toLongOrNull())
        }
    }
}
