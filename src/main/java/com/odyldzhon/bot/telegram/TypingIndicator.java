package com.odyldzhon.bot.telegram;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
public class TypingIndicator {

    static final long REFRESH_SECONDS = 4;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "telegram-typing");
                t.setDaemon(true);
                return t;
            });

    public <T> T runWith(Runnable typingPing, Supplier<T> task) {
        safeRun(typingPing);
        ScheduledFuture<?> refresh = scheduler.scheduleAtFixedRate(
                () -> safeRun(typingPing),
                REFRESH_SECONDS,
                REFRESH_SECONDS,
                TimeUnit.SECONDS);
        try {
            return task.get();
        } finally {
            refresh.cancel(false);
        }
    }

    private static void safeRun(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.warn("Typing indicator ping failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
