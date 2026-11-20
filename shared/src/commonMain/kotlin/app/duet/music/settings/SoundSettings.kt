package app.duet.music.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sound settings (Settings > Sound), persisted and live: the player applies each change at once.
 * Levels are in millibels (100 mB = 1 dB) where Android's effects use them.
 */
class SoundSettings(private val store: Settings = Settings()) {
    private fun <T> setting(key: String, default: T, read: (String, T) -> T, write: (String, T) -> Unit) = Setting(key, default, read, write)

    inner class Setting<T>(private val key: String, default: T, read: (String, T) -> T, private val write: (String, T) -> Unit) {
        private val flow = MutableStateFlow(read(key, default))
        val value: StateFlow<T> = flow.asStateFlow()
        fun set(v: T) { write(key, v); flow.value = v }
    }

    private fun int(key: String, default: Int) = setting(key, default, store::getInt, store::putInt)
    private fun float(key: String, default: Float) = setting(key, default, store::getFloat, store::putFloat)
    private fun bool(key: String, default: Boolean) = setting(key, default, store::getBoolean, store::putBoolean)

    val eqOn = bool("sound.eq.on", false)
    /** Five bands (about 60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz), each -1200..1200 mB. */
    val eqBands = List(5) { int("sound.eq.band$it", 0) }
    /** 0..1000 (Android BassBoost strength). */
    val bass = int("sound.bass", 0)
    /** Loudness boost, 0..1000 mB. */
    val loudness = int("sound.loudness", 0)
    /** [Reverb] preset. */
    val reverb = int("sound.reverb", 0)
    /** Stereo balance, -1 (left) .. 1 (right). */
    val balance = float("sound.balance", 0f)
    /** Playback speed, 0.5..2. */
    val speed = float("sound.speed", 1f)
    /** Crossfade between songs, seconds (0 = off): the next song overlaps the end of the current one. */
    val fadeSeconds = int("sound.fade", 0)
    /** Use the phone's spatializer on headphones (Android 12L+). */
    val spatial = bool("sound.spatial", false)
    /** Stream quality on Wi-Fi and on mobile data ([Quality]). */
    val wifiQuality = int("sound.quality.wifi", Quality.High.ordinal)
    val mobileQuality = int("sound.quality.mobile", Quality.DataSaver.ordinal)
    /** Play Spotify songs from the matching YouTube Music song, so quality and effects apply to them. */
    val spotifyViaYt = bool("sound.spotifyViaYt", false)

    fun resetEffects() {
        eqOn.set(false); eqBands.forEach { it.set(0) }; bass.set(0); loudness.set(0); reverb.set(0); balance.set(0f); speed.set(1f)
    }

    /** Stored by ordinal, so new values go at the end; [shown] is the on-screen order. */
    enum class Quality(val label: String) {
        High("High"), DataSaver("Data saver"), Normal("Normal");
        companion object { val shown = listOf(High, Normal, DataSaver) }
    }

    /** Android PresetReverb presets, in their order. */
    enum class Reverb(val label: String) { Off("Off"), SmallRoom("Small room"), MediumRoom("Medium room"), LargeRoom("Large room"), MediumHall("Medium hall"), LargeHall("Large hall"), Plate("Plate") }

    companion object {
        val EQ_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
        val EQ_PRESETS = mapOf(
            "Flat" to listOf(0, 0, 0, 0, 0),
            "Bass" to listOf(600, 400, 0, 0, 0),
            "Vocal" to listOf(-200, 0, 400, 300, 0),
            "Treble" to listOf(0, 0, 0, 400, 600),
            "Rock" to listOf(500, 200, -200, 300, 500),
            "Pop" to listOf(-100, 300, 500, 200, -100),
        )
    }
}
