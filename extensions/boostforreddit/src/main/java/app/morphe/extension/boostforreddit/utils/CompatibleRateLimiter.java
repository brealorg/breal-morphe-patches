/*
 * Copyright 2026 wchill.
 * Modified for the Breal undelete audit: no reflection into Boost's obfuscated Guava (2026-10-13).
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import java.util.concurrent.TimeUnit;

/**
 * Evenly spaced permits, like Guava's RateLimiter without bursts.
 *
 * This used to call Boost's bundled Guava by its obfuscated names through reflection, in a static
 * initializer. The throttling interceptors are installed in Boost's own OkHttp clients even when
 * undelete is off, so any mismatch there failed class initialization for every request.
 */
public class CompatibleRateLimiter {
    private final long intervalNanos;
    private long nextFreeNanos = System.nanoTime();

    private CompatibleRateLimiter(double permitsPerSecond) {
        if (!(permitsPerSecond > 0)) {
            throw new IllegalArgumentException("Rate must be positive");
        }
        this.intervalNanos = (long) (TimeUnit.SECONDS.toNanos(1) / permitsPerSecond);
    }

    public static CompatibleRateLimiter create(double permitsPerSecond) {
        return new CompatibleRateLimiter(permitsPerSecond);
    }

    /** Reserves permits; returns how long the caller must wait, or -1 if that exceeds the timeout. */
    private synchronized long reserve(int permits, long timeoutNanos) {
        long now = System.nanoTime();
        long start = Math.max(now, nextFreeNanos);
        long waitNanos = start - now;
        if (waitNanos > timeoutNanos) {
            return -1L;
        }
        nextFreeNanos = start + intervalNanos * permits;
        return waitNanos;
    }

    public boolean tryAcquire() {
        return tryAcquire(1, 0L, TimeUnit.MICROSECONDS);
    }

    public boolean tryAcquire(int permits, long timeout, TimeUnit timeUnit) {
        long waitNanos = reserve(permits, timeUnit.toNanos(timeout));
        if (waitNanos < 0) {
            return false;
        }
        sleep(waitNanos);
        return true;
    }

    public double acquire() {
        return acquire(1);
    }

    public double acquire(int permits) {
        long waitNanos = reserve(permits, Long.MAX_VALUE);
        sleep(waitNanos);
        return waitNanos / (double) TimeUnit.SECONDS.toNanos(1);
    }

    private static void sleep(long nanos) {
        if (nanos <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
