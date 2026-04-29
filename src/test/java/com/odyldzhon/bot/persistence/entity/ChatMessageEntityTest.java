package com.odyldzhon.bot.persistence.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageEntityTest {

    @Test
    @DisplayName("Builds an entity with all persisted fields")
    void builder_allFields_returnsEntityWithConfiguredValues() {
        // Given
        Instant createdAt = Instant.parse("2026-04-28T09:00:00Z");
        float[] embedding = {0.1f, 0.2f};

        // When
        ChatMessageEntity entity = ChatMessageEntity.builder()
                .id(1L)
                .createdAt(createdAt)
                .author("odyld")
                .message("hello")
                .embedding(embedding)
                .build();

        // Then
        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getAuthor()).isEqualTo("odyld");
        assertThat(entity.getMessage()).isEqualTo("hello");
        assertThat(entity.getEmbedding()).isSameAs(embedding);
        assertThat(ChatMessageEntity.EMBEDDING_DIM).isEqualTo(1536);
    }

    @Test
    @DisplayName("Supports no-args construction and setters for JPA")
    void noArgsConstructor_setters_returnsMutableEntity() {
        // Given
        ChatMessageEntity entity = new ChatMessageEntity();

        // When
        entity.setAuthor("bot");
        entity.setMessage("reply");

        // Then
        assertThat(entity.getAuthor()).isEqualTo("bot");
        assertThat(entity.getMessage()).isEqualTo("reply");
    }
}


