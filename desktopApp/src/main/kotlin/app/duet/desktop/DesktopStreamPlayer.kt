package app.duet.desktop

import app.duet.music.extension.Playable
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.SourcePlayer
import app.duet.music.player.SourcePlayerState
import app.duet.music.settings.SoundSettings
import javafx.application.Platform
import javafx.scene.media.EqualizerBand
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.ln

/**
 * Plays [Playable.Stream]s with JavaFX's MediaPlayer (AAC and MP3, which is what YouTube Music's m4a and internet
 * radio give). A song with a cache key is downloaded to a temp file first, so the request can carry its headers and
 * seeking is instant; a stream without one is live radio and plays straight from its URL. Settings > Sound's
 * equalizer, bass, speed and balance are applied to the player (JavaFX has no reverb).
 */
class DesktopStreamPlayer(private val sound: SoundSettings, private val scope: CoroutineScope, private val cache: DesktopMediaCache) : SourcePlayer {
    private val _state = MutableStateFlow(SourcePlayerState())
    override val state: StateFlow<SourcePlayerState> = _state.asStateFlow()
    private val http = OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    private var player: MediaPlayer? = null
    private var loading: Job? = null
    private companion object { const val CHUNK = 1_048_576L }
    /** Bumped for every song, so a late callback of an earlier one is ignored. */
    @Volatile private var generation = 0

    init {
        // Starts JavaFX's toolkit (media needs it) without a window; it fails harmlessly if already running.
        runCatching { Platform.startup {} }
        Platform.setImplicitExit(false)
        val bands = sound.eqBands.map { it.value }
        scope.launch { combine(listOf(sound.eqOn.value, sound.bass.value, sound.loudness.value, sound.speed.value, sound.balance.value) + bands) { it.toList() }.collect { onFx { applySound() } } }
        scope.launch {
            while (true) {
                delay(250)
                val p = player ?: continue
                if (_state.value.status == PlaybackStatus.Playing) _state.update { it.copy(positionMs = p.currentTime.toMillis().toLong()) }
            }
        }
    }

