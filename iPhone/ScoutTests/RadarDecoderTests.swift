// SPDX-License-Identifier: MIT
import XCTest
@testable import Scout

final class VariaV1DecoderTests: XCTestCase {
    func testParsesThreatTriplet() {
        let dec = VariaV1Decoder(nowMs: { 1_000 })
        dec.feed([0x02, 0x81, 40, 28])
        let snap = dec.snapshot()
        XCTAssertEqual(1, snap.count)
        XCTAssertEqual(40, snap[0].rangeM)
        XCTAssertNotNil(snap[0].closingSpeedMps)
        XCTAssertEqual(28, Int(snap[0].closingSpeedMps! * 3.6))
    }

    func testFlagByteMeansNoSpeed() {
        let dec = VariaV1Decoder(nowMs: { 1_000 })
        dec.feed([0x02, 0x81, 25, 1])
        XCTAssertNil(dec.snapshot()[0].closingSpeedMps)
    }

    func testSkipsAbsentVid() {
        let dec = VariaV1Decoder(nowMs: { 1_000 })
        dec.feed([0x02, 0x01, 10, 20])
        XCTAssertTrue(dec.snapshot().isEmpty)
    }

    func testFitChannelsWhileTracking() {
        let obs = RadarObservation(
            state: .tracking,
            targets: [
                RadarTarget(occupied: true, rangeM: 40, closingSpeedMps: 28 / 3.6),
                RadarTarget(occupied: true, rangeM: 60, closingSpeedMps: 40 / 3.6),
            ]
        )
        let ch = obs.fitChannels()
        XCTAssertEqual(2, ch[0])
        XCTAssertEqual(40, ch[1])
        XCTAssertEqual(28, ch[2])
    }

    func testFitChannelsNotTrackingAreNa() {
        let ch = RadarObservation(state: .disconnected).fitChannels()
        XCTAssertEqual(255, ch[0])
        XCTAssertEqual(255, ch[1])
        XCTAssertEqual(255, ch[2])
    }

    func testEmptyRoadWhileTracking() {
        let ch = RadarObservation(state: .tracking, targets: []).fitChannels()
        XCTAssertEqual(0, ch[0])
        XCTAssertEqual(255, ch[1])
        XCTAssertEqual(255, ch[2])
    }
}

final class MageneBleDecoderTests: XCTestCase {
    func testEmptyRoad() {
        let d = MageneBleDecoder(nowMs: { 1_000 })
        d.feed(hex("57 09 00 30 00 00 00 00 00 00 00"))
        XCTAssertTrue(d.snapshot().isEmpty)
    }

    func testOneCar() {
        let d = MageneBleDecoder(nowMs: { 1_000 })
        d.feed(hex("57 09 00 30 01 00 29 00 00 04 00"))
        let t = d.snapshot()
        XCTAssertEqual(1, t.count)
        XCTAssertTrue(t[0].occupied)
        XCTAssertEqual((0x29 & 0x3F) * 312 / 100, t[0].rangeM)
        XCTAssertEqual(4 * 3.04, t[0].closingSpeedMps!, accuracy: 0.01)
    }

    func testPageBAddsHigherSlots() {
        let d = MageneBleDecoder(nowMs: { 1_000 })
        d.feed(hex("57 09 00 30 55 00 68 BA B2 44 44"))
        d.feed(hex("57 09 00 31 01 00 2E 00 00 04 00"))
        XCTAssertEqual(5, d.snapshot().count)
    }

    private func hex(_ s: String) -> [UInt8] {
        s.split(whereSeparator: { $0.isWhitespace }).compactMap { UInt8($0, radix: 16) }
    }
}
