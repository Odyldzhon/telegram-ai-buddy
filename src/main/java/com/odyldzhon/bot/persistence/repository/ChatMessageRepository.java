package com.odyldzhon.bot.persistence.repository;

import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    /**
     * Cosine-distance similarity search using the pgvector {@code <=>} operator.
     * Lower distance = more similar. Returns the top {@code limit} closest rows.
     *
     * The query embedding must be passed as a pgvector literal: {@code '[0.1,0.2,...]'}.
     * We bind it as text and cast it inside the SQL.
     */
    @Query(value = """
            SELECT *
            FROM chat_message
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatMessageEntity> findSimilar(@Param("queryVector") String queryVector,
                                        @Param("limit")       int    limit);

    Optional<ChatMessageEntity> findTopByCreatedAtAfterOrderByCreatedAtDesc(Instant createdAt);

    @Query("SELECT m FROM ChatMessageEntity m ORDER BY m.createdAt DESC")
    List<ChatMessageEntity> findRecent(org.springframework.data.domain.Pageable pageable);
}

