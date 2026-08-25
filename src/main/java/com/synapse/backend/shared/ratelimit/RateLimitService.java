package com.synapse.backend.shared.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.synapse.backend.shared.ratelimit.RateLimitProperties.Limit;
import com.synapse.backend.shared.ratelimit.exceptions.RateLimitExceededException;

/**
 * Counts requests per key in fixed time windows.
 *
 * <p>Counters are held in a bounded in-memory cache that drops each window when
 * it resets, so limits apply to a single application instance and are lost on
 * restart.</p>
 */
@Service
public class RateLimitService {

    private static final int MAX_TRACKED_KEYS = 100000;

    private final Cache<String, Window> windows = Caffeine.newBuilder()
        .maximumSize(MAX_TRACKED_KEYS)
        .expireAfter(Expiry.writing(
            (String key, Window window) -> Duration.between(Instant.now(), window.resetsAt)
        ))
        .build();

    private final RateLimitProperties rateLimitProperties;

    public RateLimitService(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * Records a request against a key and enforces its limit.
     *
     * @param key the counter key, such as a user id or client address.
     * @param limit the request count and window to enforce.
     * @throws RateLimitExceededException if the key has exceeded its limit for the current window.
     */
    public void check(String key, Limit limit) {
        if (!rateLimitProperties.enabled()) return;

        Instant now = Instant.now();

        Window window = windows.asMap().compute(key, (currentKey, currentWindow) ->
            currentWindow == null || currentWindow.hasExpired(now)
                ? new Window(now.plus(limit.window()))
                : currentWindow
        );

        if (window.increment() > limit.limit()) {
            throw new RateLimitExceededException(window.secondsUntilReset(now));
        }
    }

    /**
     * Clears all counters.
     */
    public void reset() {
        windows.invalidateAll();
    }

    /**
     * Returns how many keys are currently counted, after running pending cache maintenance.
     *
     * @return the number of tracked keys.
     */
    public long trackedKeys() {
        windows.cleanUp();

        return windows.estimatedSize();
    }

    private static final class Window {
        private final Instant resetsAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant resetsAt) {
            this.resetsAt = resetsAt;
        }

        private boolean hasExpired(Instant now) {
            return !now.isBefore(resetsAt);
        }

        private int increment() {
            return count.incrementAndGet();
        }

        private long secondsUntilReset(Instant now) {
            long millisUntilReset = Duration.between(now, resetsAt).toMillis();

            return Math.max(1, (millisUntilReset + 999) / 1000);
        }
    }

}
