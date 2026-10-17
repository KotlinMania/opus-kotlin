// port-lint: source tests/fec.rs
package io.github.kotlinmania.opus

import kotlin.test.Test
import kotlin.test.assertEquals

class FecTest {
    @Test
    fun blah() {
        val opus = Decoder.new(48_000u, Channels.Mono)
        val output = ShortArray(5760)
        val size = opus.decode(ByteArray(0), output, true)
        assertEquals(5760, size)
    }
}
