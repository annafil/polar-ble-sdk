//  Copyright © 2024 Polar. All rights reserved.
//  FORK: Fast-delete APIs — delete entire directories in a single BLE round-trip.
//  Kept in a separate file for easier maintenance across upstream rebases.

import Foundation
import RxSwift

extension PolarBleApiImpl {

    func deleteAutoSamplesDirectory(_ identifier: String) -> Completable {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }
            var operation = Protocol_PbPFtpOperation()
            operation.command = Protocol_PbPFtpOperation.Command.remove
            operation.path = "/U/0/AUTOS/"
            let request = try operation.serializedData()
            return client.request(request)
                .asCompletable()
                .do(onError: { error in
                    BleLogger.error("Error deleting AUTOS directory from device \(identifier). Error: \(error.localizedDescription)")
                }, onCompleted: {
                    BleLogger.trace("Successfully deleted AUTOS directory from device \(identifier).")
                }, onSubscribe: {
                    BleLogger.trace("Started deleting AUTOS directory from device \(identifier).")
                })
        } catch {
            return Completable.error(PolarErrors.deviceError(description: "Failed to delete AUTOS directory from device \(identifier)."))
        }
    }

    func deleteStoredDataDirectories(_ identifier: String, subdirectory: String, fromDate: Date, toDate: Date) -> Completable {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMdd"
        dateFormatter.timeZone = TimeZone(identifier: "UTC")

        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }

            var dates: [Date] = []
            var current = fromDate
            let calendar = Calendar.current
            while current <= toDate {
                dates.append(current)
                current = calendar.date(byAdding: .day, value: 1, to: current)!
            }

            return Observable.from(dates)
                .concatMap { date -> Completable in
                    let dateStr = dateFormatter.string(from: date)
                    let path = "/U/0/\(dateStr)/\(subdirectory)/"
                    var operation = Protocol_PbPFtpOperation()
                    operation.command = Protocol_PbPFtpOperation.Command.remove
                    operation.path = path
                    guard let request = try? operation.serializedData() else {
                        return Completable.empty()
                    }
                    BleLogger.trace("Deleting directory: \(path)")
                    return client.request(request)
                        .asCompletable()
                        .catch { error in
                            // Non-fatal: directory may not exist for this date
                            BleLogger.trace("No \(subdirectory) directory for \(dateStr) (non-fatal): \(error.localizedDescription)")
                            return Completable.empty()
                        }
                }
                .asCompletable()
        } catch {
            return Completable.error(PolarErrors.deviceError(description: "Failed to delete \(subdirectory) directories from device \(identifier)."))
        }
    }
}
