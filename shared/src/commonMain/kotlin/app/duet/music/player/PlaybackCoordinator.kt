package app.duet.music.player

import app.duet.music.domain.Track
import app.duet.music.extension.Capability
import app.duet.music.extension.Playable
import app.duet.music.extension.SourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class PlaybackStatus { Idle, Loading, Playing, Paused, Ended, Error }

enum class RepeatMode { Off, All, One }

/** What one platform player reports. */
data class SourcePlayerState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val error: String? = null,
)

/** Plays some kinds of [Playable] on this device. Platform code implements it (ExoPlayer on Android). */
interface SourcePlayer {
    val state: StateFlow<SourcePlayerState>
    fun canPlay(playable: Playable): Boolean
    fun play(playable: Playable)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun stop()
}

/** One entry of the queue. [id] tells apart two entries of the same track. */
data class QueueItem(val id: Long, val track: Track)

data class PlayerState(
    val queue: List<QueueItem> = emptyList(),
    /** Index of the current item in [queue], or -1. */
    val index: Int = -1,
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val error: String? = null,
    /** e.g. "Opus · 146 kbps", when the source says. */
    val quality: String? = null,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
) {
    val current: QueueItem? get() = queue.getOrNull(index)
    val track: Track? get() = current?.track
    val hasNext: Boolean get() = index < queue.lastIndex || (repeat == RepeatMode.All && queue.isNotEmpty())
}

/**
 * The one player the UI talks to: owns the queue (tracks from any source, mixed), resolves each
 * item through its source and routes it to the [SourcePlayer] that can play it. Only one player
 * is active at a time; switching sources stops the previous one.
 *
 * Must be used from the main thread ([scope] should run on the main dispatcher).
 */
