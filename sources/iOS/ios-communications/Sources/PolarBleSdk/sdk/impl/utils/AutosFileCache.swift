//  Copyright © 2024 Polar. All rights reserved.
//  FORK: Added file caching to optimize 247 data sync

import Foundation

struct AutosFileCacheEntry: Codable {
    let filename: String
    let dateYear: Int
    let dateMonth: Int
    let dateDay: Int
    let size: UInt64
    let modifiedTimestamp: Double?

    var date: Date? {
        Calendar.current.date(from: DateComponents(year: dateYear, month: dateMonth, day: dateDay))
    }
}

internal class AutosFileCache {
    static let shared = AutosFileCache()

    private let queue = DispatchQueue(label: "com.polar.autosFileCache", attributes: .concurrent)
    private var cache: [String: AutosFileCacheEntry] = [:]
    private let cacheKey = "com.polar.sdk.autosFileCache"

    private init() {
        loadFromDisk()
    }

    func getCachedEntry(filename: String) -> AutosFileCacheEntry? {
        queue.sync { cache[filename] }
    }

    func updateCache(entry: AutosFileCacheEntry) {
        queue.async(flags: .barrier) {
            self.cache[entry.filename] = entry
            self.saveToDisk()
        }
    }

    func shouldDownloadFile(
        filename: String,
        currentSize: UInt64,
        currentModified: Date?,
        requestedFromDate: Date,
        requestedToDate: Date
    ) -> Bool {
        guard let cached = getCachedEntry(filename: filename) else {
            return true
        }

        if currentSize != cached.size {
            return true
        }

        if let currentMod = currentModified,
           let cachedMod = cached.modifiedTimestamp,
           currentMod.timeIntervalSince1970 > cachedMod {
            return true
        }

        if let fileDate = cached.date {
            let calendar = Calendar.current
            let startOfFileDate = calendar.startOfDay(for: fileDate)
            let startOfFromDate = calendar.startOfDay(for: requestedFromDate)
            let startOfToDate = calendar.startOfDay(for: requestedToDate)

            if startOfFileDate >= startOfFromDate && startOfFileDate <= startOfToDate {
                return true
            }
        }

        return false
    }

    private func loadFromDisk() {
        if let data = UserDefaults.standard.data(forKey: cacheKey),
           let decoded = try? JSONDecoder().decode([String: AutosFileCacheEntry].self, from: data) {
            cache = decoded
        }
    }

    private func saveToDisk() {
        if let encoded = try? JSONEncoder().encode(cache) {
            UserDefaults.standard.set(encoded, forKey: cacheKey)
        }
    }
}
