package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.SharedExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SharedExpenseParticipantRepository extends JpaRepository<SharedExpenseParticipant, Long>, JpaSpecificationExecutor<SharedExpenseParticipant> {
}