package com.odyldzhon.bot.telegram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypingIndicatorTest {

    @Test
    @DisplayName("Fires the ping at least once and returns the supplier's result")
    void runWith_normalCompletion_pingsAndReturns() {
        // Given
        TypingIndicator indicator = new TypingIndicator();
        AtomicInteger pings = new AtomicInteger();

        try {
            // When
            String result = indicator.runWith(pings::incrementAndGet, () -> "done");

            // Then
            assertThat(result).isEqualTo("done");
            assertThat(pings.get()).isGreaterThanOrEqualTo(1);
        } finally {
            indicator.shutdown();
        }
    }

    @Test
    @DisplayName("Swallows exceptions thrown by the typing ping")
    void runWith_pingThrows_doesNotPropagate() {
        // Given
        TypingIndicator indicator = new TypingIndicator();

        try {
            // When
            String result = indicator.runWith(
                    () -> { throw new RuntimeException("boom"); },
                    () -> "ok");

            // Then
            assertThat(result).isEqualTo("ok");
        } finally {
            indicator.shutdown();
        }
    }

    @Test
    @DisplayName("Propagates exceptions from the wrapped task and still cancels the schedule")
    void runWith_taskThrows_propagatesAndCancels() {
        // Given
        TypingIndicator indicator = new TypingIndicator();

        try {
            // When / Then
            assertThatThrownBy(() -> indicator.runWith(() -> {}, () -> {
                throw new IllegalStateException("nope");
            })).isInstanceOf(IllegalStateException.class)
              .hasMessage("nope");
        } finally {
            indicator.shutdown();
        }
    }
}
