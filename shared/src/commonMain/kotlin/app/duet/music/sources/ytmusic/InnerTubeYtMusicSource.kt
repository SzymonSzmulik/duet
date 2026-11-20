package app.duet.music.sources.ytmusic

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.sha1
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Finds a playable audio URL for a YouTube video id. Platform code provides it (NewPipe on JVM). */
fun interface YtStreamResolver {
    suspend fun resolve(videoId: String, quality: StreamQuality): YtStream
}

/**
 * YouTube Music, logged out, no API key.
 * - search: InnerTube `music.youtube.com/youtubei/v1/search` as the WEB_REMIX (music web app) client.
 * - streams: delegated to [streams]. Plain InnerTube player URLs stop after ~1-2 MB without a
 *   PoToken (docs/DECISIONS.md D15), so stream resolving is NewPipe Extractor's job.
 */
class InnerTubeYtMusicSource(
    private val http: HttpClient,
    private val streams: YtStreamResolver,
    private val language: String = "en",
    private val country: String = "US",
    /** The music.youtube.com cookie header while signed in (requests are then made as the user). */
    private val cookie: () -> String? = { null },
) : YtMusicSource {

    override suspend fun search(query: String): List<YtSong> =
        YtMusicParser.parseSongs(post("search", query = query, params = YtMusicParser.FILTERS.getValue(app.duet.music.domain.SearchKind.Songs)))

    override suspend fun searchVideos(query: String): List<YtSong> =
        YtMusicParser.parseSongs(post("search", query = query, params = YtMusicParser.FILTERS.getValue(app.duet.music.domain.SearchKind.Videos)))

    override suspend fun searchCollections(query: String, kind: app.duet.music.domain.SearchKind): List<YtCollection> =
        YtMusicParser.parseCollections(post("search", query = query, params = YtMusicParser.FILTERS.getValue(kind)))

    override suspend fun radio(videoId: String): List<YtSong> =
        YtMusicParser.parseRadio(post("next", videoId = videoId, playlistId = "RDAMVM$videoId")).filter { it.videoId != videoId }

    override suspend fun libraryPlaylists(): List<YtCollection> {
        if (cookie() == null) return emptyList()
        return YtMusicParser.parseCards(post("browse", browseId = "FEmusic_liked_playlists"))
    }

    override suspend fun homeShelves(): List<YtShelf> = YtMusicParser.parseHome(post("browse", browseId = "FEmusic_home"))

    override suspend fun browseSongs(browseId: String): List<YtSong> =
        YtMusicParser.parseSongs(post("browse", browseId = browseId))

    override suspend fun suggestions(query: String): List<String> =
        YtMusicParser.parseSuggestions(post("music/get_search_suggestions", input = query))

    override suspend fun like(videoId: String, liked: Boolean): Boolean {
        if (cookie() == null) return false
        post(if (liked) "like/like" else "like/removelike", likeVideoId = videoId)
        return true
    }

    /** A STATUS_SUCCEEDED answer means YouTube took it (SpatialFlow InnerTubeClient.addToPlaylist). */
    override suspend fun editPlaylist(playlistId: String, edit: YtPlaylistEdit): Boolean {
        if (cookie() == null) return false
        val action = buildJsonObject {
            when (edit) {
                is YtPlaylistEdit.Add -> { put("action", "ACTION_ADD_VIDEO"); put("addedVideoId", edit.videoId) }
                is YtPlaylistEdit.Remove -> { put("action", "ACTION_REMOVE_VIDEO"); put("setVideoId", edit.setVideoId); put("removedVideoId", edit.videoId) }
                is YtPlaylistEdit.MoveBefore -> { put("action", "ACTION_MOVE_VIDEO_BEFORE"); put("setVideoId", edit.setVideoId); put("movedSetVideoIdSuccessor", edit.beforeSetVideoId) }
                is YtPlaylistEdit.Rename -> { put("action", "ACTION_SET_PLAYLIST_NAME"); put("playlistName", edit.name) }
            }
        }
        val answer = post("browse/edit_playlist", playlistId = playlistId, actions = kotlinx.serialization.json.JsonArray(listOf(action)))
        return (answer["status"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "STATUS_SUCCEEDED"
    }

    /**
     * `playlist/delete` works only on your own playlists (ytmusicapi delete_playlist); a saved one answers with
     * an error, so it is then "unliked" (`like/removelike` with the playlist as target), which drops it from the library.
     */
    override suspend fun removePlaylist(playlistId: String): Boolean {
        if (cookie() == null) return false
        val deleted = try {
            post("playlist/delete", playlistId = playlistId)["error"] == null
        } catch (e: YtMusicException) {
            false
        }
        if (deleted) return true
        post("like/removelike", likePlaylistId = playlistId)
        return true
    }

    override suspend fun artist(browseId: String): YtArtist = YtMusicParser.parseArtist(post("browse", browseId = browseId))

    /** One logged-out InnerTube call as the music web app (WEB_REMIX). */
    private suspend fun post(
        endpoint: String, query: String? = null, params: String? = null, browseId: String? = null,
        videoId: String? = null, playlistId: String? = null, likeVideoId: String? = null, input: String? = null,
        actions: kotlinx.serialization.json.JsonArray? = null, likePlaylistId: String? = null,
    ): kotlinx.serialization.json.JsonObject {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", language)
                    put("gl", country)
                }
            }
            query?.let { put("query", it) }
            params?.let { put("params", it) }
            browseId?.let { put("browseId", it) }
            videoId?.let { put("videoId", it) }
            playlistId?.let { put("playlistId", it) }
            input?.let { put("input", it) }
            likeVideoId?.let { id -> putJsonObject("target") { put("videoId", id) } }
            likePlaylistId?.let { id -> putJsonObject("target") { put("playlistId", id) } }
            actions?.let { put("actions", it) }
        }
        val response = try {
            http.post("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false") {
                contentType(ContentType.Application.Json)
                header("User-Agent", USER_AGENT)
                header("Origin", ORIGIN)
                header("X-Origin", ORIGIN)
                header("Referer", "$ORIGIN/")
                header("X-YouTube-Client-Name", "67")
                header("X-YouTube-Client-Version", CLIENT_VERSION)
                // Signed in: the browser's cookies and a SAPISIDHASH (SpatialFlow InnerTubeClient.buildHeaders).
                cookie()?.let { c ->
                    header("Cookie", c)
                    sapisidHash(c, ORIGIN)?.let { header("Authorization", it) }
                    header("X-Goog-AuthUser", "0")
                }
                setBody(body.toString())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw YtMusicException("Network error: ${e.message ?: e::class.simpleName}")
        }
        if (!response.status.isSuccess()) throw YtMusicException("YouTube answered HTTP ${response.status.value}")
        val text = response.bodyAsText()
        return withContext(Dispatchers.Default) {
            runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { throw YtMusicException("Answer is not JSON") }
        }
    }

    override suspend fun streamUrl(videoId: String, quality: StreamQuality): YtStream = streams.resolve(videoId, quality)

    /**
     * InnerTube `account/account_menu` with the browser's cookies and a SAPISIDHASH header, as in
     * SpatialFlow's `InnerTubeClient.buildHeaders` and `InnerTubeParser.parseAccountProfile`
     * (Apache-2.0, docs/THIRD_PARTY.md).
     */
    override suspend fun accountName(cookie: String): String? = runCatching {
        val response = http.post("$ORIGIN/youtubei/v1/account/account_menu?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header("User-Agent", USER_AGENT)
            header("Origin", ORIGIN)
            header("X-Origin", ORIGIN)
            header("Referer", "$ORIGIN/")
            header("Cookie", cookie)
            sapisidHash(cookie, ORIGIN)?.let { header("Authorization", it) }
            setBody(buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", CLIENT_VERSION)
                        put("hl", language)
                        put("gl", country)
                    }
                }
            }.toString())
        }
        if (!response.status.isSuccess()) return null
        YtMusicParser.parseAccountName(json.parseToJsonElement(response.bodyAsText()).jsonObject)
    }.getOrNull()

    companion object {
        /** `SAPISIDHASH <ts>_<sha1("<ts> <SAPISID> <origin>")>`, or null without a SAPISID cookie. */
        fun sapisidHash(cookie: String, origin: String, nowSeconds: Long = getTimeMillis() / 1000): String? {
            val jar = cookie.split(';').mapNotNull { c -> c.trim().split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            val sapisid = jar["SAPISID"] ?: jar["__Secure-3PAPISID"] ?: return null
            val hash = sha1("$nowSeconds $sapisid $origin".encodeToByteArray()).joinToString("") { b -> (b.toInt() and 0xff).toString(16).padStart(2, '0') }
            return "SAPISIDHASH ${nowSeconds}_$hash"
        }

        private const val ORIGIN = "https://music.youtube.com"
        /** The music web app's version; bump it when YouTube starts rejecting it. */
        private const val CLIENT_VERSION = "1.20251103.01.00"
        private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
