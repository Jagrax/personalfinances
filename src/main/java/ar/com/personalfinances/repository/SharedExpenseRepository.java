package ar.com.personalfinances.repository;

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

    @Query("SELECT DISTINCT se FROM SharedExpense se LEFT JOIN se.members sem WHERE se.payer = :user OR sem.user = :user")
    List<SharedExpense> findAllByUserOrMember(@Param("user") User user, Sort sort);

    @Query("SELECT se FROM SharedExpense se LEFT JOIN se.members sem WHERE se.expensesGroup IS NULL AND (se.payer = :user OR sem.user = :user)")
    List<SharedExpense> findAllWithoutGroupByUserOrMember(@Param("user") User user, Sort sort);

    @Query("SELECT se FROM SharedExpense se LEFT JOIN se.members m WHERE se.payer.id = :userId OR m.user.id = :userId")
    List<SharedExpense> findAllByUserOrMember(@Param("userId") Long userId, Sort sort);
}