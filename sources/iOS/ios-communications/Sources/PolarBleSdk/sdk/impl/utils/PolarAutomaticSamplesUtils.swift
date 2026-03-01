//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

// TEMP: instrumentation, remove before commit
private func ppiLog(_ msg: String) {
    let ts = String(format: "%.3f", Date().timeIntervalSince1970.truncatingRemainder(dividingBy: 1000))
    let line = "[PPiSync \(ts)] \(msg)"
    print(line)
    BleLogger.error(line)
}

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let AUTOMATIC_SAMPLES_DIRECTORY = "AUTOS/"
private let AUTOMATIC_SAMPLES_PATTERN = #"AUTOS\d{3}\.BPB"#
private let TAG = "PolarAutomaticSamplesUtils"

internal class PolarAutomaticSamplesUtils {
    
    /// Read 24/7 heart rate samples for a given date range.
    static func read247HrSamples(client: BlePsFtpClient, fromDate: Date, toDate: Date) -> Single<[Polar247HrSamplesData]> {
        BleLogger.trace(TAG, "read247HrSamples: from \(fromDate) to \(toDate)")

        let autoSamplesPath = "\(ARABICA_USER_ROOT_FOLDER)\(AUTOMATIC_SAMPLES_DIRECTORY)"
        let listOperation = Protocol_PbPFtpOperation.with {
            $0.command = .get
            $0.path = autoSamplesPath
        }

        client.batchOperationStarted()
        return client.request(try! listOperation.serializedData())
            .flatMap { response -> Single<[Polar247HrSamplesData]> in
                do {
                    let dir = try Protocol_PbPFtpDirectory(serializedData: Data(response))
                    let regex = try NSRegularExpression(pattern: AUTOMATIC_SAMPLES_PATTERN)
                    let filteredFiles = dir.entries.compactMap { entry -> String? in
                        let range = NSRange(location: 0, length: entry.name.count)
                        return regex.firstMatch(in: entry.name, range: range) != nil ? entry.name : nil
                    }

                    return Observable.from(filteredFiles)
                        .concatMap { fileName -> Observable<Polar247HrSamplesData?> in
                            let filePath = "\(autoSamplesPath)\(fileName)"
                            let fileOperation = Protocol_PbPFtpOperation.with {
                                $0.command = .get
                                $0.path = filePath
                            }
                            let dateFrom = Calendar.current.dateComponents([.year, .month, .day], from: fromDate)
                            let dateTo = Calendar.current.dateComponents([.year, .month, .day], from: toDate)
                            return client.request(try! fileOperation.serializedData())
                                .asObservable()
                                .map { fileResponse -> Polar247HrSamplesData? in
                                    do {
                                        let sampleSessions = try Data_PbAutomaticSampleSessions(serializedData: Data(fileResponse))
                                        let sampleDateProto = sampleSessions.day
                                        let sampleDate = DateComponents(
                                            year: Int(sampleDateProto.year),
                                            month: Int(sampleDateProto.month),
                                            day: Int(sampleDateProto.day)
                                        )

                                        guard sampleDate >= dateFrom && sampleDate <= dateTo else { return nil }

                                        let samples = try Polar247HrSamplesData.fromPbHrDataSamples(samples: sampleSessions.samples)
                                        return Polar247HrSamplesData(date: Calendar.current.dateComponents([.year, .month, .day], from: Calendar.current.date(from: sampleDate)!), samples: samples)
                                    } catch {
                                        BleLogger.error(TAG, "Failed to parse HR in \(fileName): \(error)")
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
            .do(onDispose: { client.batchOperationFinished() })
    }

    /// Read 24/7 peak-to-peak interval samples for a given date range.
    static func read247PPiSamples(client: BlePsFtpClient, fromDate: Date, toDate: Date) -> Single<[Polar247PPiSamplesData]> {
        BleLogger.trace(TAG, "read247PPiSamples: from \(fromDate) to \(toDate)")

        let autoSamplesPath = "\(ARABICA_USER_ROOT_FOLDER)\(AUTOMATIC_SAMPLES_DIRECTORY)"
        let operation = Protocol_PbPFtpOperation.with {
            $0.command = .get
            $0.path = autoSamplesPath
        }

        let dateFrom = Calendar.current.dateComponents([.year, .month, .day], from: fromDate)
        let dateTo = Calendar.current.dateComponents([.year, .month, .day], from: toDate)

        // TEMP: instrumentation, remove before commit
        let syncStart = Date()
        ppiLog("directory listing start")

        client.batchOperationStarted()
        return client.request(try! operation.serializedData())
            .flatMap { response -> Single<[Polar247PPiSamplesData]> in
                // TEMP: instrumentation, remove before commit
                ppiLog("directory listing end (\(Int(Date().timeIntervalSince(syncStart) * 1000))ms)")
                do {
                    let dir = try Protocol_PbPFtpDirectory(serializedData: Data(response))
                    let regex = try NSRegularExpression(pattern: AUTOMATIC_SAMPLES_PATTERN)
                    let filteredFiles = dir.entries.compactMap { entry -> String? in
                        let range = NSRange(location: 0, length: entry.name.count)
                        return regex.firstMatch(in: entry.name, range: range) != nil ? entry.name : nil
                    }
                    // TEMP: instrumentation, remove before commit
                    ppiLog("\(filteredFiles.count) files found: \(filteredFiles.joined(separator: ", "))")
                    var fileIndex = 0

                    return Observable.from(filteredFiles)
                        .concatMap { fileName -> Observable<Polar247PPiSamplesData?> in
                            let filePath = "\(autoSamplesPath)\(fileName)"
                            let fileOperation = Protocol_PbPFtpOperation.with {
                                $0.command = .get
                                $0.path = filePath
                            }
                            // TEMP: instrumentation, remove before commit
                            fileIndex += 1
                            let fileStart = Date()
                            let fileNum = fileIndex
                            ppiLog("[\(fileNum)/\(filteredFiles.count)] \(fileName) transfer start (total elapsed: \(Int(Date().timeIntervalSince(syncStart) * 1000))ms)")

                            return client.request(try! fileOperation.serializedData())
                                .asObservable()
                                .map { fileResponse -> Polar247PPiSamplesData? in
                                    // TEMP: instrumentation, remove before commit
                                    let transferMs = Int(Date().timeIntervalSince(fileStart) * 1000)
                                    ppiLog("[\(fileNum)/\(filteredFiles.count)] \(fileName) transfer end (\(transferMs)ms)")
                                    let parseStart = Date()
                                    defer {
                                        // TEMP: instrumentation, remove before commit
                                        let parseMs = Int(Date().timeIntervalSince(parseStart) * 1000)
                                        let totalMs = Int(Date().timeIntervalSince(syncStart) * 1000)
                                        ppiLog("[\(fileNum)/\(filteredFiles.count)] \(fileName) parse end (\(parseMs)ms) — total elapsed: \(totalMs)ms")
                                    }
                                    do {
                                        let sampleSessions = try Data_PbAutomaticSampleSessions(serializedData: Data(fileResponse))
                                        let sampleDateProto = sampleSessions.day
                                        let sampleDate = DateComponents(
                                            year: Int(sampleDateProto.year),
                                            month: Int(sampleDateProto.month),
                                            day: Int(sampleDateProto.day)
                                        )

                                        guard sampleDate >= dateFrom && sampleDate <= dateTo else { return nil }

                                        let samples = sampleSessions.ppiSamples.map { Polar247PPiSamplesData.fromPbPPiDataSamples(ppiData: $0) }
                                        return Polar247PPiSamplesData(date: Calendar.current.dateComponents([.year, .month, .day], from: Calendar.current.date(from: sampleDate)!), samples: samples)
                                    } catch {
                                        BleLogger.error(TAG, "Failed to parse PPI in \(fileName): \(error)")
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
            .do(onDispose: { client.batchOperationFinished() })
    }
}

extension DateComponents: Comparable {
    public static func < (lhs: DateComponents, rhs: DateComponents) -> Bool {
        let now = Date()
        let calendar = Calendar.current
        return calendar.date(byAdding: lhs, to: now)! < calendar.date(byAdding: rhs, to: now)!
    }
}
