package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.InstanceTask;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InstanceTaskRepository extends JpaRepository<InstanceTask, Long> {

    List<InstanceTask> findByEnabledIsTrue();

    @Modifying
    @Transactional
    @Query("update InstanceTask t set t.lastExecution = :lastExecution where t.id = :id")
    void updateLastExecution(@Param("id") Long id, @Param("lastExecution") LocalDateTime lastExecution);
}
