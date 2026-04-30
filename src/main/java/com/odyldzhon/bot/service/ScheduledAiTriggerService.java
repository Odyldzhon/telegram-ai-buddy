package com.odyldzhon.bot.service;

import com.odyldzhon.bot.configuration.ChatClientConfig;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import com.odyldzhon.bot.telegram.TelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@Service
public class ScheduledAiTriggerService {

    private static final LocalTime QUIET_HOURS_END = LocalTime.of(8, 0);

    private final AiTriggerProperties properties;
    private final BotProperties botProperties;
    private final MessageStore messageStore;
    private final TelegramBot telegramBot;
    private final ChatClient assistantChatClient;
    private final TaskScheduler taskScheduler;
    private Clock clock;

    public ScheduledAiTriggerService(
            AiTriggerProperties properties,
            BotProperties botProperties,
            MessageStore messageStore,
            TelegramBot telegramBot,
            @Qualifier(ChatClientConfig.ASSISTANT) ChatClient assistantChatClient,
            @Qualifier("proactiveAiTaskScheduler") TaskScheduler taskScheduler) {
        this.properties = properties;
        this.botProperties = botProperties;
        this.messageStore = messageStore;
        this.telegramBot = telegramBot;
        this.assistantChatClient = assistantChatClient;
        this.taskScheduler = taskScheduler;
        this.clock = Clock.systemUTC();
    }

    void setClockForTesting(Clock clock) {
        this.clock = clock;
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

    private void runOnce() {
        Instant now = Instant.now(clock);
        if (isQuietTime(now)) {
            log.info("Skipping proactive AI trigger during quiet hours in {} timezone", properties.timeZone());
            return;
        }

        String chatId = properties.chatId();

        Instant since = now.minus(properties.historyWindow());
        Optional<ChatMessageEntity> latestMessage = messageStore.latestMessage(since);

        String prompt;
        if (latestMessage.isPresent() && !botProperties.name().equals(latestMessage.get().getAuthor())) {
            prompt = historyPrompt(since);
        } else {
            prompt = newsPrompt();
        }

        String reply = askAi(prompt);
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


    private String historyPrompt(Instant since) {
        return """
                You are about to proactively join the Telegram chat without being directly mentioned.
                Before answering, use your database tools to inspect recent chat history yourself.

                Required lookup:
                - Call executeSelectQuery with a SELECT similar to:
                  SELECT created_at, author, message
                  FROM chat_message
                  WHERE created_at >= '%s'
                  ORDER BY created_at DESC
                  LIMIT %d
                - Do not select the embedding column.
                - The query result includes your own earlier messages under author "%s"; use them to avoid repeating yourself.
                - After reading the newest rows, reason about the conversation chronologically before replying.

                Rules:
                - Be concise: 1-3 short paragraphs or bullets.
                - Do not say you are scheduled, automated, or that no one mentioned you.
                - Do not repeat the whole history; add insight, a helpful answer, a witty observation, or a question.
                - If the recent messages are unclear, make a light contextual comment rather than forcing an answer.
                """.formatted(since, properties.maxHistoryMessages(), botProperties.name());
    }

    private String newsPrompt() {
        return """
                The Telegram chat has been quiet. Post ONE interesting comment based on important news from today (%s).

                Use your available web/search grounding integration to look up current news before answering.
                Prefer financial, technological, or politically important news from reputable sources.

                Rules:
                - Keep it conversational and concise: 2-4 sentences.
                - Mention why the item matters, not just the headline.
                - Do not include more than one link, and links are optional.
                - If web/search grounding is unavailable, say that current-news lookup is unavailable and do not invent specific facts.
                """.formatted(LocalDate.now(clock.withZone(properties.timeZone())));
    }

    private String askAi(String prompt) {
        try {
            String reply = assistantChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (reply == null || reply.isBlank()) {
                log.warn("Proactive AI returned an empty reply");
                return null;
            }
            return reply.trim();
        } catch (Exception e) {
            log.error("Proactive AI request failed: {}", e.getMessage(), e);
            return null;
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
