package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.InstanceTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstanceTaskRepository extends JpaRepository<InstanceTask, Long> {

    List<InstanceTask> findByEnabledIsTrue();
}