package com.odyldzhon.bot.telegram;

import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
public class TriggerMatcher {

    private final BotProperties botProperties;
    private final AiTriggerProperties aiTriggerProperties;
    private final Clock clock;
    private volatile Instant lastBotReplyAt;

    public TriggerMatcher(BotProperties botProperties,
                          AiTriggerProperties aiTriggerProperties,
                          Clock clock) {
        this.botProperties = botProperties;
        this.aiTriggerProperties = aiTriggerProperties;
        this.clock = clock;
        this.lastBotReplyAt = Instant.now(clock);
    }

    public boolean shouldReact(Message message, String triggerText) {
        boolean react = mentionsBot(triggerText)
                || isReplyToBot(message)
                || isIdle();
        if (react) {
            markBotReplied();
        }
        return react;
    }

    private void markBotReplied() {
        this.lastBotReplyAt = Instant.now(clock);
    }

    private boolean mentionsBot(String text) {
        String botName = botProperties.name();
        if (text == null || botName == null || botName.isBlank()) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT)
                .contains(botName.toLowerCase(Locale.ROOT));
    }

    private boolean isReplyToBot(Message message) {
        String botUsername = botProperties.username();
        if (message == null || botUsername == null || botUsername.isBlank()) {
            return false;
        }
        Message replyTo = message.getReplyToMessage();
        if (replyTo == null || replyTo.getFrom() == null) {
            return false;
        }
        return botUsername.equalsIgnoreCase(replyTo.getFrom().getUserName());
    }

    private boolean isIdle() {
        Duration threshold = aiTriggerProperties.idleParticipationThreshold();
        if (threshold == null || threshold.isZero() || threshold.isNegative()) {
            return false;
        }
        Instant now = Instant.now(clock);
        boolean idle = Duration.between(lastBotReplyAt, now).compareTo(threshold) >= 0;
        if (idle) {
            log.info("Bot has been idle for >= {}; reacting to incoming message", threshold);
        }
        return idle;
    }
}
