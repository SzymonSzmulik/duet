package app.duet.music.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stereo balance and bass boost for 16-bit stereo PCM. Balance: turning toward one side lowers the other channel
 * (the usual balance knob; the louder side stays at full level), [balance] is -1 (left) .. 1 (right). Bass: Duet's own
 * low shelf ([LowShelf]) up to [MAX_BASS_DB], [bass] 0..1, so the boost is audible on phones whose bass effect is weak
 * or missing.
 */
@OptIn(UnstableApi::class)
class BalanceProcessor : BaseAudioProcessor() {
    @Volatile var balance: Float = 0f
    @Volatile var bass: Float = 0f
    private var shelves = emptyArray<LowShelf>()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == 2) {
            shelves = Array(2) { LowShelf(inputAudioFormat.sampleRate) }
            inputAudioFormat
        } else AudioProcessor.AudioFormat.NOT_SET

    override fun onFlush() { shelves.forEach { it.reset() } }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val b = balance
        val left = if (b > 0f) 1f - b else 1f
        val right = if (b < 0f) 1f + b else 1f
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val db = bass * MAX_BASS_DB
        shelves.forEach { if (db == 0f && it.gainDb != 0.0) it.reset(); it.gainDb = db.toDouble() }
        while (input.remaining() >= 4) {
            out.putShort(shape(input.short * left, shelves[0], db).toInt().toShort())
            out.putShort(shape(input.short * right, shelves[1], db).toInt().toShort())
        }
        out.flip()
    }

    /** One sample through the bass shelf (when it is on) and the soft clip that keeps the boost from crackling. */
    private fun shape(sample: Float, shelf: LowShelf, db: Float): Float =
        if (db == 0f) sample else (softClip(shelf.process(sample / 32768.0)) * 32767.0).toFloat()

    companion object {
        /** Bass boost at 100%: strong, like a "bass booster" preset. */
        const val MAX_BASS_DB = 15f
    }
}
