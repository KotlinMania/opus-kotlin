package io.github.kotlinmania.opus

/**
 * A parsed Opus packet, returned from [parse].
 */
public class Packet(
    /** The TOC byte of the packet. */
    public val toc: UByte,
    /** The frames contained in the packet. */
    public val frames: List<ByteArray>,
    /** The offset into the packet at which the payload is located. */
    public val payloadOffset: Int,
)

internal class ParsedPacketInternal(
    val toc: UByte,
    val frames: List<ByteArray>,
    val payloadOffset: Int,
    val packetOffset: Int,
)

private fun parseFrameLength(
    data: ByteArray,
    offset: Int,
    maxBytes: Int,
    funcName: String,
): Pair<Int, Int> {
    if (maxBytes < 1) {
        throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
    }
    val b0 = data[offset].toInt() and 0xFF
    if (b0 < 252) {
        return Pair(b0, 1)
    }
    if (maxBytes < 2) {
        throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
    }
    val b1 = data[offset + 1].toInt() and 0xFF
    return Pair(4 * b1 + b0, 2)
}

internal fun encodeFrameLength(
    length: Int,
    out: ByteArray,
    offset: Int,
): Int =
    if (length < 252) {
        out[offset] = length.toByte()
        1
    } else {
        val low = 252 + (length and 0x3)
        val high = (length - low) shr 2
        out[offset] = low.toByte()
        out[offset + 1] = high.toByte()
        2
    }

internal fun parsePacketInternal(
    data: ByteArray,
    offset: Int,
    len: Int,
    selfDelimited: Boolean,
    funcName: String,
): ParsedPacketInternal {
    if (len < 1) {
        throw OpusError.fromCode(funcName, OPUS_BAD_ARG)
    }
    val toc = data[offset].toUByte()
    val tocInt = toc.toInt()
    val framesPerPacketSamples48k = getSamplesPerFrameFromToc(tocInt, 48_000)
    var pos = offset + 1
    var remaining = len - 1
    var pad = 0
    var cbr = false
    val frameCount: Int
    var lastSize = remaining

    when (tocInt and 0x3) {
        0 -> {
            frameCount = 1
            cbr = true
        }

        1 -> {
            frameCount = 2
            cbr = true
            if (!selfDelimited) {
                if (remaining and 0x1 != 0) {
                    throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
                }
                lastSize = remaining / 2
            }
        }

        2 -> {
            frameCount = 2
            cbr = false
        }

        else -> {
            if (remaining < 1) {
                throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
            }
            val ch = data[pos++].toInt() and 0xFF
            remaining--
            frameCount = ch and 0x3F
            if (frameCount <= 0 || frameCount * framesPerPacketSamples48k > 5760) {
                throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
            }
            if ((ch and 0x40) != 0) {
                var p: Int
                do {
                    if (remaining <= 0) {
                        throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
                    }
                    p = data[pos++].toInt() and 0xFF
                    remaining--
                    val tmp = if (p == 255) 254 else p
                    remaining -= tmp
                    pad += tmp
                } while (p == 255)
            }
            if (remaining < 0) {
                throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
            }
            cbr = (ch and 0x80) == 0
            if (!cbr) {
                lastSize = remaining
            } else if (!selfDelimited) {
                lastSize = remaining / frameCount
                if (lastSize * frameCount != remaining) {
                    throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
                }
            }
        }
    }

    val sizes = IntArray(frameCount)
    if (cbr) {
        if (selfDelimited) {
            val (sz, consumed) = parseFrameLength(data, pos, remaining, funcName)
            pos += consumed
            remaining -= consumed
            lastSize = sz
            if (lastSize * frameCount > remaining) {
                throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
            }
        }
        if (lastSize > 1275) {
            throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
        }
        for (i in 0 until frameCount) {
            sizes[i] = lastSize
        }
    } else {
        for (i in 0 until frameCount - 1) {
            val (sz, consumed) = parseFrameLength(data, pos, remaining, funcName)
            pos += consumed
            remaining -= consumed
            if (sz > 1275) {
                throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
            }
            sizes[i] = sz
            lastSize -= consumed + sz
        }
        if (lastSize < 0) {
            throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
        }
        if (selfDelimited) {
            val (sz, consumed) = parseFrameLength(data, pos, remaining, funcName)
            pos += consumed
            remaining -= consumed
            lastSize = sz
        }
        if (lastSize > 1275) {
            throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
        }
        sizes[frameCount - 1] = lastSize
    }

    val payloadOffset = pos - offset
    val frames = ArrayList<ByteArray>(frameCount)
    for (i in 0 until frameCount) {
        val sz = sizes[i]
        if (pos + sz - offset > len) {
            throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
        }
        frames.add(data.copyOfRange(pos, pos + sz))
        pos += sz
    }
    val packetOffset = pos + pad - offset
    if (packetOffset > len) {
        throw OpusError.fromCode(funcName, OPUS_INVALID_PACKET)
    }

    return ParsedPacketInternal(
        toc = toc,
        frames = frames,
        payloadOffset = payloadOffset,
        packetOffset = packetOffset,
    )
}

