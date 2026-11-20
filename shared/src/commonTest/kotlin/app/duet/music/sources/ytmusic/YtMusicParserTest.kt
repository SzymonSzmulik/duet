package app.duet.music.sources.ytmusic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YtMusicParserTest {

    private fun item(videoId: String, title: String, vararg subtitleRuns: String) = """
        {"musicResponsiveListItemRenderer":{
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://i.test/$videoId=w60-h60-l90-rj","width":60},{"url":"https://i.test/$videoId=w120-h120-l90-rj","width":120}]}}},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title","navigationEndpoint":{"watchEndpoint":{"videoId":"$videoId"}}}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[${subtitleRuns.joinToString(",") { "{\"text\":\"$it\"}" }}]}}}
          ],
          "playlistItemData":{"videoId":"$videoId"}
        }}
    """

    private val search = Json.parseToJsonElement(
        """
        {"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
          {"musicShelfRenderer":{"contents":[
            ${item("vid1", "Get Lucky", "Daft Punk", " • ", "Random Access Memories", " • ", "6:10")},
            ${item("vid2", "Intro", "Song", " • ", "Some Band", " • ", "1:02:03")},
            ${item("vid1", "Get Lucky (dupe)", "Daft Punk")},
            ${item("vid3", "Get Lucky (feat.)", "Daft Punk", ", ", "Pharrell Williams", " & ", "Nile Rodgers", " • ", "Random Access Memories", " • ", "6:10")},
            {"musicResponsiveListItemRenderer":{"flexColumns":[]}}
          ]}}
        ]}}}}]}}}
        """,
    )

    @Test
    fun parsesSongsFromSearch() {
        val songs = YtMusicParser.parseSongs(search)
        assertEquals(3, songs.size)
        // Several artist runs form one artist part; the album is the next part, not a separator.
        assertEquals("Daft Punk, Pharrell Williams & Nile Rodgers", songs[2].artist)
        assertEquals("Random Access Memories", songs[2].album)
        assertEquals(
            YtSong("vid1", "Get Lucky", "Daft Punk", "Random Access Memories", "https://i.test/vid1=w226-h226-l90-rj", 370),
            songs[0],
        )
        // Leading type label ("Song") is dropped; h:mm:ss durations parse.
        assertEquals("Some Band", songs[1].artist)
        assertNull(songs[1].album)
        assertEquals(3723, songs[1].durationSeconds)
    }


    @Test
    fun parsesAccountName() {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{"activeAccountHeaderRenderer":{"accountName":{"runs":[{"text":"Alex Doe"}]}}}}}}}]}""",
        )
        kotlin.test.assertEquals("Alex Doe", YtMusicParser.parseAccountName(root))
    }

    @Test
    fun parsesAnArtistChannel() {
        val root = Json.parseToJsonElement(
            """
            {"header":{"musicImmersiveHeaderRenderer":{
               "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://i.test/small"},{"url":"https://i.test/big"}]}}},
               "monthlyListenerCount":{"runs":[{"text":"589M monthly audience"}]}}},
             "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
               {"musicShelfRenderer":{"contents":[${item("s1", "Levitating", "Dua Lipa", " • ", "Future Nostalgia")}]}},
               {"musicCarouselShelfRenderer":{
                 "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Albums"}]}}},
                 "contents":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Future Nostalgia"}]},"subtitle":{"runs":[{"text":"2020"}]},
                   "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_1"}}}}]}}
             ]}}}}]}}}
            """,
        )
        val a = YtMusicParser.parseArtist(root)
        assertEquals("https://i.test/big", a.imageUrl)
        assertEquals("589M monthly audience", a.about)
        assertEquals(listOf("s1"), a.songs.map { it.videoId })
        assertEquals(listOf("Albums"), a.shelves.map { it.title })
        assertEquals("MPREb_1", a.shelves.single().pages.single().browseId)
    }

    @Test
    fun parsesSearchSuggestions() {
        val root = Json.parseToJsonElement(
            """{"contents":[{"searchSuggestionsSectionRenderer":{"contents":[
              {"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"dua l"},{"text":"ipa levitating","bold":true}]}}},
              {"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"dua lipa"}]}}},
              {"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"dua lipa"}]}}}]}}]}""",
        )
        assertEquals(listOf("dua lipa levitating", "dua lipa"), YtMusicParser.parseSuggestions(root))
    }
}
