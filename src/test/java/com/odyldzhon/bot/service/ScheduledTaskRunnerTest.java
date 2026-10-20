package com.odyldzhon.bot.service;

import com.odyldzhon.bot.ai.AssistantConversation;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.persistence.entity.ScheduledTaskEntity;
import com.odyldzhon.bot.persistence.entity.ScheduledTaskEntity.Status;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import com.odyldzhon.bot.telegram.TelegramBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledTaskRunnerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");
    private static final Instant CREATED = at("2026-04-01T00:00:00");

    @Mock private ScheduledTaskService taskService;
    @Mock private AssistantConversation assistantConversation;
    @Mock private TelegramBot telegramBot;
    @Mock private MessageStore messageStore;

    private ScheduledTaskRunner runner;

    @BeforeEach
    void setUp() {
        AiTriggerProperties properties = new AiTriggerProperties(
                false, false, "42", Duration.ofMinutes(30), Duration.ofMinutes(1), Duration.ofMinutes(1),
                30, ZONE, Duration.ofHours(2));
        BotProperties botProperties = new BotProperties("test_bot", "123456:test-token", "Lebowski", "English");
        runner = new ScheduledTaskRunner(taskService, assistantConversation, telegramBot, messageStore,
                botProperties, properties, Clock.fixed(at("2026-04-28T08:00:00"), ZONE));
        runner.setExecutorForTesting(Runnable::run);
    }

    @Test
    @DisplayName("Executes a due task, sends the reply, stores it and records the execution time")
    void dispatch_dueTask_executesAndMarksExecuted() {
        // Given
        Instant now = at("2026-04-28T08:00:00.200");
        when(taskService.findEnabled()).thenReturn(List.of(task(1L, "0 0 8 * * *", null)));
        when(assistantConversation.scheduledTask(eq("t"), eq("do it"), any(ZonedDateTime.class))).thenReturn("hello");
        when(telegramBot.sendText("42", "hello")).thenReturn(true);

        // When
        runner.dispatchDueTasks(now);

        // Then
        verify(telegramBot).sendText("42", "hello");
        verify(messageStore).save(eq("Lebowski"), eq("hello"), any(Instant.class));
        verify(taskService).markExecuted(eq(1L), any(Instant.class));
    }

    @Test
    @DisplayName("Does not execute a task that is not due")
    void dispatch_notDue_doesNothing() {
        when(taskService.findEnabled()).thenReturn(List.of(task(1L, "0 5 8 * * *", null)));

        runner.dispatchDueTasks(at("2026-04-28T08:00:00.200"));

        verify(assistantConversation, never()).scheduledTask(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Does not execute a task already executed for this occurrence")
    void dispatch_alreadyExecuted_doesNothing() {
        when(taskService.findEnabled())
                .thenReturn(List.of(task(1L, "0 0 8 * * *", at("2026-04-28T08:00:00.100"))));

        runner.dispatchDueTasks(at("2026-04-28T08:00:30"));

        verify(assistantConversation, never()).scheduledTask(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Does not execute an occurrence older than one minute")
    void dispatch_missedMoreThanOneMinuteAgo_doesNothing() {
        when(taskService.findEnabled()).thenReturn(List.of(task(1L, "0 0 8 * * *", null)));

        runner.dispatchDueTasks(at("2026-04-28T08:01:30"));

        verify(assistantConversation, never()).scheduledTask(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Does not fire an occurrence that precedes task creation")
    void dispatch_occurrenceBeforeCreation_doesNothing() {
        ScheduledTaskEntity task = task(1L, "0 0 8 * * *", null);
        task.setCreatedAt(at("2026-04-28T07:59:30"));
        when(taskService.findEnabled()).thenReturn(List.of(task));

        runner.dispatchDueTasks(at("2026-04-28T08:00:00.200"));

        verify(assistantConversation).scheduledTask(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Does not record execution when the assistant gives no reply")
    void dispatch_nullReply_doesNotMarkExecuted() {
        when(taskService.findEnabled()).thenReturn(List.of(task(1L, "0 0 8 * * *", null)));
        when(assistantConversation.scheduledTask(anyString(), anyString(), any())).thenReturn(null);

        runner.dispatchDueTasks(at("2026-04-28T08:00:00.200"));

        verify(telegramBot, never()).sendText(anyString(), anyString());
        verify(taskService, never()).markExecuted(any(), any());
    }

    @Test
    @DisplayName("Skips tasks with an invalid cron expression")
    void dispatch_invalidCron_isIgnored() {
        when(taskService.findEnabled()).thenReturn(List.of(task(1L, "garbage", null)));

        runner.dispatchDueTasks(at("2026-04-28T08:00:00.200"));

        verify(assistantConversation, never()).scheduledTask(anyString(), anyString(), any());
        assertThat(runner.isDue(task(1L, "garbage", null), at("2026-04-28T08:00:00"))).isFalse();
    }

    private static ScheduledTaskEntity task(Long id, String cron, Instant lastExecution) {
        return ScheduledTaskEntity.builder()
                .id(id).name("t").prompt("do it").schedule(cron)
                .status(Status.ENABLED).lastExecutionAt(lastExecution).createdAt(CREATED)
                .build();
    }

    private static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant();
    }
}
