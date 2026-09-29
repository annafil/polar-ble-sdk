//  Copyright © 2025 Polar. All rights reserved.

import Foundation
import SwiftProtobuf

/// Polar Peak-to-peak interval data
/// - Parameters:
/// - date: date of the PPi activity data
/// - samples: PPi samples from sensor as PolarPpiDataSample object
public struct Polar247PPiSamplesData: Codable {
    public let date: DateComponents
    public let samples: [PolarPpiDataSample]
    
    /// Polar 24/7 PPi data sample
    /// - Parameters:
    ///  - startTime: start time of the sample session
    ///  - triggerType: describes how the measurement was triggered
    ///  - ppiValueList: list of Peak-to-Peak interval values in the sample session
    ///  - ppiErrorEstimateList: list of error estimate  values in the sample session
    ///  - statusList: status values in the sample session
    public struct PolarPpiDataSample: Codable {
        public let startTime: String!
        public let triggerType: PPiSampleTriggerType!
        public let ppiValueList: [Int32]!
        public let ppiErrorEstimateList: [Int32]!
        public let statusList: [PPiSampleStatus]!
    }

    public enum PPiSampleTriggerType: String, Codable {
        case TRIGGER_TYPE_UNDEFINED = "TRIGGER_TYPE_UNDEFINED"
        case TRIGGER_TYPE_AUTOMATIC = "TRIGGER_TYPE_AUTOMATIC"
        case TRIGGER_TYPE_MANUAL = "TRIGGER_TYPE_MANUAL"

        static func getByValue(value: Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType) -> PPiSampleTriggerType {

            switch value {
            case .ppiTriggerTypeUndefined: return .TRIGGER_TYPE_UNDEFINED
            case .ppiTriggerTypeAutomatic: return .TRIGGER_TYPE_AUTOMATIC
            case .ppiTriggerTypeManual: return .TRIGGER_TYPE_MANUAL
            }
        }
    }

    public enum SkinContact: String, Codable {
        case NO_SKIN_CONTACT = "NO_SKIN_CONTACT"
        case SKIN_CONTACT_DETECTED = "SKIN_CONTACT_DETECTED"

        static func getByValue(value: Int) -> SkinContact? {

            switch value {
            case 0: return .NO_SKIN_CONTACT
            case 1: return .SKIN_CONTACT_DETECTED
            default:
                return nil
            }
        }
    }

    public enum Movement: String, Codable {
        case NO_MOVING_DETECTED = "NO_MOVING_DETECTED"
        case MOVING_DETECTED = "MOVING_DETECTED"

        static func getByValue(value: Int) -> Movement? {

            switch value {
            case 0: return .NO_MOVING_DETECTED
            case 1: return .MOVING_DETECTED
            default:
                return nil
            }
        }
    }

    public enum IntervalStatus: String, Codable {
        case INTERVAL_IS_ONLINE = "INTERVAL_IS_ONLINE"
        case INTERVAL_DENOTES_OFFLINE_PERIOD = "INTERVAL_DENOTES_OFFLINE_PERIOD"
        
        static func getByValue(value: Int) -> IntervalStatus? {

            switch value {
            case 0: return .INTERVAL_IS_ONLINE
            case 1: return .INTERVAL_DENOTES_OFFLINE_PERIOD
            default:
                return nil
            }
        }
    }

    public struct PPiSampleStatus: Codable {
        public var skinContact: SkinContact
        public var movement: Movement
        public var intervalStatus: IntervalStatus

        static func fromStatusByte(byte: UInt32) -> PPiSampleStatus {
            // 32-bit representation of the incoming byte as String
            let binary = String.binaryRepresentation(of: byte)
            return PPiSampleStatus(
                skinContact: SkinContact.getByValue(value: Int(binary[31])!)!,
                movement: Movement.getByValue(value: Int(binary[30])!)!,
                intervalStatus: IntervalStatus.getByValue(value: Int(binary[29])!)!
            )
        }
    }

    static func fromPbPPiDataSamples(ppiData: Data_PbPpIntervalAutoSamples) -> PolarPpiDataSample {

        var ppiSampleStatusList = [PPiSampleStatus]()
        var ppiValueList = [Int32]()
        var ppiErrorEstimateList = [Int32]()
        var previousSample: Int32 = 0

        for sample in ppiData.ppi.ppiDelta {
            let uncompressedSample = previousSample + sample
            ppiValueList.append(uncompressedSample)
            previousSample = uncompressedSample
        }

        previousSample = 0

        for sample in ppiData.ppi.ppiErrorEstimateDelta {
            let uncompressedSample = previousSample + sample
            ppiErrorEstimateList.append(uncompressedSample)
            previousSample = uncompressedSample
        }

        for sample in ppiData.ppi.status {
            ppiSampleStatusList.append(PPiSampleStatus.fromStatusByte(byte: sample))
        }

        return PolarPpiDataSample(
            startTime: PolarTimeUtils.pbTimeToTimeString(ppiData.recordingTime),
            triggerType: PPiSampleTriggerType.getByValue(value: ppiData.triggerType),
            ppiValueList: ppiValueList,
            ppiErrorEstimateList: ppiErrorEstimateList,
            statusList: ppiSampleStatusList
        )
    }
}

