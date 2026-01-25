//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let AUTOMATIC_SAMPLES_DIRECTORY = "AUTOS/"
private let AUTOMATIC_SAMPLES_PATTERN = #"AUTOS\d{3}\.BPB"#
private let TAG = "PolarAutomaticSamplesUtils"

// FORK: Helper struct for file metadata
private struct AutosFileEntry {
    let name: String
    let size: UInt64
    let modified: Date?
}

internal class PolarAutomaticSamplesUtils {
    
    /// Read 24/7 heart rate samples for a given date range.
    static func read247HrSamples(client: BlePsFtpClient, fromDate: Date, toDate: Date) -> Single<[Polar247HrSamplesData]> {
        BleLogger.trace(TAG, "read247HrSamples: from \(fromDate) to \(toDate)")

        let autoSamplesPath = "\(ARABICA_USER_ROOT_FOLDER)\(AUTOMATIC_SAMPLES_DIRECTORY)"
        let listOperation = Protocol_PbPFtpOperation.with {
            $0.command = .get
            $0.path = autoSamplesPath
        }

        return client.request(try! listOperation.serializedData())
            .flatMap { response -> Single<[Polar247HrSamplesData]> in
                do {
                    let dir = try Protocol_PbPFtpDirectory(serializedData: Data(response))
                    let regex = try NSRegularExpression(pattern: AUTOMATIC_SAMPLES_PATTERN)

                    // FORK: Extract metadata and filter using cache
                    let allFiles: [AutosFileEntry] = dir.entries.compactMap { entry -> AutosFileEntry? in
                        let range = NSRange(location: 0, length: entry.name.count)
                        guard regex.firstMatch(in: entry.name, range: range) != nil else { return nil }
                        let modified = entry.hasModified ? pbSystemDateTimeToDate(entry.modified) : nil
                        return AutosFileEntry(name: entry.name, size: entry.size, modified: modified)
                    }

                    let filesToDownload = allFiles.filter { entry in
                        AutosFileCache.shared.shouldDownloadFile(
                            filename: entry.name,
                            currentSize: entry.size,
                            currentModified: entry.modified,
                            requestedFromDate: fromDate,
                            requestedToDate: toDate
                        )
                    }

                    BleLogger.trace(TAG, "Downloading \(filesToDownload.count) of \(allFiles.count) HR files")

                    return Observable.from(filesToDownload)
                        .concatMap { fileEntry -> Observable<Polar247HrSamplesData?> in
                            let filePath = "\(autoSamplesPath)\(fileEntry.name)"
                            let fileOperation = Protocol_PbPFtpOperation.with {
                                $0.command = .get
                                $0.path = filePath
                            }

                            return client.request(try! fileOperation.serializedData())
                                .asObservable()
                                .map { fileResponse -> Polar247HrSamplesData? in
                                    do {
                                        let sampleSessions = try Data_PbAutomaticSampleSessions(serializedData: Data(fileResponse))
                                        let sampleDateProto = sampleSessions.day
                                        let sampleDate = Calendar.current.date(from: DateComponents(
                                            year: Int(sampleDateProto.year),
                                            month: Int(sampleDateProto.month),
                                            day: Int(sampleDateProto.day)
                                        )) ?? Date.distantPast

                                        // FORK: Update cache
                                        AutosFileCache.shared.updateCache(entry: AutosFileCacheEntry(
                                            filename: fileEntry.name,
                                            dateYear: Int(sampleDateProto.year),
                                            dateMonth: Int(sampleDateProto.month),
                                            dateDay: Int(sampleDateProto.day),
                                            size: fileEntry.size,
                                            modifiedTimestamp: fileEntry.modified?.timeIntervalSince1970
                                        ))

                                        guard sampleDate >= fromDate && sampleDate <= toDate else { return nil }

                                        let samples = try Polar247HrSamplesData.fromPbHrDataSamples(samples: sampleSessions.samples)
                                        return Polar247HrSamplesData(date: Calendar.current.dateComponents([.year, .month, .day], from: sampleDate), samples: samples)
                                    } catch {
                                        BleLogger.error(TAG, "Failed to parse HR in \(fileEntry.name): \(error)")
                                        return nil
                                    }
                                }
                        }
                        .compactMap { $0 }
                        .toArray()
                } catch {
                    BleLogger.error(TAG, "read247HrSamples() failed: \(error)")
                    return Single.error(error)
                }
            }
    }

