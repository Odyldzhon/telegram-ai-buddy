package com.odyldzhon.bot.telegram.util;

import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Locale;

@UtilityClass
public class TriggerMatcher {
    public boolean matches(Message message, String triggerText, String botName, String botUsername) {
        return mentionsBot(triggerText, botName) || isReplyToBot(message, botUsername);
    }

    private boolean mentionsBot(String text, String botName) {
        if (text == null || botName == null || botName.isBlank()) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT)
                .contains(botName.toLowerCase(Locale.ROOT));
    }

    private boolean isReplyToBot(Message message, String botUsername) {
        if (message == null || botUsername == null || botUsername.isBlank()) {
            return false;
        }
        Message replyTo = message.getReplyToMessage();
        if (replyTo == null || replyTo.getFrom() == null) {
            return false;
        }
        return botUsername.equalsIgnoreCase(replyTo.getFrom().getUserName());
    }
}

