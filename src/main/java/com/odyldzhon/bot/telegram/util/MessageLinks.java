package com.odyldzhon.bot.telegram.util;

import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@UtilityClass
public class MessageLinks {

    private static final String TYPE_URL = "url";
    private static final String TYPE_TEXT_LINK = "text_link";

    public List<String> extract(Message message) {
        if (message == null) {
            return List.of();
        }
        Set<String> links = new LinkedHashSet<>();
        collect(message.getEntities(), message.getText(), links);
        collect(message.getCaptionEntities(), message.getCaption(), links);
        return new ArrayList<>(links);
    }

    public String appendLinks(String text, Message message) {
        List<String> links = extract(message);
        if (links.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text == null ? "" : text);
        for (String link : links) {
            if (sb.indexOf(link) >= 0) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(link);
        }
        return sb.toString();
    }

    private void collect(List<MessageEntity> entities, String source, Set<String> sink) {
        if (entities == null) {
            return;
        }
        for (MessageEntity entity : entities) {
            String type = entity.getType();
            if (TYPE_TEXT_LINK.equalsIgnoreCase(type)) {
                String url = entity.getUrl();
                if (url != null && !url.isBlank()) {
                    sink.add(url.trim());
                }
            } else if (TYPE_URL.equalsIgnoreCase(type) && source != null) {
                int offset = entity.getOffset();
                int length = entity.getLength();
                int end = Math.min(source.length(), offset + length);
                if (offset >= 0 && offset < end) {
                    String url = source.substring(offset, end).trim();
                    if (!url.isBlank()) {
                        sink.add(url);
                    }
                }
            }
        }
    }
}



