package com.synapse.backend.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.synapse.backend.shared.ratelimit.RateLimitProperties.Limit;
import com.synapse.backend.shared.ratelimit.exceptions.RateLimitExceededException;

class RateLimitServiceTest {

    private static final Limit THREE_PER_MINUTE = new Limit(3, Duration.ofMinutes(1));

    @Test
    void allowsRequestsUpToLimit() {
        RateLimitService service = enabledService();

        for (int i = 0; i < 3; i++) {
            assertThatCode(() -> service.check("user:1", THREE_PER_MINUTE)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> service.check("user:1", THREE_PER_MINUTE))
            .isInstanceOf(RateLimitExceededException.class)
            .hasMessageStartingWith("Too many requests.");
    }

    @Test
    void countsKeysIndependently() {
        RateLimitService service = enabledService();

        for (int i = 0; i < 3; i++) {
            service.check("user:1", THREE_PER_MINUTE);
        }

        assertThatCode(() -> service.check("user:2", THREE_PER_MINUTE)).doesNotThrowAnyException();
    }

    @Test
    void allowsRequestsAgainAfterWindowExpires() throws InterruptedException {
        RateLimitService service = enabledService();
        Limit shortWindow = new Limit(1, Duration.ofMillis(100));

        service.check("user:1", shortWindow);
        assertThatThrownBy(() -> service.check("user:1", shortWindow)).isInstanceOf(RateLimitExceededException.class);

        Thread.sleep(150);

        assertThatCode(() -> service.check("user:1", shortWindow)).doesNotThrowAnyException();
    }

    @Test
    void doesNotLimitWhenDisabled() {
        RateLimitService service = new RateLimitService(properties(false));

        for (int i = 0; i < 10; i++) {
            assertThatCode(() -> service.check("user:1", THREE_PER_MINUTE)).doesNotThrowAnyException();
        }

        assertThat(service.trackedKeys()).isZero();
    }

    @Test
    void retryAfterRoundsRemainingTimeUp() {
        RateLimitService service = enabledService();
        Limit oneEveryFiveSeconds = new Limit(1, Duration.ofSeconds(5));

        service.check("user:1", oneEveryFiveSeconds);

        assertThatThrownBy(() -> service.check("user:1", oneEveryFiveSeconds))
            .isInstanceOfSatisfying(RateLimitExceededException.class, ex ->
                assertThat(ex.getRetryAfterSeconds()).isEqualTo(5));
    }

    @Test
    void countsConcurrentRequestsExactly() throws InterruptedException {
        RateLimitService service = enabledService();
        Limit limit = new Limit(50, Duration.ofMinutes(1));
        int threads = 8;
        int requestsPerThread = 25;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    try {
                        start.await();

                        for (int request = 0; request < requestsPerThread; request++) {
                            try {
                                service.check("user:1", limit);
                                allowed.incrementAndGet();
                            } catch (RateLimitExceededException e) {
                                continue;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(allowed.get()).isEqualTo(50);
    }

    @Test
    void dropsKeysWhenTheirWindowExpires() throws InterruptedException {
        RateLimitService service = enabledService();
        Limit shortWindow = new Limit(5, Duration.ofSeconds(1));

        for (int i = 0; i < 20; i++) {
            service.check("user:" + i, shortWindow);
        }

        assertThat(service.trackedKeys()).isEqualTo(20);

        Thread.sleep(2500);

        assertThat(service.trackedKeys()).isZero();
    }

    @Test
    void staysWithinTrackedKeyCapacity() {
        RateLimitService service = enabledService();

        for (int i = 0; i < 120000; i++) {
            service.check("user:" + i, THREE_PER_MINUTE);
        }

        assertThat(service.trackedKeys()).isLessThanOrEqualTo(100000);
    }

    @Test
    void resetClearsCounters() {
        RateLimitService service = enabledService();

        for (int i = 0; i < 3; i++) {
            service.check("user:1", THREE_PER_MINUTE);
        }

        service.reset();

        assertThatCode(() -> service.check("user:1", THREE_PER_MINUTE)).doesNotThrowAnyException();
    }

    private RateLimitService enabledService() {
        return new RateLimitService(properties(true));
    }

    private RateLimitProperties properties(boolean enabled) {
        return new RateLimitProperties(
            enabled,
            THREE_PER_MINUTE,
            new Limit(50, Duration.ofDays(1)),
            new Limit(10, Duration.ofMinutes(15)),
            new Limit(3, Duration.ofHours(1)),
            new Limit(3, Duration.ofHours(1)),
            new Limit(3, Duration.ofHours(1)),
            new Limit(3, Duration.ofHours(1)),
            new Limit(60, Duration.ofMinutes(15)),
            new Limit(10, Duration.ofMinutes(15)),
            new Limit(120, Duration.ofMinutes(1))
        );
    }

}
