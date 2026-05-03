package com.odyldzhon.bot.service;

import com.odyldzhon.bot.ai.AssistantConversation;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import com.odyldzhon.bot.telegram.TelegramBot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledAiTriggerServiceTest {

    private static final ZoneId UKRAINE_ZONE = ZoneId.of("Europe/Kyiv");
    private static final Clock DEFAULT_CLOCK = clockAtUkraine("2026-04-28T09:00:00");

    @Mock
    private MessageStore messageStore;

    @Mock
    private TelegramBot telegramBot;

    @Mock
    private AssistantConversation assistantConversation;

    @Mock
    private TaskScheduler taskScheduler;

    @Test
    @DisplayName("Does not schedule proactive triggers when disabled")
    void start_disabledProperties_doesNotScheduleTask() {
        // Given
        ScheduledAiTriggerService service = service(properties(false, "42"));

        // When
        service.start();

        // Then
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("Schedules the next trigger when enabled")
    void start_enabledProperties_schedulesTask() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"));

        // When
        service.start();

        // Then
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("Moves the next trigger to 08:00 Ukraine time when random delay lands in quiet hours")
    void start_delayLandsInQuietHours_schedulesAtQuietHoursEnd() {
        // Given
        ScheduledAiTriggerService service = service(
                properties(true, "42", Duration.ofMinutes(20), Duration.ofMinutes(20)),
                clockAtUkraine("2026-04-27T23:50:00"));

        // When
        service.start();

        // Then
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isEqualTo(instantAtUkraine("2026-04-28T08:00:00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-04-28T00:00:00", "2026-04-28T07:59:59.999999999"})
    @DisplayName("Skips proactive messages during Ukraine quiet hours")
    void runOnce_quietHours_skipsWithoutSending(String localDateTime) {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"), clockAtUkraine(localDateTime));

        // When
        service.runOnce();

        // Then
        verify(assistantConversation, never())
                .proactiveHistoryReply(any(Instant.class), anyInt(), anyString());
        verify(telegramBot, never()).sendText(anyString(), anyString());
    }

    @Test
    @DisplayName("Allows proactive messages at 08:00 Ukraine time")
    void runOnce_atQuietHoursEnd_allowsSending() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"), clockAtUkraine("2026-04-28T08:00:00"));
        when(assistantConversation.proactiveHistoryReply(any(Instant.class), anyInt(), anyString()))
                .thenReturn("Morning update.");
        when(telegramBot.sendText("42", "Morning update.")).thenReturn(true);

        // When
        service.runOnce();

        // Then
        verify(telegramBot).sendText("42", "Morning update.");
    }

    @Test
    @DisplayName("Skips proactive messages when latest stored message is from the bot")
    void runOnce_latestMessageFromBot_skipsWithoutSending() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"));
        when(messageStore.recent(1)).thenReturn(List.of(ChatMessageEntity.builder()
                .author("Lebowski")
                .build()));

        // When
        service.runOnce();

        // Then
        verify(messageStore).recent(1);
        verify(assistantConversation, never())
                .proactiveHistoryReply(any(Instant.class), anyInt(), anyString());
        verify(telegramBot, never()).sendText(anyString(), anyString());
        verify(messageStore, never()).save(anyString(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("Delegates to AssistantConversation, sends the reply, and stores it")
    void runOnce_recentHumanMessage_sendsReplyAndStoresIt() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"));
        when(assistantConversation.proactiveHistoryReply(any(Instant.class), eq(30), eq("Lebowski")))
                .thenReturn("Could you check history, please?");
        when(telegramBot.sendText("42", "Could you check history, please?")).thenReturn(true);

        // When
        service.runOnce();

        // Then
        verify(telegramBot).sendText("42", "Could you check history, please?");
        verify(messageStore).save(eq("Lebowski"), eq("Could you check history, please?"), any(Instant.class));
    }

    @Test
    @DisplayName("Does not send a message when the assistant returns null")
    void runOnce_blankAiReply_doesNotSendMessage() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"));
        when(assistantConversation.proactiveHistoryReply(any(Instant.class), anyInt(), anyString()))
                .thenReturn(null);

        // When
        service.runOnce();

        // Then
        verify(telegramBot, never()).sendText(anyString(), anyString());
    }

    @Test
    @DisplayName("Sends a daily joke to the configured chat ID")
    void sendDailyJoke_sendsJokeToConfiguredChatId() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"));
        String joke = "Why don't scientists trust atoms? Because they make up everything!";
        when(assistantConversation.dailyJoke()).thenReturn(joke);
        when(telegramBot.sendText("42", joke)).thenReturn(true);

        // When
        service.sendDailyJoke();

        // Then
        verify(telegramBot).sendText("42", joke);
    }

    @Test
    @DisplayName("Sends a daily news digest for today's date in the configured zone")
    void sendNews_sendsDigestForToday() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"));
        String digest = "Today's headlines...";
        when(assistantConversation.newsDigest(any())).thenReturn(digest);
        when(telegramBot.sendText("42", digest)).thenReturn(true);

        // When
        service.sendNews();

        // Then
        verify(assistantConversation).newsDigest(LocalDateTime.parse("2026-04-28T09:00:00")
                .atZone(UKRAINE_ZONE).toLocalDate());
        verify(telegramBot).sendText("42", digest);
    }

    private ScheduledAiTriggerService service(AiTriggerProperties properties) {
        return service(properties, DEFAULT_CLOCK);
    }

    private ScheduledAiTriggerService service(AiTriggerProperties properties, Clock clock) {
        ScheduledAiTriggerService service = new ScheduledAiTriggerService(
                properties, botProperties(), messageStore, telegramBot, assistantConversation, taskScheduler);
        service.setClockForTesting(clock);
        return service;
    }

    private static BotProperties botProperties() {
        return new BotProperties("test_bot", "123456:test-token", "Lebowski", "English");
    }

    private static AiTriggerProperties properties(boolean enabled, String chatId) {
        return properties(enabled, chatId, Duration.ofMillis(1), Duration.ofMillis(1));
    }

    private static AiTriggerProperties properties(boolean enabled, String chatId, Duration minDelay, Duration maxDelay) {
        return new AiTriggerProperties(
                enabled,
                chatId,
                Duration.ofMinutes(30),
                minDelay,
                maxDelay,
                30,
                UKRAINE_ZONE,
                Duration.ofHours(2));
    }

    private static Clock clockAtUkraine(String localDateTime) {
        return Clock.fixed(instantAtUkraine(localDateTime), UKRAINE_ZONE);
    }

    private static Instant instantAtUkraine(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(UKRAINE_ZONE).toInstant();
    }
}