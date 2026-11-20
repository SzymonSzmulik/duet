package app.duet.music.sources.ytmusic

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Talks to the real YouTube Music. Skipped unless DUET_LIVE=1, because it needs the network and
 * breaks whenever YouTube changes something (which is exactly what it is for):
 *   DUET_LIVE=1 ./gradlew :shared:desktopTest --tests '*LiveYtMusicTest*'
 */
class LiveYtMusicTest {
    @Test
    fun searchThenResolveStream() = runBlocking {
        if (System.getenv("DUET_LIVE") != "1") return@runBlocking
        val http = HttpClient()
        val yt = InnerTubeYtMusicSource(http, NewPipeStreamResolver())

        val songs = yt.search("daft punk get lucky")
        println("search: ${songs.size} songs; first = ${songs.firstOrNull()}")
        assertTrue(songs.isNotEmpty(), "search returned no songs")

        val stream = yt.streamUrl(songs.first().videoId)
        println("stream: ${stream.mimeType} @ ${stream.bitrate} bps")
        assertTrue(stream.mimeType.startsWith("audio/"))

        // 1 KB at the start AND 1 KB ~3 MB in: YouTube serves the first ~1-2 MB even to URLs it
        // will refuse later (missing PoToken), so checking only the start gives a false pass.
        for (offset in listOf(0L, 3_000_000L)) {
            val status = http.get(stream.url) {
                stream.headers.forEach { (k, v) -> header(k, v) }
                header("Range", "bytes=$offset-${offset + 1023}")
            }.status.value
            println("1 KB at $offset: HTTP $status")
            assertEquals(206, status, "stream refused at byte $offset")
        }
        http.close()
    }

    @Test
    fun albumsArtistsPlaylistsAndTheirSongs() = runBlocking {
        if (System.getenv("DUET_LIVE") != "1") return@runBlocking
        val yt = InnerTubeYtMusicSource(HttpClient(), NewPipeStreamResolver())
        for (kind in listOf(app.duet.music.domain.SearchKind.Albums, app.duet.music.domain.SearchKind.Artists, app.duet.music.domain.SearchKind.Playlists)) {
            val found = yt.searchCollections("daft punk", kind)
            println("$kind: ${found.size}; first = ${found.firstOrNull()}")
            assertTrue(found.isNotEmpty(), "no $kind")
            val songs = yt.browseSongs(found.first().browseId)
            println("  songs in first: ${songs.size}; first = ${songs.firstOrNull()}")
            assertTrue(songs.isNotEmpty(), "no songs in first $kind")
        }
    }

    @Test
    fun homeFeed() = runBlocking {
        if (System.getenv("DUET_LIVE") != "1") return@runBlocking
        val shelves = InnerTubeYtMusicSource(HttpClient(), NewPipeStreamResolver()).homeShelves()
        shelves.forEach { println("shelf '${it.title}': ${it.songs.size} songs, ${it.pages.size} pages; e.g. ${it.songs.firstOrNull()?.title ?: it.pages.firstOrNull()?.title}") }
        assertTrue(shelves.isNotEmpty(), "home feed empty")
    }

    @Test
    fun videosFindYouTubeOnlyRemixes() = runBlocking {
        if (System.getenv("DUET_LIVE") != "1") return@runBlocking
        val v = InnerTubeYtMusicSource(HttpClient(), NewPipeStreamResolver()).searchVideos("instant crush remix")
        v.take(6).forEach { println("video: ${it.title} | ${it.artist} | ${it.durationSeconds}s") }
        assertTrue(v.isNotEmpty())
    }

    @Test
    fun radioForQuickPicks() = runBlocking {
        if (System.getenv("DUET_LIVE") != "1") return@runBlocking
        val r = InnerTubeYtMusicSource(HttpClient(), NewPipeStreamResolver()).radio("khnokW3Mw24")
        r.take(5).forEach { println("radio: ${it.title} | ${it.artist} | ${it.durationSeconds}s | art=${it.thumbnailUrl != null}") }
        assertTrue(r.size >= 10, "radio too short: ${r.size}")
    }
}
