package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.ExpenseMapping;
import ar.com.personalfinances.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseMappingRepository extends JpaRepository<ExpenseMapping, Long> {

    Optional<ExpenseMapping> findFirstByUserAndBankDescriptionIgnoreCase(User user, String bankDescription);

    List<ExpenseMapping> findAllByUserAndRegexPatternIsNotNull(User user);
}