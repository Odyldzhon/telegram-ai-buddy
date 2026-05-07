package com.odyldzhon.bot.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "bot.ai-trigger")
public record AiTriggerProperties(
        boolean enabled,
        boolean sendDailyJokeEnabled,
        boolean sendNewsEnabled,
        @NotBlank String chatId,
        Duration historyWindow,
        Duration minDelay,
        Duration maxDelay,
        int maxHistoryMessages,
        @NotNull ZoneId timeZone,
        Duration idleParticipationThreshold) {

}
