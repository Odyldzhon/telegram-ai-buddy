package com.odyldzhon.bot.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    @Test
    @DisplayName("Creates a single-threaded proactive AI task scheduler")
    void taskScheduler_validConfiguration_returnsThreadPoolTaskScheduler() {
        // Given
        SchedulingConfig config = new SchedulingConfig();

        // When
        TaskScheduler result = config.taskScheduler();

        // Then
        assertThat(result).isInstanceOf(ThreadPoolTaskScheduler.class);
        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) result;
        assertThat(scheduler.getPoolSize()).isEqualTo(1);
        assertThat(scheduler.isRemoveOnCancelPolicy()).isTrue();
        scheduler.shutdown();
    }
}


