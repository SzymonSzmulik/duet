package app.duet.music.player

import app.duet.music.domain.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Scrobbling to ListenBrainz (listenbrainz.org, open, no app key: the user's own token). "Playing now"
 * when a song starts, and one listen once it has played half its length or four minutes, the usual rule.
 * Radio is never sent. Off while the token is empty.
 */
class ListenBrainz(
    private val http: HttpClient,
    scope: CoroutineScope,
    state: StateFlow<PlayerState>,
    private val token: StateFlow<String>,
    private val skip: (Track) -> Boolean,
    private val serviceName: (Track) -> String?,
) {
    private var itemId: Long? = null
    private var sentNow = false
    private var sentListen = false
    private var startedAt = 0L

    init {
        scope.launch {
            state.collect { s ->
                val item = s.current ?: return@collect
                if (item.id != itemId) { itemId = item.id; sentNow = false; sentListen = false; startedAt = app.duet.music.ui.epochSeconds() }
                val t = item.track
                if (token.value.isBlank() || skip(t) || s.status != PlaybackStatus.Playing) return@collect
                if (!sentNow) { sentNow = true; scope.launch { send("playing_now", t, null) } }
                if (!sentListen && shouldScrobble(s.positionMs, s.durationMs ?: t.durationMs)) {
                    sentListen = true
                    scope.launch { send("single", t, startedAt) }
                }
            }
        }
    }

    private suspend fun send(type: String, t: Track, listenedAt: Long?) {
        runCatching {
            http.post("https://api.listenbrainz.org/1/submit-listens") {
                header("Authorization", "Token ${token.value}")
                contentType(ContentType.Application.Json)
                setBody(body(type, t, listenedAt, serviceName(t)).toString())
            }
        }
    }

    companion object {
        /** Half the song or 4 minutes, whichever comes first; songs under 30 s don't count. */
        fun shouldScrobble(positionMs: Long, durationMs: Long?): Boolean {
            val d = durationMs ?: return positionMs >= 240_000
            if (d < 30_000) return false
            return positionMs >= minOf(d / 2, 240_000)
        }

        fun body(type: String, t: Track, listenedAt: Long?, service: String?): JsonObject = buildJsonObject {
            put("listen_type", type)
            put("payload", buildJsonArray {
                add(buildJsonObject {
                    listenedAt?.let { put("listened_at", it) }
                    putJsonObject("track_metadata") {
                        put("artist_name", t.artist)
                        put("track_name", t.title)
                        t.album?.let { put("release_name", it) }
                        putJsonObject("additional_info") {
                            put("media_player", "Duet")
                            put("submission_client", "Duet")
                            service?.let { put("music_service_name", it) }
                            t.durationMs?.let { put("duration_ms", it) }
                        }
                    }
                })
            })
        }
    }
}
