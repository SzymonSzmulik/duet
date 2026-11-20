package app.duet.music

import app.duet.music.domain.Track
import app.duet.music.player.ListenBrainz
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListenBrainzTest {
    @Test
    fun scrobblesAtHalfOrFourMinutes() {
        assertFalse(ListenBrainz.shouldScrobble(90_000, 200_000))
        assertTrue(ListenBrainz.shouldScrobble(100_000, 200_000), "half of a short song")
        assertTrue(ListenBrainz.shouldScrobble(240_000, 600_000), "four minutes of a long one")
        assertFalse(ListenBrainz.shouldScrobble(20_000, 25_000), "too short to count")
    }

    @Test
    fun buildsTheListen() {
        val body = ListenBrainz.body("single", Track("yt", "v", "Levitating", "Dua Lipa", "Future Nostalgia", durationMs = 203_000), 1_700_000_000, "YouTube Music").toString()
        assertTrue("\"listened_at\":1700000000" in body)
        assertTrue("\"track_name\":\"Levitating\"" in body && "\"release_name\":\"Future Nostalgia\"" in body)
        assertEquals(1, Regex("\"listen_type\":\"single\"").findAll(body).count())
    }
}
