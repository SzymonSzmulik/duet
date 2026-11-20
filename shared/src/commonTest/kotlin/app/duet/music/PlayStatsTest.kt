package app.duet.music

import app.duet.music.domain.Track
import app.duet.music.player.PlayHistory
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayStatsTest {
    private fun t(id: String, artist: String, src: String = "yt") = Track(src, id, "Song $id", artist)

    @Test
    fun countsPlaysRanksSongsAndArtistsAndSurvivesRestart() {
        val store = MapSettings()
        val h = PlayHistory(store)
        listOf(t("a", "Vela"), t("b", "Kestrel & Vela"), t("a", "Vela"), t("c", "Kestrel"), t("a", "Vela"), t("r", "France · news", "radio"))
            .forEach(h::add)
        h.add(t("r", "France · news", "radio")) // same as last: not counted again
        assertEquals(listOf("a" to 3, "c" to 1, "b" to 1), h.topSongs(3, skipSource = "radio").map { it.first.id to it.second })
        assertEquals(listOf("Vela" to 3, "Kestrel" to 2), h.topArtists(5, skipSource = "radio"))
        val again = PlayHistory(store)
        assertEquals(3, again.counts.value["yt:a"])
        assertEquals(1, again.counts.value["radio:r"])
    }
}
