package io.github.kotlinmania.opus

internal const val OPUS_OK: Int = 0
internal const val OPUS_BAD_ARG: Int = -1
internal const val OPUS_BUFFER_TOO_SMALL: Int = -2
internal const val OPUS_INTERNAL_ERROR: Int = -3
internal const val OPUS_INVALID_PACKET: Int = -4
internal const val OPUS_UNIMPLEMENTED: Int = -5
internal const val OPUS_INVALID_STATE: Int = -6
internal const val OPUS_ALLOC_FAIL: Int = -7

internal const val OPUS_AUTO_CONST: Int = -1000
internal const val OPUS_BITRATE_MAX_CONST: Int = -1

/**
 * The possible applications for the Opus codec.
 */
public enum class Application(
    internal val rawCode: Int,
) {
    /** Best for most VoIP/videoconference applications where listening quality and intelligibility matter most. */
    Voip(2048),

    /** Best for broadcast/high-fidelity applications where the decoded audio should be as close as possible to the input. */
    Audio(2049),

    /** Only use when lowest-achievable latency is what matters most. */
    LowDelay(2051),
    ;

    internal companion object {
        fun fromRaw(
            raw: Int,
            what: String,
        ): Application =
            when (raw) {
                2048 -> Voip
                2049 -> Audio
                2051 -> LowDelay
                else -> throw OpusError.badArg(what)
            }
    }
}

/**
 * The available channel settings.
 */
public enum class Channels(
    public val count: Int,
) {
    /** One channel. */
    Mono(1),

    /** Two channels, left and right. */
    Stereo(2),
}

/**
 * The available bandwidth level settings.
 */
public enum class Bandwidth(
    internal val rawCode: Int,
) {
    /** Auto/default setting. */
    Auto(OPUS_AUTO_CONST),

    /** 4kHz bandpass. */
    Narrowband(1101),

    /** 6kHz bandpass. */
    Mediumband(1102),

    /** 8kHz bandpass. */
    Wideband(1103),

    /** 12kHz bandpass. */
    Superwideband(1104),

    /** 20kHz bandpass. */
    Fullband(1105),
    ;

    internal fun raw(): Int = rawCode

    public companion object {
        /** Return the default bandwidth setting ([Auto]). */
        public fun default(): Bandwidth = Auto

        internal fun fromInt(value: Int): Bandwidth? =
            when (value) {
                OPUS_AUTO_CONST -> Auto
                1101 -> Narrowband
                1102 -> Mediumband
                1103 -> Wideband
                1104 -> Superwideband
                1105 -> Fullband
                else -> null
            }

        internal fun decode(
            value: Int,
            what: String,
        ): Bandwidth = fromInt(value) ?: throw OpusError.badArg(what)
    }
}

/**
 * Possible Opus error codes.
 */
public enum class ErrorCode(
    internal val rawCode: Int,
) {
    /** One or more invalid/out of range arguments. */
    BadArg(OPUS_BAD_ARG),

    /** Not enough bytes allocated in the buffer. */
    BufferTooSmall(OPUS_BUFFER_TOO_SMALL),

    /** An internal error was detected. */
    InternalError(OPUS_INTERNAL_ERROR),

    /** The compressed data passed is corrupted. */
    InvalidPacket(OPUS_INVALID_PACKET),

    /** Invalid/unsupported request number. */
    Unimplemented(OPUS_UNIMPLEMENTED),

    /** An encoder or decoder structure is invalid or already freed. */
    InvalidState(OPUS_INVALID_STATE),

    /** Memory allocation has failed. */
    AllocFail(OPUS_ALLOC_FAIL),

    /** An unknown failure. */
    Unknown(-8),
    ;

    /** Get a human-readable error string for this error code. */
    public fun errorDescription(): String =
        when (this) {
            BadArg -> "invalid argument"
            BufferTooSmall -> "buffer too small"
            InternalError -> "internal error"
            InvalidPacket -> "corrupted stream"
            Unimplemented -> "request not implemented"
            InvalidState -> "invalid state"
            AllocFail -> "memory allocation failed"
            Unknown -> "unknown error"
        }

    internal companion object {
        fun fromInt(value: Int): ErrorCode =
            when (value) {
                OPUS_BAD_ARG -> BadArg
                OPUS_BUFFER_TOO_SMALL -> BufferTooSmall
                OPUS_INTERNAL_ERROR -> InternalError
                OPUS_INVALID_PACKET -> InvalidPacket
                OPUS_UNIMPLEMENTED -> Unimplemented
                OPUS_INVALID_STATE -> InvalidState
                OPUS_ALLOC_FAIL -> AllocFail
                else -> Unknown
            }
    }
}

