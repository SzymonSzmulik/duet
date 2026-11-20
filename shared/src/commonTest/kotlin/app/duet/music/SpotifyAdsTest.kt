package app.duet.music

import app.duet.music.sources.spotify.SpotifyAds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpotifyAdsTest {
    // Song 0 ends into the ad (state 1); skipping goes to song 2 (state 2).
    private fun machine(skipToAd: Boolean, current: Int = 0) = """
        {"state_machine":{"tracks":[
          {"content_type":"TRACK","metadata":{"uri":"spotify:track:a"}},
          {"content_type":"AD","metadata":{"uri":"spotify:ad:x"}},
          {"content_type":"TRACK","metadata":{"uri":"spotify:track:b"}}],
         "states":[
          {"state_id":"s0","track":0,"transitions":{"advance":{"state_index":1,"paused":false},"skip_next":{"state_index":${if (skipToAd) 1 else 2},"paused":false}}},
          {"state_id":"s1","track":1,"transitions":{"advance":null,"skip_next":null}},
          {"state_id":"s2","track":2,"transitions":{"advance":null,"skip_next":null}}]},
         "updated_state_ref":{"state_index":$current,"paused":false}}
    """.trimIndent()

    @Test
    fun songEndingIntoAnAdGoesToTheNextSong() {
        val r = SpotifyAds.withoutAds(machine(skipToAd = false))
        assertTrue(r.body.contains("\"advance\":{\"state_index\":2"), r.body)
        assertFalse(r.adAhead)
        assertFalse(r.adNow)
    }

    @Test
    fun adOnBothRoutesIsReportedAhead() {
        val r = SpotifyAds.withoutAds(machine(skipToAd = true))
        assertTrue(r.adAhead)
        assertTrue(SpotifyAds.withoutAds(machine(skipToAd = true, current = 1)).adNow)
    }

    @Test
    fun otherRepliesAndHosts() {
        assertEquals("{\"ok\":1}", SpotifyAds.withoutAds("{\"ok\":1}").body)
        assertEquals("not json", SpotifyAds.withoutAds("not json").body)
        assertTrue(SpotifyAds.isAdHost("https://adstudio-assets.scdn.co/mp3/abc.mp3"))
        assertTrue(SpotifyAds.isAdHost("https://audio-ak.scdn.co/mp3-ad/abc"))
        assertFalse(SpotifyAds.isAdHost("https://audio4-fa.scdn.co/audio/abc"))
    }
}
