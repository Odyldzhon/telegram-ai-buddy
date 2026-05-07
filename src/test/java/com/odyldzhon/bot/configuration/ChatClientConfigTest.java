package com.odyldzhon.bot.configuration;

import com.odyldzhon.bot.ai.DatabaseTools;
import com.odyldzhon.bot.properties.AssistantProperties;
import com.odyldzhon.bot.properties.BotProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatClientConfigTest {

    @Mock
    private ChatClient.Builder builder;

    @Mock
    private DatabaseTools databaseTools;

    @Mock
    private ChatClient chatClient;

    @Test
    @DisplayName("Builds assistant chat client with persona prompt and database tools")
    void assistantChatClient_validBuilder_configuresSystemPromptAndTools() {
        // Given
        ChatClientConfig config = new ChatClientConfig();
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(databaseTools)).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        // When
        ChatClient result = config.assistantChatClient(builder, databaseTools, botProperties(), assistantProperties());

        // Then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).defaultSystem(promptCaptor.capture());
        verify(builder).defaultTools(databaseTools);
        assertThat(promptCaptor.getValue())
                .contains("Test persona from environment")
                .contains("Communication language: English")
                .doesNotContain("COMMON_PERSONA_PROMPT");
        assertThat(result).isSameAs(chatClient);
    }

    @Test
    @DisplayName("Builds image chat client with image-only description prompt")
    void imageChatClient_validBuilder_configuresImagePromptWithoutTools() {
        // Given
        ChatClientConfig config = new ChatClientConfig();
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        // When
        ChatClient result = config.imageChatClient(builder, botProperties());

        // Then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).defaultSystem(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("describe images")
                .contains("Communication language: English")
                .contains("Do NOT add any prefix");
        assertThat(result).isSameAs(chatClient);
    }

    private static BotProperties botProperties() {
        return new BotProperties("test_bot", "123456:test-token", "Lebowski", "English");
    }

    private static AssistantProperties assistantProperties() {
        return new AssistantProperties(Duration.ZERO, "Test persona from environment");
    }
}