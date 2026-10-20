package com.odyldzhon.bot.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Table(name = "scheduled_task")
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTaskEntity {

    public enum Status {
        ENABLED,
        DISABLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 128, unique = true)
    private String name;

    @Column(name = "prompt", nullable = false, columnDefinition = "text")
    private String prompt;

    /** Spring 6-field cron expression: second minute hour day-of-month month day-of-week. */
    @Column(name = "schedule", nullable = false, length = 128)
    private String schedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "last_execution_at")
    private Instant lastExecutionAt;

    @Column(name = "remaining_count")
    private Integer remainingCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
