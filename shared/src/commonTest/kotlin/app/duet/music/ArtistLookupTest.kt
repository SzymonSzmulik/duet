package app.duet.music

import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.search.pickArtist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtistLookupTest {
    private fun artist(id: String, title: String) = Collection("yt", id, SearchKind.Artists, title, "Artist")

    @Test
    fun picksTheExactNameElseTheTopResult() {
        val results = listOf(artist("1", "Dua Lipa KH"), artist("2", "dua lipa"), artist("3", "Calvin Harris"))
        assertEquals("2", pickArtist("Dua Lipa", results)?.id)
        assertEquals("1", pickArtist("Someone Else", results)?.id)
        assertNull(pickArtist("Dua Lipa", emptyList()))
    }
}
