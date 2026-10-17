#if canImport(Testing)
import Testing
import Opus

@Suite("Opus Swift Export Tests")
struct OpusExportTests {
    @Test("Opus swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        #expect(!version().isEmpty)
        let encoder = Encoder.Companion.shared.new(
            sampleRate: 48000,
            channels: .Mono,
            mode: .Audio
        )
        #expect(encoder.getSampleRate() == 48000)
    }
}
#elseif canImport(XCTest)
import XCTest
import Opus

final class OpusExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertFalse(version().isEmpty)
        let encoder = Encoder.Companion.shared.new(
            sampleRate: 48000,
            channels: .Mono,
            mode: .Audio
        )
        XCTAssertEqual(encoder.getSampleRate(), 48000)
    }
}
#endif
