package com.odyldzhon.bot.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallLockTest {

    @Test
    @DisplayName("Never runs two calls at the same time")
    void run_concurrentCalls_areSerialized() {
        // Given
        AiCallLock lock = new AiCallLock();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();

        // When
        IntStream.range(0, 8).parallel().forEach(i -> lock.run(() -> {
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            active.decrementAndGet();
            return i;
        }));

        // Then
        assertThat(maxActive.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Releases the lock when the call throws")
    void run_callThrows_releasesLock() {
        AiCallLock lock = new AiCallLock();

        try {
            lock.run(() -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException ignored) {
            // expected
        }

        assertThat(lock.run(() -> "ok")).isEqualTo("ok");
    }
}
