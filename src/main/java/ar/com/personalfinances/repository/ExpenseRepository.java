package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByUser(User user, Sort sort);

    List<Expense> findByAccountAndDateAndAmountEquals(Account account, LocalDate date, BigDecimal amount);

    List<Expense> findByAccountAndDateBetween(Account account, LocalDate dateFrom, LocalDate dateTo, Sort sort);

    List<Expense> findByAccountAndAmountEqualsAndDetailsLike(Account account, BigDecimal amount, String detailsLike);

    List<Expense> findByAccountAndDescriptionStartingWith(Account account, String descriptionPrefix);

    @Query(value = "SELECT t.name, t.color, SUM(e.amount) FROM expenses e JOIN expense_tags et ON et.expense_id = e.id JOIN tags t ON t.id = et.tag_id WHERE e.account_id = :#{#account.id} AND e.date >= CURDATE() - INTERVAL 30 DAY AND t.name NOT IN (:excludedTags) GROUP BY t.name, t.color ORDER BY t.name", nativeQuery = true)
    List<Object[]> getLast30DaysSummary(Account account, List<String> excludedTags);

    @Query(value = "SELECT t.name, t.color, SUM(-1 * e.amount) FROM expenses e JOIN expense_tags et ON et.expense_id = e.id JOIN tags t ON t.id = et.tag_id WHERE e.account_id = :#{#account.id} AND e.date >= CURDATE() - INTERVAL 30 DAY AND t.name NOT IN (:excludedTags) AND e.description NOT LIKE :excludedDescriptionPattern AND e.amount < 0 GROUP BY t.name, t.color ORDER BY t.name", nativeQuery = true)
    List<Object[]> getLast30DaysSummaryForBankAccount(Account account, List<String> excludedTags, String excludedDescriptionPattern);

    @Query(value = "SELECT t.name, t.color, SUM(e.amount) FROM expenses e JOIN expense_tags et ON et.expense_id = e.id JOIN tags t ON t.id = et.tag_id WHERE e.account_id = :#{#account.id} AND e.date >= :periodStart AND (e.amount > 0 OR (e.amount < 0 AND t.id IN (:refundTagIds)) ) GROUP BY t.name, t.color ORDER BY t.name", nativeQuery = true)
    List<Object[]> getLastPeriodSummaryForCreditCard(Account account, LocalDate periodStart, List<Long> refundTagIds);

    @Query(value = "SELECT MAX(e.date) FROM expenses e WHERE e.account_id = :#{#account.id} and e.description = 'Reembolso Gastos'", nativeQuery = true)
    LocalDate findLastReimbursementDate(Account account);

    @Query(value = "SELECT e.description, CASE WHEN e.description = 'Desayuno' THEN '#ffc107' WHEN e.description = 'Almuerzo' THEN '#0d6efd' ELSE NULL END AS color, SUM(e.amount) FROM expenses e WHERE e.account_id = :#{#account.id} and e.date >= :periodStart and e.amount > 0 GROUP BY e.description", nativeQuery = true)
    List<Object[]> getLastPeriodSummaryForSDD(Account account, LocalDate periodStart);

    @Query(value = "SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.account = :account")
    BigDecimal sumByAccount(Account account);

    List<Expense> findByAccount(Account account);
}