package com.odyldzhon.bot.ai;

import com.odyldzhon.bot.configuration.ChatClientConfig;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class AssistantConversation {

    static final int RECENT_CONTEXT_MESSAGES = 20;

    private final MessageStore messageStore;
    private final ChatClient assistantChatClient;

    public AssistantConversation(
            MessageStore messageStore,
            @Qualifier(ChatClientConfig.ASSISTANT) ChatClient assistantChatClient) {
        this.messageStore = messageStore;
        this.assistantChatClient = assistantChatClient;
    }

    public String reply(String author, String userMessage) {
        try {
            log.info("Trigger detected from {} – calling AI", author);
            String recentContext = renderRecentContext();
            return assistantChatClient.prompt()
                    .user("""
                            A Telegram user mentioned you directly.

                            Recent chat history (newest first, up to %d messages – already provided so
                            you do NOT need a tool call for this. Fetch more via your database tools
                            only if you need older context or a specific lookup):
                            %s

                            Author: @%s
                            Message:
                            %s
                            """.formatted(RECENT_CONTEXT_MESSAGES, recentContext, author, userMessage))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI request failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private String renderRecentContext() {
        List<ChatMessageEntity> rows;
        try {
            rows = messageStore.recent(RECENT_CONTEXT_MESSAGES);
        } catch (Exception e) {
            log.error("Failed to load recent messages for context: {}", e.getMessage(), e);
            rows = Collections.emptyList();
        }
        if (rows.isEmpty()) {
            return "(no prior messages)";
        }
        StringBuilder sb = new StringBuilder(rows.size() * 80);
        for (ChatMessageEntity m : rows) {
            sb.append('[').append(m.getCreatedAt()).append("] ")
              .append(m.getAuthor()).append(": ")
              .append(m.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
