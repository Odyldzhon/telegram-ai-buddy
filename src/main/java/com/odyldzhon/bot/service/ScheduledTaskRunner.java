package com.odyldzhon.bot.service;

import com.odyldzhon.bot.ai.AssistantConversation;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.persistence.entity.ScheduledTaskEntity;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.properties.BotProperties;
import com.odyldzhon.bot.telegram.TelegramBot;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Every minute looks up enabled tasks and executes those whose cron schedule fired
 * within the last minute and that have not run for that occurrence yet.
 * Task execution happens on a single worker thread, so slow tasks (e.g. the news digest)
 * never block the minute tick and tasks never call the model concurrently with each other.
 */
@Slf4j
@Service
public class ScheduledTaskRunner {

    static final Duration WINDOW = Duration.ofMinutes(1);

    private final ScheduledTaskService taskService;
    private final AssistantConversation assistantConversation;
    private final TelegramBot telegramBot;
    private final MessageStore messageStore;
    private final BotProperties botProperties;
    private final AiTriggerProperties properties;
    private final Clock clock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "scheduled-task-");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private Executor executor = worker;

    public ScheduledTaskRunner(
            ScheduledTaskService taskService,
            AssistantConversation assistantConversation,
            TelegramBot telegramBot,
            MessageStore messageStore,
            BotProperties botProperties,
            AiTriggerProperties properties,
            Clock clock) {
        this.taskService = taskService;
        this.assistantConversation = assistantConversation;
        this.telegramBot = telegramBot;
        this.messageStore = messageStore;
        this.botProperties = botProperties;
        this.properties = properties;
        this.clock = clock;
    }

    void setExecutorForTesting(Executor executor) {
        this.executor = executor;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        try {
            dispatchDueTasks(Instant.now(clock));
        } catch (Exception e) {
            log.error("Scheduled task lookup failed: {}", e.getMessage(), e);
        }
    }

    void dispatchDueTasks(Instant now) {
        for (ScheduledTaskEntity task : taskService.findEnabled()) {
            if (!isDue(task, now)) {
                continue;
            }
            if (!inFlight.add(task.getId())) {
                log.info("Scheduled task '{}' is still running, skipping", task.getName());
                continue;
            }
            executor.execute(() -> executeSafely(task));
        }
    }

    boolean isDue(ScheduledTaskEntity task, Instant now) {
        CronExpression cron;
        try {
            cron = CronExpression.parse(task.getSchedule());
        } catch (IllegalArgumentException e) {
            log.warn("Scheduled task '{}' has invalid schedule '{}'", task.getName(), task.getSchedule());
            return false;
        }
        ZonedDateTime fireTime = cron.next(now.minus(WINDOW).atZone(properties.timeZone()));
        if (fireTime == null || fireTime.toInstant().isAfter(now)) {
            return false;
        }
        Instant fired = fireTime.toInstant();
        if (!fired.isAfter(task.getCreatedAt())) {
            return false;
        }
        return task.getLastExecutionAt() == null || task.getLastExecutionAt().isBefore(fired);
    }

    private void executeSafely(ScheduledTaskEntity task) {
        try {
            execute(task);
        } catch (Exception e) {
            log.error("Scheduled task '{}' failed: {}", task.getName(), e.getMessage(), e);
        } finally {
            inFlight.remove(task.getId());
        }
    }

    private void execute(ScheduledTaskEntity task) {
        log.info("Executing scheduled task '{}'", task.getName());
        Instant startedAt = Instant.now(clock);
        String reply = assistantConversation.scheduledTask(
                task.getName(), task.getPrompt(), startedAt.atZone(properties.timeZone()));
        if (reply == null) {
            log.warn("Scheduled task '{}' produced no reply", task.getName());
            return;
        }
        if (!telegramBot.sendText(properties.chatId(), reply)) {
            return;
        }
        taskService.markExecuted(task.getId(), startedAt);
        try {
            messageStore.save(botProperties.name(), reply, Instant.now(clock));
        } catch (Exception e) {
            log.error("Failed to store scheduled task reply: {}", e.getMessage(), e);
        }
    }
}
