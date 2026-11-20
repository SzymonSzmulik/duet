package app.duet.music

import android.content.Context
import app.duet.music.service.PlaybackService
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import app.duet.music.settings.AppSettings
import app.duet.music.settings.Flag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.duet.music.extension.Playable
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.SourcePlayer
import app.duet.music.player.SourcePlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays [Playable.Stream] URLs with Media3 ExoPlayer. Main thread only.
 * [PlaybackService] wraps [exo] in a MediaSession for background playback and system controls.
 *
 * Crossfade (Settings > Sound) works as in SpatialFlow's AudioPlaybackService (Apache-2.0): a song
 * asked for while the current one is in its last seconds starts on a second ExoPlayer, the two
 * volumes cross, then the new player becomes [exo] (the session, effects and listeners move to it)
 * and the old one is released.
 */
@OptIn(UnstableApi::class)
class ExoStreamPlayer(
    private val context: Context,
    private val settings: AppSettings,
    scope: CoroutineScope,
    private val mediaCache: app.duet.music.cache.AndroidMediaCache,
) : SourcePlayer {
    private val sound = settings.sound

    /** Stereo balance runs inside each player's audio pipeline (Settings > Sound). */
    private var balance = app.duet.music.audio.BalanceProcessor()
    var exo: ExoPlayer = build(balance)
        private set

    /** Called with the new player after a crossfade, so the media session can follow it. */
    var onPlayerChanged: ((ExoPlayer) -> Unit)? = null

    private fun audioAttributes() = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        // Spatial audio: let the phone's spatializer render it on headphones (Android 12L+).
        .setSpatializationBehavior(if (sound.spatial.value.value) C.SPATIALIZATION_BEHAVIOR_AUTO else C.SPATIALIZATION_BEHAVIOR_NEVER)
        .build()

    private fun build(processor: app.duet.music.audio.BalanceProcessor): ExoPlayer =
        ExoPlayer.Builder(context, object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean) =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(processor))
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        })
            .setAudioAttributes(audioAttributes(), /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(settings.flag(Flag.PauseOnUnplug).value)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also {
                it.skipSilenceEnabled = settings.flag(Flag.SkipSilence).value
                it.setPlaybackSpeed(sound.speed.value.value)
                processor.balance = sound.balance.value.value
            }

    private val _state = MutableStateFlow(SourcePlayerState())
    override val state: StateFlow<SourcePlayerState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            publish()
            applyFade()
            if (exo.isPlaying) handler.postDelayed(this, if (sound.fadeSeconds.value.value > 0) 100 else 500)
        }
    }

    /** With crossfade on, the song's volume rises over its first seconds and falls over its last. */
    private fun applyFade() {
        val fadeMs = sound.fadeSeconds.value.value * 1000L
        if (fadeMs <= 0) { exo.volume = 1f; return }
        val pos = exo.currentPosition
        val dur = exo.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        val fadeIn = (pos.toFloat() / fadeMs).coerceIn(0f, 1f)
        val fadeOut = ((dur - pos).toFloat() / fadeMs).coerceIn(0f, 1f)
        exo.volume = minOf(fadeIn, fadeOut)
    }

    private val effects = app.duet.music.audio.SoundEffects(exo, sound, balance, scope)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
            handler.removeCallbacks(ticker)
            if (player.isPlaying) handler.post(ticker)
        }

        override fun onPlayerError(error: PlaybackException) = publish()

        override fun onAudioSessionIdChanged(audioSessionId: Int) = effects.onSession(audioSessionId)

        // Loading stopped: the song may now be complete in the cache.
        override fun onIsLoadingChanged(isLoading: Boolean) { if (!isLoading) mediaCache.refresh() }
    }

    init {
        scope.launch { sound.spatial.value.collect { exo.setAudioAttributes(audioAttributes(), true) } }
        // Settings > Playback, applied live.
        scope.launch { settings.flag(Flag.SkipSilence).collect { exo.skipSilenceEnabled = it } }
        scope.launch { settings.flag(Flag.PauseOnUnplug).collect { exo.setHandleAudioBecomingNoisy(it) } }
        exo.addListener(listener)
        // ExoPlayer makes its audio session when built and only reports later changes, so attach the
        // effects to that first session here or they never take hold.
        effects.onSession(exo.audioSessionId)
    }

    override fun canPlay(playable: Playable) = playable is Playable.Stream

    override fun play(playable: Playable) {
        val stream = playable as Playable.Stream
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(stream.headers)
        stream.headers["User-Agent"]?.let(http::setUserAgent)
        val item = MediaItem.Builder().setUri(stream.url)
            .apply { stream.mimeType?.let { setMimeType(it.substringBefore(';')) } }
            .apply { stream.cacheKey?.let { setCustomCacheKey(it) } }
            .build()
        // DefaultDataSource opens saved songs (file://) as well as web streams (through [http]); streams
        // go through the disk cache, keyed by the song so the next play can come from the cache.
        val upstream = DefaultDataSource.Factory(context, http)
        val source = if (stream.cacheKey == null || stream.url.startsWith("file:")) upstream
            else androidx.media3.datasource.cache.CacheDataSource.Factory().setCache(mediaCache.cache).setUpstreamDataSourceFactory(upstream)
        val media = ProgressiveMediaSource.Factory(source).createMediaSource(item)

        val crossMs = sound.fadeSeconds.value.value * 1000L
        val left = exo.duration.takeIf { it != C.TIME_UNSET }?.let { it - exo.currentPosition }
        if (crossMs > 0 && exo.isPlaying && left != null && left <= crossMs + 2_000) crossfadeTo(media, crossMs) else {
            exo.setMediaSource(media)
            exo.prepare()
            exo.play()
        }
        PlaybackService.start(context)
    }

    /** Starts [media] on a second player and crosses the volumes over [ms], then hands everything to it. */
    private fun crossfadeTo(media: androidx.media3.exoplayer.source.MediaSource, ms: Long) {
        val old = exo
        old.removeListener(listener)
        // The outgoing player stops handling audio focus, or it would pause itself the moment the
        // incoming player takes focus, and there would be no overlap.
        old.setAudioAttributes(audioAttributes(), /* handleAudioFocus = */ false)
        val nextBalance = app.duet.music.audio.BalanceProcessor()
        val next = build(nextBalance)
        next.volume = 0f
        next.setMediaSource(media)
        next.prepare()
        next.play()
        exo = next
        balance = nextBalance
        next.addListener(listener)
        effects.retarget(next, nextBalance)
        onPlayerChanged?.invoke(next)
        val from = old.volume
        val start = android.os.SystemClock.uptimeMillis()
        handler.post(object : Runnable {
            override fun run() {
                val f = ((android.os.SystemClock.uptimeMillis() - start).toFloat() / ms).coerceIn(0f, 1f)
                old.volume = from * (1f - f)
                if (f < 1f) handler.postDelayed(this, 50) else old.release()
            }
        })
    }

    override fun pause() = exo.pause()

    override fun resume() {
        if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
        exo.play()
    }

    override fun seekTo(positionMs: Long) {
        exo.seekTo(positionMs)
        publish()
    }

    override fun stop() {
        handler.removeCallbacks(ticker)
        exo.stop()
        exo.clearMediaItems()
        _state.value = SourcePlayerState()
    }

    private fun publish() {
        val error = exo.playerError
        val status = when {
            error != null -> PlaybackStatus.Error
            exo.playbackState == Player.STATE_ENDED -> PlaybackStatus.Ended
            exo.playbackState == Player.STATE_IDLE -> PlaybackStatus.Idle
            exo.isPlaying -> PlaybackStatus.Playing
            exo.playbackState == Player.STATE_BUFFERING && exo.playWhenReady -> PlaybackStatus.Loading
            else -> PlaybackStatus.Paused
        }
        _state.value = SourcePlayerState(
            status = status,
            positionMs = exo.currentPosition,
            durationMs = exo.duration.takeIf { it != C.TIME_UNSET },
            error = error?.let { "${it.errorCodeName}${it.message?.let { m -> " ($m)" } ?: ""}" },
        )
    }
}
