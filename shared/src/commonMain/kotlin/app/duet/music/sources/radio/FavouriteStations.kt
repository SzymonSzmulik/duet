package app.duet.music.sources.radio

import app.duet.music.domain.Track
import app.duet.music.player.PlayHistory
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Radio stations the user starred, newest first, kept on the phone. */
class FavouriteStations(private val store: Settings = Settings()) {
    private val _list = MutableStateFlow(runCatching { PlayHistory.decode(store.getString(KEY, "[]")) }.getOrDefault(emptyList()))
    val list: StateFlow<List<Track>> = _list.asStateFlow()

    fun isFavourite(t: Track) = _list.value.any { it.key == t.key }

    fun toggle(t: Track) {
        _list.value = if (isFavourite(t)) _list.value.filter { it.key != t.key } else listOf(t) + _list.value
        store.putString(KEY, PlayHistory.encode(_list.value))
    }

    /** The favourite listened to most recently (by [recent], newest first), else the first favourite. */
    fun lastListened(recent: List<Track>): Track? {
        val keys = _list.value.associateBy { it.key }
        return recent.firstNotNullOfOrNull { keys[it.key] } ?: _list.value.firstOrNull()
    }

    private companion object { const val KEY = "radio.favourites" }
}
