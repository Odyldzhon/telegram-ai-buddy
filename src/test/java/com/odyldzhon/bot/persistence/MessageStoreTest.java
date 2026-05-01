package com.odyldzhon.bot.persistence;

import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import com.odyldzhon.bot.persistence.repository.ChatMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageStoreTest {

    @Mock
    private ChatMessageRepository repository;
    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    @DisplayName("Embeds and saves an incoming chat message")
    void save_validMessage_persistsEntityWithEmbedding() {
        // Given
        MessageStore store = new MessageStore(repository, embeddingModel);
        Instant time = Instant.parse("2026-04-28T12:00:00Z");
        when(embeddingModel.embed("hello")).thenReturn(new float[]{0.1f, 0.2f});
        when(repository.save(any(ChatMessageEntity.class)))
                .thenAnswer(invocation -> {
                    ChatMessageEntity entity = invocation.getArgument(0);
                    entity.setId(42L);
                    return entity;
                });

        // When
        ChatMessageEntity result = store.save("odyld", "hello", time);

        // Then
        ArgumentCaptor<ChatMessageEntity> captor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue())
                .extracting(ChatMessageEntity::getAuthor, ChatMessageEntity::getMessage, ChatMessageEntity::getCreatedAt)
                .containsExactly("odyld", "hello", time);
        assertThat(captor.getValue().getEmbedding()).containsExactly(0.1f, 0.2f);
        assertThat(result.getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("Embeds a query and delegates vector literal search to the repository")
    void search_validQuery_usesPgVectorLiteral() {
        // Given
        MessageStore store = new MessageStore(repository, embeddingModel);
        List<ChatMessageEntity> rows = List.of(ChatMessageEntity.builder().id(1L).build());
        when(embeddingModel.embed("rug")).thenReturn(new float[]{0.1f, -2.0f, 3.25f});
        when(repository.findSimilar("[0.1,-2.0,3.25]", 7)).thenReturn(rows);

        // When
        List<ChatMessageEntity> result = store.search("rug", 7);

        // Then
        assertThat(result).isSameAs(rows);
        verify(repository).findSimilar("[0.1,-2.0,3.25]", 7);
    }

    @Test
    @DisplayName("recent() asks the repository for a page of N newest rows")
    void recent_validLimit_callsRepositoryWithPageRequest() {
        // Given
        MessageStore store = new MessageStore(repository, embeddingModel);
        List<ChatMessageEntity> rows = List.of(
                ChatMessageEntity.builder().id(2L).build(),
                ChatMessageEntity.builder().id(1L).build());
        when(repository.findRecent(any(Pageable.class))).thenReturn(rows);

        // When
        List<ChatMessageEntity> result = store.recent(20);

        // Then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findRecent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(PageRequest.of(0, 20));
        assertThat(result).isSameAs(rows);
    }

    @Test
    @DisplayName("recent() clamps non-positive limits to 1 to satisfy PageRequest")
    void recent_nonPositiveLimit_clampsToOne() {
        // Given
        MessageStore store = new MessageStore(repository, embeddingModel);
        when(repository.findRecent(any(Pageable.class))).thenReturn(List.of());

        // When
        store.recent(0);

        // Then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findRecent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(PageRequest.of(0, 1));
    }
}
