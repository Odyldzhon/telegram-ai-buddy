package com.odyldzhon.bot.properties;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BotPropertiesConfiguration.class);

    @Test
    @DisplayName("Stores Telegram bot configuration values")
    void botProperties_constructor_returnsConfiguredValues() {
        // Given
        BotProperties properties = new BotProperties("bot_name", "token", "Assistant", "English");

        // When
        String username = properties.username();
        String token = properties.token();
        String name = properties.name();
        String language = properties.language();

        // Then
        assertThat(username).isEqualTo("bot_name");
        assertThat(token).isEqualTo("token");
        assertThat(name).isEqualTo("Assistant");
        assertThat(language).isEqualTo("English");
    }

    @Test
    @DisplayName("Fails validation when required bot values are blank")
    void botProperties_blankRequiredValues_hasValidationViolations() {
        // Given
        BotProperties properties = new BotProperties("bot_name", "token", " ", null);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        // When
        Set<ConstraintViolation<BotProperties>> violations = validator.validate(properties);

        // Then
        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "language");
    }

    @Test
    @DisplayName("Fails Spring context startup when a required bot property is blank")
    void botProperties_blankRequiredProperty_failsContextStartup() {
        contextRunner
                .withPropertyValues(
                        "bot.username=test_bot",
                        "bot.token=123456:test-token",
                        "bot.name= ",
                        "bot.language=English")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasStackTraceContaining("bot.name"));
    }

    @Test
    @DisplayName("Stores proactive AI trigger configuration values")
    void aiTriggerProperties_constructor_returnsConfiguredValues() {
        // Given
        AiTriggerProperties properties = new AiTriggerProperties(
                true,
                "42",
                Duration.ofHours(2),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                20,
                ZoneId.of("Europe/Kyiv"),
                Duration.ofHours(2));

        // When
        boolean enabled = properties.enabled();
        String chatId = properties.chatId();
        Duration historyWindow = properties.historyWindow();
        Duration minDelay = properties.minDelay();
        Duration maxDelay = properties.maxDelay();
        int maxHistoryMessages = properties.maxHistoryMessages();
        ZoneId timeZone = properties.timeZone();
        Duration idleThreshold = properties.idleParticipationThreshold();

        // Then
        assertThat(enabled).isTrue();
        assertThat(chatId).isEqualTo("42");
        assertThat(historyWindow).isEqualTo(Duration.ofHours(2));
        assertThat(minDelay).isEqualTo(Duration.ofMinutes(5));
        assertThat(maxDelay).isEqualTo(Duration.ofMinutes(10));
        assertThat(maxHistoryMessages).isEqualTo(20);
        assertThat(timeZone).isEqualTo(ZoneId.of("Europe/Kyiv"));
        assertThat(idleThreshold).isEqualTo(Duration.ofHours(2));
    }

    @Test
    @DisplayName("Fails Spring context startup when proactive AI trigger timezone is missing")
    void aiTriggerProperties_missingTimeZone_failsContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiTriggerPropertiesConfiguration.class)
                .withPropertyValues(
                        "bot.ai-trigger.enabled=true",
                        "bot.ai-trigger.chat-id=42",
                        "bot.ai-trigger.history-window=PT30M",
                        "bot.ai-trigger.min-delay=PT1M",
                        "bot.ai-trigger.max-delay=PT2M",
                        "bot.ai-trigger.max-history-messages=30")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasStackTraceContaining("timeZone"));
    }

    @Test
    @DisplayName("Stores assistant configuration values")
    void assistantProperties_constructor_returnsConfiguredValues() {
        // Given
        AssistantProperties properties = new AssistantProperties(Duration.ofSeconds(1), "Configured persona");

        // When / Then
        assertThat(properties.retryBackoff()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.personaPrompt()).isEqualTo("Configured persona");
    }

    @Test
    @DisplayName("Fails Spring context startup when assistant persona prompt is blank")
    void assistantProperties_blankPersonaPrompt_failsContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(AssistantPropertiesConfiguration.class)
                .withPropertyValues(
                        "bot.assistant.retry-backoff=1s",
                        "bot.assistant.persona-prompt= ")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasStackTraceContaining("personaPrompt"));
    }

    @Test
    @DisplayName("Binds native Google Search retrieval option for Gemini chat")
    void googleGenAiChatProperties_googleSearchRetrieval_bindsToOptions() {
        new ApplicationContextRunner()
                .withUserConfiguration(GoogleGenAiChatPropertiesConfiguration.class)
                .withPropertyValues("spring.ai.google.genai.chat.options.google-search-retrieval=true")
                .run(context -> assertThat(context.getBean(GoogleGenAiChatProperties.class)
                        .getOptions()
                        .getGoogleSearchRetrieval()).isTrue());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BotProperties.class)
    static class BotPropertiesConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiTriggerProperties.class)
    static class AiTriggerPropertiesConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AssistantProperties.class)
    static class AssistantPropertiesConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GoogleGenAiChatProperties.class)
    static class GoogleGenAiChatPropertiesConfiguration {
    }
}