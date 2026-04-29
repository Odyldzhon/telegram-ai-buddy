package com.odyldzhon.bot.telegram.util;

import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.meta.api.objects.Message;

@UtilityClass
public class MessageAuthors {

    public String resolve(Message message) {
        if (message == null || message.getFrom() == null) {
            return null;
        }
        String username = message.getFrom().getUserName();
        return username != null ? username : message.getFrom().getFirstName();
    }
}
