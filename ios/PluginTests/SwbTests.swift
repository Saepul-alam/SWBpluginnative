import XCTest
@testable import Plugin

class SwbTests: XCTestCase {

    func testIdentifierRoundTrip() {
        let id = 12345
        let identifier = SwbNotif.identifier(for: id)
        XCTAssertEqual(SwbNotif.id(from: identifier), id)
    }

    func testIntervalLookup() {
        XCTAssertEqual(SwbNotif.interval(for: "minute"), 60)
        XCTAssertEqual(SwbNotif.interval(for: "hour"), 3600)
        XCTAssertEqual(SwbNotif.interval(for: "day"), 86400)
        XCTAssertEqual(SwbNotif.interval(for: "week"), 604800)
        XCTAssertNil(SwbNotif.interval(for: "year"))
    }

    func testDateParsing() {
        let milliseconds = 1_700_000_000_000.0
        XCTAssertEqual(SwbNotif.date(from: milliseconds)?.timeIntervalSince1970, 1_700_000_000)
        XCTAssertNotNil(SwbNotif.date(from: "1700000000000"))
        XCTAssertNotNil(SwbNotif.date(from: "2023-11-14T22:13:20Z"))
    }

    func testSanitizeDropsNulls() {
        let sanitized = SwbNotif.sanitize(["a": "value", "b": NSNull()] as [String: Any])
        XCTAssertEqual(sanitized["a"] as? String, "value")
        XCTAssertNil(sanitized["b"])
    }

    func testValidateOptionsSuccess() {
        let options: [String: Any] = [
            "title": "Clean Alert",
            "body": "Alert message body",
            "extra": ["key": "val"]
        ]
        let result = SwbNotif.validate(options: options)
        XCTAssertTrue(result.isValid)
        XCTAssertNil(result.error)
    }

    func testValidateOptionsEmptyTitleThrows() {
        let options: [String: Any] = ["title": "   "]
        let result = SwbNotif.validate(options: options)
        XCTAssertFalse(result.isValid)
        XCTAssertNotNil(result.error)
    }

    func testValidateOptionsLongTitleThrows() {
        let longTitle = String(repeating: "T", count: SwbNotif.maxTitleLength + 5)
        let options: [String: Any] = ["title": longTitle]
        let result = SwbNotif.validate(options: options)
        XCTAssertFalse(result.isValid)
        XCTAssertNotNil(result.error)
    }

    func testValidateOptionsLongBodyThrows() {
        let longBody = String(repeating: "B", count: SwbNotif.maxBodyLength + 10)
        let options: [String: Any] = ["title": "Valid", "body": longBody]
        let result = SwbNotif.validate(options: options)
        XCTAssertFalse(result.isValid)
        XCTAssertNotNil(result.error)
    }

    func testValidateOptionsLargeExtraThrows() {
        let largePayload = String(repeating: "E", count: SwbNotif.maxExtraBytes + 100)
        let options: [String: Any] = ["title": "Valid", "extra": ["big": largePayload]]
        let result = SwbNotif.validate(options: options)
        XCTAssertFalse(result.isValid)
        XCTAssertNotNil(result.error)
    }
}
