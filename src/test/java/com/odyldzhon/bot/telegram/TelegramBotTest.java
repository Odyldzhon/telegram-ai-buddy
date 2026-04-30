package com.odyldzhon.bot.telegram;

import com.odyldzhon.bot.ai.AssistantConversation;
import com.odyldzhon.bot.ai.ImageDescriber;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramBotTest {

    /** Single chat the bot is allowed to listen to / answer in. */
    private static final String ALLOWED_CHAT_ID = "123";

    @Mock private MessageStore messageStore;
    @Mock private ImageDescriber imageDescriber;
    @Mock private AssistantConversation assistantConversation;
    @Mock private TypingIndicator typingIndicator;

    /**
     * Default behaviour: TypingIndicator runs the supplier directly and also
     * fires the ping once, so tests can assert both.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    void wireTypingIndicatorPassThrough() {
        org.mockito.Mockito.lenient()
                .when(typingIndicator.runWith(any(Runnable.class), any(Supplier.class)))
                .thenAnswer(inv -> {
                    Runnable ping = inv.getArgument(0);
                    Supplier<?> task = inv.getArgument(1);
                    ping.run();
                    return task.get();
                });
    }

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
        verify(messageStore, never()).save(any(), any(), any());
        verify(assistantConversation, never()).reply(any(), any());
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
        verify(messageStore, never()).save(any(), any(), any());
        verify(assistantConversation, never()).reply(any(), any());
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
        verify(assistantConversation, never()).reply(any(), any());
    }

    @Test
    @DisplayName("Calls the assistant, sends its reply, stores it, and shows typing")
    @SuppressWarnings("unchecked")
    void onUpdateReceived_textWithTrigger_sendsAndStoresAiReply() {
        // Given
        TestableTelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "Hey, Lebowski, what do you think?", 1_777_398_401);
        when(assistantConversation.reply("odyld", "Hey, Lebowski, what do you think?"))
                .thenReturn("Нормально, держимся.");

        // When
        bot.onUpdateReceived(update);

        // Then
        assertThat(bot.sentMessages)
                .containsExactly(new SentMessage("123", "Нормально, держимся."));
        verify(messageStore, times(2))
                .save(any(String.class), any(String.class), any(Instant.class));
        verify(messageStore).save("odyld", "Hey, Lebowski, what do you think?",
                Instant.ofEpochSecond(1_777_398_401));
        verify(messageStore).save(eq("Lebowski"), eq("Нормально, держимся."), any(Instant.class));
        verify(typingIndicator).runWith(any(Runnable.class), any(Supplier.class));
        assertThat(bot.typingActions).contains("123");
    }

    @Test
    @DisplayName("Replies when the user replies to one of the bot's own messages without naming it")
    void onUpdateReceived_replyToBotMessage_triggersAssistant() {
        // Given
        TestableTelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "and what about now?", 1_777_398_403);
        Message botMessage = new Message();
        User botUser = new User();
        botUser.setUserName("test_bot");
        botMessage.setFrom(botUser);
        botMessage.setText("previous bot reply");
        update.getMessage().setReplyToMessage(botMessage);
        when(assistantConversation.reply("odyld", "and what about now?"))
                .thenReturn("Всё ещё держимся.");

        // When
        bot.onUpdateReceived(update);

        // Then
        assertThat(bot.sentMessages)
                .containsExactly(new SentMessage("123", "Всё ещё держимся."));
        verify(assistantConversation).reply("odyld", "and what about now?");
        verify(messageStore).save(eq("Lebowski"), eq("Всё ещё держимся."), any(Instant.class));
    }

    @Test
    @DisplayName("Does not trigger assistant when reply target is another user's message")
    void onUpdateReceived_replyToOtherUser_doesNotTrigger() {
        // Given
        TestableTelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "agreed", 1_777_398_404);
        Message otherMessage = new Message();
        User otherUser = new User();
        otherUser.setUserName("someone_else");
        otherMessage.setFrom(otherUser);
        otherMessage.setText("random thought");
        update.getMessage().setReplyToMessage(otherMessage);

        // When
        bot.onUpdateReceived(update);

        // Then
        verify(assistantConversation, never()).reply(any(), any());
        assertThat(bot.sentMessages).isEmpty();
    }

    @Test
    @DisplayName("Sends a fallback message when the assistant returns null")
    void onUpdateReceived_aiFailure_sendsFallbackMessage() {
        // Given
        TestableTelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "Lebowski?", 1_777_398_402);
        when(assistantConversation.reply(any(), any())).thenReturn(null);

        // When
        bot.onUpdateReceived(update);

        // Then
        assertThat(bot.sentMessages)
                .containsExactly(new SentMessage("123", "⚠️ Lebowski is unavailable right now."));
    }

    @Test
    @DisplayName("Replies to a non-trigger message after the idle threshold has elapsed")
    void onUpdateReceived_idleThresholdElapsed_triggersAssistantOnPlainMessage() {
        // Given
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        MutableClock clock = new MutableClock(t0);
        TestableTelegramBot bot = newBot(clock);
        // Idle threshold is 2h; advance the clock just past that.
        clock.set(t0.plus(Duration.ofHours(2)).plusSeconds(1));
        Update update = textUpdate(123L, "odyld", "just chatting", 1_777_398_500);
        when(assistantConversation.reply("odyld", "just chatting"))
                .thenReturn("Любопытно.");

        // When
        bot.onUpdateReceived(update);

        // Then
        assertThat(bot.sentMessages)
                .containsExactly(new SentMessage("123", "Любопытно."));
        verify(assistantConversation).reply("odyld", "just chatting");
    }

    @Test
    @DisplayName("Does not reply to a non-trigger message before the idle threshold has elapsed")
    void onUpdateReceived_idleThresholdNotElapsed_doesNotTrigger() {
        // Given
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        MutableClock clock = new MutableClock(t0);
        TestableTelegramBot bot = newBot(clock);
        clock.set(t0.plus(Duration.ofMinutes(30)));
        Update update = textUpdate(123L, "odyld", "just chatting", 1_777_398_501);

        // When
        bot.onUpdateReceived(update);

        // Then
        verify(assistantConversation, never()).reply(any(), any());
        assertThat(bot.sentMessages).isEmpty();
    }

    @Test
    @DisplayName("Idle-triggered reply resets the idle timer for subsequent messages")
    void onUpdateReceived_idleTriggeredReply_resetsIdleTimer() {
        // Given
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        MutableClock clock = new MutableClock(t0);
        TestableTelegramBot bot = newBot(clock);
        Instant idleNow = t0.plus(Duration.ofHours(2)).plusSeconds(1);
        clock.set(idleNow);
        when(assistantConversation.reply(eq("odyld"), any())).thenReturn("first idle reply");

        // When: first message triggers idle reply
        bot.onUpdateReceived(textUpdate(123L, "odyld", "first", 1_777_398_600));

        // And: a second message arrives only 5 minutes later, still without a mention
        clock.set(idleNow.plus(Duration.ofMinutes(5)));
        bot.onUpdateReceived(textUpdate(123L, "odyld", "second", 1_777_398_900));

        // Then: only the first message produced a reply
        assertThat(bot.sentMessages).containsExactly(new SentMessage("123", "first idle reply"));
        verify(assistantConversation, times(1)).reply(eq("odyld"), any());
    }

// TODO rewrite test or class to support testing
    
//    @Test
//    @DisplayName("Splits and sends text messages longer than 4096 characters")
//    void sendText_longMessage_splitsAndSends() {
//        // Given
//        TestableTelegramBot bot = newBot();
//        String longText = "a".repeat(5000); // 5000 characters
//        String chatId = "123";
//
//        // When
//        bot.sendText(chatId, longText);
//
//        // Then
//        assertThat(bot.sentMessages).containsExactly(
//            new SentMessage(chatId, longText.substring(0, 4096)),
//            new SentMessage(chatId, longText.substring(4096))
//        );
//    }

    private TestableTelegramBot newBot() {
        return newBot(Clock.systemUTC());
    }

    private TestableTelegramBot newBot(Clock clock) {
        BotProperties botProps = botProperties();
        AiTriggerProperties aiProps = aiTriggerProperties();
        TriggerMatcher matcher = new TriggerMatcher(botProps, aiProps, clock);
        return new TestableTelegramBot(
                botProps, aiProps,
                messageStore, imageDescriber, assistantConversation, typingIndicator, matcher);
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
                ZoneId.of("Europe/Kyiv"),
                Duration.ofHours(2));
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
        private final List<String> typingActions = new ArrayList<>();

        TestableTelegramBot(BotProperties botProperties,
                            AiTriggerProperties aiTriggerProperties,
                            MessageStore messageStore,
                            ImageDescriber imageDescriber,
                            AssistantConversation assistantConversation,
                            TypingIndicator typingIndicator,
                            TriggerMatcher triggerMatcher) {
            super(botProperties, aiTriggerProperties, messageStore, imageDescriber,
                    assistantConversation, typingIndicator, triggerMatcher);
        }

        @Override
        public boolean sendText(String chatId, String text) {
            sentMessages.add(new SentMessage(chatId, text));
            return true;
        }

        @Override
        void sendTypingAction(String chatId) {
            typingActions.add(chatId);
        }
    }

    /** Test-only {@link Clock} whose current instant can be moved forward at will. */
    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
