package com.odyldzhon.bot.service;

import com.odyldzhon.bot.ai.AssistantConversation;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import com.odyldzhon.bot.telegram.TelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@Service
public class ScheduledAiTriggerService {

    private static final LocalTime QUIET_HOURS_END = LocalTime.of(8, 0);

    private final AiTriggerProperties properties;
    private final BotProperties botProperties;
    private final MessageStore messageStore;
    private final TelegramBot telegramBot;
    private final AssistantConversation assistantConversation;
    private final TaskScheduler taskScheduler;
    private Clock clock;

    public ScheduledAiTriggerService(
            AiTriggerProperties properties,
            BotProperties botProperties,
            MessageStore messageStore,
            TelegramBot telegramBot,
            AssistantConversation assistantConversation,
            @Qualifier("proactiveAiTaskScheduler") TaskScheduler taskScheduler) {
        this.properties = properties;
        this.botProperties = botProperties;
        this.messageStore = messageStore;
        this.telegramBot = telegramBot;
        this.assistantConversation = assistantConversation;
        this.taskScheduler = taskScheduler;
        this.clock = Clock.systemUTC();
    }

    void setClockForTesting(Clock clock) {
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Kiev")
    public void sendDailyJoke() {
        sendAndSave(properties.chatId(), assistantConversation.dailyJoke());
    }

    @Scheduled(cron = "0 5 8 * * *", zone = "Europe/Kiev")
    public void sendNews() {
        sendAndSave(properties.chatId(),
                assistantConversation.newsDigest(LocalDate.now(clock.withZone(properties.timeZone()))));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled()) {
            log.info("Proactive AI trigger is disabled");
            return;
        }
        scheduleNext();
    }

    private void runSafely() {
        try {
            runOnce();
        } catch (Exception e) {
            log.error("Proactive AI trigger failed: {}", e.getMessage(), e);
        } finally {
            scheduleNext();
        }
    }

    private void scheduleNext() {
        Duration delay = randomDelay();
        Instant nextRun = nextAllowedRunAt(Instant.now(clock).plus(delay));
        taskScheduler.schedule(this::runSafely, nextRun);
        long minutesUntilNextRun = Math.max(1, Duration.between(Instant.now(clock), nextRun).toMinutes());
        log.info("Next proactive AI trigger scheduled in {} minutes", minutesUntilNextRun);
    }

    void runOnce() {
        Instant now = Instant.now(clock);
        if (isQuietTime(now)) {
            log.info("Skipping proactive AI trigger during quiet hours in {} timezone", properties.timeZone());
            return;
        }
        Instant since = now.minus(properties.historyWindow());
        String reply = assistantConversation.proactiveHistoryReply(
                since, properties.maxHistoryMessages(), botProperties.name());
        sendAndSave(properties.chatId(), reply);
    }

    private void sendAndSave(String chatId, String reply) {
        if (reply == null) {
            return;
        }

        if (telegramBot.sendText(chatId, reply)) {
            try {
                messageStore.save(botProperties.name(), reply, Instant.now(clock));
            } catch (Exception e) {
                log.error("Failed to store proactive AI reply: {}", e.getMessage(), e);
            }
        }
    }

    private Duration randomDelay() {
        long minMillis = properties.minDelay().toMillis();
        long maxMillis = properties.maxDelay().toMillis();
        if (minMillis == maxMillis) {
            return Duration.ofMillis(minMillis);
        }
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1));
    }

    private Instant nextAllowedRunAt(Instant candidate) {
        if (!isQuietTime(candidate)) {
            return candidate;
        }
        ZonedDateTime candidateInConfiguredZone = candidate.atZone(properties.timeZone());
        return candidateInConfiguredZone.toLocalDate()
                .atTime(QUIET_HOURS_END)
                .atZone(properties.timeZone())
                .toInstant();
    }

    private boolean isQuietTime(Instant instant) {
        return instant.atZone(properties.timeZone()).toLocalTime().isBefore(QUIET_HOURS_END);
    }
}
