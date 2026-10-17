package io.github.kotlinmania.opus

internal fun repacketizerOutRangeImpl(
    tocBase: Int,
    frames: List<ByteArray>,
    begin: Int,
    end: Int,
    buffer: ByteArray,
    offset: Int,
    maxLen: Int,
    selfDelimited: Boolean,
    padToMaxLen: Boolean,
    funcName: String,
): Int {
    val count = end - begin
    if (begin < 0 || end > frames.size || count <= 0 || count > 48) {
        throw OpusError.fromCode(funcName, OPUS_BAD_ARG)
    }

    var cbr = true
    val firstLen = frames[begin].size
    var totPayload = 0
    for (i in begin until end) {
        val sz = frames[i].size
        if (sz != firstLen) {
            cbr = false
        }
        totPayload += sz
    }

    val headerBuf = ByteArray(128)
    var headerLen = 0
    var useCode3 = count >= 3 || padToMaxLen

    if (!useCode3) {
        if (count == 1) {
            headerBuf[headerLen++] = (tocBase and 0xFC).toByte()
            if (selfDelimited) {
                headerLen += encodeFrameLength(firstLen, headerBuf, headerLen)
            }
        } else if (cbr) {
            headerBuf[headerLen++] = ((tocBase and 0xFC) or 1).toByte()
            if (selfDelimited) {
                headerLen += encodeFrameLength(firstLen, headerBuf, headerLen)
            }
        } else {
            headerBuf[headerLen++] = ((tocBase and 0xFC) or 2).toByte()
            headerLen += encodeFrameLength(frames[begin].size, headerBuf, headerLen)
            if (selfDelimited) {
                headerLen += encodeFrameLength(frames[begin + 1].size, headerBuf, headerLen)
            }
        }
    }

    var vbrLengthsBytes = 0
    if (useCode3) {
        if (!cbr) {
            val tmp = ByteArray(4)
            for (i in begin until end - 1) {
                vbrLengthsBytes += encodeFrameLength(frames[i].size, tmp, 0)
            }
            if (selfDelimited) {
                vbrLengthsBytes += encodeFrameLength(frames[end - 1].size, tmp, 0)
            }
        } else if (selfDelimited) {
            val tmp = ByteArray(4)
            vbrLengthsBytes += encodeFrameLength(firstLen, tmp, 0)
        }
    }

    val baseCode3Len = 2 + vbrLengthsBytes + totPayload
    val unpaddedLen = if (useCode3) baseCode3Len else headerLen + totPayload

    if (padToMaxLen && maxLen > unpaddedLen && !useCode3) {
        useCode3 = true
        if (!cbr) {
            val tmp = ByteArray(4)
            vbrLengthsBytes = 0
            for (i in begin until end - 1) {
                vbrLengthsBytes += encodeFrameLength(frames[i].size, tmp, 0)
            }
            if (selfDelimited) {
                vbrLengthsBytes += encodeFrameLength(frames[end - 1].size, tmp, 0)
            }
        } else if (selfDelimited) {
            val tmp = ByteArray(4)
            vbrLengthsBytes = encodeFrameLength(firstLen, tmp, 0)
        }
    }

    val finalBaseLen = if (useCode3) 2 + vbrLengthsBytes + totPayload else headerLen + totPayload
    if (finalBaseLen > maxLen) {
        throw OpusError.fromCode(funcName, OPUS_BUFFER_TOO_SMALL)
    }

    val extraBytes = if (padToMaxLen) maxLen - finalBaseLen else 0
    if (useCode3) {
        headerLen = 0
        headerBuf[headerLen++] = ((tocBase and 0xFC) or 3).toByte()
        var ch = count and 0x3F
        if (!cbr) {
            ch = ch or 0x80
        }
        var padZeroBytes = 0
        if (extraBytes > 0) {
            ch = ch or 0x40
            headerBuf[headerLen++] = ch.toByte()
            var remPad = extraBytes - 1
            while (remPad >= 255) {
                headerBuf[headerLen++] = 255.toByte()
                remPad -= 255
                padZeroBytes += 254
            }
            headerBuf[headerLen++] = remPad.toByte()
            padZeroBytes += remPad
        } else {
            headerBuf[headerLen++] = ch.toByte()
        }

        if (!cbr) {
            for (i in begin until end - 1) {
                headerLen += encodeFrameLength(frames[i].size, headerBuf, headerLen)
            }
            if (selfDelimited) {
                headerLen += encodeFrameLength(frames[end - 1].size, headerBuf, headerLen)
            }
        } else if (selfDelimited) {
            headerLen += encodeFrameLength(firstLen, headerBuf, headerLen)
        }

        val totalOut = headerLen + totPayload + padZeroBytes
        if (totalOut > maxLen) {
            throw OpusError.fromCode(funcName, OPUS_BUFFER_TOO_SMALL)
        }
        val payloads = ByteArray(totPayload)
        var pOff = 0
        for (i in begin until end) {
            val f = frames[i]
            f.copyInto(payloads, pOff)
            pOff += f.size
        }
        headerBuf.copyInto(buffer, offset, 0, headerLen)
        payloads.copyInto(buffer, offset + headerLen)
        for (i in 0 until padZeroBytes) {
            buffer[offset + headerLen + totPayload + i] = 0
        }
        return totalOut
    } else {
        val payloads = ByteArray(totPayload)
        var pOff = 0
        for (i in begin until end) {
            val f = frames[i]
            f.copyInto(payloads, pOff)
            pOff += f.size
        }
        headerBuf.copyInto(buffer, offset, 0, headerLen)
        payloads.copyInto(buffer, offset + headerLen)
        return headerLen + totPayload
    }
}

