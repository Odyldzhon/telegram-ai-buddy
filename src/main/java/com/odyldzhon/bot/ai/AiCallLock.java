package com.odyldzhon.bot.ai;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes every request to the AI model; waiting callers are served in arrival order. */
@Component
public class AiCallLock {

    private final ReentrantLock lock = new ReentrantLock(true);

    public <T> T run(Supplier<T> call) {
        lock.lock();
        try {
            return call.get();
        } finally {
            lock.unlock();
        }
    }
}
