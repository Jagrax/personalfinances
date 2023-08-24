package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    Expense findByIdAndUser_Id(Long expenseId, Long userId);
}