extension Polar247PPiSamplesData {

    /// How far a sample's time-of-day may run ahead of the phone's clock and still count as
    /// today when inferring a missing day (covers device/phone clock drift).
    private static let clockSkewToleranceSeconds = 60 * 60

    /// Parses one raw AUTOS file into per-day PPI data.
    ///
    /// Loop firmware 6.2.x writes AUTOS files without the required `day` field (seen on files
    /// recreated after the AUTOS directory is deleted). Such files are dated by inference from
    /// `now`: the last sample belongs to today (or yesterday if its time-of-day is later than
    /// `now`), and each backwards step in time-of-day marks a midnight rollover.
    static func fromAutoSamplesFile(_ data: Data, now: Date, calendar: Calendar = .current) throws -> [Polar247PPiSamplesData] {
        let sessions: Data_PbAutomaticSampleSessions
        do {
            sessions = try Data_PbAutomaticSampleSessions(serializedData: data)
        } catch BinaryDecodingError.missingRequiredFields {
            let partial = try Data_PbAutomaticSampleSessions(serializedData: data, partial: true)
            // Only a missing `day` is recoverable — any other missing field is still a corrupt file.
            var withPlaceholderDay = partial
            withPlaceholderDay.day = PbDate.with { $0.year = 1; $0.month = 1; $0.day = 1 }
            guard !partial.hasDay, withPlaceholderDay.isInitialized else {
                throw BinaryDecodingError.missingRequiredFields
            }
            return inferDays(for: partial.ppiSamples, now: now, calendar: calendar)
        }
        let day = DateComponents(year: Int(sessions.day.year), month: Int(sessions.day.month), day: Int(sessions.day.day))
        return [Polar247PPiSamplesData(date: day, samples: sessions.ppiSamples.map { fromPbPPiDataSamples(ppiData: $0) })]
    }

    /// Groups samples (in recording order) into days, walking backwards from `now`.
    private static func inferDays(for samples: [Data_PbPpIntervalAutoSamples], now: Date, calendar: Calendar) -> [Polar247PPiSamplesData] {
        func secondsOfDay(_ time: PbTime) -> Int {
            Int(time.hour) * 3600 + Int(time.minute) * 60 + Int(time.seconds)
        }
        let nowParts = calendar.dateComponents([.hour, .minute, .second], from: now)
        let nowSeconds = nowParts.hour! * 3600 + nowParts.minute! * 60 + nowParts.second!

        var dayOffset = 0
        var laterSeconds: Int? = nil
        var buckets: [(dayOffset: Int, samples: [PolarPpiDataSample])] = []
        for sample in samples.reversed() {
            let seconds = secondsOfDay(sample.recordingTime)
            if let later = laterSeconds {
                if seconds > later { dayOffset -= 1 }  // crossed midnight going backwards
            } else if seconds > nowSeconds + clockSkewToleranceSeconds {
                dayOffset = -1  // newest sample is from before midnight
            }
            laterSeconds = seconds
            if buckets.last?.dayOffset != dayOffset {
                buckets.append((dayOffset, []))
            }
            buckets[buckets.count - 1].samples.append(fromPbPPiDataSamples(ppiData: sample))
        }

        let today = calendar.startOfDay(for: now)
        return buckets.reversed().map { bucket in
            let date = calendar.date(byAdding: .day, value: bucket.dayOffset, to: today)!
            return Polar247PPiSamplesData(
                date: calendar.dateComponents([.year, .month, .day], from: date),
                samples: bucket.samples.reversed()
            )
        }
    }
}

extension String {

    static func binaryRepresentation<F: FixedWidthInteger>(of value: F) -> String {

        let binaryString = String(value, radix: 2)

        if value.leadingZeroBitCount > 0 {
            return String(repeating: "0", count: value.leadingZeroBitCount) + binaryString
        }

        return binaryString
    }
}

extension String {

    var length: Int {
        return count
    }

    subscript (i: Int) -> String {
        return self[i ..< i + 1]
    }

    func substring(fromIndex: Int) -> String {
        return self[min(fromIndex, length) ..< length]
    }

    func substring(toIndex: Int) -> String {
        return self[0 ..< max(0, toIndex)]
    }

    subscript (r: Range<Int>) -> String {
        let range = Range(uncheckedBounds: (lower: max(0, min(length, r.lowerBound)),
                                            upper: min(length, max(0, r.upperBound))))
        let start = index(startIndex, offsetBy: range.lowerBound)
        let end = index(start, offsetBy: range.upperBound - range.lowerBound)
        return String(self[start ..< end])
    }
}
