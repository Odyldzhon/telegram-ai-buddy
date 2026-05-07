package com.odyldzhon.bot.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "bot.assistant")
public record AssistantProperties(
        Duration retryBackoff,
        @NotBlank String personaPrompt) {
}

