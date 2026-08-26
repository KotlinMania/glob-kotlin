#if canImport(Testing)
import Testing
import Glob

@Suite("Glob Swift Export Tests")
struct GlobExportTests {
    @Test("Swift module loads")
    func testSwiftModuleLoads() {
        #expect(Bool(true), "Glob swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Glob

final class GlobExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "Glob swift module imported cleanly")
    }
}
#endif
