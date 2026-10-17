package io.github.kotlinmania.opus.internal

internal object OpusJni {
    init {
        System.loadLibrary("opus_jni")
    }

    @JvmStatic
    external fun getpid(): Int
}
