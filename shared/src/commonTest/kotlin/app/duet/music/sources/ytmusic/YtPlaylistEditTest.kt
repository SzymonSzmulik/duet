package app.duet.music.sources.ytmusic

import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.domain.Track
import app.duet.music.extension.PlaylistEdit
import app.duet.music.settings.ExtensionSettings
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YtPlaylistEditTest {
    private class FakeYt(val rows: List<YtSong>) : YtMusicSource {
        val sent = mutableListOf<Pair<String, YtPlaylistEdit>>()
        override suspend fun editPlaylist(playlistId: String, edit: YtPlaylistEdit): Boolean { sent += playlistId to edit; return true }
        val removed = mutableListOf<String>()
        override suspend fun removePlaylist(playlistId: String): Boolean { removed += playlistId; return true }
        override suspend fun browseSongs(browseId: String) = rows
        override suspend fun search(query: String) = emptyList<YtSong>()
        override suspend fun searchVideos(query: String) = emptyList<YtSong>()
        override suspend fun streamUrl(videoId: String, quality: StreamQuality) = error("not used")
        override suspend fun searchCollections(query: String, kind: SearchKind) = emptyList<YtCollection>()
        override suspend fun radio(videoId: String) = emptyList<YtSong>()
        override suspend fun libraryPlaylists() = emptyList<YtCollection>()
        override suspend fun homeShelves() = emptyList<YtShelf>()
        override suspend fun accountName(cookie: String): String? = null
    }

    private fun row(id: String) = YtSong(id, id, "a", null, null, null, setVideoId = "set-$id")
    private val playlist = Collection(YouTubeMusicProvider.ID, "VLPLmine", SearchKind.Playlists, "Mine", "")

    @Test
    fun removeAndMoveUseTheRowsSetVideoIdAndCheckTheRow() = runTest {
        val yt = FakeYt(listOf(row("a"), row("b"), row("c")))
        val p = YouTubeMusicProvider(yt, ExtensionSettings(MapSettings(), "t."))
        val b = Track(YouTubeMusicProvider.ID, "b", "b", "a")

        assertTrue(p.editPlaylist(playlist, PlaylistEdit.Remove(1, b)))
        assertEquals("PLmine" to YtPlaylistEdit.Remove("set-b", "b"), yt.sent.last())
        assertFalse(p.editPlaylist(playlist, PlaylistEdit.Remove(2, b)), "row 2 is not that song any more")

        assertTrue(p.editPlaylist(playlist, PlaylistEdit.MoveUp(2)))
        assertEquals(YtPlaylistEdit.MoveBefore("set-c", "set-b"), yt.sent.last().second)
        assertFalse(p.editPlaylist(playlist, PlaylistEdit.MoveUp(0)), "the first row can't go up")
    }

    @Test
    fun onlyYourPlaylistsCanBeEdited() {
        val p = YouTubeMusicProvider(FakeYt(emptyList()), ExtensionSettings(MapSettings(), "t."))
        val liked = playlist.copy(id = "VLLM")
        val album = playlist.copy(id = "MPREb_x", kind = SearchKind.Albums)
        assertEquals(listOf(playlist), p.addablePlaylists(listOf(playlist, liked, album)))
    }

    @Test
    fun removingFromTheLibraryDeletesByPlaylistIdButNeverLikedMusic() = runTest {
        val yt = FakeYt(emptyList())
        val p = YouTubeMusicProvider(yt, ExtensionSettings(MapSettings(), "t."))
        val lib = app.duet.music.library.LibraryModel(app.duet.music.extension.SourceRegistry(listOf(p)), backgroundScope)
        assertTrue(lib.remove(playlist))
        assertEquals(listOf("PLmine"), yt.removed)
        assertFalse(lib.remove(playlist.copy(id = "VLLM")))
        assertEquals(1, yt.removed.size)
    }

    @Test
    fun parserKeepsThePlaylistRowId() {
        val json = """{"contents":[{"musicResponsiveListItemRenderer":{
            "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song"}]}}},
                           {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist"}]}}}],
            "playlistItemData":{"videoId":"vid1","playlistSetVideoId":"SET1"}}}]}"""
        val song = YtMusicParser.parseSongs(Json.parseToJsonElement(json)).single()
        assertEquals("vid1", song.videoId)
        assertEquals("SET1", song.setVideoId)
    }
}
