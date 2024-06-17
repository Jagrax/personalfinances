package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.ExpensesGroup;
import ar.com.personalfinances.entity.SharedExpense;
import ar.com.personalfinances.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedExpenseRepository extends JpaRepository<SharedExpense, Long>, JpaSpecificationExecutor<SharedExpense> {

    List<SharedExpense> findByExpensesGroupId(Long expensesGroupId, Sort sort);

    @Query("SELECT se FROM SharedExpense se WHERE se.user = :user OR :user IN (SELECT m.id FROM se.members m)")
    List<SharedExpense> findAllByUserOrMember(@Param("user") User user, Sort sort);
}