package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.ExpenseMapping;
import ar.com.personalfinances.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ExpenseMappingRepository extends JpaRepository<ExpenseMapping, Long>, JpaSpecificationExecutor<ExpenseMapping> {

    Optional<ExpenseMapping> findFirstByUserAndBankDescriptionIgnoreCaseAndEnabledTrue(User user, String bankDescription);

    List<ExpenseMapping> findAllByUserAndRegexPatternIsNotNullAndEnabledTrue(User user);
}
