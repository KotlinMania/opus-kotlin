// port-lint: source tests/tests.rs
package io.github.kotlinmania.opus

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val MONO_20MS: Int = 48_000 * 20 / 1000

private fun checkAscii(s: String): String {
    for (b in s.encodeToByteArray()) {
        val code = b.toInt() and 0xFF
        assertTrue(code < 0x80, "Non-ASCII character in string")
        assertTrue(code > 0x00, "NUL in string")
    }
    return s
}

class OpusTestsTest {
    @Test
    fun stringsAscii() {
        assertTrue(checkAscii(version()).isNotEmpty())
        assertEquals(8, ErrorCode.entries.size)
        for (code in ErrorCode.entries) {
            assertTrue(checkAscii(code.errorDescription()).isNotEmpty())
        }
    }

    @Test
    fun encodeMono() {
        val encoder = Encoder.new(48_000u, Channels.Mono, Application.Audio)

        val output = ByteArray(256)
        val len1 = encoder.encode(ShortArray(MONO_20MS), output)
        assertContentEquals(
            byteArrayOf(248.toByte(), 255.toByte(), 254.toByte()),
            output.copyOfRange(0, len1),
        )

        val len2 = encoder.encode(ShortArray(MONO_20MS), output)
        assertContentEquals(
            byteArrayOf(248.toByte(), 255.toByte(), 254.toByte()),
            output.copyOfRange(0, len2),
        )

        val len3 = encoder.encode(ShortArray(MONO_20MS) { 1 }, output)
        assertTrue(len3 > 190 && len3 < 220)

        val len4 = encoder.encode(ShortArray(MONO_20MS), output)
        assertTrue(len4 > 170 && len4 < 190)

        val myvec = encoder.encodeVec(ShortArray(MONO_20MS) { 1 }, output.size)
        assertTrue(myvec.size > 120 && myvec.size < 140)
    }

    @Test
    fun encodeStereo() {
        val encoder = Encoder.new(48_000u, Channels.Stereo, Application.Audio)

        val output = ByteArray(512)
        val len1 = encoder.encode(ShortArray(2 * MONO_20MS), output)
        assertContentEquals(
            byteArrayOf(252.toByte(), 255.toByte(), 254.toByte()),
            output.copyOfRange(0, len1),
        )

        val len2 = encoder.encode(ShortArray(4 * MONO_20MS), output)
        assertContentEquals(
            byteArrayOf(253.toByte(), 255.toByte(), 254.toByte(), 255.toByte(), 254.toByte()),
            output.copyOfRange(0, len2),
        )

        val len3 = encoder.encode(ShortArray(2 * MONO_20MS) { 17 }, output)
        assertTrue(len3 > 240)

        val len4 = encoder.encode(ShortArray(2 * MONO_20MS), output)
        assertTrue(len4 > 240)

        val len5 = encoder.encode(ShortArray(2 * MONO_20MS) { 95 }, ByteArray(20))
        assertTrue(len5 <= 20)

        val myvec = encoder.encodeVec(ShortArray(2 * MONO_20MS) { 95 }, 20)
        assertTrue(myvec.size <= 20)
    }

    @Test
    fun encodeDecodeStereo() {
        val opusEncoder = Encoder.new(48_000u, Channels.Stereo, Application.Voip)
        val opusDecoder = Decoder.new(48_000u, Channels.Stereo)
        val pcmRawData = ShortArray(MONO_20MS * 2) { 17 }
        pcmRawData[1] = 1

        val encodedOpus = ByteArray(1500)
        val size = opusEncoder.encode(pcmRawData, encodedOpus)
        val packet = encodedOpus.copyOfRange(0, size)

        assertEquals(MONO_20MS, opusDecoder.getNbSamples(packet))
        assertEquals(MONO_20MS, getNbSamples(packet, 48_000u))
        assertEquals(1, getNbFrames(packet))
        assertEquals(Channels.Stereo, getNbChannels(packet))
        assertEquals(MONO_20MS, getSamplesPerFrame(packet, 48_000u))

        val output = ShortArray(MONO_20MS * 2)
        assertEquals(MONO_20MS, opusDecoder.decode(packet, output, false))
    }

    @Test
    fun encodeBadRate() {
        val err =
            assertFailsWith<OpusError> {
                Encoder.new(48_001u, Channels.Mono, Application.Audio)
            }
        assertEquals(ErrorCode.BadArg, err.code())
    }

    @Test
    fun encodeBadBuffer() {
        val encoder = Encoder.new(48_000u, Channels.Stereo, Application.Audio)
        val err =
            assertFailsWith<OpusError> {
                encoder.encode(ShortArray(2 * MONO_20MS) { 1 }, ByteArray(0))
            }
        assertEquals(ErrorCode.BadArg, err.code())
    }

    @Test
    fun repacketizer() {
        val rp = Repacketizer.new()
        val out = ByteArray(256)
        val expected3 =
            byteArrayOf(
                251.toByte(),
                3.toByte(),
                255.toByte(),
                254.toByte(),
                255.toByte(),
                254.toByte(),
                255.toByte(),
                254.toByte(),
            )

        repeat(2) {
            val packet1 =
                byteArrayOf(
                    249.toByte(),
                    255.toByte(),
                    254.toByte(),
                    255.toByte(),
                    254.toByte(),
                )
            val packet2 = byteArrayOf(248.toByte(), 255.toByte(), 254.toByte())

            val state = rp.begin()
            state.cat(packet1)
            state.cat(packet2)
            val len = state.out(out)
            assertContentEquals(expected3, out.copyOfRange(0, len))
        }
        repeat(2) {
            val packet1 = byteArrayOf(248.toByte(), 255.toByte(), 254.toByte())
            val state1 = rp.begin().catMove(packet1)
            val packet2 =
                byteArrayOf(
                    249.toByte(),
                    255.toByte(),
                    254.toByte(),
                    255.toByte(),
                    254.toByte(),
                )
            val state2 = state1.catMove(packet2)
            val len = state2.out(out)
            assertContentEquals(expected3, out.copyOfRange(0, len))
        }
        repeat(2) {
            val len =
                rp.combine(
                    listOf(
                        byteArrayOf(
                            249.toByte(),
                            255.toByte(),
                            254.toByte(),
                            255.toByte(),
                            254.toByte(),
                        ),
                        byteArrayOf(248.toByte(), 255.toByte(), 254.toByte()),
                    ),
                    out,
                )
            assertContentEquals(expected3, out.copyOfRange(0, len))
        }
        repeat(2) {
            val len =
                rp
                    .begin()
                    .catMove(byteArrayOf(248.toByte(), 255.toByte(), 254.toByte()))
                    .catMove(byteArrayOf(248.toByte(), 71.toByte(), 71.toByte()))
                    .out(out)
            assertContentEquals(
                byteArrayOf(249.toByte(), 255.toByte(), 254.toByte(), 71.toByte(), 71.toByte()),
                out.copyOfRange(0, len),
            )
        }
    }
}
