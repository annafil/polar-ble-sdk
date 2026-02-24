//  Copyright © 2024 Polar. All rights reserved.
//  FORK: Cache integration for PolarAutomaticSamplesUtils.
//  This file is entirely fork-owned. PolarAutomaticSamplesUtils.swift calls into
//  these helpers and is otherwise kept as close to upstream as possible.

import Foundation

// Metadata extracted from a directory listing entry for cache decisions.
struct AutosFileEntry {
    let name: String
    let size: UInt64
    let modified: Date?
}

internal class PolarAutomaticSamplesCacheUtils {

    /// Given the upstream-filtered file names and the full directory listing, return only
    /// those entries the cache says need downloading.
    static func filesToDownload(
        filteredNames: [String],
        dir: Protocol_PbPFtpDirectory,
        fromDate: Date,
        toDate: Date,
        tag: String
    ) -> [AutosFileEntry] {
        let entryMap = Dictionary(uniqueKeysWithValues: dir.entries.map { ($0.name, $0) })
        let allFiles: [AutosFileEntry] = filteredNames.compactMap { name in
            guard let entry = entryMap[name] else { return nil }
            let modified = entry.hasModified ? pbSystemDateTimeToDate(entry.modified) : nil
            return AutosFileEntry(name: name, size: entry.size, modified: modified)
        }
        let result = allFiles.filter { entry in
            AutosFileCache.shared.shouldDownloadFile(
                filename: entry.name,
                currentSize: entry.size,
                currentModified: entry.modified,
                requestedFromDate: fromDate,
                requestedToDate: toDate
            )
        }
        BleLogger.trace("PolarAutomaticSamplesUtils", "\(tag): downloading \(result.count) of \(allFiles.count) files")
        return result
    }

    /// Update the cache after successfully parsing a file.
    static func updateCache(for fileEntry: AutosFileEntry, day: PbDate) {
        AutosFileCache.shared.updateCache(entry: AutosFileCacheEntry(
            filename: fileEntry.name,
            dateYear: Int(day.year),
            dateMonth: Int(day.month),
            dateDay: Int(day.day),
            size: fileEntry.size,
            modifiedTimestamp: fileEntry.modified?.timeIntervalSince1970
        ))
    }

    // MARK: - Private

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
