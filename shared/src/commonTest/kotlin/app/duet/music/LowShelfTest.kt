package app.duet.music

import app.duet.music.audio.LowShelf
import app.duet.music.audio.softClip
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LowShelfTest {
    /** The gain in dB the filter gives a sine of [hz], measured on the last second of a few seconds of it. */
    private fun measuredDb(hz: Double, shelfDb: Double): Double {
        val rate = 44100
        val f = LowShelf(rate).apply { gainDb = shelfDb }
        var peak = 0.0
        for (n in 0 until rate * 3) {
            val y = f.process(0.1 * sin(2 * PI * hz * n / rate))
            if (n > rate * 2) peak = maxOf(peak, abs(y))
        }
        return 20 * log10(peak / 0.1)
    }

    @Test
    fun boostsBassByTheAskedDbAndLeavesTrebleAlone() {
        assertTrue(abs(measuredDb(40.0, 12.0) - 12.0) < 1.0, "40 Hz should rise about 12 dB")
        assertTrue(abs(measuredDb(4000.0, 12.0)) < 0.5, "4 kHz should not change")
    }

    @Test
    fun zeroGainPassesTheSignalThrough() {
        assertEquals(0.0, measuredDb(60.0, 0.0), 0.01)
    }

    @Test
    fun softClipNeverExceedsOneAndKeepsQuietSoundsExact() {
        assertEquals(0.5, softClip(0.5))
        assertTrue(abs(softClip(3.0)) <= 1.0 && abs(softClip(-3.0)) <= 1.0)
    }
}
