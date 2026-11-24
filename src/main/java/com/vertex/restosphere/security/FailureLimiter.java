package com.vertex.restosphere.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Counts failed attempts per key and blocks the key for a window once the limit is reached. */
public class FailureLimiter {
    private final int maxFailures;
    private final long windowMs;
    // shortcut: in-memory (single instance), lets an attacker lock a known key for the window;
    // move to a shared store when scaling out.
    private final Map<String, long[]> failures = new ConcurrentHashMap<>();

    public FailureLimiter(int maxFailures, long windowMs) {
        this.maxFailures = maxFailures;
        this.windowMs = windowMs;
    }

    public boolean blocked(String key) {
        long now = System.currentTimeMillis();
        if (failures.size() > 10_000) {
            failures.values().removeIf(v -> now - v[1] >= windowMs);
        }
        long[] f = failures.get(key);
        return f != null && now - f[1] < windowMs && f[0] >= maxFailures;
    }

    public void fail(String key) {
        long now = System.currentTimeMillis();
        failures.compute(
                key,
                (k, v) ->
                        v == null || now - v[1] >= windowMs
                                ? new long[] {1, now}
                                : new long[] {v[0] + 1, v[1]});
    }

    public void clear(String key) {
        failures.remove(key);
    }
}
