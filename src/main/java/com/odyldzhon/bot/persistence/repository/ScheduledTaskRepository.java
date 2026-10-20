package com.odyldzhon.bot.persistence.repository;

import com.odyldzhon.bot.persistence.entity.ScheduledTaskEntity;
import com.odyldzhon.bot.persistence.entity.ScheduledTaskEntity.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduledTaskRepository extends JpaRepository<ScheduledTaskEntity, Long> {

    Optional<ScheduledTaskEntity> findByName(String name);

    List<ScheduledTaskEntity> findByStatus(Status status);

    List<ScheduledTaskEntity> findAllByOrderByNameAsc();
}
