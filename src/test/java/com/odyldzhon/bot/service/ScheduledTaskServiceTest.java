package com.odyldzhon.bot.service;

import com.odyldzhon.bot.persistence.entity.ScheduledTaskEntity;
import com.odyldzhon.bot.persistence.entity.ScheduledTaskEntity.Status;
import com.odyldzhon.bot.persistence.repository.ScheduledTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledTaskServiceTest {

    @Mock private ScheduledTaskRepository repository;

    private ScheduledTaskService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledTaskService(repository,
                Clock.fixed(Instant.parse("2026-04-28T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Creates an enabled task with trimmed values")
    void create_validInput_savesEnabledTask() {
        when(repository.findByName("morning")).thenReturn(Optional.empty());
        when(repository.save(any(ScheduledTaskEntity.class))).thenAnswer(i -> i.getArgument(0));

        ScheduledTaskEntity task = service.create(" morning ", " say hi ", "0 0 9 * * *", true, null);

        assertThat(task.getName()).isEqualTo("morning");
        assertThat(task.getPrompt()).isEqualTo("say hi");
        assertThat(task.getStatus()).isEqualTo(Status.ENABLED);
        assertThat(task.getRemainingCount()).isNull();
        assertThat(task.getCreatedAt()).isEqualTo(Instant.parse("2026-04-28T08:00:00Z"));
    }

    @Test
    @DisplayName("Rejects a count below 1 or above 100")
    void create_invalidCount_throws() {
        assertThatThrownBy(() -> service.create("x", "p", "0 0 9 * * *", true, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("count");
        assertThatThrownBy(() -> service.create("x", "p", "0 0 9 * * *", true, 101))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("count");
    }

    @Test
    @DisplayName("Each execution consumes one run and the last one disables the task")
    void markExecuted_countReachesZero_disablesTask() {
        ScheduledTaskEntity task = ScheduledTaskEntity.builder()
                .id(1L).name("x").status(Status.ENABLED).remainingCount(2).build();
        when(repository.findById(1L)).thenReturn(Optional.of(task));
        Instant at = Instant.parse("2026-04-28T08:00:00Z");

        service.markExecuted(1L, at);
        assertThat(task.getRemainingCount()).isEqualTo(1);
        assertThat(task.getStatus()).isEqualTo(Status.ENABLED);

        service.markExecuted(1L, at);
        assertThat(task.getRemainingCount()).isZero();
        assertThat(task.getStatus()).isEqualTo(Status.DISABLED);
        assertThat(task.getLastExecutionAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("Unlimited tasks stay enabled and keep a null count")
    void markExecuted_unlimited_staysEnabled() {
        ScheduledTaskEntity task = ScheduledTaskEntity.builder()
                .id(1L).name("x").status(Status.ENABLED).build();
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        service.markExecuted(1L, Instant.parse("2026-04-28T08:00:00Z"));

        assertThat(task.getRemainingCount()).isNull();
        assertThat(task.getStatus()).isEqualTo(Status.ENABLED);
    }

    @Test
    @DisplayName("Cannot enable a task that has no runs left")
    void setEnabled_noRunsLeft_throws() {
        ScheduledTaskEntity task = ScheduledTaskEntity.builder()
                .name("x").status(Status.DISABLED).remainingCount(0).build();
        when(repository.findByName("x")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.setEnabled("x", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no runs left");
    }

    @Test
    @DisplayName("Update can set a new count or remove the limit")
    void update_countAndUnlimited_applied() {
        ScheduledTaskEntity task = ScheduledTaskEntity.builder()
                .name("x").prompt("p").schedule("0 0 9 * * *").status(Status.DISABLED).remainingCount(0).build();
        when(repository.findByName("x")).thenReturn(Optional.of(task));
        when(repository.save(task)).thenReturn(task);

        assertThat(service.update("x", null, null, 3, false).getRemainingCount()).isEqualTo(3);
        assertThat(service.update("x", null, null, null, true).getRemainingCount()).isNull();
        assertThatThrownBy(() -> service.update("x", null, null, 2, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects duplicate names")
    void create_duplicateName_throws() {
        when(repository.findByName("morning")).thenReturn(Optional.of(new ScheduledTaskEntity()));

        assertThatThrownBy(() -> service.create("morning", "p", "0 0 9 * * *", true, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects an invalid cron expression")
    void create_invalidCron_throws() {
        assertThatThrownBy(() -> service.create("x", "p", "every day", true, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid cron");
    }

    @Test
    @DisplayName("Updates only provided fields")
    void update_onlySchedule_keepsPrompt() {
        ScheduledTaskEntity existing = ScheduledTaskEntity.builder()
                .name("x").prompt("old").schedule("0 0 9 * * *").status(Status.ENABLED).build();
        when(repository.findByName("x")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        ScheduledTaskEntity updated = service.update("x", null, "0 30 9 * * *", null, false);

        assertThat(updated.getPrompt()).isEqualTo("old");
        assertThat(updated.getSchedule()).isEqualTo("0 30 9 * * *");
    }

    @Test
    @DisplayName("Disables a task")
    void setEnabled_false_disablesTask() {
        ScheduledTaskEntity existing = ScheduledTaskEntity.builder().name("x").status(Status.ENABLED).build();
        when(repository.findByName("x")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        assertThat(service.setEnabled("x", false).getStatus()).isEqualTo(Status.DISABLED);
    }

    @Test
    @DisplayName("Reports unknown tasks")
    void get_unknown_throws() {
        when(repository.findByName("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("nope"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
    }
}
