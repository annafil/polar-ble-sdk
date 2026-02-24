//  Copyright © 2024 Polar. All rights reserved.
//  FORK: Fast-delete API protocol — kept in a separate file for easier maintenance.

import Foundation
import RxSwift

/// Fork-added API for deleting device directories in a single BLE round-trip.
public protocol PolarFastDeleteApi {

    /// Delete the entire AUTOS directory from a device in a single BLE round-trip.
    /// This is faster than deleteStoredDeviceData(.AUTO_SAMPLE) which downloads and
    /// inspects every file before deleting. The firmware handles recursive deletion.
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Returns: Completable stream
    ///   - success: when AUTOS directory successfully deleted
    ///   - onError: see `PolarErrors` for possible errors invoked
    func deleteAutoSamplesDirectory(_ identifier: String) -> Completable

    /// Delete a named subdirectory under each date folder from fromDate to toDate (inclusive).
    /// Each date's /U/0/YYYYMMDD/<subdirectory>/ is removed in a single BLE round-trip per date.
    /// This is faster than deleteStoredDeviceData() which traverses and deletes files individually.
    /// Missing directories are silently skipped (non-fatal).
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - subdirectory: The subdirectory name under each date folder (e.g. "ACT", "DSUM", "NR")
    ///   - fromDate: Start date (inclusive)
    ///   - toDate: End date (inclusive)
    /// - Returns: Completable stream
    ///   - success: when all matching directories in range successfully deleted
    ///   - onError: see `PolarErrors` for possible errors invoked
    func deleteStoredDataDirectories(_ identifier: String, subdirectory: String, fromDate: Date, toDate: Date) -> Completable
}
