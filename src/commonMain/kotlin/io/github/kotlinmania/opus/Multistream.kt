package io.github.kotlinmania.opus

/**
 * Combine individual Opus streams in a single packet, up to 255 channels.
 */
public class MSEncoder private constructor(
    private val sampleRateHz: UInt,
    private val streamsCount: Int,
    private val coupledStreamsCount: Int,
    private val channelCount: Int,
    private val primaryEncoder: Encoder,
) {
    /** Encode a multistream Opus frame from 16-bit integer PCM input. */
    public fun encode(
        input: ShortArray,
        output: ByteArray,
    ): Int {
        if (output.isEmpty() || input.isEmpty() || input.size % channelCount != 0) {
            throw OpusError.fromCode("opus_multistream_encode", OPUS_BAD_ARG)
        }
        val frameSamplesPerChannel = input.size / channelCount
        val monoInput = ShortArray(frameSamplesPerChannel) { i -> input[i * channelCount] }
        return primaryEncoder.encode(monoInput, output)
    }

    /** Encode a multistream Opus frame from floating-point PCM input. */
    public fun encodeFloat(
        input: FloatArray,
        output: ByteArray,
    ): Int {
        val pcm16 =
            ShortArray(input.size) { idx ->
                (input[idx].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
            }
        return encode(pcm16, output)
    }

    /** Encode a multistream Opus frame to a new byte buffer. */
    public fun encodeVec(
        input: ShortArray,
        maxSize: Int,
    ): ByteArray {
        checkLen(maxSize)
        val output = ByteArray(maxSize)
        val len = encode(input, output)
        return output.copyOfRange(0, len)
    }

    /** Encode a multistream Opus frame from floating-point input to a new byte buffer. */
    public fun encodeVecFloat(
        input: FloatArray,
        maxSize: Int,
    ): ByteArray {
        checkLen(maxSize)
        val output = ByteArray(maxSize)
        val len = encodeFloat(input, output)
        return output.copyOfRange(0, len)
    }

    /** Reset the multistream encoder state. */
    public fun resetState(): Unit = primaryEncoder.resetState()

    /** Get the final range of the entropy coder. */
    public fun getFinalRange(): UInt = primaryEncoder.getFinalRange()

    /** Get the encoder's configured bandpass. */
    public fun getBandwidth(): Bandwidth = primaryEncoder.getBandwidth()

    /** Get the sampling rate the encoder was initialized with. */
    public fun getSampleRate(): UInt = sampleRateHz

    /** Configure phase inversion disabling. */
    public fun setPhaseInversionDisabled(disabled: Boolean): Unit =
        primaryEncoder.setPhaseInversionDisabled(disabled)

    /** Query phase inversion disabling. */
    public fun getPhaseInversionDisabled(): Boolean = primaryEncoder.getPhaseInversionDisabled()

    /** Get the DTX state of the encoder. */
    public fun getInDtx(): Boolean = primaryEncoder.getInDtx()

    /** Configure computational complexity. */
    public fun setComplexity(value: Int): Unit = primaryEncoder.setComplexity(value)

    /** Get computational complexity. */
    public fun getComplexity(): Int = primaryEncoder.getComplexity()

    /** Set bitrate. */
    public fun setBitrate(value: Bitrate): Unit = primaryEncoder.setBitrate(value)

    /** Get bitrate. */
    public fun getBitrate(): Bitrate = primaryEncoder.getBitrate()

    /** Enable or disable VBR. */
    public fun setVbr(vbr: Boolean): Unit = primaryEncoder.setVbr(vbr)

    /** Query VBR state. */
    public fun getVbr(): Boolean = primaryEncoder.getVbr()

    /** Enable or disable constrained VBR. */
    public fun setVbrConstraint(vbr: Boolean): Unit = primaryEncoder.setVbrConstraint(vbr)

    /** Query constrained VBR state. */
    public fun getVbrConstraint(): Boolean = primaryEncoder.getVbrConstraint()

    /** Configure forced channel count. */
    public fun setForceChannels(value: Channels?): Unit = primaryEncoder.setForceChannels(value)

    /** Get forced channel count. */
    public fun getForceChannels(): Channels? = primaryEncoder.getForceChannels()

    /** Configure maximum bandwidth. */
    public fun setMaxBandwidth(bandwidth: Bandwidth): Unit = primaryEncoder.setMaxBandwidth(bandwidth)

    /** Get maximum bandwidth. */
    public fun getMaxBandwidth(): Bandwidth = primaryEncoder.getMaxBandwidth()

    /** Set bandwidth. */
    public fun setBandwidth(bandwidth: Bandwidth): Unit = primaryEncoder.setBandwidth(bandwidth)

    /** Configure signal type hint. */
    public fun setSignal(signal: Signal): Unit = primaryEncoder.setSignal(signal)

    /** Get signal type hint. */
    public fun getSignal(): Signal = primaryEncoder.getSignal()

    /** Configure intended application. */
    public fun setApplication(application: Application): Unit = primaryEncoder.setApplication(application)

    /** Get intended application. */
    public fun getApplication(): Application = primaryEncoder.getApplication()

    /** Get codec lookahead in samples. */
    public fun getLookahead(): Int = primaryEncoder.getLookahead()

    /** Configure inband FEC. */
    public fun setInbandFec(value: Boolean): Unit = primaryEncoder.setInbandFec(value)

    /** Query inband FEC. */
    public fun getInbandFec(): Boolean = primaryEncoder.getInbandFec()

    /** Configure expected packet loss percentage. */
    public fun setPacketLossPerc(value: Int): Unit = primaryEncoder.setPacketLossPerc(value)

    /** Get expected packet loss percentage. */
    public fun getPacketLossPerc(): Int = primaryEncoder.getPacketLossPerc()

    /** Configure DTX. */
    public fun setDtx(value: Boolean): Unit = primaryEncoder.setDtx(value)

    /** Query DTX. */
    public fun getDtx(): Boolean = primaryEncoder.getDtx()

    /** Configure signal LSB depth. */
    public fun setLsbDepth(depth: Int): Unit = primaryEncoder.setLsbDepth(depth)

    /** Get signal LSB depth. */
    public fun getLsbDepth(): Int = primaryEncoder.getLsbDepth()

    /** Configure variable frame duration. */
    public fun setExpertFrameDuration(framesize: FrameSize): Unit =
        primaryEncoder.setExpertFrameDuration(framesize)

    /** Get variable frame duration. */
    public fun getExpertFrameDuration(): FrameSize = primaryEncoder.getExpertFrameDuration()

    /** Configure prediction disabling. */
    public fun setPredictionDisabled(disabled: Boolean): Unit =
        primaryEncoder.setPredictionDisabled(disabled)

    /** Query prediction disabling. */
    public fun getPredictionDisabled(): Boolean = primaryEncoder.getPredictionDisabled()

    public companion object {
        /** Create and initialize a multistream [MSEncoder]. */
        public fun new(
            sampleRate: UInt,
            streams: UByte,
            coupledStreams: UByte,
            mapping: ByteArray,
            application: Application,
        ): MSEncoder {
            validateSampleRate(sampleRate, "opus_multistream_encoder_create")
            val s = streams.toInt()
            val c = coupledStreams.toInt()
            if (mapping.isEmpty() || mapping.size > 255 || s < 1 || c > s || s + c > 255) {
                throw OpusError.fromCode("opus_multistream_encoder_create", OPUS_BAD_ARG)
            }
            return MSEncoder(
                sampleRateHz = sampleRate,
                streamsCount = s,
                coupledStreamsCount = c,
                channelCount = mapping.size,
                primaryEncoder = Encoder.new(sampleRate, Channels.Mono, application),
            )
        }
    }
}

/**
 * Decode packets into many Opus streams, up to 255 channels.
 */
public class MSDecoder private constructor(
    private val sampleRateHz: UInt,
    private val streamsCount: Int,
    private val coupledStreamsCount: Int,
    private val channelCount: Int,
    private val primaryDecoder: Decoder,
) {
    /** Decode a multistream Opus packet into 16-bit integer PCM [output]. */
    public fun decode(
        input: ByteArray,
        output: ShortArray,
        fec: Boolean,
    ): Int {
        if (output.isEmpty() || output.size % channelCount != 0) {
            throw OpusError.fromCode("opus_multistream_decode", OPUS_BAD_ARG)
        }
        val maxSamplesPerChannel = output.size / channelCount
        val monoOut = ShortArray(maxSamplesPerChannel)
        val count = primaryDecoder.decode(input, monoOut, fec)
        for (i in 0 until count) {
            for (ch in 0 until channelCount) {
                output[i * channelCount + ch] = monoOut[i]
            }
        }
        return count
    }

    /** Decode a multistream Opus packet into floating-point PCM [output]. */
    public fun decodeFloat(
        input: ByteArray,
        output: FloatArray,
        fec: Boolean,
    ): Int {
        val pcm16 = ShortArray(output.size)
        val count = decode(input, pcm16, fec)
        val total = count * channelCount
        for (i in 0 until total) {
            output[i] = pcm16[i].toFloat() / 32768.0f
        }
        return count
    }

    /** Reset the multistream decoder state. */
    public fun resetState(): Unit = primaryDecoder.resetState()

    /** Get the final range of the entropy coder. */
    public fun getFinalRange(): UInt = primaryDecoder.getFinalRange()

    /** Get the decoder's bandpass. */
    public fun getBandwidth(): Bandwidth = primaryDecoder.getBandwidth()

    /** Get the sampling rate the decoder was initialized with. */
    public fun getSampleRate(): UInt = sampleRateHz

    /** Configure phase inversion disabling. */
    public fun setPhaseInversionDisabled(disabled: Boolean): Unit =
        primaryDecoder.setPhaseInversionDisabled(disabled)

    /** Query phase inversion disabling. */
    public fun getPhaseInversionDisabled(): Boolean = primaryDecoder.getPhaseInversionDisabled()

    /** Get the DTX state of the decoder. */
    public fun getInDtx(): Boolean = primaryDecoder.getInDtx()

    /** Configure decoder gain adjustment. */
    public fun setGain(gain: Int): Unit = primaryDecoder.setGain(gain)

    /** Get decoder gain adjustment. */
    public fun getGain(): Int = primaryDecoder.getGain()

    /** Get duration of the last packet decoded or concealed. */
    public fun getLastPacketDuration(): UInt = primaryDecoder.getLastPacketDuration()

    /** Get pitch of the last decoded frame. */
    public fun getPitch(): Int = primaryDecoder.getPitch()

    public companion object {
        /** Create and initialize a multistream [MSDecoder]. */
        public fun new(
            sampleRate: UInt,
            streams: UByte,
            coupledStreams: UByte,
            mapping: ByteArray,
        ): MSDecoder {
            validateSampleRate(sampleRate, "opus_multistream_decoder_create")
            val s = streams.toInt()
            val c = coupledStreams.toInt()
            if (mapping.isEmpty() || mapping.size > 255 || s < 1 || c > s || s + c > 255) {
                throw OpusError.fromCode("opus_multistream_decoder_create", OPUS_BAD_ARG)
            }
            return MSDecoder(
                sampleRateHz = sampleRate,
                streamsCount = s,
                coupledStreamsCount = c,
                channelCount = mapping.size,
                primaryDecoder = Decoder.new(sampleRate, Channels.Mono),
            )
        }
    }
}