internal fun getSamplesPerFrameFromToc(
    tocByte: Int,
    sampleRate: Int,
): Int =
    if ((tocByte and 0x80) != 0) {
        val audioSize = (tocByte shr 3) and 0x3
        (sampleRate shl audioSize) / 400
    } else if ((tocByte and 0x60) == 0x60) {
        if ((tocByte and 0x08) != 0) sampleRate / 50 else sampleRate / 100
    } else {
        val audioSize = (tocByte shr 3) and 0x3
        if (audioSize == 3) sampleRate * 60 / 1000 else (sampleRate shl audioSize) / 100
    }

/**
 * Get the bandwidth of an Opus packet.
 */
public fun getBandwidth(packet: ByteArray): Bandwidth {
    if (packet.isEmpty()) {
        throw OpusError.badArg("opus_packet_get_bandwidth")
    }
    val b0 = packet[0].toInt() and 0xFF
    return if ((b0 and 0x80) != 0) {
        when ((b0 shr 3) and 0x3) {
            0 -> Bandwidth.Narrowband
            1 -> Bandwidth.Wideband
            2 -> Bandwidth.Superwideband
            else -> Bandwidth.Fullband
        }
    } else if ((b0 and 0x60) == 0x60) {
        if ((b0 and 0x10) != 0) Bandwidth.Fullband else Bandwidth.Superwideband
    } else {
        when ((b0 shr 5) and 0x3) {
            0 -> Bandwidth.Narrowband
            1 -> Bandwidth.Mediumband
            else -> Bandwidth.Wideband
        }
    }
}

/**
 * Get the number of channels from an Opus packet.
 */
public fun getNbChannels(packet: ByteArray): Channels {
    if (packet.isEmpty()) {
        throw OpusError.badArg("opus_packet_get_nb_channels")
    }
    return if ((packet[0].toInt() and 0x04) != 0) Channels.Stereo else Channels.Mono
}

/**
 * Get the number of frames in an Opus packet.
 */
public fun getNbFrames(packet: ByteArray): Int {
    if (packet.isEmpty()) {
        throw OpusError.fromCode("opus_packet_get_nb_frames", OPUS_BAD_ARG)
    }
    val count = packet[0].toInt() and 0x3
    if (count == 0) return 1
    if (count != 3) return 2
    if (packet.size < 2) {
        throw OpusError.fromCode("opus_packet_get_nb_frames", OPUS_INVALID_PACKET)
    }
    return packet[1].toInt() and 0x3F
}

/**
 * Get the number of samples per frame from an Opus packet.
 */
public fun getSamplesPerFrame(
    packet: ByteArray,
    sampleRate: UInt,
): Int {
    if (packet.isEmpty()) {
        throw OpusError.badArg("opus_packet_get_samples_per_frame")
    }
    return getSamplesPerFrameFromToc(packet[0].toInt() and 0xFF, sampleRate.toInt())
}

/**
 * Get the number of samples of an Opus packet.
 */
public fun getNbSamples(
    packet: ByteArray,
    sampleRate: UInt,
): Int {
    val frames = getNbFrames(packet)
    val samples = frames * getSamplesPerFrame(packet, sampleRate)
    if (samples * 25 > sampleRate.toInt() * 3) {
        throw OpusError.fromCode("opus_packet_get_nb_samples", OPUS_INVALID_PACKET)
    }
    return samples
}

/**
 * Parse an Opus packet into one or more frames.
 */
public fun parse(packet: ByteArray): Packet {
    val parsed = parsePacketInternal(packet, 0, packet.size, false, "opus_packet_parse")
    return Packet(
        toc = parsed.toc,
        frames = parsed.frames,
        payloadOffset = parsed.payloadOffset,
    )
}

/**
 * Pad a given Opus packet to a larger size.
 *
 * The packet will be extended from the first [prevLen] bytes of the
 * buffer into the rest of the available space.
 */