    /// Read 24/7 peak-to-peak interval samples for a given date range.
    static func read247PPiSamples(client: BlePsFtpClient, fromDate: Date, toDate: Date) -> Single<[Polar247PPiSamplesData]> {
        BleLogger.trace(TAG, "read247PPiSamples: from \(fromDate) to \(toDate)")

        let autoSamplesPath = "\(ARABICA_USER_ROOT_FOLDER)\(AUTOMATIC_SAMPLES_DIRECTORY)"
        let operation = Protocol_PbPFtpOperation.with {
            $0.command = .get
            $0.path = autoSamplesPath
        }

        return client.request(try! operation.serializedData())
            .flatMap { response -> Single<[Polar247PPiSamplesData]> in
                do {
                    let dir = try Protocol_PbPFtpDirectory(serializedData: Data(response))
                    let regex = try NSRegularExpression(pattern: AUTOMATIC_SAMPLES_PATTERN)

                    // FORK: Extract metadata and filter using cache
                    let allFiles: [AutosFileEntry] = dir.entries.compactMap { entry -> AutosFileEntry? in
                        let range = NSRange(location: 0, length: entry.name.count)
                        guard regex.firstMatch(in: entry.name, range: range) != nil else { return nil }
                        let modified = entry.hasModified ? pbSystemDateTimeToDate(entry.modified) : nil
                        return AutosFileEntry(name: entry.name, size: entry.size, modified: modified)
                    }

                    let filesToDownload = allFiles.filter { entry in
                        AutosFileCache.shared.shouldDownloadFile(
                            filename: entry.name,
                            currentSize: entry.size,
                            currentModified: entry.modified,
                            requestedFromDate: fromDate,
                            requestedToDate: toDate
                        )
                    }

                    BleLogger.trace(TAG, "Downloading \(filesToDownload.count) of \(allFiles.count) PPI files")

                    return Observable.from(filesToDownload)
                        .concatMap { fileEntry -> Observable<Polar247PPiSamplesData?> in
                            let filePath = "\(autoSamplesPath)\(fileEntry.name)"
                            let fileOperation = Protocol_PbPFtpOperation.with {
                                $0.command = .get
                                $0.path = filePath
                            }

                            return client.request(try! fileOperation.serializedData())
                                .asObservable()
                                .map { fileResponse -> Polar247PPiSamplesData? in
                                    do {
                                        let sampleSessions = try Data_PbAutomaticSampleSessions(serializedData: Data(fileResponse))
                                        let sampleDateProto = sampleSessions.day
                                        let sampleDate = Calendar.current.date(from: DateComponents(
                                            year: Int(sampleDateProto.year),
                                            month: Int(sampleDateProto.month),
                                            day: Int(sampleDateProto.day)
                                        )) ?? Date.distantPast

                                        // FORK: Update cache
                                        AutosFileCache.shared.updateCache(entry: AutosFileCacheEntry(
                                            filename: fileEntry.name,
                                            dateYear: Int(sampleDateProto.year),
                                            dateMonth: Int(sampleDateProto.month),
                                            dateDay: Int(sampleDateProto.day),
                                            size: fileEntry.size,
                                            modifiedTimestamp: fileEntry.modified?.timeIntervalSince1970
                                        ))

                                        guard sampleDate >= fromDate && sampleDate <= toDate else { return nil }

                                        let samples = sampleSessions.ppiSamples.map { Polar247PPiSamplesData.fromPbPPiDataSamples(ppiData: $0) }
                                        return Polar247PPiSamplesData(date: Calendar.current.dateComponents([.year, .month, .day], from: sampleDate), samples: samples)
                                    } catch {
                                        BleLogger.error(TAG, "Failed to parse PPI in \(fileEntry.name): \(error)")
                                        return nil
                                    }
                                }
                        }
                        .compactMap { $0 }
                        .toArray()
                } catch {
                    BleLogger.error(TAG, "read247PPiSamples() failed: \(error)")
                    return Single.error(error)
                }
            }
    }

    // FORK: Helper to convert protobuf timestamp to Date
    private static func pbSystemDateTimeToDate(_ pbDateTime: PbSystemDateTime) -> Date? {
        guard pbDateTime.hasDate else { return nil }
        var components = DateComponents()
        components.year = Int(pbDateTime.date.year)
        components.month = Int(pbDateTime.date.month)
        components.day = Int(pbDateTime.date.day)
        if pbDateTime.hasTime {
            components.hour = Int(pbDateTime.time.hour)
            components.minute = Int(pbDateTime.time.minute)
            components.second = Int(pbDateTime.time.seconds)
        }
        return Calendar.current.date(from: components)
    }
}

extension DateComponents: Comparable {
    public static func < (lhs: DateComponents, rhs: DateComponents) -> Bool {
        let now = Date()
        let calendar = Calendar.current
        return calendar.date(byAdding: lhs, to: now)! < calendar.date(byAdding: rhs, to: now)!
    }
}
