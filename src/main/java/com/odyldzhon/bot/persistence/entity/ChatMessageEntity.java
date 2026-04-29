package com.odyldzhon.bot.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Table(name = "chat_message")
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEntity {

    public static final int EMBEDDING_DIM = 1536;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "author", nullable = false, length = 128)
    private String author;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    /** pgvector column; Hibernate 6.4+ maps {@code float[]} to the SQL VECTOR type. */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIM)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;
}

