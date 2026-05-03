package com.odyldzhon.bot.ai;

import com.odyldzhon.bot.configuration.ChatClientConfig;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import com.odyldzhon.bot.properties.AssistantProperties;
import com.odyldzhon.bot.telegram.util.MessageAuthors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class AssistantConversation {

    static final int RECENT_CONTEXT_MESSAGES = 20;
    static final int MAX_ATTEMPTS = 5;

    private final MessageStore messageStore;
    private final ChatClient assistantChatClient;
    private final Duration retryBackoff;

    public AssistantConversation(
            MessageStore messageStore,
            @Qualifier(ChatClientConfig.ASSISTANT) ChatClient assistantChatClient,
            AssistantProperties properties) {
        this.messageStore = messageStore;
        this.assistantChatClient = assistantChatClient;
        this.retryBackoff = properties.retryBackoff();
    }

    public String reply(String author, String userMessage) {
        log.info("Trigger detected from {} – calling AI", author);
        String recentContext = renderRecentContext();
        String authorForPrompt = renderAuthorForPrompt(author);
        return askAi("""
                A Telegram user mentioned you directly.
                
                Recent chat history (newest first, up to %d messages – already provided so
                you do NOT need a tool call for this. Fetch more via your database tools
                only if you need older context or a specific lookup):
                %s
                
                Author: %s
                Message:
                %s
                """.formatted(RECENT_CONTEXT_MESSAGES, recentContext, authorForPrompt, userMessage));
    }

    public String dailyJoke() {
        return askAi("This is scheduled trigger to post a joke to telegram group. Please provide one");
    }

    public String newsDigest(LocalDate today) {
        return askAi("""
                This is scheduled trigger to post a news to telegram group.
                Review important news from the previous 24 hours (as of %s) and post a digest to the Telegram chat.
                Cover financial, technological, and politically important news from reputable sources.

                Rules:
                - Keep it conversational; length can vary depending on how many important items there are.
                - For each item, mention why it matters, not just the headline.
                - Links are optional; include them only when they add clear value.
                """.formatted(today));
    }

    public String proactiveHistoryReply(Instant since, int maxMessages, String botName) {
        return askAi("""
                You are about to proactively join the Telegram chat without being directly mentioned.
                Before answering, use your database tools to inspect recent chat history yourself.

                Required lookup:
                - Call executeSelectQuery with a SELECT similar to:
                  SELECT created_at, author, message
                  FROM chat_message
                  WHERE created_at >= '%s'
                  ORDER BY created_at DESC
                  LIMIT %d
                - Do not select the embedding column.
                - The query result includes your own earlier messages under author "%s"; use them to avoid repeating yourself.
                - After reading the newest rows, reason about the conversation chronologically before replying.

                Rules:
                - Be concise: 1-3 short paragraphs or bullets.
                - Do not say you are scheduled, automated, or that no one mentioned you.
                - Do not repeat the whole history; add insight, a helpful answer, a witty observation, or a question.
                - If the recent messages are unclear, make a light contextual comment rather than forcing an answer.
                """.formatted(since, maxMessages, botName));
    }

    private String askAi(String prompt) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String reply = assistantChatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();
                if (reply != null && !reply.isBlank()) {
                    return reply.trim();
                }
                log.warn("Assistant returned an empty reply (attempt {}/{})", attempt, MAX_ATTEMPTS);
            } catch (Exception e) {
                log.error("Assistant request failed (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage(), e);
            }
            if (attempt < MAX_ATTEMPTS && !sleepBeforeRetry()) {
                return null;
            }
        }
        return null;
    }

    private boolean sleepBeforeRetry() {
        if (retryBackoff.isZero() || retryBackoff.isNegative()) {
            return true;
        }
        try {
            Thread.sleep(retryBackoff.toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }


    private String renderAuthorForPrompt(String author) {
        if (MessageAuthors.OUTSIDE_SOURCE.equals(author)) {
            return "%s (forwarded/shared external content; not a chat member)".formatted(author);
        }
        return author == null ? "unknown" : author;
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