/**
 * Possible bitrates.
 */
public sealed class Bitrate {
    /** Explicit bitrate choice (in bits/second). */
    public data class Bits(
        public val bitsPerSecond: Int,
    ) : Bitrate()

    /** Maximum bitrate allowed (up to maximum number of bytes for the packet). */
    public data object Max : Bitrate()

    /** Default bitrate decided by the encoder (not recommended). */
    public data object Auto : Bitrate()

    internal fun raw(): Int =
        when (this) {
            is Auto -> OPUS_AUTO_CONST
            is Max -> OPUS_BITRATE_MAX_CONST
            is Bits -> bitsPerSecond
        }

    internal companion object {
        fun fromRaw(raw: Int): Bitrate =
            when (raw) {
                OPUS_AUTO_CONST -> Auto
                OPUS_BITRATE_MAX_CONST -> Max
                else -> Bits(raw)
            }
    }
}

/**
 * Possible signal types. Hints for the encoder's mode selection.
 */
public enum class Signal(
    internal val rawCode: Int,
) {
    /** Auto/default setting. */
    Auto(OPUS_AUTO_CONST),

    /** Bias thresholds towards choosing LPC or Hybrid modes. */
    Voice(3001),

    /** Bias thresholds towards choosing MDCT modes. */
    Music(3002),
    ;

    internal fun raw(): Int = rawCode

    public companion object {
        /** Return the default signal setting ([Auto]). */
        public fun default(): Signal = Auto

        internal fun fromRaw(
            raw: Int,
            what: String,
        ): Signal =
            when (raw) {
                OPUS_AUTO_CONST -> Auto
                3001 -> Voice
                3002 -> Music
                else -> throw OpusError.badArg(what)
            }
    }
}

/**
 * Possible frame sizes. Controls the encoder's use of variable duration frames.
 */
public enum class FrameSize(
    internal val rawCode: Int,
) {
    /** Select frame size from the argument (default). */
    Arg(5000),

    /** Use 2.5 ms frames. */
    Ms25Tenths(5001),

    /** Use 5 ms frames. */
    Ms5(5002),

    /** Use 10 ms frames. */
    Ms10(5003),

    /** Use 20 ms frames. */
    Ms20(5004),

    /** Use 40 ms frames. */
    Ms40(5005),

    /** Use 60 ms frames. */
    Ms60(5006),

    /** Use 80 ms frames. */
    Ms80(5007),

    /** Use 100 ms frames. */
    Ms100(5008),

    /** Use 120 ms frames. */
    Ms120(5009),
    ;

    internal fun raw(): Int = rawCode

    public companion object {
        /** Return the default frame size setting ([Arg]). */
        public fun default(): FrameSize = Arg

        internal fun fromRaw(
            raw: Int,
            what: String,
        ): FrameSize =
            when (raw) {
                5000 -> Arg
                5001 -> Ms25Tenths
                5002 -> Ms5
                5003 -> Ms10
                5004 -> Ms20
                5005 -> Ms40
                5006 -> Ms60
                5007 -> Ms80
                5008 -> Ms100
                5009 -> Ms120
                else -> throw OpusError.badArg(what)
            }
    }
}

/**
 * Get the libopus version string.
 */
public fun version(): String = "libopus 1.5.2"
