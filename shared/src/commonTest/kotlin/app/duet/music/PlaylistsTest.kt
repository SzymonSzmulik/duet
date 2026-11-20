package app.duet.music

import app.duet.music.domain.Track
import app.duet.music.library.Playlists
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistsTest {
    @Test
    fun mixesSourcesAndSurvivesRestart() {
        val store = MapSettings()
        val p = Playlists(store)
        val id = p.create("Drive").id
        p.add(id, Track("app.duet.source.spotify", "s1", "Song A", "X"))
        p.add(id, Track("app.duet.source.ytmusic", "y1", "Song B", "Y"))
        p.add(id, Track("app.duet.source.ytmusic", "y1", "Song B", "Y")) // once only
        val again = Playlists(store)
        assertEquals(listOf("s1", "y1"), again[id]?.tracks?.map { it.id })
        assertEquals("Drive", again[id]?.name)
        again.remove(id, Track("app.duet.source.spotify", "s1", "Song A", "X"))
        again.delete(p.create("Other").id)
        assertEquals(listOf("y1"), Playlists(store)[id]?.tracks?.map { it.id })
        assertTrue(Playlists(store).list.value.size == 1)
    }

    @Test
    fun copiesAndReorders() {
        val p = Playlists(MapSettings())
        val id = p.create("Mix").id
        val a = Track("s", "a", "A", "X"); val b = Track("s", "b", "B", "X"); val c = Track("y", "c", "C", "Y")
        p.addAll(id, listOf(a, b, a, c))
        assertEquals(listOf("a", "b", "c"), p[id]?.tracks?.map { it.id }, "copied once each, in order")
        p.move(id, 2, 0)
        assertEquals(listOf("c", "a", "b"), p[id]?.tracks?.map { it.id })
        p.rename(id, "  ")
        assertEquals("Mix", p[id]?.name, "a blank name keeps the old one")
    }
}