public fun pad(
    packet: ByteArray,
    prevLen: Int,
): Int {
    checkLen(prevLen)
    val newLen = packet.size
    if (prevLen < 1 || prevLen > newLen) {
        throw OpusError.fromCode("opus_packet_pad", OPUS_BAD_ARG)
    }
    if (prevLen == newLen) {
        return newLen
    }
    val parsed = parsePacketInternal(packet, 0, prevLen, false, "opus_packet_pad")
    val written =
        repacketizerOutRangeImpl(
            tocBase = parsed.toc.toInt() and 0xFC,
            frames = parsed.frames,
            begin = 0,
            end = parsed.frames.size,
            buffer = packet,
            offset = 0,
            maxLen = newLen,
            selfDelimited = false,
            padToMaxLen = true,
            funcName = "opus_packet_pad",
        )
    return written
}

/**
 * Remove all padding from a given Opus packet and rewrite the TOC sequence
 * to minimize space usage.
 */
public fun unpad(packet: ByteArray): Int = unpadSlice(packet, packet.size, "opus_packet_unpad")

internal fun unpadSlice(
    packet: ByteArray,
    len: Int,
    funcName: String,
): Int {
    if (len < 1) {
        throw OpusError.fromCode(funcName, OPUS_BAD_ARG)
    }
    val parsed = parsePacketInternal(packet, 0, len, false, funcName)
    return repacketizerOutRangeImpl(
        tocBase = parsed.toc.toInt() and 0xFC,
        frames = parsed.frames,
        begin = 0,
        end = parsed.frames.size,
        buffer = packet,
        offset = 0,
        maxLen = len,
        selfDelimited = false,
        padToMaxLen = false,
        funcName = funcName,
    )
}

/**
 * Pad a given Opus multi-stream packet to a larger size.
 */
public fun multistreamPad(
    packet: ByteArray,
    prevLen: Int,
    nbStreams: UByte,
): Int {
    checkLen(prevLen)
    val streams = nbStreams.toInt()
    if (prevLen < 1 || streams < 1 || prevLen > packet.size) {
        throw OpusError.fromCode("opus_multistream_packet_pad", OPUS_BAD_ARG)
    }
    if (prevLen == packet.size) {
        return packet.size
    }
    var pos = 0
    var remaining = prevLen
    repeat(streams - 1) {
        if (remaining <= 0) {
            throw OpusError.fromCode("opus_multistream_packet_pad", OPUS_INVALID_PACKET)
        }
        val parsed =
            parsePacketInternal(
                data = packet,
                offset = pos,
                len = remaining,
                selfDelimited = true,
                funcName = "opus_multistream_packet_pad",
            )
        pos += parsed.packetOffset
        remaining -= parsed.packetOffset
    }
    val lastParsed =
        parsePacketInternal(
            data = packet,
            offset = pos,
            len = remaining,
            selfDelimited = false,
            funcName = "opus_multistream_packet_pad",
        )
    repacketizerOutRangeImpl(
        tocBase = lastParsed.toc.toInt() and 0xFC,
        frames = lastParsed.frames,
        begin = 0,
        end = lastParsed.frames.size,
        buffer = packet,
        offset = pos,
        maxLen = packet.size - pos,
        selfDelimited = false,
        padToMaxLen = true,
        funcName = "opus_multistream_packet_pad",
    )
    return packet.size
}

/**
 * Remove all padding from a given Opus multi-stream packet and rewrite
 * the TOC sequence to minimize space usage.
 */
public fun multistreamUnpad(
    packet: ByteArray,
    nbStreams: UByte,
): Int {
    val streams = nbStreams.toInt()
    if (packet.isEmpty() || streams < 1) {
        throw OpusError.fromCode("opus_multistream_packet_unpad", OPUS_BAD_ARG)
    }
    var readPos = 0
    var writePos = 0
    var remaining = packet.size
    for (s in 0 until streams) {
        val selfDelimited = s < streams - 1
        if (remaining <= 0) {
            throw OpusError.fromCode("opus_multistream_packet_unpad", OPUS_INVALID_PACKET)
        }
        val parsed =
            parsePacketInternal(
                data = packet,
                offset = readPos,
                len = remaining,
                selfDelimited = selfDelimited,
                funcName = "opus_multistream_packet_unpad",
            )
        readPos += parsed.packetOffset
        remaining -= parsed.packetOffset
        val written =
            repacketizerOutRangeImpl(
                tocBase = parsed.toc.toInt() and 0xFC,
                frames = parsed.frames,
                begin = 0,
                end = parsed.frames.size,
                buffer = packet,
                offset = writePos,
                maxLen = packet.size - writePos,
                selfDelimited = selfDelimited,
                padToMaxLen = false,
                funcName = "opus_multistream_packet_unpad",
            )
        writePos += written
    }
    return writePos
}
