package com.odyldzhon.bot.ai;

import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantConversationTest {

    @Mock private MessageStore messageStore;
    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;

    @Test
    @DisplayName("Inlines recent history into the prompt and returns the assistant's content")
    void reply_withHistory_buildsPromptAndReturnsContent() {
        // Given
        AssistantConversation conv = new AssistantConversation(messageStore, chatClient);
        when(messageStore.recent(anyInt())).thenReturn(List.of(
                ChatMessageEntity.builder()
                        .author("odyld").message("Lebowski, are you here?")
                        .createdAt(Instant.ofEpochSecond(1_777_398_401)).build(),
                ChatMessageEntity.builder()
                        .author("walter").message("Earlier message")
                        .createdAt(Instant.ofEpochSecond(1_777_398_300)).build()));
        when(chatClient.prompt()).thenReturn(requestSpec);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(requestSpec.user(promptCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Тут.");

        // When
        String reply = conv.reply("odyld", "Lebowski, are you here?");

        // Then
        assertThat(reply).isEqualTo("Тут.");
        verify(messageStore).recent(AssistantConversation.RECENT_CONTEXT_MESSAGES);
        assertThat(promptCaptor.getValue())
                .contains("Recent chat history")
                .contains("odyld: Lebowski, are you here?")
                .contains("walter: Earlier message")
                .contains("Author: @odyld");
    }

    @Test
    @DisplayName("Falls back to a '(no prior messages)' marker when history is empty")
    void reply_emptyHistory_usesEmptyMarker() {
        // Given
        AssistantConversation conv = new AssistantConversation(messageStore, chatClient);
        when(messageStore.recent(anyInt())).thenReturn(List.of());
        when(chatClient.prompt()).thenReturn(requestSpec);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(requestSpec.user(promptCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("hi");

        // When
        conv.reply("odyld", "yo");

        // Then
        assertThat(promptCaptor.getValue()).contains("(no prior messages)");
    }

    @Test
    @DisplayName("Returns null when the assistant call throws")
    void reply_chatClientThrows_returnsNull() {
        // Given
        AssistantConversation conv = new AssistantConversation(messageStore, chatClient);
        when(chatClient.prompt()).thenThrow(new RuntimeException("AI is down"));

        // When
        String reply = conv.reply("odyld", "yo");

        // Then
        assertThat(reply).isNull();
    }

    @Test
    @DisplayName("Still calls the assistant with the empty marker when history lookup fails")
    void reply_historyLookupFails_stillCallsAssistantWithEmptyMarker() {
        // Given
        AssistantConversation conv = new AssistantConversation(messageStore, chatClient);
        when(messageStore.recent(anyInt())).thenThrow(new RuntimeException("db down"));
        when(chatClient.prompt()).thenReturn(requestSpec);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(requestSpec.user(promptCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("ok");

        // When
        String reply = conv.reply("odyld", "yo");

        // Then
        assertThat(reply).isEqualTo("ok");
        assertThat(promptCaptor.getValue()).contains("(no prior messages)");
    }
}
