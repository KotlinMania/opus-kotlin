#if canImport(Testing)
import Testing
import Opus

@Suite("Opus Swift Export Tests")
struct OpusExportTests {
    @Test("Opus swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        #expect(Bool(true), "Opus swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Opus

final class OpusExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "Opus swift module imported cleanly")
    }
}
#endif
