package app.duet.music.library

import app.duet.music.domain.Track
import app.duet.music.player.PlayHistory
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Songs you liked (the heart), newest first, kept on the phone; the graph also tells the song's own service. */
class LikedSongs(private val store: Settings = Settings()) {
    private val _list = MutableStateFlow(runCatching { PlayHistory.decode(store.getString(KEY, "[]")) }.getOrDefault(emptyList()))
    val list: StateFlow<List<Track>> = _list.asStateFlow()

    fun isLiked(t: Track) = _list.value.any { it.key == t.key }

    /** Likes or unlikes [t]; returns the new state. */
    fun toggle(t: Track): Boolean {
        val liked = !isLiked(t)
        _list.value = if (liked) listOf(t) + _list.value else _list.value.filter { it.key != t.key }
        store.putString(KEY, PlayHistory.encode(_list.value))
        return liked
    }

    private companion object { const val KEY = "liked.songs" }
}
