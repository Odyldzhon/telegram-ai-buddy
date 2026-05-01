package com.odyldzhon.bot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "bot.assistant")
public record AssistantProperties(Duration retryBackoff) {
}

