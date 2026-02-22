package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByUser(User user, Sort sort);

    List<Expense> findByAccountAndDateAndAmountEquals(Account account, Date date, BigDecimal amount);

    @EntityGraph(attributePaths = "category")
    List<Expense> findByAccountAndDateBetween(Account account, Date dateFrom, Date dateTo, Sort sort);

    List<Expense> findByAccountAndAmountEqualsAndDetailsLike(Account account, BigDecimal amount, String detailsLike);

    @Query(value = "SELECT c.name, SUM(e.amount) FROM expenses e JOIN categories c ON c.id = e.category_id WHERE e.account_id = :#{#account.id} AND e.date >= CURDATE() - INTERVAL 30 DAY AND c.name NOT IN (:excludedCategories) GROUP BY c.name ORDER BY SUM(e.amount) DESC", nativeQuery = true)
    List<Object[]> getLast30DaysSumary(Account account, List<String> excludedCategories);
}