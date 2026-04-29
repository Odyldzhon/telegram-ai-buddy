package com.odyldzhon.bot.telegram;

import com.odyldzhon.bot.ai.ImageDescriber;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import com.odyldzhon.bot.configuration.ChatClientConfig;
import com.odyldzhon.bot.persistence.MessageStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Long-polling Telegram bot:
 *   • Only listens to the single chat configured via {@code bot.ai-trigger.chat-id}.
 *     Updates from any other chat are silently ignored.
 *   • Stores every text or photo message in Postgres (with vector embedding).
 *   • Photos are described by the AI; the description is stored prefixed with "image: ".
 *   • Invokes the AI ONLY when the message contains the configured bot name.
 *
 * Wiring (credentials, ChatClient construction) lives in
 * {@link BotProperties} / {@link ChatClientConfig} so this class stays focused on behaviour.
 *
 * Note: the {@code assistantChatClient} field name MUST match the bean name
 * declared in {@link ChatClientConfig#ASSISTANT} – Spring uses parameter name
 * to disambiguate between the two {@link ChatClient} beans.
 */
@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    private final BotProperties botProperties;
    private final AiTriggerProperties aiTriggerProperties;
    private final MessageStore messageStore;
    private final ImageDescriber imageDescriber;
    /** Field/parameter name matches bean name {@link ChatClientConfig#ASSISTANT}. */
    private final ChatClient assistantChatClient;

    public TelegramBot(
            BotProperties botProperties,
            AiTriggerProperties aiTriggerProperties,
            MessageStore messageStore,
            ImageDescriber imageDescriber,
            @Qualifier(ChatClientConfig.ASSISTANT) ChatClient assistantChatClient) {
        super(botProperties.token());
        this.botProperties = botProperties;
        this.aiTriggerProperties = aiTriggerProperties;
        this.messageStore = messageStore;
        this.imageDescriber = imageDescriber;
        this.assistantChatClient = assistantChatClient;
    }

    @Override
    public String getBotUsername() { return botProperties.username(); }

    @Async
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        String  chatId  = message.getChatId().toString();
        if (!isAllowedChat(chatId)) {
            log.warn("Ignoring message from unrelated chat {}", chatId);
            return;
        }
        String  author  = resolveAuthor(message);
        Instant time    = Instant.ofEpochSecond(message.getDate());

        String storedText = null;
        String triggerSource = null;

        if (message.hasPhoto()) {
            String caption = message.getCaption();
            String description = describePhoto(message.getPhoto(), caption);
            if (description != null) {
                storedText = description
                        + (caption != null && !caption.isBlank() ? " | caption: " + caption : "");
                triggerSource = caption;
            }
        } else if (message.hasText()) {
            storedText    = message.getText();
            triggerSource = storedText;
        }

        if (storedText == null) return;

        try {
            messageStore.save(author, storedText, time);
        } catch (Exception e) {
            log.error("Failed to store message: {}", e.getMessage(), e);
        }

        if (containsTrigger(triggerSource)) {
            String reply = askAi(author, triggerSource);
            if (reply != null) {
                sendText(chatId, reply);
                try {
                    messageStore.save(botProperties.name(), reply, Instant.now());
                } catch (Exception e) {
                    log.error("Failed to store AI reply: {}", e.getMessage(), e);
                }
            } else {
                sendText(chatId, "⚠️ %s is unavailable right now.".formatted(botProperties.name()));
            }
        }
    }

    private boolean isAllowedChat(String chatId) {
        String allowed = aiTriggerProperties.chatId();
        return allowed != null && allowed.equals(chatId);
    }

    private String describePhoto(List<PhotoSize> photos, String caption) {
        try {
            PhotoSize biggest = photos.stream()
                    .max(Comparator.comparingInt(PhotoSize::getFileSize))
                    .orElse(null);
            if (biggest == null) return null;

            org.telegram.telegrambots.meta.api.objects.File tgFile =
                    execute(new GetFile(biggest.getFileId()));
            String url = tgFile.getFileUrl(botProperties.token());

            byte[] bytes;
            try (InputStream in = URI.create(url).toURL().openStream()) {
                bytes = in.readAllBytes();
            }
            log.info("Downloaded photo {} ({} bytes), asking AI for description", biggest.getFileId(), bytes.length);
            return imageDescriber.describe(bytes, "image/jpeg", caption);
        } catch (Exception e) {
            log.error("Failed to download/describe photo: {}", e.getMessage(), e);
            return null;
        }
    }

    private static String resolveAuthor(Message message) {
        return message.getFrom().getUserName() != null
                ? message.getFrom().getUserName()
                : message.getFrom().getFirstName();
    }

    private boolean containsTrigger(String text) {
        return text != null
                && botProperties.name() != null
                && !botProperties.name().isBlank()
                && text.toLowerCase(Locale.ROOT).contains(botProperties.name().toLowerCase(Locale.ROOT));
    }

    private String askAi(String author, String userMessage) {
        try {
            log.info("Trigger detected from {} – calling AI", author);
            return assistantChatClient.prompt()
                    .user("""
                            A Telegram user mentioned you directly.

                            Before answering, use your database tools to inspect recent chat history so you do not
                            answer out of context.

                            Required lookup:
                            - Call executeSelectQuery with a SELECT similar to:
                              SELECT created_at, author, message
                              FROM chat_message
                              ORDER BY created_at DESC
                              LIMIT 60
                            - Do not select the embedding column.
                            - Treat the newest rows as context, but answer the current message below.

                            Author: @%s
                            Message:
                            %s
                            """.formatted(author, userMessage))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI request failed: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean sendText(String chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        try {
            execute(msg);
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send message: {}", e.getMessage(), e);
            return false;
        }
    }
}

