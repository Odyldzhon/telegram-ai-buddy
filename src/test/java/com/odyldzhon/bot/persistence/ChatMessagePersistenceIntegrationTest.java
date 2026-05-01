package com.odyldzhon.bot.persistence;

import com.odyldzhon.bot.configuration.BotRegistrar;
import com.odyldzhon.bot.configuration.ChatClientConfig;
import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import com.odyldzhon.bot.persistence.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema.sql",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class ChatMessagePersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("botdb")
            .withUsername("postgres")
            .withPassword("postgres");


    @Autowired
    private MessageStore messageStore;

    @Autowired
    private ChatMessageRepository repository;

    @MockitoBean
    private BotRegistrar botRegistrar;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean(name = ChatClientConfig.ASSISTANT)
    private ChatClient assistantChatClient;

    @MockitoBean(name = ChatClientConfig.IMAGE)
    private ChatClient imageChatClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Persists embedded messages into PostgreSQL with pgvector")
    void save_validMessage_persistsMessageInPostgres() {
        // Given
        Instant createdAt = Instant.parse("2026-04-28T08:00:00Z");
        when(embeddingModel.embed("rug message")).thenReturn(vector(1.0f, 0.0f));

        // When
        ChatMessageEntity saved = messageStore.save("odyld", "rug message", createdAt);

        // Then
        Optional<ChatMessageEntity> reloaded = repository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getAuthor()).isEqualTo("odyld");
        assertThat(reloaded.get().getMessage()).isEqualTo("rug message");
        assertThat(reloaded.get().getEmbedding()).hasSize(ChatMessageEntity.EMBEDDING_DIM);
    }

    @Test
    @DisplayName("Finds semantically closest messages using pgvector cosine distance")
    void search_vectorSimilarity_returnsClosestMessagesFirst() {
        // Given
        Instant base = Instant.parse("2026-04-28T09:00:00Z");
        when(embeddingModel.embed("rug"))
                .thenReturn(vector(1.0f, 0.0f))
                .thenReturn(vector(1.0f, 0.0f));
        when(embeddingModel.embed("coffee"))
                .thenReturn(vector(0.0f, 1.0f));
        messageStore.save("alice", "rug", base);
        messageStore.save("bob", "coffee", base.plusSeconds(60));

        // When
        List<ChatMessageEntity> result = messageStore.search("rug", 2);

        // Then
        assertThat(result)
                .extracting(ChatMessageEntity::getMessage)
                .containsExactly("rug", "coffee");
    }

    private static float[] vector(float first, float second) {
        float[] vector = new float[ChatMessageEntity.EMBEDDING_DIM];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }
}