/**
 * A repacketizer used to merge together or split apart multiple Opus packets.
 */
public class Repacketizer private constructor() {
    internal var toc: UByte = 0u
    internal val frames: MutableList<ByteArray> = ArrayList()
    internal var samplesPerFrame48k: Int = 0

    internal fun initState() {
        toc = 0u
        frames.clear()
        samplesPerFrame48k = 0
    }

    /** Shortcut to combine several smaller packets into one larger one. */
    public fun combine(
        input: List<ByteArray>,
        output: ByteArray,
    ): Int {
        val state = begin()
        for (packet in input) {
            state.cat(packet)
        }
        return state.out(output)
    }

    /** Begin using the repacketizer. */
    public fun begin(): RepacketizerState {
        initState()
        return RepacketizerState(this)
    }

    public companion object {
        /** Create and initialize a repacketizer. */
        public fun new(): Repacketizer = Repacketizer()
    }
}

/**
 * An in-progress repacketization.
 */
public class RepacketizerState internal constructor(
    private val rp: Repacketizer,
) {
    /** Add a packet to the current repacketizer state. */
    public fun cat(packet: ByteArray) {
        if (packet.isEmpty()) {
            throw OpusError.fromCode("opus_repacketizer_cat", OPUS_INVALID_PACKET)
        }
        val packetToc = packet[0].toUByte()
        if (rp.frames.isEmpty()) {
            rp.toc = packetToc
            rp.samplesPerFrame48k = getSamplesPerFrameFromToc(packetToc.toInt(), 48_000)
        } else if ((rp.toc.toInt() and 0xFC) != (packetToc.toInt() and 0xFC)) {
            throw OpusError.fromCode("opus_repacketizer_cat", OPUS_INVALID_PACKET)
        }
        val parsed = parsePacketInternal(packet, 0, packet.size, false, "opus_repacketizer_cat")
        if ((rp.frames.size + parsed.frames.size) * rp.samplesPerFrame48k > 5760) {
            throw OpusError.fromCode("opus_repacketizer_cat", OPUS_INVALID_PACKET)
        }
        for (frame in parsed.frames) {
            rp.frames.add(frame)
        }
    }

    /** Add a packet to the current repacketizer state, returning this state for chaining. */
    public fun catMove(packet: ByteArray): RepacketizerState {
        cat(packet)
        return this
    }

    /** Get the total number of frames contained in packet data submitted so far via [cat]. */
    public fun getNbFrames(): Int = rp.frames.size

    /**
     * Construct a new packet from data previously submitted via [cat].
     *
     * All previously submitted frames are used.
     */
    public fun out(buffer: ByteArray): Int =
        outRange(
            begin = 0,
            end = rp.frames.size,
            buffer = buffer,
        )

    /**
     * Construct a new packet from data previously submitted via [cat], with
     * a manually specified subrange `[begin, end)`.
     */
    public fun outRange(
        begin: Int,
        end: Int,
        buffer: ByteArray,
    ): Int {
        checkLen(begin)
        checkLen(end)
        return repacketizerOutRangeImpl(
            tocBase = rp.toc.toInt() and 0xFC,
            frames = rp.frames,
            begin = begin,
            end = end,
            buffer = buffer,
            offset = 0,
            maxLen = buffer.size,
            selfDelimited = false,
            padToMaxLen = false,
            funcName = "opus_repacketizer_out_range",
        )
    }
}
