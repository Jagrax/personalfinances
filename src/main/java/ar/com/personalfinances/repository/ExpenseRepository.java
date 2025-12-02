package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByUser(User user, Sort sort);

    List<Expense> findByAccountAndDateAndAmountEquals(Account account, Date date, BigDecimal amount);

    List<Expense> findByAccountAndDateBetween(Account account, Date dateFrom, Date dateTo, Sort sort);
}