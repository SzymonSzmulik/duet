package app.duet.music.sources.radio

import app.duet.music.domain.Track
import app.duet.music.extension.Capability
import app.duet.music.extension.ExtensionManifest
import app.duet.music.extension.ExtensionPermissions
import app.duet.music.extension.Playable
import app.duet.music.extension.SourceProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Live internet radio from the free, community-run radio-browser.info directory (tens of
 * thousands of stations). A station is a [Track] with no length; playing it streams the
 * station's own URL. HLS stations are left out (the player only opens plain streams).
 */
/** [codecs]: the stream formats the device's player can play (radio-browser's names, e.g. "MP3"); null means all of them. */
class RadioProvider(private val http: HttpClient, private val codecs: Set<String>? = null) : SourceProvider {
    override val manifest = ExtensionManifest(
        id = ID,
        name = "Radio",
        version = "0.1.0",
        capabilities = setOf(Capability.LiveRadio),
        permissions = ExtensionPermissions(networkHosts = listOf("*.api.radio-browser.info", "each station's own stream server")),
    )
    override val shortName = "Radio"
    override val color = 0xFFE8B04A
    override val accountStatus = "No account needed"

    /** Stations whose names match [query], most listened first. */
    override suspend fun search(query: String): List<Track> =
        stations("stations/search", "name" to query.trim(), "limit" to "60", "hidebroken" to "true", "order" to "clickcount", "reverse" to "true")

    /**
     * Stations matching any mix of a [tag] (genre) and a [country] (ISO 3166-1 code, e.g. "GB"),
     * most listened first. With neither, the most listened stations worldwide.
     */
    suspend fun find(tag: String? = null, country: String? = null): List<Track> = stations(
        "stations/search", *listOfNotNull(tag?.let { "tag" to it }, country?.let { "countrycode" to it }).toTypedArray(),
        "tagExact" to "true", "limit" to "60", "hidebroken" to "true", "order" to "clickcount", "reverse" to "true",
    )

    /** Countries that have stations, most stations first. */
    suspend fun countries(): List<Country> = parseCountries(get("countries", "order" to "stationcount", "reverse" to "true", "hidebroken" to "true"))

    /** Asks the directory for the stream (this also counts a listen, as radio-browser asks clients to). */
    override suspend fun resolve(track: Track): Playable {
        val body = get("url/${track.id.encodeURLPathPart()}")
        val url = (Json.parseToJsonElement(body) as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
            ?: error("This station is off the air")
        return Playable.Stream(url = url, headers = mapOf("User-Agent" to USER_AGENT), quality = "Live")
    }

    private suspend fun stations(path: String, vararg params: Pair<String, String>) = parseStations(get(path, *params), codecs)

    /** Tries the directory's mirrors in turn (any one can be down). */
    private suspend fun get(path: String, vararg params: Pair<String, String>): String {
        var last: Throwable? = null
        for (host in MIRRORS) {
            try {
                val r = http.get("https://$host/json/$path") {
                    header("User-Agent", USER_AGENT)
                    params.forEach { (k, v) -> parameter(k, v) }
                }
                if (r.status.isSuccess()) return r.bodyAsText()
                last = IllegalStateException("Radio directory answered ${r.status.value}")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                last = e
            }
        }
        throw last ?: IllegalStateException("Radio directory unreachable")
    }

    companion object {
        const val ID = "app.duet.source.radio"
        private val MIRRORS = listOf("de1.api.radio-browser.info", "nl1.api.radio-browser.info", "at1.api.radio-browser.info")
        // Generic client name: no user or device details (radio-browser asks for a descriptive agent).
        private const val USER_AGENT = "Duet/0.15 (Android music player)"

        /** Genres offered as chips on the Radio page (radio-browser tags). */
        val GENRES = listOf("pop", "rock", "jazz", "classical", "news", "chillout", "hiphop", "dance", "electronic", "country")

        /** Countries from radio-browser's countries list; drops ones without a code or with few stations. */
        fun parseCountries(body: String): List<Country> {
            val arr = runCatching { Json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
            return arr.filterIsInstance<JsonObject>().mapNotNull { o ->
                val code = o["iso_3166_1"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase().orEmpty()
                val name = o["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val count = o["stationcount"]?.jsonPrimitive?.intOrNull ?: 0
                if (code.length != 2 || name.isEmpty() || count < 20) null else Country(code, name, count)
            }.distinctBy { it.code }.sortedByDescending { it.stations }
        }

        /** Stations from a radio-browser JSON array; skips HLS and ones without a stream. */
        fun parseStations(body: String, codecs: Set<String>? = null): List<Track> {
            val arr = runCatching { Json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
            return arr.filterIsInstance<JsonObject>().mapNotNull { o ->
                fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val id = s("stationuuid")
                val name = s("name")
                if (id.isEmpty() || name.isEmpty() || s("url_resolved").isEmpty()) return@mapNotNull null
                if (o["hls"]?.jsonPrimitive?.intOrNull == 1) return@mapNotNull null
                if (codecs != null && s("codec").uppercase() !in codecs) return@mapNotNull null
                val tags = s("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(2)
                val bitrate = o["bitrate"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.let { "$it kbps" }
                Track(
                    sourceId = ID, id = id, title = name,
                    artist = (listOf(s("country")) + tags + listOfNotNull(bitrate)).filter { it.isNotEmpty() }.joinToString(" · "),
                    artworkUrl = s("favicon").takeIf { it.startsWith("https://") },
                    durationMs = null,
                )
            }.distinctBy { it.title.lowercase() }
        }
    }
}

/** A country in the radio directory: ISO code (e.g. "GB"), name and how many stations it has. */
data class Country(val code: String, val name: String, val stations: Int)
