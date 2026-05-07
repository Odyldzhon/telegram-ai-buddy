package com.odyldzhon.bot.telegram;

import com.odyldzhon.bot.ai.AssistantConversation;
import com.odyldzhon.bot.ai.ImageDescriber;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import com.odyldzhon.bot.telegram.util.MessageAuthors;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginChannel;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginUser;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramBotTest {

    /** Single chat the bot is allowed to listen to / answer in. */
    private static final String ALLOWED_CHAT_ID = "123";
    private static final Duration DEFAULT_IDLE_THRESHOLD = Duration.ofHours(2);

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
        Mockito.lenient()
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
    void onUpdateReceived_updateWithoutMessage_doesNothing() throws TelegramApiException {
        // Given
        TelegramBot bot = newBot();
        Update update = new Update();

        // When
        bot.onUpdateReceived(update);

        // Then
        verify(messageStore, never()).save(any(), any(), any());
        verify(assistantConversation, never()).reply(any(), any());
        verify(bot, never()).execute(any(SendMessage.class));
    }

    @Test
    @DisplayName("Ignores messages coming from chats other than the configured one")
    void onUpdateReceived_messageFromUnrelatedChat_isIgnored() throws TelegramApiException {
        // Given
        TelegramBot bot = newBot();
        Update update = textUpdate(999L, "intruder", "Hey, Lebowski, hi", 1_777_398_410);

        // When
        bot.onUpdateReceived(update);

        // Then
        verify(messageStore, never()).save(any(), any(), any());
        verify(assistantConversation, never()).reply(any(), any());
        verify(bot, never()).execute(any(SendMessage.class));
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
    @DisplayName("Stores forwarded/shared text messages as outside source")
    void onUpdateReceived_forwardedText_storesOutsideSourceAuthor() {
        // Given
        TelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "Forwarded external text", 1_777_398_405);
        update.getMessage().setForwardOrigin(new MessageOriginChannel());

        // When
        bot.onUpdateReceived(update);

        // Then
        verify(messageStore).save(MessageAuthors.OUTSIDE_SOURCE, "Forwarded external text",
                Instant.ofEpochSecond(1_777_398_405));
        verify(assistantConversation, never()).reply(any(), any());
    }

    @Test
    @DisplayName("Stores self-forwarded text messages under the posting member")
    void onUpdateReceived_selfForwardedText_storesMemberAuthor() {
        // Given
        TelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "My own forwarded text", 1_777_398_406);
        MessageOriginUser origin = new MessageOriginUser();
        origin.setSenderUser(user(1L, "odyld", "First"));
        update.getMessage().setForwardOrigin(origin);

        // When
        bot.onUpdateReceived(update);

        // Then
        verify(messageStore).save("odyld", "My own forwarded text",
                Instant.ofEpochSecond(1_777_398_406));
        verify(assistantConversation, never()).reply(any(), any());
    }

    @Test
    @DisplayName("Calls the assistant, sends its reply, stores it, and shows typing")
    @SuppressWarnings("unchecked")
    void onUpdateReceived_textWithTrigger_sendsAndStoresAiReply() throws TelegramApiException {
        // Given
        TelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "Hey, Lebowski, what do you think?", 1_777_398_401);
        when(assistantConversation.reply("odyld", "Hey, Lebowski, what do you think?"))
                .thenReturn("Нормально, держимся.");

        // When
        bot.onUpdateReceived(update);

        // Then
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(sent.capture());
        assertThat(sent.getValue().getChatId()).isEqualTo("123");
        assertThat(sent.getValue().getText()).isEqualTo("Нормально, держимся.");

        verify(messageStore, times(2))
                .save(any(String.class), any(String.class), any(Instant.class));
        verify(messageStore).save("odyld", "Hey, Lebowski, what do you think?",
                Instant.ofEpochSecond(1_777_398_401));
        verify(messageStore).save(eq("Lebowski"), eq("Нормально, держимся."), any(Instant.class));
        verify(typingIndicator).runWith(any(Runnable.class), any(Supplier.class));

        ArgumentCaptor<SendChatAction> typing = ArgumentCaptor.forClass(SendChatAction.class);
        verify(bot).execute(typing.capture());
        assertThat(typing.getValue().getChatId()).isEqualTo("123");
    }

    @Test
    @DisplayName("Replies when the user replies to one of the bot's own messages without naming it")
    void onUpdateReceived_replyToBotMessage_triggersAssistant() throws TelegramApiException {
        // Given
        TelegramBot bot = newBot();
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
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(sent.capture());
        assertThat(sent.getValue().getChatId()).isEqualTo("123");
        assertThat(sent.getValue().getText()).isEqualTo("Всё ещё держимся.");

        verify(assistantConversation).reply("odyld", "and what about now?");
        verify(messageStore).save(eq("Lebowski"), eq("Всё ещё держимся."), any(Instant.class));
    }

    @Test
    @DisplayName("Does not trigger assistant when reply target is another user's message")
    void onUpdateReceived_replyToOtherUser_doesNotTrigger() throws TelegramApiException {
        // Given
        TelegramBot bot = newBot();
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
        verify(bot, never()).execute(any(SendMessage.class));
    }

    @Test
    @DisplayName("Sends a fallback message when the assistant returns null")
    void onUpdateReceived_aiFailure_sendsFallbackMessage() throws TelegramApiException {
        // Given
        TelegramBot bot = newBot();
        Update update = textUpdate(123L, "odyld", "Lebowski?", 1_777_398_402);
        when(assistantConversation.reply(any(), any())).thenReturn(null);

        // When
        bot.onUpdateReceived(update);

        // Then
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(sent.capture());
        assertThat(sent.getValue().getChatId()).isEqualTo("123");
        assertThat(sent.getValue().getText()).isEqualTo("⚠️ Lebowski is unavailable right now.");
    }

    @Test
    @DisplayName("Replies to a non-trigger message after the idle threshold has elapsed")
    void onUpdateReceived_idleThresholdElapsed_triggersAssistantOnPlainMessage()
            throws TelegramApiException {
        // Given: idle threshold of 1 nanosecond - effectively any elapsed time triggers idle.
        TelegramBot bot = newBot(Clock.systemUTC(), aiTriggerProperties(Duration.ofNanos(1)));
        Update update = textUpdate(123L, "odyld", "just chatting", 1_777_398_500);
        when(assistantConversation.reply("odyld", "just chatting"))
                .thenReturn("Любопытно.");

        // When
        bot.onUpdateReceived(update);

        // Then
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(sent.capture());
        assertThat(sent.getValue().getChatId()).isEqualTo("123");
        assertThat(sent.getValue().getText()).isEqualTo("Любопытно.");
        verify(assistantConversation).reply("odyld", "just chatting");
    }

    @Test
    @DisplayName("Does not reply to a non-trigger message before the idle threshold has elapsed")
    void onUpdateReceived_idleThresholdNotElapsed_doesNotTrigger() throws TelegramApiException {
        // Given: extremely large idle threshold so isIdle never fires.
        TelegramBot bot = newBot(Clock.systemUTC(), aiTriggerProperties(Duration.ofDays(3650)));
        Update update = textUpdate(123L, "odyld", "just chatting", 1_777_398_501);

        // When
        bot.onUpdateReceived(update);

        // Then
        verify(assistantConversation, never()).reply(any(), any());
        verify(bot, never()).execute(any(SendMessage.class));
    }

    @Test
    @DisplayName("Idle-triggered reply resets the idle timer for subsequent messages")
    void onUpdateReceived_idleTriggeredReply_resetsIdleTimer() throws TelegramApiException {
        // Given: a mocked Clock advanced through four discrete instants.
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        Instant idleNow = t0.plus(Duration.ofHours(2)).plusSeconds(1);
        Instant later = idleNow.plus(Duration.ofMinutes(5));

        Clock clock = mock(Clock.class);
        Mockito.lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        // Sequence of clock.instant() invocations:
        //   1) TriggerMatcher constructor -> lastBotReplyAt = t0
        //   2) first message isIdle()    -> idleNow (triggers idle)
        //   3) markBotReplied()           -> idleNow
        //   4) second message isIdle()   -> later (5 min after reply, not idle anymore)
        Mockito.lenient().when(clock.instant()).thenReturn(t0, idleNow, idleNow, later);

        TelegramBot bot = newBot(clock, aiTriggerProperties(DEFAULT_IDLE_THRESHOLD));
        when(assistantConversation.reply(eq("odyld"), any())).thenReturn("first idle reply");

        // When: first message triggers an idle reply
        bot.onUpdateReceived(textUpdate(123L, "odyld", "first", 1_777_398_600));
        // And: a second message arrives only 5 minutes later, still without a mention
        bot.onUpdateReceived(textUpdate(123L, "odyld", "second", 1_777_398_900));

        // Then: only the first message produced a reply
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot, times(1)).execute(sent.capture());
        assertThat(sent.getValue().getChatId()).isEqualTo("123");
        assertThat(sent.getValue().getText()).isEqualTo("first idle reply");
        verify(assistantConversation, times(1)).reply(eq("odyld"), any());
    }

    @Test
    @DisplayName("Splits and sends text messages longer than 4096 characters")
    void sendText_longMessage_splitsAndSends() throws TelegramApiException {
        // Given
        TelegramBot bot = newBot();
        String longText = "a".repeat(5000);
        String chatId = "123";

        // When
        bot.sendText(chatId, longText);

        // Then: two SendMessage calls in order — head first, then tail.
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot, times(2)).execute(sent.capture());
        assertThat(sent.getAllValues())
                .extracting(SendMessage::getChatId, SendMessage::getText)
                .containsExactly(
                        Tuple.tuple(chatId, longText.substring(0, 4096)),
                        Tuple.tuple(chatId, longText.substring(4096)));
    }

    private TelegramBot newBot() {
        return newBot(Clock.systemUTC(), aiTriggerProperties(DEFAULT_IDLE_THRESHOLD));
    }

    private TelegramBot newBot(Clock clock, AiTriggerProperties aiProps) {
        BotProperties botProps = botProperties();
        TriggerMatcher matcher = new TriggerMatcher(botProps, aiProps, clock);
        TelegramBot bot = Mockito.spy(new TelegramBot(
                botProps, aiProps,
                messageStore, imageDescriber, assistantConversation, typingIndicator, matcher));
        // Stub Telegram I/O so the spy never makes a network call.
        // Marked lenient because not every test exercises both methods.
        try {
            Mockito.lenient().doReturn(null).when(bot).execute(any(SendMessage.class));
            Mockito.lenient().doReturn(null).when(bot).execute(any(SendChatAction.class));
        } catch (TelegramApiException e) {
            throw new IllegalStateException(e);
        }
        return bot;
    }

    private static BotProperties botProperties() {
        return new BotProperties("test_bot", "123456:test-token", "Lebowski", "English");
    }

    private static AiTriggerProperties aiTriggerProperties(Duration idleThreshold) {
        return new AiTriggerProperties(
                false,
                false,
                false,
                false,
                ALLOWED_CHAT_ID,
                Duration.ofMinutes(30),
                Duration.ofMinutes(1),
                Duration.ofMinutes(1),
                30,
                ZoneId.of("Europe/Kyiv"),
                idleThreshold);
    }

    private static Update textUpdate(long chatId, String username, String text, int epochSecond) {
        User user = user(1L, username, "First");

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

    private static User user(Long id, String username, String firstName) {
        User user = new User();
        user.setId(id);
        user.setUserName(username);
        user.setFirstName(firstName);
        return user;
    }
}
