package io.github.kotlinmania.opus

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Soft-clipping to bring a float signal within the `[-1, 1]` range.
 */
public class SoftClip private constructor(
    private val channels: Channels,
) {
    private val memory: FloatArray = FloatArray(2)

    /** Apply soft-clipping to a float signal in-place. */
    public fun apply(signal: FloatArray) {
        val c = channels.count
        val frameSize = signal.size / c
        if (frameSize <= 0) return

        for (ch in 0 until c) {
            var a = memory[ch]
            var endIdx = frameSize
            for (i in 0 until frameSize) {
                val idx = ch + i * c
                if (abs(signal[idx]) > 1.0f) {
                    break
                }
                if (i == frameSize - 1) {
                    endIdx = 0
                }
            }
            for (i in 0 until endIdx) {
                val idx = ch + i * c
                var x = signal[idx] + a
                var x2 = x * x
                a += x * (1.0f - x2) * 0.125f
                a = max(-0.5f, min(0.5f, a))
                x = signal[idx] + a
                x2 = x * x
                signal[idx] = x * (1.5f - 0.5f * x2).coerceIn(-1.0f, 1.0f)
                if (abs(signal[idx]) > 1.0f) {
                    signal[idx] = if (signal[idx] > 0.0f) 1.0f else -1.0f
                }
            }
            memory[ch] = a
        }
    }

    public companion object {
        /** Initialize a new soft-clipping state for [channels]. */
        public fun new(channels: Channels): SoftClip = SoftClip(channels)
    }
}
