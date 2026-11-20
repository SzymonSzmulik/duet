package app.duet.music.lyrics

import app.duet.music.domain.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/** One line of lyrics; [timeMs] is null for plain (unsynced) lyrics. */
data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val provider: String)

/**
 * Lyrics from LRCLIB (lrclib.net, an open API meant for third-party players), as Myspoti's
 * `LyricsApi` (GPL-3.0) and SpatialFlow's `LyricsFetchManager` (Apache-2.0) both use it:
 * `/api/get` by title, artist, album and length first, then `/api/search`, keeping only results whose
 * artist, title and length (±3 s) match the track, synced lyrics preferred. Results are cached per track.
 */
class LyricsService(private val http: HttpClient) {
    private val cache = LinkedHashMap<String, Lyrics?>()

    suspend fun lyricsFor(track: Track): Lyrics? {
        cache[track.key]?.let { return it }
        val title = cleanTitle(track.title)
        val artist = firstArtist(track.artist)
        val seconds = ((track.durationMs ?: 0) / 1000).toInt()
        val found = runCatching {
            get(title, artist, track.album, seconds)
                ?: search(title, artist, seconds, withArtist = true)
                ?: search(title, artist, seconds, withArtist = false)
        }.getOrNull()
        if (found != null) {
            cache[track.key] = found
            if (cache.size > 100) cache.remove(cache.keys.first())
        }
        return found
    }

    private suspend fun get(title: String, artist: String, album: String?, seconds: Int): Lyrics? {
        val r = http.get("$API/get") {
            header("User-Agent", USER_AGENT)
            parameter("track_name", title)
            parameter("artist_name", artist)
            if (!album.isNullOrBlank()) parameter("album_name", album)
            if (seconds > 0) parameter("duration", seconds)
        }
        if (!r.status.isSuccess()) return null
        val o = json.parseToJsonElement(r.bodyAsText()) as? JsonObject ?: return null
        return o.takeIf { plausible(it, title, artist, seconds) }?.let(::parse)
    }

    private suspend fun search(title: String, artist: String, seconds: Int, withArtist: Boolean): Lyrics? {
        val r = http.get("$API/search") {
            header("User-Agent", USER_AGENT)
            parameter("track_name", title)
            if (withArtist) parameter("artist_name", artist)
        }
        if (!r.status.isSuccess()) return null
        val arr = json.parseToJsonElement(r.bodyAsText()) as? JsonArray ?: return null
        // Of the plausible results, prefer synced lyrics, then the closest length (Myspoti's rule).
        return arr.filterIsInstance<JsonObject>()
            .filter { plausible(it, title, artist, seconds) }
            .minByOrNull { o ->
                val diff = if (seconds > 0) abs((o.int("duration") ?: 0) - seconds) else 0
                diff + if (o.str("syncedLyrics").isNullOrBlank()) 100_000 else 0
            }?.let(::parse)
    }

    private fun plausible(o: JsonObject, title: String, artist: String, seconds: Int): Boolean {
        val candArtist = norm(o.str("artistName").orEmpty())
        val wantArtist = norm(artist)
        if (candArtist.isEmpty() || !(candArtist.contains(wantArtist) || wantArtist.contains(candArtist))) return false
        val candTitle = norm(cleanTitle(o.str("trackName").orEmpty()))
        val wantTitle = norm(title)
        if (candTitle.isEmpty() || !(candTitle.contains(wantTitle) || wantTitle.contains(candTitle))) return false
        val dur = o.int("duration") ?: 0
        return seconds <= 0 || dur <= 0 || abs(dur - seconds) <= 3
    }

    private fun parse(o: JsonObject): Lyrics? {
        o.str("syncedLyrics")?.takeIf { it.isNotBlank() }?.let { lrc ->
            parseLrc(lrc).takeIf { it.isNotEmpty() }?.let { return Lyrics(it, synced = true, provider = "LRCLIB") }
        }
        val plain = o.str("plainLyrics")?.takeIf { it.isNotBlank() } ?: return null
        return Lyrics(plain.lines().map { LyricLine(null, it.trim()) }, synced = false, provider = "LRCLIB")
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonElement)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    private fun JsonObject.int(k: String) = (this[k] as? JsonElement)?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }

    companion object {
        private const val API = "https://lrclib.net/api"
        /** LRCLIB asks clients to name themselves. */
        private const val USER_AGENT = "Duet music player (personal use)"
        private val json = Json { ignoreUnknownKeys = true }

        // LRC: "[mm:ss.xx] text"; a line may carry several times (Myspoti's parseLrc).
        private val LRC_TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        fun parseLrc(lrc: String): List<LyricLine> {
            val out = ArrayList<LyricLine>()
            for (raw in lrc.lines()) {
                val tags = LRC_TAG.findAll(raw).toList()
                if (tags.isEmpty()) continue
                val text = raw.substring(tags.last().range.last + 1).trim().ifEmpty { "♪" }
                for (m in tags) {
                    val frac = m.groupValues[3]
                    val ms = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    out += LyricLine(m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1_000 + ms, text)
                }
            }
            // Collapse runs of empty (instrumental) lines into one ♪.
            val sorted = out.sortedBy { it.timeMs }
            return sorted.filterIndexed { i, l -> !(l.text == "♪" && i > 0 && sorted[i - 1].text == "♪") }
        }

        /** Index of the line being sung at [positionMs], or -1 before the first line. */
        fun currentLine(lines: List<LyricLine>, positionMs: Long): Int =
            lines.indexOfLast { (it.timeMs ?: Long.MAX_VALUE) <= positionMs }

        /** Drops "(Official Video)", "[Remastered]", "- 2011 Remaster", "feat. …" from a title. */
        fun cleanTitle(t: String): String = t
            .replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "")
            .replace(Regex("""\s+-\s+.*(remaster|version|edit|live|mix).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(feat\.|ft\.).*$""", RegexOption.IGNORE_CASE), "")
            .trim()

        fun firstArtist(a: String): String = a.split(Regex(""",|&| x | and | feat\. """)).first().trim()

        private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    }
}
