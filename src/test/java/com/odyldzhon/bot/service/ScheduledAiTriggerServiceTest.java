package com.odyldzhon.bot.service;

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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private ChatClient assistantChatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

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
        ReflectionTestUtils.invokeMethod(service, "runOnce");

        // Then
        verify(messageStore, never()).latestMessage(any(Instant.class));
        verify(assistantChatClient, never()).prompt();
        verify(telegramBot, never()).sendText(anyString(), anyString());
    }

    @Test
    @DisplayName("Allows proactive messages at 08:00 Ukraine time")
    void runOnce_atQuietHoursEnd_allowsSending() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"), clockAtUkraine("2026-04-28T08:00:00"));
        when(messageStore.latestMessage(any(Instant.class))).thenReturn(Optional.empty());
        mockAiReply("Morning update.");
        when(telegramBot.sendText("42", "Morning update.")).thenReturn(true);

        // When
        ReflectionTestUtils.invokeMethod(service, "runOnce");

        // Then
        verify(telegramBot).sendText("42", "Morning update.");
    }

    @Test
    @DisplayName("Skips one run when the newest recent message is already from the AI")
    void runOnce_latestMessageFromAi_skipsAiCall() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"));
        ChatMessageEntity latest = ChatMessageEntity.builder()
                .author("Lebowski")
                .message("already spoke")
                .createdAt(Instant.now())
                .build();
        when(messageStore.latestMessage(any(Instant.class))).thenReturn(Optional.of(latest));

        // When
        ReflectionTestUtils.invokeMethod(service, "runOnce");

        // Then
        verify(assistantChatClient, never()).prompt();
        verify(telegramBot, never()).sendText(anyString(), anyString());
    }

    @Test
    @DisplayName("Uses recent-history prompt, sends the AI reply, and stores it")
    void runOnce_recentHumanMessage_sendsReplyAndStoresIt() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"));
        ChatMessageEntity latest = ChatMessageEntity.builder()
                .author("odyld")
                .message("what happened?")
                .createdAt(Instant.now())
                .build();
        when(messageStore.latestMessage(any(Instant.class))).thenReturn(Optional.of(latest));
        mockAiReply("Could you check history, please?");
        when(telegramBot.sendText("42", "Could you check history, please?")).thenReturn(true);

        // When
        ReflectionTestUtils.invokeMethod(service, "runOnce");

        // Then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("inspect recent chat history")
                .contains("LIMIT 30");
        verify(telegramBot).sendText("42", "Could you check history, please?");
        verify(messageStore).save(org.mockito.ArgumentMatchers.eq("Lebowski"),
                org.mockito.ArgumentMatchers.eq("Could you check history, please?"), any(Instant.class));
    }

    @Test
    @DisplayName("Uses the configured chat id and news prompt when chat history is quiet")
    void runOnce_noRecentMessage_usesConfiguredChatIdAndNewsPrompt() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "99"));
        when(messageStore.latestMessage(any(Instant.class))).thenReturn(Optional.empty());
        mockAiReply("Новость дня.");
        when(telegramBot.sendText("99", "Новость дня.")).thenReturn(true);

        // When
        ReflectionTestUtils.invokeMethod(service, "runOnce");

        // Then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("important news from today");
        verify(telegramBot).sendText("99", "Новость дня.");
    }

    @Test
    @DisplayName("Does not send a message when the AI returns a blank reply")
    void runOnce_blankAiReply_doesNotSendMessage() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"));
        when(messageStore.latestMessage(any(Instant.class))).thenReturn(Optional.empty());
        mockAiReply("  ");

        // When
        ReflectionTestUtils.invokeMethod(service, "runOnce");

        // Then
        verify(telegramBot, never()).sendText(anyString(), anyString());
    }

    @Test
    @DisplayName("Truncates overlong AI replies before sending")
    void runOnce_overlongAiReply_sendsTruncatedReply() {
        // Given
        ScheduledAiTriggerService service = service(properties(true, "42"));
        when(messageStore.latestMessage(any(Instant.class))).thenReturn(Optional.empty());
        String longReply = "x".repeat(4_000);
        mockAiReply(longReply);
        when(telegramBot.sendText(org.mockito.ArgumentMatchers.eq("42"), anyString())).thenReturn(true);

        // When
        ReflectionTestUtils.invokeMethod(service, "runOnce");

        // Then
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramBot).sendText(org.mockito.ArgumentMatchers.eq("42"), replyCaptor.capture());
        assertThat(replyCaptor.getValue())
                .hasSize(3_500)
                .endsWith("…");
    }

    private ScheduledAiTriggerService service(AiTriggerProperties properties) {
        return service(properties, DEFAULT_CLOCK);
    }

    private ScheduledAiTriggerService service(AiTriggerProperties properties, Clock clock) {
        ScheduledAiTriggerService service = new ScheduledAiTriggerService(
                properties, botProperties(), messageStore, telegramBot, assistantChatClient, taskScheduler);
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
                UKRAINE_ZONE);
    }

    private static Clock clockAtUkraine(String localDateTime) {
        return Clock.fixed(instantAtUkraine(localDateTime), UKRAINE_ZONE);
    }

    private static Instant instantAtUkraine(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(UKRAINE_ZONE).toInstant();
    }

    private void mockAiReply(String reply) {
        when(assistantChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(reply);
    }
}


