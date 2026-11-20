package app.duet.music.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * A low-shelf filter (RBJ biquad): raises everything under [cornerHz] by [gainDb] and leaves the rest alone. This is
 * Duet's own bass boost, so it works on every device and platform whether or not the system has a bass effect.
 * One instance per audio channel; samples are -1..1.
 */
class LowShelf(private val sampleRate: Int, private val cornerHz: Double = 120.0) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    var gainDb: Double = 0.0
        set(value) {
            if (value == field) return
            field = value
            val a = 10.0.pow(value / 40)
            val w = 2 * PI * cornerHz / sampleRate
            val alpha = sin(w) / 2 * sqrt(2.0)
            val cosW = cos(w)
            val twoSqrtAAlpha = 2 * sqrt(a) * alpha
            val a0 = (a + 1) + (a - 1) * cosW + twoSqrtAAlpha
            b0 = a * ((a + 1) - (a - 1) * cosW + twoSqrtAAlpha) / a0
            b1 = 2 * a * ((a - 1) - (a + 1) * cosW) / a0
            b2 = a * ((a + 1) - (a - 1) * cosW - twoSqrtAAlpha) / a0
            a1 = -2 * ((a - 1) + (a + 1) * cosW) / a0
            a2 = ((a + 1) + (a - 1) * cosW - twoSqrtAAlpha) / a0
        }

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }

    /** Forget past samples (after a seek, or when the boost was off). */
    fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }
}

/** Keeps a boosted signal from clipping harshly: untouched up to 0.8, then eased towards 1. */
fun softClip(x: Double): Double = if (abs(x) <= 0.8) x else sign(x) * (0.8 + 0.2 * tanh((abs(x) - 0.8) / 0.2))
