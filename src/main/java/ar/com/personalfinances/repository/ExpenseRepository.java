package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.CardPeriod;
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

    List<Expense> findByAccountAndDate(Account account, LocalDate date);

    List<Expense> findByAccountAndDateBetween(Account account, LocalDate dateFrom, LocalDate dateTo, Sort sort);

    List<Expense> findByAccountAndDetailsLike(Account account, String detailsLike);

    List<Expense> findByAccountAndDescriptionStartingWith(Account account, String descriptionPrefix);

    @Query(value = "SELECT MAX(e.date) FROM expenses e WHERE e.account_id = :#{#account.id} and e.description = 'Reembolso Gastos'", nativeQuery = true)
    LocalDate findLastReimbursementDate(Account account);

    List<Expense> findByAccount(Account account);

    List<Expense> findByAccountAndPeriodIsNull(Account account);

    List<Expense> findByAccountAndPeriod(Account account, CardPeriod period);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
            "WHERE e.account.id = :#{#account.id} AND e.period.id = :#{#period.id} " +
            "AND NOT EXISTS (SELECT 1 FROM Expense e2 JOIN e2.tags t WHERE e2.id = e.id AND t.name = 'Pago de tarjeta') " +
            "AND NOT (e.amount < 0 AND EXISTS (SELECT 1 FROM Expense e3 JOIN e3.tags t2 WHERE e3.id = e.id AND t2.name = 'Impuestos'))")
    BigDecimal sumAmountByAccountAndPeriodExcludingPayment(Account account, CardPeriod period);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
            "WHERE e.account.id = :#{#account.id} AND e.period.id = :#{#period.id} " +
            "AND (e.originalDescription IS NULL OR TRIM(e.originalDescription) = '') " +
            "AND NOT EXISTS (SELECT 1 FROM Expense e2 JOIN e2.tags t WHERE e2.id = e.id AND t.name = 'Pago de tarjeta')")
    BigDecimal sumAmountByAccountAndPeriodWithoutOriginalDescription(Account account, CardPeriod period);
}