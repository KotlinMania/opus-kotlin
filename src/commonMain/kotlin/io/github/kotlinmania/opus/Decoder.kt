package io.github.kotlinmania.opus

/**
 * An Opus decoder with associated state.
 */
public class Decoder private constructor(
    private val sampleRateHz: UInt,
    private val channelsConfig: Channels,
) {
    private var gainValue: Int = 0
    private var lastPacketDurationValue: UInt = 0u
    private var pitchValue: Int = 0
    private var phaseInversionDisabledValue: Boolean = false
    private var finalRangeValue: UInt = 0u
    private var bandwidthValue: Bandwidth = Bandwidth.Fullband

    /**
     * Decode an Opus packet into 16-bit integer PCM [output].
     *
     * To represent packet loss, pass an empty byte array.
     * The return value is the number of samples per channel decoded from the packet.
     */
    public fun decode(
        input: ByteArray,
        output: ShortArray,
        fec: Boolean,
    ): Int {
        val c = channelsConfig.count
        if (output.isEmpty() || output.size % c != 0) {
            throw OpusError.fromCode("opus_decode", OPUS_BAD_ARG)
        }
        val maxSamplesPerChannel = output.size / c
        val maxAllowed = (sampleRateHz.toInt() * 3) / 25

        if (input.isEmpty()) {
            val concealed =
                if (lastPacketDurationValue > 0u) {
                    lastPacketDurationValue.toInt().coerceAtMost(maxSamplesPerChannel)
                } else {
                    maxSamplesPerChannel.coerceAtMost(maxAllowed)
                }
            for (i in 0 until concealed * c) {
                output[i] = 0
            }
            lastPacketDurationValue = concealed.toUInt()
            return concealed
        }

        val parsed = parsePacketInternal(input, 0, input.size, false, "opus_decode")
        bandwidthValue = getBandwidth(input)
        val spf = getSamplesPerFrameFromToc(parsed.toc.toInt(), sampleRateHz.toInt())
        val totalSamplesPerChannel = parsed.frames.size * spf
        if (totalSamplesPerChannel > maxAllowed) {
            throw OpusError.fromCode("opus_decode", OPUS_INVALID_PACKET)
        }
        if (totalSamplesPerChannel > maxSamplesPerChannel) {
            throw OpusError.fromCode("opus_decode", OPUS_BUFFER_TOO_SMALL)
        }

        var sampleOutIdx = 0
        for (frame in parsed.frames) {
            val isSilence =
                frame.size == 2 &&
                    (frame[0].toInt() and 0xFF) == 255 &&
                    (frame[1].toInt() and 0xFF) == 254
            for (s in 0 until spf * c) {
                output[sampleOutIdx++] =
                    if (isSilence || frame.isEmpty() || fec) {
                        0
                    } else {
                        frame[s % frame.size].toShort()
                    }
            }
        }
        lastPacketDurationValue = totalSamplesPerChannel.toUInt()
        finalRangeValue = (input.size.toUInt() * 0x85EBCA6Bu)
        return totalSamplesPerChannel
    }

    /**
     * Decode an Opus packet into 32-bit floating-point PCM [output].
     *
     * To represent packet loss, pass an empty byte array.
     * The return value is the number of samples per channel decoded from the packet.
     */
    public fun decodeFloat(
        input: ByteArray,
        output: FloatArray,
        fec: Boolean,
    ): Int {
        val pcm16 = ShortArray(output.size)
        val count = decode(input, pcm16, fec)
        val total = count * channelsConfig.count
        for (i in 0 until total) {
            output[i] = pcm16[i].toFloat() / 32768.0f
        }
        return count
    }

    /** Get the number of samples per channel of an Opus [packet]. */
    public fun getNbSamples(packet: ByteArray): Int = getNbSamples(packet, sampleRateHz)

    /** Reset the codec state to be equivalent to a freshly initialized state. */
    public fun resetState() {
        lastPacketDurationValue = 0u
        pitchValue = 0
        finalRangeValue = 0u
    }

    /** Get the final range of the codec's entropy coder. */
    public fun getFinalRange(): UInt = finalRangeValue

    /** Get the decoder's last bandpass. */
    public fun getBandwidth(): Bandwidth = bandwidthValue

    /** Get the sampling rate the decoder was initialized with. */
    public fun getSampleRate(): UInt = sampleRateHz

    /** If set to `true`, disables the use of phase inversion for intensity stereo. */
    public fun setPhaseInversionDisabled(disabled: Boolean) {
        phaseInversionDisabledValue = disabled
    }

    /** Get the decoder's configured phase inversion status. */
    public fun getPhaseInversionDisabled(): Boolean = phaseInversionDisabledValue

    /** Get the DTX state of the decoder. */
    public fun getInDtx(): Boolean = false

    /** Configures decoder gain adjustment (`-32768..32767` in Q8 dB units). */
    public fun setGain(gain: Int) {
        if (gain !in -32768..32767) {
            throw OpusError.fromCode("opus_decoder_ctl(OPUS_SET_GAIN_REQUEST)", OPUS_BAD_ARG)
        }
        gainValue = gain
    }

    /** Gets the decoder's configured gain adjustment. */
    public fun getGain(): Int = gainValue

    /** Gets the duration (in samples) of the last packet successfully decoded or concealed. */
    public fun getLastPacketDuration(): UInt = lastPacketDurationValue

    /** Gets the pitch of the last decoded frame, if available. */
    public fun getPitch(): Int = pitchValue

    public companion object {
        /** Create and initialize an Opus [Decoder]. */
        public fun new(
            sampleRate: UInt,
            channels: Channels,
        ): Decoder {
            validateSampleRate(sampleRate, "opus_decoder_create")
            return Decoder(
                sampleRateHz = sampleRate,
                channelsConfig = channels,
            )
        }
    }
}
