package app.duet.music

import app.duet.music.extension.Playable
import app.duet.music.extension.SourceRegistry
import app.duet.music.player.PlaybackCoordinator
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.RepeatMode
import app.duet.music.search.SearchModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackAndSearchTest {

    private fun TestScope.childScope() = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private val songs = (1..4).map { track("yt", "$it") }

    private fun TestScope.coordinator(player: FakePlayer = FakePlayer(), vararg sources: FakeSource = arrayOf(FakeSource("yt"))) =
        PlaybackCoordinator(SourceRegistry(sources.toList()), listOf(player), childScope(), Random(1))

    private val PlaybackCoordinator.ids get() = state.value.queue.map { it.track.id }
    private val PlaybackCoordinator.currentId get() = state.value.track?.id

    /** A player whose first start fails (a refused stream link) and whose second works. */
    private class FailsOnce : app.duet.music.player.SourcePlayer {
        override val state = kotlinx.coroutines.flow.MutableStateFlow(app.duet.music.player.SourcePlayerState())
        var starts = 0
        override fun canPlay(playable: Playable) = true
        override fun play(playable: Playable) {
            starts++
            state.value = if (starts == 1) app.duet.music.player.SourcePlayerState(PlaybackStatus.Error, error = "The song's server said 403")
            else app.duet.music.player.SourcePlayerState(PlaybackStatus.Playing, durationMs = 200_000)
        }
        override fun pause() {}
        override fun resume() {}
        override fun seekTo(positionMs: Long) {}
        override fun stop() {}
    }

    @Test
    fun aSongThatFailsToStartIsLookedUpAndTriedOnceMore() = runTest {
        val player = FailsOnce()
        val c = PlaybackCoordinator(SourceRegistry(listOf(FakeSource("yt"))), listOf(player), childScope(), Random(1))
        c.playAll(songs, 0)
        advanceUntilIdle()
        assertEquals(2, player.starts)
        assertEquals(PlaybackStatus.Playing, c.state.value.status)
        assertNull(c.state.value.error)
    }

    @Test
    fun aSongThatFailsTwiceShowsTheError() = runTest {
        val player = object : app.duet.music.player.SourcePlayer {
            override val state = kotlinx.coroutines.flow.MutableStateFlow(app.duet.music.player.SourcePlayerState())
            var starts = 0
            override fun canPlay(playable: Playable) = true
            override fun play(playable: Playable) { starts++; state.value = app.duet.music.player.SourcePlayerState(PlaybackStatus.Error, error = "nope") }
            override fun pause() {}
            override fun resume() {}
            override fun seekTo(positionMs: Long) {}
            override fun stop() {}
        }
        val c = PlaybackCoordinator(SourceRegistry(listOf(FakeSource("yt"))), listOf(player), childScope(), Random(1))
        c.playAll(songs, 0)
        advanceUntilIdle()
        assertEquals(2, player.starts)
        assertEquals(PlaybackStatus.Error, c.state.value.status)
    }

    @Test
    fun playResolvesThroughSourceAndRoutesToPlayer() = runTest {
        val player = FakePlayer()
        val c = coordinator(player)
        c.play(track("yt", "abc"))
        // Streams carry the song key so the device cache can find them again.
        assertEquals(listOf<Playable>(Playable.Stream("https://yt.test/abc", cacheKey = "yt:abc")), player.played)
        assertEquals(PlaybackStatus.Playing, c.state.value.status)
        assertEquals("abc", c.currentId)

        c.togglePlayPause()
        assertEquals(PlaybackStatus.Paused, c.state.value.status)
        c.togglePlayPause()
        assertEquals(PlaybackStatus.Playing, c.state.value.status)
    }

    @Test
    fun sleepTimerPausesThenTurnsItselfOff() = runTest {
        val player = FakePlayer()
        val c = coordinator(player)
        c.play(track("yt", "abc"))
        var done = false
        c.sleepIn(15) { done = true }
        testScheduler.advanceTimeBy(15 * 60_000L - 1)
        assertEquals(PlaybackStatus.Playing, c.state.value.status)
        testScheduler.advanceTimeBy(2)
        assertEquals(PlaybackStatus.Paused, c.state.value.status)
        assertEquals(true, done)
    }

    @Test
    fun crossfadeStartsNextSongEarlyOnTheSamePlayer() = runTest {
        val player = FakePlayer()
        val yt = FakeSource("yt", capabilities = setOf(app.duet.music.extension.Capability.Download))
        val c = PlaybackCoordinator(SourceRegistry(listOf(yt)), listOf(player), childScope(), Random(1), crossfadeMs = { 5_000 })
        c.playAll(listOf(track("yt", "a"), track("yt", "b")), 0)
        player.advanceTo(190_000)
        assertEquals("a", c.currentId) // more than 5 s left: still the first song
        player.advanceTo(196_000)
        assertEquals("b", c.currentId) // 4 s left: the next song started early, on the same player
        assertEquals(2, player.played.size)
        assertEquals(0, player.stops) // not stopped: the player overlaps the two
    }

    @Test
    fun noCrossfadeIntoASourceThatIsNotAStream() = runTest {
        val player = FakePlayer()
        val c = PlaybackCoordinator(SourceRegistry(listOf(FakeSource("yt"))), listOf(player), childScope(), Random(1), crossfadeMs = { 5_000 })
        c.playAll(listOf(track("yt", "a"), track("yt", "b")), 0)
        player.advanceTo(196_000)
        assertEquals("a", c.currentId)
    }

    @Test
    fun cachedSongPlaysFromCacheWithoutResolving() = runTest {
        val player = FakePlayer()
        val source = FakeSource("yt", failResolve = "offline")
        val c = PlaybackCoordinator(SourceRegistry(listOf(source)), listOf(player), childScope(), Random(1), isCached = { it.id == "abc" })
        c.play(track("yt", "abc"))
        assertEquals(listOf<Playable>(Playable.Stream(PlaybackCoordinator.CACHED_URL + "yt:abc", quality = "From cache", cacheKey = "yt:abc")), player.played)
        assertEquals(PlaybackStatus.Playing, c.state.value.status)
    }

    @Test
    fun mixedSourcesRouteToTheirOwnPlayerAndStopTheOther() = runTest {
        val ytPlayer = FakePlayer { (it as Playable.Stream).url.startsWith("https://yt.") }
        val spPlayer = FakePlayer { (it as Playable.Stream).url.startsWith("https://sp.") }
        val c = PlaybackCoordinator(SourceRegistry(listOf(FakeSource("yt"), FakeSource("sp"))), listOf(ytPlayer, spPlayer), childScope())

        c.playAll(listOf(track("yt", "a"), track("sp", "b")))
        assertEquals(1, ytPlayer.played.size)
        c.next()
        assertEquals(1, spPlayer.played.size)
        assertEquals(1, ytPlayer.stops, "switching source stops the previous player")
        assertEquals("b", c.currentId)
    }

    @Test
    fun standInPlaysInsteadAndFallsBackWhenItFails() = runTest {
        val ytPlayer = FakePlayer { (it as Playable.Stream).url.startsWith("https://yt.") }
        val spPlayer = FakePlayer { (it as Playable.Stream).url.startsWith("https://sp.") }
        val c = PlaybackCoordinator(
            SourceRegistry(listOf(FakeSource("yt"), FakeSource("sp"))), listOf(ytPlayer, spPlayer), childScope(),
            standIn = { t -> if (t.id == "match") track("yt", "m") else if (t.id == "broken") error("search failed") else null },
        )
        c.play(track("sp", "match"))
        assertEquals(1, ytPlayer.played.size, "the stand-in plays on the YouTube player")
        assertTrue(c.state.value.quality!!.startsWith("From "))
        assertEquals("match", c.currentId, "the queue still shows the Spotify song")
        c.play(track("sp", "broken"))
        assertEquals(1, spPlayer.played.size, "a failed lookup plays the song from its own source")
        c.play(track("sp", "nomatch"))
        assertEquals(2, spPlayer.played.size)
    }

    @Test
    fun streamQualityPicksHighMiddleOrLow() {
        val rates = listOf(160, 48, 128, 70)
        assertEquals(160, app.duet.music.sources.ytmusic.StreamQuality.High.pick(rates) { it })
        assertEquals(128, app.duet.music.sources.ytmusic.StreamQuality.Normal.pick(rates) { it })
        assertEquals(48, app.duet.music.sources.ytmusic.StreamQuality.Low.pick(rates) { it })
        assertNull(app.duet.music.sources.ytmusic.StreamQuality.Normal.pick(emptyList<Int>()) { it })
    }

    @Test
    fun queueAdvancesOnEndAndStopsAtTheEnd() = runTest {
        val player = FakePlayer()
        val c = coordinator(player)
        c.playAll(songs, startIndex = 2)
        assertEquals("3", c.currentId)
        player.finish()
        assertEquals("4", c.currentId)
        player.finish()
        assertEquals("4", c.currentId)
        assertEquals(PlaybackStatus.Ended, c.state.value.status)
        // Play after the end replays the last song.
        c.togglePlayPause()
        assertEquals(PlaybackStatus.Playing, c.state.value.status)
    }

    @Test
    fun clearUpcomingKeepsThePlayingSong() = runTest {
        val player = FakePlayer()
        val c = coordinator(player)
        c.playAll(songs, startIndex = 1)
        c.clearUpcoming()
        assertEquals(2, c.state.value.queue.size)
        assertEquals("2", c.currentId)
        assertEquals(PlaybackStatus.Playing, c.state.value.status)
    }

    @Test
    fun repeatAllWrapsAndRepeatOneRestarts() = runTest {
        val player = FakePlayer()
        val c = coordinator(player)
        c.playAll(songs, startIndex = 3)
        c.cycleRepeat() // All
        assertEquals(RepeatMode.All, c.state.value.repeat)
        player.finish()
        assertEquals("1", c.currentId)

        c.cycleRepeat() // One
        player.advanceTo(50_000)
        player.finish()
        assertEquals("1", c.currentId)
        assertEquals(0, player.state.value.positionMs)
        assertEquals(PlaybackStatus.Playing, player.state.value.status)
    }

    @Test
    fun previousRestartsAfterThreeSecondsElseGoesBack() = runTest {
        val player = FakePlayer()
        val c = coordinator(player)
        c.playAll(songs, startIndex = 1)
        player.advanceTo(10_000)
        c.previous()
        assertEquals("2", c.currentId)
        assertEquals(0, c.state.value.positionMs)
        c.previous()
        assertEquals("1", c.currentId)
        c.previous() // first song, no repeat: restart
        assertEquals("1", c.currentId)
    }

    @Test
    fun shuffleKeepsCurrentFirstAndUnshuffleRestoresOrder() = runTest {
        val c = coordinator()
        c.playAll(songs, startIndex = 2)
        c.toggleShuffle()
        assertEquals("3", c.currentId)
        assertEquals(0, c.state.value.index)
        assertEquals(songs.map { it.id }.toSet(), c.ids.toSet())
        c.toggleShuffle()
        assertEquals(listOf("1", "2", "3", "4"), c.ids)
        assertEquals("3", c.currentId)
    }

    @Test
    fun playNextGoesRightAfterCurrentAndMovingKeepsPlaying() = runTest {
        val c = coordinator()
        c.playNext(songs[0]) // empty queue: starts playing
        assertEquals("1", c.currentId)
        c.addToQueue(songs[1])
        c.playNext(songs[2])
        assertEquals(listOf("1", "3", "2"), c.ids)
        c.moveInQueue(c.state.value.queue[2].id, -1) // "2" up
        assertEquals(listOf("1", "2", "3"), c.ids)
        c.moveInQueue(c.state.value.queue[0].id, +1) // the playing song moves; it stays current
        assertEquals(listOf("2", "1", "3"), c.ids)
        assertEquals("1", c.currentId)
        c.moveInQueue(c.state.value.queue[0].id, -1) // already first: no change
        assertEquals(listOf("2", "1", "3"), c.ids)
    }

    @Test
    fun addRemoveAndSkipInQueue() = runTest {
        val c = coordinator()
        c.addToQueue(songs[0]) // empty queue: starts playing
        assertEquals("1", c.currentId)
        c.addToQueue(songs[1])
        c.addToQueue(songs[2])
        assertEquals(listOf("1", "2", "3"), c.ids)

        c.skipTo(c.state.value.queue[2].id)
        assertEquals("3", c.currentId)
        c.removeFromQueue(c.state.value.queue[0].id) // before current: index shifts
        assertEquals(listOf("2", "3"), c.ids)
        assertEquals("3", c.currentId)
        c.removeFromQueue(c.state.value.current!!.id) // current and last: stops
        assertEquals(listOf("2"), c.ids)
        assertEquals(PlaybackStatus.Idle, c.state.value.status)
    }

    @Test
    fun resolveFailureBecomesErrorStateAndRetryReplays() = runTest {
        val c = coordinator(FakePlayer(), FakeSource("yt", failResolve = "No playable stream"))
        c.play(track("yt", "abc"))
        assertEquals(PlaybackStatus.Error, c.state.value.status)
        assertEquals("No playable stream", c.state.value.error)
        c.togglePlayPause() // retry: resolves again, fails again, stays in error
        assertEquals(PlaybackStatus.Error, c.state.value.status)
    }

    @Test
    fun unknownSourceIsAnError() = runTest {
        val c = coordinator(FakePlayer(), *emptyArray())
        c.play(track("gone", "abc"))
        assertEquals(PlaybackStatus.Error, c.state.value.status)
    }

    @Test
    fun registryRejectsDuplicateIds() {
        assertFailsWith<IllegalArgumentException> { SourceRegistry(listOf(FakeSource("a"), FakeSource("a"))) }
    }

    @Test
    fun searchSkipsOnlySourcesThatNeedASignIn() = runTest {
        val scope = childScope()
        val optional = FakeSource("yt", results = listOf(track("yt", "1", title = "A")), signedOutNeeded = false)
        val needed = FakeSource("sp", results = listOf(track("sp", "2", title = "B")), signedOutNeeded = true)
        val model = SearchModel(SourceRegistry(listOf(optional, needed)), scope)
        model.onQueryChange("x")
        advanceUntilIdle()
        assertEquals(listOf("yt:1"), model.state.value.results.map { it.key }, "YouTube Music signed out still searches")
        scope.cancel()
    }

    @Test
    fun searchShowsASongBothSourcesHaveOnce() = runTest {
        val scope = childScope()
        val a = FakeSource("a", results = listOf(track("a", "1", title = "Levitating", artist = "Dua Lipa")))
        val b = FakeSource("b", results = listOf(track("b", "9", title = "levitating", artist = "Dua Lipa, DaBaby"), track("b", "2", title = "Other")))
        val model = SearchModel(SourceRegistry(listOf(a, b)), scope)
        model.onQueryChange("lev")
        advanceUntilIdle()
        assertEquals(listOf("a:1", "b:2"), model.state.value.results.map { it.key })
        scope.cancel()
    }

    @Test
    fun searchInterleavesSourcesAndKeepsOtherResultsWhenOneFails() = runTest {
        val scope = childScope()
        val a = FakeSource("a", results = listOf(track("a", "1"), track("a", "2"), track("a", "3")))
        val b = FakeSource("b", results = listOf(track("b", "1", title = "Other song")))
        val broken = FakeSource("c", failSearch = "HTTP 500")
        val model = SearchModel(SourceRegistry(listOf(a, b, broken)), scope)

        model.onQueryChange("x")
        advanceUntilIdle()
        val s = model.state.value
        assertEquals(listOf("a:1", "b:1", "a:2", "a:3"), s.results.map { it.key })
        assertEquals(mapOf("c" to "HTTP 500"), s.errors)
        assertTrue(!s.loading)

        model.onSourceFilter("b")
        advanceUntilIdle()
        assertEquals(listOf("b:1"), model.state.value.results.map { it.key })

        model.onQueryChange("  ")
        assertTrue(model.state.value.results.isEmpty())
        assertNull(model.state.value.errors["c"])
        scope.cancel()
    }
}
