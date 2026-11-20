package app.duet.music.sources.ytmusic

/**
 * Everything Duet needs from YouTube Music. YouTube Music has no official API: the only
 * implementation ([InnerTubeYtMusicSource]) uses unofficial endpoints that can break at any time,
 * so all of it stays behind this interface and can be swapped (NewPipe Extractor, embedded player...).
 */
interface YtMusicSource {
    suspend fun search(query: String): List<YtSong>
    /** Any YouTube upload (remixes, covers, live videos), as playable songs. */
    suspend fun searchVideos(query: String): List<YtSong>
    suspend fun streamUrl(videoId: String, quality: StreamQuality = StreamQuality.High): YtStream
    /** Albums, artists or playlists (one of [YtMusicParser.FILTERS]' kinds). */
    suspend fun searchCollections(query: String, kind: app.duet.music.domain.SearchKind): List<YtCollection>
    /** Songs like [videoId] (YouTube Music's radio for it, the source of "Quick picks"). */
    suspend fun radio(videoId: String): List<YtSong>
    /** The signed-in user's playlists (library page); empty when signed out. */
    suspend fun libraryPlaylists(): List<YtCollection>
    /** The home feed's shelves (logged out). */
    suspend fun homeShelves(): List<YtShelf>
    /** Songs on a browse page (album, playlist, artist) by its browseId. */
    suspend fun browseSongs(browseId: String): List<YtSong>

    /** Typing suggestions (InnerTube `music/get_search_suggestions`). */
    suspend fun suggestions(query: String): List<String> = emptyList()

    /** Likes or unlikes a song in the signed-in account (InnerTube `like/like`, `like/removelike`). */
    suspend fun like(videoId: String, liked: Boolean): Boolean = false

    /** Changes one of the signed-in account's playlists (InnerTube `browse/edit_playlist`). True when YouTube took it. */
    suspend fun editPlaylist(playlistId: String, edit: YtPlaylistEdit): Boolean = false

    /** Deletes the account's own playlist, or takes a saved one out of the library. True when YouTube took it. */
    suspend fun removePlaylist(playlistId: String): Boolean = false

    /** An artist's channel page: picture, top songs and the carousels under them. */
    suspend fun artist(browseId: String): YtArtist = YtArtist(null, browseSongs(browseId), emptyList())
    /** The signed-in account's name, from the music.youtube.com [cookie] header; null if unknown. */
    suspend fun accountName(cookie: String): String?
}

data class YtSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    /** The row's id inside a playlist page (`playlistSetVideoId`), which removing and moving it need. */
    val setVideoId: String? = null,
)

data class YtCollection(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
)

/** A home shelf: songs (videos) and/or browse pages (albums, playlists, artists). */
data class YtShelf(val title: String, val songs: List<YtSong>, val pages: List<YtCollection>)

data class YtArtist(val imageUrl: String?, val songs: List<YtSong>, val shelves: List<YtShelf>, val about: String? = null)

data class YtStream(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    /** Headers the stream host expects (the same User-Agent as the client that asked for the URL). */
    val headers: Map<String, String>,
)

/** `edit_playlist` actions (SpatialFlow InnerTubeClient, ytmusicapi). Rows are addressed by their [YtSong.setVideoId]. */
sealed interface YtPlaylistEdit {
    data class Add(val videoId: String) : YtPlaylistEdit
    data class Remove(val setVideoId: String, val videoId: String) : YtPlaylistEdit
    data class MoveBefore(val setVideoId: String, val beforeSetVideoId: String) : YtPlaylistEdit
    data class Rename(val name: String) : YtPlaylistEdit
}

/** Which of a video's audio streams to play, by bitrate (SpatialFlow's High / Normal / Data Saver). */
enum class StreamQuality {
    High, Normal, Low;

    /** Highest bitrate, the middle one, or the lowest. */
    fun <T> pick(streams: List<T>, bitrate: (T) -> Int): T? {
        val sorted = streams.sortedBy(bitrate)
        return when (this) { High -> sorted.lastOrNull(); Normal -> sorted.getOrNull(sorted.size / 2); Low -> sorted.firstOrNull() }
    }
}

class YtMusicException(message: String) : Exception(message)