class PlaybackCoordinator(
    private val sources: SourceRegistry,
    private val players: List<SourcePlayer>,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
    /** A saved copy of the track on this device, played instead of streaming when present. */
    private val localCopy: (Track) -> String? = { null },
    /** Whether the device's audio cache holds the whole song (then it plays without resolving it again). */
    private val isCached: (Track) -> Boolean = { false },
    /**
     * Crossfade length in ms (0 = off). The next song then starts this long before the current one
     * ends, so the player can overlap them (SpatialFlow's AudioPlaybackService does the same).
     */
    private val crossfadeMs: () -> Long = { 0L },
    /**
     * The same song from another source to play instead (Spotify songs from YouTube Music, Settings > Sound),
     * or null to play the track from its own source.
     */
    private val standIn: suspend (Track) -> Track? = { null },
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var active: SourcePlayer? = null
    private var job: Job? = null
    private var nextId = 0L

    /** Replaces the queue with [tracks] and starts at [startIndex]. */
    fun playAll(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val items = tracks.map(::item)
        val start = startIndex.coerceIn(items.indices)
        val queue = if (_state.value.shuffle) shuffledAround(items, start) else items
        val index = if (_state.value.shuffle) 0 else start
        _state.update { it.copy(queue = queue, index = index) }
        startCurrent()
    }

    fun play(track: Track) = playAll(listOf(track))

    /** Appends to the end of the queue. Starts playing if nothing is queued. */
    fun addToQueue(track: Track) {
        val wasEmpty = _state.value.current == null
        _state.update { it.copy(queue = it.queue + item(track), index = if (wasEmpty) it.queue.size else it.index) }
        if (wasEmpty) startCurrent()
    }

    /** Puts [track] right after the current song. Starts playing if nothing is queued. */
    fun playNext(track: Track) {
        val s = _state.value
        if (s.current == null) return addToQueue(track)
        _state.update { it.copy(queue = it.queue.toMutableList().apply { add(it.index + 1, item(track)) }) }
    }

    /** Moves a queued song one place up ([by] = -1) or down (+1); the playing song keeps playing. */
    fun moveInQueue(itemId: Long, by: Int) {
        val s = _state.value
        val from = s.queue.indexOfFirst { it.id == itemId }
        val to = from + by
        if (from < 0 || to !in s.queue.indices) return
        val queue = s.queue.toMutableList().apply { add(to, removeAt(from)) }
        val index = when (s.index) {
            from -> to
            to -> from
            else -> s.index
        }
        _state.update { it.copy(queue = queue, index = index) }
    }

    fun removeFromQueue(itemId: Long) {
        val s = _state.value
        val at = s.queue.indexOfFirst { it.id == itemId }
        if (at < 0) return
        if (at == s.index) {
            // Removing what plays: move on to what follows it (or stop at the end).
            val queue = s.queue - s.queue[at]
            _state.update { it.copy(queue = queue, index = at.coerceAtMost(queue.lastIndex)) }
            if (queue.isEmpty() || at > queue.lastIndex) stopAll() else startCurrent()
        } else {
            _state.update { it.copy(queue = it.queue - it.queue[at], index = if (at < it.index) it.index - 1 else it.index) }
        }
    }

    /** Drops every song after the one playing; the playing song keeps playing. */
    fun clearUpcoming() = _state.update { it.copy(queue = it.queue.take(it.index + 1)) }

    fun skipTo(itemId: Long) {
        val at = _state.value.queue.indexOfFirst { it.id == itemId }
        if (at < 0) return
        _state.update { it.copy(index = at) }
        startCurrent()
    }

    fun next() {
        val s = _state.value
        when {
            s.index < s.queue.lastIndex -> _state.update { it.copy(index = it.index + 1) }
            s.repeat == RepeatMode.All && s.queue.isNotEmpty() -> _state.update { it.copy(index = 0) }
            else -> return
        }
        startCurrent()
    }

    /** Restarts the song if it has played a few seconds, else goes to the previous one. */
    fun previous() {
        val s = _state.value
        when {
            s.positionMs > RESTART_THRESHOLD_MS || s.index <= 0 && s.repeat != RepeatMode.All -> seekTo(0)
            s.index > 0 -> { _state.update { it.copy(index = it.index - 1) }; startCurrent() }
            else -> { _state.update { it.copy(index = it.queue.lastIndex) }; startCurrent() }
        }
    }

    fun seekTo(positionMs: Long) {
        active?.seekTo(positionMs.coerceAtLeast(0))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
    }

    fun togglePlayPause() {
        val player = active
        val current = _state.value
        when {
            // Start over with a fresh resolve: stream URLs expire, and errors are often an expired URL.
            current.status == PlaybackStatus.Error || current.status == PlaybackStatus.Ended -> if (current.current != null) startCurrent()
            player == null -> Unit
            current.status == PlaybackStatus.Playing -> player.pause()
            else -> player.resume()
        }
    }

    /** Both songs are plain streams of a downloadable source (the same player can overlap them). */
    private fun canCrossfade(from: Track, to: Track): Boolean {
        fun streams(t: Track) = sources[t.sourceId]?.manifest?.capabilities?.contains(app.duet.music.extension.Capability.Download) == true
        return streams(from) && streams(to)
    }

    /** Pauses whatever plays (any source); used when the app is swiped away. */
    fun pause() {
        job?.let { if (_state.value.status == PlaybackStatus.Loading) it.cancel() }
        active?.pause()
    }

    fun toggleShuffle() {
        val s = _state.value
        val on = !s.shuffle
        val current = s.current
        val queue = when {
            current == null -> s.queue
            on -> shuffledAround(s.queue, s.index)
            // Back to the order the items were added in (ids grow in insertion order).
            else -> s.queue.sortedBy { it.id }
        }
        _state.update { it.copy(shuffle = on, queue = queue, index = current?.let { c -> queue.indexOf(c) } ?: -1) }
    }

    fun cycleRepeat() = _state.update {
        it.copy(repeat = when (it.repeat) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; RepeatMode.One -> RepeatMode.Off })
    }

    private fun item(track: Track) = QueueItem(nextId++, track)

    /** [items] with the one at [keep] first and the rest shuffled after it. */
    private fun shuffledAround(items: List<QueueItem>, keep: Int): List<QueueItem> =
        listOf(items[keep]) + (items - items[keep]).shuffled(random)

    private var sleepJob: Job? = null

    /** Pauses the music after [minutes]; 0 cancels the timer. */
    /** Pauses after [minutes] (0 cancels), then calls [onDone] so the timer shows as off again. */
    fun sleepIn(minutes: Int, onDone: () -> Unit = {}) {
        sleepJob?.cancel()
        sleepJob = if (minutes <= 0) null else scope.launch {
            delay(minutes * 60_000L)
            active?.pause()
            onDone()
        }
    }

    private fun stopAll() {
        job?.cancel()
        active?.stop()
        active = null
        _state.update { it.copy(status = PlaybackStatus.Idle, positionMs = 0, durationMs = null, error = null, quality = null) }
    }

    private fun onEnded() {
        val s = _state.value
        when {
            s.repeat == RepeatMode.One -> { active?.seekTo(0); active?.resume() }
            s.hasNext -> next()
            else -> Unit // stay on the last item, showing Ended
        }
    }

    /** The queue entry that already had its one automatic retry (see [startCurrent]); -1 when none is waiting. */
    private var retriedItem = -1L

    private fun startCurrent() {
        val item = _state.value.current ?: return
        val track = item.track
        job?.cancel()
        _state.update {
            it.copy(status = PlaybackStatus.Loading, positionMs = 0, durationMs = track.durationMs, error = null, quality = null)
        }
        job = scope.launch {
            try {
                val playable = localCopy(track)?.let { Playable.Stream(it, quality = "Saved on this phone") }
                    ?: standInPlayable(track)
                    ?: if (isCached(track)) Playable.Stream(CACHED_URL + track.key, quality = "From cache", cacheKey = track.key)
                    else (sources[track.sourceId] ?: error("Source ${track.sourceId} is not installed")).let { src -> src.resolve(track).let {
                        // Live radio never ends: it has no key, so nothing tries to download or cache it as a song.
                        if (it is Playable.Stream && Capability.LiveRadio !in src.manifest.capabilities) it.copy(cacheKey = track.key) else it
                    }
                    }
                val player = players.firstOrNull { it.canPlay(playable) } ?: error("No player can play this item")
                if (active !== player) active?.stop()
                active = player
                _state.update { it.copy(quality = (playable as? Playable.Stream)?.quality) }
                player.play(playable)
                var ended = false
                var advancedEarly = false
                player.state.collect { s ->
                    if (_state.value.current?.id != item.id) return@collect
                    if (s.status == PlaybackStatus.Playing) retriedItem = -1
                    // A song that fails to start gets one more try with a fresh look-up: a service's stream links expire or
                    // are refused now and then (YouTube says 403), and a new link usually works.
                    if (s.status == PlaybackStatus.Error && retriedItem != item.id) {
                        retriedItem = item.id
                        startCurrent()
                        return@collect
                    }
                    // Crossfade: go on to the next song early when both are played by the same streaming player.
                    val cf = crossfadeMs()
                    val q = _state.value
                    val nextTrack = q.queue.getOrNull(q.index + 1)?.track
                    val dur = s.durationMs
                    if (cf > 0 && !advancedEarly && s.status == PlaybackStatus.Playing && dur != null && dur - s.positionMs in 1..cf &&
                        q.repeat != RepeatMode.One && nextTrack != null && canCrossfade(track, nextTrack)
                    ) {
                        advancedEarly = true
                        next()
                        return@collect
                    }
                    _state.update {
                        it.copy(
                            // The player reports Idle until it has the item; keep showing Loading meanwhile.
                            status = if (s.status == PlaybackStatus.Idle) PlaybackStatus.Loading else s.status,
                            positionMs = s.positionMs,
                            durationMs = s.durationMs ?: track.durationMs,
                            error = s.error,
                        )
                    }
                    if (s.status == PlaybackStatus.Ended && !ended) {
                        ended = true
                        onEnded()
                    } else if (s.status != PlaybackStatus.Ended) {
                        ended = false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(status = PlaybackStatus.Error, error = e.message ?: "Playback failed") }
            }
        }
    }

    /** The [standIn]'s stream (cached or resolved), or null to play [track] itself (no match, or it failed). */
    private suspend fun standInPlayable(track: Track): Playable.Stream? {
        try {
            val other = standIn(track) ?: return null
            val source = sources[other.sourceId] ?: return null
            val from = "From ${source.manifest.name}"
            if (isCached(other)) return Playable.Stream(CACHED_URL + other.key, quality = "$from · from cache", cacheKey = other.key)
            val s = source.resolve(other) as? Playable.Stream ?: return null
            return s.copy(cacheKey = other.key, quality = listOfNotNull(from, s.quality).joinToString(" · "))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
    }

    companion object {
        private const val RESTART_THRESHOLD_MS = 3_000L
        /** Address of a song played from the cache only (never fetched: the cache answers by key). */
        const val CACHED_URL = "https://cache.duet.invalid/"
    }
}
