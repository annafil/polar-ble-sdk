//  Copyright © 2024 Polar. All rights reserved.

import Foundation

/// Drop-in replacement for the mutable-var accumulation pattern that copies
/// the entire sample array on every sub-recording (`existingData + newSamples`).
/// This class appends to a single mutable array per data type, turning O(n²)
/// accumulation into O(n).
///
/// Self-contained: accepts raw `OfflineRecordingData<Any>` and does its own
/// PMD-to-Polar mapping internally, so callers need only a single method call.
internal class EfficientOfflineAccumulator {

    // MARK: - ACC
    private var accSamples: PolarAccData = []
    private var accStartTime: Date?
    private var accSettings: PolarSensorSetting?

    // MARK: - GYRO
    private var gyroSamples: PolarGyroData = []
    private var gyroStartTime: Date?
    private var gyroSettings: PolarSensorSetting?

    // MARK: - MAG
    private var magSamples: PolarMagnetometerData = []
    private var magStartTime: Date?
    private var magSettings: PolarSensorSetting?

    // MARK: - PPG
    private var ppgSamples: [(timeStamp: UInt64, channelSamples: [Int32], statusBits: [Int8]?)] = []
    private var ppgType: PpgDataType?
    private var ppgStartTime: Date?
    private var ppgSettings: PolarSensorSetting?

    // MARK: - PPI
    private var ppiSamples: [(timeStamp: UInt64, hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)] = []
    private var ppiStartTime: Date?

    // MARK: - HR
    private var hrSamples: PolarHrData = []
    private var hrStartTime: Date?

    // MARK: - Temperature
    private var tempSamples: [(timeStamp: UInt64, temperature: Float)] = []
    private var tempStartTime: Date?

    // MARK: - Skin Temperature
    private var skinTempSamples: [(timeStamp: UInt64, temperature: Float)] = []
    private var skinTempStartTime: Date?

    // MARK: - Empty
    private var emptyStartTime: Date?

    // MARK: - Accumulate

