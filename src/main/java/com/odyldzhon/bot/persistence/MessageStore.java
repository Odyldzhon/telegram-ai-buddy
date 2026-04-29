package com.odyldzhon.bot.persistence;

import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import com.odyldzhon.bot.persistence.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Embeds incoming chat messages and stores them in Postgres,
 * and exposes semantic search over the stored history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStore {

    private final ChatMessageRepository repository;
    private final EmbeddingModel embeddingModel;

    /** Embed and persist a Telegram message. */
    public ChatMessageEntity save(String author, String message, Instant time) {
        float[] embedding = embeddingModel.embed(message);
        ChatMessageEntity saved = repository.save(ChatMessageEntity.builder()
                .createdAt(time)
                .author(author)
                .message(message)
                .embedding(embedding)
                .build());
        log.debug("Stored message id={} from {} ({} chars)", saved.getId(), author, message.length());
        return saved;
    }

    /** Find the {@code limit} most semantically similar past messages. */
    public List<ChatMessageEntity> search(String query, int limit) {
        float[] qv = embeddingModel.embed(query);
        return repository.findSimilar(toPgVectorLiteral(qv), limit);
    }

    public Optional<ChatMessageEntity> latestMessage(Instant since) {
        return repository.findTopByCreatedAtAfterOrderByCreatedAtDesc(since);
    }

    /** Convert {@code float[]} to the pgvector text literal {@code "[1.0,2.0,...]"}. */
    private static String toPgVectorLiteral(float[] v) {
        return IntStream.range(0, v.length)
                .mapToObj(i -> Float.toString(v[i]))
                .collect(Collectors.joining(",", "[", "]"));
    }
}

