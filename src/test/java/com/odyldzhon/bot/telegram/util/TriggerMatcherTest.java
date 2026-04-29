package com.odyldzhon.bot.telegram.util;

import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import com.odyldzhon.bot.telegram.TriggerMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerMatcherTest {

    private static final String BOT_NAME = "Lebowski";
    private static final String BOT_USERNAME = "lebowski_bot";
    private static final Instant T0 = Instant.parse("2026-04-30T10:00:00Z");

    @Test
    @DisplayName("Reacts when the bot name appears in the text (case-insensitive)")
    void shouldReact_mentionInText_returnsTrue() {
        TriggerMatcher matcher = matcher(Duration.ofHours(2), Clock.fixed(T0, ZoneOffset.UTC));

        assertThat(matcher.shouldReact(new Message(), "Hey, lebowski, what's up?")).isTrue();
        assertThat(matcher.shouldReact(new Message(), "LEBOWSKI here")).isTrue();
    }

    @Test
    @DisplayName("Reacts when the message is a reply to the bot's own message")
    void shouldReact_replyToBot_returnsTrue() {
        TriggerMatcher matcher = matcher(Duration.ofHours(2), Clock.fixed(T0, ZoneOffset.UTC));

        assertThat(matcher.shouldReact(messageReplyingTo(BOT_USERNAME), "agreed")).isTrue();
    }

    @Test
    @DisplayName("Does not react when the message is a reply to another user")
    void shouldReact_replyToOtherUser_returnsFalse() {
        TriggerMatcher matcher = matcher(Duration.ofHours(2), Clock.fixed(T0, ZoneOffset.UTC));

        assertThat(matcher.shouldReact(messageReplyingTo("someone_else"), "agreed")).isFalse();
    }

    @Test
    @DisplayName("Does not react when text has no mention and message is not a reply")
    void shouldReact_noMentionAndNoReply_returnsFalse() {
        TriggerMatcher matcher = matcher(Duration.ofHours(2), Clock.fixed(T0, ZoneOffset.UTC));

        assertThat(matcher.shouldReact(new Message(), "nothing relevant")).isFalse();
    }

    @Test
    @DisplayName("Reacts when the bot has been silent for at least the idle threshold")
    void shouldReact_idleThresholdElapsed_returnsTrue() {
        MutableClock clock = new MutableClock(T0);
        TriggerMatcher matcher = matcher(Duration.ofHours(2), clock);
        clock.set(T0.plus(Duration.ofHours(2)).plusSeconds(1));

        assertThat(matcher.shouldReact(new Message(), "plain chatter")).isTrue();
    }

    @Test
    @DisplayName("Does not react on idle when the threshold has not yet elapsed")
    void shouldReact_idleThresholdNotElapsed_returnsFalse() {
        MutableClock clock = new MutableClock(T0);
        TriggerMatcher matcher = matcher(Duration.ofHours(2), clock);
        clock.set(T0.plus(Duration.ofMinutes(30)));

        assertThat(matcher.shouldReact(new Message(), "plain chatter")).isFalse();
    }

    @Test
    @DisplayName("Does not react on idle when the threshold is null, zero, or negative")
    void shouldReact_idleThresholdDisabled_returnsFalse() {
        MutableClock clock = new MutableClock(T0);
        clock.set(T0.plus(Duration.ofDays(7)));

        assertThat(matcher(null, clock).shouldReact(new Message(), "plain chatter")).isFalse();
        assertThat(matcher(Duration.ZERO, clock).shouldReact(new Message(), "plain chatter")).isFalse();
        assertThat(matcher(Duration.ofSeconds(-1), clock).shouldReact(new Message(), "plain chatter")).isFalse();
    }

    @Test
    @DisplayName("shouldReact resets the idle timer automatically when it returns true")
    void shouldReact_returnsTrue_resetsIdleTimer() {
        MutableClock clock = new MutableClock(T0);
        TriggerMatcher matcher = matcher(Duration.ofHours(2), clock);
        clock.set(T0.plus(Duration.ofHours(2)).plusSeconds(1));

        // First call returns true (idle elapsed) and is expected to reset the timer.
        assertThat(matcher.shouldReact(new Message(), "plain chatter")).isTrue();

        // Only 30 minutes later: should not fire again because the idle timer was reset.
        clock.set(clock.instant().plus(Duration.ofMinutes(30)));
        assertThat(matcher.shouldReact(new Message(), "plain chatter")).isFalse();
    }

    @Test
    @DisplayName("shouldReact does not reset the idle timer when it returns false")
    void shouldReact_returnsFalse_doesNotResetIdleTimer() {
        MutableClock clock = new MutableClock(T0);
        TriggerMatcher matcher = matcher(Duration.ofHours(2), clock);
        // 30 minutes in: not idle yet, should not react and should not reset.
        clock.set(T0.plus(Duration.ofMinutes(30)));
        assertThat(matcher.shouldReact(new Message(), "plain chatter")).isFalse();

        // Another 1h31m later (total ~2h1m past T0) – idle threshold elapsed since T0.
        clock.set(T0.plus(Duration.ofHours(2)).plusSeconds(1));
        assertThat(matcher.shouldReact(new Message(), "plain chatter")).isTrue();
    }

    private static TriggerMatcher matcher(Duration idleThreshold, Clock clock) {
        BotProperties botProps = new BotProperties(BOT_USERNAME, "123456:test-token", BOT_NAME, "English");
        AiTriggerProperties aiProps = new AiTriggerProperties(
                false, "1",
                Duration.ofMinutes(30), Duration.ofMinutes(1), Duration.ofMinutes(1),
                30, ZoneId.of("UTC"), idleThreshold);
        return new TriggerMatcher(botProps, aiProps, clock);
    }

    private static Message messageReplyingTo(String username) {
        Message reply = new Message();
        User user = new User();
        user.setUserName(username);
        reply.setFrom(user);

        Message message = new Message();
        message.setReplyToMessage(reply);
        return message;
    }

    /** Test-only {@link Clock} whose current instant can be moved forward at will. */
    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant initial) { this.now = initial; }
        void set(Instant instant) { this.now = instant; }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
