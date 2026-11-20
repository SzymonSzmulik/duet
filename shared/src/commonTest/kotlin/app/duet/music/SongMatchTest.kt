package app.duet.music

import app.duet.music.domain.Track
import app.duet.music.downloads.SongMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongMatchTest {
    private fun t(src: String, id: String, title: String, artist: String, ms: Long?) = Track(src, id, title, artist, durationMs = ms)
    private val spotify = t("spotify", "s1", "Instant Crush (feat. Julian Casablancas)", "Daft Punk, Julian Casablancas", 337_560)

    @Test
    fun picksSameSongClosestLength() {
        val yt = listOf(
            t("yt", "cover", "Instant Crush", "Ruben Dunn", 200_000),
            t("yt", "live", "Instant Crush (Live)", "Daft Punk", 400_000),
            t("yt", "right", "Instant Crush (feat. Julian Casablancas)", "Daft Punk & Julian Casablancas", 337_000),
            t("yt", "drumless", "Instant Crush (Drumless Edition)", "Daft Punk & Julian Casablancas", 339_000),
        )
        assertEquals("right", SongMatch.best(spotify, yt)?.id)
    }

    @Test
    fun noMatchWhenArtistDiffers() = assertNull(SongMatch.best(spotify, listOf(t("yt", "x", "Instant Crush", "Someone Else", 337_000))))

    @Test
    fun queryIsCleanTitleAndFirstArtist() = assertEquals("Instant Crush Daft Punk", SongMatch.query(spotify))
}

class PlayHistoryCodecTest {
    @Test
    fun roundTrips() {
        val t = listOf(Track("yt", "a", "Song", "Artist", "Album", "https://x/y.jpg", 1000L), Track("sp", "b", "Two", "B"))
        assertEquals(t, app.duet.music.player.PlayHistory.decode(app.duet.music.player.PlayHistory.encode(t)))
    }
}
