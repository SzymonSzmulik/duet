package app.duet.music

import app.duet.music.domain.Track
import app.duet.music.player.matchStation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoicePlayTest {
    private fun station(name: String) = Track("app.duet.source.radio", name, name, "")
    private val stations = listOf(station("Heart 80s"), station("ESKA ROCK"), station("Eska"))

    @Test
    fun matchesStationNames() {
        assertEquals("Eska", matchStation("eska", stations)?.title, "exact name wins over a longer one")
        assertEquals("ESKA ROCK", matchStation("Eska Rock", stations)?.title)
        assertEquals("Heart 80s", matchStation("heart 80s radio", stations)?.title, "extra words around the name")
        assertNull(matchStation("Dua Lipa", stations))
        assertNull(matchStation("  ", stations))
    }
}
