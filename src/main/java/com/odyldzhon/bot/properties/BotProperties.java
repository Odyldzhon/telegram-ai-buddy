package com.odyldzhon.bot.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bot")
public record BotProperties(
        @NotBlank String username,
        @NotBlank String token,
        @NotBlank String name,
        @NotBlank String language) {
}

