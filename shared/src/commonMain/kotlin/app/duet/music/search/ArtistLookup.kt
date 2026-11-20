package app.duet.music.search

import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.domain.Track
import app.duet.music.extension.Capability
import app.duet.music.extension.SourceRegistry
import app.duet.music.lyrics.LyricsService

/**
 * The artist page behind a song's artist line: the song's first artist, looked up on the song's own
 * service first, then on the others. Null for radio stations and when no service knows the artist.
 */
suspend fun findArtist(sources: SourceRegistry, track: Track): Collection? {
    val own = sources[track.sourceId] ?: return null
    if (Capability.LiveRadio in own.manifest.capabilities) return null
    val name = LyricsService.firstArtist(track.artist).ifBlank { return null }
    val order = listOf(own) + sources.music.filter { it !== own && it.playableNow }
    for (p in order) {
        if (SearchKind.Artists !in p.collectionKinds) continue
        val found = runCatching { p.searchCollections(name, SearchKind.Artists) }.getOrDefault(emptyList())
        pickArtist(name, found)?.let { return it }
    }
    return null
}

/** The result named exactly [name] (ignoring case and punctuation), else the top result. */
fun pickArtist(name: String, candidates: List<Collection>): Collection? {
    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    return candidates.firstOrNull { norm(it.title) == norm(name) } ?: candidates.firstOrNull()
}
