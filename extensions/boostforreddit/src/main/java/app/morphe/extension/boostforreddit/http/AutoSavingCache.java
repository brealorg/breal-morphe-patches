/*
 * Copyright 2026 wchill.
 * Modified for the Breal undelete audit: lazy, fail-open database and fewer writes (2026-10-13).
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.http;

import android.util.LruCache;

import java.util.Optional;

import app.morphe.extension.boostforreddit.http.cache.CacheDao;
import app.morphe.extension.boostforreddit.http.cache.CacheDatabase;
import app.morphe.extension.boostforreddit.http.cache.CacheEntry;
import app.morphe.extension.boostforreddit.utils.LoggingUtils;

/**
 * Memory LRU in front of a Room table. The database is opened on first use and any database
 * failure degrades to memory only: interceptors holding these caches are installed in Boost's own
 * OkHttp clients, so construction and lookups must never throw.
 */
public class AutoSavingCache {
    // Eviction counts rows, so it only needs to run now and then.
    private static final int EVICT_EVERY_PUTS = 32;

    private final String cacheName;
    private final int maxSize;
    private final LruCache<String, String> memoryCache;
    private volatile CacheDao cacheDao;
    private volatile boolean databaseFailed;
    private int putsSinceEviction = EVICT_EVERY_PUTS;

    public AutoSavingCache(String name, int maxSize) {
        this.cacheName = name;
        this.maxSize = maxSize;
        this.memoryCache = new LruCache<>(maxSize);
    }

    private CacheDao dao() {
        if (databaseFailed) {
            return null;
        }
        CacheDao dao = cacheDao;
        if (dao == null) {
            try {
                dao = CacheDatabase.getInstance().cacheDao();
                cacheDao = dao;
            } catch (Throwable failure) {
                disableDatabase(failure);
            }
        }
        return dao;
    }

    private void disableDatabase(Throwable failure) {
        databaseFailed = true;
        LoggingUtils.logInfo(false, () -> "morphe_boost_undelete_cache_memory_only: " + cacheName
                + " " + failure.getClass().getSimpleName());
    }

    public Optional<String> get(String key) {
        // Memory hits do not touch the database; recency there only matters for eviction,
        // and updating it on every comment of every thread meant a SQLite write per lookup.
        String memoryValue = memoryCache.get(key);
        if (memoryValue != null) {
            return Optional.of(memoryValue);
        }

        CacheDao dao = dao();
        if (dao == null) {
            return Optional.empty();
        }
        try {
            String dbValue = dao.get(cacheName, key);
            if (dbValue != null) {
                memoryCache.put(key, dbValue);
                dao.updateLastAccessed(cacheName, key, System.currentTimeMillis());
                return Optional.of(dbValue);
            }
        } catch (Throwable failure) {
            disableDatabase(failure);
        }
        return Optional.empty();
    }

    public void put(String key, String value) {
        if (value.equals(memoryCache.get(key))) {
            return; // Unchanged; skip the database write.
        }
        memoryCache.put(key, value);

        CacheDao dao = dao();
        if (dao == null) {
            return;
        }
        try {
            dao.put(new CacheEntry(cacheName, key, value, System.currentTimeMillis()));
            synchronized (this) {
                if (++putsSinceEviction < EVICT_EVERY_PUTS) {
                    return;
                }
                putsSinceEviction = 0;
            }
            int count = dao.getCount(cacheName);
            if (count > maxSize) {
                dao.evictOldest(cacheName, count - maxSize);
            }
        } catch (Throwable failure) {
            disableDatabase(failure);
        }
    }
}
