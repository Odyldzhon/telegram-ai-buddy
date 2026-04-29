package com.odyldzhon.bot.telegram.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.assertj.core.api.Assertions.assertThat;

class MessageAuthorsTest {

    @Test
    @DisplayName("Prefers the Telegram username when present")
    void resolve_userWithUsername_returnsUsername() {
        // Given
        Message message = message("walter", "Walter");

        // When
        String result = MessageAuthors.resolve(message);

        // Then
        assertThat(result).isEqualTo("walter");
    }

    @Test
    @DisplayName("Falls back to first name when no username is set")
    void resolve_userWithoutUsername_returnsFirstName() {
        // Given
        Message message = message(null, "Walter");

        // When
        String result = MessageAuthors.resolve(message);

        // Then
        assertThat(result).isEqualTo("Walter");
    }

    @Test
    @DisplayName("Returns null for null message or missing sender")
    void resolve_nullMessageOrMissingSender_returnsNull() {
        // Given / When / Then
        assertThat(MessageAuthors.resolve(null)).isNull();
        assertThat(MessageAuthors.resolve(new Message())).isNull();
    }

    private static Message message(String username, String firstName) {
        User user = new User();
        user.setUserName(username);
        user.setFirstName(firstName);
        Message m = new Message();
        m.setFrom(user);
        return m;
    }
}
