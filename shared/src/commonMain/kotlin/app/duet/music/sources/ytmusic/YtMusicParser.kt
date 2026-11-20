package app.duet.music.sources.ytmusic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Parses InnerTube responses. Search parsing is a port of reference/web-prototype/server.js
 * (`collect` + `parseSongs`): walk the whole JSON for `musicResponsiveListItemRenderer`, so small
 * layout changes on YouTube's side do not break it.
 */
object YtMusicParser {

    fun parseSongs(root: JsonElement): List<YtSong> {
        val seen = HashSet<String>()
        return collect(root, "musicResponsiveListItemRenderer").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val cols = obj["flexColumns"] as? JsonArray ?: return@mapNotNull null
            val title = columnRuns(cols, 0).firstOrNull() ?: return@mapNotNull null
            val videoId = obj.path("playlistItemData", "videoId")?.str()
                ?: collect(obj, "videoId").firstNotNullOfOrNull { it.str() }
                ?: return@mapNotNull null
            if (!seen.add(videoId)) return@mapNotNull null

            // Subtitle: "[Song •] Artist, Artist & Artist • Album • 3:42". Runs inside a part
            // ("Daft Punk", ", ", "Pharrell Williams") are joined; parts are split on "•".
            val parts = splitParts(columnRuns(cols, 1))
            val durationPart = parts.lastOrNull { DURATION.matches(it) }
            val body = parts.filter { it != durationPart }.let { if (it.firstOrNull() in TYPE_LABELS) it.drop(1) else it }
            // Last thumbnail is the largest.
            val thumbs = obj.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails") as? JsonArray
            YtSong(
                videoId = videoId,
                title = title,
                artist = body.getOrNull(0).orEmpty(),
                album = body.getOrNull(1),
                thumbnailUrl = thumbs?.lastOrNull()?.jsonObject?.get("url")?.str()?.let(::sharperThumbnail),
                // Album and playlist rows keep the length in a fixed column instead of the subtitle.
                durationSeconds = (durationPart ?: fixedText(obj)?.takeIf { DURATION.matches(it) })?.let(::parseDuration),
                setVideoId = obj.path("playlistItemData", "playlistSetVideoId")?.str(),
            )
        }.toList()
    }

    /** InnerTube search `params` per kind (the same values SpatialFlow's `SearchFilter` uses). */
    val FILTERS = mapOf(
        app.duet.music.domain.SearchKind.Songs to "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D",
        // Videos: everything uploaded to YouTube (remixes, covers, live), not only official songs.
        app.duet.music.domain.SearchKind.Videos to "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D",
        app.duet.music.domain.SearchKind.Albums to "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D",
        app.duet.music.domain.SearchKind.Artists to "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D",
        app.duet.music.domain.SearchKind.Playlists to "EgeKAQQoAEABagwQDhAKEAMQBRAJEAQ%3D",
    )

    /**
     * The home feed (`browse FEmusic_home`): each `musicCarouselShelfRenderer` is a shelf. Its rows are
     * songs (`musicResponsiveListItemRenderer`, "Quick picks") or cards (`musicTwoRowItemRenderer`)
     * that are either a song (watchEndpoint) or a browse page (browseEndpoint).
     */
    fun parseHome(root: JsonElement): List<YtShelf> =
        collect(root, "musicCarouselShelfRenderer").mapNotNull { shelf ->
            val title = (shelf.path("header", "musicCarouselShelfBasicHeaderRenderer", "title", "runs") as? JsonArray)
                ?.joinToString("") { it.jsonObject["text"]?.str().orEmpty() }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val contents = shelf.path("contents") ?: return@mapNotNull null
            val songs = parseSongs(contents).toMutableList()
            val pages = ArrayList<YtCollection>()
            collect(contents, "musicTwoRowItemRenderer").forEach { card ->
                val name = (card.path("title", "runs") as? JsonArray)?.firstOrNull()?.jsonObject?.get("text")?.str() ?: return@forEach
                val sub = (card.path("subtitle", "runs") as? JsonArray)?.joinToString("") { it.jsonObject["text"]?.str().orEmpty() }.orEmpty()
                val thumb = (card.path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails") as? JsonArray)
                    ?.lastOrNull()?.jsonObject?.get("url")?.str()?.let(::sharperThumbnail)
                val videoId = card.path("navigationEndpoint", "watchEndpoint", "videoId")?.str()
                val browseId = card.path("navigationEndpoint", "browseEndpoint", "browseId")?.str()
                when {
                    videoId != null -> songs += YtSong(videoId, name, sub.substringAfter(" • ", sub).substringBefore(" • "), null, thumb, null)
                    browseId != null -> pages += YtCollection(browseId, name, sub.replace(" • ", " · "), thumb)
                }
            }
            if (songs.isEmpty() && pages.isEmpty()) null else YtShelf(title, songs, pages)
        }.toList()

    /**
     * An artist channel (`browse UC…`): the header picture (`musicImmersiveHeaderRenderer` or
     * `musicVisualHeaderRenderer`), the top songs (`musicShelfRenderer`), then carousels (Albums, Singles,
     * Videos, Fans might also like) parsed like Home's shelves.
     */
    fun parseArtist(root: JsonElement): YtArtist {
        val header = collect(root, "musicImmersiveHeaderRenderer").firstOrNull() ?: collect(root, "musicVisualHeaderRenderer").firstOrNull()
        val image = (header?.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails") as? JsonArray)
            ?.lastOrNull()?.jsonObject?.get("url")?.str()?.let(::sharperThumbnail)
        val about = (header?.path("monthlyListenerCount", "runs") as? JsonArray)?.joinToString("") { it.jsonObject["text"]?.str().orEmpty() }
            ?.takeIf { it.isNotBlank() }
        val songs = collect(root, "musicShelfRenderer").firstOrNull()?.let { parseSongs(it) }.orEmpty()
        return YtArtist(image, songs, parseHome(root), about)
    }

    /** Typing suggestions: each `searchSuggestionRenderer`'s runs joined ("dua lipa " + bold "levitating"). */
    fun parseSuggestions(root: JsonElement): List<String> =
        collect(root, "searchSuggestionRenderer").mapNotNull { r ->
            (r.path("suggestion", "runs") as? JsonArray)?.joinToString("") { it.jsonObject["text"]?.str().orEmpty() }?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct().toList()

    /** A radio / up-next list (`next` answer): `playlistPanelVideoRenderer` rows. */
    fun parseRadio(root: JsonElement): List<YtSong> {
        val seen = HashSet<String>()
        return collect(root, "playlistPanelVideoRenderer").mapNotNull { row ->
            val id = row.path("videoId")?.str() ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            val title = (row.path("title", "runs") as? JsonArray)?.firstOrNull()?.jsonObject?.get("text")?.str() ?: return@mapNotNull null
            val by = (row.path("longBylineText", "runs") as? JsonArray)?.joinToString("") { it.jsonObject["text"]?.str().orEmpty() }.orEmpty()
            val parts = by.split(" • ").map { it.trim() }
            val thumb = (row.path("thumbnail", "thumbnails") as? JsonArray)?.lastOrNull()?.jsonObject?.get("url")?.str()?.let(::sharperThumbnail)
            val length = (row.path("lengthText", "runs") as? JsonArray)?.firstOrNull()?.jsonObject?.get("text")?.str()
            YtSong(id, title, parts.getOrNull(0).orEmpty(), parts.getOrNull(1), thumb, length?.takeIf { DURATION.matches(it) }?.let(::parseDuration))
        }.toList()
    }

    /** Grid cards that open a browse page (the library's playlists grid). */
    fun parseCards(root: JsonElement): List<YtCollection> {
        val seen = HashSet<String>()
        return collect(root, "musicTwoRowItemRenderer").mapNotNull { card ->
            val browseId = card.path("navigationEndpoint", "browseEndpoint", "browseId")?.str() ?: return@mapNotNull null
            if (!seen.add(browseId)) return@mapNotNull null
            val name = (card.path("title", "runs") as? JsonArray)?.firstOrNull()?.jsonObject?.get("text")?.str() ?: return@mapNotNull null
            val sub = (card.path("subtitle", "runs") as? JsonArray)?.joinToString("") { it.jsonObject["text"]?.str().orEmpty() }.orEmpty()
            val thumb = (card.path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails") as? JsonArray)
                ?.lastOrNull()?.jsonObject?.get("url")?.str()?.let(::sharperThumbnail)
            YtCollection(browseId, name, sub.replace(" • ", " · "), thumb)
        }.toList()
    }

    /** Albums, artists or playlists in a filtered search: rows that open a browse page. */
    fun parseCollections(root: JsonElement): List<YtCollection> {
        val seen = HashSet<String>()
        return collect(root, "musicResponsiveListItemRenderer").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val browseId = obj.path("navigationEndpoint", "browseEndpoint", "browseId")?.str() ?: return@mapNotNull null
            if (!seen.add(browseId)) return@mapNotNull null
            val cols = obj["flexColumns"] as? JsonArray ?: return@mapNotNull null
            val title = columnRuns(cols, 0).firstOrNull() ?: return@mapNotNull null
            val parts = splitParts(columnRuns(cols, 1))
            val thumbs = obj.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails") as? JsonArray
            YtCollection(
                browseId = browseId,
                title = title,
                subtitle = parts.joinToString(" · "),
                thumbnailUrl = thumbs?.lastOrNull()?.jsonObject?.get("url")?.str()?.let(::sharperThumbnail),
            )
        }.toList()
    }

    /** The account name in an `account/account_menu` answer (header of the account popup). */
    fun parseAccountName(root: JsonElement): String? {
        val header = collect(root, "activeAccountHeaderRenderer").firstOrNull() ?: return null
        val name = header.path("accountName") ?: return null
        return name.path("simpleText")?.str()
            ?: (name.path("runs") as? JsonArray)?.joinToString("") { it.path("text")?.str().orEmpty() }?.takeIf { it.isNotBlank() }
    }

    private fun fixedText(obj: JsonObject): String? =
        ((obj["fixedColumns"] as? JsonArray)?.firstOrNull()
            ?.path("musicResponsiveListItemFixedColumnRenderer", "text", "runs") as? JsonArray)
            ?.firstOrNull()?.jsonObject?.get("text")?.str()

    private val DURATION = Regex("""^\d+:\d\d(:\d\d)?$""")
    private val TYPE_LABELS = setOf("Song", "Video", "Episode")

    private fun splitParts(runs: List<String>): List<String> {
        val parts = mutableListOf(StringBuilder())
        runs.forEach { if (it.trim() == "•") parts += StringBuilder() else parts.last().append(it) }
        return parts.map { it.toString().trim() }.filter { it.isNotEmpty() }
    }

    /** Search thumbnails come at 60-120 px; Google's image hosts serve any size via the `=wN-hN` suffix. */
    private val THUMB_SIZE = Regex("""=w\d+-h\d+""")
    private fun sharperThumbnail(url: String): String = url.replace(THUMB_SIZE, "=w226-h226")

    private fun parseDuration(text: String): Int = text.split(':').fold(0) { acc, part -> acc * 60 + part.toInt() }

    private fun columnRuns(cols: JsonArray, index: Int): List<String> =
        (cols.getOrNull(index)?.path("musicResponsiveListItemFlexColumnRenderer", "text", "runs") as? JsonArray)
            ?.mapNotNull { it.jsonObject["text"]?.str() }
            .orEmpty()

    /** Every value stored under [key], anywhere in the tree (depth-first, document order). */
    private fun collect(node: JsonElement, key: String): Sequence<JsonElement> = sequence {
        when (node) {
            is JsonArray -> node.forEach { yieldAll(collect(it, key)) }
            is JsonObject -> node.forEach { (k, v) -> if (k == key) yield(v) else yieldAll(collect(v, key)) }
            else -> Unit
        }
    }

    private fun JsonElement.path(vararg keys: String): JsonElement? =
        keys.fold(this as JsonElement?) { acc, k -> (acc as? JsonObject)?.get(k) }

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
