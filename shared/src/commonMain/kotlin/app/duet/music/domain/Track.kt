package app.duet.music.domain

/**
 * A playable song from any source. [sourceId] is the id of the [app.duet.music.extension.SourceProvider]
 * that owns it; [id] is that source's own id (a YouTube videoId, a Spotify track id, ...).
 */
data class Track(
    val sourceId: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
) {
    /** Unique across sources. */
    val key: String get() = "$sourceId:$id"
}

/** What a search can look for. */
enum class SearchKind(val label: String) { Songs("Songs"), Videos("Videos"), Albums("Albums"), Artists("Artists"), Playlists("Playlists") }

/**
 * An album, artist or playlist from a source. [id] is what that source needs to list its songs
 * (a YouTube Music browseId, a Spotify URI).
 */
data class Collection(
    val sourceId: String,
    val id: String,
    val kind: SearchKind,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
) {
    val key: String get() = "$sourceId:$id"
}

/** A row of Home: songs and/or albums and playlists, under a title ("Quick picks", "New releases"). */
data class Shelf(
    /** The source the shelf comes from (Home's service switch filters on it). */
    val sourceId: String,
    val title: String,
    val songs: List<Track> = emptyList(),
    val collections: List<Collection> = emptyList(),
)

/** An artist's page: a big picture, their popular songs, then rows such as Albums, Singles and Fans also like. */
data class ArtistPage(
    val imageUrl: String?,
    val songs: List<Track>,
    val shelves: List<Shelf>,
    /** One line under the name, e.g. "67M monthly listeners". */
    val about: String? = null,
)
