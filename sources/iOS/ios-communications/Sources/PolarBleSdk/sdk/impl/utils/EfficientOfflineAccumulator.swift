//  Copyright © 2024 Polar. All rights reserved.

import Foundation

/// Drop-in replacement for the mutable-var accumulation pattern that copies
/// the entire sample array on every sub-recording (`existingData + newSamples`).
/// This class appends to a single mutable array per data type, turning O(n²)
/// accumulation into O(n).
///
/// Usage:
///   1. Call the appropriate `accumulate*()` method with already-mapped Polar data.
///   2. After all sub-recordings are processed, call `getResult()`.
internal class EfficientOfflineAccumulator {

    // MARK: - ACC
    private var accSamples: PolarAccData = []
    private var accStartTime: Date?
    private var accSettings: PolarSensorSetting?

    func accumulateAcc(_ data: PolarAccData, startTime: Date, settings: PolarSensorSetting) {
        if accStartTime == nil {
            accStartTime = startTime
            accSettings = settings
        }
        accSamples.append(contentsOf: data)
    }

    // MARK: - GYRO
    private var gyroSamples: PolarGyroData = []
    private var gyroStartTime: Date?
    private var gyroSettings: PolarSensorSetting?

    func accumulateGyro(_ data: PolarGyroData, startTime: Date, settings: PolarSensorSetting) {
        if gyroStartTime == nil {
            gyroStartTime = startTime
            gyroSettings = settings
        }
        gyroSamples.append(contentsOf: data)
    }

    // MARK: - MAG
    private var magSamples: PolarMagnetometerData = []
    private var magStartTime: Date?
    private var magSettings: PolarSensorSetting?

    func accumulateMag(_ data: PolarMagnetometerData, startTime: Date, settings: PolarSensorSetting) {
        if magStartTime == nil {
            magStartTime = startTime
            magSettings = settings
        }
        magSamples.append(contentsOf: data)
    }

    // MARK: - PPG
    private var ppgSamples: [(timeStamp: UInt64, channelSamples: [Int32], statusBits: [Int8]?)] = []
    private var ppgType: PpgDataType?
    private var ppgStartTime: Date?
    private var ppgSettings: PolarSensorSetting?

    func accumulatePpg(_ data: PolarPpgData, startTime: Date, settings: PolarSensorSetting) {
        if ppgStartTime == nil {
            ppgStartTime = startTime
            ppgSettings = settings
            ppgType = data.type
        }
        ppgSamples.append(contentsOf: data.samples)
    }

    // MARK: - PPI
    private var ppiSamples: [(timeStamp: UInt64, hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)] = []
    private var ppiStartTime: Date?

    func accumulatePpi(_ data: PolarPpiData, startTime: Date) {
        if ppiStartTime == nil {
            ppiStartTime = startTime
        }
        ppiSamples.append(contentsOf: data.samples)
    }

    // MARK: - HR
    private var hrSamples: PolarHrData = []
    private var hrStartTime: Date?

    func accumulateHr(_ data: PolarHrData, startTime: Date) {
        if hrStartTime == nil {
            hrStartTime = startTime
        }
        hrSamples.append(contentsOf: data)
    }

    // MARK: - Temperature
    private var tempSamples: [(timeStamp: UInt64, temperature: Float)] = []
    private var tempStartTime: Date?

    func accumulateTemperature(_ data: PolarTemperatureData, startTime: Date) {
        if tempStartTime == nil {
            tempStartTime = startTime
        }
        tempSamples.append(contentsOf: data.samples)
    }

    // MARK: - Skin Temperature
    private var skinTempSamples: [(timeStamp: UInt64, temperature: Float)] = []
    private var skinTempStartTime: Date?

    func accumulateSkinTemperature(_ data: PolarTemperatureData, startTime: Date) {
        if skinTempStartTime == nil {
            skinTempStartTime = startTime
        }
        skinTempSamples.append(contentsOf: data.samples)
    }

    // MARK: - Empty
    private var emptyStartTime: Date?

    func accumulateEmpty(startTime: Date) {
        if emptyStartTime == nil {
            emptyStartTime = startTime
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
