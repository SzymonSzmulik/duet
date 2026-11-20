package app.duet.music.sources.spotify

import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.domain.Shelf
import app.duet.music.domain.Track
import app.duet.music.extension.Capability
import app.duet.music.extension.ExtensionManifest
import app.duet.music.extension.ExtensionPermissions
import app.duet.music.extension.Playable
import app.duet.music.extension.SourceAccount
import app.duet.music.extension.SourceProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A Spotify Web Player access token, as `open.spotify.com/api/token` hands it to the page. */
data class SpotifyWebToken(val accessToken: String, val expiresAtMs: Long, val isAnonymous: Boolean)

/**
 * The platform's Spotify Web Player session (Myspoti's approach, docs/DECISIONS.md D24): the user
 * signs in on Spotify's own login page, the session lives in that page's cookies, and the token is
 * the one the Web Player page fetches for itself. Nothing here computes or stores a password.
 */
interface SpotifyWebSession {
    /** The current token, or null when signed out or not fetched yet. */
    val token: StateFlow<SpotifyWebToken?>
    /** Headers the Web Player sends to its own GraphQL (`client-token`, `spotify-app-version`); empty until seen. */
    val apiHeaders: StateFlow<Map<String, String>>
    /** True while a Spotify session cookie exists. */
    val signedIn: StateFlow<Boolean>
    /**
     * Sends a request from inside the Web Player page (same browser, cookies and origin as Spotify's own
     * calls; Spotify refuses its GraphQL from other clients). Returns the HTTP status and body text.
     */
    suspend fun pageFetch(url: String, method: String, headers: Map<String, String>, body: String?): Pair<Int, String>
    /** Shows Spotify's login page. */
    fun signIn()
    /** Forgets the session (cookies and token). */
    fun signOut()
}

