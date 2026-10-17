// port-lint: source tests/opus-padding.rs
package io.github.kotlinmania.opus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpusPaddingTest {
    @Test
    fun testOverflow() {
        val packetSize = 16_909_318
        val channels = Channels.Stereo
        val frameSize = 5760

        val input = ByteArray(packetSize) { 0xFF.toByte() }
        val output = ShortArray(frameSize * 2)

        input[0] = 0xFF.toByte()
        input[1] = 0x41.toByte()
        input[input.size - 1] = 0x0B.toByte()

        val decoder = Decoder.new(48_000u, channels)
        val err =
            assertFailsWith<OpusError> {
                decoder.decode(input, output, false)
            }
        assertEquals(ErrorCode.InvalidPacket, err.code())
    }
}
