package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.ExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, Long> {

    List<ExpenseItem> findByExpense_Id(Long expenseId);
}