/** Spotify as a [SourceProvider]. Build step 3, first part: sign-in only; search and playback follow. */
class SpotifyProvider(
    private val session: SpotifyWebSession,
    private val http: HttpClient,
    scope: CoroutineScope,
) : SourceProvider, SourceAccount {

    override val manifest = ExtensionManifest(
        id = ID,
        name = "Spotify",
        version = "0.1.0",
        capabilities = setOf(Capability.Search),
        permissions = ExtensionPermissions(
            networkHosts = listOf("accounts.spotify.com", "open.spotify.com", "api.spotify.com"),
            accountScopes = listOf("Your Spotify Web Player session (you sign in on Spotify's own page)"),
        ),
    )
    override val shortName = "Spotify"
    override val color = 0xFF3ECF7A // design token color.sourceSpotify
    override val account: SourceAccount get() = this

    private val _status = MutableStateFlow(STATUS_OUT)
    override val status: StateFlow<String> = _status.asStateFlow()
    override val signedIn: StateFlow<Boolean> = session.signedIn
    override val accountStatus: String get() = _status.value

    init {
        scope.launch {
            session.token.distinctUntilChangedBy { it?.accessToken }.collect { t ->
                _status.value = when {
                    !session.signedIn.value -> STATUS_OUT
                    t == null || t.isAnonymous -> "Connecting…"
                    else -> profileName(t)?.let { "Signed in as $it" } ?: "Signed in"
                }
            }
        }
        scope.launch {
            session.signedIn.collect { if (!it) _status.value = STATUS_OUT else if (session.token.value == null) _status.value = "Connecting…" }
        }
    }

    /** Why the GraphQL search failed last, for the error message. */
    private var gqlProblem: String? = null

    override fun signIn() = session.signIn()
    override fun signOut() = session.signOut()

    private suspend fun profileName(t: SpotifyWebToken): String? = runCatching {
        val r = http.get("https://api.spotify.com/v1/me") { header("Authorization", "Bearer ${t.accessToken}") }
        if (!r.status.isSuccess()) return null
        val me = Json.parseToJsonElement(r.bodyAsText()).jsonObject
        (me["display_name"] ?: me["id"])?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /**
     * Search as the Web Player does it: its GraphQL `searchTracks` (Myspoti's docs/api/SEARCH-HOME.md),
     * with the page's token and client-token. The public Web API is the fallback; Spotify often answers
     * it with HTTP 429 for Web Player tokens.
     */
    override suspend fun search(query: String): List<Track> {
        val token = withTimeoutOrNull(15_000) { session.token.filterNotNull().first() }
            ?: throw IllegalStateException("Spotify isn't ready yet. Try again in a moment.")
        val headers = withTimeoutOrNull(10_000) { session.apiHeaders.first { it.containsKey("client-token") } }
        if (headers != null) {
            val (status, body) = session.pageFetch(
                url = "https://api-partner.spotify.com/pathfinder/v2/query",
                method = "POST",
                headers = headers + mapOf(
                    "authorization" to "Bearer ${token.accessToken}",
                    "app-platform" to "WebPlayer",
                    "content-type" to "application/json;charset=UTF-8",
                    "accept" to "application/json",
                ),
                body = searchBody(query),
            )
            if (status in 200..299) {
                val tracks = parseGqlTracks(Json.parseToJsonElement(body).jsonObject)
                if (tracks.isNotEmpty()) return tracks
            }
            gqlProblem = "GraphQL HTTP $status: ${body.take(160)}"
        }
        val r = http.get("https://api.spotify.com/v1/search") {
            header("Authorization", "Bearer ${token.accessToken}")
            parameter("q", query)
            parameter("type", "track")
            parameter("limit", 20)
        }
        if (!r.status.isSuccess()) throw IllegalStateException("Spotify answered HTTP ${r.status.value}" + (gqlProblem?.let { "; $it" } ?: ""))
        return parseTracks(Json.parseToJsonElement(r.bodyAsText()).jsonObject)
    }

    override val collectionKinds = setOf(SearchKind.Albums, SearchKind.Artists, SearchKind.Playlists)

    override suspend fun searchCollections(query: String, kind: SearchKind): List<Collection> {
        val (op, path) = when (kind) {
            SearchKind.Albums -> "searchAlbums" to "albumsV2"
            SearchKind.Artists -> "searchArtists" to "artists"
            SearchKind.Playlists -> "searchPlaylists" to "playlists"
            SearchKind.Songs, SearchKind.Videos -> return emptyList()
        }
        val root = gql(op, HASHES.getValue(op), searchVariables(query)) ?: return emptyList()
        return parseCollections(root, path, kind)
    }

    override suspend fun collectionTracks(collection: Collection): List<Track> {
        val uri = collection.id
        if (uri == LIKED_URI) {
            // Liked Songs: GraphQL fetchLibraryTracks (Myspoti SpotifyLibrary).
            return gql("fetchLibraryTracks", HASHES.getValue("fetchLibraryTracks"), buildJsonObject { put("offset", 0); put("limit", 200) })
                ?.let { root ->
                    (root.at("data", "me", "library", "tracks", "items") as? JsonArray)?.mapNotNull { row ->
                        val d = row.at("track", "data") as? JsonObject ?: return@mapNotNull null
                        val withUri = if (d["uri"] == null) row.at("track", "_uri").s()?.let { JsonObject(d + ("uri" to JsonPrimitive(it))) } ?: d else d
                        trackFrom(withUri)
                    }
                }.orEmpty()
        }
        return when (collection.kind) {
            SearchKind.Albums -> gql("getAlbum", HASHES.getValue("getAlbum"), buildJsonObject {
                put("uri", uri); put("locale", ""); put("offset", 0); put("limit", 100)
            })?.let { root ->
                val album = root.at("data", "albumUnion")
                val cover = (album.at("coverArt", "sources") as? JsonArray)?.lastOrNull()?.at("url").s()
                (album.at("tracksV2", "items") as? JsonArray)?.mapNotNull { trackFrom(it.at("track"))?.copy(album = album.at("name").s(), artworkUrl = cover) }
            }
            SearchKind.Playlists -> gql("fetchPlaylist", HASHES.getValue("fetchPlaylist"), buildJsonObject {
                put("uri", uri); put("offset", 0); put("limit", 100)
                put("enableWatchFeedEntrypoint", false); put("includeEpisodeContentRatingsV2", false)
            })?.let { root -> (root.at("data", "playlistV2", "content", "items") as? JsonArray)?.mapNotNull { trackFrom(it.at("itemV2", "data")) } }
            SearchKind.Artists -> gql("queryArtistOverview", HASHES.getValue("queryArtistOverview"), buildJsonObject {
                put("uri", uri); put("locale", ""); put("preReleaseV2", false)
            })?.let { root -> (root.at("data", "artistUnion", "discography", "topTracks", "items") as? JsonArray)?.mapNotNull { trackFrom(it.at("track")) } }
            SearchKind.Songs, SearchKind.Videos -> null
        }.orEmpty()
    }

    override fun shareUrl(track: Track) = "https://open.spotify.com/track/${track.id}"

    /** The heart: `addToLibrary` / `removeFromLibrary` with the song's URI (Myspoti docs/api/LIBRARY.md). */
    override suspend fun setLiked(track: Track, liked: Boolean): Boolean {
        if (!session.signedIn.value) return false
        val op = if (liked) "addToLibrary" else "removeFromLibrary"
        return gql(op, HASHES.getValue(op), buildJsonObject {
            put("libraryItemUris", JsonArray(listOf(JsonPrimitive("spotify:track:${track.id}"))))
        }) != null
    }

    override fun addablePlaylists(library: List<Collection>) =
        library.filter { it.sourceId == ID && it.id.startsWith("spotify:playlist:") }

    /** GraphQL `addToPlaylist` (Myspoti docs/api/LIBRARY.md); Spotify refuses playlists you don't own or edit. */
    override suspend fun addToPlaylist(playlist: Collection, track: Track): Boolean {
        if (!session.signedIn.value || track.sourceId != ID) return false
        val root = gql("addToPlaylist", HASHES.getValue("addToPlaylist"), buildJsonObject {
            put("playlistUri", playlist.id)
            put("playlistItemUris", JsonArray(listOf(JsonPrimitive("spotify:track:${track.id}"))))
            putJsonObject("newPosition") { put("moveType", "BOTTOM_OF_PLAYLIST"); put("fromUid", kotlinx.serialization.json.JsonNull) }
        }) ?: return false
        return root["errors"] == null
    }

    /**
     * Remove and move address rows by `uid` (the same song twice has two), so the playlist is read again
     * first and the row checked; rename is a playlist/v2 change (Myspoti docs/api/LIBRARY.md, SpotifyLibrary).
     */
    override suspend fun editPlaylist(playlist: Collection, edit: app.duet.music.extension.PlaylistEdit): Boolean {
        if (!session.signedIn.value || !playlist.id.startsWith("spotify:playlist:")) return false
        if (edit is app.duet.music.extension.PlaylistEdit.Rename) return renamePlaylist(playlist.id, edit.name)
        val rows = playlistRows(playlist.id) ?: return false
        val variables = when (edit) {
            is app.duet.music.extension.PlaylistEdit.Remove -> {
                val row = rows.getOrNull(edit.index)?.takeIf { it.second == edit.track.id } ?: return false
                buildJsonObject { put("playlistUri", playlist.id); put("uids", JsonArray(listOf(JsonPrimitive(row.first)))) }
            }
            is app.duet.music.extension.PlaylistEdit.MoveUp -> {
                val moved = rows.getOrNull(edit.index) ?: return false
                val before = rows.getOrNull(edit.index - 1) ?: return false
                buildJsonObject {
                    put("playlistUri", playlist.id); put("uids", JsonArray(listOf(JsonPrimitive(moved.first))))
                    putJsonObject("newPosition") { put("moveType", "BEFORE_UID"); put("fromUid", before.first) }
                }
            }
            is app.duet.music.extension.PlaylistEdit.Rename -> return false
        }
        val op = if (edit is app.duet.music.extension.PlaylistEdit.Remove) "removeFromPlaylist" else "moveItemsInPlaylist"
        return gql(op, HASHES.getValue(op), variables)?.let { it["errors"] == null } == true
    }

    override fun canRemoveFromLibrary(item: Collection) =
        item.sourceId == ID && listOf("spotify:playlist:", "spotify:album:", "spotify:artist:").any { item.id.startsWith(it) }

    /**
     * `removeFromLibrary` with the item's URI, as the Web Player's "Remove from Your Library" (Myspoti
     * docs/api/LIBRARY.md). For a playlist you made that is Spotify's delete: it leaves your library and profile.
     */
    override suspend fun removeFromLibrary(item: Collection): Boolean {
        if (!session.signedIn.value || !canRemoveFromLibrary(item)) return false
        return gql("removeFromLibrary", HASHES.getValue("removeFromLibrary"), buildJsonObject {
            put("libraryItemUris", JsonArray(listOf(JsonPrimitive(item.id))))
        })?.let { it["errors"] == null } == true
    }

    /** (uid, track id) per playable row, in the order [collectionTracks] lists them. */
    private suspend fun playlistRows(uri: String): List<Pair<String, String>>? =
        gql("fetchPlaylist", HASHES.getValue("fetchPlaylist"), buildJsonObject {
            put("uri", uri); put("offset", 0); put("limit", 100)
            put("enableWatchFeedEntrypoint", false); put("includeEpisodeContentRatingsV2", false)
        })?.let { root ->
            (root.at("data", "playlistV2", "content", "items") as? JsonArray)?.mapNotNull { row ->
                trackFrom(row.at("itemV2", "data"))?.let { t -> (row.at("uid").s() ?: "") to t.id }
            }
        }?.takeIf { rows -> rows.all { it.first.isNotEmpty() } }

    private suspend fun renamePlaylist(uri: String, name: String): Boolean {
        val token = withTimeoutOrNull(15_000) { session.token.filterNotNull().first() } ?: return false
        val body = buildJsonObject {
            put("deltas", JsonArray(listOf(buildJsonObject {
                put("ops", JsonArray(listOf(buildJsonObject {
                    put("kind", "UPDATE_LIST_ATTRIBUTES")
                    putJsonObject("updateListAttributes") { putJsonObject("newAttributes") { putJsonObject("values") { put("name", name) } } }
                })))
                putJsonObject("info") { putJsonObject("source") { put("client", "WEBPLAYER") } }
            })))
        }.toString()
        val (status, _) = session.pageFetch(
            "https://spclient.wg.spotify.com/playlist/v2/playlist/${uri.removePrefix("spotify:playlist:")}/changes", "POST",
            mapOf("authorization" to "Bearer ${token.accessToken}", "content-type" to "application/json;charset=UTF-8", "accept" to "application/json"),
            body,
        )
        return status in 200..299
    }

    override suspend fun artistPage(artist: Collection): app.duet.music.domain.ArtistPage? {
        if (artist.kind != SearchKind.Artists) return null
        val root = gql("queryArtistOverview", HASHES.getValue("queryArtistOverview"), buildJsonObject {
            put("uri", artist.id); put("locale", ""); put("preReleaseV2", false)
        }) ?: return null
        return parseArtist(root, artist)
    }

    /** Your Library (GraphQL `libraryV3`, Myspoti docs/api/LIBRARY.md): Liked Songs, playlists, albums, artists. */
    override suspend fun library(): List<Collection> {
        if (!session.signedIn.value) return emptyList()
        val root = gql("libraryV3", HASHES.getValue("libraryV3"), buildJsonObject {
            put("filters", JsonArray(emptyList())); put("order", "Recents"); put("textFilter", "")
            put("features", JsonArray(listOf("LIKED_SONGS").map { JsonPrimitive(it) }))
            put("limit", 200); put("offset", 0); put("flatten", false)
            put("expandedFolders", JsonArray(emptyList())); put("folderUri", kotlinx.serialization.json.JsonNull)
            put("includeFoldersWhenFlattening", true)
        }) ?: return emptyList()
        return parseLibrary(root)
    }

    /** The Web Player's home feed (GraphQL `home`, Myspoti docs/api/SEARCH-HOME.md): playlists, albums and artists per section. */
    override suspend fun homeShelves(): List<Shelf> {
        val root = gql("home", HASHES.getValue("home"), buildJsonObject {
            put("timeZone", "UTC"); put("sp_t", ""); put("facet", ""); put("sectionItemsLimit", 12)
            put("homeEndUserIntegration", "INTEGRATION_WEB_PLAYER")
            put("includeEpisodeContentRatingsV2", false); put("includeHomeChapterVideoCards", false)
        }) ?: return emptyList()
        return parseHome(root)
    }

    /** One Web Player GraphQL call, sent from inside the page. Null when Spotify refuses it. */
    private suspend fun gql(operation: String, hash: String, variables: JsonObject): JsonObject? {
        val token = withTimeoutOrNull(15_000) { session.token.filterNotNull().first() } ?: return null
        val headers = withTimeoutOrNull(10_000) { session.apiHeaders.first { it.containsKey("client-token") } } ?: return null
        val body = buildJsonObject {
            put("variables", variables)
            put("operationName", operation)
            putJsonObject("extensions") { putJsonObject("persistedQuery") { put("version", 1); put("sha256Hash", hash) } }
        }.toString()
        val (status, text) = session.pageFetch(
            "https://api-partner.spotify.com/pathfinder/v2/query", "POST",
            headers + mapOf(
                "authorization" to "Bearer ${token.accessToken}", "app-platform" to "WebPlayer",
                "content-type" to "application/json;charset=UTF-8", "accept" to "application/json",
            ),
            body,
        )
        return if (status in 200..299) runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() else null
    }

    /** Spotify songs are played by the Spotify Web Player itself, with its DRM. */
    override suspend fun resolve(track: Track): Playable {
        require(track.sourceId == ID) { "Not a Spotify track: ${track.key}" }
        if (!session.signedIn.value) throw IllegalStateException("Sign in to Spotify to play this song (Settings > Spotify).")
        return Playable.Remote("spotify:track:${track.id}", quality = "Spotify Web Player")
    }

    companion object {
        const val ID = "app.duet.source.spotify"

        /** Persisted queries of the Web Player (Myspoti docs/api/SESSION.md table, 2026-10). */
        private val HASHES = mapOf(
            "searchAlbums" to "202cb3305e31e5a0767ba7925f28bd728cf8f8b0217e6da43909056071cd70e9",
            "searchArtists" to "7bf95d754fdbe32c8b161fbbe54d1ae50974900df4dce4c8f1afcbcad153224d",
            "searchPlaylists" to "d520014e748f9ea44f7707d8df1819867ac1205e8b7f3e28f22fe5fc858921b1",
            "getAlbum" to "11c9cad9cb24c4f9e25ef9456f68621cfd6006a90f679e729c1d2f2fd70dfe16",
            "fetchPlaylist" to "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf",
            "libraryV3" to "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455",
            "fetchLibraryTracks" to "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
            "home" to "5fb7da7a03ee2776c0670856609cb85a189a3cecc36cc160b91cde79063b9cf1",
            "addToLibrary" to "896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148",
            "removeFromLibrary" to "896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148",
            "queryArtistOverview" to "9f8134ef565e78621f1e1793555bd6633c5ac144ae0f89604ed3ae3f80b3c8e6",
            // One document holds all three playlist mutations, so they share a hash (Myspoti gql-ops.json).
            "addToPlaylist" to "b907ad6b088d5bf7680fa81198996d58398c2d55dfcdf59fcc959e0be98a418a",
            "removeFromPlaylist" to "b907ad6b088d5bf7680fa81198996d58398c2d55dfcdf59fcc959e0be98a418a",
            "moveItemsInPlaylist" to "b907ad6b088d5bf7680fa81198996d58398c2d55dfcdf59fcc959e0be98a418a",
        )

        private fun JsonElement?.at(vararg keys: String): JsonElement? = keys.fold(this) { acc, k -> (acc as? JsonObject)?.get(k) }
        private fun JsonElement?.s() = (this as? JsonPrimitive)?.contentOrNull

        fun searchVariables(query: String) = buildJsonObject {
            put("searchTerm", query); put("offset", 0); put("limit", 20); put("numberOfTopResults", 20)
            put("includeAudiobooks", false); put("includeAuthors", false); put("includePreReleases", false)
            put("includeAlbumPreReleases", false); put("includeEpisodeContentRatingsV2", false)
        }

        /** A song entity (`uri`, `name`, `duration`, `artists`, `albumOfTrack`), as Myspoti's `Mappers.track`. */
        fun trackFrom(d: JsonElement?): Track? {
            val uri = d.at("uri").s() ?: return null
            if (!uri.startsWith("spotify:track:")) return null
            val sources = d.at("albumOfTrack", "coverArt", "sources") as? JsonArray
            return Track(
                sourceId = ID,
                id = uri.removePrefix("spotify:track:"),
                title = d.at("name").s() ?: return null,
                artist = (d.at("artists", "items") as? JsonArray)?.mapNotNull { it.at("profile", "name").s() }?.joinToString(", ").orEmpty(),
                album = d.at("albumOfTrack", "name").s(),
                artworkUrl = sources?.lastOrNull()?.at("url").s(),
                durationMs = (d.at("duration", "totalMilliseconds") ?: d.at("trackDuration", "totalMilliseconds")).s()?.toLongOrNull(),
            )
        }

        /**
         * `queryArtistOverview` → artist page (Myspoti docs/api/CATALOG.md): banner `headerImage`, Popular
         * `discography.topTracks`, Albums / Singles `discography.albums|singles.items[].releases.items[0]`,
         * Fans also like `relatedContent.relatedArtists`. Missing parts are left out.
         */
        fun parseArtist(root: JsonObject, artist: Collection): app.duet.music.domain.ArtistPage? {
            val a = root.at("data", "artistUnion") ?: return null
            fun img(e: JsonElement?) = (e as? JsonArray)?.lastOrNull()?.at("url").s()
            fun releases(kind: String, title: String): Shelf? {
                val items = (a.at("discography", kind, "items") as? JsonArray)?.mapNotNull { row ->
                    val r = (row.at("releases", "items") as? JsonArray)?.firstOrNull() ?: return@mapNotNull null
                    val uri = r.at("uri").s() ?: return@mapNotNull null
                    val year = r.at("date", "year").s()
                    Collection(ID, uri, SearchKind.Albums, r.at("name").s() ?: return@mapNotNull null,
                        listOfNotNull(if (kind == "singles") "Single" else "Album", year).joinToString(" · "), img(r.at("coverArt", "sources")))
                }.orEmpty()
                return if (items.isEmpty()) null else Shelf(ID, title, collections = items)
            }
            val related = (a.at("relatedContent", "relatedArtists", "items") as? JsonArray)?.mapNotNull { r ->
                val uri = r.at("uri").s() ?: return@mapNotNull null
                Collection(ID, uri, SearchKind.Artists, r.at("profile", "name").s() ?: return@mapNotNull null, "Artist", img(r.at("visuals", "avatarImage", "sources")))
            }.orEmpty()
            val listeners = a.at("stats", "monthlyListeners").s()?.toLongOrNull()
            return app.duet.music.domain.ArtistPage(
                imageUrl = img(a.at("headerImage", "data", "sources")) ?: img(a.at("visuals", "avatarImage", "sources")) ?: artist.artworkUrl,
                songs = (a.at("discography", "topTracks", "items") as? JsonArray)?.mapNotNull { trackFrom(it.at("track")) }.orEmpty(),
                shelves = listOfNotNull(
                    releases("albums", "Albums"), releases("singles", "Singles"),
                    related.takeIf { it.isNotEmpty() }?.let { Shelf(ID, "Fans also like", collections = it) },
                ),
                about = listeners?.let { compactCount(it) + " monthly listeners" },
            )
        }

        /** 67636683 → "67.6M". */
        fun compactCount(n: Long): String = when {
            n >= 1_000_000_000 -> oneDecimal(n / 1e9) + "B"
            n >= 1_000_000 -> oneDecimal(n / 1e6) + "M"
            n >= 1_000 -> oneDecimal(n / 1e3) + "K"
            else -> n.toString()
        }
        private fun oneDecimal(x: Double): String { val t = kotlin.math.round(x * 10).toLong(); return if (t % 10 == 0L) "${t / 10}" else "${t / 10}.${t % 10}" }

        const val LIKED_URI = "spotify:collection:tracks"

        /** `data.me.libraryV3.items[].item`: the Liked Songs pseudo-playlist, playlists, albums, artists. */
        fun parseLibrary(root: JsonObject): List<Collection> {
            val items = root.at("data", "me", "libraryV3", "items") as? JsonArray ?: return emptyList()
            return items.mapNotNull { row ->
                val item = row.at("item")
                val d = item.at("data") ?: return@mapNotNull null
                val uri = d.at("uri").s() ?: item.at("_uri").s() ?: return@mapNotNull null
                when {
                    uri == LIKED_URI -> Collection(ID, LIKED_URI, SearchKind.Playlists, d.at("name").s() ?: "Liked Songs",
                        "Playlist · ${d.at("count").s() ?: ""} songs".replace(" ·  songs", ""),
                        (d.at("image", "sources") as? JsonArray)?.firstOrNull()?.at("url").s())
                    uri.startsWith("spotify:playlist:") -> Collection(ID, uri, SearchKind.Playlists, d.at("name").s() ?: return@mapNotNull null,
                        "Playlist · " + (d.at("ownerV2", "data", "name").s() ?: ""),
                        ((d.at("images", "items") as? JsonArray)?.firstOrNull()?.at("sources") as? JsonArray)?.firstOrNull()?.at("url").s())
                    uri.startsWith("spotify:album:") -> Collection(ID, uri, SearchKind.Albums, d.at("name").s() ?: return@mapNotNull null,
                        "Album · " + ((d.at("artists", "items") as? JsonArray)?.mapNotNull { it.at("profile", "name").s() }?.joinToString(", ") ?: ""),
                        (d.at("coverArt", "sources") as? JsonArray)?.lastOrNull()?.at("url").s())
                    uri.startsWith("spotify:artist:") -> Collection(ID, uri, SearchKind.Artists, d.at("profile", "name").s() ?: return@mapNotNull null, "Artist",
                        (d.at("visuals", "avatarImage", "sources") as? JsonArray)?.lastOrNull()?.at("url").s())
                    else -> null
                }
            }
        }

        /** Home sections: each `sectionItems.items[].content.data` is a Playlist, Album or Artist card. */
        fun parseHome(root: JsonObject): List<Shelf> {
            val sections = root.at("data", "home", "sectionContainer", "sections", "items") as? JsonArray ?: return emptyList()
            return sections.mapNotNull { sec ->
                val title = sec.at("data", "title", "transformedLabel").s() ?: return@mapNotNull null
                val cards = (sec.at("sectionItems", "items") as? JsonArray)?.mapNotNull { item ->
                    val d = item.at("content", "data") ?: return@mapNotNull null
                    val uri = d.at("uri").s() ?: return@mapNotNull null
                    when {
                        uri.startsWith("spotify:playlist:") -> Collection(
                            ID, uri, SearchKind.Playlists, d.at("name").s() ?: return@mapNotNull null,
                            d.at("ownerV2", "data", "name").s() ?: "Playlist",
                            ((d.at("images", "items") as? JsonArray)?.firstOrNull()?.at("sources") as? JsonArray)?.firstOrNull()?.at("url").s(),
                        )
                        uri.startsWith("spotify:album:") -> Collection(
                            ID, uri, SearchKind.Albums, d.at("name").s() ?: return@mapNotNull null,
                            (d.at("artists", "items") as? JsonArray)?.mapNotNull { it.at("profile", "name").s() }?.joinToString(", ") ?: "Album",
                            (d.at("coverArt", "sources") as? JsonArray)?.lastOrNull()?.at("url").s(),
                        )
                        uri.startsWith("spotify:artist:") -> Collection(
                            ID, uri, SearchKind.Artists, d.at("profile", "name").s() ?: return@mapNotNull null, "Artist",
                            (d.at("visuals", "avatarImage", "sources") as? JsonArray)?.lastOrNull()?.at("url").s(),
                        )
                        else -> null
                    }
                }.orEmpty()
                if (cards.isEmpty()) null else Shelf(ID, title, collections = cards)
            }
        }

        /** Albums (`albumsV2`), artists (`artists`) or playlists (`playlists`) of a search answer. */
        fun parseCollections(root: JsonObject, path: String, kind: SearchKind): List<Collection> {
            val items = root.at("data", "searchV2", path, "items") as? JsonArray ?: return emptyList()
            return items.mapNotNull { row ->
                val d = row.at("data") ?: return@mapNotNull null
                val uri = d.at("uri").s() ?: return@mapNotNull null
                when (kind) {
                    SearchKind.Albums -> Collection(
                        ID, uri, kind, d.at("name").s() ?: return@mapNotNull null,
                        listOf("Album", (d.at("artists", "items") as? JsonArray)?.mapNotNull { it.at("profile", "name").s() }?.joinToString(", ")).filterNot { it.isNullOrBlank() }.joinToString(" · "),
                        (d.at("coverArt", "sources") as? JsonArray)?.lastOrNull()?.at("url").s(),
                    )
                    SearchKind.Artists -> Collection(
                        ID, uri, kind, d.at("profile", "name").s() ?: return@mapNotNull null, "Artist",
                        (d.at("visuals", "avatarImage", "sources") as? JsonArray)?.lastOrNull()?.at("url").s(),
                    )
                    SearchKind.Playlists -> Collection(
                        ID, uri, kind, d.at("name").s() ?: return@mapNotNull null,
                        listOfNotNull("Playlist", d.at("ownerV2", "data", "name").s()).joinToString(" · "),
                        ((d.at("images", "items") as? JsonArray)?.firstOrNull()?.at("sources") as? JsonArray)?.firstOrNull()?.at("url").s(),
                    )
                    SearchKind.Songs, SearchKind.Videos -> null
                }
            }
        }

        /** Persisted query of the Web Player's Songs search tab (Myspoti docs/api/SEARCH-HOME.md, 2026-10-15). */
        private const val SEARCH_TRACKS_HASH = "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751"

        fun searchBody(query: String): String = buildJsonObject {
            putJsonObject("variables") {
                put("searchTerm", query); put("offset", 0); put("limit", 20); put("numberOfTopResults", 20)
                put("includeAudiobooks", false); put("includeAuthors", false); put("includePreReleases", false)
                put("includeAlbumPreReleases", false); put("includeEpisodeContentRatingsV2", false)
            }
            put("operationName", "searchTracks")
            putJsonObject("extensions") { putJsonObject("persistedQuery") { put("version", 1); put("sha256Hash", SEARCH_TRACKS_HASH) } }
        }.toString()

        /** Tracks from a GraphQL `searchTracks` answer (`data.searchV2.tracksV2.items[].item.data`), as Myspoti's `Mappers.track`. */
        fun parseGqlTracks(root: JsonObject): List<Track> {
            fun JsonElement?.at(vararg keys: String): JsonElement? = keys.fold(this) { acc, k -> (acc as? JsonObject)?.get(k) }
            fun JsonElement?.s() = (this as? JsonPrimitive)?.contentOrNull
            val items = root.at("data", "searchV2", "tracksV2", "items") as? JsonArray ?: return emptyList()
            return items.mapNotNull { row ->
                val d = row.at("item", "data") ?: return@mapNotNull null
                val uri = d.at("uri").s() ?: return@mapNotNull null
                if (!uri.startsWith("spotify:track:")) return@mapNotNull null
                if ((d.at("playability", "playable") as? JsonPrimitive)?.contentOrNull == "false") return@mapNotNull null
                val sources = d.at("albumOfTrack", "coverArt", "sources") as? JsonArray
                Track(
                    sourceId = ID,
                    id = uri.removePrefix("spotify:track:"),
                    title = d.at("name").s() ?: return@mapNotNull null,
                    artist = (d.at("artists", "items") as? JsonArray)?.mapNotNull { it.at("profile", "name").s() }?.joinToString(", ").orEmpty(),
                    album = d.at("albumOfTrack", "name").s(),
                    // Sources come small to large; take the largest.
                    artworkUrl = sources?.lastOrNull()?.at("url").s(),
                    durationMs = d.at("duration", "totalMilliseconds").s()?.toLongOrNull(),
                )
            }
        }

        /** Tracks from a Web API search answer (`tracks.items`). */
        fun parseTracks(root: kotlinx.serialization.json.JsonObject): List<Track> {
            val items = (root["tracks"] as? kotlinx.serialization.json.JsonObject)?.get("items") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            return items.mapNotNull { e ->
                val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                fun str(x: kotlinx.serialization.json.JsonElement?) = (x as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                val id = str(o["id"]) ?: return@mapNotNull null
                val album = o["album"] as? kotlinx.serialization.json.JsonObject
                val images = album?.get("images") as? kotlinx.serialization.json.JsonArray
                Track(
                    sourceId = ID,
                    id = id,
                    title = str(o["name"]) ?: return@mapNotNull null,
                    artist = (o["artists"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { a -> str((a as? kotlinx.serialization.json.JsonObject)?.get("name")) }?.joinToString(", ").orEmpty(),
                    album = str(album?.get("name")),
                    artworkUrl = images?.firstOrNull()?.let { str((it as? kotlinx.serialization.json.JsonObject)?.get("url")) },
                    durationMs = str(o["duration_ms"])?.toLongOrNull(),
                )
            }
        }

        private const val STATUS_OUT = "Not signed in"
    }
}
