package com.odyldzhon.bot.telegram.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.ExternalReplyInfo;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginChannel;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginUser;

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

    @Test
    @DisplayName("Returns outside source for forwarded messages even when the posting member is known")
    void resolve_forwardOrigin_returnsOutsideSource() {
        // Given
        Message message = message("walter", "Walter");
        message.setForwardOrigin(new MessageOriginChannel());

        // When
        String result = MessageAuthors.resolve(message);

        // Then
        assertThat(result).isEqualTo(MessageAuthors.OUTSIDE_SOURCE);
    }

    @Test
    @DisplayName("Returns the posting member for forwarded messages originally authored by the same user")
    void resolve_selfForwardOrigin_returnsMember() {
        // Given
        Message message = message("walter", "Walter", 1L);
        MessageOriginUser origin = new MessageOriginUser();
        origin.setSenderUser(user("original_walter", "Walter", 1L));
        message.setForwardOrigin(origin);

        // When
        String result = MessageAuthors.resolve(message);

        // Then
        assertThat(result).isEqualTo("walter");
    }

    @Test
    @DisplayName("Returns outside source for forwarded messages authored by a different user")
    void resolve_otherUserForwardOrigin_returnsOutsideSource() {
        // Given
        Message message = message("walter", "Walter", 1L);
        MessageOriginUser origin = new MessageOriginUser();
        origin.setSenderUser(user("donny", "Donny", 2L));
        message.setForwardOrigin(origin);

        // When
        String result = MessageAuthors.resolve(message);

        // Then
        assertThat(result).isEqualTo(MessageAuthors.OUTSIDE_SOURCE);
    }

    @Test
    @DisplayName("Returns outside source for legacy forwarded message metadata")
    void resolve_legacyForwardMetadata_returnsOutsideSource() {
        // Given
        Message message = message("walter", "Walter");
        message.setForwardSenderName("Hidden original author");

        // When
        String result = MessageAuthors.resolve(message);

        // Then
        assertThat(result).isEqualTo(MessageAuthors.OUTSIDE_SOURCE);
    }

    @Test
    @DisplayName("Returns the posting member for legacy forwarded messages originally authored by the same user")
    void resolve_legacySelfForward_returnsMember() {
        // Given
        Message message = message("walter", "Walter", 1L);
        message.setForwardFrom(user("original_walter", "Walter", 1L));
        message.setForwardDate(1_777_398_405);

        // When
        String result = MessageAuthors.resolve(message);

        // Then
        assertThat(result).isEqualTo("walter");
    }

    @Test
    @DisplayName("Returns outside source for legacy forwarded messages authored by a different user")
    void resolve_legacyOtherUserForward_returnsOutsideSource() {
        // Given
        Message message = message("walter", "Walter", 1L);
        message.setForwardFrom(user("donny", "Donny", 2L));
        message.setForwardDate(1_777_398_405);

        // When
        String result = MessageAuthors.resolve(message);

        // Then
        assertThat(result).isEqualTo(MessageAuthors.OUTSIDE_SOURCE);
    }

    @Test
    @DisplayName("Returns outside source for external reply metadata")
    void resolve_externalReply_returnsOutsideSource() {
        // Given
        Message message = message("walter", "Walter");
        message.setExternalReplyInfo(new ExternalReplyInfo());

        // When
        String result = MessageAuthors.resolve(message);

        // Then
        assertThat(result).isEqualTo(MessageAuthors.OUTSIDE_SOURCE);
    }

    private static Message message(String username, String firstName) {
        return message(username, firstName, 1L);
    }

    private static Message message(String username, String firstName, Long id) {
        User user = user(username, firstName, id);
        Message m = new Message();
        m.setFrom(user);
        return m;
    }

    private static User user(String username, String firstName, Long id) {
        User user = new User();
        user.setId(id);
        user.setUserName(username);
        user.setFirstName(firstName);
        return user;
    }
}
