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
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByUser(User user, Sort sort);

    List<Expense> findByAccountAndDateAndAmountEquals(Account account, Date date, BigDecimal amount);

    @EntityGraph(attributePaths = "category")
    List<Expense> findByAccountAndDateBetween(Account account, Date dateFrom, Date dateTo, Sort sort);

    List<Expense> findByAccountAndAmountEqualsAndDetailsLike(Account account, BigDecimal amount, String detailsLike);

    @Query(value = "SELECT c.name, c.color, SUM(e.amount) FROM expenses e JOIN categories c ON c.id = e.category_id WHERE e.account_id = :#{#account.id} AND e.date >= CURDATE() - INTERVAL 30 DAY AND c.name NOT IN (:excludedCategories) GROUP BY c.name, c.color ORDER BY c.name", nativeQuery = true)
    List<Object[]> getLast30DaysSummary(Account account, List<String> excludedCategories);

    @Query(value = "SELECT c.name, c.color, SUM(-1 * e.amount) FROM expenses e JOIN categories c ON c.id = e.category_id WHERE e.account_id = :#{#account.id} AND e.date >= CURDATE() - INTERVAL 30 DAY AND c.name NOT IN (:excludedCategories) AND e.description NOT LIKE :excludedDescriptionPattern AND e.amount < 0 GROUP BY c.name, c.color ORDER BY c.name", nativeQuery = true)
    List<Object[]> getLast30DaysSummaryForBankAccount(Account account, List<String> excludedCategories, String excludedDescriptionPattern);

    @Query(value = "SELECT c.name, c.color, SUM(e.amount) FROM expenses e JOIN categories c ON c.id = e.category_id WHERE e.account_id = :#{#account.id} AND e.date >= :periodStart AND (e.amount > 0 OR (e.amount < 0 AND c.id IN (:refundCategoryIds)) ) GROUP BY c.name, c.color ORDER BY c.name", nativeQuery = true)
    List<Object[]> getLastPeriodSummaryForCreditCard(Account account, LocalDate periodStart, List<Long> refundCategoryIds);

    @Query(value = "SELECT MAX(e.date) FROM expenses e WHERE e.account_id = :#{#account.id} and e.description = 'Reembolso Gastos'", nativeQuery = true)
    Date findLastReimbursementDate(Account account);

    @Query(value = "SELECT e.description, CASE WHEN e.description = 'Desayuno' THEN '#ffc107' WHEN e.description = 'Almuerzo' THEN '#0d6efd' ELSE NULL END AS color, SUM(e.amount) FROM expenses e WHERE e.account_id = :#{#account.id} and e.date >= :periodStart and e.amount > 0 GROUP BY e.description", nativeQuery = true)
    List<Object[]> getLastPeriodSummaryForSDD(Account account, LocalDate periodStart);

    @Query(value = "SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.account = :account")
    BigDecimal sumByAccount(Account account);
}