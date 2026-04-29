package com.odyldzhon.bot.telegram.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerMatcherTest {

    @Test
    @DisplayName("Matches case-insensitively as a substring")
    void contains_caseInsensitiveSubstring_matches() {
        // Given / When / Then
        assertThat(TriggerMatcher.contains("Hey, lebowski, what's up?", "Lebowski")).isTrue();
        assertThat(TriggerMatcher.contains("LEBOWSKI here", "lebowski")).isTrue();
        assertThat(TriggerMatcher.contains("nothing relevant", "Lebowski")).isFalse();
    }

    @Test
    @DisplayName("Returns false for null or blank inputs")
    void contains_nullOrBlankInputs_returnsFalse() {
        // Given / When / Then
        assertThat(TriggerMatcher.contains(null, "Lebowski")).isFalse();
        assertThat(TriggerMatcher.contains("Hey", null)).isFalse();
        assertThat(TriggerMatcher.contains("Hey", "")).isFalse();
        assertThat(TriggerMatcher.contains("Hey", "   ")).isFalse();
    }
}
