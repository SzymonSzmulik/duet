package app.duet.music

import app.duet.music.sources.spotify.SpotifyProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SpotifyParseTest {
    @Test
    fun parsesSearchTracks() {
        val json = """{"tracks":{"items":[{"id":"2cGxRwrMyEAp8dEbuZaVv6","name":"Instant Crush","duration_ms":337560,
            "artists":[{"name":"Daft Punk"},{"name":"Julian Casablancas"}],
            "album":{"name":"Random Access Memories","images":[{"url":"https://i.scdn.co/image/big"}]}}]}}"""
        val t = SpotifyProvider.parseTracks(Json.parseToJsonElement(json).jsonObject).single()
        assertEquals("app.duet.source.spotify", t.sourceId)
        assertEquals("Instant Crush", t.title)
        assertEquals("Daft Punk, Julian Casablancas", t.artist)
        assertEquals("Random Access Memories", t.album)
        assertEquals("https://i.scdn.co/image/big", t.artworkUrl)
        assertEquals(337560L, t.durationMs)
    }
}

class SpotifyGqlParseTest {
    @Test
    fun parsesGraphQlSearch() {
        val json = """{"data":{"searchV2":{"tracksV2":{"items":[{"item":{"data":{"uri":"spotify:track:2cGx","name":"Instant Crush",
            "duration":{"totalMilliseconds":337560},"playability":{"playable":true},
            "artists":{"items":[{"profile":{"name":"Daft Punk"}},{"profile":{"name":"Julian Casablancas"}}]},
            "albumOfTrack":{"name":"Random Access Memories","coverArt":{"sources":[{"url":"small"},{"url":"large"}]}}}}},
            {"item":{"data":{"uri":"spotify:episode:x","name":"Podcast"}}}]}}}}"""
        val t = SpotifyProvider.parseGqlTracks(Json.parseToJsonElement(json).jsonObject).single()
        assertEquals("2cGx", t.id)
        assertEquals("Daft Punk, Julian Casablancas", t.artist)
        assertEquals("large", t.artworkUrl)
        assertEquals(337560L, t.durationMs)
    }
}

class SpotifyHomeParseTest {
    @Test
    fun parsesHomeSections() {
        val json = """{"data":{"home":{"sectionContainer":{"sections":{"items":[
            {"data":{"title":{"transformedLabel":"Recommended radios"}},"sectionItems":{"items":[
              {"content":{"data":{"uri":"spotify:playlist:p1","name":"Radio X","images":{"items":[{"sources":[{"url":"img"}]}]},"ownerV2":{"data":{"name":"Spotify"}}}}},
              {"content":{"data":{"uri":"spotify:artist:a1","profile":{"name":"Daft Punk"},"visuals":{"avatarImage":{"sources":[{"url":"av"}]}}}}},
              {"content":{"data":{"uri":"spotify:episode:e1","name":"Pod"}}}]}},
            {"data":{"title":{"transformedLabel":"Empty"}},"sectionItems":{"items":[]}}]}}}}}"""
        val shelves = SpotifyProvider.parseHome(Json.parseToJsonElement(json).jsonObject)
        assertEquals(1, shelves.size)
        assertEquals("Recommended radios", shelves[0].title)
        assertEquals(listOf("Radio X", "Daft Punk"), shelves[0].collections.map { it.title })
        assertEquals("img", shelves[0].collections[0].artworkUrl)
    }
}

class SpotifyLibraryParseTest {
    @Test
    fun parsesLibrary() {
        val json = """{"data":{"me":{"libraryV3":{"items":[
            {"item":{"_uri":"spotify:collection:tracks","data":{"name":"Liked Songs","count":42,"image":{"sources":[{"url":"liked"}]}}}},
            {"item":{"data":{"uri":"spotify:playlist:p1","name":"Gym","images":{"items":[{"sources":[{"url":"g"}]}]},"ownerV2":{"data":{"name":"me"}}}}},
            {"item":{"data":{"uri":"spotify:show:s1","name":"Podcast"}}}]}}}}"""
        val lib = SpotifyProvider.parseLibrary(Json.parseToJsonElement(json).jsonObject)
        assertEquals(listOf("Liked Songs", "Gym"), lib.map { it.title })
        assertEquals("spotify:collection:tracks", lib[0].id)
        assertEquals("Playlist · 42 songs", lib[0].subtitle)
    }
}
