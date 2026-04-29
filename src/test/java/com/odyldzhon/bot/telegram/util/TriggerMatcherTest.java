package com.odyldzhon.bot.telegram.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerMatcherTest {

    private static final String BOT_NAME = "Lebowski";
    private static final String BOT_USERNAME = "lebowski_bot";

    @Test
    @DisplayName("Matches when the bot name appears in the text (case-insensitive)")
    void matches_mentionInText_returnsTrue() {
        // Given
        Message message = new Message();

        // When / Then
        assertThat(TriggerMatcher.matches(message, "Hey, lebowski, what's up?", BOT_NAME, BOT_USERNAME)).isTrue();
        assertThat(TriggerMatcher.matches(message, "LEBOWSKI here", BOT_NAME, BOT_USERNAME)).isTrue();
    }

    @Test
    @DisplayName("Matches when the message is a reply to the bot's own message")
    void matches_replyToBot_returnsTrue() {
        // Given
        Message reply = new Message();
        User botUser = new User();
        botUser.setUserName(BOT_USERNAME);
        reply.setFrom(botUser);

        Message message = new Message();
        message.setReplyToMessage(reply);

        // When / Then
        assertThat(TriggerMatcher.matches(message, "agreed", BOT_NAME, BOT_USERNAME)).isTrue();
    }

    @Test
    @DisplayName("Does not match when the message is a reply to another user")
    void matches_replyToOtherUser_returnsFalse() {
        // Given
        Message reply = new Message();
        User otherUser = new User();
        otherUser.setUserName("someone_else");
        reply.setFrom(otherUser);

        Message message = new Message();
        message.setReplyToMessage(reply);

        // When / Then
        assertThat(TriggerMatcher.matches(message, "agreed", BOT_NAME, BOT_USERNAME)).isFalse();
    }

    @Test
    @DisplayName("Does not match when text has no mention and message is not a reply")
    void matches_noMentionAndNoReply_returnsFalse() {
        // Given
        Message message = new Message();

        // When / Then
        assertThat(TriggerMatcher.matches(message, "nothing relevant", BOT_NAME, BOT_USERNAME)).isFalse();
    }

    @Test
    @DisplayName("Returns false for null or blank trigger inputs without a reply")
    void matches_nullOrBlankInputs_returnsFalse() {
        // Given
        Message message = new Message();

        // When / Then
        assertThat(TriggerMatcher.matches(message, null, BOT_NAME, BOT_USERNAME)).isFalse();
        assertThat(TriggerMatcher.matches(message, "Hey", null, BOT_USERNAME)).isFalse();
        assertThat(TriggerMatcher.matches(message, "Hey", "", BOT_USERNAME)).isFalse();
        assertThat(TriggerMatcher.matches(message, "Hey", "   ", BOT_USERNAME)).isFalse();
    }
}
