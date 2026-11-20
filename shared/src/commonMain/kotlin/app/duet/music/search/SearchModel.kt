package app.duet.music.search

import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.domain.Track
import app.duet.music.extension.SourceProvider
import app.duet.music.extension.SourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    /** The one source id to search, or null for all installed sources. */
    val only: String? = null,
    /** What to look for; null is "All" (songs plus a few of each other kind). */
    val kind: SearchKind? = null,
    val loading: Boolean = false,
    val results: List<Track> = emptyList(),
    /** Uploads (remixes, covers, live videos) found. */
    val videos: List<Track> = emptyList(),
    /** Albums, artists and playlists found, by kind. */
    val collections: Map<SearchKind, List<Collection>> = emptyMap(),
    /** Per-source failures, shown under the results. Other sources' results still show. */
    val errors: Map<String, String> = emptyMap(),
    /** What people search for that starts like the query (YouTube Music), as tap-to-search chips. */
    val suggestions: List<String> = emptyList(),
)

/** One search box over every installed [SourceProvider]. Results are interleaved across sources. */
class SearchModel(private val sources: SourceRegistry, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null

    /** Typing: search after a short pause. */
    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        run(debounceMs = 350)
    }

    /** Keyboard search button: search now. */
    fun submit() = run(debounceMs = 0)

    /** Fills in [query] and searches right away (Home's suggestions). */
    fun searchFor(query: String) {
        onQueryChange(query)
        submit()
    }

    fun onSourceFilter(sourceId: String?) {
        _state.update { it.copy(only = sourceId) }
        run(debounceMs = 0)
    }

    fun onKind(kind: SearchKind?) {
        _state.update { it.copy(kind = kind) }
        run(debounceMs = 0)
    }

    fun retry() = run(debounceMs = 0)

    private fun run(debounceMs: Long) {
        job?.cancel()
        val query = _state.value.query.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(loading = false, results = emptyList(), videos = emptyList(), collections = emptyMap(), errors = emptyMap(), suggestions = emptyList()) }
            return
        }
        // A signed-out service's songs can't play, so they aren't offered (no dead ends for a new user).
        val targets = _state.value.only?.let { id -> listOfNotNull(sources[id]) } ?: sources.music.filter { it.playableNow }
        job = scope.launch {
            delay(debounceMs)
            _state.update { it.copy(loading = true, errors = emptyMap()) }
            // Suggestions come in on their own; a slow or failing one never holds up the results.
            launch {
                val s = targets.firstNotNullOfOrNull { p -> runCatching { p.suggestions(query) }.getOrNull()?.takeIf { it.isNotEmpty() } }.orEmpty()
                _state.update { st -> st.copy(suggestions = s.filter { !it.equals(query, ignoreCase = true) }.take(6)) }
            }
            val kind = _state.value.kind
            val wantSongs = kind == null || kind == SearchKind.Songs
            val wantVideos = kind == null || kind == SearchKind.Videos
            val videoJobs = if (wantVideos) targets.filter { it.searchesVideos }.map { p -> async { runCatching { p.searchVideos(query) }.getOrDefault(emptyList()) } } else emptyList()
            val otherKinds = if (kind == null) SearchKind.entries.filter { it != SearchKind.Songs && it != SearchKind.Videos }
                else listOfNotNull(kind.takeIf { it != SearchKind.Songs && it != SearchKind.Videos })
            val songJobs = if (wantSongs) targets.map { p -> async { p to runCatching { p.search(query) } } } else emptyList()
            val collectionJobs = otherKinds.associateWith { k ->
                targets.filter { k in it.collectionKinds }.map { p -> async { runCatching { p.searchCollections(query, k) }.getOrDefault(emptyList()) } }
            }
            val outcomes = songJobs.awaitAll()
            outcomes.forEach { (_, r) -> (r.exceptionOrNull() as? CancellationException)?.let { throw it } }
            // "All" shows a few of each kind; a single kind shows everything found.
            val perKind = if (kind == null) 4 else Int.MAX_VALUE
            val collections = collectionJobs.mapValues { (_, jobs) -> interleave(jobs.awaitAll()).take(perKind) }.filterValues { it.isNotEmpty() }
            _state.update {
                it.copy(
                    loading = false,
                    // A song both services have shows once: from a service that can play it (signed in, or needing no account) first.
                    results = oneOfEach(interleave(outcomes.sortedBy { (p, _) -> !p.playableNow }
                        .map { (_, r) -> r.getOrDefault(emptyList()) })).let { r -> if (kind == null) r.take(12) else r },
                    collections = collections,
                    videos = interleave(videoJobs.awaitAll()).let { v -> if (kind == null) v.take(4) else v },
                    errors = outcomes.mapNotNull { (p, r) -> r.exceptionOrNull()?.let { e -> p.manifest.id to (e.message ?: "Search failed") } }.toMap(),
                )
            }
        }
    }

    /** Drops a song already listed under the same title and first artist (letters and digits only). */
    private fun oneOfEach(tracks: List<Track>): List<Track> {
        fun norm(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
        val seen = HashSet<String>()
        return tracks.filter { seen.add(norm(it.title) + "|" + norm(app.duet.music.lyrics.LyricsService.firstArtist(it.artist))) }
    }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = ArrayList<T>(lists.sumOf { it.size })
        var i = 0
        while (lists.any { i < it.size }) {
            lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
            i++
        }
        return out
    }
}
