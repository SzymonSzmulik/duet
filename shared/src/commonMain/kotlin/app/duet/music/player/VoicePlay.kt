package app.duet.music.player

import app.duet.music.DuetGraph
import app.duet.music.domain.Track
import app.duet.music.sources.radio.RadioProvider

/**
 * "Hey Google, play X on Duet" (phone Assistant and Android Auto): a favourite or recently played station
 * named X, else a station named exactly X, else songs matching X, else any station matching X. An empty query resumes, or plays recent songs.
 */
suspend fun DuetGraph.playFromVoice(query: String) {
    val q = query.trim()
    if (q.isEmpty()) {
        when {
            player.state.value.track == null -> history.recent.value.takeIf { it.isNotEmpty() }?.let { player.playAll(it, 0) }
            player.state.value.status != PlaybackStatus.Playing -> player.togglePlayPause()
        }
        return
    }
    val known = (favouriteStations.list.value + history.recent.value.filter { it.sourceId == RadioProvider.ID }).distinctBy { it.key }
    matchStation(q, known)?.let { station ->
        val favourites = favouriteStations.list.value
        val i = favourites.indexOfFirst { it.key == station.key }
        // A favourite plays with the other favourites queued, so next/previous switch station.
        if (i >= 0) player.playAll(favourites, i) else player.play(station)
        return
    }
    // A station whose name is exactly what was said ("play Eska Rock") beats songs; otherwise songs first.
    val name = q.lowercase().removeSuffix(" radio")
    val stations = sources[RadioProvider.ID]?.let { r -> runCatching { r.search(name) }.getOrDefault(emptyList()) }.orEmpty()
    stations.firstOrNull { it.title.trim().lowercase() == name }?.let { return player.play(it) }
    val songs = sources.music.flatMap { runCatching { it.search(q) }.getOrDefault(emptyList()) }
    if (songs.isNotEmpty()) return player.playAll(songs, 0)
    stations.firstOrNull()?.let(player::play)
}

/** The station whose name matches [query]: exact first, then either name containing the other ("eska rock radio"). */
fun matchStation(query: String, stations: List<Track>): Track? {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return null
    return stations.firstOrNull { it.title.trim().lowercase() == q }
        ?: stations.firstOrNull { val name = it.title.trim().lowercase(); name.isNotEmpty() && (q in name || name in q) }
}
