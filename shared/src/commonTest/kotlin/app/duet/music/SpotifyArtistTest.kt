package app.duet.music

import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.sources.spotify.SpotifyProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SpotifyArtistTest {
    // Shape from Myspoti docs/api/CATALOG.md (queryArtistOverview).
    private val root = Json.parseToJsonElement(
        """
        {"data":{"artistUnion":{"uri":"spotify:artist:a","profile":{"name":"Artist A"},
         "stats":{"monthlyListeners":67636683},
         "visuals":{"avatarImage":{"sources":[{"url":"https://i.test/avatar"}]}},
         "headerImage":{"data":{"sources":[{"url":"https://i.test/banner"}]}},
         "discography":{
          "topTracks":{"items":[{"track":{"uri":"spotify:track:t1","name":"Song","duration":{"totalMilliseconds":297090},
             "artists":{"items":[{"profile":{"name":"Artist A"}}]},"albumOfTrack":{"coverArt":{"sources":[{"url":"https://i.test/c"}]}}}}]},
          "albums":{"items":[{"releases":{"items":[{"uri":"spotify:album:x","name":"Album","date":{"year":2026},"coverArt":{"sources":[{"url":"https://i.test/x"}]}}]}}]},
          "singles":{"items":[]}},
         "relatedContent":{"relatedArtists":{"items":[{"uri":"spotify:artist:b","profile":{"name":"Artist B"}}]}}}}}
        """,
    ) as JsonObject

    @Test
    fun parsesTheArtistOverview() {
        val page = SpotifyProvider.parseArtist(root, Collection(SpotifyProvider.ID, "spotify:artist:a", SearchKind.Artists, "Artist A", "Artist"))!!
        assertEquals("https://i.test/banner", page.imageUrl)
        assertEquals("67.6M monthly listeners", page.about)
        assertEquals(listOf("t1"), page.songs.map { it.id })
        assertEquals(listOf("Albums", "Fans also like"), page.shelves.map { it.title })
        assertEquals("Album · 2026", page.shelves[0].collections.single().subtitle)
        assertEquals(SearchKind.Artists, page.shelves[1].collections.single().kind)
    }
}
