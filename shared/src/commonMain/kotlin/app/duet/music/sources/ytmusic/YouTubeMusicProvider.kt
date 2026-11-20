package app.duet.music.sources.ytmusic

import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.domain.Shelf
import app.duet.music.domain.Track
import app.duet.music.extension.Capability
import app.duet.music.extension.ExtensionManifest
import app.duet.music.extension.ExtensionPermissions
import app.duet.music.extension.ExtensionSetting
import app.duet.music.extension.Playable
import app.duet.music.extension.SourceProvider
import app.duet.music.extension.SourceAccount
import app.duet.music.extension.WebLoginSession
import app.duet.music.settings.ExtensionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** YouTube Music as a [SourceProvider] plug-in. All YouTube-specific calls live in [YtMusicSource]. */
class YouTubeMusicProvider(
    private val yt: YtMusicSource,
    private val prefs: ExtensionSettings,
    /** Google sign-in on music.youtube.com (optional: search and playback work without it). */
    private val login: WebLoginSession? = null,
    scope: CoroutineScope? = null,
    /** Which audio stream to play (Settings > Sound > quality for the current network). */
    private val quality: () -> StreamQuality = { StreamQuality.High },
) : SourceProvider {

    override val manifest = ExtensionManifest(
        id = ID,
        name = "YouTube Music",
        version = "0.2.0",
        capabilities = setOf(Capability.Search, Capability.Download),
        permissions = ExtensionPermissions(
            accountScopes = listOf("Optional: your Google sign-in on music.youtube.com (you sign in on Google's own page)"),
            networkHosts = listOf("music.youtube.com", "www.youtube.com", "*.googlevideo.com", "*.ggpht.com", "*.googleusercontent.com", "i.ytimg.com"),
        ),
    )
    override val shortName = "YT Music"
    override val color = 0xFFFF6B6B // design token color.sourceYouTubeMusic
    override val accountStatus get() = if (login == null) "No account needed" else _status.value

    private val _status = MutableStateFlow(STATUS_OUT)
    override val account: SourceAccount? = login?.let { session ->
        object : SourceAccount {
            override val neededToPlay = false
            override val status: StateFlow<String> = _status.asStateFlow()
            override val signedIn: StateFlow<Boolean> = session.signedIn
            override fun signIn() = session.signIn()
            override fun signOut() = session.signOut()
        }
    }

    init {
        if (login != null) requireNotNull(scope) { "YouTube Music sign-in needs a scope" }.launch {
            login.signedIn.collect { on ->
                _status.value = if (!on) STATUS_OUT else {
                    _status.value = "Signed in"
                    login.cookieHeader()?.let { yt.accountName(it) }?.let { "Signed in as $it" } ?: "Signed in"
                }
            }
        }
    }

    override suspend fun search(query: String): List<Track> = yt.search(query).map {
        Track(
            sourceId = ID,
            id = it.videoId,
            title = it.title,
            artist = it.artist,
            album = it.album,
            artworkUrl = it.thumbnailUrl,
            durationMs = it.durationSeconds?.let { s -> s * 1000L },
        )
    }

    override val searchesVideos = true
    override suspend fun searchVideos(query: String): List<Track> = yt.searchVideos(query).map { toTrack(it) }

    override val collectionKinds = setOf(SearchKind.Albums, SearchKind.Artists, SearchKind.Playlists)

    override suspend fun searchCollections(query: String, kind: SearchKind): List<Collection> =
        yt.searchCollections(query, kind).map { Collection(ID, it.browseId, kind, it.title, it.subtitle, it.thumbnailUrl) }

    /** Liked music (the LM playlist) and the library's playlists, while signed in. */
    override suspend fun library(): List<Collection> {
        if (login?.signedIn?.value != true) return emptyList()
        val liked = Collection(ID, "VLLM", SearchKind.Playlists, "Liked music", "Playlist · YouTube Music")
        val lists = yt.libraryPlaylists().filter { it.browseId != "VLLM" }
            .map { Collection(ID, it.browseId, kindOf(it.browseId), it.title, it.subtitle, it.thumbnailUrl) }
        return listOf(liked) + lists
    }

    /** YouTube Music's radio for the song; another service's song is first looked up here by title and artist. */
    override suspend fun similar(track: Track): List<Track> {
        val videoId = if (track.sourceId == ID) track.id else
            app.duet.music.downloads.SongMatch.best(track, search(app.duet.music.downloads.SongMatch.query(track)))?.id ?: return emptyList()
        return yt.radio(videoId).map { toTrack(it) }
    }

    override suspend fun homeShelves(): List<Shelf> = yt.homeShelves().map { sh ->
        Shelf(
            sourceId = ID,
            title = sh.title,
            songs = sh.songs.map { toTrack(it) },
            collections = sh.pages.map { Collection(ID, it.browseId, kindOf(it.browseId), it.title, it.subtitle, it.thumbnailUrl) },
        )
    }

    private fun toTrack(it: YtSong) = Track(ID, it.videoId, it.title, it.artist, it.album, it.thumbnailUrl, it.durationSeconds?.let { s -> s * 1000L })

    /** browseId prefixes: MPREb albums, UC artists, VL/RD/PL playlists. */
    private fun kindOf(browseId: String) = when {
        browseId.startsWith("MPRE") -> SearchKind.Albums
        browseId.startsWith("UC") -> SearchKind.Artists
        else -> SearchKind.Playlists
    }

    override suspend fun setLiked(track: Track, liked: Boolean): Boolean = yt.like(track.id, liked)

    /** Playlists (browse ids "VL" + playlist id), not Liked music. */
    override fun addablePlaylists(library: List<Collection>) =
        library.filter { it.sourceId == ID && it.kind == SearchKind.Playlists && it.id.startsWith("VL") && it.id != "VLLM" }

    override suspend fun addToPlaylist(playlist: Collection, track: Track): Boolean =
        track.sourceId == ID && yt.editPlaylist(playlist.id.removePrefix("VL"), YtPlaylistEdit.Add(track.id))

    override fun canRemoveFromLibrary(item: Collection) = addablePlaylists(listOf(item)).isNotEmpty()

    override suspend fun removeFromLibrary(item: Collection): Boolean =
        canRemoveFromLibrary(item) && yt.removePlaylist(item.id.removePrefix("VL"))

    /** Remove and move need each row's setVideoId, so the playlist page is read again and the row checked. */
    override suspend fun editPlaylist(playlist: Collection, edit: app.duet.music.extension.PlaylistEdit): Boolean {
        val id = playlist.id.removePrefix("VL")
        val ytEdit = when (edit) {
            is app.duet.music.extension.PlaylistEdit.Rename -> YtPlaylistEdit.Rename(edit.name)
            is app.duet.music.extension.PlaylistEdit.Remove -> {
                val row = yt.browseSongs(playlist.id).getOrNull(edit.index)?.takeIf { it.videoId == edit.track.id } ?: return false
                YtPlaylistEdit.Remove(row.setVideoId ?: return false, row.videoId)
            }
            is app.duet.music.extension.PlaylistEdit.MoveUp -> {
                val rows = yt.browseSongs(playlist.id)
                val moved = rows.getOrNull(edit.index)?.setVideoId ?: return false
                YtPlaylistEdit.MoveBefore(moved, rows.getOrNull(edit.index - 1)?.setVideoId ?: return false)
            }
        }
        return yt.editPlaylist(id, ytEdit)
    }

    override suspend fun suggestions(query: String): List<String> = yt.suggestions(query)

    override fun shareUrl(track: Track) = "https://music.youtube.com/watch?v=${track.id}"

    override suspend fun artistPage(artist: Collection): app.duet.music.domain.ArtistPage? {
        if (artist.kind != SearchKind.Artists) return null
        val a = yt.artist(artist.id)
        return app.duet.music.domain.ArtistPage(
            imageUrl = a.imageUrl ?: artist.artworkUrl,
            songs = a.songs.map { toTrack(it).let { t -> if (t.artist.isBlank()) t.copy(artist = artist.title) else t } },
            shelves = a.shelves.map { sh ->
                Shelf(ID, sh.title, sh.songs.map { toTrack(it) }, sh.pages.map { Collection(ID, it.browseId, kindOf(it.browseId), it.title, it.subtitle, it.thumbnailUrl) })
            },
            about = a.about,
        )
    }

    /** Album and playlist rows often leave out the artist and cover; the collection fills them in. */
    override suspend fun collectionTracks(collection: Collection): List<Track> =
        yt.browseSongs(collection.id).map {
            Track(
                sourceId = ID,
                id = it.videoId,
                title = it.title,
                artist = it.artist.ifBlank { if (collection.kind == SearchKind.Artists) collection.title else collection.subtitle.substringAfter(" · ", collection.subtitle) },
                album = it.album ?: collection.title.takeIf { collection.kind == SearchKind.Albums },
                artworkUrl = it.thumbnailUrl ?: collection.artworkUrl,
                durationMs = it.durationSeconds?.let { s -> s * 1000L },
            )
        }

    override suspend fun resolve(track: Track): Playable {
        require(track.sourceId == ID) { "Not a YouTube Music track: ${track.key}" }
        val stream = yt.streamUrl(track.id, quality())
        return Playable.Stream(stream.url, stream.headers, stream.mimeType, qualityLabel(stream))
    }

    private fun qualityLabel(s: YtStream): String {
        val codec = when {
            "opus" in s.mimeType || "webm" in s.mimeType -> "Opus"
            "mp4" in s.mimeType || "m4a" in s.mimeType -> "AAC"
            else -> s.mimeType.substringAfter('/').substringBefore(';')
        }
        return if (s.bitrate > 0) "$codec · ${s.bitrate / 1000} kbps" else codec
    }

    companion object {
        const val ID = "app.duet.source.ytmusic"
        const val KEY_DATA_SAVER = "dataSaver"
        private const val STATUS_OUT = "Not signed in (optional)"
    }
}
