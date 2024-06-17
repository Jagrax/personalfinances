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
public interface ExpensesGroupRepository extends JpaRepository<ExpensesGroup, Long>, JpaSpecificationExecutor<ExpensesGroup> {

    List<ExpensesGroup> findByCreationUser(User user);

    @Query("SELECT eg FROM ExpensesGroup eg WHERE eg.creationUser = :user OR :user IN (SELECT m.id FROM eg.members m)")
    List<ExpensesGroup> findAllByCreationUserOrMember(@Param("user") User user, Sort sort);

    boolean existsByNameAndCreationUser(String name, User creationUSer);
}