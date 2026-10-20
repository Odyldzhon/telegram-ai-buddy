package com.odyldzhon.bot.service;

import com.odyldzhon.bot.persistence.entity.ScheduledTaskEntity;
import com.odyldzhon.bot.persistence.entity.ScheduledTaskEntity.Status;
import com.odyldzhon.bot.persistence.repository.ScheduledTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * CRUD and validation for {@link ScheduledTaskEntity}. Violations are reported
 * with {@link IllegalArgumentException} so callers (e.g. LLM tools) can relay the message.
 */
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    static final int MAX_NAME_LENGTH = 128;
    static final int MAX_COUNT = 100;

    private final ScheduledTaskRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ScheduledTaskEntity> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<ScheduledTaskEntity> findEnabled() {
        return repository.findByStatus(Status.ENABLED);
    }

    @Transactional(readOnly = true)
    public ScheduledTaskEntity get(String name) {
        return find(name);
    }

    @Transactional
    public ScheduledTaskEntity create(String name, String prompt, String schedule, boolean enabled,
                                      Integer remainingCount) {
        String normalizedName = normalizeName(name);
        requireText(prompt, "prompt");
        validateSchedule(schedule);
        validateCount(remainingCount);
        if (repository.findByName(normalizedName).isPresent()) {
            throw new IllegalArgumentException("Task '%s' already exists".formatted(normalizedName));
        }
        return repository.save(ScheduledTaskEntity.builder()
                .name(normalizedName)
                .prompt(prompt.trim())
                .schedule(schedule.trim())
                .status(enabled ? Status.ENABLED : Status.DISABLED)
                .remainingCount(remainingCount)
                .createdAt(Instant.now(clock))
                .build());
    }

    @Transactional
    public ScheduledTaskEntity update(String name, String prompt, String schedule,
                                      Integer remainingCount, boolean unlimited) {
        ScheduledTaskEntity task = find(name);
        if (prompt == null && schedule == null && remainingCount == null && !unlimited) {
            throw new IllegalArgumentException(
                    "Nothing to update: provide a new prompt, schedule and/or count");
        }
        if (unlimited && remainingCount != null) {
            throw new IllegalArgumentException("Provide either a count or unlimited, not both");
        }
        if (prompt != null) {
            requireText(prompt, "prompt");
            task.setPrompt(prompt.trim());
        }
        if (schedule != null) {
            validateSchedule(schedule);
            task.setSchedule(schedule.trim());
        }
        if (remainingCount != null) {
            validateCount(remainingCount);
            task.setRemainingCount(remainingCount);
        }
        if (unlimited) {
            task.setRemainingCount(null);
        }
        return repository.save(task);
    }

    @Transactional
    public ScheduledTaskEntity setEnabled(String name, boolean enabled) {
        ScheduledTaskEntity task = find(name);
        if (enabled && task.getRemainingCount() != null && task.getRemainingCount() <= 0) {
            throw new IllegalArgumentException(
                    "Task '%s' has no runs left; set a new count or make it unlimited first".formatted(task.getName()));
        }
        task.setStatus(enabled ? Status.ENABLED : Status.DISABLED);
        return repository.save(task);
    }

    @Transactional
    public void markExecuted(Long id, Instant executedAt) {
        repository.findById(id).ifPresent(task -> {
            task.setLastExecutionAt(executedAt);
            Integer left = task.getRemainingCount();
            if (left != null) {
                int next = Math.max(left - 1, 0);
                task.setRemainingCount(next);
                if (next == 0) {
                    task.setStatus(Status.DISABLED);
                }
            }
            repository.save(task);
        });
    }

    private ScheduledTaskEntity find(String name) {
        String normalizedName = normalizeName(name);
        return repository.findByName(normalizedName)
                .orElseThrow(() -> new IllegalArgumentException("Task '%s' not found".formatted(normalizedName)));
    }

    private static String normalizeName(String name) {
        requireText(name, "name");
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be at most %d characters".formatted(MAX_NAME_LENGTH));
        }
        return trimmed;
    }

    private static void validateCount(Integer count) {
        if (count != null && (count < 1 || count > MAX_COUNT)) {
            throw new IllegalArgumentException(
                    "count must be between 1 and %d (omit it for unlimited runs)".formatted(MAX_COUNT));
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    static void validateSchedule(String schedule) {
        requireText(schedule, "schedule");
        try {
            CronExpression.parse(schedule.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid cron schedule '%s': %s. Use 6 fields: second minute hour day-of-month month day-of-week"
                            .formatted(schedule, e.getMessage()));
        }
    }
}