    /// Processes a single sub-recording chunk, mapping PMD types to Polar types
    /// and appending samples to the internal mutable arrays.
    func accumulate(_ offlineRecordingData: OfflineRecordingData<Any>) throws {
        let settings = offlineRecordingData.recordingSettings?.mapToPolarSettings() ?? PolarSensorSetting()
        let startTime = offlineRecordingData.startTime

        switch offlineRecordingData.data {
        case let accData as AccData:
            if accStartTime == nil {
                accStartTime = startTime
                accSettings = settings
            }
            for sample in accData.samples {
                accSamples.append((timeStamp: sample.timeStamp, x: sample.x, y: sample.y, z: sample.z))
            }

        case let gyroData as GyrData:
            if gyroStartTime == nil {
                gyroStartTime = startTime
                gyroSettings = settings
            }
            for sample in gyroData.samples {
                gyroSamples.append((timeStamp: sample.timeStamp, x: sample.x, y: sample.y, z: sample.z))
            }

        case let magData as MagData:
            if magStartTime == nil {
                magStartTime = startTime
                magSettings = settings
            }
            for sample in magData.samples {
                magSamples.append((timeStamp: sample.timeStamp, x: sample.x, y: sample.y, z: sample.z))
            }

        case let ppgData as PpgData:
            if ppgStartTime == nil {
                ppgStartTime = startTime
                ppgSettings = settings
            }
            for sample in ppgData.samples {
                if sample.frameType == PmdDataFrameType.type_0 {
                    let d = sample as! PpgDataFrameType0
                    ppgSamples.append((timeStamp: sample.timeStamp!, channelSamples: [d.ppgDataSamples[0], d.ppgDataSamples[1], d.ppgDataSamples[2], d.ambientSample], statusBits: nil))
                    ppgType = .ppg3_ambient1
                } else if sample.frameType == PmdDataFrameType.type_6 {
                    let d = sample as! PpgDataFrameType6
                    ppgSamples.append((timeStamp: sample.timeStamp!, channelSamples: [d.sportId], statusBits: nil))
                    ppgType = .ppg1
                } else if sample.frameType == PmdDataFrameType.type_7 {
                    let d = sample as! PpgDataFrameType7
                    ppgSamples.append((timeStamp: sample.timeStamp!, channelSamples: d.ppgDataSamples, statusBits: nil))
                    ppgType = .ppg17
                } else if sample.frameType == PmdDataFrameType.type_10 {
                    let d = sample as! PpgDataFrameType10
                    ppgSamples.append((timeStamp: sample.timeStamp!, channelSamples: d.greenSamples + d.redSamples + d.irSamples, statusBits: d.statusBits))
                    ppgType = .ppg21
                } else if sample.frameType == PmdDataFrameType.type_9 {
                    let d = sample as! PpgDataFrameType9
                    ppgSamples.append((timeStamp: sample.timeStamp!, channelSamples: d.ppgDataSamples, statusBits: nil))
                    ppgType = .ppg3
                } else if sample.frameType == PmdDataFrameType.type_13 {
                    let d = sample as! PpgDataFrameType13
                    ppgSamples.append((timeStamp: sample.timeStamp!, channelSamples: [d.ppgDataSamples[0], d.ppgDataSamples[1]], statusBits: d.statusBits))
                    ppgType = .ppg2
                }
            }

        case let ppiData as PpiData:
            if ppiStartTime == nil {
                ppiStartTime = startTime
            }
            for sample in ppiData.samples {
                ppiSamples.append((timeStamp: sample.timeStamp, hr: sample.hr, ppInMs: sample.ppInMs, ppErrorEstimate: sample.ppErrorEstimate, blockerBit: sample.blockerBit, skinContactStatus: sample.skinContactStatus, skinContactSupported: sample.skinContactSupported))
            }

        case let hrData as OfflineHrData:
            if hrStartTime == nil {
                hrStartTime = startTime
            }
            for sample in hrData.samples {
                hrSamples.append((hr: sample.hr, ppgQuality: sample.ppgQuality, correctedHr: sample.correctedHr, rrsMs: [], rrAvailable: false, contactStatus: false, contactStatusSupported: false))
            }

        case let temperatureData as TemperatureData:
            if tempStartTime == nil {
                tempStartTime = startTime
            }
            for sample in temperatureData.samples {
                tempSamples.append((timeStamp: sample.timeStamp, temperature: sample.temperature))
            }

        case let skinTemperatureData as SkinTemperatureData:
            if skinTempStartTime == nil {
                skinTempStartTime = startTime
            }
            for sample in skinTemperatureData.samples {
                skinTempSamples.append((timeStamp: sample.timeStamp, temperature: sample.skinTemperature))
            }

        case _ as EmptyData:
            if emptyStartTime == nil {
                emptyStartTime = startTime
            }

        default:
            throw PolarErrors.polarOfflineRecordingError(
                description: "GetOfflineRecording failed. Data type is not supported."
            )
        }
    }

    // MARK: - Result

    func getResult() -> PolarOfflineRecordingData? {
        if let startTime = ppiStartTime, !ppiSamples.isEmpty {
            return .ppiOfflineRecordingData(
                (timeStamp: UInt64(startTime.timeIntervalSince1970), samples: ppiSamples),
                startTime: startTime
            )
        }
        if let startTime = ppgStartTime, let settings = ppgSettings, let type = ppgType, !ppgSamples.isEmpty {
            return .ppgOfflineRecordingData(
                (type: type, samples: ppgSamples),
                startTime: startTime,
                settings: settings
            )
        }
        if let startTime = accStartTime, let settings = accSettings, !accSamples.isEmpty {
            return .accOfflineRecordingData(accSamples, startTime: startTime, settings: settings)
        }
        if let startTime = gyroStartTime, let settings = gyroSettings, !gyroSamples.isEmpty {
            return .gyroOfflineRecordingData(gyroSamples, startTime: startTime, settings: settings)
        }
        if let startTime = magStartTime, let settings = magSettings, !magSamples.isEmpty {
            return .magOfflineRecordingData(magSamples, startTime: startTime, settings: settings)
        }
        if let startTime = hrStartTime, !hrSamples.isEmpty {
            return .hrOfflineRecordingData(hrSamples, startTime: startTime)
        }
        if let startTime = tempStartTime, !tempSamples.isEmpty {
            return .temperatureOfflineRecordingData(
                (timeStamp: tempSamples.last?.timeStamp ?? 0, samples: tempSamples),
                startTime: startTime
            )
        }
        if let startTime = skinTempStartTime, !skinTempSamples.isEmpty {
            return .skinTemperatureOfflineRecordingData(
                (timeStamp: skinTempSamples.last?.timeStamp ?? 0, samples: skinTempSamples),
                startTime: startTime
            )
        }
        if let startTime = emptyStartTime {
            return .emptyData(startTime: startTime)
        }
        return nil
    }
}
