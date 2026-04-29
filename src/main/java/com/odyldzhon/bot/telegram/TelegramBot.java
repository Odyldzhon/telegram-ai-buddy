package com.odyldzhon.bot.telegram;

import com.odyldzhon.bot.ai.AssistantConversation;
import com.odyldzhon.bot.ai.ImageDescriber;
import com.odyldzhon.bot.configuration.ChatClientConfig;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import com.odyldzhon.bot.telegram.util.MessageAuthors;
import com.odyldzhon.bot.telegram.util.TriggerMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
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

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    private final BotProperties botProperties;
    private final AiTriggerProperties aiTriggerProperties;
    private final MessageStore messageStore;
    private final ImageDescriber imageDescriber;
    private final AssistantConversation assistantConversation;
    private final TypingIndicator typingIndicator;

    public TelegramBot(
            BotProperties botProperties,
            AiTriggerProperties aiTriggerProperties,
            MessageStore messageStore,
            ImageDescriber imageDescriber,
            AssistantConversation assistantConversation,
            TypingIndicator typingIndicator) {
        super(botProperties.token());
        this.botProperties = botProperties;
        this.aiTriggerProperties = aiTriggerProperties;
        this.messageStore = messageStore;
        this.imageDescriber = imageDescriber;
        this.assistantConversation = assistantConversation;
        this.typingIndicator = typingIndicator;
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
        String  author  = MessageAuthors.resolve(message);
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

        if (TriggerMatcher.contains(triggerSource, botProperties.name())) {
            final String triggerText = triggerSource;
            String reply = typingIndicator.runWith(
                    () -> sendTypingAction(chatId),
                    () -> assistantConversation.reply(author, triggerText));
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

    private void sendTypingAction(String chatId) {
        SendChatAction action = new SendChatAction();
        action.setChatId(chatId);
        action.setAction(ActionType.TYPING);
        try {
            execute(action);
        } catch (TelegramApiException e) {
            log.warn("Failed to send typing action to {}: {}", chatId, e.getMessage());
        }
    }
}
