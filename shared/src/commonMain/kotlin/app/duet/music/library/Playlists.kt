package app.duet.music.library

import app.duet.music.domain.Track
import app.duet.music.player.PlayHistory
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A Duet playlist: songs from any source (Spotify and YouTube Music side by side), kept on the phone. */
data class Playlist(val id: String, val name: String, val tracks: List<Track>)

/** The user's own playlists, newest first. Each one's songs are stored under their own key. */
class Playlists(private val store: Settings = Settings()) {
    @Serializable private data class Entry(val id: String, val name: String)

    private val _list = MutableStateFlow(load())
    val list: StateFlow<List<Playlist>> = _list.asStateFlow()

    operator fun get(id: String) = _list.value.firstOrNull { it.id == id }

    fun create(name: String): Playlist {
        val p = Playlist("p" + kotlin.random.Random.nextLong().toULong().toString(36), name.trim().ifEmpty { "My playlist" }, emptyList())
        save(listOf(p) + _list.value)
        return p
    }

    /** Adds [track] at the end, once. */
    fun add(id: String, track: Track) = change(id) { if (it.tracks.any { t -> t.key == track.key }) it else it.copy(tracks = it.tracks + track) }

    /** Adds every song not already in it, in order (copying an album or another service's playlist). */
    fun addAll(id: String, tracks: List<Track>) = change(id) { p ->
        val have = p.tracks.mapTo(HashSet()) { it.key }
        p.copy(tracks = p.tracks + tracks.filter { have.add(it.key) })
    }

    /** Moves the song at [from] to [to] (both clamped). */
    fun move(id: String, from: Int, to: Int) = change(id) { p ->
        if (from !in p.tracks.indices) p else p.copy(tracks = p.tracks.toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) })
    }

    fun remove(id: String, track: Track) = change(id) { it.copy(tracks = it.tracks.filter { t -> t.key != track.key }) }

    fun rename(id: String, name: String) = change(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) {
        store.remove(songsKey(id))
        save(_list.value.filter { it.id != id })
    }

    private fun change(id: String, f: (Playlist) -> Playlist) = save(_list.value.map { if (it.id == id) f(it) else it })

    private fun save(list: List<Playlist>) {
        val old = _list.value.associateBy { it.id }
        list.forEach { p -> if (old[p.id]?.tracks != p.tracks || old[p.id] == null) store.putString(songsKey(p.id), PlayHistory.encode(p.tracks)) }
        store.putString(INDEX, Json.encodeToString(list.map { Entry(it.id, it.name) }))
        _list.value = list
    }

    private fun load(): List<Playlist> = runCatching {
        Json.decodeFromString<List<Entry>>(store.getString(INDEX, "[]")).map { e ->
            Playlist(e.id, e.name, runCatching { PlayHistory.decode(store.getString(songsKey(e.id), "[]")) }.getOrDefault(emptyList()))
        }
    }.getOrDefault(emptyList())

    private fun songsKey(id: String) = "playlist.$id"

    private companion object { const val INDEX = "playlists" }
}
