package app.duet.music.home

import app.duet.music.domain.Shelf
import app.duet.music.extension.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Home's shelves from every source that has a home page, loaded once and on [refresh]. */
class HomeModel(
    private val sources: SourceRegistry,
    private val scope: CoroutineScope,
    /** Recently played, newest first: its first song seeds Quick picks. */
    recent: StateFlow<List<app.duet.music.domain.Track>>? = null,
) {
    private val _shelves = MutableStateFlow<List<Shelf>>(emptyList())
    val shelves: StateFlow<List<Shelf>> = _shelves.asStateFlow()

    /** Radio stations: one per artist among the last songs played (newest first), seeded by that song. */
    fun radioSeeds(recent: List<app.duet.music.domain.Track>, max: Int = 8): List<app.duet.music.domain.Track> =
        recent.filter { it.sourceId != app.duet.music.sources.radio.RadioProvider.ID } // live stations aren't songs to mix from
            .distinctBy { app.duet.music.lyrics.LyricsService.firstArtist(it.artist).lowercase() }.take(max)

    /** A station's songs: the seed first, then songs like it from the first source that has a radio. */
    suspend fun radioFor(seed: app.duet.music.domain.Track): List<app.duet.music.domain.Track> {
        val similar = sources.providers.firstNotNullOfOrNull { p -> runCatching { p.similar(seed) }.getOrNull()?.takeIf { it.isNotEmpty() } }.orEmpty()
        return listOf(seed) + similar.filter { it.key != seed.key }
    }

    /** Shelves to show: a service's own "Quick picks" is left out while Duet's (from your listening) has songs. */
    fun visibleShelves(all: List<Shelf>, quick: List<app.duet.music.domain.Track>): List<Shelf> =
        if (quick.isEmpty()) all else all.filterNot { it.title.equals("Quick picks", ignoreCase = true) }

    private val _quickPicks = MutableStateFlow<List<app.duet.music.domain.Track>>(emptyList())
    /** Songs like the last one played (YouTube Music's radio), minus what was just played. */
    val quickPicks: StateFlow<List<app.duet.music.domain.Track>> = _quickPicks.asStateFlow()

    init {
        refresh()
        if (recent != null) scope.launch {
            recent.map { it.firstOrNull() }.distinctUntilChangedBy { it?.key }.collect { seed ->
                if (seed == null) return@collect
                val radio = sources.providers.firstNotNullOfOrNull { p ->
                    runCatching { p.similar(seed) }.getOrNull()?.takeIf { it.isNotEmpty() }
                }.orEmpty()
                val played = recent.value.take(20).map { it.key }.toSet()
                _quickPicks.value = radio.filter { it.key !in played }.take(20)
            }
        }
    }

    fun refresh() {
        scope.launch {
            val all = sources.providers.map { p -> async { runCatching { p.homeShelves() }.getOrDefault(emptyList()) } }.awaitAll()
            _shelves.value = all.flatten()
        }
    }
}
