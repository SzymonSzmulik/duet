package app.duet.music.extension

import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.domain.Shelf
import app.duet.music.domain.Track

/** What an extension can do (SPEC §3b). [Download]: its audio is a plain stream that may be saved for offline play. [LiveRadio]: live stations (no search, no saving). */
enum class Capability { Search, Library, Radio, Lyrics, Download, LiveRadio }

/** What an extension may reach. Shown to the user before a third-party extension is enabled (Phase 2). */
data class ExtensionPermissions(
    val networkHosts: List<String> = emptyList(),
    val accountScopes: List<String> = emptyList(),
    val storage: Boolean = false,
)

data class ExtensionManifest(
    /** Reverse-domain, never changes: `app.duet.source.ytmusic`. */
    val id: String,
    val name: String,
    val version: String,
    val capabilities: Set<Capability>,
    val permissions: ExtensionPermissions,
)

/**
 * A setting an extension declares; the core draws it on the extension's own page and stores it in
 * that extension's private store ([app.duet.music.settings.ExtensionSettings]).
 */
sealed interface ExtensionSetting {
    val key: String
    val title: String
    val detail: String

    data class Toggle(override val key: String, override val title: String, override val detail: String, val default: Boolean) : ExtensionSetting
}

/** How a source wants a track played. Each [app.duet.music.player.SourcePlayer] says which kinds it can play. */
sealed interface Playable {
    /** A plain audio URL the device plays itself (ExoPlayer on Android). */
    data class Stream(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val mimeType: String? = null,
        /** Shown on Now Playing, e.g. "Opus · 146 kbps". */
        val quality: String? = null,
        /** Key for the device's audio cache (the song's key), so a cached song plays even when its URL changed. */
        val cacheKey: String? = null,
    ) : Playable

    /**
     * A song the service's own player must play (it stays DRM-protected inside that player), e.g.
     * `spotify:track:…` played by the Spotify Web Player. Never downloaded.
     */
    data class Remote(val uri: String, val quality: String? = null) : Playable
}

/** A change to one of your playlists on a service ([SourceProvider.editPlaylist]). */
sealed interface PlaylistEdit {
    /** Takes out row [index], which must still be [track] (else nothing changes). */
    data class Remove(val index: Int, val track: Track) : PlaylistEdit
    /** Moves row [index] one place up (before row index - 1). Moving row i down is MoveUp(i + 1). */
    data class MoveUp(val index: Int) : PlaylistEdit
    data class Rename(val name: String) : PlaylistEdit
}

/**
 * A music source plug-in. Spotify, YouTube Music and later ones (local files, podcasts) are all
 * just implementations; the core and UI know nothing source-specific.
 */
interface SourceProvider {
    val manifest: ExtensionManifest
    /** Short label for switches and rows, e.g. "YT Music". */
    val shortName: String
    /** Brand dot color (ARGB), always shown next to [manifest] name, never alone. */
    val color: Long
    /** One line for the profile sheet: account state, e.g. "No account needed" or "Signed in as …". */
    val accountStatus: String
    /** Sign-in for sources with accounts; null when the source needs none. Its status replaces [accountStatus]. */
    val account: SourceAccount? get() = null

    /** Can play right now: no account needed, sign-in optional, or signed in. */
    val playableNow: Boolean get() = account?.let { !it.neededToPlay || it.signedIn.value } ?: true
    /** Settings shown on this source's page. */
    val settings: List<ExtensionSetting> get() = emptyList()

    suspend fun search(query: String): List<Track>

    /** Any upload matching [query] (remixes, covers, live) as playable songs; empty if the source has none. */
    suspend fun searchVideos(query: String): List<Track> = emptyList()
    val searchesVideos: Boolean get() = false

    /** Albums, artists or playlists matching [query]; only for kinds in [collectionKinds]. */
    suspend fun searchCollections(query: String, kind: SearchKind): List<Collection> = emptyList()
    /** Kinds besides songs this source can search. */
    val collectionKinds: Set<SearchKind> get() = emptySet()
    /** The signed-in user's playlists (liked songs first), albums and artists; empty when signed out. */
    suspend fun library(): List<Collection> = emptyList()

    /** Songs like [track] from this source (a radio); empty if it has none. */
    suspend fun similar(track: Track): List<Track> = emptyList()

    /** What the service would show on its own home page. */
    suspend fun homeShelves(): List<Shelf> = emptyList()

    /** The songs of an album or playlist, or an artist's top songs. */
    suspend fun collectionTracks(collection: Collection): List<Track> = emptyList()

    /** Search suggestions for what's typed so far ("dua l" → "dua lipa levitating"). */
    suspend fun suggestions(query: String): List<String> = emptyList()

    /** A web link to [track] for sharing; null when it has none. */
    fun shareUrl(track: Track): String? = null

    /** Likes or unlikes [track] in your account on this service (signed in only). True when the service took it. */
    suspend fun setLiked(track: Track, liked: Boolean): Boolean = false

    /** Your playlists on this service that [addToPlaylist] can try (not Liked songs), from [library] items. */
    fun addablePlaylists(library: List<Collection>): List<Collection> = emptyList()

    /** Adds [track] (one of this service's songs) to the end of [playlist] in your account. True when the service took it. */
    suspend fun addToPlaylist(playlist: Collection, track: Track): Boolean = false

    /**
     * Changes one of your playlists on this service ([addablePlaylists]). Rows are counted as in
     * [collectionTracks]. True when the service took it; it refuses playlists you can't edit.
     */
    suspend fun editPlaylist(playlist: Collection, edit: PlaylistEdit): Boolean = false

    /** Whether [removeFromLibrary] can take [item] (one of [library]'s; not Liked songs). */
    fun canRemoveFromLibrary(item: Collection): Boolean = false

    /**
     * Takes [item] out of your library on this service: a playlist you made is deleted, one you follow
     * is unfollowed, an album unsaved, an artist unfollowed. True when the service took it.
     */
    suspend fun removeFromLibrary(item: Collection): Boolean = false

    /** The full artist page for an artist from [searchCollections] or a shelf; null when the source has none. */
    suspend fun artistPage(artist: Collection): app.duet.music.domain.ArtistPage? = null

    /** Turns one of this source's tracks into something a player can start. */
    suspend fun resolve(track: Track): Playable
}

/** The installed sources, in display order. */
class SourceRegistry(val providers: List<SourceProvider>) {
    private val byId = providers.associateBy { it.manifest.id }

    init {
        require(byId.size == providers.size) { "Two sources share an id" }
    }

    operator fun get(sourceId: String): SourceProvider? = byId[sourceId]

    /** The music services (songs, albums, playlists); live radio is kept out of song search and service switches. */
    val music: List<SourceProvider> get() = providers.filter { Capability.LiveRadio !in it.manifest.capabilities }
}
