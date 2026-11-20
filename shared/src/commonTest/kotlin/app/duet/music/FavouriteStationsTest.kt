package app.duet.music

import app.duet.music.domain.Track
import app.duet.music.sources.radio.FavouriteStations
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FavouriteStationsTest {
    private fun station(id: String) = Track("app.duet.source.radio", id, "Station $id", "")

    @Test
    fun toggleKeepsAndPersists() {
        val store = MapSettings()
        val f = FavouriteStations(store)
        f.toggle(station("a")); f.toggle(station("b"))
        assertEquals(listOf("b", "a"), FavouriteStations(store).list.value.map { it.id }, "newest first, saved")
        f.toggle(station("a"))
        assertFalse(f.isFavourite(station("a")))
    }

    @Test
    fun lastListenedPrefersMostRecentlyPlayedFavourite() {
        val f = FavouriteStations(MapSettings())
        assertNull(f.lastListened(emptyList()))
        f.toggle(station("a")); f.toggle(station("b"))
        assertEquals("a", f.lastListened(listOf(station("x"), station("a"), station("b")))?.id)
        assertEquals("b", f.lastListened(listOf(station("x")))?.id, "no favourite played yet: first favourite")
    }
}
