package com.odyldzhon.bot.telegram;

import com.odyldzhon.bot.ai.ImageDescriber;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramBotTest {

    /** Single chat the bot is allowed to listen to / answer in. */
    private static final String ALLOWED_CHAT_ID = "123";

    @Mock
    private MessageStore messageStore;

    @Mock
    private ImageDescriber imageDescriber;

    @Mock
    private ChatClient assistantChatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Test
    @DisplayName("Returns the configured bot username")
    void getBotUsername_configuredProperties_returnsUsername() {
        // Given
        TelegramBot bot = newBot();

        // When
        String result = bot.getBotUsername();

        // Then
        assertThat(result).isEqualTo("test_bot");
    }

    @Test
    @DisplayName("Ignores updates that do not contain a message")
    void onUpdateReceived_updateWithoutMessage_doesNothing() {
        // Given
        TelegramBot bot = newBot();
        Update update = new Update();

        // When
        bot.onUpdateReceived(update);

        // Then
        verify(messageStore, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Ignores messages coming from chats other than the configured one")
    void onUpdateReceived_messageFromUnrelatedChat_isIgnored() {
        // Given
        TestableTelegramBot bot = newBot();
        Update update = textUpdate(999L, "intruder", "Hey, Lebowski, hi", 1_777_398_410);

        // When
        bot.onUpdateReceived(update);

        // Then
        verify(messageStore, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(assistantChatClient, never()).prompt();
        assertThat(bot.sentMessages).isEmpty();
    }

    @Test
    @DisplayName("Stores ordinary text messages from the configured chat")
    void onUpdateReceived_textWithoutTrigger_storesMessage() {
        // Given
        TelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "Hello room", 1_777_398_400);

        // When
        bot.onUpdateReceived(update);

        // Then
        verify(messageStore).save("odyld", "Hello room", Instant.ofEpochSecond(1_777_398_400));
    }

    @Test
    @DisplayName("Calls AI, sends a reply, and stores that reply when trigger text is present")
    void onUpdateReceived_textWithTrigger_sendsAndStoresAiReply() {
        // Given
        TestableTelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "Hey, Lebowski, what do you think?", 1_777_398_401);
        when(assistantChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Нормально, держимся.");

        // When
        bot.onUpdateReceived(update);

        // Then
        assertThat(bot.sentMessages).containsExactly(new SentMessage("123", "Нормально, держимся."));
        ArgumentCaptor<String> authorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageStore, org.mockito.Mockito.times(2))
                .save(authorCaptor.capture(), messageCaptor.capture(), org.mockito.ArgumentMatchers.any(Instant.class));
        assertThat(authorCaptor.getAllValues()).containsExactly("odyld", "Lebowski");
        assertThat(messageCaptor.getAllValues()).containsExactly("Hey, Lebowski, what do you think?", "Нормально, держимся.");
    }

    @Test
    @DisplayName("Sends a fallback message when the AI call fails")
    void onUpdateReceived_aiFailure_sendsFallbackMessage() {
        // Given
        TestableTelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "Lebowski?", 1_777_398_402);
        when(assistantChatClient.prompt()).thenThrow(new RuntimeException("AI is down"));

        // When
        bot.onUpdateReceived(update);

        // Then
        assertThat(bot.sentMessages)
                .containsExactly(new SentMessage("123", "⚠️ Lebowski is unavailable right now."));
    }

    private TestableTelegramBot newBot() {
        return new TestableTelegramBot(
                botProperties(), aiTriggerProperties(), messageStore, imageDescriber, assistantChatClient);
    }

    private static BotProperties botProperties() {
        return new BotProperties("test_bot", "123456:test-token", "Lebowski", "English");
    }

    private static AiTriggerProperties aiTriggerProperties() {
        return new AiTriggerProperties(
                false,
                ALLOWED_CHAT_ID,
                Duration.ofMinutes(30),
                Duration.ofMinutes(1),
                Duration.ofMinutes(1),
                30,
                ZoneId.of("Europe/Kyiv"));
    }

    private static Update textUpdate(long chatId, String username, String text, int epochSecond) {
        User user = new User();
        user.setUserName(username);
        user.setFirstName("First");

        Chat chat = new Chat();
        chat.setId(chatId);

        Message message = new Message();
        message.setChat(chat);
        message.setFrom(user);
        message.setText(text);
        message.setDate(epochSecond);

        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private record SentMessage(String chatId, String text) {
    }

    private static class TestableTelegramBot extends TelegramBot {
        private final List<SentMessage> sentMessages = new ArrayList<>();

        TestableTelegramBot(BotProperties botProperties,
                            AiTriggerProperties aiTriggerProperties,
                            MessageStore messageStore,
                            ImageDescriber imageDescriber,
                            ChatClient assistantChatClient) {
            super(botProperties, aiTriggerProperties, messageStore, imageDescriber, assistantChatClient);
        }

        @Override
        public boolean sendText(String chatId, String text) {
            sentMessages.add(new SentMessage(chatId, text));
            return true;
        }
    }
}

