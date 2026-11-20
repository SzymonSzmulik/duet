package app.duet.music.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.duet.music.settings.SoundSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Settings > Sound on the phone's player: Android's audio effects (equalizer, bass boost, loudness,
 * reverb) on ExoPlayer's audio session, plus balance and speed. Effects are made again when the
 * session changes. Phones may refuse an effect; then that one is skipped and the rest still work.
 */
@OptIn(UnstableApi::class)
class SoundEffects(private var exo: ExoPlayer, private val sound: SoundSettings, private var balance: BalanceProcessor, scope: CoroutineScope) {
    private var session = 0
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null
    private var reverb: PresetReverb? = null

    init {
        scope.launch { combine(listOf(sound.eqOn.value, sound.reverb.value, sound.bass.value, sound.loudness.value) + sound.eqBands.map { it.value }) { it.toList() }.collect { apply() } }
        scope.launch { sound.balance.value.collect { balance.balance = it } }
        scope.launch { sound.bass.value.collect { balance.bass = it / 1000f } }
        scope.launch { sound.speed.value.collect { exo.setPlaybackSpeed(it) } }
    }

    /** After a crossfade: effects, balance and speed follow the new player. */
    fun retarget(player: ExoPlayer, processor: BalanceProcessor) {
        exo = player
        balance = processor
        processor.balance = sound.balance.value.value
        processor.bass = sound.bass.value.value / 1000f
        player.setPlaybackSpeed(sound.speed.value.value)
        session = 0
        onSession(player.audioSessionId)
    }

    /** ExoPlayer's audio session changed (a new audio track): effects belong to a session. */
    fun onSession(id: Int) {
        if (id == session || id == 0) return
        session = id
        release()
        eq = runCatching { Equalizer(0, id) }.onFailure { Log.w(TAG, "no equalizer", it) }.getOrNull()
        bass = runCatching { BassBoost(0, id) }.onFailure { Log.w(TAG, "no bass boost", it) }.getOrNull()
        loud = runCatching { LoudnessEnhancer(id) }.onFailure { Log.w(TAG, "no loudness", it) }.getOrNull()
        // Reverb as an insert effect on the player's own session: apps may no longer create effects on
        // the global output mix (session 0), so the old auxiliary reverb was never made.
        reverb = runCatching { PresetReverb(0, id) }.onFailure { Log.w(TAG, "no reverb", it) }.getOrNull()
        apply()
    }

    private fun apply() {
        runCatching {
            eq?.let { e ->
                e.enabled = sound.eqOn.value.value
                val range = e.bandLevelRange
                val centers = (0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 }
                // Map our five bands to the phone's bands by nearest centre frequency.
                val ours = listOf(60, 230, 910, 3600, 14000)
                centers.forEachIndexed { band, hz ->
                    val nearest = ours.indices.minBy { kotlin.math.abs(kotlin.math.ln(ours[it].toDouble()) - kotlin.math.ln(hz.coerceAtLeast(1).toDouble())) }
                    val level = sound.eqBands[nearest].value.value.coerceIn(range[0].toInt(), range[1].toInt())
                    e.setBandLevel(band.toShort(), level.toShort())
                }
            }
        }.onFailure { Log.w(TAG, "equalizer", it) }
        runCatching { bass?.let { b -> val s = sound.bass.value.value; b.enabled = s > 0; if (b.strengthSupported) b.setStrength(s.toShort()) } }
        runCatching { loud?.let { l -> val g = sound.loudness.value.value; l.setTargetGain(g); l.enabled = g > 0 } }
        runCatching {
            val preset = sound.reverb.value.value
            reverb?.let { r ->
                if (preset > 0) r.preset = preset.toShort()
                r.enabled = preset > 0
            }
        }.onFailure { Log.w(TAG, "reverb", it) }
    }

    private fun release() {
        listOf(eq, bass, loud, reverb).forEach { runCatching { it?.release() } }
        eq = null; bass = null; loud = null; reverb = null
    }

    private companion object { const val TAG = "DuetSound" }
}
