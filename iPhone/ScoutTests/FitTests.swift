// SPDX-License-Identifier: MIT
import XCTest
@testable import Scout

final class ScoutFitWriterTests: XCTestCase {
    func testEncodesScenarioMatchingReferenceStructure() {
        let samples = Self.buildScenario()
        let bytes = ScoutFitWriter.encode(samples)
        XCTAssertEqual(UInt8(ascii: "."), bytes[8])
        XCTAssertEqual(UInt8(ascii: "F"), bytes[9])
        XCTAssertEqual(UInt8(ascii: "I"), bytes[10])
        XCTAssertEqual(UInt8(ascii: "T"), bytes[11])
        XCTAssertEqual(12, Int(bytes[0]))
        let bodyLen =
            Int(bytes[4]) |
            (Int(bytes[5]) << 8) |
            (Int(bytes[6]) << 16) |
            (Int(bytes[7]) << 24)
        XCTAssertEqual(bytes.count - 14, bodyLen)
        let expectedCrc = FitCrc.crc16(bytes, start: 0, end: bytes.count - 2)
        let fileCrc = Int(bytes[bytes.count - 2]) | (Int(bytes[bytes.count - 1]) << 8)
        XCTAssertEqual(expectedCrc, fileCrc)
        let asString = String(bytes: bytes, encoding: .isoLatin1) ?? ""
        XCTAssertTrue(asString.contains("poi_type"))
        XCTAssertTrue(asString.contains("radar_speed"))
        XCTAssertEqual(60, samples.count)
        XCTAssertEqual(1, samples[5].poiType)
        XCTAssertEqual(255, samples[0].radarCount)
        XCTAssertEqual(1, samples[6].radarCount)
    }

    func testFlushWritesReadableFile() throws {
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("scout-p2-\(UUID().uuidString).fit")
        defer { try? FileManager.default.removeItem(at: tmp) }
        let writer = ScoutFitWriter(outFile: tmp)
        writer.append(
            ScoutSample(
                timestampFit: 1_000_000_000,
                latSemi: ScoutSample.degreesToSemi(52.0),
                lonSemi: ScoutSample.degreesToSemi(4.0),
                speedMmPerS: 6000,
                poiType: 1,
                poiDetail: 0
            )
        )
        writer.finish()
        let attrs = try FileManager.default.attributesOfItem(atPath: tmp.path)
        XCTAssertGreaterThan((attrs[.size] as? NSNumber)?.intValue ?? 0, 40)
        let data = try Data(contentsOf: tmp)
        let bytes = [UInt8](data)
        let crc = FitCrc.crc16(bytes, start: 0, end: bytes.count - 2)
        let fileCrc = Int(bytes[bytes.count - 2]) | (Int(bytes[bytes.count - 1]) << 8)
        XCTAssertEqual(crc, fileCrc)
    }

    func testStreamedFlushesMatchOneShotEncode() throws {
        let samples = Self.buildScenario()
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("scout-stream-\(UUID().uuidString).fit")
        defer { try? FileManager.default.removeItem(at: tmp) }
        let writer = ScoutFitWriter(outFile: tmp)
        for (i, sample) in samples.enumerated() {
            writer.append(sample)
            if i % 7 == 0 { writer.flush() }
        }
        writer.finish()
        XCTAssertEqual(samples.count, writer.recordCount)
        XCTAssertEqual(ScoutFitWriter.encode(samples), [UInt8](try Data(contentsOf: tmp)))
    }

    func testResumeAppendContinuesPartialFile() throws {
        let samples = Self.buildScenario()
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("scout-resume-\(UUID().uuidString).fit")
        defer { try? FileManager.default.removeItem(at: tmp) }
        let writer = ScoutFitWriter(outFile: tmp)
        samples.prefix(20).forEach(writer.append)
        writer.flush()
        XCTAssertEqual(20, writer.recordCount)
        let resumed = ScoutFitWriter(outFile: tmp)
        resumed.resumeAppend(existingRecordCount: 20)
        samples.dropFirst(20).forEach(resumed.append)
        resumed.finish()
        XCTAssertEqual(samples.count, resumed.recordCount)
        XCTAssertEqual(ScoutFitWriter.encode(samples), [UInt8](try Data(contentsOf: tmp)))
    }

    func testMidRideFlushLeavesValidFile() throws {
        let samples = Self.buildScenario()
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("scout-partial-\(UUID().uuidString).fit")
        defer { try? FileManager.default.removeItem(at: tmp) }
        let writer = ScoutFitWriter(outFile: tmp)
        samples.prefix(20).forEach(writer.append)
        writer.flush()
        let bytes = [UInt8](try Data(contentsOf: tmp))
        XCTAssertEqual(ScoutFitWriter.encode(Array(samples.prefix(20))), bytes)
        samples.dropFirst(20).forEach(writer.append)
        writer.finish()
        XCTAssertEqual(ScoutFitWriter.encode(samples), [UInt8](try Data(contentsOf: tmp)))
    }

    static func buildScenario() -> [ScoutSample] {
        let deg = pow(2.0, 31) / 180.0
        var rec = (0..<60).map { i in
            ScoutSample(
                timestampFit: 1_000_000_000 + Int64(i),
                latSemi: Int32(((52.0 + Double(i) * 0.0004) * deg).rounded(.toNearestOrAwayFromZero)),
                lonSemi: Int32(((4.0 + Double(i) * 0.0006) * deg).rounded(.toNearestOrAwayFromZero)),
                speedMmPerS: 6000,
                poiType: 0,
                poiDetail: 0,
                radarCount: 255,
                radarNear: 255,
                radarSpeed: 255
            )
        }
        func tag(_ i: Int, _ type: Int, _ detail: Int) {
            rec[i].poiType = type
            rec[i].poiDetail = detail
        }
        func radar(_ i: Int, _ c: Int, _ near: Int, _ sp: Int) {
            rec[i].radarCount = c
            rec[i].radarNear = near
            rec[i].radarSpeed = sp
        }
        tag(5, 1, 0)
        tag(10, 2, 0)
        tag(13, 9, 1)
        tag(18, 9, 2)
        tag(25, 9, 3)
        tag(19, 4, 0)
        tag(22, 5, 1)
        tag(30, 5, 2)
        tag(38, 5, 3)
        tag(46, 5, 4)
        tag(54, 5, 5)
        tag(24, 6, 5)
        tag(27, 6, 6)
        tag(33, 6, 9)
        tag(42, 6, 7)
        tag(57, 1, 0)
        tag(58, 1, 0)
        radar(6, 1, 40, 28); radar(7, 1, 25, 30); radar(8, 0, 255, 255)
        radar(35, 1, 50, 45); radar(36, 1, 30, 48); radar(37, 0, 255, 255)
        return rec
    }
}
