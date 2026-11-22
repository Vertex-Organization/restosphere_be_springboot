package com.vertex.restosphere.security;

import com.vertex.restosphere.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Per-IP request cap shared by the unauthenticated customer endpoints. */
@Component
public class PublicRateLimiter {
    static final int LIMIT_PER_MINUTE = 20;
    private final Map<String, long[]> hits = new ConcurrentHashMap<>();

    // In-memory per-IP counter (single instance); behind a proxy set
    // server.forward-headers-strategy.
    public void check(HttpServletRequest req) {
        long minute = System.currentTimeMillis() / 60000;
        var h =
                hits.compute(
                        req.getRemoteAddr(),
                        (k, v) ->
                                v == null || v[0] != minute
                                        ? new long[] {minute, 1}
                                        : new long[] {minute, v[1] + 1});
        if (hits.size() > 10000) hits.values().removeIf(v -> v[0] != minute);
        if (h[1] > LIMIT_PER_MINUTE)
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn thao tác quá nhanh. Vui lòng thử lại sau 1 phút.");
    }
}
