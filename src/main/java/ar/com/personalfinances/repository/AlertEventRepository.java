package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {
}