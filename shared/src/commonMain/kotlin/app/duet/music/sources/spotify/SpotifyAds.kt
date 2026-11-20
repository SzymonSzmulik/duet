package app.duet.music.sources.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Keeps Spotify's audio ads (free accounts) out of the Web Player, following Myspoti's method
 * (docs/api/ADS.md, GPL-3.0; re-implemented here):
 * - the ad audio hosts are refused ([isAdHost]), so an ad at the start of a play moves on by itself;
 * - every playback state machine the page receives is rewritten ([withoutAds]): a song whose natural
 *   end (`advance`) leads to an ad goes where its `skip_next` goes instead;
 * - when `skip_next` leads to the ad too, [Rewrite.adAhead] tells the player to start the next song
 *   itself just before this one ends.
 */
object SpotifyAds {
    /** What [withoutAds] did to one state machine reply. */
    data class Rewrite(
        val body: String,
        /** The current song ends into an ad that could not be routed around. */
        val adAhead: Boolean,
        /** The current state is an ad. */
        val adNow: Boolean,
        /** The reply carried a state machine (the two flags above are about it). */
        val hasMachine: Boolean = true,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Ad audio hosts (uBlock Origin uAssets and Myspoti's list, GPL-3.0). */
    private val AD_HOSTS = listOf(
        "adstudio-assets.scdn.co", "2mdn.net", "adxcel.com", "amillionads.com", "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com", "imasdk.googleapis.com",
    )

    fun isAdHost(url: String): Boolean {
        val rest = url.substringAfter("://")
        val host = rest.substringBefore('/').substringBefore('?').substringAfterLast('@').substringBefore(':').lowercase()
        val path = rest.drop(rest.substringBefore('/').length)
        return AD_HOSTS.any { host == it || host.endsWith(".$it") } || (host.endsWith("scdn.co") && path.startsWith("/mp3-ad/"))
    }

    /** Rewrites a reply that carries a `state_machine` (track-playback state answers, `replace_state` pushes); anything else is returned as is. */
    fun withoutAds(body: String): Rewrite {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return Rewrite(body, false, false, hasMachine = false)
        val machine = root["state_machine"] as? JsonObject ?: return Rewrite(body, false, false, hasMachine = false)
        val tracks = machine["tracks"] as? JsonArray ?: return Rewrite(body, false, false, hasMachine = false)
        val states = (machine["states"] as? JsonArray)?.map { it as? JsonObject } ?: return Rewrite(body, false, false, hasMachine = false)

        fun isAd(stateIndex: Int?): Boolean {
            val s = states.getOrNull(stateIndex ?: return false) ?: return false
            val t = tracks.getOrNull(s["track"]?.jsonPrimitive?.intOrNull ?: return false) as? JsonObject ?: return false
            val type = (t["content_type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val uri = ((t["metadata"] as? JsonObject)?.get("uri") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            return type == "AD" || uri.startsWith("spotify:ad:")
        }
        fun target(t: JsonElement?): Int? = ((t as? JsonObject)?.get("state_index") as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull

        var changed = false
        val blocked = HashSet<Int>()
        val newStates = states.mapIndexed { i, s ->
            val transitions = s?.get("transitions") as? JsonObject ?: return@mapIndexed s ?: JsonNull
            val advance = transitions["advance"]
            if (!isAd(target(advance))) return@mapIndexed s
            val skip = transitions["skip_next"]
            if (skip != null && skip !is JsonNull && !isAd(target(skip))) {
                changed = true
                JsonObject(s + ("transitions" to JsonObject(transitions + ("advance" to skip))))
            } else { blocked += i; s }
        }
        val current = ((root["updated_state_ref"] ?: root["state_ref"]) as? JsonObject)?.get("state_index")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull }
        val out = if (changed) JsonObject(root + ("state_machine" to JsonObject(machine + ("states" to JsonArray(newStates))))).toString() else body
        return Rewrite(out, adAhead = current != null && current in blocked, adNow = isAd(current))
    }
}
