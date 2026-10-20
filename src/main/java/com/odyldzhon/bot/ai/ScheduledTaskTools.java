package com.odyldzhon.bot.ai;

import com.odyldzhon.bot.persistence.entity.ScheduledTaskEntity;
import com.odyldzhon.bot.properties.AiTriggerProperties;
import com.odyldzhon.bot.service.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Tools the LLM can call to manage the bot's scheduled tasks. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledTaskTools {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (zzz, xxx)");

    private final ScheduledTaskService service;
    private final AiTriggerProperties properties;
    private final Clock clock;

    @Tool(description = """
            Get the current date and time in the bot's time zone. Cron schedules are evaluated in
            this zone (NOT UTC), so call this before scheduling relative times like "in 2 hours"
            or "tomorrow at 9".
            """)
    public String getCurrentTime() {
        return TIME_FMT.format(ZonedDateTime.now(clock.withZone(properties.timeZone())));
    }

    @Tool(description = "List all scheduled tasks with their schedule, status, runs left and last execution time.")
    public String listScheduledTasks() {
        log.info("Tool listScheduledTasks");
        List<ScheduledTaskEntity> tasks = service.findAll();
        if (tasks.isEmpty()) {
            return "(no scheduled tasks)";
        }
        return tasks.stream().map(this::summary).collect(Collectors.joining("\n"));
    }

    @Tool(description = "Get one scheduled task by name, including its full prompt.")
    public String getScheduledTask(
            @ToolParam(description = "Unique task name") String name) {
        log.info("Tool getScheduledTask(name='{}')", name);
        return run(() -> {
            ScheduledTaskEntity task = service.get(name);
            return summary(task) + "\nprompt:\n" + task.getPrompt();
        });
    }

    @Tool(description = """
            Create a new scheduled task. When it is due, its prompt is sent back to you
            as "Scheduled task: ..." and your answer is posted to the chat.
            Write the prompt as a self-contained instruction.
            The cron schedule is evaluated in the bot's time zone, not UTC.
            Use 'count' to limit how many times it runs (1 = run once); omit it to run forever.
            """)
    public String createScheduledTask(
            @ToolParam(description = "Unique short task name, e.g. 'morning-weather'") String name,
            @ToolParam(description = "Instruction to execute on every run") String prompt,
            @ToolParam(description = "Spring 6-field cron: second minute hour day-of-month month day-of-week, "
                    + "e.g. '0 30 9 * * MON-FRI'. Interpreted in the bot's time zone") String schedule,
            @ToolParam(description = "Number of runs before the task disables itself (1-100). "
                    + "Omit for unlimited", required = false) Integer count,
            @ToolParam(description = "Create enabled (default true)", required = false) Boolean enabled) {
        log.info("Tool createScheduledTask(name='{}', schedule='{}', count={})", name, schedule, count);
        return run(() -> "Created: " + summary(
                service.create(name, prompt, schedule, enabled == null || enabled, count)));
    }

    @Tool(description = """
            Update the prompt, schedule and/or number of runs of an existing task. Pass only what
            should change. Setting a new count does not re-enable a disabled task; call
            enableScheduledTask afterwards.
            """)
    public String updateScheduledTask(
            @ToolParam(description = "Name of the existing task") String name,
            @ToolParam(description = "New prompt", required = false) String prompt,
            @ToolParam(description = "New 6-field Spring cron expression (bot's time zone)", required = false)
            String schedule,
            @ToolParam(description = "New number of remaining runs (1-100)", required = false) Integer count,
            @ToolParam(description = "true to remove the run limit", required = false) Boolean unlimited) {
        log.info("Tool updateScheduledTask(name='{}')", name);
        return run(() -> "Updated: " + summary(
                service.update(name, prompt, schedule, count, Boolean.TRUE.equals(unlimited))));
    }

    @Tool(description = "Enable a scheduled task so it runs on its schedule. Fails if it has no runs left.")
    public String enableScheduledTask(
            @ToolParam(description = "Task name") String name) {
        log.info("Tool enableScheduledTask(name='{}')", name);
        return run(() -> "Enabled: " + summary(service.setEnabled(name, true)));
    }

    @Tool(description = "Disable a scheduled task so it stops running (it is kept and can be re-enabled).")
    public String disableScheduledTask(
            @ToolParam(description = "Task name") String name) {
        log.info("Tool disableScheduledTask(name='{}')", name);
        return run(() -> "Disabled: " + summary(service.setEnabled(name, false)));
    }

    private static String run(Supplier<String> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            return "ERROR: " + e.getMessage();
        } catch (Exception e) {
            log.warn("Scheduled task tool failed: {}", e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    private String summary(ScheduledTaskEntity t) {
        String lastRun = t.getLastExecutionAt() == null
                ? "never"
                : TIME_FMT.format(t.getLastExecutionAt().atZone(properties.timeZone()));
        String runsLeft = t.getRemainingCount() == null ? "unlimited" : t.getRemainingCount().toString();
        return "%s | schedule: %s (%s) | status: %s | runs left: %s | last run: %s".formatted(
                t.getName(), t.getSchedule(), properties.timeZone(), t.getStatus(), runsLeft, lastRun);
    }
}
