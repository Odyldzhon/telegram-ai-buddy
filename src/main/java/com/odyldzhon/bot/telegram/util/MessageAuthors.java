package com.odyldzhon.bot.telegram.util;

import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginUser;

import java.util.Objects;

@UtilityClass
public class MessageAuthors {

    public static final String OUTSIDE_SOURCE = "outside source";

    public String resolve(Message message) {
        if (message == null) {
            return null;
        }
        if (isOutsideSource(message)) {
            return OUTSIDE_SOURCE;
        }
        if (message.getFrom() == null) {
            return null;
        }
        String username = message.getFrom().getUserName();
        return username != null ? username : message.getFrom().getFirstName();
    }

    private boolean isOutsideSource(Message message) {
        if (message.getExternalReplyInfo() != null
                || message.getStory() != null
                || Boolean.TRUE.equals(message.getIsAutomaticForward())) {
            return true;
        }
        return hasForwardMetadata(message) && !isVerifiedSelfForward(message);
    }

    private boolean hasForwardMetadata(Message message) {
        return message.getForwardOrigin() != null
                || message.getForwardFrom() != null
                || message.getForwardFromChat() != null
                || message.getForwardDate() != null
                || message.getForwardFromMessageId() != null
                || message.getForwardSignature() != null
                || message.getForwardSenderName() != null;
    }

    private boolean isVerifiedSelfForward(Message message) {
        User sender = message.getFrom();
        if (sender == null) {
            return false;
        }
        if (message.getForwardOrigin() instanceof MessageOriginUser origin) {
            return sameUser(sender, origin.getSenderUser());
        }
        return sameUser(sender, message.getForwardFrom());
    }

    private boolean sameUser(User left, User right) {
        Long leftId = left != null ? left.getId() : null;
        Long rightId = right != null ? right.getId() : null;
        return left != null
                && right != null
                && Objects.equals(leftId, rightId);
    }
}
