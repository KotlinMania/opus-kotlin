package io.github.kotlinmania.opus

@JsModule("@kotlinmania/opus-native-bindings")
public external interface OpusNativeBindings {
    public fun getpid(): Int
}