    private var relayStream: Playable.Stream? = null
    private val relayServer by lazy {
        com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { ex ->
                val st = relayStream
                try {
                    if (st == null) { ex.sendResponseHeaders(404, -1); return@createContext }
                    val req = Request.Builder().url(st.url).apply { st.headers.forEach { (k, v) -> header(k, v) } }.build()
                    // Live radio is never downloaded: OkHttp reads it (Icecast/ICY servers, redirects) and JavaFX reads this plain local copy.
                    http.newBuilder().readTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
                        val body = r.body ?: run { ex.sendResponseHeaders(502, -1); return@use }
                        ex.responseHeaders.add("Content-Type", body.contentType()?.toString() ?: "audio/mpeg")
                        ex.sendResponseHeaders(200, 0)
                        ex.responseBody.use { out -> body.byteStream().copyTo(out, 8192) }
                    }
                } catch (_: Exception) { // the listener closed or the station dropped: nothing more to send
                } finally { ex.close() }
            }
            executor = java.util.concurrent.Executors.newCachedThreadPool { r -> Thread(r, "duet-radio-relay").apply { isDaemon = true } }
            start()
        }
    }

    /** The local address JavaFX plays a live stream from. */
    private fun relay(stream: Playable.Stream): String {
        relayStream = stream
        return "http://127.0.0.1:${relayServer.address.port}/live.${if (stream.url.contains("aac", true)) "aac" else "mp3"}?t=${System.nanoTime()}"
    }

    /** Debug output while DUET_SMOKE is set (see Main.kt). */
    private fun log(message: String) { if (System.getenv("DUET_SMOKE") != null || System.getenv("DUET_SMOKE_RADIO") != null) println("PLAYER $message") }

    override fun canPlay(playable: Playable) = playable is Playable.Stream

    override fun play(playable: Playable) {
        val stream = playable as? Playable.Stream ?: return
        release()
        val gen = ++generation
        _state.value = SourcePlayerState(PlaybackStatus.Loading)
        loading = scope.launch(Dispatchers.IO) {
            val uri = try {
                when {
                    stream.url.startsWith("file:") -> stream.url
                    stream.cacheKey == null -> relay(stream)
                    else -> download(stream).toURI().toString()
                }
            } catch (e: Exception) {
                if (gen == generation) _state.value = SourcePlayerState(PlaybackStatus.Error, error = e.message ?: "Couldn't load the song")
                return@launch
            }
            log("opening $uri")
            onFx { if (gen == generation) open(uri, gen) }
        }
    }

    private fun open(uri: String, gen: Int) {
        val mp = try { MediaPlayer(Media(uri).also { m -> m.setOnError { log("media error ${m.error}") } }) } catch (e: Exception) {
            log("can't open: $e")
            _state.value = SourcePlayerState(PlaybackStatus.Error, error = e.message ?: "Can't play this format")
            return
        }
        player = mp
        fun live() = gen == generation
        mp.setOnReady {
            log("ready")
            if (!live()) return@setOnReady
            // No duration from here: JavaFX reads YouTube's fragmented m4a as twice as long, so the song's own length is used.
            applySound()
            mp.play()
        }
        mp.setOnPlaying { if (live()) _state.update { it.copy(status = PlaybackStatus.Playing, error = null) } }
        mp.setOnPaused { if (live()) _state.update { it.copy(status = PlaybackStatus.Paused) } }
        mp.setOnStalled { if (live()) _state.update { it.copy(status = PlaybackStatus.Loading) } }
        mp.setOnEndOfMedia { if (live()) _state.update { it.copy(status = PlaybackStatus.Ended) } }
        mp.setOnError { log("player error ${mp.error}"); if (live()) _state.value = SourcePlayerState(PlaybackStatus.Error, error = mp.error?.message ?: "Playback error") }
    }

    /**
     * Fetches the song in 1 MB pieces with Range requests, as ExoPlayer does: YouTube's servers slow a plain
     * whole-file request down to playback speed, which would make a three-minute song take three minutes to load.
     */
    private fun download(s: Playable.Stream): File {
        val key = s.cacheKey!!
        // Already kept (a cached song): play it from disk. Otherwise fetch it into the cache, where it stays for next time.
        cache.fileFor(key)?.let { log("from cache"); return it }
        log("downloading ${s.url.take(80)} (${s.mimeType})")
        val f = cache.target(key)
        fetchRanged(http, s, f)
        cache.committed(key)
        log("downloaded ${f.length()} bytes")
        return f
    }

    /** Equalizer, bass, speed and balance on the current song. Runs on JavaFX's thread. */
    private fun applySound() {
        val mp = player ?: return
        mp.rate = sound.speed.value.value.toDouble().coerceIn(0.25, 4.0)
        mp.balance = sound.balance.value.value.toDouble().coerceIn(-1.0, 1.0)
        val eq = mp.audioEqualizer
        eq.isEnabled = true
        val ours = listOf(60, 230, 910, 3600, 14000)
        val on = sound.eqOn.value.value
        val bass = sound.bass.value.value / 1000.0 * EqualizerBand.MAX_GAIN
        val loud = sound.loudness.value.value / 100.0
        eq.bands.forEach { band ->
            // Our five bands go to JavaFX's ten by nearest centre frequency; the bass boost lifts the lowest ones.
            val nearest = ours.indices.minBy { abs(ln(ours[it].toDouble()) - ln(band.centerFrequency)) }
            val eqDb = if (on) sound.eqBands[nearest].value.value / 100.0 else 0.0
            // Bass boost lifts the low bands (and half as much the ones just above); loudness lifts lows and highs, the way the ear needs.
            val bassDb = when { band.centerFrequency <= 150 -> bass; band.centerFrequency <= 300 -> bass / 2; else -> 0.0 }
            val loudDb = if (band.centerFrequency <= 150 || band.centerFrequency >= 8000) loud else 0.0
            band.gain = (eqDb + bassDb + loudDb).coerceIn(EqualizerBand.MIN_GAIN, EqualizerBand.MAX_GAIN)
        }
    }

    override fun pause() = onFx { player?.pause() }
    override fun resume() = onFx { player?.play() }
    override fun seekTo(positionMs: Long) {
        onFx { player?.seek(javafx.util.Duration.millis(positionMs.toDouble())) }
        _state.update { it.copy(positionMs = positionMs) }
    }

    override fun stop() {
        release()
        _state.value = SourcePlayerState()
    }

    private fun release() {
        generation++
        loading?.cancel()
        val old = player
        player = null
        onFx { old?.dispose() }
    }

    private fun onFx(block: () -> Unit) = if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
}
